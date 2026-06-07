package ink.tenqui.flowtone.playback

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import ink.tenqui.flowtone.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlaybackController(
    context: Context,
    private val onPlaybackEnded: () -> Unit
) {
    private val mediaControllerConnection = FlowtoneMediaControllerConnection(context.applicationContext)
    private val _playbackState = MutableStateFlow(PlaybackState())
    private var pendingSong: Song? = null
    private var isReleased = false

    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(isPlaying = isPlaying)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _playbackState.update {
                    it.copy(isPlaying = false)
                }
                onPlaybackEnded()
            }
        }
    }

    init {
        mediaControllerConnection.connect(
            onConnected = { controller ->
                if (isReleased) {
                    return@connect
                }

                controller.addListener(listener)
                pendingSong?.let { song ->
                    pendingSong = null
                    play(song)
                }
            },
            onConnectionFailed = { error ->
                if (isReleased) {
                    return@connect
                }

                if (pendingSong != null) {
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            errorMessage = error.message ?: "\u64ad\u653e\u5668\u8fde\u63a5\u5931\u8d25"
                        )
                    }
                }
            }
        )
    }

    fun play(song: Song) {
        val controller = mediaControllerConnection.currentController
        if (controller == null) {
            pendingSong = song
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = false,
                    errorMessage = null
                )
            }
            return
        }

        runCatching {
            val mediaItem = song.toMediaItem()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = true,
                    errorMessage = null
                )
            }
        }.onFailure { error ->
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = false,
                    errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
                )
            }
        }
    }

    fun play() {
        val controller = mediaControllerConnection.currentController ?: return
        controller.play()
        _playbackState.update {
            it.copy(
                isPlaying = true,
                errorMessage = null
            )
        }
    }

    fun pause() {
        val controller = mediaControllerConnection.currentController ?: return
        controller.pause()
        _playbackState.update {
            it.copy(isPlaying = false)
        }
    }

    fun togglePlayPause() {
        val controller = mediaControllerConnection.currentController
        val isPlaying = controller?.isPlaying ?: playbackState.value.isPlaying
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun release() {
        if (isReleased) {
            return
        }

        isReleased = true
        pendingSong = null
        mediaControllerConnection.currentController?.removeListener(listener)
        mediaControllerConnection.release()
    }
}
