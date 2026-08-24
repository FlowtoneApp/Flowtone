package ink.tenqui.flowtone.ui.library

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

private val AlbumArtworkCoverSize = 128.dp
private val AlbumVinylDiscSize = 112.dp
private val AlbumVinylExposedWidth = 28.dp
private const val VinylAccelerationMillis = 1_800f
private const val VinylDecelerationMillis = 3_200f
private val VinylGrooveScales = listOf(0.22f, 0.34f, 0.46f, 0.58f, 0.70f, 0.82f, 0.92f)

private data class VinylMeteorArc(
    val radiusScale: Float,
    val startAngle: Float,
    val sweepAngle: Float,
    val tailAlpha: Float,
    val strokeWidthDp: Float,
    val degreesPerSecond: Float
)

private val VinylMeteorArcPattern = listOf(
    VinylMeteorArc(0.92f, 128f, 48f, 0.25f, 1.1f, -11.6f),
    VinylMeteorArc(0.70f, 191f, 24f, 0.18f, 0.9f, -14.0f),
    VinylMeteorArc(0.46f, 143f, 30f, 0.30f, 1.25f, -9.7f),
    VinylMeteorArc(0.22f, 169f, 19f, 0.21f, 1.0f, -12.7f)
)

private val VinylMeteorArcs = VinylMeteorArcPattern.flatMap { arc ->
    listOf(0f, 59f, 119f, 179f, 239f, 299f).map { followerOffset ->
        arc.copy(startAngle = arc.startAngle + followerOffset)
    }
}

@Composable
internal fun AlbumArtwork(
    artworkUri: Uri?,
    vinylMotionActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(AlbumArtworkCoverSize + AlbumVinylExposedWidth)
            .height(AlbumArtworkCoverSize)
    ) {
        VinylDisc(
            motionActive = vinylMotionActive,
            modifier = Modifier
                .size(AlbumVinylDiscSize)
                .align(Alignment.CenterStart)
        )
        FlowtoneArtwork(
            artworkUri = artworkUri,
            modifier = Modifier
                .size(AlbumArtworkCoverSize)
                .align(Alignment.CenterEnd)
        )
    }
}

@Composable
internal fun VinylDisc(
    motionActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var travelSeconds by remember { mutableFloatStateOf(0f) }
    var velocityScale by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(motionActive) {
        if (!motionActive && velocityScale <= 0f) return@LaunchedEffect

        val initialVelocityScale = velocityScale
        val targetVelocityScale = if (motionActive) 1f else 0f
        val velocityTransitionMillis = if (motionActive) {
            VinylAccelerationMillis
        } else {
            VinylDecelerationMillis
        }
        var transitionElapsedMillis = 0f
        var previousFrameNanos = withFrameNanos { frameNanos -> frameNanos }

        while (isActive && (motionActive || velocityScale > 0f)) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                .coerceIn(0f, 0.05f)
            previousFrameNanos = frameNanos
            transitionElapsedMillis += deltaSeconds * 1_000f

            val fraction = (transitionElapsedMillis / velocityTransitionMillis)
                .coerceIn(0f, 1f)
            val easedFraction = FlowtoneMotion.Easing.transform(fraction)
            velocityScale = initialVelocityScale +
                (targetVelocityScale - initialVelocityScale) * easedFraction
            travelSeconds += deltaSeconds * velocityScale

            if (!motionActive && fraction >= 1f) {
                velocityScale = 0f
            }
        }
    }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF24242A),
                    Color(0xFF15151A),
                    Color(0xFF0D0D11)
                ),
                center = center,
                radius = diameter / 2f
            ),
            radius = diameter / 2f,
            center = center
        )

        VinylGrooveScales.forEach { scale ->
            drawCircle(
                color = Color.White.copy(alpha = 0.045f),
                radius = diameter * scale / 2f,
                center = center,
                style = Stroke(width = 0.75.dp.toPx())
            )
        }

        VinylMeteorArcs.forEach { arc ->
            val radius = diameter * arc.radiusScale / 2f
            val animatedStartAngle = arc.startAngle + travelSeconds * arc.degreesPerSecond
            val tail = vinylArcPoint(center, radius, animatedStartAngle)
            val head = vinylArcPoint(center, radius, animatedStartAngle + arc.sweepAngle)
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = arc.tailAlpha),
                        Color.White.copy(alpha = arc.tailAlpha * 0.42f),
                        Color.White.copy(alpha = 0f)
                    ),
                    start = tail,
                    end = head
                ),
                startAngle = animatedStartAngle,
                sweepAngle = arc.sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(
                    width = arc.strokeWidthDp.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        drawCircle(
            color = Color(0xFF34343A),
            radius = diameter * 0.095f,
            center = center
        )
        drawCircle(
            color = Color(0xFF111116),
            radius = 1.6.dp.toPx(),
            center = center
        )
    }
}

private fun vinylArcPoint(center: Offset, radius: Float, angleDegrees: Float): Offset {
    val angleRadians = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        x = center.x + radius * cos(angleRadians).toFloat(),
        y = center.y + radius * sin(angleRadians).toFloat()
    )
}
