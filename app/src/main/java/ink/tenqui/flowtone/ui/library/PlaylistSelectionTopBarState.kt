package ink.tenqui.flowtone.ui.library

internal enum class PlaylistSelectionSource {
    LocalLibrary,
    LikedSongs,
    UserPlaylist,
    ReadOnly
}

/**
 * 歌单列表只发布顶栏需要展示的状态和用户意图，实际选择集合仍由列表页面持有。
 */
internal data class PlaylistSelectionTopBarState(
    val selectedCount: Int,
    val busy: Boolean,
    val onAddNext: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onDelete: (() -> Unit)?
)
