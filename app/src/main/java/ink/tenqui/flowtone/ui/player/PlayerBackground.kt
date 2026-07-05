package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

@Composable
internal fun FlowCloudBackground(
    colors: List<Color>,
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val cloudColors = colors.toOpaqueCloudColors(isDarkTheme)
    val motionSpeed = remember { Animatable(if (isPlaying) 1f else 0f) }
    var motionTimeMs by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(isPlaying) {
        motionSpeed.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(
                durationMillis = FLOW_CLOUD_STOP_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        )
    }
    LaunchedEffect(Unit) {
        var previousFrameNanos = 0L
        while (isActive) {
            if (motionSpeed.value <= 0.0001f) {
                snapshotFlow { motionSpeed.value }.first { it > 0.0001f }
                previousFrameNanos = 0L
            }
            withFrameNanos { frameNanos ->
                if (previousFrameNanos != 0L) {
                    val elapsedMs = (frameNanos - previousFrameNanos) / 1_000_000.0
                    motionTimeMs += elapsedMs * motionSpeed.value * FLOW_CLOUD_SPEED_MULTIPLIER
                }
                previousFrameNanos = frameNanos
            }
        }
    }
    val blob1Drift = reverseDrift(motionTimeMs, 1_200)
    val blob2Drift = reverseDrift(motionTimeMs, 1_560)
    val blob3Drift = reverseDrift(motionTimeMs, 1_800)
    val blob4Drift = reverseDrift(motionTimeMs, 2_300)
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val alphaMultiplier = 1f + progress * 0.08f
        val base = size.maxDimension
        val baseAlpha = 0.14f * alphaMultiplier
        val cloudBlendMode = if (isDarkTheme) {
            BlendMode.Screen
        } else {
            BlendMode.SrcOver
        }

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    cloudColors[0].copy(alpha = baseAlpha),
                    cloudColors[1].copy(alpha = baseAlpha * 0.70f),
                    cloudColors[2].copy(alpha = baseAlpha * 0.86f)
                ),
                start = Offset(size.width * -0.20f, size.height * 0.10f),
                end = Offset(size.width * 1.20f, size.height * 0.95f)
            )
        )

        fun drawCloudBlob(
            color: Color,
            centerX: Float,
            centerY: Float,
            radiusFactor: Float,
            coreAlpha: Float
        ) {
            val center = Offset(size.width * centerX, size.height * centerY)
            val radius = base * radiusFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to color.copy(alpha = coreAlpha * alphaMultiplier),
                        0.22f to color.copy(alpha = coreAlpha * 0.72f * alphaMultiplier),
                        0.48f to color.copy(alpha = coreAlpha * 0.39f * alphaMultiplier),
                        0.72f to color.copy(alpha = coreAlpha * 0.14f * alphaMultiplier),
                        1.00f to Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center,
                blendMode = cloudBlendMode
            )
        }

        drawCloudBlob(
            color = cloudColors[0],
            centerX = -0.30f + blob1Drift * 0.56f,
            centerY = -0.18f + blob1Drift * 0.42f,
            radiusFactor = 1.02f,
            coreAlpha = 0.72f
        )
        drawCloudBlob(
            color = cloudColors[1],
            centerX = 1.30f - blob2Drift * 0.58f,
            centerY = -0.22f + blob2Drift * 0.36f,
            radiusFactor = 0.84f,
            coreAlpha = 0.66f
        )
        drawCloudBlob(
            color = cloudColors[2],
            centerX = 0.40f - blob3Drift * 0.34f,
            centerY = 1.28f - blob3Drift * 0.54f,
            radiusFactor = 1.18f,
            coreAlpha = 0.62f
        )
        drawCloudBlob(
            color = cloudColors[1],
            centerX = 1.12f - blob4Drift * 0.34f,
            centerY = 1.18f - blob4Drift * 0.26f,
            radiusFactor = 0.66f,
            coreAlpha = 0.54f
        )
        drawCloudBlob(
            color = cloudColors[0],
            centerX = 0.16f + blob2Drift * 0.38f,
            centerY = 0.42f + blob3Drift * 0.18f,
            radiusFactor = 0.48f,
            coreAlpha = 0.46f
        )
    }
}

