package ink.tenqui.flowtone.ui.player

internal fun isFullscreenInteractionActive(
    fullscreen: Boolean,
    fullscreenProgress: Float
): Boolean = fullscreen || fullscreenProgress > 0.01f

internal fun shouldShowFullscreenContentExit(
    fullscreenContentMode: FullscreenContentMode,
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean
): Boolean =
    (
        fullscreenContentMode == FullscreenContentMode.AddToPlaylist ||
            fullscreenContentMode == FullscreenContentMode.SongInfo
        ) &&
        fullscreen &&
        expanded &&
        hasCurrentSong

internal fun shouldShowAddToPlaylistContent(
    fullscreenContentMode: FullscreenContentMode,
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean
): Boolean =
    fullscreenContentMode == FullscreenContentMode.AddToPlaylist &&
        fullscreen &&
        expanded &&
        hasCurrentSong

internal fun shouldShowSongInfoContent(
    fullscreenContentMode: FullscreenContentMode,
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean
): Boolean =
    fullscreenContentMode == FullscreenContentMode.SongInfo &&
        fullscreen &&
        expanded &&
        hasCurrentSong

internal fun isArtistPlaceholderActive(
    fullscreenContentMode: FullscreenContentMode,
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean
): Boolean =
    fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder &&
        fullscreen &&
        expanded &&
        hasCurrentSong

internal fun isArtistPlaceholderExitInProgress(
    fullscreenContentMode: FullscreenContentMode,
    artistPlaceholderArtists: List<String>,
    artistPlaceholderProgress: Float
): Boolean =
    fullscreenContentMode == FullscreenContentMode.Playback &&
        artistPlaceholderArtists.isNotEmpty() &&
        artistPlaceholderProgress > 0.001f

internal fun isArtistClickEnabled(
    isFullscreenPlayer: Boolean,
    fullscreenContentMode: FullscreenContentMode,
    fullscreenContentExitProgress: Float,
    artistPlaceholderProgress: Float
): Boolean =
    isFullscreenPlayer &&
        fullscreenContentMode == FullscreenContentMode.Playback &&
        fullscreenContentExitProgress <= 0.01f &&
        artistPlaceholderProgress <= 0.01f

internal fun shouldShowAddToPlaylistGrid(
    fullscreenContentMode: FullscreenContentMode,
    addToPlaylistSharedProgress: Float
): Boolean =
    fullscreenContentMode == FullscreenContentMode.AddToPlaylist ||
        addToPlaylistSharedProgress > 0.001f

internal fun shouldShowArtistPlaceholderOverlay(
    artistPlaceholderArtists: List<String>,
    artistPlaceholderActive: Boolean,
    artistPlaceholderProgress: Float
): Boolean =
    artistPlaceholderArtists.isNotEmpty() &&
        (artistPlaceholderActive || artistPlaceholderProgress > 0.001f)

internal fun shouldShowSongInfoOverlay(
    fullscreenContentMode: FullscreenContentMode,
    songInfoProgress: Float
): Boolean =
    fullscreenContentMode == FullscreenContentMode.SongInfo ||
        songInfoProgress > 0.001f

internal fun canAttachMiniPlayerLyricsHost(
    scene: MiniPlayerScene,
    fullscreenContentExitProgress: Float,
    artistPlaceholderProgress: Float
): Boolean =
    scene == MiniPlayerScene.FullscreenPlayback &&
        fullscreenContentExitProgress <= 0.01f &&
        artistPlaceholderProgress <= 0.01f
