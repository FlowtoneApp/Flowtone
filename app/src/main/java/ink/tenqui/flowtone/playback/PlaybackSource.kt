package ink.tenqui.flowtone.playback

import java.util.Locale

enum class PlaybackSourceType(val label: String) {
    UserPlaylist("歌单"),
    LikedSongs("我喜欢的音乐"),
    LocalLibrary("本地曲库"),
    Artist("艺术家"),
    Album("专辑"),
    Other("其他"),
    Unknown("未知来源")
}

data class PlaybackSource(
    val type: PlaybackSourceType,
    val key: String,
    val sourceId: String? = null,
    val displayName: String
) {
    companion object {
        val Unknown = PlaybackSource(
            type = PlaybackSourceType.Unknown,
            key = "unknown",
            displayName = "未知来源"
        )

        val LocalLibrary = PlaybackSource(
            type = PlaybackSourceType.LocalLibrary,
            key = "local_library",
            displayName = "本地曲库"
        )

        val LikedSongs = PlaybackSource(
            type = PlaybackSourceType.LikedSongs,
            key = "liked_songs",
            sourceId = "system_liked_songs",
            displayName = "我喜欢的音乐"
        )

        fun userPlaylist(
            playlistId: String,
            displayName: String
        ): PlaybackSource {
            val safeId = playlistId.trim().ifBlank { return Unknown }
            return PlaybackSource(
                type = PlaybackSourceType.UserPlaylist,
                key = "playlist:$safeId",
                sourceId = safeId,
                displayName = displayName.trim().ifBlank { "歌单" }
            )
        }

        fun artist(artistName: String): PlaybackSource {
            val displayName = artistName.trim()
            if (displayName.isBlank()) {
                return Unknown
            }
            val stableId = displayName.lowercase(Locale.ROOT)
            return PlaybackSource(
                type = PlaybackSourceType.Artist,
                key = "artist:$stableId",
                sourceId = stableId,
                displayName = displayName
            )
        }

        fun album(
            albumId: Long?,
            displayName: String
        ): PlaybackSource {
            val safeId = albumId?.takeIf { it > 0L }?.toString() ?: return Unknown
            return PlaybackSource(
                type = PlaybackSourceType.Album,
                key = "album:$safeId",
                sourceId = safeId,
                displayName = displayName.trim().ifBlank { "专辑" }
            )
        }

        fun other(displayName: String): PlaybackSource {
            val safeName = displayName.trim().ifBlank { "其他来源" }
            val stableId = safeName.lowercase(Locale.ROOT)
            return PlaybackSource(
                type = PlaybackSourceType.Other,
                key = "other:$stableId",
                sourceId = stableId,
                displayName = safeName
            )
        }
    }
}
