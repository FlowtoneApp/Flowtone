package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

@Composable
internal fun PlayerMainControls(
    isPlaying: Boolean,
    iconColor: Color,
    screenWidth: Dp,
    previousNextTouchSize: Dp,
    playPauseTouchSize: Dp,
    previousNextIconSize: Dp,
    playPauseIconSize: Dp,
    previousX: Dp,
    playPauseX: Dp,
    nextX: Dp,
    currentTop: Dp,
    fullscreenScale: Float,
    controlsEnabled: Boolean,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            enabled = controlsEnabled,
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
            enabled = controlsEnabled,
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
            enabled = controlsEnabled,
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
