package ink.tenqui.flowtone.core.model

data class LibraryPlaylistCard(
    val id: String,
    val title: String,
    val subtitle: String = "0 \u9996\u6b4c\u66f2",
    val order: Int,
    val widthDp: Float = 320f,
    val heightDp: Float = 236f,
    val isSystem: Boolean = false,
    val appearanceColorKey: PlaylistAppearanceColorKey? = if (isSystem) {
        null
    } else {
        playlistAppearanceColorKeyForStableId(id)
    }
)

const val LikedSongsPlaylistId = "system_liked_songs"

fun likedSongsPlaylistCard(songCount: Int): LibraryPlaylistCard {
    return LibraryPlaylistCard(
        id = LikedSongsPlaylistId,
        title = "\u6211\u559c\u6b22\u7684\u97f3\u4e50",
        subtitle = "$songCount \u9996\u6b4c\u66f2",
        order = Int.MIN_VALUE,
        isSystem = true
    )
}

fun LibraryPlaylistCard.isLikedSongsPlaylist(): Boolean {
    return isSystem && id == LikedSongsPlaylistId
}
