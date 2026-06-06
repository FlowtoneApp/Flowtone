package ink.tenqui.flowtone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ink.tenqui.flowtone.data.AudioScanner
import ink.tenqui.flowtone.model.Song
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val playbackController = PlaybackController(
        context = application,
        onPlaybackEnded = ::playNext
    )
    private val _uiState = MutableStateFlow(MusicUiState())
    private var playbackQueue: List<Song> = emptyList()
    private var currentQueueIndex: Int = -1

    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

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

        val song = playbackQueue[index]
        currentQueueIndex = index
        playbackController.play(song)
    }

    private fun syncCurrentQueueIndex() {
        val currentSong = playbackState.value.currentSong
        currentQueueIndex = if (currentSong == null) {
            -1
        } else {
            playbackQueue.indexOfFirst { it.id == currentSong.id || it.uri == currentSong.uri }
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun playNext() {
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

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
