package ink.tenqui.flowtone.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.request.ImageRequest
import ink.tenqui.flowtone.data.local.AudioScanner
import ink.tenqui.flowtone.data.local.LikedTracksRepository
import ink.tenqui.flowtone.lyrics.LocalLyricsRepository
import ink.tenqui.flowtone.lyrics.LyricsLoadResult
import ink.tenqui.flowtone.lyrics.LyricsLoadSource
import ink.tenqui.flowtone.lyrics.LyricsPreloadScheduler
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.lyrics.SongLyricsState
import ink.tenqui.flowtone.lyrics.LyricsFolder
import ink.tenqui.flowtone.data.local.LocalMusicRepository
import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.data.local.SongMetadataPreloader
import ink.tenqui.flowtone.data.listening.ListeningStatsRepositoryProvider
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.repository.MusicRepository
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.data.online.ProviderSearchCallResult
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.localAlbumsFrom
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.ProviderSearchCategoryState
import ink.tenqui.flowtone.data.search.ProviderSearchCoordinator
import ink.tenqui.flowtone.data.search.ExtensionManagerProviderSearchGateway
import ink.tenqui.flowtone.data.search.acceptFailure
import ink.tenqui.flowtone.data.search.acceptPage
import ink.tenqui.flowtone.data.search.startRequest
import ink.tenqui.flowtone.data.search.SearchQuery
import ink.tenqui.flowtone.data.search.SearchRepository
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.data.online.ProviderSearchLandingState
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.core.model.toPresentationSong
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackQueueItem
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import ink.tenqui.flowtone.playback.PlaybackState
import ink.tenqui.flowtone.playback.toPlaybackSource
import ink.tenqui.flowtone.playback.toSongOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class ExtensionContentFailure(
    val id: Long,
    val title: String,
    val details: String
)

data class MusicUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<LocalAlbum> = emptyList(),
    val sourceQueue: List<Song> = emptyList(),
    val playbackQueue: List<Song> = emptyList(),
    val sourceQueueItems: List<PlaybackQueueItem> = emptyList(),
    val playbackQueueItems: List<PlaybackQueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null,
    val hasScanned: Boolean = false,
    val listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot(),
    val likedTracks: List<PersistentTrack> = emptyList(),
    val trackPlaybackErrorMessage: String? = null,
    val trackPlaybackErrorEventId: Long = 0L,
    /** 在线请求尚未被播放器确认；绝不能覆盖 confirmed PlaybackState。 */
    val providerSongs: Map<String, List<ProviderSong>> = emptyMap(),
    val providerAlbums: Map<String, List<ProviderAlbum>> = emptyMap(),
    val providerUserPlaylists: Map<String, List<ProviderPlaylist>> = emptyMap(),
    val providerPlaylistDetails: Map<String, ProviderPlaylistDetailState> = emptyMap(),
    val libraryProviderOptions: List<LibraryProviderOption> = emptyList(),
    val libraryProviderSourcesResolved: Boolean = false,
    val providerSongCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerAlbumCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerUserPlaylistLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val extensionContentFailures: List<ExtensionContentFailure> = emptyList(),
    val libraryRefreshingSources: Set<String> = emptySet(),
    val libraryRefreshFailure: Pair<String, Long>? = null
)

internal fun MusicUiState.startLibraryRefresh(sourceKey: String): MusicUiState =
    copy(libraryRefreshingSources = libraryRefreshingSources + sourceKey)

internal fun MusicUiState.finishLibraryRefresh(sourceKey: String, failureId: Long?): MusicUiState =
    copy(
        libraryRefreshingSources = libraryRefreshingSources - sourceKey,
        libraryRefreshFailure = failureId?.let { sourceKey to it } ?: libraryRefreshFailure
    )

enum class ProviderCatalogLoadState {
    Loading,
    Loaded,
    Failed
}

data class ProviderPlaylistDetailState(
    val songs: List<ProviderSong> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
)

private fun searchScopeFromPreference(value: String): SearchScope = when {
    value == "local" -> SearchScope.Local
    value.startsWith("provider:") -> SearchScope.Provider(value.removePrefix("provider:").trim())
    else -> SearchScope.All
}

private fun searchScopePreferenceValue(scope: SearchScope): String = when (scope) {
    SearchScope.All -> "all"
    SearchScope.Local -> "local"
    is SearchScope.Provider -> "provider:${scope.extensionId}"
}

private fun emptyProviderSearchCategoryStates(): Map<ProviderSearchCategory, ProviderSearchCategoryState> =
    ProviderSearchCategory.entries.associateWith { ProviderSearchCategoryState() }

internal fun providerSearchCategoryStatesForNewQuery(
    scope: SearchScope,
    selectedCategory: ProviderSearchCategory
): Map<ProviderSearchCategory, ProviderSearchCategoryState> =
    ProviderSearchCategory.entries.associateWith { category ->
        if (scope is SearchScope.Provider && category == selectedCategory) {
            ProviderSearchCategoryState().startRequest(cursor = null)
        } else {
            ProviderSearchCategoryState()
        }
    }

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepository = MusicRepository(
        localMusicRepository = LocalMusicRepository(
            audioScanner = AudioScanner(application)
        )
    )
    private val playbackSettingsStore = PlaybackSettingsStore(application)
    private val likedTracksRepository = LikedTracksRepository.get(application)
    private val localLyricsRepository = LocalLyricsRepository(application)
    private val listeningStatsRepository = ListeningStatsRepositoryProvider.get(application)
    private val searchRepository = SearchRepository()
    private val extensionManager = ExtensionManager.get(application)
    private val appPreferences = AppPreferences(application)
    private val playbackController = PlaybackController(
        context = application,
        initialPlaybackOrderMode = playbackSettingsStore.getPlaybackOrderMode(),
        onPlaybackEnded = ::handlePlaybackEnded,
        onMediaItemChanged = ::syncCurrentSongFromMediaId
    )
    private val songMetadataPreloader = SongMetadataPreloader(application)
    private val initialLikedTracks = likedTracksRepository.tracks.value
    private val _uiState = MutableStateFlow(
        MusicUiState(
            listeningStats = listeningStatsRepository.getStats(),
            likedTracks = initialLikedTracks
        )
    )
    private val _searchUiState = MutableStateFlow(GlobalSearchUiState())
    private val providerSearchCoordinator = ProviderSearchCoordinator(
        state = _searchUiState,
        gateway = ExtensionManagerProviderSearchGateway(extensionManager),
        onFailure = { scope, category, error ->
            val source = when (scope) {
                SearchScope.All -> "所有来源"
                SearchScope.Local -> "本地音乐"
                is SearchScope.Provider -> "扩展 ID：${scope.extensionId}"
            }
            reportExtensionContentFailure(
                title = "在线搜索失败",
                context = "搜索类别：$category\n搜索来源：$source",
                error = error
            )
        }
    )
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    private val _likedTracks = MutableStateFlow(initialLikedTracks)
    private val _songLyricsState = MutableStateFlow(SongLyricsState())
    // 歌词只使用 MediaController 确认的位置，不读取进度条的动画或拖动状态。
    private val _confirmedPlaybackPosition = MutableStateFlow(PlaybackPositionSnapshot())
    private val _lyricsFolders = MutableStateFlow(localLyricsRepository.getLyricsFolders())
    private val lyricsReloadVersion = MutableStateFlow(0)
