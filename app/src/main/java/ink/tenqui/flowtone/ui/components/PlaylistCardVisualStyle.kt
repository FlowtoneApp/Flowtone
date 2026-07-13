package ink.tenqui.flowtone.ui.components

import androidx.compose.runtime.Immutable
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist

internal enum class PlaylistCardVisualType {
    LocalLibrary,
    LikedMusic,
    CreatePlaylist,
    UserPlaylist,
    Default
}

internal enum class PlaylistCardBackgroundType {
    Static,
    FlowCloud
}

@Immutable
internal data class PlaylistCardVisualStyle(
    val type: PlaylistCardVisualType,
    val backgroundType: PlaylistCardBackgroundType,
    val usesFlowCloudSpeed: Boolean
)

internal fun playlistCardVisualTypeFor(
    playlist: LibraryPlaylistCard
): PlaylistCardVisualType {
    return when {
        playlist.isLikedSongsPlaylist() -> PlaylistCardVisualType.LikedMusic
        playlist.isSystem -> PlaylistCardVisualType.Default
        else -> PlaylistCardVisualType.UserPlaylist
    }
}

internal fun playlistCardVisualStyleFor(
    playlist: LibraryPlaylistCard
): PlaylistCardVisualStyle {
    return playlistCardVisualStyleFor(playlistCardVisualTypeFor(playlist))
}

internal fun playlistCardVisualStyleFor(
    type: PlaylistCardVisualType
): PlaylistCardVisualStyle {
    return when (type) {
        PlaylistCardVisualType.LocalLibrary -> PlaylistCardVisualStyle(
            type = type,
            backgroundType = PlaylistCardBackgroundType.Static,
            usesFlowCloudSpeed = false
        )

        PlaylistCardVisualType.LikedMusic -> PlaylistCardVisualStyle(
            type = type,
            backgroundType = PlaylistCardBackgroundType.FlowCloud,
            usesFlowCloudSpeed = true
        )

        PlaylistCardVisualType.CreatePlaylist -> PlaylistCardVisualStyle(
            type = type,
            backgroundType = PlaylistCardBackgroundType.Static,
            usesFlowCloudSpeed = false
        )

        PlaylistCardVisualType.UserPlaylist -> PlaylistCardVisualStyle(
            type = type,
            backgroundType = PlaylistCardBackgroundType.Static,
            usesFlowCloudSpeed = false
        )

        PlaylistCardVisualType.Default -> PlaylistCardVisualStyle(
            type = type,
            backgroundType = PlaylistCardBackgroundType.Static,
            usesFlowCloudSpeed = false
        )
    }
}
