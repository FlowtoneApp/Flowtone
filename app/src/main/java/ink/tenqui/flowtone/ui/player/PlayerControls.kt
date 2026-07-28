package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.playback.PlaybackOrderMode

@Composable
internal fun SharedPlaybackControls(
    progress: Float,
    isPlaying: Boolean,
    iconColor: Color,
    screenWidth: Dp,
    minimizedProgress: Float,
    minimizedHeight: Dp,
    collapsedHeight: Dp,
    expandedTop: Dp,
    fullscreenProgress: Float,
    controlsExitProgress: Float = 0f,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minimizedTouchSize = 40.dp
    val collapsedTouchSize = 48.dp
    val expandedPreviousNextTouchSize = 64.dp
    val expandedPlayPauseTouchSize = 80.dp
    val minimizedSpacing = 4.dp
    val collapsedSpacing = 8.dp
    val progressWidth = screenWidth * 0.76f
    val expandedSpacing = if (progressWidth / 6f < 36.dp) {
        progressWidth / 6f
    } else {
        36.dp
    }
    val baseTouchSize = lerpDp(minimizedTouchSize, collapsedTouchSize, minimizedProgress)
    val basePreviousNextIconSize = lerpDp(20.dp, 24.dp, minimizedProgress)
    val basePlayPauseIconSize = lerpDp(24.dp, 28.dp, minimizedProgress)
    val baseSpacing = lerpDp(minimizedSpacing, collapsedSpacing, minimizedProgress)
    val previousNextTouchSize = lerpDp(
        baseTouchSize,
        expandedPreviousNextTouchSize,
        progress
    )
    val playPauseTouchSize = lerpDp(baseTouchSize, expandedPlayPauseTouchSize, progress)
    val previousNextIconSize = lerpDp(basePreviousNextIconSize, 32.dp, progress)
    val playPauseIconSize = lerpDp(basePlayPauseIconSize, 42.dp, progress)
    val spacing = lerpDp(baseSpacing, expandedSpacing, progress)
    val baseControlsWidth = baseTouchSize * 3f + baseSpacing * 2f
    val controlsWidth = previousNextTouchSize * 2f + playPauseTouchSize + spacing * 2f
    val baseEndPadding = lerpDp(20.dp, 30.dp, minimizedProgress)
    val baseLeft = screenWidth - baseControlsWidth - baseEndPadding
    val baseHeight = lerpDp(minimizedHeight, collapsedHeight, minimizedProgress)
    val baseControlsY = (baseHeight - baseTouchSize) / 2f
    val currentTop = lerpDp(baseControlsY, expandedTop, progress)
    val progressWidthLeft = (screenWidth - progressWidth) / 2f
    val favoriteCenterX = progressWidthLeft + 24.dp
    val orderCenterX = progressWidthLeft + progressWidth - 24.dp
    val playPauseCenterX = screenWidth / 2f
    val previousCenterX = (favoriteCenterX + playPauseCenterX) / 2f
    val nextCenterX = (playPauseCenterX + orderCenterX) / 2f
    val collapsedPreviousX = baseLeft
    val collapsedPlayPauseX = baseLeft + baseTouchSize + baseSpacing
    val collapsedNextX = collapsedPlayPauseX + baseTouchSize + baseSpacing
    val expandedPreviousX = previousCenterX - expandedPreviousNextTouchSize / 2f
    val expandedPlayPauseX = playPauseCenterX - expandedPlayPauseTouchSize / 2f
    val expandedNextX = nextCenterX - expandedPreviousNextTouchSize / 2f
    val previousX = lerpDp(collapsedPreviousX, expandedPreviousX, progress)
    val playPauseX = lerpDp(collapsedPlayPauseX, expandedPlayPauseX, progress)
    val nextX = lerpDp(collapsedNextX, expandedNextX, progress)
    val fullscreenScale = lerpFloat(1f, 1.2f, fullscreenProgress)
    val controlsExit = controlsExitProgress.coerceIn(0f, 1f)
    val controlsEnabled = controlsExit <= 0.01f

    PlayerMainControls(
        isPlaying = isPlaying,
        iconColor = iconColor,
        screenWidth = screenWidth,
        previousNextTouchSize = previousNextTouchSize,
        playPauseTouchSize = playPauseTouchSize,
        previousNextIconSize = previousNextIconSize,
        playPauseIconSize = playPauseIconSize,
        previousX = previousX,
        playPauseX = playPauseX,
        nextX = nextX,
        currentTop = currentTop,
        fullscreenScale = fullscreenScale,
        controlsEnabled = controlsEnabled,
        onPlayPrevious = onPlayPrevious,
        onTogglePlayPause = onTogglePlayPause,
        onPlayNext = onPlayNext,
        modifier = modifier
    )
}