@Composable
internal fun CrossfadeFlowCloudBackground(
    colors: List<Color>,
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val targetColors = colors.toOpaqueCloudColors(isDarkTheme)
    var previousColors by remember { mutableStateOf<List<Color>?>(null) }
    var displayedColors by remember { mutableStateOf(targetColors) }
    val crossfadeProgress = remember { Animatable(1f) }

    LaunchedEffect(targetColors) {
        if (targetColors == displayedColors) {
            return@LaunchedEffect
        }

        previousColors = previousColors?.let { oldColors ->
            blendCloudColors(
                from = oldColors,
                to = displayedColors,
                fraction = crossfadeProgress.value
            )
        } ?: displayedColors
        displayedColors = targetColors

        crossfadeProgress.snapTo(0f)
        crossfadeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 360,
                easing = FastOutSlowInEasing
            )
        )

        previousColors = null
    }

    val blendedColors = previousColors?.let { oldColors ->
        blendCloudColors(
            from = oldColors,
            to = displayedColors,
            fraction = crossfadeProgress.value
        )
    } ?: displayedColors

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
        }
    ) {
        FlowCloudBaseBackground(
            colors = blendedColors,
            modifier = Modifier.matchParentSize()
        )
        FlowCloudBackground(
            colors = blendedColors,
            progress = progress,
            isPlaying = isPlaying,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun FlowCloudBaseBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val cloudColors = colors.toOpaqueCloudColors(isDarkTheme)
    val anchorColor = if (isDarkTheme) {
        Color(0xFF202431)
    } else {
        Color(0xFFDCD8E8)
    }
    val tintFraction = if (isDarkTheme) 0.42f else 0.36f
    val baseColors = cloudColors.take(3).map { color ->
        lerp(anchorColor, color, tintFraction)
    }

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = baseColors,
                start = Offset(size.width * -0.20f, size.height * 0.10f),
                end = Offset(size.width * 1.20f, size.height * 0.95f)
            )
        )
    }
}

private fun reverseDrift(timeMs: Double, oneWayDurationMs: Int): Float {
    val cycleProgress = (timeMs % (oneWayDurationMs * 2.0)) / oneWayDurationMs
    return if (cycleProgress <= 1.0) {
        cycleProgress.toFloat()
    } else {
        (2.0 - cycleProgress).toFloat()
    }
}

private fun blendCloudColors(
    from: List<Color>,
    to: List<Color>,
    fraction: Float
): List<Color> {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val fromFallback = from.lastOrNull() ?: Color(0xFF5D6C8F)
    val toFallback = to.lastOrNull() ?: fromFallback
    val colorCount = maxOf(from.size, to.size, 3)

    return List(colorCount) { index ->
        lerp(
            start = from.getOrElse(index) { fromFallback },
            stop = to.getOrElse(index) { toFallback },
            fraction = safeFraction
        )
    }
}

private fun List<Color>.toOpaqueCloudColors(isDarkTheme: Boolean): List<Color> {
    val fallbackColors = if (isDarkTheme) {
        listOf(
            Color(0xFF5D6C8F),
            Color(0xFF77658E),
            Color(0xFF4E7A73)
        )
    } else {
        listOf(
            Color(0xFF7185B7),
            Color(0xFF9B7EB3),
            Color(0xFF72A79C)
        )
    }
    val sourceColors = ifEmpty { fallbackColors }
    val lastColor = sourceColors.lastOrNull() ?: fallbackColors.last()

    return List(3) { index ->
        sourceColors.getOrElse(index) { lastColor }.copy(alpha = 1f)
    }
}

private const val FLOW_CLOUD_STOP_DURATION_MS = 2_000
private const val FLOW_CLOUD_SPEED_MULTIPLIER = 0.5

@Composable
internal fun BlurredArtworkBackground(
    imageRequest: ImageRequest?,
    alpha: Float,
    waitForArtworkLoad: Boolean,
    modifier: Modifier = Modifier
) {
    if (alpha <= 0.01f) {
        return
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = 1.22f
                scaleY = 1.22f
            }
            .blur(100.dp)
    ) {
        CrossfadeArtworkImage(
            imageRequest = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            waitForImageLoad = waitForArtworkLoad,
            modifier = Modifier.fillMaxSize()
        )
    }
}

