package ink.tenqui.flowtone.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.tenqui.flowtone.data.local.AudioScanner
import ink.tenqui.flowtone.data.local.LikedTracksRepository
import ink.tenqui.flowtone.lyrics.LocalLyricsRepository
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.lyrics.SongLyricsState
import ink.tenqui.flowtone.lyrics.LyricsFolder
import ink.tenqui.flowtone.data.local.LocalMusicRepository
import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.data.local.SongMetadataPreloader
import ink.tenqui.flowtone.data.listening.ListeningStatsRepositoryProvider
import ink.tenqui.flowtone.data.repository.MusicRepository
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import ink.tenqui.flowtone.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val extensionManager = ExtensionManager.get(application)
    private val appPreferences = AppPreferences(application)
    private val musicRepository = MusicRepository(LocalMusicRepository(AudioScanner(application)))
    private val playbackSettingsStore = PlaybackSettingsStore(application)
    private val likedTracksRepository = LikedTracksRepository.get(application)
    private val listeningStatsRepository = ListeningStatsRepositoryProvider.get(application).also { it.getStats() }
    private val _feedbackState = MutableStateFlow(MusicFeedbackState())
    private var libraryRefreshFailureSequence = 0L
    private val extensionContentFailureSequence = AtomicLong()
    private var observedExtensionRuntimeGeneration = extensionManager.runtimeState.value.generation
    private var extensionRuntimeRefreshPending = extensionManager.runtimeState.value.isReloading

    private val playbackController: PlaybackController = PlaybackController(
        context = application,
        initialPlaybackOrderMode = playbackSettingsStore.getPlaybackOrderMode(),
        onPlaybackEnded = ::handlePlaybackEnded,
        onMediaItemChanged = ::syncCurrentSongFromMediaId
    )
    private val searchSession: MusicSearchSession = MusicSearchSession(
        viewModelScope, extensionManager, appPreferences, ::reportExtensionContentFailure
    )
    private val lyricsSession: MusicLyricsSession = MusicLyricsSession(
        viewModelScope,
        LocalLyricsRepository(application),
        playbackController.playbackState,
        ::scheduleNextSongsPreload
    )
    private val localLibrarySession: LocalLibrarySession = LocalLibrarySession(
        scope = viewModelScope,
        loadSongs = musicRepository::loadLocalSongs,
        onSongsDiscovered = ::onLocalSongsDiscovered,
        onScanCompleted = ::onLocalScanCompleted
    )
    private val playbackSession: MusicPlaybackSession = MusicPlaybackSession(
        viewModelScope,
        playbackController,
        playbackSettingsStore,
        localSongs = { localLibrarySession.state.value.songs },
        onQueueChanged = ::scheduleNextSongsPreload
    )
    private val preloadCoordinator: MusicPreloadCoordinator = MusicPreloadCoordinator(
        application,
        viewModelScope,
        playbackController,
        SongMetadataPreloader(application),
        lyricsSession.preloadScheduler,
        extensionManager,
        snapshotProvider = playbackSession::preloadSnapshot,
        onHydrated = playbackSession::applyHydratedMetadata
    )
    private val progressMonitor: PlaybackProgressMonitor = PlaybackProgressMonitor(
        viewModelScope, playbackController, playbackSettingsStore
    )
    private val providerLibraryCatalogCoordinator: ProviderLibraryCatalogCoordinator = ProviderLibraryCatalogCoordinator(
        scope = viewModelScope,
        gateway = ExtensionManagerProviderLibraryCatalogGateway(extensionManager),
        onFailure = ::reportExtensionContentFailure,
        isRefreshing = { it in _feedbackState.value.libraryRefreshingSources }
    )

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val searchUiState: StateFlow<GlobalSearchUiState> = searchSession.state
    val lyricsState: StateFlow<LyricsState> = lyricsSession.lyricsState
    val songLyricsState: StateFlow<SongLyricsState> = lyricsSession.songLyricsState
    val lyricsFolders: StateFlow<List<LyricsFolder>> = lyricsSession.folders
    val likedTracks: StateFlow<List<PersistentTrack>> = likedTracksRepository.tracks
    val confirmedPlaybackPosition: StateFlow<PlaybackPositionSnapshot> = progressMonitor.confirmedPosition

    val uiState: StateFlow<MusicUiState> = combine(
        localLibrarySession.state,
        providerLibraryCatalogCoordinator.state,
        playbackSession.queueState,
        _feedbackState,
        likedTracksRepository.tracks
    ) { local, provider, queue, feedback, liked ->
        assembleMusicUiState(local, provider, queue, feedback, liked, listeningStatsRepository.stats.value)
    }.combine(listeningStatsRepository.stats) { state, stats ->
        state.copy(listeningStats = stats)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        assembleMusicUiState(
            localLibrarySession.state.value,
            providerLibraryCatalogCoordinator.state.value,
            playbackSession.queueState.value,
            _feedbackState.value,
            likedTracksRepository.tracks.value,
            listeningStatsRepository.stats.value
        )
    )

    init {
        observeExtensionRuntime()
        observeExtensionImageFailures()
        observePlaybackFailures()
        progressMonitor.start()
        playbackSession.start()
        lyricsSession.start()
        refreshSearchSources()
    }

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
        _feedbackState.update { state ->
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
        _feedbackState.update { state ->
            state.copy(extensionContentFailures = state.extensionContentFailures.filterNot { it.id == id })
        }
    }

    private fun observeExtensionRuntime() {
        viewModelScope.launch {
            extensionManager.runtimeState.collect { runtimeState ->
                if (runtimeState.generation != observedExtensionRuntimeGeneration) {
                    observedExtensionRuntimeGeneration = runtimeState.generation
                    extensionRuntimeRefreshPending = true
                    invalidateExtensionRuntimeRequests()
                }
                if (!runtimeState.isReloading && extensionRuntimeRefreshPending) {
                    extensionRuntimeRefreshPending = false
                    val queryText = searchSession.state.value.queryText
                    refreshSearchSources()
                    providerLibraryCatalogCoordinator.reloadRequestedProviderData()
                    if (runtimeState.lastReloadErrorType == null && queryText.isNotBlank()) {
                        updateSearchQuery(queryText)
                    }
                }
            }
        }
    }

    private fun invalidateExtensionRuntimeRequests() {
        searchSession.invalidateRuntime()
        preloadCoordinator.invalidateRuntime()
        providerLibraryCatalogCoordinator.invalidateRuntime()
        playbackSession.publishConfirmedQueueIndex()
    }

    fun refreshSearchSources() {
        val runtimeStateBeforeRefresh = extensionManager.runtimeState.value
        val providers = extensionManager.availableMusicProviderOptions()
        val libraryProviders = extensionManager.availableLibraryProviderOptions()
        val runtimeStateAfterRefresh = extensionManager.runtimeState.value
        val libraryProviderSourcesResolved = runtimeStateBeforeRefresh == runtimeStateAfterRefresh &&
            runtimeStateAfterRefresh.generation > 0L &&
            !runtimeStateAfterRefresh.isReloading
        providerLibraryCatalogCoordinator.updateSources(libraryProviders, libraryProviderSourcesResolved)
        searchSession.updateProviders(providers)
    }

    private fun onLocalSongsDiscovered(songs: List<Song>, preservePlayback: Boolean) {
        playbackSession.prepareLibraryQueue(songs, preservePlayback)
    }

    private suspend fun onLocalScanCompleted(
        songs: List<Song>,
        albums: List<LocalAlbum>,
        preservePlayback: Boolean
    ) {
        likedTracksRepository.refreshLocalSongs(songs)
        searchSession.updateLocalLibrary(songs, albums).join()
        if (!preservePlayback) {
            playbackSession.reconcileCurrentSongWithLibrary()
            playbackSession.restoreFromControllerIfPossible()
        }
    }

    private fun scheduleNextSongsPreload(): Unit = preloadCoordinator.update()
    private fun handlePlaybackEnded(): Unit = playbackSession.handlePlaybackEnded()
    private fun syncCurrentSongFromMediaId(mediaId: String): Unit = playbackSession.syncCurrentSongFromMediaId(mediaId)

    fun setPermissionStatus(hasPermission: Boolean) = localLibrarySession.setPermissionStatus(hasPermission)
    fun scanSongs() = localLibrarySession.scanSongs()
    fun setPreloadSongMetadataCount(count: Int) = preloadCoordinator.setPreloadSongMetadataCount(count)
    fun setPreloadLyricsCount(count: Int) = preloadCoordinator.setPreloadLyricsCount(count)

    @Suppress("UNUSED_PARAMETER")
    fun setSongRecordThresholdSeconds(seconds: Int) {
        // 阈值由 AppPreferences 保存，后台播放服务中的 ListeningStatsTracker 会读取最新值。
    }

    fun updateSearchQuery(queryText: String) = searchSession.updateSearchQuery(queryText)
    fun clearSearchQuery() = searchSession.clearSearchQuery()
    fun selectSearchScope(scope: SearchScope) = searchSession.selectSearchScope(scope)
    fun selectProviderSearchCategory(category: ProviderSearchCategory) = searchSession.selectProviderSearchCategory(category)
    fun loadMoreProviderSearchResults(category: ProviderSearchCategory = searchUiState.value.selectedProviderCategory) =
        searchSession.loadMoreProviderSearchResults(category)

    fun setLyricsDirectory(treeUri: Uri) = lyricsSession.setLyricsDirectory(treeUri)
    fun addLyricsFolder(treeUri: Uri): Boolean = lyricsSession.addLyricsFolder(treeUri)
    fun removeLyricsFolder(treeUri: Uri) = lyricsSession.removeLyricsFolder(treeUri)
    fun refreshLyricsFolders() = lyricsSession.refreshLyricsFolders()

    fun handleLocalSongsDeleted(deletedSongs: List<Song>) {
        if (deletedSongs.isEmpty()) return
        playbackSession.handleLocalSongsDeleted(deletedSongs)
        localLibrarySession.scanSongs()
    }

    fun playSong(song: Song, source: PlaybackSource = PlaybackSource.LocalLibrary) =
        playbackSession.playSong(song, source)

    fun playSongQueue(songs: List<Song>, startIndex: Int, source: PlaybackSource = PlaybackSource.Unknown) =
        playbackSession.playSongQueue(songs, startIndex, source)

    fun playProviderSong(song: ProviderSong) {
        val snapshot = searchSession.state.value.providerCategoryStates[ProviderSearchCategory.Single]
            ?.items.orEmpty()
            .filterIsInstance<ProviderSong>()
        val queue = snapshot.takeIf { results -> results.any { it.trackRef == song.trackRef } }
            ?: listOf(song)
        playProviderSongQueue(queue, queue.indexOfFirst { it.trackRef == song.trackRef }, PlaybackSource.Search)
    }

    fun playProviderSongQueue(queue: List<ProviderSong>, startIndex: Int, source: PlaybackSource) =
        playbackSession.playProviderSongQueue(queue, startIndex, source)

    fun playPersistentTrackQueue(
        tracks: List<PersistentTrack>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.Unknown
    ) = playbackSession.playPersistentTrackQueue(tracks, startIndex, source)

    fun playPersistentTrack(track: PersistentTrack) = playbackSession.playPersistentTrack(track)
    fun playQueueSong(song: Song) = playbackSession.playQueueSong(song)
    fun addSongsToNext(songs: List<Song>): Boolean = playbackSession.addSongsToNext(songs)
    fun appendSongsToQueue(songs: List<Song>): Boolean = playbackSession.appendSongsToQueue(songs)
    fun togglePlayPause() = playbackSession.togglePlayPause()
    fun togglePlaybackOrderMode() = playbackSession.togglePlaybackOrderMode()
    fun seekTo(positionMs: Long) = playbackSession.seekTo(positionMs)
    fun playNext() = playbackSession.playNext()
    fun playPrevious() = playbackSession.playPrevious()

    fun setTrackLiked(track: PersistentTrack, liked: Boolean) {
        likedTracksRepository.setLiked(track, liked)
    }

    fun toggleCurrentTrackLiked(): Boolean {
        val track = playbackState.value.currentTrack ?: return false
        val liked = likedTracksRepository.tracks.value.any { it.identityKey == track.identityKey }
        setTrackLiked(track, !liked)
        return true
    }

    fun refreshLibrary(sourceKey: String) {
        if (sourceKey in _feedbackState.value.libraryRefreshingSources) return
        val providerId = sourceKey.removePrefix("extension:").takeIf { sourceKey.startsWith("extension:") && it.isNotBlank() }
        if (sourceKey != "local" && providerId == null) return
        _feedbackState.update { it.copy(libraryRefreshingSources = it.libraryRefreshingSources + sourceKey) }
        val version = providerId?.let(providerLibraryCatalogCoordinator::beginRefresh)
        viewModelScope.launch {
            var success = false
            try {
                success = if (providerId == null) {
                    localLibrarySession.refresh()
                } else {
                    providerLibraryCatalogCoordinator.refreshProviderLibrary(providerId, requireNotNull(version))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w("FlowtoneLibrary", "Library refresh failed for $sourceKey", error)
            } finally {
                _feedbackState.update { state ->
                    state.copy(
                        libraryRefreshingSources = state.libraryRefreshingSources - sourceKey,
                        libraryRefreshFailure = if (success) state.libraryRefreshFailure
                            else sourceKey to ++libraryRefreshFailureSequence
                    )
                }
            }
        }
    }

    fun loadProviderEntityCollections(providerId: String) {
        providerLibraryCatalogCoordinator.loadProviderEntityCollections(providerId)
    }

    fun loadProviderUserPlaylists(providerId: String) {
        providerLibraryCatalogCoordinator.loadProviderUserPlaylists(providerId)
    }

    fun loadProviderPlaylist(playlist: ProviderPlaylistSearchItem) {
        providerLibraryCatalogCoordinator.loadProviderPlaylist(playlist)
    }

    override fun onCleared() {
        searchSession.close()
        preloadCoordinator.close()
        progressMonitor.close()
        playbackSession.close()
        lyricsSession.close()
        playbackController.release()
        super.onCleared()
    }
}
