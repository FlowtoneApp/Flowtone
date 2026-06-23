package ink.tenqui.flowtone.ui.player

import android.net.Uri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackState

data class PlayerUiState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val artworkUri: Uri?,
    val playbackOrderMode: PlaybackOrderMode,
    val hasCurrentSong: Boolean,
    val canPlay: Boolean
) {
    companion object {
        fun from(playbackState: PlaybackState): PlayerUiState {
            val currentSong = playbackState.currentSong
            val durationMs = when {
                playbackState.durationMs > 0L -> playbackState.durationMs
                currentSong?.durationMs != null && currentSong.durationMs > 0L -> currentSong.durationMs
                else -> 0L
            }

            return PlayerUiState(
                currentSong = currentSong,
                isPlaying = playbackState.isPlaying,
                positionMs = playbackState.positionMs,
                durationMs = durationMs,
                artworkUri = currentSong?.artworkUri,
                playbackOrderMode = playbackState.playbackOrderMode,
                hasCurrentSong = currentSong != null,
                canPlay = currentSong != null
            )
        }
    }
}
