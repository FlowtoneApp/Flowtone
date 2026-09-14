package ink.tenqui.flowtone.playback

import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ProviderSong

/**
 * Service、MediaSession 与 App 队列共同使用的逻辑播放项。
 *
 * 它只描述稳定身份和展示信息，不包含可能很快过期的在线播放 URL。
 */
data class PlaybackQueueItem(
    val queueId: String,
    val stableIdentity: String,
    val presentation: Song,
    val persistentTrack: PersistentTrack?,
    val runtimeProviderSong: ProviderSong? = null,
    val source: PlaybackSource = PlaybackSource.Unknown,
    val sourceIndex: Int = 0
) {
    val extensionArtwork: ExtensionImage?
        get() = runtimeProviderSong?.artwork

    val extensionLargeArtwork: ExtensionImage?
        get() = runtimeProviderSong?.largeArtwork

    val isOnline: Boolean
        get() = presentation.sourceType == SourceType.Online ||
            persistentTrack is PersistentTrack.Online ||
            runtimeProviderSong != null

    fun withSourceIndex(index: Int): PlaybackQueueItem = copy(sourceIndex = index)

    companion object {
        fun local(
            song: Song,
            source: PlaybackSource,
            sourceIndex: Int
        ): PlaybackQueueItem {
            val track = song.toPersistentTrack()
            return PlaybackQueueItem(
                queueId = queueId(track.identityKey, sourceIndex),
                stableIdentity = track.identityKey,
                presentation = song,
                persistentTrack = track,
                source = source,
                sourceIndex = sourceIndex
            )
        }

        fun provider(
            song: ProviderSong,
            presentation: Song,
            source: PlaybackSource,
            sourceIndex: Int
        ): PlaybackQueueItem {
            val persistentTrack = song.toPersistentTrackOrNull()
            val identity = persistentTrack?.identityKey
                ?: "runtime:${song.trackRef.extensionId}:${song.trackRef.opaqueId}"
            return PlaybackQueueItem(
                queueId = queueId(identity, sourceIndex),
                stableIdentity = identity,
                presentation = presentation,
                persistentTrack = persistentTrack,
                runtimeProviderSong = song,
                source = source,
                sourceIndex = sourceIndex
            )
        }

        fun persistent(
            track: PersistentTrack,
            presentation: Song,
            source: PlaybackSource,
            sourceIndex: Int,
            hydratedSong: ProviderSong? = null
        ): PlaybackQueueItem = PlaybackQueueItem(
            queueId = queueId(track.identityKey, sourceIndex),
            stableIdentity = track.identityKey,
            presentation = presentation,
            persistentTrack = track,
            runtimeProviderSong = hydratedSong,
            source = source,
            sourceIndex = sourceIndex
        )

        private fun queueId(identity: String, sourceIndex: Int): String =
            "$identity@$sourceIndex"
    }
}

fun ProviderSong.toPersistentTrackOrNull(): PersistentTrack.Online? {
    val identity = persistentTrackRef ?: return null
    return PersistentTrack.Online(
        sourceHost = identity.sourceHost,
        persistentId = identity.persistentId,
        cachedTitle = title,
        cachedArtist = artist,
        cachedDurationMs = durationMs
    )
}
