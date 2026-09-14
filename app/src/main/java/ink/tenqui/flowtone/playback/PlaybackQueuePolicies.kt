package ink.tenqui.flowtone.playback

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
