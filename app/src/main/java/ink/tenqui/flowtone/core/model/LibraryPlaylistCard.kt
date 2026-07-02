package ink.tenqui.flowtone.core.model

data class LibraryPlaylistCard(
    val id: String,
    val title: String,
    val subtitle: String = "0 \u9996\u6b4c\u66f2",
    val order: Int,
    val widthDp: Float = 320f,
    val heightDp: Float = 236f
)
