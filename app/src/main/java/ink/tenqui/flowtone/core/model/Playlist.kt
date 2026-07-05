package ink.tenqui.flowtone.core.model

enum class PlaylistCardStyle {
    SQUARE,
    WIDE,
    LARGE
}

data class Playlist(
    val id: String,
    val title: String,
    val subtitle: String = "0 首歌曲",
    val cardStyle: PlaylistCardStyle = PlaylistCardStyle.SQUARE,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class PlaylistSongEntry(
    val playlistId: String,
    val songId: Long,
    val addedAt: Long,
    val order: Int,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val artworkUriSnapshot: String?
)
