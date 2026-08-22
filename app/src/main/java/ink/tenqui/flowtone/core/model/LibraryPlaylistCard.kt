package ink.tenqui.flowtone.core.model

import android.net.Uri

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
    },
    /** Provider 或用户指定的封面；为空时由展示层回退到第一首歌曲的封面。 */
    val customArtworkUri: Uri? = null,
    val creatorName: String? = null,
    val description: String? = null
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