private var sourceQueue: List<Song> = emptyList()
private var playbackQueue: List<Song> = emptyList()
private var sourceTrackQueue: List<QueueTrackEntry> = emptyList()
private var playbackTrackQueue: List<QueueTrackEntry> = emptyList()
    /** 当前队列内在线展示项对应的 runtime 引用；只保存身份，不保存媒体资源。 */
    private var onlineQueueSongs: Map<String, ProviderSong> = emptyMap()
    private var currentQueueIndex: Int = -1
    private var preloadSongMetadataCount: Int = 5
    private var preloadLyricsCount: Int = 5
    private var preloadJob: Job? = null
    private var onlineArtworkPreloadJob: Job? = null
    private var onlinePreloadGeneration: Long = 0L
    /** 仅本进程有效；不保存 runtime ref 或播放 URL。 */
    private val onlinePresentationCache = mutableMapOf<String, ProviderSong>()
    private var metadataPreloadUris: List<String> = emptyList()
    private val lyricsPreloadScheduler by lazy {
        LyricsPreloadScheduler(viewModelScope, localLyricsRepository)
    }
    private var searchJob: Job? = null
    private var providerSearchJob: Job? = null
    private var searchLandingJob: Job? = null
    private var searchLandingGeneration: Long = 0L
    private var playbackOrderModeJob: Job? = null
    private var lastProgressDiagnosticLogUptimeMs = 0L
    private var currentPlaybackSource: PlaybackSource = PlaybackSource.Unknown
    private val localScanMutex = Mutex()
    private val providerCollectionLoads = mutableSetOf<String>()
    private val providerRefreshVersions = mutableMapOf<String, Long>()
    private var libraryRefreshFailureSequence = 0L
    private val extensionContentFailureSequence = AtomicLong()
    private val requestedProviderCollectionIds = mutableSetOf<String>()
    private val requestedProviderUserPlaylistIds = mutableSetOf<String>()
    private val requestedProviderPlaylists = mutableMapOf<String, ProviderPlaylistSearchItem>()
    private var observedExtensionRuntimeGeneration = extensionManager.runtimeState.value.generation
    private var extensionRuntimeRefreshPending = extensionManager.runtimeState.value.isReloading

    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    val searchUiState: StateFlow<GlobalSearchUiState> = _searchUiState.asStateFlow()
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()
    val likedTracks: StateFlow<List<PersistentTrack>> = _likedTracks.asStateFlow()
    val songLyricsState: StateFlow<SongLyricsState> = _songLyricsState.asStateFlow()
    val confirmedPlaybackPosition: StateFlow<PlaybackPositionSnapshot> =
        _confirmedPlaybackPosition.asStateFlow()
    val lyricsFolders: StateFlow<List<LyricsFolder>> = _lyricsFolders.asStateFlow()

    private fun reportExtensionContentFailure(
        title: String,
        context: String,
        error: Throwable
    ) {
        enqueueExtensionContentFailure(
            title = title,
            details = "$context\n\n${error.stackTraceToString()}"
        )
    }

    private fun enqueueExtensionContentFailure(title: String, details: String) {
        val failure = ExtensionContentFailure(
            id = extensionContentFailureSequence.incrementAndGet(),
            title = title,
            details = details
        )
        _uiState.update { state ->
            state.copy(extensionContentFailures = (state.extensionContentFailures + failure).takeLast(20))
        }
    }

    private fun observeExtensionImageFailures() {
        viewModelScope.launch {
            extensionManager.extensionImageFailures.collect { failure ->
                reportExtensionContentFailure(
                    title = "在线图片加载失败",
                    context = "扩展 ID：${failure.image.extensionId}\n图片地址：${failure.image.url}",
                    error = failure.error
                )
            }
        }
    }

    private fun observePlaybackFailures() {
        var lastErrorEventId = playbackState.value.errorEventId
        viewModelScope.launch {
            playbackState.collect { state ->
                if (state.errorEventId == lastErrorEventId) return@collect
                lastErrorEventId = state.errorEventId
                val details = state.errorDetails ?: return@collect
                val title = state.currentSong?.title?.takeIf(String::isNotBlank) ?: "未知歌曲"
                enqueueExtensionContentFailure(
                    title = "播放失败",
                    details = "曲目：$title\n队列 ID：${state.currentQueueId ?: "未知"}\n\n$details"
                )
            }
        }
    }

    fun dismissExtensionContentFailure(id: Long) {
        _uiState.update { state ->
            state.copy(extensionContentFailures = state.extensionContentFailures.filterNot { it.id == id })
        }
    }

    private fun beginPlaybackRequest() {
        playbackController.clearPendingPlaybackRequest()
    }

    init {
        observeExtensionRuntime()
        observeExtensionImageFailures()
        observePlaybackFailures()
        startProgressTicker()
        startPlaybackOrderTicker()
        startConfirmedPlaybackPositionTicker()
        observeControllerConnection()
        observeListeningStats()
        observeLikedTracks()
        observeLogicalQueue()
        observeLyrics()
        refreshSearchSources()
    }

    private fun observeExtensionRuntime() {
        viewModelScope.launch {
            extensionManager.runtimeState.collect { runtimeState ->
                if (runtimeState.generation != observedExtensionRuntimeGeneration) {
                    observedExtensionRuntimeGeneration = runtimeState.generation
                    extensionRuntimeRefreshPending = true
                    _uiState.update { state ->
                        state.copy(libraryProviderSourcesResolved = false)
                    }
                    invalidateExtensionRuntimeRequests()
                }
                if (!runtimeState.isReloading && extensionRuntimeRefreshPending) {
                    extensionRuntimeRefreshPending = false
                    val queryText = _searchUiState.value.queryText
                    refreshSearchSources()
                    reloadRequestedProviderData()
                    if (runtimeState.lastReloadErrorType == null && queryText.isNotBlank()) {
                        updateSearchQuery(queryText)
                    }
                }
            }
        }
    }

    private fun invalidateExtensionRuntimeRequests() {
        searchJob?.cancel()
        providerSearchJob?.cancel()
        searchLandingJob?.cancel()
        searchLandingGeneration += 1L
        onlineArtworkPreloadJob?.cancel()
        onlinePreloadGeneration += 1L
        providerCollectionLoads.clear()
        onlinePresentationCache.clear()
        val confirmedQueueIndex = confirmedPlaybackQueueIndex()
        _uiState.update { state ->
            state.copy(
                currentQueueIndex = confirmedQueueIndex,
                providerSongs = emptyMap(),
                providerAlbums = emptyMap(),
                providerUserPlaylists = emptyMap(),
                providerPlaylistDetails = emptyMap(),
                providerSongCatalogLoadStates = emptyMap(),
                providerAlbumCatalogLoadStates = emptyMap(),
                providerUserPlaylistLoadStates = emptyMap()
            )
        }
        _searchUiState.update { state ->
            state.copy(
                scope = if (state.scope is SearchScope.Provider) SearchScope.All else state.scope,
                providerOptions = emptyList(),
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                landingState = ProviderSearchLandingState.Idle,
                isSearching = false,
                searchGeneration = state.searchGeneration + 1L
            )
        }
    }

    private fun reloadRequestedProviderData() {
        requestedProviderCollectionIds.toList().forEach(::loadProviderEntityCollections)
        requestedProviderUserPlaylistIds.toList().forEach(::loadProviderUserPlaylists)
        requestedProviderPlaylists.values.toList()
            .filter { extensionManager.isMusicProviderRuntimeAvailable(it.providerId) }
            .forEach { playlist -> loadProviderPlaylist(playlist, preferEmbeddedSongs = false) }
    }

    fun setPermissionStatus(hasPermission: Boolean) {
        _uiState.update {
            it.copy(
                hasPermission = hasPermission,
                errorMessage = null
            )
        }
    }

    fun setPreloadSongMetadataCount(count: Int) {
        val allowedValues = listOf(1, 3, 5, 7, 10)
        val sanitizedCount = allowedValues.minBy { kotlin.math.abs(it - count) }
        if (preloadSongMetadataCount == sanitizedCount) {
            return
        }

        preloadSongMetadataCount = sanitizedCount
        scheduleNextSongsPreload()
    }

    fun setPreloadLyricsCount(count: Int) {
        val allowedValues = listOf(1, 3, 5, 7, 10)
        val sanitizedCount = allowedValues.minBy { kotlin.math.abs(it - count) }
        if (preloadLyricsCount == sanitizedCount) {
            return
        }

        preloadLyricsCount = sanitizedCount
        scheduleNextSongsPreload()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setSongRecordThresholdSeconds(seconds: Int) {
        // 阈值由 AppPreferences 保存，后台播放服务中的 ListeningStatsTracker 会读取最新值。
    }

    fun updateSearchQuery(queryText: String) {
        updateSearchQueryForScope(queryText, _searchUiState.value.scope)
    }

    private fun updateSearchQueryForScope(queryText: String, scope: SearchScope) {
        val query = SearchQuery.from(queryText)
        searchJob?.cancel()
        providerSearchJob?.cancel()

        if (query.isBlank) {
            _searchUiState.update { current ->
                current.copy(
                    queryText = queryText,
                    scope = scope,
                    isSearching = false,
                    songResults = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    providerCategoryStates = emptyProviderSearchCategoryStates(),
                    searchGeneration = current.searchGeneration + 1
                )
            }
            loadSearchLandingForCurrentScope()
            return
        }

        _searchUiState.update { currentState ->
            currentState.copy(
                queryText = queryText,
                scope = scope,
                isSearching = scope == SearchScope.All || scope == SearchScope.Local,
                songResults = emptyList(),
                artistResults = emptyList(),
                albumResults = emptyList(),
                providerCategoryStates = providerSearchCategoryStatesForNewQuery(
                    scope = scope,
                    selectedCategory = currentState.selectedProviderCategory
                ),
                searchGeneration = currentState.searchGeneration + 1
            )
        }
        searchJob = viewModelScope.launch {
            delay(200)
            publishLocalSearchResults(query = query, visibleQueryText = queryText, scope = scope)
            loadInitialProviderSearchPage(acceptPreparedRequest = scope is SearchScope.Provider)
        }
    }

    fun clearSearchQuery() {
        searchJob?.cancel()
        providerSearchJob?.cancel()
        _searchUiState.update { current ->
            current.copy(
                queryText = "",
                isSearching = false,
                songResults = emptyList(),
                artistResults = emptyList(),
                albumResults = emptyList(),
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                searchGeneration = current.searchGeneration + 1
            )
        }
        loadSearchLandingForCurrentScope()
    }

    fun refreshSearchSources() {
        val runtimeStateBeforeRefresh = extensionManager.runtimeState.value
        val providers = extensionManager.availableMusicProviderOptions()
        val libraryProviders = extensionManager.availableLibraryProviderOptions()
        val runtimeStateAfterRefresh = extensionManager.runtimeState.value
        val libraryProviderSourcesResolved = runtimeStateBeforeRefresh == runtimeStateAfterRefresh &&
            runtimeStateAfterRefresh.generation > 0L &&
            !runtimeStateAfterRefresh.isReloading
        _uiState.update { state ->
            state.copy(
                // 重载期间的空列表只是临时态，保留上次确认的来源直到新列表就绪。
                libraryProviderOptions = if (libraryProviderSourcesResolved) {
                    libraryProviders
                } else {
                    state.libraryProviderOptions
                },
                libraryProviderSourcesResolved = libraryProviderSourcesResolved
            )
        }
        val preferredScope = searchScopeFromPreference(appPreferences.getSearchScopePreference())
        _searchUiState.update { current ->
            val selected = when (val existing = if (current.providerOptions.isEmpty()) preferredScope else current.scope) {
                is SearchScope.Provider -> existing.takeIf { candidate -> providers.any { it.extensionId == candidate.extensionId } }
                    ?: SearchScope.All
                else -> existing
            }
            current.copy(scope = selected, providerOptions = providers)
        }
        if (_searchUiState.value.query.isBlank) loadSearchLandingForCurrentScope()
    }

    fun selectSearchScope(scope: SearchScope) {
        val validScope = when (scope) {
            is SearchScope.Provider -> scope.takeIf { selected ->
                _searchUiState.value.providerOptions.any { it.extensionId == selected.extensionId }
            } ?: SearchScope.All
            else -> scope
        }
        appPreferences.setSearchScopePreference(searchScopePreferenceValue(validScope))
        val currentQuery = _searchUiState.value.queryText
        if (SearchQuery.from(currentQuery).isBlank) {
            _searchUiState.update {
                it.copy(
                    scope = validScope,
                    songResults = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    providerCategoryStates = emptyProviderSearchCategoryStates(),
                    isSearching = false,
                    searchGeneration = it.searchGeneration + 1
                )
            }
            loadSearchLandingForCurrentScope()
        } else {
            updateSearchQueryForScope(currentQuery, validScope)
        }
    }

    private suspend fun publishLocalSearchResults(
        query: SearchQuery,
        visibleQueryText: String,
        scope: SearchScope
    ) {
        val results = if (scope == SearchScope.All || scope == SearchScope.Local) {
            searchRepository.search(query)
        } else ink.tenqui.flowtone.data.search.SearchResults.Empty
        _searchUiState.update { currentState ->
            if (currentState.queryText != visibleQueryText || currentState.scope != scope) {
                currentState
            } else {
                currentState.copy(
                    isSearching = false,
                    songResults = results.songs,
                    artistResults = results.artists,
                    albumResults = results.albums
                )
            }
        }
    }

    fun selectProviderSearchCategory(category: ProviderSearchCategory) {
        _searchUiState.update { it.copy(selectedProviderCategory = category) }
        if (!_searchUiState.value.query.isBlank) loadInitialProviderSearchPage()
    }

    fun loadMoreProviderSearchResults(category: ProviderSearchCategory = _searchUiState.value.selectedProviderCategory) {
        viewModelScope.launch { providerSearchCoordinator.loadMore(category) }
    }

    private fun loadInitialProviderSearchPage(acceptPreparedRequest: Boolean = false) {
        providerSearchJob = viewModelScope.launch {
            providerSearchCoordinator.loadInitial(acceptPreparedRequest)
        }
    }

    private fun refreshSearchIndex(songs: List<Song>, albums: List<LocalAlbum>): Job =
        viewModelScope.launch {
            searchRepository.updateLocalLibrary(songs, albums)
            val currentSearchState = _searchUiState.value
            val query = SearchQuery.from(currentSearchState.queryText)
            if (!query.isBlank) {
                searchJob?.cancel()
                _searchUiState.update { it.copy(isSearching = true) }
                publishLocalSearchResults(
                    query = query,
                    visibleQueryText = currentSearchState.queryText,
                    scope = currentSearchState.scope
                )
            }
        }

    private fun rebuildPlaybackQueueForMode(
        mode: PlaybackOrderMode,
        currentSong: Song?
    ) {
        playbackQueue = buildPlaybackQueueForMode(sourceQueue, mode, currentSong)
        playbackTrackQueue = playbackQueue.mapNotNull { queuedSong ->
            sourceTrackQueue.firstOrNull { isSameSong(it.presentation, queuedSong) }
        }
        currentQueueIndex = findSongIndex(playbackQueue, currentSong)
    }

    private fun buildPlaybackQueueForMode(
        queue: List<Song>,
        mode: PlaybackOrderMode,
        currentSong: Song?
    ): List<Song> {
        return when (mode) {
            PlaybackOrderMode.Shuffle -> buildShuffledPlaybackQueue(queue, currentSong)
            PlaybackOrderMode.Sequence,
            PlaybackOrderMode.RepeatOne -> queue
        }
    }

    private fun buildShuffledPlaybackQueue(
        queue: List<Song>,
        currentSong: Song?
    ): List<Song> {
        if (queue.isEmpty()) {
            return emptyList()
        }

        val officialCurrentSong = currentSong?.let { findSong(queue, it) }
        return if (officialCurrentSong == null) {
            queue.shuffled()
        } else {
            listOf(officialCurrentSong) + queue
                .filterNot { isSameSong(it, officialCurrentSong) }
                .shuffled()
        }
    }

    private fun buildPlaybackOrderIndices(
        orderedQueue: List<Song>,
        sourceQueue: List<Song>
    ): IntArray? {
        if (orderedQueue.size != sourceQueue.size) {
            return null
        }

        val indicesBySongId = sourceQueue
            .withIndex()
            .groupBy(
                keySelector = { it.value.id },
                valueTransform = { it.index }
            )
            .mapValues { (_, indices) -> indices.toMutableList() }
            .toMutableMap()
        val orderedIndices = IntArray(orderedQueue.size)

        orderedQueue.forEachIndexed { orderIndex, song ->
            val availableIndices = indicesBySongId[song.id] ?: return null
            if (availableIndices.isEmpty()) {
                return null
            }
            orderedIndices[orderIndex] = availableIndices.removeAt(0)
        }

        return orderedIndices
    }

    private fun findSong(queue: List<Song>, song: Song): Song? {
        return queue.firstOrNull { isSameSong(it, song) }
    }

    private fun findSongIndex(queue: List<Song>, song: Song?): Int {
        song ?: return -1
        return queue.indexOfFirst { isSameSong(it, song) }
    }

    private fun isSameSong(first: Song, second: Song): Boolean {
        return first.id == second.id || first.uri == second.uri
    }

    fun scanSongs() {
        if (!_uiState.value.hasPermission || _uiState.value.isLoading) {
            return
        }

        viewModelScope.launch { performLocalScan(preservePlayback = false) }
    }

    private suspend fun performLocalScan(preservePlayback: Boolean): Boolean = localScanMutex.withLock {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = musicRepository.loadLocalSongs()
                    songs to localAlbumsFrom(songs)
                }
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            val loadedSongs = result.getOrNull()?.first

            _uiState.update { currentState ->
                result.fold(
                    onSuccess = { (songs, albums) ->
                        if (!preservePlayback && playbackState.value.playbackQueue.isEmpty()) {
                            sourceQueue = songs
                            sourceTrackQueue = songs.map { song ->
                                QueueTrackEntry(song.toPersistentTrack(), song)
                            }
                            rebuildPlaybackQueueForMode(
                                mode = playbackState.value.playbackOrderMode,
                                currentSong = playbackState.value.currentSong
                            )
                        }
                        currentState.copy(
                            isLoading = false,
                            songs = songs,
                            albums = albums,
                            sourceQueue = sourceQueue,
                            playbackQueue = playbackQueue,
                            currentQueueIndex = currentQueueIndex,
                            errorMessage = null,
                            hasScanned = true
                        )
                    },
                    onFailure = { error ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "\u626b\u63cf\u672c\u5730\u97f3\u4e50\u5931\u8d25",
                            hasScanned = true
                        )
                    }
                )
            }

            if (result.isSuccess) {
                loadedSongs?.let(likedTracksRepository::refreshLocalSongs)
                if (loadedSongs != null) {
                    refreshSearchIndex(
                        songs = loadedSongs,
                        albums = result.getOrNull()?.second.orEmpty()
                    ).join()
                }
                if (!preservePlayback) {
                    reconcileCurrentSongWithLibrary()
                    restoreFromControllerIfPossible()
                }
            }
            result.isSuccess
    }

    fun setLyricsDirectory(treeUri: Uri) {
        addLyricsFolder(treeUri)
    }

    fun addLyricsFolder(treeUri: Uri): Boolean {
        val added = localLyricsRepository.addLyricsDirectory(treeUri)
        if (!added) {
            return false
        }
        lyricsPreloadScheduler.clear()
        _lyricsFolders.value = localLyricsRepository.getLyricsFolders()
        lyricsReloadVersion.update { it + 1 }
        scheduleNextSongsPreload()
        return true
    }

    fun removeLyricsFolder(treeUri: Uri) {
        lyricsPreloadScheduler.clear()
        localLyricsRepository.removeLyricsDirectory(treeUri)
        _lyricsFolders.value = localLyricsRepository.getLyricsFolders()
        lyricsReloadVersion.update { it + 1 }
        scheduleNextSongsPreload()
    }

    fun refreshLyricsFolders() {
        _lyricsFolders.value = localLyricsRepository.getLyricsFolders()
    }

    fun handleLocalSongsDeleted(deletedSongs: List<Song>) {
        if (deletedSongs.isEmpty()) {
            return
        }
        val currentSong = playbackState.value.currentSong
        if (currentSong != null && deletedSongs.any { deletedSong ->
                isSameSong(deletedSong, currentSong)
            }
        ) {
            playbackController.clearPlayback()
            sourceQueue = emptyList()
            sourceTrackQueue = emptyList()
            playbackQueue = emptyList()
            playbackTrackQueue = emptyList()
            currentQueueIndex = -1
            currentPlaybackSource = PlaybackSource.Unknown
            publishPlaybackQueue()
        }
        scanSongs()
    }

    fun playSong(
        song: Song,
        source: PlaybackSource = PlaybackSource.LocalLibrary
    ) {
        val queue = _uiState.value.songs
        val songIndex = queue.indexOfFirst { it.id == song.id || it.uri == song.uri }
        if (songIndex == -1) {
            sourceQueue = listOf(song)
            sourceTrackQueue = listOf(QueueTrackEntry(song.toPersistentTrack(), song))
            playbackQueue = listOf(song)
            playbackTrackQueue = sourceTrackQueue
            playSongAt(index = 0, source = source)
            return
        }

        sourceQueue = queue
        sourceTrackQueue = queue.map { QueueTrackEntry(it.toPersistentTrack(), it) }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = song
        )
        val playbackIndex = findSongIndex(playbackQueue, song)
        playSongAt(index = playbackIndex, source = source)
    }

    fun playSongQueue(
        songs: List<Song>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.Unknown
    ) {
        if (songs.isEmpty() || startIndex !in songs.indices) {
            return
        }

        val startSong = songs[startIndex]
        sourceQueue = songs
        sourceTrackQueue = songs.map { QueueTrackEntry(it.toPersistentTrack(), it) }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = startSong
        )
        val playbackIndex = findSongIndex(playbackQueue, startSong)
        playSongAt(index = playbackIndex, source = source)
    }

    fun playProviderSong(song: ProviderSong) {
        val snapshot = _searchUiState.value.providerCategoryStates[ProviderSearchCategory.Single]
            ?.items.orEmpty()
            .filterIsInstance<ProviderSong>()
        val queue = snapshot.takeIf { results -> results.any { it.trackRef == song.trackRef } }
            ?: listOf(song)
        playProviderSongQueue(queue, queue.indexOfFirst { it.trackRef == song.trackRef }, PlaybackSource.Search)
    }

    fun playProviderSongQueue(
        queue: List<ProviderSong>,
        startIndex: Int,
        source: PlaybackSource
    ) {
        val selected = queue.getOrNull(startIndex) ?: return
        val displayQueue = queue.map(ProviderSong::toPresentationSong)
        val selectedDisplaySong = selected.toPresentationSong()
        sourceQueue = displayQueue
        sourceTrackQueue = queue.zip(displayQueue) { providerSong, presentation ->
            QueueTrackEntry(providerSong.toPersistentTrack(), presentation, providerSong)
        }
        onlineQueueSongs = queue.associateBy { providerSong ->
            providerSong.toPresentationSong().uri.toString()
        }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = selectedDisplaySong
        )
        val selectedIndex = findSongIndex(playbackQueue, selectedDisplaySong)
        if (selectedIndex == -1) return
        playSongAt(index = selectedIndex, source = source)
    }

    fun refreshLibrary(sourceKey: String) {
        if (sourceKey in _uiState.value.libraryRefreshingSources) return
        val providerId = sourceKey.removePrefix("extension:").takeIf { sourceKey.startsWith("extension:") && it.isNotBlank() }
        if (sourceKey != "local" && providerId == null) return
        _uiState.update { it.startLibraryRefresh(sourceKey) }
        val version = providerId?.let { id ->
            ((providerRefreshVersions[id] ?: 0L) + 1L).also { providerRefreshVersions[id] = it }
        }
        viewModelScope.launch {
            var success = false
            try {
                success = if (providerId == null) {
                    _uiState.first { !it.isLoading }
                    _uiState.value.hasPermission && performLocalScan(preservePlayback = true)
                } else {
                    refreshProviderLibrary(providerId, requireNotNull(version))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w("FlowtoneLibrary", "Library refresh failed for $sourceKey", error)
            } finally {
                _uiState.update { state ->
                    state.finishLibraryRefresh(
                        sourceKey,
                        if (success) null else ++libraryRefreshFailureSequence
                    )
                }
            }
        }
    }

    private suspend fun refreshProviderLibrary(providerId: String, version: Long): Boolean {
        val generation = extensionManager.runtimeState.value.generation
        if (!extensionManager.isMusicProviderRuntimeAvailable(providerId)) return false
        val capabilities = extensionManager.providerCapabilities(providerId)
        var success = true
        suspend fun <T> fetch(label: String, operation: suspend () -> T?): T? {
            return try {
                operation().also { if (it == null) success = false }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                success = false
                Log.w("FlowtoneLibrary", "Provider $providerId $label refresh failed", error)
                null
            }
        }
        val wantsSongs = AtomicCapabilityId.CatalogSongsList in capabilities
        val wantsAlbums = AtomicCapabilityId.CatalogAlbumsList in capabilities
        val wantsPlaylists = AtomicCapabilityId.UserPlaylistsList in capabilities
        val songs = if (wantsSongs) fetch("songs") { extensionManager.getProviderSongs(providerId, refresh = true) } else null
        val albums = if (wantsAlbums) fetch("albums") { extensionManager.getProviderAlbums(providerId, refresh = true) } else null
        val playlists = if (wantsPlaylists) fetch("playlists") { extensionManager.getProviderUserPlaylists(providerId) } else null
        val runtimeState = extensionManager.runtimeState.value
        if (runtimeState.isReloading || runtimeState.generation != generation || providerRefreshVersions[providerId] != version) return false
        _uiState.update { state ->
            state.copy(
                providerSongs = if (songs != null) state.providerSongs + (providerId to songs) else state.providerSongs,
                providerAlbums = if (albums != null) state.providerAlbums + (providerId to albums) else state.providerAlbums,
                providerUserPlaylists = if (playlists != null) state.providerUserPlaylists + (providerId to playlists) else state.providerUserPlaylists,
                providerSongCatalogLoadStates = if (wantsSongs) state.providerSongCatalogLoadStates +
                    (providerId to if (songs != null || providerId in state.providerSongs) ProviderCatalogLoadState.Loaded else ProviderCatalogLoadState.Failed) else state.providerSongCatalogLoadStates,
                providerAlbumCatalogLoadStates = if (wantsAlbums) state.providerAlbumCatalogLoadStates +
                    (providerId to if (albums != null || providerId in state.providerAlbums) ProviderCatalogLoadState.Loaded else ProviderCatalogLoadState.Failed) else state.providerAlbumCatalogLoadStates,
                providerUserPlaylistLoadStates = if (wantsPlaylists) state.providerUserPlaylistLoadStates +
                    (providerId to if (playlists != null || providerId in state.providerUserPlaylists) ProviderCatalogLoadState.Loaded else ProviderCatalogLoadState.Failed) else state.providerUserPlaylistLoadStates
            )
        }
        return success
    }

    fun loadProviderEntityCollections(providerId: String) {
        val normalizedProviderId = providerId.trim()
        if (normalizedProviderId.isEmpty()) return
        requestedProviderCollectionIds += normalizedProviderId
        if ("extension:$normalizedProviderId" in _uiState.value.libraryRefreshingSources) return
        val refreshVersion = providerRefreshVersions[normalizedProviderId] ?: 0L
        val capabilities = extensionManager.providerCapabilities(normalizedProviderId)
        val runtimeGeneration = extensionManager.runtimeState.value.generation

        if (AtomicCapabilityId.CatalogSongsList in capabilities) {
            val loadKey = "$runtimeGeneration:song:$normalizedProviderId"
            if (normalizedProviderId in _uiState.value.providerSongs) {
                _uiState.update { state ->
                    state.copy(
                        providerSongCatalogLoadStates = state.providerSongCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                    )
                }
            } else if (providerCollectionLoads.add(loadKey)) {
                _uiState.update { state ->
                    state.copy(
                        providerSongCatalogLoadStates = state.providerSongCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loading)
                    )
                }
                viewModelScope.launch {
                    var songs: List<ProviderSong>? = null
                    var failure: Throwable? = null
                    var cancelled = false
                    try {
                        songs = extensionManager.getProviderSongs(normalizedProviderId)
                    } catch (error: CancellationException) {
                        cancelled = true
                        throw error
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        providerCollectionLoads.remove(loadKey)
                        val runtimeState = extensionManager.runtimeState.value
                        if (!cancelled && !runtimeState.isReloading &&
                            runtimeState.generation == runtimeGeneration &&
                            (providerRefreshVersions[normalizedProviderId] ?: 0L) == refreshVersion
                        ) {
                            if (songs == null) {
                                reportExtensionContentFailure(
                                    title = "扩展歌曲列表读取失败",
                                    context = "扩展 ID：$normalizedProviderId",
                                    error = failure ?: IllegalStateException("扩展声明了歌曲列表能力，但没有返回结果。")
                                )
                            }
                            _uiState.update { state ->
                                if (songs == null) {
                                    state.copy(
                                        providerSongCatalogLoadStates =
                                            state.providerSongCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Failed)
                                    )
                                } else {
                                    state.copy(
                                        providerSongs = state.providerSongs +
                                            (normalizedProviderId to songs),
                                        providerSongCatalogLoadStates =
                                            state.providerSongCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (AtomicCapabilityId.CatalogAlbumsList in capabilities) {
            val loadKey = "$runtimeGeneration:album:$normalizedProviderId"
            if (normalizedProviderId in _uiState.value.providerAlbums) {
                _uiState.update { state ->
                    state.copy(
                        providerAlbumCatalogLoadStates = state.providerAlbumCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                    )
                }
            } else if (providerCollectionLoads.add(loadKey)) {
                _uiState.update { state ->
                    state.copy(
                        providerAlbumCatalogLoadStates = state.providerAlbumCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loading)
                    )
                }
                viewModelScope.launch {
                    var albums: List<ProviderAlbum>? = null
                    var failure: Throwable? = null
                    var cancelled = false
                    try {
                        albums = extensionManager.getProviderAlbums(normalizedProviderId)
                    } catch (error: CancellationException) {
                        cancelled = true
                        throw error
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        providerCollectionLoads.remove(loadKey)
                        val runtimeState = extensionManager.runtimeState.value
                        if (!cancelled && !runtimeState.isReloading &&
                            runtimeState.generation == runtimeGeneration &&
                            (providerRefreshVersions[normalizedProviderId] ?: 0L) == refreshVersion
                        ) {
                            if (albums == null) {
                                reportExtensionContentFailure(
                                    title = "扩展专辑列表读取失败",
                                    context = "扩展 ID：$normalizedProviderId",
                                    error = failure ?: IllegalStateException("扩展声明了专辑列表能力，但没有返回结果。")
                                )
                            }
                            _uiState.update { state ->
                                if (albums == null) {
                                    state.copy(
                                        providerAlbumCatalogLoadStates =
                                            state.providerAlbumCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Failed)
                                    )
                                } else {
                                    state.copy(
                                        providerAlbums = state.providerAlbums +
                                            (normalizedProviderId to albums),
                                        providerAlbumCatalogLoadStates =
                                            state.providerAlbumCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadProviderUserPlaylists(providerId: String) {
        val normalizedProviderId = providerId.trim()
        if (normalizedProviderId.isEmpty()) return
        requestedProviderUserPlaylistIds += normalizedProviderId
        if ("extension:$normalizedProviderId" in _uiState.value.libraryRefreshingSources) return
        val refreshVersion = providerRefreshVersions[normalizedProviderId] ?: 0L
        if (AtomicCapabilityId.UserPlaylistsList !in
            extensionManager.providerCapabilities(normalizedProviderId)
        ) return

        val runtimeGeneration = extensionManager.runtimeState.value.generation
        val loadKey = "$runtimeGeneration:user-playlists:$normalizedProviderId"
        if (normalizedProviderId in _uiState.value.providerUserPlaylists) {
            _uiState.update { state ->
                state.copy(
                    providerUserPlaylistLoadStates = state.providerUserPlaylistLoadStates +
                        (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                )
            }
        } else if (providerCollectionLoads.add(loadKey)) {
            _uiState.update { state ->
                state.copy(
                    providerUserPlaylistLoadStates = state.providerUserPlaylistLoadStates +
                        (normalizedProviderId to ProviderCatalogLoadState.Loading)
                )
            }
            viewModelScope.launch {
                var playlists: List<ProviderPlaylist>? = null
                var failure: Throwable? = null
                var cancelled = false
                try {
                    playlists = extensionManager.getProviderUserPlaylists(normalizedProviderId)
                } catch (error: CancellationException) {
                    cancelled = true
                    throw error
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    providerCollectionLoads.remove(loadKey)
                    val runtimeState = extensionManager.runtimeState.value
                    if (!cancelled && !runtimeState.isReloading &&
                        runtimeState.generation == runtimeGeneration &&
                        (providerRefreshVersions[normalizedProviderId] ?: 0L) == refreshVersion
                    ) {
                        if (playlists == null) {
                            reportExtensionContentFailure(
                                title = "扩展歌单读取失败",
                                context = "扩展 ID：$normalizedProviderId",
                                error = failure ?: IllegalStateException("扩展声明了用户歌单能力，但没有返回结果。")
                            )
                        }
                        _uiState.update { state ->
                            if (playlists == null) {
                                state.copy(
                                    providerUserPlaylistLoadStates =
                                        state.providerUserPlaylistLoadStates +
                                            (normalizedProviderId to ProviderCatalogLoadState.Failed)
                                )
                            } else {
                                state.copy(
                                    providerUserPlaylists = state.providerUserPlaylists +
                                        (normalizedProviderId to playlists),
                                    providerUserPlaylistLoadStates =
                                        state.providerUserPlaylistLoadStates +
                                            (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadProviderPlaylist(playlist: ProviderPlaylistSearchItem) {
        val detailKey = playlist.identity.stableKey
        requestedProviderPlaylists[detailKey] = playlist
        loadProviderPlaylist(playlist, preferEmbeddedSongs = true)
    }

    private fun loadProviderPlaylist(
        playlist: ProviderPlaylistSearchItem,
        preferEmbeddedSongs: Boolean
    ) {
        val detailKey = playlist.identity.stableKey
        if (detailKey in _uiState.value.providerPlaylistDetails) return
        if (preferEmbeddedSongs && playlist.songs.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    providerPlaylistDetails = state.providerPlaylistDetails +
                        (detailKey to ProviderPlaylistDetailState(songs = playlist.songs))
                )
            }
            return
        }
        val loadKey = "${extensionManager.runtimeState.value.generation}:playlist:$detailKey"
        if (!providerCollectionLoads.add(loadKey)) return
        _uiState.update { state ->
            state.copy(
                providerPlaylistDetails = state.providerPlaylistDetails +
                    (detailKey to ProviderPlaylistDetailState(isLoading = true))
            )
        }
        viewModelScope.launch {
            var songs: List<ProviderSong>? = null
            var failure: Throwable? = null
            var cancelled = false
            try {
                songs = extensionManager.getProviderPlaylistSongs(
                    playlist.providerId,
                    playlist.id
                )
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            } catch (error: Throwable) {
                failure = error
            } finally {
                providerCollectionLoads.remove(loadKey)
                if (!cancelled) {
                    val effectiveSongs = songs ?: playlist.songs.takeIf { it.isNotEmpty() }
                    val loadFailed = effectiveSongs == null
                    _uiState.update { state ->
                        state.copy(
                            providerPlaylistDetails = state.providerPlaylistDetails +
                                (detailKey to ProviderPlaylistDetailState(
                                    songs = effectiveSongs.orEmpty(),
                                    loadFailed = loadFailed
                                ))
                        )
                    }
                    if (failure != null || loadFailed) {
                        reportExtensionContentFailure(
                            title = "扩展歌单曲目读取失败",
                            context = "扩展 ID：${playlist.providerId}\n歌单 ID：${playlist.id}",
                            error = failure ?: IllegalStateException("扩展没有返回此歌单的曲目。")
                        )
                    }
                }
            }
        }
    }

    fun playQueueSong(song: Song) {
        val playbackIndex = findSongIndex(playbackQueue, song)
        if (playbackIndex != -1) {
            if (!playbackController.playAt(playbackIndex, playWhenReady = true)) {
                playSongAt(playbackIndex, source = currentPlaybackSource)
            }
        }
    }

    fun addSongsToNext(songs: List<Song>): Boolean {
        if (songs.isEmpty()) return false
        val currentSong = playbackState.value.currentSong
        if (currentSong == null) {
            playSongQueue(songs, 0, PlaybackSource.Unknown)
            return true
        }
        syncCurrentQueueIndex()
        val insertionIndex = (currentQueueIndex + 1).coerceIn(0, playbackQueue.size)
        if (!playbackController.addSongsNext(songs, currentPlaybackSource)) return false
        playbackQueue = playbackQueue.toMutableList().apply {
            addAll(insertionIndex, songs)
        }
        playbackTrackQueue = playbackTrackQueue.toMutableList().apply {
            addAll(insertionIndex, songs.map { QueueTrackEntry(it.toPersistentTrack(), it) })
        }
        sourceQueue = playbackQueue
        sourceTrackQueue = playbackTrackQueue
        publishPlaybackQueue()
        scheduleNextSongsPreload()
        return true
    }

    fun appendSongsToQueue(songs: List<Song>): Boolean {
        if (songs.isEmpty()) return false
        if (playbackState.value.currentSong == null) {
            playSongQueue(songs, 0, PlaybackSource.Unknown)
            return true
        }
        if (!playbackController.appendSongsToQueue(songs, currentPlaybackSource)) return false
        playbackQueue = playbackQueue + songs
        playbackTrackQueue = playbackTrackQueue + songs.map {
            QueueTrackEntry(it.toPersistentTrack(), it)
        }
        sourceQueue = playbackQueue
        sourceTrackQueue = playbackTrackQueue
        publishPlaybackQueue()
        scheduleNextSongsPreload()
        return true
    }

    private fun playSongAt(
        index: Int,
        source: PlaybackSource = currentPlaybackSource
    ) {
        if (playbackQueue.isEmpty() || index !in playbackQueue.indices) {
            currentQueueIndex = -1
            publishPlaybackQueue()
            return
        }

        val selectedEntry = playbackTrackQueue.getOrNull(index) ?: return
        beginPlaybackRequest()
        currentQueueIndex = index
        currentPlaybackSource = source
        val logicalItems = sourceTrackQueue.mapIndexed { sourceIndex, entry ->
            entry.toPlaybackQueueItem(source, sourceIndex)
        }
        val selectedSourceIndex = sourceTrackQueue.indexOfFirst { entry ->
            entry.stableIdentity == selectedEntry.stableIdentity
        }
        if (selectedSourceIndex == -1) return
        playbackController.playLogicalQueue(logicalItems, selectedSourceIndex, playWhenReady = true)
        publishPlaybackQueue()
        scheduleNextSongsPreload()
    }

    private fun syncCurrentQueueIndex() {
        val currentSong = playbackState.value.currentSong
        currentQueueIndex = if (currentSong == null) {
            -1
        } else {
            findSongIndex(playbackQueue, currentSong)
        }
    }

    private fun syncCurrentSongFromMediaId(mediaId: String) {
        val songIndex = playbackQueue.indexOfFirst { song ->
            song.id.toString() == mediaId || song.uri.toString() == mediaId
        }
        if (songIndex == -1) {
            return
        }

        currentQueueIndex = songIndex
        playbackController.updateCurrentSong(playbackQueue[songIndex])
        publishPlaybackQueue()
        scheduleNextSongsPreload()
    }

    private fun observeControllerConnection() {
        viewModelScope.launch {
            playbackController.isConnected.collect { connected ->
                if (connected) {
                    restoreFromControllerIfPossible()
                }
            }
        }
    }

    private fun observeListeningStats() {
        viewModelScope.launch {
            listeningStatsRepository.stats.collect { snapshot ->
                publishListeningStats(snapshot)
            }
        }
    }

    private fun observeLyrics() {
        viewModelScope.launch {
            combine(
                playbackState.map { it.currentSong }.distinctUntilChanged(),
                lyricsReloadVersion
            ) { song, version -> song to version }
                .collectLatest { song ->
                    val currentSong = song.first
                    if (currentSong == null) {
                        publishLyricsState(songId = null, state = LyricsState.Idle)
                        return@collectLatest
                    }

                    val request = localLyricsRepository.request(currentSong)
                    Log.d(
                        "Lyrics",
                        "foreground request song=${currentSong.id} source=${request.source}"
                    )
                    publishLyricsState(
                        songId = currentSong.id,
                        state = if (request.source == LyricsLoadSource.NewRead) {
                            LyricsState.Loading
                        } else {
                            LyricsState.Idle
                        }
                    )
                    val result = try {
                        request.deferred.await()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        LyricsLoadResult.Failed(error)
                    }

                    publishLyricsState(
                        songId = currentSong.id,
                        state = when (result) {
                            is LyricsLoadResult.Found -> LyricsState.Available(result.lines)
                            LyricsLoadResult.DirectoryNotSelected ->
                                LyricsState.DirectoryNotSelected
                            LyricsLoadResult.DirectoryPermissionLost ->
                                LyricsState.DirectoryPermissionLost
                            LyricsLoadResult.OutsideSelectedDirectory ->
                                LyricsState.OutsideSelectedDirectory
                            LyricsLoadResult.NotFound -> LyricsState.NotFound
                            is LyricsLoadResult.Failed -> {
                                Log.d(
                                    "Lyrics",
                                    "failed=${result.throwable::class.simpleName}"
                                )
                                LyricsState.Error(result.throwable.message)
                            }
                        }
                    )
                }
        }
    }

    private fun publishLyricsState(songId: Long?, state: LyricsState) {
        _songLyricsState.value = SongLyricsState(songId = songId, state = state)
        _lyricsState.value = state
    }

    private fun restoreFromControllerIfPossible() {
        val snapshot = playbackController.getPlaybackSnapshot() ?: return
        if (snapshot.logicalQueue.isNotEmpty()) {
            sourceTrackQueue = snapshot.sourceQueue.map(PlaybackQueueItem::toQueueTrackEntry)
            playbackTrackQueue = snapshot.logicalQueue.map(PlaybackQueueItem::toQueueTrackEntry)
            sourceQueue = sourceTrackQueue.map(QueueTrackEntry::presentation)
            playbackQueue = playbackTrackQueue.map(QueueTrackEntry::presentation)
            currentQueueIndex = snapshot.currentMediaItemIndex
                .coerceIn(0, playbackQueue.lastIndex.coerceAtLeast(0))
            currentPlaybackSource = snapshot.logicalQueue
                .getOrNull(currentQueueIndex)
                ?.source
                ?: PlaybackSource.Unknown
            publishPlaybackQueue()
            scheduleNextSongsPreload()
            return
        }
        val currentMediaItem = snapshot.currentMediaItem ?: return
        val scannedSongs = _uiState.value.songs
        val currentSong = currentMediaItem.toSongOrNull(scannedSongs) ?: return
        currentPlaybackSource = currentMediaItem.toPlaybackSource()

        val restoredQueue = if (snapshot.queueMediaItems.isNotEmpty()) {
            snapshot.queueMediaItems.mapNotNull { mediaItem ->
                mediaItem.toSongOrNull(scannedSongs)
            }
        } else {
            listOf(currentSong)
        }
        if (restoredQueue.isEmpty()) {
            return
        }

        sourceQueue = restoredQueue
        playbackQueue = buildPlaybackQueueForMode(
            queue = sourceQueue,
            mode = snapshot.playbackOrderMode,
            currentSong = currentSong
        )
        currentQueueIndex = findSongIndex(playbackQueue, currentSong)
            .takeIf { it != -1 } ?: 0

        val duration = when {
            snapshot.durationMs > 0L -> snapshot.durationMs
            currentSong.durationMs > 0L -> currentSong.durationMs
            else -> 0L
        }
        val position = if (duration > 0L) {
            snapshot.positionMs.coerceIn(0L, duration)
        } else {
            0L
        }

        playbackController.updateFromSnapshot(
            currentSong = currentSong,
            isPlaying = snapshot.isPlaying,
            playWhenReady = snapshot.playWhenReady,
            isBuffering = snapshot.isBuffering,
            positionMs = position,
            bufferedPositionMs = snapshot.bufferedPositionMs.coerceIn(0L, duration),
            durationMs = duration,
            playbackOrderMode = snapshot.playbackOrderMode
        )
        publishPlaybackQueue()
        scheduleNextSongsPreload()
    }

    private fun reconcileCurrentSongWithLibrary() {
        val scannedSongs = _uiState.value.songs
        val currentSong = playbackState.value.currentSong ?: return
        val officialSong = scannedSongs.firstOrNull {
            it.id == currentSong.id || it.uri == currentSong.uri
        } ?: return

        sourceQueue = sourceQueue.map { queuedSong ->
            scannedSongs.firstOrNull { it.id == queuedSong.id || it.uri == queuedSong.uri }
                ?: queuedSong
        }.ifEmpty {
            scannedSongs
        }
        playbackQueue = playbackQueue.map { queuedSong ->
            scannedSongs.firstOrNull { it.id == queuedSong.id || it.uri == queuedSong.uri }
                ?: queuedSong
        }
        currentQueueIndex = playbackQueue.indexOfFirst {
            it.id == officialSong.id || it.uri == officialSong.uri
        }
        playbackController.updateFromSnapshot(
            currentSong = officialSong,
            isPlaying = playbackState.value.isPlaying,
            playWhenReady = playbackState.value.playWhenReady,
            isBuffering = playbackState.value.isBuffering,
            positionMs = playbackState.value.positionMs,
            bufferedPositionMs = playbackState.value.bufferedPositionMs,
            durationMs = playbackState.value.durationMs.takeIf { it > 0L }
                ?: officialSong.durationMs.coerceAtLeast(0L),
            playbackOrderMode = playbackState.value.playbackOrderMode
        )
        publishPlaybackQueue()
        scheduleNextSongsPreload()
    }

    private fun publishPlaybackQueue() {
        val hydratedByIdentity = (sourceTrackQueue + playbackTrackQueue)
            .mapNotNull { entry -> entry.runtimeProviderSong?.let { entry.stableIdentity to it } }
            .toMap()
        val controllerState = playbackState.value
        val sourceItems = controllerState.sourceQueue.takeIf { it.isNotEmpty() }
            ?.map { item ->
                hydratedByIdentity[item.stableIdentity]
                    ?.let { item.copy(runtimeProviderSong = it) }
                    ?: item
            }
            ?: sourceTrackQueue.mapIndexed { index, entry ->
                entry.toPlaybackQueueItem(currentPlaybackSource, index)
            }
        val playbackItems = controllerState.playbackQueue.takeIf { it.isNotEmpty() }
            ?.map { item ->
                hydratedByIdentity[item.stableIdentity]
                    ?.let { item.copy(runtimeProviderSong = it) }
                    ?: item
            }
            ?: playbackTrackQueue.mapIndexed { index, entry ->
                entry.toPlaybackQueueItem(currentPlaybackSource, index)
            }
        _uiState.update {
            it.copy(
                sourceQueue = sourceQueue,
                playbackQueue = playbackQueue,
                sourceQueueItems = sourceItems,
                playbackQueueItems = playbackItems,
                currentQueueIndex = currentQueueIndex
            )
        }
    }

    private fun scheduleNextSongsPreload() {
        val metadataSongs = songsInPlaybackPreloadWindow(preloadSongMetadataCount)
        val nextMetadataPreloadUris = metadataSongs.map { it.uri.toString() }
        if (nextMetadataPreloadUris != metadataPreloadUris) {
            metadataPreloadUris = nextMetadataPreloadUris
            preloadJob?.cancel()
            preloadJob = viewModelScope.launch {
                songMetadataPreloader.preload(metadataSongs)
            }
        }

        val songsToPreload = songsInPlaybackPreloadWindow(preloadLyricsCount)
        lyricsPreloadScheduler.update(
            currentIndex = playbackController.getCurrentMediaItemIndex() ?: currentQueueIndex,
            currentSong = playbackState.value.currentSong
                ?: playbackQueue.getOrNull(currentQueueIndex),
            songsInPlaybackOrder = songsToPreload
        )
        scheduleOnlineArtworkPreload()
        // Service owns online playback-resource/content preload from the logical next item.
    }

    /**
     * 在线预载只 hydrate 元数据并交给现有 ExtensionImage Coil 管线；绝不请求播放资源。
     * 先完整调度普通封面，再调度大图，避免大图抢占下一首缩略图。
     */
    private fun scheduleOnlineArtworkPreload() {
        onlineArtworkPreloadJob?.cancel()
        val generation = ++onlinePreloadGeneration
        val entries = queueItemsInPreloadWindow(
            items = playbackTrackQueue,
            currentIndex = currentQueueIndex,
            totalCount = preloadSongMetadataCount
        )
            .filter { it.persistentTrack is PersistentTrack.Online || it.runtimeProviderSong != null }
        Log.d(
            "FlowtonePlayback",
            "online.artwork.preload.window currentIndex=$currentQueueIndex count=$preloadSongMetadataCount entries=${entries.size}"
        )
        if (entries.isEmpty()) return
        onlineArtworkPreloadJob = viewModelScope.launch {
            val hydratedEntries = entries.mapNotNull { entry ->
                if (generation != onlinePreloadGeneration) {
                    Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=staleGeneration")
                    return@launch
                }
                val song = entry.runtimeProviderSong ?: hydrateOnlinePresentation(
                    entry.persistentTrack as? PersistentTrack.Online
                ).also { hydrated ->
                    if (hydrated == null) Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=hydrationFailed")
                }
                song?.let { entry to it }
            }
            if (generation != onlinePreloadGeneration) return@launch
            val hydratedByIdentity = hydratedEntries.associate { (entry, song) ->
                entry.stableIdentity to song
            }
            if (hydratedByIdentity.isNotEmpty()) {
                sourceTrackQueue = sourceTrackQueue.map { entry ->
                    hydratedByIdentity[entry.stableIdentity]
                        ?.let { entry.copy(runtimeProviderSong = it) }
                        ?: entry
                }
                playbackTrackQueue = playbackTrackQueue.map { entry ->
                    hydratedByIdentity[entry.stableIdentity]
                        ?.let { entry.copy(runtimeProviderSong = it) }
                        ?: entry
                }
                publishPlaybackQueue()
            }
            val songs = hydratedEntries.map { it.second }
            songs.forEach { song ->
                song.artwork?.let { image ->
                    Log.d("FlowtonePlayback", "online.artwork.preload.small identityHash=${song.trackRef.opaqueId.hashCode()}")
                    extensionManager.extensionImageLoader.execute(
                        ImageRequest.Builder(getApplication()).data(image).build()
                    )
                } ?: Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=noArtwork")
            }
            if (generation != onlinePreloadGeneration) return@launch
            songs.forEach { song ->
                song.largeArtwork?.let { image ->
                    Log.d("FlowtonePlayback", "online.artwork.preload.large identityHash=${song.trackRef.opaqueId.hashCode()}")
                    extensionManager.extensionImageLoader.execute(
                        ImageRequest.Builder(getApplication()).data(image).build()
                    )
                } ?: Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=noLargeArtwork")
            }
        }
    }

    private fun loadSearchLandingForCurrentScope() {
        searchLandingJob?.cancel()
        val generation = ++searchLandingGeneration
        val scope = _searchUiState.value.scope
        if (scope !is SearchScope.Provider) {
            _searchUiState.update { it.copy(landingState = ProviderSearchLandingState.Idle) }
            return
        }
        _searchUiState.update { it.copy(landingState = ProviderSearchLandingState.Loading) }
        searchLandingJob = viewModelScope.launch {
            try {
                val landing = extensionManager.getSearchLanding(scope.extensionId)
                _searchUiState.update { current ->
                    if (generation == searchLandingGeneration && current.scope == scope && current.query.isBlank) {
                        current.copy(landingState = ProviderSearchLandingState.Loaded(landing))
                    } else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == searchLandingGeneration &&
                    _searchUiState.value.scope == scope &&
                    _searchUiState.value.query.isBlank
                ) {
                    reportExtensionContentFailure(
                        title = "扩展推荐内容读取失败",
                        context = "扩展 ID：${scope.extensionId}",
                        error = error
                    )
                    _searchUiState.update { current ->
                        if (generation == searchLandingGeneration && current.scope == scope && current.query.isBlank) {
                            current.copy(landingState = ProviderSearchLandingState.Error(error.stackTraceToString()))
                        } else current
                    }
                }
            }
        }
    }

    /** presentation hydration 与播放路径分离，失败仅保留 placeholder。 */
    private suspend fun hydrateOnlinePresentation(track: PersistentTrack.Online?): ProviderSong? {
        track ?: return null
        onlinePresentationCache[track.identityKey]?.let { return it }
        Log.d("FlowtonePlayback", "online.hydration.started source=${track.sourceHost}")
        val result = extensionManager.resolvePersistentPlaylistSong(track)
        val song = (result as? ink.tenqui.flowtone.data.online.PersistentSongResolution.Resolved)?.song
        if (song != null) {
            onlinePresentationCache[track.identityKey] = song
            Log.d("FlowtonePlayback", "online.hydration.success source=${track.sourceHost}")
        } else {
            Log.d("FlowtonePlayback", "online.hydration.failed source=${track.sourceHost}")
        }
        return song
    }

    private fun upcomingSongsInPlaybackOrder(limit: Int): List<Song> {
        if (limit <= 0 || playbackState.value.playbackOrderMode == PlaybackOrderMode.RepeatOne) {
            return emptyList()
        }

        val mediaIds = playbackController.getUpcomingMediaIdsInPlaybackOrder(limit)
        if (mediaIds != null) {
            return mediaIds.mapNotNull { mediaId ->
                val songId = mediaId.toLongOrNull()
                sourceQueue.firstOrNull { it.id == songId }
                    ?: playbackQueue.firstOrNull { it.id == songId }
            }
        }

        val startIndex = currentQueueIndex + 1
        return if (startIndex in playbackQueue.indices) {
            playbackQueue.drop(startIndex).take(limit)
        } else {
            emptyList()
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    private fun observeLikedTracks() {
        viewModelScope.launch {
            likedTracksRepository.tracks.collect { tracks ->
                _likedTracks.value = tracks
                _uiState.update { it.copy(likedTracks = tracks) }
            }
        }
    }

    private fun observeLogicalQueue() {
        viewModelScope.launch {
            playbackState
                .map { state ->
                    LogicalQueueSnapshot(
                        sourceQueue = state.sourceQueue,
                        playbackQueue = state.playbackQueue,
                        currentIndex = state.currentQueueIndex
                    )
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    if (snapshot.playbackQueue.isEmpty()) return@collect
                    sourceTrackQueue = snapshot.sourceQueue.map(PlaybackQueueItem::toQueueTrackEntry)
                    playbackTrackQueue = snapshot.playbackQueue.map(PlaybackQueueItem::toQueueTrackEntry)
                    sourceQueue = sourceTrackQueue.map(QueueTrackEntry::presentation)
                    playbackQueue = playbackTrackQueue.map(QueueTrackEntry::presentation)
                    currentQueueIndex = snapshot.currentIndex
                    onlineQueueSongs = sourceTrackQueue.mapNotNull { entry ->
                        entry.runtimeProviderSong?.let { entry.presentation.uri.toString() to it }
                    }.toMap()
                    publishPlaybackQueue()
                    scheduleNextSongsPreload()
                }
        }
    }

    private fun confirmedPlaybackQueueIndex(): Int {
        val confirmedSong = playbackState.value.currentSong ?: return -1
        return findSongIndex(playbackQueue, confirmedSong)
    }

    fun setTrackLiked(track: PersistentTrack, liked: Boolean) {
        likedTracksRepository.setLiked(track, liked)
    }

    fun playPersistentTrackQueue(
        tracks: List<PersistentTrack>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.Unknown
    ) {
        if (startIndex !in tracks.indices) return
        val entries = tracks.mapNotNull(::queueEntryForPersistentTrack)
        val selectedIdentity = tracks[startIndex].identityKey
        val selectedEntry = entries.firstOrNull {
            it.persistentTrack?.identityKey == selectedIdentity
        } ?: run {
            publishTrackPlaybackError("该本地歌曲已不可用")
            return
        }
        sourceTrackQueue = entries.toList()
        sourceQueue = entries.map(QueueTrackEntry::presentation)
        rebuildPlaybackQueueForMode(
            playbackState.value.playbackOrderMode,
            selectedEntry.presentation
        )
        val index = playbackTrackQueue.indexOfFirst {
            it.persistentTrack?.identityKey == selectedIdentity
        }
        playSongAt(index, source)
    }

    fun playPersistentTrack(track: PersistentTrack) = playPersistentTrackQueue(listOf(track), 0)

    private fun queueEntryForPersistentTrack(track: PersistentTrack): QueueTrackEntry? = when (track) {
        is PersistentTrack.Local -> _uiState.value.songs
            .firstOrNull { it.id.toString() == track.songId }
            ?.let { QueueTrackEntry(track, it) }
        is PersistentTrack.Online -> QueueTrackEntry(
            track,
            requireNotNull(track.toPresentationSong(emptyList()))
        )
    }

    private fun previousSongsInPlaybackOrder(limit: Int): List<Song> {
        if (limit <= 0 || playbackState.value.playbackOrderMode == PlaybackOrderMode.RepeatOne) {
            return emptyList()
        }

        val mediaIds = playbackController.getPreviousMediaIdsInPlaybackOrder(limit)
        if (mediaIds != null) {
            return mediaIds.mapNotNull { mediaId ->
                val songId = mediaId.toLongOrNull()
                sourceQueue.firstOrNull { it.id == songId }
                    ?: playbackQueue.firstOrNull { it.id == songId }
            }
        }

        return queueItemsBefore(playbackQueue, currentQueueIndex, limit)
    }

    private fun songsInPlaybackPreloadWindow(totalCount: Int): List<Song> {
        val counts = preloadWindowCounts(totalCount)
        return (
            upcomingSongsInPlaybackOrder(counts.next) +
                previousSongsInPlaybackOrder(counts.previous)
            ).distinctBy { it.uri.toString() }
    }

    private fun publishTrackPlaybackError(message: String) {
        val confirmedQueueIndex = confirmedPlaybackQueueIndex()
        _uiState.update { state ->
            state.copy(
                trackPlaybackErrorMessage = message,
                trackPlaybackErrorEventId = state.trackPlaybackErrorEventId + 1L,
                currentQueueIndex = confirmedQueueIndex
            )
        }
    }

    fun toggleCurrentTrackLiked(): Boolean {
        val track = playbackState.value.currentTrack ?: return false
        val liked = _likedTracks.value.any { it.identityKey == track.identityKey }
        setTrackLiked(track, !liked)
        return true
    }

    fun togglePlaybackOrderMode() {
        val currentMode = playbackState.value.playbackOrderMode
        val nextMode = when (currentMode) {
            PlaybackOrderMode.Sequence -> PlaybackOrderMode.RepeatOne
            PlaybackOrderMode.RepeatOne -> PlaybackOrderMode.Shuffle
            PlaybackOrderMode.Shuffle -> PlaybackOrderMode.Sequence
        }
        playbackOrderModeJob?.cancel()
        playbackOrderModeJob = viewModelScope.launch {
            applyPlaybackOrderMode(nextMode)
        }
    }

    private suspend fun applyPlaybackOrderMode(mode: PlaybackOrderMode) {
        if (mode == playbackState.value.playbackOrderMode) {
            return
        }
        playbackController.setPlaybackOrderMode(mode)
        playbackSettingsStore.setPlaybackOrderMode(mode)
    }

    fun seekTo(positionMs: Long) {
        val durationMs = playbackState.value.durationMs
        val clampedPosition = if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            0L
        }

        playbackController.seekTo(clampedPosition)
        playbackController.updateProgress(
            positionMs = clampedPosition,
            bufferedPositionMs = playbackState.value.bufferedPositionMs,
            durationMs = durationMs
        )
    }

    fun playNext() {
        playNext(playWhenReady = true)
    }

    private fun handlePlaybackEnded() {
        playNext(playWhenReady = false)
    }

    private fun playNext(playWhenReady: Boolean) {
        if (playbackController.playNext(playWhenReady = playWhenReady)) {
            return
        }

        syncCurrentQueueIndex()
        if (playbackQueue.isEmpty() || currentQueueIndex !in playbackQueue.indices) {
            return
        }

        val nextIndex = currentQueueIndex + 1
        if (nextIndex !in playbackQueue.indices) {
            return
        }

        playSongAt(index = nextIndex)
    }

    fun playPrevious() {
        if (playbackController.playPrevious(playWhenReady = true)) {
            return
        }

        syncCurrentQueueIndex()
        if (playbackQueue.isEmpty() || currentQueueIndex !in playbackQueue.indices) {
            return
        }

        val previousIndex = currentQueueIndex - 1
        if (previousIndex !in playbackQueue.indices) {
            return
        }

        playSongAt(index = previousIndex)
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (isActive) {
                updateProgressFromController()
                delay(32L)
            }
        }
    }

    private fun startPlaybackOrderTicker() {
        viewModelScope.launch {
            while (isActive) {
                syncPlaybackOrderModeFromController()
                delay(500L)
            }
        }
    }

    private fun startConfirmedPlaybackPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                _confirmedPlaybackPosition.value =
                    playbackController.getCurrentPositionSnapshot()
                delay(100)
            }
        }
    }

    private fun updateProgressFromController() {
        val currentSong = playbackState.value.currentSong
        if (currentSong == null) {
            playbackController.updateProgress(
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = 0L
            )
            return
        }

        val controllerSnapshot = playbackController.getProgressSnapshot()
        val controllerDuration = controllerSnapshot.durationMs
            .takeIf { controllerSnapshot.belongsToCurrentLogicalTarget }
            ?: 0L
        val duration = when {
            controllerDuration > 0L -> controllerDuration
            currentSong.durationMs > 0L -> currentSong.durationMs
            else -> 0L
        }
        val position = if (duration > 0L && controllerSnapshot.belongsToCurrentLogicalTarget) {
            controllerSnapshot.positionMs.coerceIn(0L, duration)
        } else {
            0L
        }
        val bufferedPosition = if (duration > 0L && controllerSnapshot.belongsToCurrentLogicalTarget) {
            controllerSnapshot.bufferedPositionMs.coerceIn(0L, duration)
        } else {
            0L
        }

        playbackController.updateProgress(
            positionMs = position,
            bufferedPositionMs = bufferedPosition,
            durationMs = duration
        )
        val now = SystemClock.elapsedRealtime()
        if (
            lastProgressDiagnosticLogUptimeMs == 0L ||
            now - lastProgressDiagnosticLogUptimeMs >= ProgressDiagnosticLogIntervalMillis
        ) {
            lastProgressDiagnosticLogUptimeMs = now
            val state = playbackState.value
            Log.d(
                ProgressDiagnosticLogTag,
                "ticker controllerMediaId=${controllerSnapshot.mediaId} " +
                    "controllerPositionMs=${controllerSnapshot.positionMs} " +
                    "controllerBufferedPositionMs=${controllerSnapshot.bufferedPositionMs} " +
                    "controllerDurationMs=${controllerSnapshot.durationMs} " +
                    "belongsToCurrent=${controllerSnapshot.belongsToCurrentLogicalTarget} " +
                    "stateQueueId=${state.currentQueueId} statePositionMs=${state.positionMs} " +
                    "stateBufferedPositionMs=${state.bufferedPositionMs} " +
                    "stateDurationMs=${state.durationMs}"
            )
        }
    }

    private fun syncPlaybackOrderModeFromController() {
        val playbackOrderMode = playbackController.getPlaybackOrderMode()
        if (playbackState.value.playbackOrderMode != playbackOrderMode) {
            playbackController.updatePlaybackOrderMode(playbackOrderMode)
            playbackSettingsStore.setPlaybackOrderMode(playbackOrderMode)
        }
    }

    private fun publishListeningStats(snapshot: ListeningStatsSnapshot) {
        _uiState.update {
            it.copy(listeningStats = snapshot)
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        providerSearchJob?.cancel()
        playbackOrderModeJob?.cancel()
        lyricsPreloadScheduler.clear()
        localLyricsRepository.close()
        playbackController.release()
        super.onCleared()
    }
}

private const val ProgressDiagnosticLogTag = "FlowtoneProgress"
private const val ProgressDiagnosticLogIntervalMillis = 2_000L

internal data class QueueTrackEntry(
    val persistentTrack: PersistentTrack?,
    val presentation: Song,
    val runtimeProviderSong: ProviderSong? = null
)

private data class LogicalQueueSnapshot(
    val sourceQueue: List<PlaybackQueueItem>,
    val playbackQueue: List<PlaybackQueueItem>,
    val currentIndex: Int
)

private val QueueTrackEntry.stableIdentity: String
    get() = persistentTrack?.identityKey
        ?: runtimeProviderSong?.trackRef?.let { "runtime:${it.extensionId}:${it.opaqueId}" }
        ?: "song:${presentation.sourceType}:${presentation.id}:${presentation.uri}"

private fun QueueTrackEntry.toPlaybackQueueItem(
    source: PlaybackSource,
    sourceIndex: Int
): PlaybackQueueItem = when {
    runtimeProviderSong != null -> PlaybackQueueItem.provider(
        song = runtimeProviderSong,
        presentation = presentation,
        source = source,
        sourceIndex = sourceIndex
    )
    persistentTrack != null -> PlaybackQueueItem.persistent(
        track = persistentTrack,
        presentation = presentation,
        source = source,
        sourceIndex = sourceIndex
    )
    else -> PlaybackQueueItem.local(presentation, source, sourceIndex)
}

private fun PlaybackQueueItem.toQueueTrackEntry(): QueueTrackEntry = QueueTrackEntry(
    persistentTrack = persistentTrack,
    presentation = presentation,
    runtimeProviderSong = runtimeProviderSong
)

internal data class PreloadWindowCounts(
    val previous: Int,
    val next: Int
)

internal fun preloadWindowCounts(totalCount: Int): PreloadWindowCounts {
    val safeTotalCount = totalCount.coerceAtLeast(0)
    val previousCount = (safeTotalCount * 0.2f).roundToInt()
    return PreloadWindowCounts(
        previous = previousCount,
        next = safeTotalCount - previousCount
    )
}

internal fun <T> queueItemsInPreloadWindow(
    items: List<T>,
    currentIndex: Int,
    totalCount: Int
): List<T> {
    val counts = preloadWindowCounts(totalCount)
    val nextItems = if (currentIndex + 1 in items.indices) {
        items.drop(currentIndex + 1).take(counts.next)
    } else {
        emptyList()
    }
    return nextItems + queueItemsBefore(items, currentIndex, counts.previous)
}

private fun <T> queueItemsBefore(
    items: List<T>,
    currentIndex: Int,
    limit: Int
): List<T> {
    if (limit <= 0 || currentIndex <= 0 || currentIndex > items.size) return emptyList()
    return (currentIndex - 1 downTo 0)
        .take(limit)
        .map(items::get)
}

private fun ProviderSong.toPersistentTrack(): PersistentTrack.Online? {
    val identity = persistentTrackRef ?: return null
    return PersistentTrack.Online(
        sourceHost = identity.sourceHost,
        persistentId = identity.persistentId,
        cachedTitle = title,
        cachedArtist = artist,
        cachedDurationMs = durationMs
    )
}
