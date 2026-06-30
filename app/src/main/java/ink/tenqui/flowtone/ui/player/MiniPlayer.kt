package ink.tenqui.flowtone.ui.player

import android.util.Log
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val MiniPlayerCollapsedHeight = 92.dp
internal val MiniPlayerMinimizedHeight = 52.dp
internal val MiniPlayerDragHotZoneHeight = 20.dp

@Composable
fun MiniPlayer(
    playerUiState: PlayerUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    fullscreenHeight: Dp,
    allowFullscreenFromCollapsed: Boolean = false,
    allowFullscreenFromExpanded: Boolean = true,
    minimized: Boolean,
    onMinimizedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    sourceQueue: List<Song> = emptyList(),
    playbackQueue: List<Song> = emptyList(),
    currentQueueIndex: Int = -1,
    onPlayQueueSong: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSong = playerUiState.currentSong
    val hasCurrentSong = playerUiState.hasCurrentSong
    val title = currentSong?.title.orEmpty()
    val artist = currentSong?.artist.orEmpty()
    var collapsedMetadataSwitchDirection by remember { mutableStateOf(1) }
    val artworkUri = playerUiState.artworkUri
    val useLocalArtworkLoading = currentSong?.sourceType == SourceType.Local
    val durationMs = playerUiState.durationMs
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val collapsedHeight = MiniPlayerCollapsedHeight
    val minimizedHeight = MiniPlayerMinimizedHeight
    val dragHotZoneHeight = MiniPlayerDragHotZoneHeight
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    val fullscreenSwipeThresholdPx = with(density) { 72.dp.toPx() }
    val songSwipeThresholdPx = with(density) { 64.dp.toPx() }
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
    val fullscreenTargetHeight = if (fullscreenHeight > expandedHeight) {
        fullscreenHeight
    } else {
        expandedHeight
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
    val minimizedProgress by animateFloatAsState(
        targetValue = if (minimized) 0f else 1f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_MINIMIZE_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerMinimizedProgress"
    )
    val artworkAnimationProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = ARTWORK_ANIMATION_DURATION_MS,
            easing = ArtworkEasing
        ),
        label = "MiniPlayerArtworkProgress"
    )
    val artworkScaleProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = ARTWORK_ANIMATION_DURATION_MS,
            easing = if (expanded) {
                ArtworkScaleShrinkEasing
            } else {
                ArtworkScaleEasing
            }
        ),
        label = "MiniPlayerArtworkScaleProgress"
    )
    val baseHeight = lerpDp(minimizedHeight, collapsedHeight, minimizedProgress)
    val currentHeight = baseHeight + (expandedHeight - collapsedHeight) * animationProgress
    val fullscreenProgress by animateFloatAsState(
        targetValue = if (fullscreen && expanded && hasCurrentSong) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "MiniPlayerFullscreenProgress"
    )
    val fullscreenInteractionActive = fullscreen || fullscreenProgress > 0.01f
    val hostHeight = lerpDp(
        currentHeight + dragHotZoneHeight,
        fullscreenTargetHeight,
        fullscreenProgress
    )
    val visualPanelHeight = lerpDp(currentHeight, fullscreenTargetHeight, fullscreenProgress)
    val visualPanelTop = hostHeight - visualPanelHeight
    val handleOffsetY = (visualPanelHeight + dragHotZoneHeight) * fullscreenProgress
    val fullscreenCoverCenterY = fullscreenTargetHeight * 0.4f
    val fullscreenStationaryControlsOffsetY =
        (fullscreenTargetHeight - currentHeight) * fullscreenProgress
    val fullscreenControlsLiftY = 50.dp * fullscreenProgress
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
                .size(96, 96)
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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val fallbackSeedColor = MaterialTheme.colorScheme.primary.toArgb()
    val fallbackCloudColors = materialYouCloudColors(
        seedColors = listOf(fallbackSeedColor, fallbackSeedColor, fallbackSeedColor),
        isDarkTheme = isDarkTheme
    )
    var extractedCloudColors by remember {
        mutableStateOf<List<Color>?>(null)
    }
    LaunchedEffect(currentSong?.id, artworkUri, fallbackSeedColor, isDarkTheme) {
        if (!useLocalArtworkLoading || artworkUri == null) {
            extractedCloudColors = null
        }
        Log.d(
            FLOWTONE_CLOUD_COLORS_TAG,
            "start songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                "requestData=${paletteImageRequest?.data}"
        )

        if (artworkUri == null || paletteImageRequest == null) {
            Log.d(
                FLOWTONE_CLOUD_COLORS_TAG,
                "fallback used for songId=${currentSong?.id}, song=${title}, reason=artworkUri is null, " +
                    "path=${CloudColorPath.ThemeFallback.logName}, " +
                    "colors=${fallbackCloudColors.joinToString { it.toArgbHex() }}"
            )
            return@LaunchedEffect
        }

        extractedCloudColors = runCatching {
            withContext(Dispatchers.Default) {
                val result = context.imageLoader.execute(paletteImageRequest)
                Log.d(
                    FLOWTONE_CLOUD_COLORS_TAG,
                    "coil result songId=${currentSong?.id}, song=${title}, success=${result is SuccessResult}"
                )

                val bitmap = (result as? SuccessResult)?.image?.toBitmap(96, 96)
                    ?: error("Coil did not return a bitmap image")
                val seedResult = extractMaterialYouSeedColors(
                    bitmap = bitmap,
                    fallbackColor = fallbackSeedColor,
                    count = 3
                )
                val colors = when (seedResult.colorPath) {
                    CloudColorPath.MaterialYouSeeds -> materialYouCloudColors(
                        seedColors = seedResult.seedColors,
                        isDarkTheme = isDarkTheme
                    )

                    CloudColorPath.NeutralLowChroma -> neutralCloudColorsFromCover(
                        averageLuminance = seedResult.averageLuminance,
                        isDarkTheme = isDarkTheme
                    )

                    CloudColorPath.ThemeFallback -> fallbackCloudColors
                }
                Log.d(
                    FLOWTONE_CLOUD_COLORS_TAG,
                    "success songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                        "requestData=${paletteImageRequest.data}, bitmap=${bitmap.width}x${bitmap.height}, " +
                        "opaque=${seedResult.opaquePixelCount}, quantized=${seedResult.quantizedColorCount}, " +
                        "sat=${seedResult.averageSaturation}, lum=${seedResult.averageLuminance}, " +
                        "lowChroma=${seedResult.isLowChromaCover}, path=${seedResult.colorPath.logName}, " +
                        "seeds=${seedResult.seedColors.joinToString { it.toArgbHex() }}, " +
                        "colors=${colors.joinToString { it.toArgbHex() }}, " +
                        "fallback=${seedResult.usedFallback}, reason=${seedResult.fallbackReason.orEmpty()}"
                )
                colors
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            Log.w(
                FLOWTONE_CLOUD_COLORS_TAG,
                "fallback used for songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                    "requestData=${paletteImageRequest.data}, reason=${throwable.message}, " +
                    "path=${CloudColorPath.ThemeFallback.logName}, " +
                    "colors=${fallbackCloudColors.joinToString { it.toArgbHex() }}",
                throwable
            )
        }.getOrNull()
    }
    val cloudColors = extractedCloudColors ?: fallbackCloudColors
    LaunchedEffect(currentSong?.id, artworkUri, cloudColors) {
        Log.d(
            FLOWTONE_CLOUD_COLORS_TAG,
            "render songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                "colors=${cloudColors.joinToString { it.toArgbHex() }}, " +
                "usingFallback=${extractedCloudColors == null}"
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
    var lockedIsPlayingDuringScrub by remember { mutableStateOf(playerUiState.isPlaying) }
    var keepPlayPauseVisualLockedAfterSeek by remember { mutableStateOf(false) }
    var playPauseVisualLockToken by remember { mutableStateOf(0) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    val currentSongKey = currentSong?.id?.toString()
    var likedSongKeys by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val isCurrentSongLiked = currentSongKey != null && likedSongKeys.contains(currentSongKey)
    val onToggleCurrentSongLiked: () -> Unit = {
        currentSongKey?.let { key ->
            likedSongKeys = if (likedSongKeys.contains(key)) {
                likedSongKeys - key
            } else {
                likedSongKeys + key
            }
        }
        Unit
    }
    LaunchedEffect(currentSong?.id) {
        isProgressScrubbing = false
    }
    val visualIsPlaying = if (isProgressScrubbing || keepPlayPauseVisualLockedAfterSeek) {
        lockedIsPlayingDuringScrub
    } else {
        playerUiState.isPlaying
    }
    fun lockPlayPauseVisual(isPlayingToLock: Boolean) {
        lockedIsPlayingDuringScrub = isPlayingToLock
        keepPlayPauseVisualLockedAfterSeek = true
        playPauseVisualLockToken += 1
    }
    fun playPreviousFromMiniPlayer() {
        if (hasCurrentSong) {
            collapsedMetadataSwitchDirection = -1
            lockPlayPauseVisual(true)
            onPlayPrevious()
        }
    }
    fun playNextFromMiniPlayer() {
        if (hasCurrentSong) {
            collapsedMetadataSwitchDirection = 1
            lockPlayPauseVisual(true)
            onPlayNext()
        }
    }
    LaunchedEffect(playPauseVisualLockToken) {
        val token = playPauseVisualLockToken
        if (keepPlayPauseVisualLockedAfterSeek) {
            delay(650L)
            if (playPauseVisualLockToken == token) {
                keepPlayPauseVisualLockedAfterSeek = false
            }
        }
    }
    var accumulatedDragY by remember { mutableStateOf(0f) }
    val gestureModifier = Modifier.pointerInput(
        hasCurrentSong,
        expanded,
        fullscreenInteractionActive,
        minimized,
        swipeThresholdPx,
        fullscreenSwipeThresholdPx,
        allowFullscreenFromCollapsed,
        allowFullscreenFromExpanded
    ) {
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
                when {
                    accumulatedDragY <= -swipeThresholdPx && minimized -> {
                        onMinimizedChange(false)
                    }
                    accumulatedDragY <= -fullscreenSwipeThresholdPx &&
                        !expanded &&
                        !fullscreenInteractionActive &&
                        allowFullscreenFromCollapsed -> {
                        onMinimizedChange(false)
                        onFullscreenChange(true)
                        onExpandedChange(true)
                    }
                    accumulatedDragY <= -fullscreenSwipeThresholdPx &&
                        expanded &&
                        !fullscreenInteractionActive &&
                        allowFullscreenFromExpanded -> {
                        onFullscreenChange(true)
                    }
                    accumulatedDragY <= -swipeThresholdPx && !expanded -> {
                        onExpandedChange(true)
                    }
                    accumulatedDragY >= fullscreenSwipeThresholdPx && fullscreenInteractionActive -> {
                        onFullscreenChange(false)
                    }
                    accumulatedDragY >= swipeThresholdPx && expanded && !fullscreenInteractionActive -> {
                        onExpandedChange(false)
                    }
                    accumulatedDragY >= swipeThresholdPx &&
                        !expanded &&
                        !fullscreenInteractionActive &&
                        !minimized -> {
                        onMinimizedChange(true)
                    }
                }
            },
            onDragCancel = {
                accumulatedDragY = 0f
            }
        )
    }
    val progressGestureStartY =
        expandedProgressTop + fullscreenStationaryControlsOffsetY - fullscreenControlsLiftY
    val progressGestureEndY = progressGestureStartY + 64.dp
    val progressGestureIgnoreRangePx = with(density) {
        progressGestureStartY.toPx()..progressGestureEndY.toPx()
    }
    val songSwipeModifier = Modifier.swipeToChangeSong(
        enabled = hasCurrentSong,
        thresholdPx = songSwipeThresholdPx,
        ignoredStartYRangePx = progressGestureIgnoreRangePx,
        onSwipeLeft = ::playNextFromMiniPlayer,
        onSwipeRight = ::playPreviousFromMiniPlayer
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hostHeight)
            .graphicsLayer {
                translationY = miniPlayerSlideOffsetY.toPx()
                alpha = visibleProgress
                clip = fullscreenProgress > 0.01f
            }
            .then(gestureModifier)
    ) {
        val playerShape = RoundedCornerShape(
            topStart = lerpDp(24.dp, 0.dp, fullscreenProgress),
            topEnd = lerpDp(24.dp, 0.dp, fullscreenProgress),
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        val playerShadowElevation = lerpDp(0.dp, 18.dp, animationProgress)
        PlayerDragHandle(
            animationProgress = animationProgress,
            hasCurrentSong = hasCurrentSong,
            expanded = expanded,
            interactionSource = noRippleInteractionSource,
            onActivate = {
                if (minimized) {
                    onMinimizedChange(false)
                } else {
                    onExpandedChange(true)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(dragHotZoneHeight)
                .graphicsLayer {
                    translationY = handleOffsetY.toPx()
                }
                .align(Alignment.TopCenter)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = visualPanelTop)
                .fillMaxWidth()
                .height(visualPanelHeight)
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
                    if (minimized) {
                        onMinimizedChange(false)
                    } else {
                        onExpandedChange(true)
                    }
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(visualPanelHeight)
                .graphicsLayer {
                    shape = playerShape
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .then(songSwipeModifier)
                .then(
                    if (hasArtworkBackground) {
                            Modifier
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    )
            ) {
                val playerWidth = maxWidth
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                BlurredArtworkBackground(
                    imageRequest = backgroundImageRequest,
                    alpha = lerpFloat(0.78f, 0f, animationProgress),
                    waitForArtworkLoad = useLocalArtworkLoading,
                    modifier = Modifier.matchParentSize()
                )
                CrossfadeFlowCloudBackground(
                    colors = cloudColors,
                    progress = animationProgress,
                    isPlaying = playerUiState.isPlaying,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, animationProgress)))
                )
                val fullscreenProgressTrackWidth = playerWidth * 0.76f
                val fullscreenArtworkX = (playerWidth - fullscreenProgressTrackWidth) / 2f
                val fullscreenMetadataTop =
                    fullscreenCoverCenterY + fullscreenProgressTrackWidth / 2f + 14.dp
                MorphArtworkLayer(
                    imageRequest = coverImageRequest,
                    waitForArtworkLoad = useLocalArtworkLoading,
                    progress = artworkAnimationProgress,
                    scaleProgress = artworkScaleProgress,
                    currentHeight = currentHeight,
                    viewportHeight = currentHeight,
                    collapsedHeight = collapsedHeight,
                    playerWidth = playerWidth,
                    expandedArtworkSize = expandedArtworkSize,
                    expandedArtworkTop = expandedArtworkTop,
                    fullscreenProgress = fullscreenProgress,
                    fullscreenArtworkSize = fullscreenProgressTrackWidth,
                    fullscreenArtworkCenterY = fullscreenCoverCenterY,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            translationY = with(density) {
                                (16.dp * (1f - minimizedProgress) * (1f - fullscreenProgress)).toPx()
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentHeight)
                        .align(Alignment.TopCenter)
                ) {
                    SharedSongInfo(
                        title = title,
                        artist = artist,
                        currentSongKey = currentSong?.id,
                        progress = animationProgress,
                        titleColor = titleColor,
                        artistColor = artistColor,
                        playerWidth = playerWidth,
                        minimizedProgress = minimizedProgress,
                        minimizedHeight = minimizedHeight,
                        collapsedHeight = collapsedHeight,
                        expandedTop = expandedMetadataTop,
                        fullscreenProgress = fullscreenProgress,
                        fullscreenX = fullscreenArtworkX,
                        fullscreenTop = fullscreenMetadataTop,
                        switchDirection = collapsedMetadataSwitchDirection,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    ExpandedOnlyContent(
                        progress = animationProgress,
                        positionMs = playerUiState.positionMs,
                        durationMs = durationMs,
                        isPlaying = playerUiState.isPlaying,
                        isPlayingForVisualLock = visualIsPlaying,
                        currentSongKey = currentSong?.id,
                        hasCurrentSong = hasCurrentSong,
                        progressTrackColor = progressTrackColor,
                        progressColor = progressColor,
                        fullscreenProgress = fullscreenProgress,
                        onSeekTo = onSeekTo,
                        onLockPlayPauseVisual = ::lockPlayPauseVisual,
                        onScrubbingChange = { scrubbing ->
                            isProgressScrubbing = scrubbing
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = expandedProgressTop)
                            .graphicsLayer {
                                translationY = (fullscreenStationaryControlsOffsetY - fullscreenControlsLiftY).toPx()
                            }
                    )
                    SharedPlaybackControls(
                        progress = animationProgress,
                        isPlaying = visualIsPlaying,
                        iconColor = controlIconColor,
                        screenWidth = playerWidth,
                        minimizedProgress = minimizedProgress,
                        minimizedHeight = minimizedHeight,
                        collapsedHeight = collapsedHeight,
                        expandedTop = expandedControlsTop,
                        fullscreenProgress = fullscreenProgress,
                        onPlayPrevious = ::playPreviousFromMiniPlayer,
                        onTogglePlayPause = {
                            if (hasCurrentSong) {
                                isProgressScrubbing = false
                                keepPlayPauseVisualLockedAfterSeek = false
                                onTogglePlayPause()
                            }
                        },
                        onPlayNext = ::playNextFromMiniPlayer,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                translationY = (fullscreenStationaryControlsOffsetY - fullscreenControlsLiftY).toPx()
                            }
                    )
                    SideButtonsOverlay(
                        progress = animationProgress,
                        playerWidth = playerWidth,
                        currentHeight = currentHeight,
                        expandedHeight = expandedHeight,
                        expandedProgressTop = expandedProgressTop,
                        expandedControlsTop = expandedControlsTop,
                        hasCurrentSong = hasCurrentSong,
                        isCurrentSongLiked = isCurrentSongLiked,
                        playbackOrderMode = playerUiState.playbackOrderMode,
                        iconColor = controlIconColor,
                        fullscreenProgress = fullscreenProgress,
                        onToggleLiked = onToggleCurrentSongLiked,
                        onOpenQueue = {
                            showQueueSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = (fullscreenStationaryControlsOffsetY - fullscreenControlsLiftY).toPx()
                            },
                        onTogglePlaybackOrderMode = onTogglePlaybackOrderMode
                    )
                }
                FullscreenCollapseArrow(
                    progress = fullscreenProgress,
                    interactionSource = noRippleInteractionSource,
                    onClick = {
                        if (allowFullscreenFromCollapsed) {
                            onFullscreenChange(false)
                            onExpandedChange(false)
                        } else {
                            onFullscreenChange(false)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(10f)
                )
            }
        }
    }
    if (showQueueSheet) {
        PlayerQueueBottomSheet(
            playbackQueue = playbackQueue,
            sourceQueue = sourceQueue,
            currentQueueIndex = currentQueueIndex,
            currentSong = currentSong,
            onSongClick = onPlayQueueSong,
            onDismiss = {
                showQueueSheet = false
            }
        )
    }
}

@Composable
private fun FullscreenCollapseArrow(
    progress: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleProgress = SoftElementEasing.transform(progress.coerceIn(0f, 1f))
    val density = LocalDensity.current
    val safeTopPadding = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val arrowOffsetY = lerpDp((-32).dp, safeTopPadding + 18.dp, visibleProgress)

    Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = "\u6536\u8d77\u5168\u5c4f\u64ad\u653e\u5668",
        tint = Color.White,
        modifier = modifier
            .offset(y = arrowOffsetY)
            .size(width = 42.dp, height = 30.dp)
            .clickable(
                enabled = progress > 0.72f,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                alpha = visibleProgress
                scaleX = lerpFloat(0.72f, 1f, visibleProgress)
                scaleY = lerpFloat(0.72f, 1f, visibleProgress)
            }
    )
}
