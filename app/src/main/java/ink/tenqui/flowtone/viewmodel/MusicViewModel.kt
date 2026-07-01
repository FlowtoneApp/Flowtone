package ink.tenqui.flowtone.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.tenqui.flowtone.data.local.AudioScanner
import ink.tenqui.flowtone.data.local.ListeningStatsStore
import ink.tenqui.flowtone.data.local.LocalMusicRepository
import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.data.local.SongMetadataPreloader
import ink.tenqui.flowtone.data.listening.ListeningStatsRepository
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.repository.MusicRepository
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackState
import ink.tenqui.flowtone.playback.toSongOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    private val listeningStatsRepository = ListeningStatsRepository(
        localStore = ListeningStatsStore(application)
    )
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
    private var sourceQueue: List<Song> = emptyList()
    private var playbackQueue: List<Song> = emptyList()
    private var currentQueueIndex: Int = -1
    private var preloadSongMetadataCount: Int = 5
    private var preloadJob: Job? = null
    private var activeListeningSongKey: String? = null
    private var lastListeningTickElapsedMs: Long? = null
    private var pendingListeningDurationMs: Long = 0L
    private var lastPlaybackPositionMs: Long = 0L
    private var activeSongListeningDurationMs: Long = 0L
    private var activeSongRecorded: Boolean = false
    private var songRecordThresholdMs: Long = DEFAULT_SONG_RECORD_THRESHOLD_SECONDS * 1_000L

    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    init {
        startProgressTicker()
        observeControllerConnection()
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

    fun setSongRecordThresholdSeconds(seconds: Int) {
        songRecordThresholdMs = seconds
            .coerceIn(
                MIN_SONG_RECORD_THRESHOLD_SECONDS,
                MAX_SONG_RECORD_THRESHOLD_SECONDS
            )
            .toLong() * 1_000L
    }

    private fun rebuildPlaybackQueueForMode(
        mode: PlaybackOrderMode,
        currentSong: Song?
    ) {
        playbackQueue = when (mode) {
            PlaybackOrderMode.Shuffle -> buildShuffledPlaybackQueue(currentSong)
            PlaybackOrderMode.Sequence,
            PlaybackOrderMode.RepeatOne -> sourceQueue
        }
        currentQueueIndex = findSongIndex(playbackQueue, currentSong)
    }

    private fun buildShuffledPlaybackQueue(currentSong: Song?): List<Song> {
        if (sourceQueue.isEmpty()) {
            return emptyList()
        }

        val officialCurrentSong = currentSong?.let { findSong(sourceQueue, it) }
        return if (officialCurrentSong == null) {
            sourceQueue.shuffled()
        } else {
            listOf(officialCurrentSong) + sourceQueue
                .filterNot { isSameSong(it, officialCurrentSong) }
                .shuffled()
        }
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
                reconcileCurrentSongWithLibrary()
                restoreFromControllerIfPossible()
            }
        }
    }

    fun playSong(song: Song) {
        val queue = _uiState.value.songs
        val songIndex = queue.indexOfFirst { it.id == song.id || it.uri == song.uri }
        if (songIndex == -1) {
            sourceQueue = listOf(song)
            playbackQueue = listOf(song)
            playSongAt(index = 0)
            return
        }

        sourceQueue = queue
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = song
        )
        val playbackIndex = findSongIndex(playbackQueue, song)
        playSongAt(index = playbackIndex)
    }

    fun playQueueSong(song: Song) {
        val playbackIndex = findSongIndex(playbackQueue, song)
        if (playbackIndex != -1) {
            playSongAt(playbackIndex)
        }
    }

    private fun playSongAt(index: Int) {
        if (playbackQueue.isEmpty() || index !in playbackQueue.indices) {
            currentQueueIndex = -1
            publishPlaybackQueue()
            return
        }

        currentQueueIndex = index
        playbackController.playQueue(playbackQueue, index)
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

    private fun restoreFromControllerIfPossible() {
        val snapshot = playbackController.getPlaybackSnapshot() ?: return
        val currentMediaItem = snapshot.currentMediaItem ?: return
        val scannedSongs = _uiState.value.songs
        val currentSong = currentMediaItem.toSongOrNull(scannedSongs) ?: return

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
        playbackQueue = restoredQueue
        currentQueueIndex = when {
            snapshot.currentMediaItemIndex in restoredQueue.indices -> snapshot.currentMediaItemIndex
            else -> restoredQueue.indexOfFirst { it.id == currentSong.id || it.uri == currentSong.uri }
                .takeIf { it != -1 } ?: 0
        }

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
        preloadJob?.cancel()
        val startIndex = currentQueueIndex + 1
        if (
            preloadSongMetadataCount <= 0 ||
            playbackQueue.isEmpty() ||
            startIndex !in playbackQueue.indices
        ) {
            return
        }

        val songsToPreload = playbackQueue
            .drop(startIndex)
            .take(preloadSongMetadataCount)
        if (songsToPreload.isEmpty()) {
            return
        }

        preloadJob = viewModelScope.launch {
            songMetadataPreloader.preload(songsToPreload)
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
        applyPlaybackOrderMode(nextMode)
    }

    private fun applyPlaybackOrderMode(mode: PlaybackOrderMode) {
        val currentSong = playbackState.value.currentSong
        val positionMs = playbackController.getCurrentPositionMs()
        val wasPlaying = playbackState.value.isPlaying

        if (sourceQueue.isEmpty() && playbackQueue.isNotEmpty()) {
            sourceQueue = playbackQueue
        }

        playbackController.updatePlaybackOrderMode(mode)
        playbackController.setPlaybackOrderMode(mode)
        playbackSettingsStore.setPlaybackOrderMode(mode)

        rebuildPlaybackQueueForMode(
            mode = mode,
            currentSong = currentSong
        )

        if (currentSong != null && currentQueueIndex in playbackQueue.indices) {
            playbackController.replaceQueueKeepingCurrent(
                songs = playbackQueue,
                startIndex = currentQueueIndex,
                positionMs = positionMs,
                playWhenReady = wasPlaying
            )
        }

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
                updateListeningStatsFromPlayback()
                delay(500)
            }
        }
    }

    private fun updateProgressFromController() {
        val playbackOrderMode = playbackController.getPlaybackOrderMode()
        if (playbackState.value.playbackOrderMode != playbackOrderMode) {
            playbackController.updatePlaybackOrderMode(playbackOrderMode)
            playbackSettingsStore.setPlaybackOrderMode(playbackOrderMode)
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

    private fun updateListeningStatsFromPlayback() {
        val state = playbackState.value
        val currentSong = state.currentSong
        if (currentSong == null) {
            flushPendingListeningDuration()
            activeListeningSongKey = null
            lastListeningTickElapsedMs = null
            lastPlaybackPositionMs = 0L
            activeSongListeningDurationMs = 0L
            activeSongRecorded = false
            publishListeningStats(listeningStatsRepository.getStats())
            return
        }

        if (!state.isPlaying) {
            val songKey = currentSong.listeningStatsKey()
            if (activeListeningSongKey != songKey) {
                activeListeningSongKey = songKey
                activeSongListeningDurationMs = 0L
                activeSongRecorded = false
            }
            flushPendingListeningDuration()
            lastListeningTickElapsedMs = null
            lastPlaybackPositionMs = state.positionMs
            publishListeningStats(listeningStatsRepository.getStats())
            return
        }

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val songKey = currentSong.listeningStatsKey()
        val isNewSong = activeListeningSongKey != songKey
        val restartedSameSong = !isNewSong &&
            lastPlaybackPositionMs > LISTENING_RESTART_PREVIOUS_POSITION_MS &&
            state.positionMs <= LISTENING_RESTART_POSITION_MS

        if (isNewSong || restartedSameSong) {
            flushPendingListeningDuration()
            activeListeningSongKey = songKey
            activeSongListeningDurationMs = 0L
            activeSongRecorded = false
            lastListeningTickElapsedMs = nowElapsedMs
            lastPlaybackPositionMs = state.positionMs
            return
        }

        val previousTickElapsedMs = lastListeningTickElapsedMs
        if (previousTickElapsedMs != null) {
            val elapsedMs = nowElapsedMs - previousTickElapsedMs
            if (elapsedMs in 1..LISTENING_MAX_TICK_INTERVAL_MS) {
                pendingListeningDurationMs += elapsedMs
                activeSongListeningDurationMs += elapsedMs
                if (pendingListeningDurationMs >= LISTENING_FLUSH_INTERVAL_MS) {
                    flushPendingListeningDuration()
                }
                recordActiveSongIfNeeded(currentSong)
            } else {
                flushPendingListeningDuration()
            }
        }

        lastListeningTickElapsedMs = nowElapsedMs
        lastPlaybackPositionMs = state.positionMs
    }

    private fun recordActiveSongIfNeeded(song: Song) {
        if (activeSongRecorded || activeSongListeningDurationMs < songRecordThresholdMs) {
            return
        }

        activeSongRecorded = true
        publishListeningStats(listeningStatsRepository.recordSongPlayed(song))
    }

    private fun flushPendingListeningDuration() {
        val durationMs = pendingListeningDurationMs
        if (durationMs <= 0L) {
            return
        }

        pendingListeningDurationMs = 0L
        publishListeningStats(listeningStatsRepository.addListeningDuration(durationMs))
    }

    private fun publishListeningStats(snapshot: ListeningStatsSnapshot) {
        _uiState.update {
            it.copy(listeningStats = snapshot)
        }
    }

    override fun onCleared() {
        flushPendingListeningDuration()
        playbackController.release()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_SONG_RECORD_THRESHOLD_SECONDS = 30
        const val MIN_SONG_RECORD_THRESHOLD_SECONDS = 1
        const val MAX_SONG_RECORD_THRESHOLD_SECONDS = 60
        const val LISTENING_FLUSH_INTERVAL_MS = 5_000L
        const val LISTENING_MAX_TICK_INTERVAL_MS = 5_000L
        const val LISTENING_RESTART_PREVIOUS_POSITION_MS = 5_000L
        const val LISTENING_RESTART_POSITION_MS = 1_500L
    }
}

private fun Song.listeningStatsKey(): String {
    return "${sourceType.name}:$id:$uri"
}
