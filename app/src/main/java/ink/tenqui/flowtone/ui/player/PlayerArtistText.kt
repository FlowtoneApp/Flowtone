package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerArtistText(
    artist: String,
    style: TextStyle,
    color: Color,
    contentAlpha: Float,
    lineBoxWidth: Dp,
    offsetX: Dp,
    topPadding: Dp,
    lineHorizontalPadding: Dp,
    minimizedAlpha: Float,
    fullscreenAlpha: Float,
    scale: Float,
    textAlign: TextAlign,
    canClickArtist: Boolean,
    onArtistClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val artistClickShape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .width(lineBoxWidth)
            .offset(x = offsetX)
            .padding(top = topPadding)
            .graphicsLayer {
                alpha = minimizedAlpha * fullscreenAlpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
    ) {
        Text(
            text = artist,
            style = style,
            color = color.copy(alpha = color.alpha * contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canClickArtist) {
                        Modifier
                            .clip(artistClickShape)
                            .clickable {
                                onArtistClick?.invoke(artist)
                            }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = lineHorizontalPadding)
        )
    }
}
