package ink.tenqui.flowtone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.tenqui.flowtone.data.local.AudioScanner
import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackState
import ink.tenqui.flowtone.playback.toSongOrNull
import kotlinx.coroutines.Dispatchers
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
    val errorMessage: String? = null,
    val hasScanned: Boolean = false
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val audioScanner = AudioScanner(application.contentResolver)
    private val playbackSettingsStore = PlaybackSettingsStore(application)
    private val playbackController = PlaybackController(
        context = application,
        initialPlaybackOrderMode = playbackSettingsStore.getPlaybackOrderMode(),
        onPlaybackEnded = ::handlePlaybackEnded,
        onMediaItemChanged = ::syncCurrentSongFromMediaId
    )
    private val _uiState = MutableStateFlow(MusicUiState())
    private var playbackQueue: List<Song> = emptyList()
    private var currentQueueIndex: Int = -1

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
                    audioScanner.scanSongs()
                }
            }

            _uiState.update { currentState ->
                result.fold(
                    onSuccess = { songs ->
                        playbackQueue = songs
                        syncCurrentQueueIndex()
                        currentState.copy(
                            isLoading = false,
                            songs = songs,
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
            playbackQueue = listOf(song)
            playSongAt(index = 0)
            return
        }

        playbackQueue = queue
        playSongAt(index = songIndex)
    }

    private fun playSongAt(index: Int) {
        if (playbackQueue.isEmpty() || index !in playbackQueue.indices) {
            currentQueueIndex = -1
            return
        }

        currentQueueIndex = index
        playbackController.playQueue(playbackQueue, index)
    }

    private fun syncCurrentQueueIndex() {
        val currentSong = playbackState.value.currentSong
        currentQueueIndex = if (currentSong == null) {
            -1
        } else {
            playbackQueue.indexOfFirst { it.id == currentSong.id || it.uri == currentSong.uri }
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
    }

    private fun reconcileCurrentSongWithLibrary() {
        val scannedSongs = _uiState.value.songs
        val currentSong = playbackState.value.currentSong ?: return
        val officialSong = scannedSongs.firstOrNull {
            it.id == currentSong.id || it.uri == currentSong.uri
        } ?: return

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
        playbackController.updatePlaybackOrderMode(nextMode)
        playbackController.setPlaybackOrderMode(nextMode)
        playbackSettingsStore.setPlaybackOrderMode(nextMode)
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

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
