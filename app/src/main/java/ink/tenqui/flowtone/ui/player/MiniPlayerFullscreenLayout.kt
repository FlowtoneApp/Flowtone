package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.lyrics.LyricsState

@Composable
internal fun BoxScope.MiniPlayerFullscreenLayout(
    imageRequest: ImageRequest?,
    waitForArtworkLoad: Boolean,
    playerUiState: PlayerUiState,
    lyricsState: LyricsState,
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
    artworkVisibilityProgress: Float,
    lyricsVisibilityProgress: Float,
    lyricsMetadataProgress: Float,
    titleColor: Color,
    artistColor: Color,
    controlIconColor: Color,
    progressTrackColor: Color,
    progressColor: Color,
    collapsedMetadataSwitchDirection: Int,
    artistClickEnabled: Boolean,
    fullscreenContentMode: FullscreenContentMode,
    addToPlaylistEnteredFromLyrics: Boolean,
    songInfoEnteredFromLyrics: Boolean,
    artistEnteredFromLyrics: Boolean,
    libraryPlaylists: List<LibraryPlaylistCard>,
    playlistIdsContainingCurrentSong: Set<String>,
    newlyCreatedPlaylistId: String?,
    addToPlaylistListState: LazyListState,
    isCurrentSongLiked: Boolean,
    expandedMoreMenu: Boolean,
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
    onChooseLyricsDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actualDensity = LocalDensity.current
    val layoutScale = fullscreenPlayerLayoutScale(playerWidth)
    val effectiveLayoutScale = lerpFloat(1f, layoutScale, fullscreenProgress)
    val fullscreenDensity = Density(
        density = actualDensity.density * effectiveLayoutScale,
        fontScale = actualDensity.fontScale
    )

    CompositionLocalProvider(LocalDensity provides fullscreenDensity) {
        val designPlayerWidth = playerWidth / effectiveLayoutScale
        val designCurrentHeight = currentHeight / effectiveLayoutScale
        val designVisualPanelHeight = visualPanelHeight / effectiveLayoutScale
        val designCollapsedHeight = collapsedHeight / effectiveLayoutScale
        val designMinimizedHeight = minimizedHeight / effectiveLayoutScale
        val designExpandedHeight = expandedHeight / effectiveLayoutScale
        val designExpandedArtworkSize = expandedArtworkSize / effectiveLayoutScale
        val designExpandedArtworkTop = expandedArtworkTop
        val designExpandedMetadataTop =
            designExpandedArtworkTop + designExpandedArtworkSize + 14.dp
        val designExpandedProgressTop = designExpandedMetadataTop + 76.dp
        val designExpandedControlsTop = designExpandedProgressTop + 58.dp
        val designFullscreenCoverCenterY = fullscreenCoverCenterY / effectiveLayoutScale
        val designFullscreenStationaryControlsOffsetY =
            fullscreenStationaryControlsOffsetY / effectiveLayoutScale
        val designFullscreenControlsLiftY = fullscreenControlsLiftY
        val designFullscreenProgressTrackWidth = designPlayerWidth * 0.76f
        val metrics = layoutMetrics.copy(
            fullscreenProgressTrackWidth = designFullscreenProgressTrackWidth,
            fullscreenArtworkX =
                (designPlayerWidth - designFullscreenProgressTrackWidth) / 2f,
            fullscreenMetadataTop =
                designFullscreenCoverCenterY +
                    designFullscreenProgressTrackWidth / 2f +
                    14.dp,
            playbackContentOffsetY =
                32.dp * layoutMetrics.fullscreenContentExitSharedProgress
        )
        val safeTopPadding = with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
        val lyricsAddToPlaylistProgress = if (addToPlaylistEnteredFromLyrics) {
            metrics.addToPlaylistSharedProgress
        } else {
            0f
        }
        val hidePlaybackSharedArtwork =
            addToPlaylistEnteredFromLyrics ||
                songInfoEnteredFromLyrics ||
                artistEnteredFromLyrics

        MorphArtworkLayer(
        imageRequest = imageRequest,
        waitForArtworkLoad = waitForArtworkLoad,
        progress = artworkAnimationProgress,
        scaleProgress = artworkScaleProgress,
        currentHeight = designCurrentHeight,
        viewportHeight = designCurrentHeight,
        collapsedHeight = designCollapsedHeight,
        playerWidth = designPlayerWidth,
        expandedArtworkSize = designExpandedArtworkSize,
        expandedArtworkTop = designExpandedArtworkTop,
        fullscreenProgress = fullscreenProgress,
        fullscreenArtworkSize = metrics.fullscreenProgressTrackWidth,
        fullscreenArtworkCenterY = designFullscreenCoverCenterY,
        contentExitProgress = metrics.artworkContentExitProgress,
        addToPlaylistArtworkSize = metrics.addToPlaylistArtworkSize,
        addToPlaylistArtworkX = metrics.addToPlaylistArtworkLeft,
        addToPlaylistArtworkTop = metrics.addToPlaylistArtworkTop,
        playbackScale = artworkPlaybackScale,
        playbackRotationDegrees = artworkPlaybackRotationDegrees,
        layerAlpha = if (hidePlaybackSharedArtwork) {
            0f
        } else {
            (1f - metrics.artistExitProgress) * artworkVisibilityProgress
        },
        layerTranslationY =
            16.dp *
                (1f - minimizedProgress) *
                (1f - fullscreenProgress) -
                24.dp * metrics.artistExitProgress -
                24.dp * (1f - artworkVisibilityProgress),
        modifier = modifier.align(Alignment.TopStart)
    )
        if (lyricsAddToPlaylistProgress > 0.001f) {
            MorphArtworkLayer(
                imageRequest = imageRequest,
                waitForArtworkLoad = waitForArtworkLoad,
                progress = 1f,
                scaleProgress = 1f,
                currentHeight = designCurrentHeight,
                viewportHeight = designCurrentHeight,
                collapsedHeight = designCollapsedHeight,
                playerWidth = designPlayerWidth,
                expandedArtworkSize = designExpandedArtworkSize,
                expandedArtworkTop = designExpandedArtworkTop,
                fullscreenProgress = 1f,
                fullscreenArtworkSize = metrics.fullscreenProgressTrackWidth,
                fullscreenArtworkCenterY = designFullscreenCoverCenterY,
                contentExitProgress = 1f,
                addToPlaylistArtworkSize = metrics.addToPlaylistArtworkSize,
                addToPlaylistArtworkX = metrics.addToPlaylistArtworkLeft,
                addToPlaylistArtworkTop = safeTopPadding + 56.dp,
                playbackScale = 1f,
                playbackRotationDegrees = 0f,
                layerAlpha = lyricsAddToPlaylistProgress,
                // 这个层在歌词页进入歌单时才创建；仅保留容器的位移和透明度动画，
                // 避免封面图片再次执行独立渐入。
                crossfadeArtworkImage = false,
                modifier = modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX =
                            ((-48).dp * (1f - lyricsAddToPlaylistProgress)).toPx()
                    }
            )
        }
        if (songInfoEnteredFromLyrics && songInfoProgress > 0.001f) {
            MorphArtworkLayer(
                imageRequest = imageRequest,
                waitForArtworkLoad = waitForArtworkLoad,
                progress = 1f,
                scaleProgress = 1f,
                currentHeight = designCurrentHeight,
                viewportHeight = designCurrentHeight,
                collapsedHeight = designCollapsedHeight,
                playerWidth = designPlayerWidth,
                expandedArtworkSize = designExpandedArtworkSize,
                expandedArtworkTop = designExpandedArtworkTop,
                fullscreenProgress = 1f,
                fullscreenArtworkSize = metrics.fullscreenProgressTrackWidth,
                fullscreenArtworkCenterY = designFullscreenCoverCenterY,
                contentExitProgress = 1f,
                addToPlaylistArtworkSize = metrics.addToPlaylistArtworkSize,
                addToPlaylistArtworkX = metrics.addToPlaylistArtworkLeft,
                addToPlaylistArtworkTop = metrics.addToPlaylistArtworkTop,
                playbackScale = 1f,
                playbackRotationDegrees = 0f,
                layerAlpha = songInfoProgress,
                modifier = modifier.align(Alignment.TopStart)
            )
        }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(designVisualPanelHeight)
            .align(Alignment.TopCenter)
    ) {
        SharedSongInfo(
            title = title,
            artist = artist,
            currentSongKey = playerUiState.currentSong?.id,
            progress = animationProgress,
            titleColor = titleColor,
            artistColor = artistColor,
            playerWidth = designPlayerWidth,
            minimizedProgress = minimizedProgress,
            minimizedHeight = designMinimizedHeight,
            collapsedHeight = designCollapsedHeight,
            expandedTop = designExpandedMetadataTop,
            fullscreenProgress = fullscreenProgress,
            fullscreenX = metrics.fullscreenArtworkX,
            fullscreenTop = metrics.fullscreenMetadataTop,
            lyricsMetadataProgress = lyricsMetadataProgress,
            contentExitProgress = if (
                addToPlaylistEnteredFromLyrics ||
                songInfoEnteredFromLyrics ||
                artistEnteredFromLyrics
            ) {
                1f
            } else {
                metrics.fullscreenContentExitSharedProgress
            },
            switchDirection = collapsedMetadataSwitchDirection,
            artistClickEnabled = artistClickEnabled,
            onArtistClick = onArtistClick,
            modifier = Modifier
                .align(Alignment.TopStart)
        )
        if (!addToPlaylistEnteredFromLyrics) {
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
        }
        // Keep the scrollable lyrics between the metadata block and the fullscreen action row.
        val lyricsTop = safeTopPadding + 56.dp + 60.dp + 12.dp
        val lyricsBottom = (
            designExpandedProgressTop +
                designFullscreenStationaryControlsOffsetY -
                designFullscreenControlsLiftY -
                56.dp
            ).coerceAtLeast(lyricsTop)
        val lyricsHeight = (lyricsBottom - lyricsTop).coerceAtLeast(0.dp)
        LyricsPlaceholderSongInfo(
            title = title,
            artist = artist,
            visibilityProgress = if (addToPlaylistEnteredFromLyrics) {
                1f
            } else {
                lyricsMetadataProgress
            },
            titleColor = titleColor,
            artistColor = artistColor,
            playerWidth = designPlayerWidth,
            addToPlaylistProgress = lyricsAddToPlaylistProgress,
            addToPlaylistTextStart = metrics.addToPlaylistTextStart,
            addToPlaylistTextWidth = metrics.addToPlaylistTextWidth,
            artistClickable = artistClickEnabled && isSelectableArtist(artist),
            onArtistClick = { onArtistClick(artist) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(5f)
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
                screenWidth = designPlayerWidth,
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
                .zIndex(if (expandedMoreMenu) 6f else 0f)
                .graphicsLayer {
                    alpha = metrics.playbackContentAlpha
                    translationY =
                        (designFullscreenStationaryControlsOffsetY -
                            designFullscreenControlsLiftY +
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
                    .padding(top = designExpandedProgressTop)
            )
            SharedPlaybackControls(
                progress = animationProgress,
                isPlaying = visualIsPlaying,
                iconColor = controlIconColor,
                screenWidth = designPlayerWidth,
                minimizedProgress = minimizedProgress,
                minimizedHeight = designMinimizedHeight,
                collapsedHeight = designCollapsedHeight,
                expandedTop = designExpandedControlsTop,
                fullscreenProgress = fullscreenProgress,
                controlsExitProgress = metrics.fullscreenContentExitSharedProgress,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                modifier = Modifier.align(Alignment.TopStart)
            )
            SideButtonsOverlay(
                progress = animationProgress,
                playerWidth = designPlayerWidth,
                currentHeight = designCurrentHeight,
                expandedHeight = designExpandedHeight,
                expandedProgressTop = designExpandedProgressTop,
                expandedControlsTop = designExpandedControlsTop,
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
        }
        // The host is deliberately constrained to the same middle band used for artwork.
        MiniPlayerLyricsHost(
            currentSong = playerUiState.currentSong,
            lyricsState = lyricsState,
            visibilityProgress = lyricsVisibilityProgress,
            onChooseLyricsDirectory = onChooseLyricsDirectory,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = lyricsTop)
                .fillMaxWidth()
                .height(lyricsHeight)
                .clipToBounds()
                .zIndex(4f)
        )
        MiniPlayerArtistHost(
            fullscreenContentMode = fullscreenContentMode,
            artistPlaceholderArtists = artistPlaceholderArtists,
            artistPlaceholderLocalSongs = artistPlaceholderLocalSongs,
            artistPlaceholderActive = artistPlaceholderActive,
            artistPlaceholderProgress = artistPlaceholderProgress,
            currentSong = playerUiState.currentSong,
            fullscreenSwipeThresholdPx = fullscreenSwipeThresholdPx,
            visualPanelHeight = designVisualPanelHeight,
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
}
