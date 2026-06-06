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
    private val playbackController = PlaybackController(application)
    private val _uiState = MutableStateFlow(MusicUiState())

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
        playbackController.play(song)
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
