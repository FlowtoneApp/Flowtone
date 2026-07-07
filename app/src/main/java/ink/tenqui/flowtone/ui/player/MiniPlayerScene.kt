package ink.tenqui.flowtone.ui.player

internal enum class FullscreenContentMode {
    Playback,
    AddToPlaylist,
    SongInfo,
    ArtistPlaceholder
}

internal enum class MiniPlayerScene {
    Ordinary,
    Expanded,
    FullscreenPlayback,
    FullscreenAddToPlaylist,
    FullscreenSongInfo,
    ArtistSelection,
    QueueOpen
}

internal fun miniPlayerScene(
    expanded: Boolean,
    fullscreen: Boolean,
    hasCurrentSong: Boolean,
    fullscreenContentMode: FullscreenContentMode,
    artistPlaceholderActive: Boolean,
    artistPlaceholderProgress: Float,
    showQueueSheet: Boolean
): MiniPlayerScene {
    if (showQueueSheet) {
        return MiniPlayerScene.QueueOpen
    }
    if (artistPlaceholderActive || artistPlaceholderProgress > 0.001f) {
        return MiniPlayerScene.ArtistSelection
    }
    if (fullscreen && expanded && hasCurrentSong) {
        return when (fullscreenContentMode) {
            FullscreenContentMode.Playback -> MiniPlayerScene.FullscreenPlayback
            FullscreenContentMode.AddToPlaylist -> MiniPlayerScene.FullscreenAddToPlaylist
            FullscreenContentMode.SongInfo -> MiniPlayerScene.FullscreenSongInfo
            FullscreenContentMode.ArtistPlaceholder -> MiniPlayerScene.ArtistSelection
        }
    }
    if (expanded) {
        return MiniPlayerScene.Expanded
    }
    return MiniPlayerScene.Ordinary
}
