package ink.tenqui.flowtone.ui.player

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult

@Composable
internal fun CrossfadeArtworkImage(
    imageRequest: ImageRequest?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    waitForImageLoad: Boolean = false
) {
    val context = LocalContext.current
    var previousPainter by remember { mutableStateOf<Painter?>(null) }
    var displayedPainter by remember { mutableStateOf<Painter?>(null) }
    var displayedImageRequest by remember { mutableStateOf<ImageRequest?>(null) }
    val crossfadeProgress = remember { Animatable(1f) }

    LaunchedEffect(imageRequest, waitForImageLoad) {
        if (imageRequest == displayedImageRequest) {
            return@LaunchedEffect
        }

        val nextPainter = imageRequest?.let { request ->
            (context.imageLoader.execute(request) as? SuccessResult)
                ?.image
                ?.asPainter(context)
        }

        previousPainter = displayedPainter
        displayedPainter = nextPainter
        displayedImageRequest = imageRequest

        crossfadeProgress.snapTo(0f)
        crossfadeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            )
        )

        previousPainter = null
    }

    Box(modifier = modifier) {
        previousPainter?.let { oldPainter ->
            Image(
                painter = oldPainter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.alpha = alpha * (1f - crossfadeProgress.value)
                    }
            )
        }

        displayedPainter?.let { painter ->
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.alpha = alpha * crossfadeProgress.value
                    }
            )
        }
    }
}

@Composable
internal fun MorphArtworkLayer(
    imageRequest: ImageRequest?,
    waitForArtworkLoad: Boolean,
    progress: Float,
    scaleProgress: Float,
    currentHeight: Dp,
    viewportHeight: Dp,
    collapsedHeight: Dp,
    playerWidth: Dp,
    expandedArtworkSize: Dp,
    expandedArtworkTop: Dp,
    fullscreenProgress: Float = 0f,
    fullscreenArtworkSize: Dp = expandedArtworkSize,
    fullscreenArtworkCenterY: Dp = expandedArtworkTop + expandedArtworkSize / 2f,
    playbackScale: Float = 1f,
    playbackRotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    val baseArtworkSize = expandedArtworkSize
    val expandedX = (playerWidth - baseArtworkSize) / 2f
    val fullscreenX = (playerWidth - fullscreenArtworkSize) / 2f
    val artworkX = lerpDp(expandedX, fullscreenX, fullscreenProgress)
    val artworkSize = lerpDp(baseArtworkSize, fullscreenArtworkSize, fullscreenProgress)
    val collapsedContainerScale = 2f
    val collapsedAnchorFraction = 0.382f
    val collapsedArtworkCenterY = collapsedHeight * 0.5f
    val collapsedArtworkTop = collapsedArtworkCenterY -
        baseArtworkSize * (0.5f + (collapsedAnchorFraction - 0.5f) * collapsedContainerScale)
    val baseArtworkY = lerpDp(collapsedArtworkTop, expandedArtworkTop, progress)
    val fullscreenArtworkY = fullscreenArtworkCenterY - fullscreenArtworkSize / 2f
    val artworkY = lerpDp(baseArtworkY, fullscreenArtworkY, fullscreenProgress)
    val blurRadius = lerpDp(16.dp, 0.dp, progress)
    val cornerRadius = lerpDp(24.dp, 28.dp, progress)
    val shadowPadding = 32.dp
    val shadowProgress = progress.coerceIn(0f, 1f)
    val containerScale = lerpFloat(collapsedContainerScale, 1f, scaleProgress)
    val collapsedArtworkDimAlpha = lerpFloat(0.38f, 0f, progress)
    val coverShape = RoundedCornerShape(cornerRadius)
    val blurModifier = if (blurRadius > 0.5.dp) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .offset(x = artworkX - shadowPadding, y = artworkY - shadowPadding)
            .wrapContentSize(
                align = Alignment.TopStart,
                unbounded = true
            )
            .width(artworkSize + shadowPadding * 2)
            .height(artworkSize + shadowPadding * 2)
            .graphicsLayer {
                alpha = 1f
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (shadowProgress > 0f) {
                val shadowPaddingPx = shadowPadding.toPx()
                val shadowAlpha = 0.20f * shadowProgress
                val shadowBlur = 22.dp.toPx() * shadowProgress
                val shadowOffsetY = 10.dp.toPx() * shadowProgress
                drawIntoCanvas { canvas ->
                    val paint = NativePaint().apply {
                        isAntiAlias = true
                        color = Color.Black.copy(alpha = shadowAlpha).toArgb()
                        maskFilter = BlurMaskFilter(
                            shadowBlur.coerceAtLeast(0.1f),
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    val rect = RectF(
                        shadowPaddingPx,
                        shadowPaddingPx + shadowOffsetY,
                        shadowPaddingPx + artworkSize.toPx(),
                        shadowPaddingPx + artworkSize.toPx() + shadowOffsetY
                    )
                    canvas.nativeCanvas.drawRoundRect(
                        rect,
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                        paint
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset(x = shadowPadding, y = shadowPadding)
                .width(artworkSize)
                .height(artworkSize)
                .graphicsLayer {
                    shape = coverShape
                    clip = true
                    scaleX = containerScale * playbackScale
                    scaleY = containerScale * playbackScale
                    rotationZ = playbackRotationDegrees
                    transformOrigin = TransformOrigin.Center
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = coverShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(blurModifier),
                contentAlignment = Alignment.Center
            ) {
                CrossfadeArtworkImage(
                    imageRequest = imageRequest,
                    contentDescription = "\u4e13\u8f91\u5c01\u9762",
                    contentScale = ContentScale.Crop,
                    waitForImageLoad = waitForArtworkLoad,
                    modifier = Modifier.matchParentSize()
                )
                if (collapsedArtworkDimAlpha > 0.01f && imageRequest != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = collapsedArtworkDimAlpha))
                    )
                }
                if (imageRequest == null) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpandedArtwork(
    imageRequest: ImageRequest?,
    progress: Float,
    artworkSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val translationY = with(LocalDensity.current) {
        (80.dp * (1f - progress)).toPx()
    }
    val scale = 0.82f + (1f - 0.82f) * progress
    val helperAlpha = 0.9f + 0.1f * progress

    Box(
        modifier = modifier
            .size(artworkSize)
            .graphicsLayer {
                alpha = helperAlpha
                scaleX = scale
                scaleY = scale
                this.translationY = translationY
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "\u4e13\u8f91\u5c01\u9762",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
