package ink.tenqui.flowtone.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionImage

internal data class PlaybackRequestToken(
    val generation: Long,
    val queueId: String
)

/** Generation + identity guard used before an asynchronous resolve may commit to ExoPlayer. */
internal class PlaybackRequestGate {
    private var generation = 0L

    fun begin(queueId: String): PlaybackRequestToken =
        PlaybackRequestToken(++generation, queueId)

    fun invalidate() {
        generation += 1L
    }

    fun isCurrent(token: PlaybackRequestToken, currentQueueId: String?): Boolean =
        token.generation == generation && token.queueId == currentQueueId
}

internal data class SystemQueueMetadata(
    val mediaId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artwork: ExtensionImage?
)

internal fun systemQueueMetadata(item: PlaybackQueueItem): SystemQueueMetadata =
    SystemQueueMetadata(
        mediaId = item.queueId,
        title = item.presentation.title,
        artist = item.presentation.artist,
        durationMs = item.presentation.durationMs.coerceAtLeast(0L),
        artwork = item.extensionLargeArtwork ?: item.extensionArtwork
    )

internal fun likeCommandTarget(item: PlaybackQueueItem?): PersistentTrack? =
    item?.persistentTrack

internal enum class PlaybackOrderConnectionAction {
    ApplyPendingRequest,
    ApplyInitialPreference,
    RestoreFromSession
}

internal fun playbackOrderConnectionAction(
    sessionQueueSize: Int,
    hasPendingRequest: Boolean
): PlaybackOrderConnectionAction = when {
    hasPendingRequest -> PlaybackOrderConnectionAction.ApplyPendingRequest
    sessionQueueSize > 0 -> PlaybackOrderConnectionAction.RestoreFromSession
    else -> PlaybackOrderConnectionAction.ApplyInitialPreference
}

internal fun shouldActivateForPrepare(
    currentQueueId: String?,
    resolvingQueueId: String?,
    resolvedQueueId: String?
): Boolean = currentQueueId != null &&
    currentQueueId != resolvingQueueId &&
    currentQueueId != resolvedQueueId

internal fun canApplyLogicalSnapshot(
    expectedQueueId: String?,
    snapshotQueueId: String?
): Boolean = expectedQueueId == null || expectedQueueId == snapshotQueueId

/** The latest unresolved seek is scoped to one logical queue entry. */
internal data class PendingLogicalSeek(
    val queueId: String,
    val positionMs: Long
)

internal fun desiredSeekPositionMs(positionMs: Long, durationMs: Long): Long = when {
    durationMs > 0L -> positionMs.coerceIn(0L, durationMs)
    else -> positionMs.coerceAtLeast(0L)
}

internal fun pendingSeekForTarget(
    pendingSeek: PendingLogicalSeek?,
    queueId: String?
): Long? = pendingSeek?.takeIf { it.queueId == queueId }?.positionMs

internal data class ControllerPlaybackSnapshot(
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val durationMs: Long
)

internal fun controllerPlaybackSnapshot(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long
): ControllerPlaybackSnapshot = ControllerPlaybackSnapshot(
    isPlaying = isPlaying,
    playWhenReady = playWhenReady,
    isBuffering = playbackState == Player.STATE_BUFFERING,
    positionMs = positionMs.coerceAtLeast(0L),
    bufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L),
    durationMs = durationMs.takeIf { it != C.TIME_UNSET && it >= 0L } ?: 0L
)
