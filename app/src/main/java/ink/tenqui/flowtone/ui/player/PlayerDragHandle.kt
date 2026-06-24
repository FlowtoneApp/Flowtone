package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerDragHandle(
    animationProgress: Float,
    hasCurrentSong: Boolean,
    expanded: Boolean,
    interactionSource: MutableInteractionSource,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(
                enabled = hasCurrentSong && !expanded,
                interactionSource = interactionSource,
                indication = null
            ) {
                onExpandedChange(true)
            }
    ) {
        val handleAlpha = lerpFloat(1f, 0.65f, animationProgress)
        val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val handleShape = RoundedCornerShape(percent = 50)
        val handleBaseColor = if (isLightTheme) {
            Color.Black.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.26f)
        }
        val handleBlurColor = if (isLightTheme) {
            Color.Black.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.20f)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .offset(y = 0.dp)
                .width(72.dp)
                .height(6.dp)
                .graphicsLayer {
                    alpha = handleAlpha
                }
                .clip(handleShape)
                .background(handleBaseColor, handleShape)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = handleBlurColor,
                        shape = handleShape
                    )
                    .blur(8.dp)
            )
        }
    }
}
