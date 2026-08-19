package ink.tenqui.flowtone.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.request.ImageRequest
import ink.tenqui.flowtone.data.local.AudioScanner
import ink.tenqui.flowtone.data.local.LikedSongsStore
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
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderSearchCallResult
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import ink.tenqui.flowtone.core.model.SourceType
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
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import ink.tenqui.flowtone.playback.PlaybackState
import ink.tenqui.flowtone.playback.toPlaybackSource
import ink.tenqui.flowtone.playback.toSongOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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

data class MusicUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val sourceQueue: List<Song> = emptyList(),
    val playbackQueue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null,
    val hasScanned: Boolean = false,
    val listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot(),
    val likedTracks: List<PersistentTrack> = emptyList(),
    val trackPlaybackErrorMessage: String? = null,
    val trackPlaybackErrorEventId: Long = 0L,
    /** 在线请求尚未被播放器确认；绝不能覆盖 confirmed PlaybackState。 */
    val pendingPlayback: PendingPlayback? = null,
    val pendingQueueIndex: Int? = null
)

data class PendingPlayback(
    val track: PersistentTrack?,
    val presentation: Song,
    val requestGeneration: Long,
    val phase: Phase
) {
    enum class Phase { Resolving, Preparing }
}

private fun ProviderSong.toOnlineDisplaySong(): Song {
    val opaqueUri = Uri.Builder()
        .scheme("flowtone-extension")
        .authority("track")
        .appendPath(trackRef.extensionId)
        .appendPath(trackRef.opaqueId)
        .build()
    return Song(
        id = -((trackRef.extensionId + ":" + trackRef.opaqueId).hashCode().toLong().let { kotlin.math.abs(it) + 1L }),
        sourceType = SourceType.Online,
        title = title,
        artist = artist,
        durationMs = durationMs ?: 0L,
        uri = opaqueUri,
        displayName = title
    )
}

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

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepository = MusicRepository(
        localMusicRepository = LocalMusicRepository(
            audioScanner = AudioScanner(application)
        )
    )
    private val playbackSettingsStore = PlaybackSettingsStore(application)
    private val likedSongsStore = LikedSongsStore(application)
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
    private val initialLikedTracks = likedSongsStore.loadLikedTracks(emptyList())
    private val _uiState = MutableStateFlow(
        MusicUiState(
            listeningStats = listeningStatsRepository.getStats(),
            likedTracks = initialLikedTracks
        )
    )
    private val _searchUiState = MutableStateFlow(GlobalSearchUiState())
    private val providerSearchCoordinator = ProviderSearchCoordinator(
        state = _searchUiState,
        gateway = ExtensionManagerProviderSearchGateway(extensionManager)
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
    private var pendingPlaybackJob: Job? = null
    private var playbackRequestGeneration: Long = 0L
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
    private var currentPlaybackSource: PlaybackSource = PlaybackSource.Unknown

    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    val searchUiState: StateFlow<GlobalSearchUiState> = _searchUiState.asStateFlow()
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()
    val likedTracks: StateFlow<List<PersistentTrack>> = _likedTracks.asStateFlow()
    val songLyricsState: StateFlow<SongLyricsState> = _songLyricsState.asStateFlow()
    val confirmedPlaybackPosition: StateFlow<PlaybackPositionSnapshot> =
        _confirmedPlaybackPosition.asStateFlow()
    val lyricsFolders: StateFlow<List<LyricsFolder>> = _lyricsFolders.asStateFlow()

    private fun beginPlaybackRequest(): Long {
        pendingPlaybackJob?.cancel()
        pendingPlaybackJob = null
        playbackRequestGeneration += 1L
        playbackController.clearPendingPlaybackRequest()
        // 本地请求不依赖旧在线协程结束：同一帧撤销其视觉 pending。
        _uiState.update { it.copy(pendingPlayback = null, pendingQueueIndex = null) }
        return playbackRequestGeneration
    }

    private fun isCurrentPlaybackRequest(generation: Long) =
        generation == playbackRequestGeneration

    private fun setPendingPlayback(
        track: PersistentTrack?,
        presentation: Song,
        index: Int,
        generation: Long,
        phase: PendingPlayback.Phase
    ) {
        if (!isCurrentPlaybackRequest(generation)) return
        _uiState.update {
            it.copy(
                pendingPlayback = PendingPlayback(track, presentation, generation, phase),
                pendingQueueIndex = index
            )
        }
    }

    private fun clearPendingPlayback(generation: Long) {
        if (!isCurrentPlaybackRequest(generation)) return
        _uiState.update { state ->
            if (state.pendingPlayback?.requestGeneration == generation) {
                state.copy(pendingPlayback = null, pendingQueueIndex = null)
            } else state
        }
    }

    init {
        startProgressTicker()
        startConfirmedPlaybackPositionTicker()
        observeControllerConnection()
        observeListeningStats()
        observeLyrics()
        refreshSearchSources()
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
        val query = SearchQuery.from(queryText)
        searchJob?.cancel()
        providerSearchJob?.cancel()

        if (query.isBlank) {
            _searchUiState.update { current ->
                current.copy(
                    queryText = queryText,
                    isSearching = false,
                    songResults = emptyList(),
                    artistResults = emptyList(),
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
                isSearching = currentState.scope == SearchScope.All || currentState.scope == SearchScope.Local,
                songResults = emptyList(),
                artistResults = emptyList(),
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                searchGeneration = currentState.searchGeneration + 1
            )
        }
        searchJob = viewModelScope.launch {
            delay(200)
            val scope = _searchUiState.value.scope
            publishLocalSearchResults(query = query, visibleQueryText = queryText, scope = scope)
            loadInitialProviderSearchPage()
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
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                searchGeneration = current.searchGeneration + 1
            )
        }
        loadSearchLandingForCurrentScope()
    }

    fun refreshSearchSources() {
        val providers = extensionManager.availableMusicProviderOptions()
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
        _searchUiState.update {
            it.copy(
                scope = validScope,
                songResults = emptyList(),
                artistResults = emptyList(),
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                isSearching = false,
                searchGeneration = it.searchGeneration + 1
            )
        }
        val currentQuery = _searchUiState.value.queryText
        if (SearchQuery.from(currentQuery).isBlank) loadSearchLandingForCurrentScope()
        else updateSearchQuery(currentQuery)
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
                      artistResults = results.artists
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

    private fun loadInitialProviderSearchPage() {
        providerSearchJob = viewModelScope.launch { providerSearchCoordinator.loadInitial() }
    }

    private fun refreshSearchIndex(songs: List<Song>) {
        viewModelScope.launch {
            searchRepository.updateLocalSongs(songs)
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    musicRepository.loadLocalSongs()
                }
            }
            val loadedSongs = result.getOrNull()

            _uiState.update { currentState ->
                result.fold(
                    onSuccess = { songs ->
                        sourceQueue = songs
                        sourceTrackQueue = songs.map { song ->
                            QueueTrackEntry(song.toPersistentTrack(), song)
                        }
                        rebuildPlaybackQueueForMode(
                            mode = playbackState.value.playbackOrderMode,
                            currentSong = playbackState.value.currentSong
                        )
                        currentState.copy(
                            isLoading = false,
                            songs = songs,
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
                loadedSongs?.let { songs ->
                    _likedTracks.value = likedSongsStore.loadLikedTracks(songs)
                    likedSongsStore.saveLikedTracks(_likedTracks.value)
                    _uiState.update { it.copy(likedTracks = _likedTracks.value) }
                }
                loadedSongs?.let(::refreshSearchIndex)
                reconcileCurrentSongWithLibrary()
                restoreFromControllerIfPossible()
            }
        }
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
        val queue = snapshot.takeIf { results -> results.any { it.trackRef == song.trackRef } }
            ?: listOf(song)
        playProviderSongQueue(queue, song)
    }

    private fun playProviderSongQueue(queue: List<ProviderSong>, selected: ProviderSong) {
        val displayQueue = queue.map(ProviderSong::toOnlineDisplaySong)
        val selectedDisplaySong = selected.toOnlineDisplaySong()
        sourceQueue = displayQueue
        sourceTrackQueue = queue.zip(displayQueue) { providerSong, presentation ->
            QueueTrackEntry(providerSong.toPersistentTrack(), presentation, providerSong)
        }
        onlineQueueSongs = queue.associateBy { providerSong ->
            providerSong.toOnlineDisplaySong().uri.toString()
        }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = selectedDisplaySong
        )
        val selectedIndex = findSongIndex(playbackQueue, selectedDisplaySong)
        if (selectedIndex == -1) return
        playSongAt(index = selectedIndex, source = PlaybackSource.Search)
    }

    private fun playOnlineSongAt(
        selectedSong: Song,
        index: Int,
        source: PlaybackSource
    ) {
        val requestGeneration = beginPlaybackRequest()
        setPendingPlayback(
            track = playbackTrackQueue.getOrNull(index)?.persistentTrack,
            presentation = selectedSong,
            index = index,
            generation = requestGeneration,
            phase = PendingPlayback.Phase.Preparing
        )
        pendingPlaybackJob = viewModelScope.launch {
            val providerSong = playbackTrackQueue.getOrNull(index)?.runtimeProviderSong
                ?: onlineQueueSongs[selectedSong.uri.toString()] ?: run {
                publishTrackPlaybackError("在线歌曲引用已失效")
                return@launch
            }
            val mediaItem = extensionManager.createPlaybackMediaItem(providerSong) ?: run {
                if (!isCurrentPlaybackRequest(requestGeneration)) return@launch
                clearPendingPlayback(requestGeneration)
                publishTrackPlaybackError("无法解析在线播放资源")
                return@launch
            }
            if (!isCurrentPlaybackRequest(requestGeneration)) {
                Log.d("FlowtonePlayback", "playback.request.stale success")
                return@launch
            }
            currentQueueIndex = index
            currentPlaybackSource = source
            publishPlaybackQueue()
            playbackController.playResolvedMediaItem(
                song = selectedSong,
                mediaItem = mediaItem,
                extensionArtwork = providerSong.artwork,
                extensionLargeArtwork = providerSong.largeArtwork,
                persistentTrack = providerSong.toPersistentTrack()
            )
            clearPendingPlayback(requestGeneration)
            scheduleNextSongsPreload()
        }
    }

    fun playQueueSong(song: Song) {
        val playbackIndex = findSongIndex(playbackQueue, song)
        if (playbackIndex != -1) {
            playSongAt(playbackIndex, source = currentPlaybackSource)
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
        sourceQueue = playbackQueue
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
        sourceQueue = playbackQueue
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

        val selectedSong = playbackQueue[index]
        val queueEntry = playbackTrackQueue.getOrNull(index)
        when (queueEntry?.playbackKind) {
            QueueTrackPlaybackKind.RuntimeProvider -> {
                playOnlineSongAt(selectedSong, index, source)
                return
            }
            QueueTrackPlaybackKind.PersistentOnline -> {
                playPersistentOnlineTrack(
                    queueEntry.persistentTrack as PersistentTrack.Online,
                    selectedSong,
                    index,
                    source
                )
                return
            }
            QueueTrackPlaybackKind.Local, null -> Unit
        }
        // 所有 Local 入口（列表、歌单、Likes、next/previous、auto-next）最终都到这里。
        beginPlaybackRequest()
        val containsOnlineTrack = playbackTrackQueue.any {
            it.playbackKind != QueueTrackPlaybackKind.Local
        }
        val playerQueue = mediaControllerQueueForSelection(
            containsOnlineTrack = containsOnlineTrack,
            selectedSong = selectedSong,
            sourceQueue = sourceQueue,
            playbackQueue = playbackQueue
        )
        val playerStartIndex = findSongIndex(playerQueue, selectedSong)
        if (playerStartIndex == -1) {
            return
        }

        currentQueueIndex = index
        currentPlaybackSource = source
        playbackController.playQueue(playerQueue, playerStartIndex, source)
        if (!containsOnlineTrack) {
            playbackController.setPlaybackOrderMode(
                mode = playbackState.value.playbackOrderMode,
                shuffleOrderIndices = if (
                    playbackState.value.playbackOrderMode == PlaybackOrderMode.Shuffle
                ) {
                    buildPlaybackOrderIndices(playbackQueue, playerQueue)
                } else {
                    null
                }
            )
        } else {
            playbackController.setPlaybackOrderMode(
                mode = playbackState.value.playbackOrderMode,
                shuffleOrderIndices = null
            )
        }
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

        sourceQueue = if (scannedSongs.isNotEmpty()) {
            scannedSongs
        } else {
            restoredQueue
        }
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
            positionMs = position,
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
            positionMs = playbackState.value.positionMs,
            durationMs = playbackState.value.durationMs.takeIf { it > 0L }
                ?: officialSong.durationMs.coerceAtLeast(0L),
            playbackOrderMode = playbackState.value.playbackOrderMode
        )
        publishPlaybackQueue()
        scheduleNextSongsPreload()
    }

    private fun publishPlaybackQueue() {
        _uiState.update {
            it.copy(
                sourceQueue = sourceQueue,
                playbackQueue = playbackQueue,
                currentQueueIndex = currentQueueIndex
            )
        }
    }

    private fun scheduleNextSongsPreload() {
        val maximumPreloadCount = maxOf(preloadSongMetadataCount, preloadLyricsCount)
        val upcomingSongs = upcomingSongsInPlaybackOrder(maximumPreloadCount)

        val metadataSongs = upcomingSongs.take(preloadSongMetadataCount)
        val nextMetadataPreloadUris = metadataSongs.map { it.uri.toString() }
        if (nextMetadataPreloadUris != metadataPreloadUris) {
            metadataPreloadUris = nextMetadataPreloadUris
            preloadJob?.cancel()
            preloadJob = viewModelScope.launch {
                songMetadataPreloader.preload(metadataSongs)
            }
        }

        val songsToPreload = upcomingSongs.take(preloadLyricsCount)
        lyricsPreloadScheduler.update(
            currentIndex = playbackController.getCurrentMediaItemIndex() ?: currentQueueIndex,
            currentSong = playbackState.value.currentSong
                ?: playbackQueue.getOrNull(currentQueueIndex),
            songsInPlaybackOrder = songsToPreload
        )
        scheduleOnlineArtworkPreload()
    }

    /**
     * 在线预载只 hydrate 元数据并交给现有 ExtensionImage Coil 管线；绝不请求播放资源。
     * 先完整调度普通封面，再调度大图，避免大图抢占下一首缩略图。
     */
    private fun scheduleOnlineArtworkPreload() {
        onlineArtworkPreloadJob?.cancel()
        val generation = ++onlinePreloadGeneration
        val entries = (currentQueueIndex + 1).coerceAtLeast(0)
            .let { start -> playbackTrackQueue.drop(start).take(preloadSongMetadataCount) }
            .filter { it.persistentTrack is PersistentTrack.Online || it.runtimeProviderSong != null }
        Log.d(
            "FlowtonePlayback",
            "online.artwork.preload.window currentIndex=$currentQueueIndex count=$preloadSongMetadataCount entries=${entries.size}"
        )
        if (entries.isEmpty()) return
        onlineArtworkPreloadJob = viewModelScope.launch {
            val songs = entries.mapNotNull { entry ->
                if (generation != onlinePreloadGeneration) {
                    Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=staleGeneration")
                    return@launch
                }
                entry.runtimeProviderSong ?: hydrateOnlinePresentation(
                    entry.persistentTrack as? PersistentTrack.Online
                ).also { hydrated ->
                    if (hydrated == null) Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=hydrationFailed")
                }
            }
            if (generation != onlinePreloadGeneration) return@launch
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
            val state = runCatching { extensionManager.getSearchLanding(scope.extensionId) }
                .fold(
                    onSuccess = { ProviderSearchLandingState.Loaded(it) },
                    onFailure = { ProviderSearchLandingState.Error }
                )
            _searchUiState.update { current ->
                if (generation == searchLandingGeneration && current.scope == scope && current.query.isBlank) {
                    current.copy(landingState = state)
                } else current
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

    fun setTrackLiked(track: PersistentTrack, liked: Boolean) {
        val next = if (liked) {
            (_likedTracks.value + track).distinctBy(PersistentTrack::identityKey)
        } else {
            _likedTracks.value.filterNot { it.identityKey == track.identityKey }
        }
        if (next != _likedTracks.value) {
            _likedTracks.value = next
            _uiState.update { it.copy(likedTracks = next) }
            likedSongsStore.saveLikedTracks(next)
        }
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

    private fun playPersistentOnlineTrack(
        track: PersistentTrack.Online,
        presentation: Song,
        index: Int,
        source: PlaybackSource
    ) {
        val requestGeneration = beginPlaybackRequest()
        setPendingPlayback(track, presentation, index, requestGeneration, PendingPlayback.Phase.Resolving)
        pendingPlaybackJob = viewModelScope.launch {
            val cachedSong = onlinePresentationCache[track.identityKey]
                ?.takeIf { extensionManager.isMusicProviderRuntimeAvailable(it.trackRef.extensionId) }
            if (!isCurrentPlaybackRequest(requestGeneration)) return@launch
            when (val result = cachedSong?.let {
                ink.tenqui.flowtone.data.online.PersistentSongResolution.Resolved(it)
            } ?: extensionManager.resolvePersistentPlaylistSong(track)) {
                is ink.tenqui.flowtone.data.online.PersistentSongResolution.Resolved -> {
                    if (!isCurrentPlaybackRequest(requestGeneration)) return@launch
                    onlinePresentationCache[track.identityKey] = result.song
                    setPendingPlayback(track, presentation, index, requestGeneration, PendingPlayback.Phase.Preparing)
                    val mediaItem = extensionManager.createPlaybackMediaItem(result.song)
                    if (!isCurrentPlaybackRequest(requestGeneration)) return@launch
                    if (mediaItem == null) {
                        publishTrackPlaybackError("无法解析在线播放资源")
                        return@launch
                    }
                    currentQueueIndex = index
                    currentPlaybackSource = source
                    publishPlaybackQueue()
                    playbackController.playResolvedMediaItem(
                        song = presentation,
                        mediaItem = mediaItem,
                        extensionArtwork = result.song.artwork,
                        extensionLargeArtwork = result.song.largeArtwork,
                        persistentTrack = track
                    )
                    clearPendingPlayback(requestGeneration)
                    scheduleNextSongsPreload()
                }
                is ink.tenqui.flowtone.data.online.PersistentSongResolution.ProviderMissing -> if (isCurrentPlaybackRequest(requestGeneration))
                    publishTrackPlaybackError("当前没有可处理 ${track.sourceHost} 的扩展")
                is ink.tenqui.flowtone.data.online.PersistentSongResolution.Unresolved -> if (isCurrentPlaybackRequest(requestGeneration))
                    publishTrackPlaybackError("该在线歌曲暂时无法恢复")
            }
        }
    }

    private fun publishTrackPlaybackError(message: String) {
        _uiState.update { state ->
            state.copy(
                trackPlaybackErrorMessage = message,
                trackPlaybackErrorEventId = state.trackPlaybackErrorEventId + 1L,
                pendingPlayback = null,
                pendingQueueIndex = null
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

        val currentSong = playbackState.value.currentSong

        if (sourceQueue.isEmpty() && playbackQueue.isNotEmpty()) {
            sourceQueue = playbackQueue
        }

        val sourceSnapshot = sourceQueue
        val nextPlaybackQueue = withContext(Dispatchers.Default) {
            buildPlaybackQueueForMode(sourceSnapshot, mode, currentSong)
        }
        val nextShuffleOrderIndices = if (mode == PlaybackOrderMode.Shuffle) {
            withContext(Dispatchers.Default) {
                buildPlaybackOrderIndices(nextPlaybackQueue, sourceSnapshot)
            }
        } else {
            null
        }

        playbackQueue = nextPlaybackQueue
        currentQueueIndex = findSongIndex(playbackQueue, currentSong)

        playbackController.updatePlaybackOrderMode(mode)
        playbackController.setPlaybackOrderMode(
            mode = mode,
            shuffleOrderIndices = nextShuffleOrderIndices
        )
        playbackSettingsStore.setPlaybackOrderMode(mode)
        publishPlaybackQueue()
        scheduleNextSongsPreload()
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
                delay(500)
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
        val playbackOrderMode = playbackController.getPlaybackOrderMode()
        if (playbackState.value.playbackOrderMode != playbackOrderMode) {
            playbackController.updatePlaybackOrderMode(playbackOrderMode)
            playbackSettingsStore.setPlaybackOrderMode(playbackOrderMode)
            rebuildPlaybackQueueForMode(
                mode = playbackOrderMode,
                currentSong = playbackState.value.currentSong
            )
            publishPlaybackQueue()
            scheduleNextSongsPreload()
        }

        val currentSong = playbackState.value.currentSong
        if (currentSong == null) {
            playbackController.updateProgress(
                positionMs = 0L,
                durationMs = 0L
            )
            return
        }

        val controllerDuration = playbackController.getDurationMs()
        val duration = when {
            controllerDuration > 0L -> controllerDuration
            currentSong.durationMs > 0L -> currentSong.durationMs
            else -> 0L
        }
        val position = if (duration > 0L) {
            playbackController.getCurrentPositionMs().coerceIn(0L, duration)
        } else {
            0L
        }

        playbackController.updateProgress(
            positionMs = position,
            durationMs = duration
        )
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

internal data class QueueTrackEntry(
    val persistentTrack: PersistentTrack?,
    val presentation: Song,
    val runtimeProviderSong: ProviderSong? = null
)

internal enum class QueueTrackPlaybackKind {
    Local,
    RuntimeProvider,
    PersistentOnline
}

internal val QueueTrackEntry.playbackKind: QueueTrackPlaybackKind
    get() = when {
        runtimeProviderSong != null -> QueueTrackPlaybackKind.RuntimeProvider
        persistentTrack is PersistentTrack.Online -> QueueTrackPlaybackKind.PersistentOnline
        else -> QueueTrackPlaybackKind.Local
    }

internal fun mediaControllerQueueForSelection(
    containsOnlineTrack: Boolean,
    selectedSong: Song,
    sourceQueue: List<Song>,
    playbackQueue: List<Song>
): List<Song> = if (containsOnlineTrack) {
    listOf(selectedSong)
} else {
    sourceQueue.ifEmpty { playbackQueue }
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
