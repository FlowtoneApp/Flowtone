package ink.tenqui.flowtone.ui.player.lyrics

import ink.tenqui.flowtone.ui.player.FullscreenContentMode

internal enum class FullscreenPlaybackContentMode {
    Artwork,
    LyricsPlaceholder
}

enum class LyricsBackgroundStyle {
    BlurredArtwork,
    FlowingClouds
}

internal fun isLyricsPlaybackContentActive(
    playbackContentMode: FullscreenPlaybackContentMode,
    fullscreenContentMode: FullscreenContentMode,
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean
): Boolean =
    playbackContentMode == FullscreenPlaybackContentMode.LyricsPlaceholder &&
        fullscreenContentMode == FullscreenContentMode.Playback &&
        fullscreen &&
        expanded &&
        hasCurrentSong
