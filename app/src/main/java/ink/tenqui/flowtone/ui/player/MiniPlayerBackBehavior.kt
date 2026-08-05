package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.ui.player.lyrics.FullscreenPlaybackContentMode

internal fun shouldReturnFromLyricsToArtwork(
    fullscreenContentMode: FullscreenContentMode,
    fullscreenPlaybackContentMode: FullscreenPlaybackContentMode,
    fullscreenInteractionActive: Boolean
): Boolean =
    fullscreenInteractionActive &&
        fullscreenContentMode == FullscreenContentMode.Playback &&
        fullscreenPlaybackContentMode == FullscreenPlaybackContentMode.Lyrics

internal fun isPlaybackGestureContent(
    fullscreenContentMode: FullscreenContentMode
): Boolean = fullscreenContentMode == FullscreenContentMode.Playback

internal fun isFullscreenContentBackGestureContent(
    fullscreenContentMode: FullscreenContentMode
): Boolean =
    fullscreenContentMode == FullscreenContentMode.SongInfo ||
        fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder

internal fun isPlayerGesturesEnabled(
    playbackGesturesEnabled: Boolean,
    fullscreenContentBackGesturesEnabled: Boolean
): Boolean =
    playbackGesturesEnabled ||
        fullscreenContentBackGesturesEnabled

internal fun isAddToPlaylistBackGestureEnabled(
    fullscreenContentMode: FullscreenContentMode
): Boolean = fullscreenContentMode == FullscreenContentMode.AddToPlaylist

internal fun isArtistPlaceholderBackGestureEnabled(
    artistPlaceholderActive: Boolean,
    artistPlaceholderProgress: Float
): Boolean =
    artistPlaceholderActive &&
        artistPlaceholderProgress > 0.5f
