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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val MiniPlayerCollapsedHeight = 92.dp
internal val MiniPlayerDragHotZoneHeight = 20.dp

@Composable
fun MiniPlayer(
    playerUiState: PlayerUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong = playerUiState.currentSong
    val hasCurrentSong = playerUiState.hasCurrentSong
    val title = currentSong?.title.orEmpty()
    val artist = currentSong?.artist.orEmpty()
    var collapsedMetadataSwitchDirection by remember { mutableStateOf(1) }
    val artworkUri = playerUiState.artworkUri
    val durationMs = playerUiState.durationMs
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val collapsedHeight = MiniPlayerCollapsedHeight
    val dragHotZoneHeight = MiniPlayerDragHotZoneHeight
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
        extractedCloudColors = null
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

                val bitmap = (result as? SuccessResult)?.image?.toBitmap(128, 128)
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
    val currentSongKey = currentSong?.id?.toString()
    var likedSongKeys by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val isCurrentSongLiked = currentSongKey != null && likedSongKeys.contains(currentSongKey)
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
        val playerShape = RoundedCornerShape(24.dp)
        val playerShadowElevation = lerpDp(0.dp, 18.dp, animationProgress)
        PlayerDragHandle(
            animationProgress = animationProgress,
            hasCurrentSong = hasCurrentSong,
            expanded = expanded,
            interactionSource = noRippleInteractionSource,
            onExpandedChange = onExpandedChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(dragHotZoneHeight)
                .align(Alignment.TopCenter)
        )
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
            BoxWithConstraints(
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
                val playerWidth = maxWidth
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
                    colors = cloudColors,
                    progress = animationProgress,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, animationProgress)))
                )
                MorphArtworkLayer(
                    imageRequest = coverImageRequest,
                    progress = artworkAnimationProgress,
                    scaleProgress = artworkScaleProgress,
                    currentHeight = currentHeight,
                    viewportHeight = currentHeight,
                    collapsedHeight = collapsedHeight,
                    playerWidth = playerWidth,
                    expandedArtworkSize = expandedArtworkSize,
                    expandedArtworkTop = expandedArtworkTop,
                    modifier = Modifier
                        .align(Alignment.TopStart)
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
                        progress = animationProgress,
                        titleColor = titleColor,
                        artistColor = artistColor,
                        playerWidth = playerWidth,
                        collapsedHeight = collapsedHeight,
                        expandedTop = expandedMetadataTop,
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
                        onSeekTo = onSeekTo,
                        onLockPlayPauseVisual = ::lockPlayPauseVisual,
                        onScrubbingChange = { scrubbing ->
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
                                collapsedMetadataSwitchDirection = -1
                                lockPlayPauseVisual(true)
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
                                collapsedMetadataSwitchDirection = 1
                                lockPlayPauseVisual(true)
                                onPlayNext()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    SideButtonsOverlay(
                        progress = animationProgress,
                        playerWidth = playerWidth,
                        currentHeight = currentHeight,
                        expandedHeight = expandedHeight,
                        expandedControlsTop = expandedControlsTop,
                        hasCurrentSong = hasCurrentSong,
                        isCurrentSongLiked = isCurrentSongLiked,
                        playbackOrderMode = playerUiState.playbackOrderMode,
                        iconColor = controlIconColor,
                        onToggleLiked = {
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
                            .fillMaxWidth(),
                        onTogglePlaybackOrderMode = onTogglePlaybackOrderMode
                    )
                }
            }
        }
    }
}
