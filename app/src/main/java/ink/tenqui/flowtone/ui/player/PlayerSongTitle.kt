package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

@Composable
internal fun PlayerSongTitle(
    title: String,
    style: TextStyle,
    color: Color,
    contentAlpha: Float,
    lineBoxWidth: Dp,
    offsetX: Dp,
    lineHorizontalPadding: Dp,
    scale: Float,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(lineBoxWidth)
            .offset(x = offsetX)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
    ) {
        Text(
            text = title,
            style = style,
            color = color.copy(alpha = color.alpha * contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = lineHorizontalPadding)
        )
    }
}
