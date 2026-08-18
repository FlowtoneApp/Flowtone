package ink.tenqui.flowtone.core.model

import android.net.Uri

/** Flowtone 自己保存的歌曲身份；不包含扩展 runtime 引用或任何播放资源。 */
sealed interface PersistentTrack {
    val cachedTitle: String
    val cachedArtist: String
    val cachedDurationMs: Long?
    val identityKey: String

    data class Local(
        val songId: String,
        override val cachedTitle: String = "",
        override val cachedArtist: String = "",
        override val cachedDurationMs: Long? = null
    ) : PersistentTrack {
        override val identityKey: String = "local:$songId"
    }

    class Online(
        sourceHost: String,
        val persistentId: String,
        override val cachedTitle: String,
        override val cachedArtist: String,
        override val cachedDurationMs: Long? = null
    ) : PersistentTrack {
        val sourceHost: String = normalizeMusicSourceHost(sourceHost)
        override val identityKey: String = "online:$sourceHost:$persistentId"

        override fun equals(other: Any?): Boolean =
            other is Online && sourceHost == other.sourceHost && persistentId == other.persistentId

        override fun hashCode(): Int = 31 * sourceHost.hashCode() + persistentId.hashCode()

        override fun toString(): String =
            "Online(sourceHost=$sourceHost, persistentId=$persistentId, cachedTitle=$cachedTitle)"
    }
}

fun normalizeMusicSourceHost(value: String): String = value
    .trim()
    .lowercase()
    .removeSuffix("/")

fun Song.toPersistentTrack(): PersistentTrack.Local = PersistentTrack.Local(
    songId = id.toString(),
    cachedTitle = title,
    cachedArtist = artist,
    cachedDurationMs = durationMs
)

fun PersistentTrack.toPresentationSong(localSongs: List<Song>): Song? = when (this) {
    is PersistentTrack.Local -> localSongs.firstOrNull { it.id.toString() == songId }
    is PersistentTrack.Online -> {
        val stableId = identityKey.hashCode().toLong().let { kotlin.math.abs(it) + 1L }
        Song(
            id = -stableId,
            sourceType = SourceType.Online,
            title = cachedTitle.ifBlank { "未知歌曲" },
            artist = cachedArtist.ifBlank { "未知艺术家" },
            durationMs = cachedDurationMs ?: 0L,
            uri = Uri.Builder().scheme("flowtone-persistent").authority("presentation")
                .appendPath(stableId.toString()).build(),
            displayName = cachedTitle
        )
    }
}
