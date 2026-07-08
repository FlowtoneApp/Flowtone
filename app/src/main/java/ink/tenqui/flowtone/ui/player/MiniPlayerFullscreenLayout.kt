package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song

@Composable
internal fun BoxScope.MiniPlayerFullscreenLayout(
    imageRequest: ImageRequest?,
    waitForArtworkLoad: Boolean,
    playerUiState: PlayerUiState,
    title: String,
    artist: String,
    hasCurrentSong: Boolean,
    visualIsPlaying: Boolean,
    strictProgressBar: Boolean,
    currentHeight: Dp,
    visualPanelHeight: Dp,
    collapsedHeight: Dp,
    minimizedHeight: Dp,
    expandedHeight: Dp,
    expandedArtworkSize: Dp,
    expandedArtworkTop: Dp,
    expandedMetadataTop: Dp,
    expandedProgressTop: Dp,
    expandedControlsTop: Dp,
    playerWidth: Dp,
    fullscreenProgress: Float,
    animationProgress: Float,
    artworkAnimationProgress: Float,
    artworkScaleProgress: Float,
    minimizedProgress: Float,
    fullscreenCoverCenterY: Dp,
    fullscreenStationaryControlsOffsetY: Dp,
    fullscreenControlsLiftY: Dp,
    layoutMetrics: MiniPlayerFullscreenLayoutMetrics,
    artworkPlaybackScale: Float,
    artworkPlaybackRotationDegrees: Float,
    titleColor: Color,
    artistColor: Color,
    controlIconColor: Color,
    progressTrackColor: Color,
    progressColor: Color,
    collapsedMetadataSwitchDirection: Int,
    artistClickEnabled: Boolean,
    fullscreenContentMode: FullscreenContentMode,
    libraryPlaylists: List<LibraryPlaylistCard>,
    playlistIdsContainingCurrentSong: Set<String>,
    newlyCreatedPlaylistId: String?,
    addToPlaylistListState: LazyListState,
    isCurrentSongLiked: Boolean,
    expandedMoreMenu: Boolean,
    lyricsHostCanAttach: Boolean,
    fullscreen: Boolean,
    expanded: Boolean,
    artistPlaceholderArtists: List<String>,
    artistPlaceholderLocalSongs: List<Song>,
    artistPlaceholderActive: Boolean,
    artistPlaceholderProgress: Float,
    fullscreenSwipeThresholdPx: Float,
    songInfoProgress: Float,
    callbacks: MiniPlayerCallbacks,
    collapseInteractionSource: MutableInteractionSource,
    onArtistClick: (String) -> Unit,
    onNewPlaylistCreateAnimationFinished: (String) -> Unit,
    onDismissAddToPlaylistAtTop: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (LibraryPlaylistCard) -> Unit,
    onLockPlayPauseVisual: (Boolean) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onMoreMenuExpandedChange: (Boolean) -> Unit,
    onToggleLiked: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongInfo: () -> Unit,
    onOpenQueue: () -> Unit,
    onArtistHostBack: () -> Unit,
    onArtistHostArtistClick: (String) -> Unit,
    onCollapseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = layoutMetrics

    MorphArtworkLayer(
        imageRequest = imageRequest,
        waitForArtworkLoad = waitForArtworkLoad,
        progress = artworkAnimationProgress,
        scaleProgress = artworkScaleProgress,
        currentHeight = currentHeight,
        viewportHeight = currentHeight,
        collapsedHeight = collapsedHeight,
        playerWidth = playerWidth,
        expandedArtworkSize = expandedArtworkSize,
        expandedArtworkTop = expandedArtworkTop,
        fullscreenProgress = fullscreenProgress,
        fullscreenArtworkSize = metrics.fullscreenProgressTrackWidth,
        fullscreenArtworkCenterY = fullscreenCoverCenterY,
        contentExitProgress = metrics.artworkContentExitProgress,
        addToPlaylistArtworkSize = metrics.addToPlaylistArtworkSize,
        addToPlaylistArtworkX = metrics.addToPlaylistArtworkLeft,
        addToPlaylistArtworkTop = metrics.addToPlaylistArtworkTop,
        playbackScale = artworkPlaybackScale,
        playbackRotationDegrees = artworkPlaybackRotationDegrees,
        layerAlpha = 1f - metrics.artistExitProgress,
        layerTranslationY =
            16.dp *
                (1f - minimizedProgress) *
                (1f - fullscreenProgress) -
                24.dp * metrics.artistExitProgress,
        modifier = modifier.align(Alignment.TopStart)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(visualPanelHeight)
            .align(Alignment.TopCenter)
    ) {
        SharedSongInfo(
            title = title,
            artist = artist,
            currentSongKey = playerUiState.currentSong?.id,
            progress = animationProgress,
            titleColor = titleColor,
            artistColor = artistColor,
            playerWidth = playerWidth,
            minimizedProgress = minimizedProgress,
            minimizedHeight = minimizedHeight,
            collapsedHeight = collapsedHeight,
            expandedTop = expandedMetadataTop,
            fullscreenProgress = fullscreenProgress,
            fullscreenX = metrics.fullscreenArtworkX,
            fullscreenTop = metrics.fullscreenMetadataTop,
            contentExitProgress = metrics.fullscreenContentExitSharedProgress,
            switchDirection = collapsedMetadataSwitchDirection,
            artistClickEnabled = artistClickEnabled,
            onArtistClick = onArtistClick,
            modifier = Modifier
                .align(Alignment.TopStart)
        )
        AddToPlaylistItemSongInfo(
            title = title,
            artist = artist,
            progress = metrics.addToPlaylistSharedProgress,
            titleColor = titleColor,
            artistColor = artistColor,
            width = metrics.addToPlaylistTextWidth,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = metrics.addToPlaylistTextStart,
                    y = metrics.addToPlaylistArtworkTop
                )
        )
        if (
            shouldShowAddToPlaylistGrid(
                fullscreenContentMode = fullscreenContentMode,
                addToPlaylistSharedProgress = metrics.addToPlaylistSharedProgress
            )
        ) {
            AddToPlaylistPlaylistGrid(
                playlists = libraryPlaylists,
                playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
                newlyCreatedPlaylistId = newlyCreatedPlaylistId,
                onNewPlaylistCreateAnimationFinished = onNewPlaylistCreateAnimationFinished,
                listState = addToPlaylistListState,
                progress = metrics.addToPlaylistSharedProgress,
                screenWidth = playerWidth,
                pullToDismissEnabled = isAddToPlaylistBackGestureEnabled(fullscreenContentMode),
                onDismissAtTop = onDismissAddToPlaylistAtTop,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onPlaylistClick = onPlaylistClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = metrics.addToPlaylistCardsTop)
                    .fillMaxWidth()
                    .height(metrics.addToPlaylistCardsHeight)
                    .padding(horizontal = 20.dp)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = metrics.playbackContentAlpha
                    translationY =
                        (fullscreenStationaryControlsOffsetY -
                            fullscreenControlsLiftY +
                            metrics.playbackContentOffsetY).toPx()
                }
        ) {
            ExpandedOnlyContent(
                progress = animationProgress,
                positionMs = playerUiState.positionMs,
                durationMs = playerUiState.durationMs,
                isPlaying = playerUiState.isPlaying,
                isPlayingForVisualLock = visualIsPlaying,
                strictProgressBar = strictProgressBar,
                currentSongKey = playerUiState.currentSong?.id,
                hasCurrentSong = hasCurrentSong,
                progressTrackColor = progressTrackColor,
                progressColor = progressColor,
                fullscreenProgress = fullscreenProgress,
                onSeekTo = callbacks.onSeekTo,
                onLockPlayPauseVisual = onLockPlayPauseVisual,
                onScrubbingChange = onScrubbingChange,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = expandedProgressTop)
            )
            SharedPlaybackControls(
                progress = animationProgress,
                isPlaying = visualIsPlaying,
                iconColor = controlIconColor,
                screenWidth = playerWidth,
                minimizedProgress = minimizedProgress,
                minimizedHeight = minimizedHeight,
                collapsedHeight = collapsedHeight,
                expandedTop = expandedControlsTop,
                fullscreenProgress = fullscreenProgress,
                controlsExitProgress = metrics.fullscreenContentExitSharedProgress,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                modifier = Modifier.align(Alignment.TopStart)
            )
            SideButtonsOverlay(
                progress = animationProgress,
                playerWidth = playerWidth,
                currentHeight = currentHeight,
                expandedHeight = expandedHeight,
                expandedProgressTop = expandedProgressTop,
                expandedControlsTop = expandedControlsTop,
                hasCurrentSong = hasCurrentSong,
                isCurrentSongLiked = isCurrentSongLiked,
                playbackOrderMode = playerUiState.playbackOrderMode,
                iconColor = controlIconColor,
                fullscreenProgress = fullscreenProgress,
                controlsExitProgress = metrics.fullscreenContentExitSharedProgress,
                moreMenuExpanded = expandedMoreMenu,
                onMoreMenuExpandedChange = onMoreMenuExpandedChange,
                onToggleLiked = onToggleLiked,
                onAddToPlaylist = onAddToPlaylist,
                onOpenSongInfo = onOpenSongInfo,
                onOpenQueue = onOpenQueue,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxSize()
                    .zIndex(2f),
                onTogglePlaybackOrderMode = callbacks.onTogglePlaybackOrderMode
            )
            MiniPlayerLyricsHost(
                currentSong = playerUiState.currentSong,
                positionMs = playerUiState.positionMs,
                playbackProgress = animationProgress,
                fullscreenProgress = fullscreenProgress,
                fullscreen = fullscreen && expanded && hasCurrentSong,
                interactionsEnabled = lyricsHostCanAttach,
                onLyricSeekRequested = callbacks.onSeekTo,
                modifier = Modifier.matchParentSize()
            )
        }
        MiniPlayerArtistHost(
            fullscreenContentMode = fullscreenContentMode,
            artistPlaceholderArtists = artistPlaceholderArtists,
            artistPlaceholderLocalSongs = artistPlaceholderLocalSongs,
            artistPlaceholderActive = artistPlaceholderActive,
            artistPlaceholderProgress = artistPlaceholderProgress,
            currentSong = playerUiState.currentSong,
            fullscreenSwipeThresholdPx = fullscreenSwipeThresholdPx,
            visualPanelHeight = visualPanelHeight,
            songInfoProgress = songInfoProgress,
            songInfoTopPadding = metrics.addToPlaylistCardsTop,
            onBack = onArtistHostBack,
            onArtistClick = onArtistHostArtistClick,
            onSongClick = callbacks.onPlayArtistSongQueue
        )
    }
    FullscreenCollapseArrow(
        progress = fullscreenProgress,
        interactionSource = collapseInteractionSource,
        onClick = onCollapseClick,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(10f)
    )
}
