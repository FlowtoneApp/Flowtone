package ink.tenqui.flowtone.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MINI_PLAYER_ANIMATION_DURATION_MS = 400
private val MiniPlayerEasing = CubicBezierEasing(0.20f, 0.00f, 0.08f, 1.00f)
private val SoftElementEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
private val HeavyElementEasing = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)
private val MiniPlayerMotionEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
private val TrackSwitchProgressEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong = playbackState.currentSong
    val hasCurrentSong = currentSong != null
    val title = currentSong?.title.orEmpty()
    val artist = currentSong?.artist.orEmpty()
    val artworkUri = currentSong?.artworkUri
    val durationMs = when {
        playbackState.durationMs > 0L -> playbackState.durationMs
        currentSong?.durationMs != null && currentSong.durationMs > 0L -> currentSong.durationMs
        else -> 0L
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val collapsedHeight = 92.dp
    val dragHotZoneHeight = 40.dp
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    val targetExpandedHeight = configuration.screenHeightDp.dp * 0.618f
    val widthBasedArtworkSize = if (configuration.screenWidthDp.dp * 0.76f < 340.dp) {
        configuration.screenWidthDp.dp * 0.76f
    } else {
        340.dp
    }
    val expandedHeight = if (targetExpandedHeight > collapsedHeight) {
        targetExpandedHeight
    } else {
        collapsedHeight
    }
    val heightLimitedArtworkSize = expandedHeight * 0.52f
    val expandedArtworkSize = if (widthBasedArtworkSize < heightLimitedArtworkSize) {
        widthBasedArtworkSize
    } else {
        heightLimitedArtworkSize
    }
    val expandedArtworkTop = 24.dp
    val expandedMetadataTop = expandedArtworkTop + expandedArtworkSize + 14.dp
    val expandedProgressTop = expandedMetadataTop + 76.dp
    val expandedControlsTop = expandedProgressTop + 58.dp
    val animationProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "MiniPlayerProgress"
    )
    val currentHeight = collapsedHeight + (expandedHeight - collapsedHeight) * animationProgress
    val visibleProgress by animateFloatAsState(
        targetValue = if (hasCurrentSong) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerVisibleProgress"
    )
    val hiddenOffsetDp = currentHeight + dragHotZoneHeight + 32.dp

    val slideInEasing = CubicBezierEasing(0.05f, 0.85f, 0.18f, 1.0f)

    val miniPlayerSlideOffsetY by animateDpAsState(
        targetValue = if (hasCurrentSong) 0.dp else hiddenOffsetDp,
        animationSpec = tween(
            durationMillis = 360,
            easing = if (hasCurrentSong) {
                slideInEasing
            } else {
                FastOutSlowInEasing
            }
        ),
        label = "MiniPlayerSlideOffsetY"
    )
    val hasArtworkBackground = artworkUri != null
    val backgroundImageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(256, 256)
                .crossfade(false)
                .build()
        }
    }
    val coverImageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(768, 768)
                .crossfade(false)
                .build()
        }
    }
    val paletteImageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(128, 128)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
    }
    LaunchedEffect(coverImageRequest) {
        coverImageRequest?.let { request ->
            context.imageLoader.enqueue(request)
        }
    }
    val fallbackCloudColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    var extractedCloudColors by remember {
        mutableStateOf<List<Color>?>(null)
    }
    LaunchedEffect(paletteImageRequest) {
        extractedCloudColors = paletteImageRequest?.let { request ->
            runCatching {
                withContext(Dispatchers.Default) {
                    val result = context.imageLoader.execute(request)
                    val bitmap = (result as? SuccessResult)?.image?.toBitmap(128, 128)
                    bitmap?.let { sourceBitmap ->
                        val palette = Palette.from(sourceBitmap).generate()
                        listOf(
                            Color(
                                palette.vibrantSwatch?.rgb
                                    ?: palette.dominantSwatch?.rgb
                                    ?: palette.getDominantColor(android.graphics.Color.DKGRAY)
                            ),
                            Color(
                                palette.mutedSwatch?.rgb
                                    ?: palette.darkVibrantSwatch?.rgb
                                    ?: palette.getMutedColor(android.graphics.Color.GRAY)
                            ),
                            Color(
                                palette.lightVibrantSwatch?.rgb
                                    ?: palette.lightMutedSwatch?.rgb
                                    ?: palette.getLightMutedColor(android.graphics.Color.LTGRAY)
                            )
                        )
                    }
                }
            }.getOrNull()
        }
    }
    val cloudColors = extractedCloudColors ?: fallbackCloudColors
    val cloudBrightenFactor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        1.20f
    } else {
        1.32f
    }
    val cloudBrightenOffset = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        0.06f
    } else {
        0.10f
    }
    val brightCloudColors = cloudColors.map { color ->
        Color(
            red = (color.red * cloudBrightenFactor + cloudBrightenOffset).coerceIn(0f, 1f),
            green = (color.green * cloudBrightenFactor + cloudBrightenOffset).coerceIn(0f, 1f),
            blue = (color.blue * cloudBrightenFactor + cloudBrightenOffset).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val titleColor = if (hasArtworkBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val artistColor = if (hasArtworkBackground) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val controlIconColor = if (hasArtworkBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progressTrackColor = if (hasArtworkBackground) {
        Color(0xFF9E9E9E)
    } else {
        Color(0xFF8A8A8A)
    }
    val progressColor = if (hasArtworkBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }
    var isProgressScrubbing by remember { mutableStateOf(false) }
    var lockedIsPlayingDuringScrub by remember { mutableStateOf(playbackState.isPlaying) }
    var keepPlayPauseVisualLockedAfterSeek by remember { mutableStateOf(false) }
    val currentSongKey = currentSong?.id?.toString()
    var likedSongKeys by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val isCurrentSongLiked = currentSongKey != null && likedSongKeys.contains(currentSongKey)
    LaunchedEffect(currentSong?.id) {
        isProgressScrubbing = false
        keepPlayPauseVisualLockedAfterSeek = false
    }
    LaunchedEffect(isProgressScrubbing, keepPlayPauseVisualLockedAfterSeek) {
        if (keepPlayPauseVisualLockedAfterSeek && !isProgressScrubbing) {
            delay(500)
            keepPlayPauseVisualLockedAfterSeek = false
        }
    }
    val visualIsPlaying = if (isProgressScrubbing || keepPlayPauseVisualLockedAfterSeek) {
        lockedIsPlayingDuringScrub
    } else {
        playbackState.isPlaying
    }
    var accumulatedDragY by remember { mutableStateOf(0f) }
    val gestureModifier = Modifier.pointerInput(hasCurrentSong, expanded, swipeThresholdPx) {
        detectVerticalDragGestures(
            onDragStart = {
                accumulatedDragY = 0f
            },
            onVerticalDrag = { _, dragAmount ->
                if (hasCurrentSong) {
                    accumulatedDragY += dragAmount
                }
            },
            onDragEnd = {
                if (!hasCurrentSong) {
                    return@detectVerticalDragGestures
                }
                if (!expanded && accumulatedDragY <= -swipeThresholdPx) {
                    onExpandedChange(true)
                } else if (expanded && accumulatedDragY >= swipeThresholdPx) {
                    onExpandedChange(false)
                }
            },
            onDragCancel = {
                accumulatedDragY = 0f
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight + dragHotZoneHeight)
            .graphicsLayer {
                translationY = miniPlayerSlideOffsetY.toPx()
                alpha = visibleProgress
            }
            .then(gestureModifier)
    ) {
        val playerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        val playerShadowElevation = lerpDp(0.dp, 18.dp, animationProgress)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dragHotZoneHeight)
                .align(Alignment.TopCenter)
                .clickable(
                    enabled = hasCurrentSong && !expanded,
                    interactionSource = noRippleInteractionSource,
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
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = dragHotZoneHeight)
                .fillMaxWidth()
                .height(currentHeight)
                .shadow(
                    elevation = playerShadowElevation,
                    shape = playerShape,
                    clip = false
                )
                .clickable(
                    enabled = hasCurrentSong && !expanded,
                    interactionSource = noRippleInteractionSource,
                    indication = null
                ) {
                    onExpandedChange(true)
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(currentHeight)
                    .clip(playerShape)
                    .then(
                        if (hasArtworkBackground) {
                            Modifier
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                BlurredArtworkBackground(
                    imageRequest = backgroundImageRequest,
                    alpha = lerpFloat(0.78f, 0f, animationProgress),
                    modifier = Modifier.matchParentSize()
                )
                CrossfadeFlowCloudBackground(
                    colors = brightCloudColors,
                    progress = animationProgress,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, animationProgress)))
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.BottomCenter
            ) {
                val playerWidth = maxWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentHeight)
                        .align(Alignment.TopCenter)
                ) {
                    MorphArtworkLayer(
                        imageRequest = coverImageRequest,
                        progress = animationProgress,
                        playerWidth = playerWidth,
                        collapsedHeight = collapsedHeight,
                        expandedArtworkSize = expandedArtworkSize,
                        expandedArtworkTop = expandedArtworkTop,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    SharedSongInfo(
                        title = title,
                        artist = artist,
                        progress = animationProgress,
                        titleColor = titleColor,
                        artistColor = artistColor,
                        playerWidth = playerWidth,
                        collapsedHeight = collapsedHeight,
                        expandedTop = expandedMetadataTop,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    ExpandedOnlyContent(
                        progress = animationProgress,
                        positionMs = playbackState.positionMs,
                        durationMs = durationMs,
                        isPlaying = playbackState.isPlaying,
                        currentSongKey = currentSong?.id,
                        hasCurrentSong = hasCurrentSong,
                        progressTrackColor = progressTrackColor,
                        progressColor = progressColor,
                        onSeekTo = onSeekTo,
                        onTapSeekVisualLock = {
                            lockedIsPlayingDuringScrub = visualIsPlaying
                            keepPlayPauseVisualLockedAfterSeek = true
                        },
                        onScrubbingChange = { scrubbing ->
                            if (scrubbing) {
                                lockedIsPlayingDuringScrub = playbackState.isPlaying
                                keepPlayPauseVisualLockedAfterSeek = true
                            }
                            isProgressScrubbing = scrubbing
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = expandedProgressTop)
                    )
                    SharedPlaybackControls(
                        progress = animationProgress,
                        isPlaying = visualIsPlaying,
                        iconColor = controlIconColor,
                        screenWidth = playerWidth,
                        collapsedHeight = collapsedHeight,
                        expandedTop = expandedControlsTop,
                        onPlayPrevious = {
                            if (hasCurrentSong) {
                                onPlayPrevious()
                            }
                        },
                        onTogglePlayPause = {
                            if (hasCurrentSong) {
                                isProgressScrubbing = false
                                keepPlayPauseVisualLockedAfterSeek = false
                                onTogglePlayPause()
                            }
                        },
                        onPlayNext = {
                            if (hasCurrentSong) {
                                onPlayNext()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    val sideButtonProgress = easeMiniPlayerMotion(
                        delayedProgress(animationProgress, 0.28f, 0.92f)
                    )
                    FavoriteButton(
                        liked = isCurrentSongLiked,
                        enabled = hasCurrentSong && sideButtonProgress > 0.55f,
                        onClick = {
                            currentSongKey?.let { key ->
                                likedSongKeys = if (likedSongKeys.contains(key)) {
                                    likedSongKeys - key
                                } else {
                                    likedSongKeys + key
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                val buttonSize = 48.dp
                                val progressWidth = playerWidth * 0.76f
                                val progressLeft = (playerWidth - progressWidth) / 2f
                                val targetX = progressLeft
                                val hiddenX = -buttonSize - 32.dp
                                val buttonX = lerpDp(hiddenX, targetX, sideButtonProgress)
                                val buttonY = expandedControlsTop + 16.dp
                                translationX = buttonX.toPx()
                                translationY = buttonY.toPx()
                                alpha = sideButtonProgress
                            }
                            .size(48.dp)
                    )
                    PlaybackOrderButton(
                        mode = playbackState.playbackOrderMode,
                        iconColor = controlIconColor,
                        enabled = hasCurrentSong && sideButtonProgress > 0.55f,
                        onClick = {
                            onTogglePlaybackOrderMode()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                val buttonSize = 48.dp
                                val progressWidth = playerWidth * 0.76f
                                val progressLeft = (playerWidth - progressWidth) / 2f
                                val targetX = progressLeft + progressWidth - buttonSize
                                val hiddenX = playerWidth + 32.dp
                                val buttonX = lerpDp(hiddenX, targetX, sideButtonProgress)
                                val buttonY = expandedControlsTop + 16.dp
                                translationX = buttonX.toPx()
                                translationY = buttonY.toPx()
                                alpha = sideButtonProgress
                            }
                            .size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowCloudBackground(
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
            animation = tween(durationMillis = 4_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob1Drift"
    )
    val blob2Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob2Drift"
    )
    val blob3Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob3Drift"
    )
    val blob4Drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlowCloudBlob4Drift"
    )

    Canvas(modifier = modifier) {
        val alphaMultiplier = 1f + progress * 0.08f
        val minSide = size.minDimension

        val blob1Radius = minSide * 0.62f
        val blob2Radius = minSide * 0.54f
        val blob3Radius = minSide * 0.48f
        val blob4Radius = minSide * 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to cloudColors[0].copy(alpha = 0.82f * alphaMultiplier),
                    0.58f to cloudColors[0].copy(alpha = 0.30f * alphaMultiplier),
                    1f to Color.Transparent
                ),
                center = Offset(
                    x = size.width * (-0.28f + blob1Drift * 0.50f),
                    y = size.height * (-0.06f + blob1Drift * 0.36f)
                ),
                radius = blob1Radius
            ),
            radius = blob1Radius,
            center = Offset(
                x = size.width * (-0.28f + blob1Drift * 0.50f),
                y = size.height * (-0.06f + blob1Drift * 0.36f)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to cloudColors[1].copy(alpha = 0.68f * alphaMultiplier),
                    0.6f to cloudColors[1].copy(alpha = 0.25f * alphaMultiplier),
                    1f to Color.Transparent
                ),
                center = Offset(
                    x = size.width * (1.24f - blob2Drift * 0.46f),
                    y = size.height * (-0.02f + blob2Drift * 0.28f)
                ),
                radius = blob2Radius
            ),
            radius = blob2Radius,
            center = Offset(
                x = size.width * (1.24f - blob2Drift * 0.46f),
                y = size.height * (-0.02f + blob2Drift * 0.28f)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to cloudColors[2].copy(alpha = 0.58f * alphaMultiplier),
                    0.62f to cloudColors[2].copy(alpha = 0.22f * alphaMultiplier),
                    1f to Color.Transparent
                ),
                center = Offset(
                    x = size.width * (0.46f - blob3Drift * 0.38f),
                    y = size.height * (1.20f - blob3Drift * 0.40f)
                ),
                radius = blob3Radius
            ),
            radius = blob3Radius,
            center = Offset(
                x = size.width * (0.46f - blob3Drift * 0.38f),
                y = size.height * (1.20f - blob3Drift * 0.40f)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to cloudColors[1].copy(alpha = 0.50f * alphaMultiplier),
                    0.64f to cloudColors[1].copy(alpha = 0.18f * alphaMultiplier),
                    1f to Color.Transparent
                ),
                center = Offset(
                    x = size.width * (1.08f - blob4Drift * 0.28f),
                    y = size.height * (1.08f - blob4Drift * 0.18f)
                ),
                radius = blob4Radius
            ),
            radius = blob4Radius,
            center = Offset(
                x = size.width * (1.08f - blob4Drift * 0.28f),
                y = size.height * (1.08f - blob4Drift * 0.18f)
            )
        )
    }
}

@Composable
private fun CrossfadeFlowCloudBackground(
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
private fun BlurredArtworkBackground(
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

@Composable
private fun CrossfadeArtworkImage(
    imageRequest: ImageRequest?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    var previousImageRequest by remember { mutableStateOf<ImageRequest?>(null) }
    var displayedImageRequest by remember { mutableStateOf(imageRequest) }
    val crossfadeProgress = remember { Animatable(1f) }

    LaunchedEffect(imageRequest) {
        if (imageRequest == displayedImageRequest) {
            return@LaunchedEffect
        }

        previousImageRequest = displayedImageRequest
        displayedImageRequest = imageRequest

        crossfadeProgress.snapTo(0f)
        crossfadeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            )
        )

        previousImageRequest = null
    }

    Box(modifier = modifier) {
        previousImageRequest?.let { oldRequest ->
            AsyncImage(
                model = oldRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.alpha = alpha * (1f - crossfadeProgress.value)
                    }
            )
        }

        displayedImageRequest?.let { request ->
            AsyncImage(
                model = request,
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
private fun MorphArtworkLayer(
    imageRequest: ImageRequest?,
    progress: Float,
    playerWidth: Dp,
    collapsedHeight: Dp,
    expandedArtworkSize: Dp,
    expandedArtworkTop: Dp,
    modifier: Modifier = Modifier
) {
    val collapsedX = 0.dp
    val collapsedY = 0.dp
    val collapsedWidth = playerWidth
    val expandedX = (playerWidth - expandedArtworkSize) / 2f
    val expandedY = expandedArtworkTop
    val artworkX = lerpDp(collapsedX, expandedX, progress)
    val artworkY = lerpDp(collapsedY, expandedY, progress)
    val artworkWidth = lerpDp(collapsedWidth, expandedArtworkSize, progress)
    val artworkHeight = lerpDp(collapsedHeight, expandedArtworkSize, progress)
    val blurRadius = lerpDp(16.dp, 0.dp, progress)
    val cornerRadius = lerpDp(24.dp, 28.dp, progress)
    val shadowPadding = 32.dp
    val shadowProgress = progress.coerceIn(0f, 1f)
    val imageScale = lerpFloat(1.22f, 1f, progress)
    val coverShape = RoundedCornerShape(cornerRadius)
    val blurModifier = if (blurRadius > 0.5.dp) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .offset(x = artworkX - shadowPadding, y = artworkY - shadowPadding)
            .width(artworkWidth + shadowPadding * 2)
            .height(artworkHeight + shadowPadding * 2)
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
                        shadowPaddingPx + artworkWidth.toPx(),
                        shadowPaddingPx + artworkHeight.toPx() + shadowOffsetY
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
                .width(artworkWidth)
                .height(artworkHeight)
                .graphicsLayer {
                    shape = coverShape
                    clip = true
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = coverShape
                ),
            contentAlignment = Alignment.Center
        ) {
            CrossfadeArtworkImage(
                imageRequest = imageRequest,
                contentDescription = "\u4e13\u8f91\u5c01\u9762",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = imageScale
                        scaleY = imageScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .then(blurModifier)
            )
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

@Composable
private fun ExpandedOnlyContent(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    currentSongKey: Long?,
    hasCurrentSong: Boolean,
    progressTrackColor: Color,
    progressColor: Color,
    onSeekTo: (Long) -> Unit,
    onTapSeekVisualLock: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressEnterProgress = ((progress - 0.08f) / 0.92f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlaybackProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            currentSongKey = currentSongKey,
            enabled = hasCurrentSong && durationMs > 0L,
            trackColor = progressTrackColor,
            progressColor = progressColor,
            onSeekTo = onSeekTo,
            onTapSeekVisualLock = onTapSeekVisualLock,
            onScrubbingChange = onScrubbingChange,
            enterProgress = progressEnterProgress,
            modifier = Modifier
                .fillMaxWidth(0.76f)
        )
    }
}

@Composable
private fun SharedSongInfo(
    title: String,
    artist: String,
    progress: Float,
    titleColor: Color,
    artistColor: Color,
    playerWidth: Dp,
    collapsedHeight: Dp,
    expandedTop: Dp,
    modifier: Modifier = Modifier
) {
    val metadataGroupHeight = 60.dp
    val collapsedCenterY = collapsedHeight / 2f
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleMedium
    val artistStyle = MaterialTheme.typography.bodyMedium
    val collapsedViewportX = 30.dp
    val collapsedControlsReservedWidth = 48.dp * 3f + 8.dp * 2f + 30.dp
    val collapsedViewportWidth = playerWidth - collapsedViewportX - collapsedControlsReservedWidth
    val collapsedViewportY = collapsedCenterY - metadataGroupHeight / 2f
    val expandedViewportWidth = playerWidth * 0.82f
    val expandedViewportX = (playerWidth - expandedViewportWidth) / 2f
    val expandedViewportY = expandedTop
    val viewportX = lerpDp(collapsedViewportX, expandedViewportX, progress)
    val viewportY = lerpDp(collapsedViewportY, expandedViewportY, progress)
    val viewportWidth = lerpDp(collapsedViewportWidth, expandedViewportWidth, progress)
    val titleWidth = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(title),
            style = titleStyle,
            maxLines = 1
        ).size.width.toDp()
    }
    val artistWidth = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(artist),
            style = artistStyle,
            maxLines = 1
        ).size.width.toDp()
    }
    val rawTitleLineWidth = titleWidth + 12.dp
    val titleLineWidth = when {
        rawTitleLineWidth < 48.dp -> 48.dp
        rawTitleLineWidth > expandedViewportWidth -> expandedViewportWidth
        else -> rawTitleLineWidth
    }
    val rawArtistLineWidth = artistWidth + 12.dp
    val artistLineWidth = when {
        rawArtistLineWidth < 48.dp -> 48.dp
        rawArtistLineWidth > expandedViewportWidth -> expandedViewportWidth
        else -> rawArtistLineWidth
    }
    val titleVisualWidth = when {
        titleWidth < 48.dp -> 48.dp
        titleWidth > expandedViewportWidth -> expandedViewportWidth
        else -> titleWidth
    }
    val artistVisualWidth = when {
        artistWidth < 48.dp -> 48.dp
        artistWidth > expandedViewportWidth -> expandedViewportWidth
        else -> artistWidth
    }
    val collapsedTitleX = 0.dp
    val centeredTitleX = (viewportWidth - titleVisualWidth) / 2f
    val expandedTitleX = if (centeredTitleX < 0.dp) {
        0.dp
    } else {
        centeredTitleX
    }
    val collapsedArtistX = 0.dp
    val centeredArtistX = (viewportWidth - artistVisualWidth) / 2f
    val expandedArtistX = if (centeredArtistX < 0.dp) {
        0.dp
    } else {
        centeredArtistX
    }
    val titleX = lerpDp(collapsedTitleX, expandedTitleX, progress)
    val artistX = lerpDp(collapsedArtistX, expandedArtistX, progress)

    Box(
        modifier = modifier
            .width(viewportWidth)
            .height(metadataGroupHeight)
            .graphicsLayer {
                translationX = viewportX.toPx()
                translationY = viewportY.toPx()
            }
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .width(viewportWidth)
                .height(metadataGroupHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .width(titleLineWidth)
                    .offset(x = titleX)
            )
            Text(
                text = artist,
                style = artistStyle,
                color = artistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .width(artistLineWidth)
                    .offset(x = artistX)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SharedPlaybackControls(
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
private fun PlaybackProgressBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    currentSongKey: Long?,
    enabled: Boolean,
    trackColor: Color,
    progressColor: Color,
    onSeekTo: (Long) -> Unit,
    onTapSeekVisualLock: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    enterProgress: Float,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var smoothPositionMs by remember { mutableStateOf(positionMs) }
    var anchorPositionMs by remember { mutableStateOf(positionMs) }
    var anchorFrameTimeNanos by remember { mutableStateOf(0L) }
    var isTapSeeking by remember { mutableStateOf(false) }
    val tapSeekProgress = remember { Animatable(0f) }
    val tapSeekScope = rememberCoroutineScope()
    var tapSeekJob by remember { mutableStateOf<Job?>(null) }
    val trackSwitchProgress = remember { Animatable(1f) }
    var lastSongKey by remember { mutableStateOf(currentSongKey) }
    var lastRenderedProgress by remember { mutableStateOf(0f) }
    var trackSwitchStartProgress by remember { mutableStateOf(0f) }
    var isTrackSwitchProgressAnimating by remember { mutableStateOf(false) }
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 16.dp else 8.dp,
        animationSpec = tween(
            durationMillis = 160,
            easing = FastOutSlowInEasing
        ),
        label = "ProgressTrackHeight"
    )
    LaunchedEffect(positionMs, durationMs, currentSongKey) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration)
        if (!isPlaying || kotlin.math.abs(smoothPositionMs - safePosition) > 800L) {
            smoothPositionMs = safePosition
        }
        anchorPositionMs = smoothPositionMs.coerceIn(0L, safeDuration)
        anchorFrameTimeNanos = 0L
        if (durationMs <= 0L) {
            scrubProgress = 0f
            isScrubbing = false
        }
    }

    LaunchedEffect(isPlaying, durationMs, currentSongKey, positionMs) {
        if (!isPlaying || durationMs <= 0L) {
            val safeDuration = durationMs.coerceAtLeast(0L)
            smoothPositionMs = positionMs.coerceIn(0L, safeDuration)
            anchorPositionMs = smoothPositionMs
            anchorFrameTimeNanos = 0L
            return@LaunchedEffect
        }

        anchorPositionMs = smoothPositionMs.coerceIn(0L, durationMs)
        anchorFrameTimeNanos = 0L

        while (isActive && isPlaying && durationMs > 0L) {
            withFrameNanos { frameTime ->
                if (anchorFrameTimeNanos == 0L) {
                    anchorFrameTimeNanos = frameTime
                }

                val elapsedMs = (frameTime - anchorFrameTimeNanos) / 1_000_000L
                smoothPositionMs = (anchorPositionMs + elapsedMs).coerceIn(0L, durationMs)
            }
        }
    }

    LaunchedEffect(currentSongKey) {
        if (currentSongKey != lastSongKey) {
            trackSwitchStartProgress = lastRenderedProgress.coerceIn(0f, 1f)
            isTrackSwitchProgressAnimating = true
            lastSongKey = currentSongKey
            trackSwitchProgress.snapTo(0f)
            trackSwitchProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = LinearEasing
                )
            )
            isTrackSwitchProgressAnimating = false
        }
        tapSeekJob?.cancel()
        isScrubbing = false
        scrubProgress = 0f
        isTapSeeking = false
    }

    val smoothPlaybackProgress = if (durationMs > 0L) {
        smoothPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    val trackSwitchVisualProgress = if (isTrackSwitchProgressAnimating) {
        val eased = TrackSwitchProgressEasing.transform(trackSwitchProgress.value.coerceIn(0f, 1f))
        lerpFloat(trackSwitchStartProgress, smoothPlaybackProgress, eased)
    } else {
        smoothPlaybackProgress
    }.coerceIn(0f, 1f)
    val visibleProgress = when {
        isScrubbing -> scrubProgress
        isTapSeeking -> tapSeekProgress.value
        else -> trackSwitchVisualProgress
    }.coerceIn(0f, 1f)
    val currentVisibleProgress by rememberUpdatedState(visibleProgress)
    SideEffect {
        if (!isScrubbing && !isTapSeeking) {
            lastRenderedProgress = visibleProgress.coerceIn(0f, 1f)
        }
    }
    val displayTimePositionMs = if (durationMs > 0L) {
        (durationMs * visibleProgress).toLong()
    } else {
        0L
    }.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val activeProgressColor = progressColor

    fun updateScrubProgress(x: Float) {
        val width = containerSize.width.toFloat().coerceAtLeast(1f)
        scrubProgress = (x / width).coerceIn(0f, 1f)
    }

    fun progressFromX(x: Float): Float {
        val width = containerSize.width.toFloat().coerceAtLeast(1f)
        return (x / width).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val progressBarTranslationY = with(density) {
        (300.dp * (1f - enterProgress)).toPx()
    }
    val progressBarScale = lerpFloat(2.6f, 1f, enterProgress)
    val progressBarAlpha = lerpFloat(0.18f, 1f, enterProgress)

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progressBarAlpha
                    translationY = progressBarTranslationY
                    scaleX = progressBarScale
                    scaleY = progressBarScale
                    transformOrigin = TransformOrigin.Center
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val centerY = size.height / 2f
                val trackHeight = animatedTrackHeight.toPx()
                val trackTop = centerY - trackHeight / 2f
                val trackLeft = 0f
                val trackWidth = size.width
                val cornerRadius = trackHeight / 2f
                val progressWidth = trackWidth * visibleProgress.coerceIn(0f, 1f)
                val shadowOffsetY = 3.dp.toPx()
                val shadowBlurRadius = 8.dp.toPx()
                val shadowAlpha = enterProgress * 0.14f
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
                            trackTop + trackHeight + shadowOffsetY
                        ),
                        cornerRadius,
                        cornerRadius,
                        paint
                    )
                }
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
                if (visibleProgress > 0f) {
                    val trackPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = trackLeft,
                                top = trackTop,
                                right = trackLeft + trackWidth,
                                bottom = trackTop + trackHeight,
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        )
                    }
                    clipPath(trackPath) {
                        drawRect(
                            color = activeProgressColor,
                            topLeft = Offset(trackLeft, trackTop),
                            size = Size(progressWidth, trackHeight)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp)
                    .offset(y = (-2).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeColor = Color.White.copy(alpha = 0.90f)
                Text(
                    text = formatPlaybackTime(displayTimePositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor,
                    maxLines = 1
                )
                Text(
                    text = formatPlaybackTime(durationMs.coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .onSizeChanged { size ->
                    containerSize = size
                }
                .pointerInput(enabled, durationMs, containerSize) {
                    if (!enabled || durationMs <= 0L) {
                        return@pointerInput
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragStarted = false
                        var lastX = down.position.x

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: continue

                            if (!change.pressed) {
                                break
                            }

                            lastX = change.position.x
                            val distanceFromDown = (change.position - down.position).getDistance()
                            if (!dragStarted && distanceFromDown > viewConfiguration.touchSlop) {
                                dragStarted = true
                                isTapSeeking = false
                                isScrubbing = true
                                onScrubbingChange(true)
                            }

                            if (dragStarted) {
                                updateScrubProgress(change.position.x)
                                change.consume()
                            }
                        }

                        if (dragStarted) {
                            val targetPositionMs = (durationMs * scrubProgress)
                                .toLong()
                                .coerceIn(0L, durationMs)
                            smoothPositionMs = targetPositionMs
                            anchorPositionMs = targetPositionMs
                            anchorFrameTimeNanos = 0L
                            onSeekTo(targetPositionMs)
                            isScrubbing = false
                            onScrubbingChange(false)
                        } else {
                            val targetProgress = progressFromX(lastX)
                            val targetPositionMs = (durationMs * targetProgress)
                                .toLong()
                                .coerceIn(0L, durationMs)

                            tapSeekJob?.cancel()
                            isTapSeeking = true
                            onTapSeekVisualLock()
                            onSeekTo(targetPositionMs)
                            smoothPositionMs = targetPositionMs
                            anchorPositionMs = targetPositionMs
                            anchorFrameTimeNanos = 0L
                            tapSeekJob = tapSeekScope.launch {
                                tapSeekProgress.snapTo(currentVisibleProgress)
                                tapSeekProgress.animateTo(
                                    targetValue = targetProgress,
                                    animationSpec = tween(
                                        durationMillis = 260,
                                        easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
                                    )
                                )
                                isTapSeeking = false
                            }
                        }
                    }
                }
        )
    }
}

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun FavoriteButton(
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
private fun PlaybackOrderButton(
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
private fun TransparentControlButton(
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

@Composable
private fun ExpandedArtwork(
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

private fun lerpFloat(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

private fun delayedProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) {
        return progress.coerceIn(0f, 1f)
    }
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

private fun easeMiniPlayerMotion(progress: Float): Float {
    return MiniPlayerMotionEasing.transform(progress.coerceIn(0f, 1f))
}
