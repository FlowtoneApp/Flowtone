package ink.tenqui.flowtone.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.tenqui.flowtone.data.local.AudioScanner
import ink.tenqui.flowtone.lyrics.LocalLyricsRepository
import ink.tenqui.flowtone.lyrics.LyricsLoadResult
import ink.tenqui.flowtone.lyrics.LyricsLoadSource
import ink.tenqui.flowtone.lyrics.LyricsPreloadScheduler
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.lyrics.LyricsFolder
import ink.tenqui.flowtone.data.local.LocalMusicRepository
import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.data.local.SongMetadataPreloader
import ink.tenqui.flowtone.data.listening.ListeningStatsRepositoryProvider
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.repository.MusicRepository
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.SearchQuery
import ink.tenqui.flowtone.data.search.SearchRepository
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
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
    val listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot()
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepository = MusicRepository(
        localMusicRepository = LocalMusicRepository(
            audioScanner = AudioScanner(application.contentResolver)
        )
    )
    private val playbackSettingsStore = PlaybackSettingsStore(application)
    private val localLyricsRepository = LocalLyricsRepository(application)
    private val listeningStatsRepository = ListeningStatsRepositoryProvider.get(application)
    private val searchRepository = SearchRepository()
    private val playbackController = PlaybackController(
        context = application,
        initialPlaybackOrderMode = playbackSettingsStore.getPlaybackOrderMode(),
        onPlaybackEnded = ::handlePlaybackEnded,
        onMediaItemChanged = ::syncCurrentSongFromMediaId
    )
    private val songMetadataPreloader = SongMetadataPreloader(application)
    private val _uiState = MutableStateFlow(
        MusicUiState(listeningStats = listeningStatsRepository.getStats())
    )
    private val _searchUiState = MutableStateFlow(GlobalSearchUiState())
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    // 歌词只使用 MediaController 确认的位置，不读取进度条的动画或拖动状态。
    private val _confirmedPlaybackPositionMs = MutableStateFlow(0L)
    private val _lyricsFolders = MutableStateFlow(localLyricsRepository.getLyricsFolders())
    private val lyricsReloadVersion = MutableStateFlow(0)
    private var sourceQueue: List<Song> = emptyList()
    private var playbackQueue: List<Song> = emptyList()
    private var currentQueueIndex: Int = -1
    private var preloadSongMetadataCount: Int = 5
    private var preloadLyricsCount: Int = 5
    private var preloadJob: Job? = null
    private var metadataPreloadUris: List<String> = emptyList()
    private val lyricsPreloadScheduler by lazy {
        LyricsPreloadScheduler(viewModelScope, localLyricsRepository)
    }
    private var searchJob: Job? = null
    private var playbackOrderModeJob: Job? = null
    private var currentPlaybackSource: PlaybackSource = PlaybackSource.Unknown

    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    val searchUiState: StateFlow<GlobalSearchUiState> = _searchUiState.asStateFlow()
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()
    val confirmedPlaybackPositionMs: StateFlow<Long> =
        _confirmedPlaybackPositionMs.asStateFlow()
    val lyricsFolders: StateFlow<List<LyricsFolder>> = _lyricsFolders.asStateFlow()

    init {
        startProgressTicker()
        startConfirmedPlaybackPositionTicker()
        observeControllerConnection()
        observeListeningStats()
        observeLyrics()
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

        if (query.isBlank) {
            _searchUiState.value = GlobalSearchUiState(queryText = queryText)
            return
        }

        _searchUiState.update { currentState ->
            currentState.copy(
                queryText = queryText,
                isSearching = true
            )
        }
        searchJob = viewModelScope.launch {
            delay(200)
            publishSearchResults(query = query, visibleQueryText = queryText)
        }
    }

    fun clearSearchQuery() {
        searchJob?.cancel()
        _searchUiState.value = GlobalSearchUiState()
    }

    private suspend fun publishSearchResults(
        query: SearchQuery,
        visibleQueryText: String
    ) {
        val results = searchRepository.search(query)
        _searchUiState.update { currentState ->
            if (currentState.queryText != visibleQueryText) {
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

    private fun refreshSearchIndex(songs: List<Song>) {
        viewModelScope.launch {
            searchRepository.updateLocalSongs(songs)
            val currentSearchState = _searchUiState.value
            val query = SearchQuery.from(currentSearchState.queryText)
            if (!query.isBlank) {
                searchJob?.cancel()
                _searchUiState.update { it.copy(isSearching = true) }
                publishSearchResults(
                    query = query,
                    visibleQueryText = currentSearchState.queryText
                )
            }
        }
    }

    private fun rebuildPlaybackQueueForMode(
        mode: PlaybackOrderMode,
        currentSong: Song?
    ) {
        playbackQueue = buildPlaybackQueueForMode(sourceQueue, mode, currentSong)
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
            playbackQueue = listOf(song)
            playSongAt(index = 0, source = source)
            return
        }

        sourceQueue = queue
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
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = startSong
        )
        val playbackIndex = findSongIndex(playbackQueue, startSong)
        playSongAt(index = playbackIndex, source = source)
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
        val playerQueue = sourceQueue.ifEmpty { playbackQueue }
        val playerStartIndex = findSongIndex(playerQueue, selectedSong)
        if (playerStartIndex == -1) {
            return
        }

        currentQueueIndex = index
        currentPlaybackSource = source
        playbackController.playQueue(playerQueue, playerStartIndex, source)
        playbackController.setPlaybackOrderMode(
            mode = playbackState.value.playbackOrderMode,
            shuffleOrderIndices = if (playbackState.value.playbackOrderMode == PlaybackOrderMode.Shuffle) {
                buildPlaybackOrderIndices(playbackQueue, playerQueue)
            } else {
                null
            }
        )
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
        val songId = mediaId.toLongOrNull() ?: return
        val songIndex = playbackQueue.indexOfFirst { it.id == songId }
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
                        _lyricsState.value = LyricsState.Idle
                        return@collectLatest
                    }

                    val request = localLyricsRepository.request(currentSong)
                    Log.d(
                        "Lyrics",
                        "foreground request song=${currentSong.id} source=${request.source}"
                    )
                    _lyricsState.value = if (request.source == LyricsLoadSource.NewRead) {
                        LyricsState.Loading
                    } else {
                        LyricsState.Idle
                    }
                    val result = try {
                        request.deferred.await()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        LyricsLoadResult.Failed(error)
                    }

                    _lyricsState.value = when (result) {
                        is LyricsLoadResult.Found -> LyricsState.Available(result.lines)
                        LyricsLoadResult.DirectoryNotSelected ->
                            LyricsState.DirectoryNotSelected
                        LyricsLoadResult.DirectoryPermissionLost ->
                            LyricsState.DirectoryPermissionLost
                        LyricsLoadResult.OutsideSelectedDirectory ->
                            LyricsState.OutsideSelectedDirectory
                        LyricsLoadResult.NotFound -> LyricsState.NotFound
                        is LyricsLoadResult.Failed -> {
                            Log.d("Lyrics", "failed=${result.throwable::class.simpleName}")
                            LyricsState.Error(result.throwable.message)
                        }
                    }
                }
        }
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
                _confirmedPlaybackPositionMs.value =
                    playbackController.getCurrentPositionMs()
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
        playbackOrderModeJob?.cancel()
        lyricsPreloadScheduler.clear()
        localLyricsRepository.close()
        playbackController.release()
        super.onCleared()
    }
}