@Composable
internal fun SideButtonsOverlay(
    progress: Float,
    playerWidth: Dp,
    currentHeight: Dp,
    expandedHeight: Dp,
    expandedProgressTop: Dp,
    expandedControlsTop: Dp,
    hasCurrentSong: Boolean,
    isCurrentSongLiked: Boolean,
    playbackOrderMode: PlaybackOrderMode,
    iconColor: Color,
    fullscreenProgress: Float,
    controlsExitProgress: Float = 0f,
    moreMenuExpanded: Boolean,
    onMoreMenuExpandedChange: (Boolean) -> Unit,
    onToggleLiked: () -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongInfo: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enterProgress = ((progress - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val buttonSize = PlayerSideButtonSize
    val sideButtonHorizontalOffset = 48.dp
    val progressWidth = playerWidth * 0.76f
    val progressLeft = (playerWidth - progressWidth) / 2f
    val buttonEndY = expandedControlsTop + 16.dp
    val parentGrowthCompensationY = currentHeight - expandedHeight
    val buttonY = parentGrowthCompensationY + buttonEndY + 300.dp * (1f - enterProgress)
    val scale = lerpFloat(2.6f, 1.0f, enterProgress)
    val fullscreenScale = lerpFloat(1f, 1.08f, fullscreenProgress)
    val buttonAlpha = lerpFloat(0.18f, 1.0f, enterProgress)
    val sideButtonsVisualEnabled = hasCurrentSong && enterProgress > 0.55f
    val favoriteExitProgress = fullscreenProgress.coerceIn(0f, 1f)
    val favoriteEnterProgress = fullscreenProgress.coerceIn(0f, 1f)
    val queueEnterProgress = fullscreenProgress.coerceIn(0f, 1f)
    val controlsExit = controlsExitProgress.coerceIn(0f, 1f)
    val controlsEnabled = controlsExit <= 0.01f
    LaunchedEffect(hasCurrentSong, fullscreenProgress) {
        if (!hasCurrentSong || fullscreenProgress <= 0.01f) {
            onMoreMenuExpandedChange(false)
        }
    }
    fun collapseMoreMenu() {
        if (moreMenuExpanded) {
            onMoreMenuExpandedChange(false)
        }
    }

    Box(modifier = modifier) {
        val favoriteEndX = progressLeft
        val favoriteStartX = favoriteEndX - sideButtonHorizontalOffset
        val favoriteX = lerpDp(favoriteStartX, favoriteEndX, enterProgress)
        val bottomFavoriteAlpha = buttonAlpha * (1f - favoriteExitProgress)
        val bottomFavoriteVisible = bottomFavoriteAlpha > 0.01f
        val bottomFavoriteOffsetY = lerpDp(0.dp, (-24).dp, favoriteExitProgress)
        if (bottomFavoriteVisible) {
            FavoriteButton(
                liked = isCurrentSongLiked,
                enabled = hasCurrentSong && controlsEnabled,
                onClick = {
                    collapseMoreMenu()
                    onToggleLiked()
                },
                modifier = Modifier
                    .zIndex(4f)
                    .offset(x = favoriteX, y = buttonY + bottomFavoriteOffsetY)
                    .size(buttonSize)
                    .graphicsLayer {
                        scaleX = scale * fullscreenScale
                        scaleY = scale * fullscreenScale
                        alpha = bottomFavoriteAlpha
                    },
                visualEnabled = sideButtonsVisualEnabled
            )
        }

        val fullscreenMenuSpacing = 4.dp
        val fullscreenMenuX = progressLeft + progressWidth - buttonSize
        val fullscreenFavoriteX = fullscreenMenuX - buttonSize - fullscreenMenuSpacing
        val fullscreenFavoriteY =
            expandedProgressTop - 56.dp + lerpDp(12.dp, 0.dp, favoriteEnterProgress)
        val fullscreenActionsEnabled = hasCurrentSong && fullscreenProgress > 0.72f
        val fullscreenFavoriteVisible = favoriteEnterProgress > 0.01f
        val moreMenuVisible = hasCurrentSong
        val expandedMoreMenuVisible = moreMenuExpanded && moreMenuVisible
        Row(
            modifier = Modifier
                .offset(x = fullscreenFavoriteX, y = fullscreenFavoriteY)
                .size(height = buttonSize, width = buttonSize * 2f + fullscreenMenuSpacing),
            horizontalArrangement = Arrangement.spacedBy(fullscreenMenuSpacing)
        ) {
            FavoriteButton(
                liked = isCurrentSongLiked,
                enabled = hasCurrentSong && fullscreenFavoriteVisible && controlsEnabled,
                onClick = {
                    collapseMoreMenu()
                    onToggleLiked()
                },
                modifier = Modifier
                    .zIndex(4f)
                    .size(buttonSize)
                    .graphicsLayer {
                        alpha = favoriteEnterProgress
                    },
                visualEnabled = hasCurrentSong
            )
            AnimatedVisibility(
                visible = moreMenuVisible && !expandedMoreMenuVisible,
                enter = fullscreenMoreButtonEnterTransition(),
                exit = fullscreenMoreButtonExitTransition(),
                modifier = Modifier
                    .size(buttonSize)
                    .graphicsLayer {
                        alpha = favoriteEnterProgress
                    }
            ) {
                MoreMenuButton(
                    iconColor = iconColor,
                    enabled = fullscreenActionsEnabled && controlsEnabled,
                    onClick = {
                        onMoreMenuExpandedChange(true)
                    },
                    modifier = Modifier.size(buttonSize),
                    visualEnabled = hasCurrentSong
                )
            }
        }
        FullscreenMoreMenu(
            visible = expandedMoreMenuVisible,
            iconColor = iconColor,
            alpha = favoriteEnterProgress,
            enabled = controlsEnabled,
            onCollapse = {
                onMoreMenuExpandedChange(false)
            },
            onAddToPlaylist = {
                onAddToPlaylist()
            },
            onOpenSongInfo = {
                onOpenSongInfo()
            },
            modifier = Modifier.offset(
                x = fullscreenMenuX,
                y = fullscreenFavoriteY - buttonSize * 2f
            )
        )

        val orderEndX = progressLeft + progressWidth - buttonSize
        val orderStartX = orderEndX + sideButtonHorizontalOffset
        val orderX = lerpDp(orderStartX, orderEndX, enterProgress)
        PlaybackOrderButton(
            mode = playbackOrderMode,
            iconColor = iconColor,
            enabled = sideButtonsVisualEnabled && controlsEnabled,
            onClick = {
                collapseMoreMenu()
                onTogglePlaybackOrderMode()
            },
            modifier = Modifier
                .offset(x = orderX, y = buttonY)
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale * fullscreenScale
                    scaleY = scale * fullscreenScale
                    alpha = buttonAlpha
                },
            visualEnabled = sideButtonsVisualEnabled
        )

        if (queueEnterProgress > 0.01f) {
            val queueButtonEnabled =
                hasCurrentSong && fullscreenProgress > 0.72f && controlsEnabled
            QueueButton(
                iconColor = iconColor,
                enabled = queueButtonEnabled,
                onClick = {
                    collapseMoreMenu()
                    onOpenQueue()
                },
                modifier = Modifier
                    .offset(x = favoriteEndX, y = buttonY)
                    .size(buttonSize)
                    .graphicsLayer {
                        scaleX = fullscreenScale
                        scaleY = fullscreenScale
                        alpha = buttonAlpha * queueEnterProgress
                    },
                visualEnabled = hasCurrentSong
            )
        }
    }
}

