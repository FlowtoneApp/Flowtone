package ink.tenqui.flowtone.ui.player

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

@Composable
internal fun PlayerProgressBarTrack(
    visibleProgress: Float,
    trackHeight: Dp,
    trackColor: Color,
    progressColor: Color,
    enterProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val trackHeightPx = trackHeight.toPx()
        val trackTop = centerY - trackHeightPx / 2f
        val trackLeft = 0f
        val trackWidth = size.width
        val cornerRadius = trackHeightPx / 2f
        val progressWidth = trackWidth * visibleProgress.coerceIn(0f, 1f)
        val shadowOffsetY = PlaybackProgressShadowOffsetY.toPx()
        val shadowBlurRadius = PlaybackProgressShadowBlurRadius.toPx()
        val shadowAlpha = enterProgress * PlaybackProgressShadowAlphaMultiplier
        drawIntoCanvas { canvas ->
            val paint = NativePaint().apply {
                isAntiAlias = true
                color = Color.Black.copy(alpha = shadowAlpha).toArgb()
                maskFilter = BlurMaskFilter(
                    shadowBlurRadius,
                    BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.nativeCanvas.drawRoundRect(
                RectF(
                    trackLeft,
                    trackTop + shadowOffsetY,
                    trackLeft + trackWidth,
                    trackTop + trackHeightPx + shadowOffsetY
                ),
                cornerRadius,
                cornerRadius,
                paint
            )
        }
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackLeft, trackTop),
            size = Size(trackWidth, trackHeightPx),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
        if (visibleProgress > 0f) {
            val trackPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = trackLeft,
                        top = trackTop,
                        right = trackLeft + trackWidth,
                        bottom = trackTop + trackHeightPx,
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                )
            }
            clipPath(trackPath) {
                drawRect(
                    color = progressColor,
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(progressWidth, trackHeightPx)
                )
            }
        }
    }
}
