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
    collapsedHeight: Dp,
    expandedTop: Dp,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val collapsedTouchSize = 48.dp
    val expandedPreviousNextTouchSize = 64.dp
    val expandedPlayPauseTouchSize = 80.dp
    val collapsedSpacing = 8.dp
    val progressWidth = screenWidth * 0.76f
    val expandedSpacing = if (progressWidth / 6f < 36.dp) {
        progressWidth / 6f
    } else {
        36.dp
    }
    val previousNextTouchSize = lerpDp(collapsedTouchSize, expandedPreviousNextTouchSize, progress)
    val playPauseTouchSize = lerpDp(collapsedTouchSize, expandedPlayPauseTouchSize, progress)
    val previousNextIconSize = lerpDp(24.dp, 32.dp, progress)
    val playPauseIconSize = lerpDp(28.dp, 42.dp, progress)
    val spacing = lerpDp(collapsedSpacing, expandedSpacing, progress)
    val collapsedControlsWidth = collapsedTouchSize * 3f + collapsedSpacing * 2f
    val controlsWidth = previousNextTouchSize * 2f + playPauseTouchSize + spacing * 2f
    val collapsedLeft = screenWidth - collapsedControlsWidth - 30.dp
    val collapsedControlsY = (collapsedHeight - collapsedTouchSize) / 2f
    val currentTop = lerpDp(collapsedControlsY, expandedTop, progress)
    val progressLeft = (screenWidth - progressWidth) / 2f
    val favoriteCenterX = progressLeft + 24.dp
    val orderCenterX = progressLeft + progressWidth - 24.dp
    val playPauseCenterX = screenWidth / 2f
    val previousCenterX = (favoriteCenterX + playPauseCenterX) / 2f
    val nextCenterX = (playPauseCenterX + orderCenterX) / 2f
    val collapsedPreviousX = collapsedLeft
    val collapsedPlayPauseX = collapsedLeft + collapsedTouchSize + collapsedSpacing
    val collapsedNextX = collapsedPlayPauseX + collapsedTouchSize + collapsedSpacing
    val expandedPreviousX = previousCenterX - expandedPreviousNextTouchSize / 2f
    val expandedPlayPauseX = playPauseCenterX - expandedPlayPauseTouchSize / 2f
    val expandedNextX = nextCenterX - expandedPreviousNextTouchSize / 2f
    val previousX = lerpDp(collapsedPreviousX, expandedPreviousX, progress)
    val playPauseX = lerpDp(collapsedPlayPauseX, expandedPlayPauseX, progress)
    val nextX = lerpDp(collapsedNextX, expandedNextX, progress)

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
    expandedControlsTop: Dp,
    hasCurrentSong: Boolean,
    isCurrentSongLiked: Boolean,
    playbackOrderMode: PlaybackOrderMode,
    iconColor: Color,
    onToggleLiked: () -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
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
    val buttonAlpha = lerpFloat(0.18f, 1.0f, enterProgress)

    Box(modifier = modifier) {
        val favoriteEndX = progressLeft
        val favoriteStartX = favoriteEndX - sideButtonHorizontalOffset
        val favoriteX = lerpDp(favoriteStartX, favoriteEndX, enterProgress)
        Box(
            modifier = Modifier
                .offset(x = favoriteX, y = buttonY)
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = buttonAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            FavoriteButton(
                liked = isCurrentSongLiked,
                enabled = hasCurrentSong && enterProgress > 0.55f,
                onClick = onToggleLiked,
                modifier = Modifier.size(buttonSize)
            )
        }

        val orderEndX = progressLeft + progressWidth - buttonSize
        val orderStartX = orderEndX + sideButtonHorizontalOffset
        val orderX = lerpDp(orderStartX, orderEndX, enterProgress)
        Box(
            modifier = Modifier
                .offset(x = orderX, y = buttonY)
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = buttonAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            PlaybackOrderButton(
                mode = playbackOrderMode,
                iconColor = iconColor,
                enabled = hasCurrentSong && enterProgress > 0.55f,
                onClick = onTogglePlaybackOrderMode,
                modifier = Modifier.size(buttonSize)
            )
        }
    }
}

@Composable
internal fun FavoriteButton(
    liked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = if (liked) {
                Icons.Rounded.Favorite
            } else {
                Icons.Outlined.FavoriteBorder
            },
            contentDescription = if (liked) {
                "鍙栨秷鍠滄"
            } else {
                "鍠滄"
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
    modifier: Modifier = Modifier
) {
    val icon = when (mode) {
        PlaybackOrderMode.Sequence -> Icons.Rounded.Repeat
        PlaybackOrderMode.RepeatOne -> Icons.Rounded.RepeatOne
        PlaybackOrderMode.Shuffle -> Icons.Rounded.Shuffle
    }
    val description = when (mode) {
        PlaybackOrderMode.Sequence -> "顺序播放"
        PlaybackOrderMode.RepeatOne -> "单曲循环"
        PlaybackOrderMode.Shuffle -> "随机播放"
    }
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
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

