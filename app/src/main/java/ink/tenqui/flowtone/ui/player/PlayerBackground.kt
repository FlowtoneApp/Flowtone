package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest

@Composable
internal fun FlowCloudBackground(
    colors: List<Color>,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val cloudColors = if (colors.size >= 3) {
        colors
    } else {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer
        )
    }
    val infiniteTransition = rememberInfiniteTransition(label = "FlowCloudDrift")
    val blob1Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob1Drift"
    )
    val blob2Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_560, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob2Drift"
    )
    val blob3Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob3Drift"
    )
    val blob4Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob4Drift"
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f

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
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    var previousColors by remember { mutableStateOf<List<Color>?>(null) }
    var displayedColors by remember { mutableStateOf(colors) }
    val crossfadeProgress = remember { Animatable(1f) }

    LaunchedEffect(colors) {
        if (colors == displayedColors) {
            return@LaunchedEffect
        }

        previousColors = displayedColors
        displayedColors = colors

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

    Box(modifier = modifier) {
        previousColors?.let { oldColors ->
            FlowCloudBackground(
                colors = oldColors,
                progress = progress,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.alpha = alpha * (1f - crossfadeProgress.value)
                    }
            )
        }

        FlowCloudBackground(
            colors = displayedColors,
            progress = progress,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.alpha = alpha * crossfadeProgress.value
                }
        )
    }
}

@Composable
internal fun BlurredArtworkBackground(
    imageRequest: ImageRequest?,
    alpha: Float,
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
            modifier = Modifier.fillMaxSize()
        )
    }
}

