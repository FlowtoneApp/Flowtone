package ink.tenqui.flowtone.ui.player

import android.net.Uri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackState

data class PlayerUiState(
    val currentSong: Song?,
    val currentTrack: PersistentTrack?,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val isPlaybackWaitingForData: Boolean,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val durationMs: Long,
    val artworkUri: Uri?,
    val extensionArtwork: ExtensionImage?,
    val extensionLargeArtwork: ExtensionImage?,
    val playbackOrderMode: PlaybackOrderMode,
    val currentQueueId: String?,
    val hasCurrentSong: Boolean,
    val canPlay: Boolean
) {
    companion object {
        fun from(
            playbackState: PlaybackState,
            pendingSong: Song? = null,
            pendingTrack: PersistentTrack? = null,
            pendingExtensionArtwork: ExtensionImage? = null,
            pendingExtensionLargeArtwork: ExtensionImage? = null,
            pendingPlayWhenReady: Boolean = true
        ): PlayerUiState {
            val hasPendingTarget = pendingSong != null
            val currentSong = pendingSong ?: playbackState.currentSong
            val durationMs = when {
                hasPendingTarget -> currentSong?.durationMs?.coerceAtLeast(0L) ?: 0L
                playbackState.durationMs > 0L -> playbackState.durationMs
                currentSong?.durationMs != null && currentSong.durationMs > 0L -> currentSong.durationMs
                else -> 0L
            }
            val playWhenReady = if (hasPendingTarget) {
                pendingPlayWhenReady
            } else {
                playbackState.playWhenReady
            }

            return PlayerUiState(
                currentSong = currentSong,
                currentTrack = if (hasPendingTarget) pendingTrack else playbackState.currentTrack,
                isPlaying = if (hasPendingTarget) false else playbackState.isPlaying,
                playWhenReady = playWhenReady,
                isPlaybackWaitingForData = currentSong != null &&
                    playWhenReady &&
                    (hasPendingTarget || playbackState.isBuffering),
                positionMs = if (hasPendingTarget) 0L else playbackState.positionMs,
                bufferedPositionMs = if (hasPendingTarget) 0L else playbackState.bufferedPositionMs,
                durationMs = durationMs,
                artworkUri = currentSong?.artworkUri,
                extensionArtwork = if (hasPendingTarget) {
                    pendingExtensionArtwork
                } else {
                    playbackState.extensionArtwork
                },
                extensionLargeArtwork = if (hasPendingTarget) {
                    pendingExtensionLargeArtwork
                } else {
                    playbackState.extensionLargeArtwork
                },
                playbackOrderMode = playbackState.playbackOrderMode,
                currentQueueId = playbackState.currentQueueId,
                hasCurrentSong = currentSong != null,
                canPlay = currentSong != null
            )
        }
    }
}
