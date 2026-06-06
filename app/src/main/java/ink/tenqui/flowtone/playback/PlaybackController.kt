package ink.tenqui.flowtone.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ink.tenqui.flowtone.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlaybackController(
    context: Context,
    private val onPlaybackEnded: () -> Unit
) {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val _playbackState = MutableStateFlow(PlaybackState())

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
        player.addListener(listener)
    }

    fun play(song: Song) {
        runCatching {
            val mediaItem = MediaItem.fromUri(song.uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
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
        player.play()
        _playbackState.update {
            it.copy(
                isPlaying = true,
                errorMessage = null
            )
        }
    }

    fun pause() {
        player.pause()
        _playbackState.update {
            it.copy(isPlaying = false)
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}
