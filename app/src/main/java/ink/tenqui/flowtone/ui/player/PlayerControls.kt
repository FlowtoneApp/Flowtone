package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    Box(
        modifier = modifier
            .width(screenWidth)
            .height(playPauseTouchSize)
            .graphicsLayer {
                translationY = currentTop.toPx()
            }
    ) {
        TransparentControlButton(
            onClick = onPlayPrevious,
            modifier = Modifier
                .size(previousNextTouchSize)
                .graphicsLayer {
                    translationX = previousX.toPx()
                    translationY = ((playPauseTouchSize - previousNextTouchSize) / 2f).toPx()
                    scaleX = fullscreenScale
                    scaleY = fullscreenScale
                }
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "\u4e0a\u4e00\u66f2",
                tint = iconColor,
                modifier = Modifier.size(previousNextIconSize)
            )
        }
        TransparentControlButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(playPauseTouchSize)
                .graphicsLayer {
                    translationX = playPauseX.toPx()
                    scaleX = fullscreenScale
                    scaleY = fullscreenScale
                }
        ) {
            Icon(
                imageVector = if (isPlaying) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (isPlaying) {
                    "\u6682\u505c"
                } else {
                    "\u64ad\u653e"
                },
                tint = iconColor,
                modifier = Modifier.size(playPauseIconSize)
            )
        }
        TransparentControlButton(
            onClick = onPlayNext,
            modifier = Modifier
                .size(previousNextTouchSize)
                .graphicsLayer {
                    translationX = nextX.toPx()
                    translationY = ((playPauseTouchSize - previousNextTouchSize) / 2f).toPx()
                    scaleX = fullscreenScale
                    scaleY = fullscreenScale
                }
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "\u4e0b\u4e00\u66f2",
                tint = iconColor,
                modifier = Modifier.size(previousNextIconSize)
            )
        }
    }
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
    onToggleLiked: () -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enterProgress = ((progress - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val buttonSize = 48.dp
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

    Box(modifier = modifier) {
        val favoriteEndX = progressLeft
        val favoriteStartX = favoriteEndX - sideButtonHorizontalOffset
        val favoriteX = lerpDp(favoriteStartX, favoriteEndX, enterProgress)
        val bottomFavoriteAlpha = buttonAlpha * (1f - favoriteExitProgress)
        val bottomFavoriteOffsetY = lerpDp(0.dp, (-24).dp, favoriteExitProgress)
        FavoriteButton(
            liked = isCurrentSongLiked,
            enabled = sideButtonsVisualEnabled && fullscreenProgress < 0.45f,
            onClick = onToggleLiked,
            modifier = Modifier
                .offset(x = favoriteX, y = buttonY + bottomFavoriteOffsetY)
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale * fullscreenScale
                    scaleY = scale * fullscreenScale
                    alpha = bottomFavoriteAlpha
                },
            visualEnabled = sideButtonsVisualEnabled
        )

        val fullscreenFavoriteX = progressLeft + progressWidth - buttonSize
        val fullscreenFavoriteY =
            expandedProgressTop - 56.dp + lerpDp(12.dp, 0.dp, favoriteEnterProgress)
        FavoriteButton(
            liked = isCurrentSongLiked,
            enabled = hasCurrentSong && fullscreenProgress > 0.72f,
            onClick = onToggleLiked,
            modifier = Modifier
                .offset(x = fullscreenFavoriteX, y = fullscreenFavoriteY)
                .size(buttonSize)
                .graphicsLayer {
                    alpha = favoriteEnterProgress
                },
            visualEnabled = hasCurrentSong
        )

        val orderEndX = progressLeft + progressWidth - buttonSize
        val orderStartX = orderEndX + sideButtonHorizontalOffset
        val orderX = lerpDp(orderStartX, orderEndX, enterProgress)
        PlaybackOrderButton(
            mode = playbackOrderMode,
            iconColor = iconColor,
            enabled = sideButtonsVisualEnabled,
            onClick = onTogglePlaybackOrderMode,
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

        QueueButton(
            iconColor = iconColor,
            enabled = hasCurrentSong && fullscreenProgress > 0.72f,
            onClick = onOpenQueue,
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

@Composable
internal fun FavoriteButton(
    liked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = if (liked) {
                Icons.Rounded.Favorite
            } else {
                Icons.Outlined.FavoriteBorder
            },
            contentDescription = if (liked) {
                "\u5df2\u559c\u6b22"
            } else {
                "\u6dfb\u52a0\u559c\u6b22"
            },
            tint = if (liked) {
                Color(0xFFFF4D67)
            } else {
                Color.White.copy(alpha = 0.92f)
            },
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
internal fun PlaybackOrderButton(
    mode: PlaybackOrderMode,
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    val icon = when (mode) {
        PlaybackOrderMode.Sequence -> Icons.Rounded.Repeat
        PlaybackOrderMode.RepeatOne -> Icons.Rounded.RepeatOne
        PlaybackOrderMode.Shuffle -> Icons.Rounded.Shuffle
    }
    val description = when (mode) {
        PlaybackOrderMode.Sequence -> "\u987a\u5e8f\u64ad\u653e"
        PlaybackOrderMode.RepeatOne -> "\u5355\u66f2\u5faa\u73af"
        PlaybackOrderMode.Shuffle -> "\u968f\u673a\u64ad\u653e"
    }
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = iconColor,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
internal fun QueueButton(
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "\u64ad\u653e\u961f\u5217",
            tint = iconColor,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
internal fun TransparentControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(52.dp),
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
