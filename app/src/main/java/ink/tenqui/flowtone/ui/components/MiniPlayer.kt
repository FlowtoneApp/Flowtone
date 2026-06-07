package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import ink.tenqui.flowtone.playback.PlaybackState

private const val MINI_PLAYER_ANIMATION_DURATION_MS = 390

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong = playbackState.currentSong ?: return
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
    val expandedLyricTop = expandedMetadataTop + 72.dp
    val expandedControlsTop = expandedLyricTop + 62.dp
    val animationProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerProgress"
    )
    val currentHeight = collapsedHeight + (expandedHeight - collapsedHeight) * animationProgress
    val coverScale = 1f + (1.18f - 1f) * animationProgress
    val hasArtworkBackground = currentSong.artworkUri != null
    val backgroundImageRequest: ImageRequest? = remember(currentSong.artworkUri, context) {
        currentSong.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(256, 256)
                .crossfade(false)
                .build()
        }
    }
    val coverImageRequest: ImageRequest? = remember(currentSong.artworkUri, context) {
        currentSong.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(768, 768)
                .crossfade(false)
                .build()
        }
    }
    LaunchedEffect(coverImageRequest) {
        coverImageRequest?.let { request ->
            context.imageLoader.enqueue(request)
        }
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
    val expandedSecondaryColor = if (hasArtworkBackground) {
        Color.White.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progressTrackColor = if (hasArtworkBackground) {
        Color.White.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val progressColor = if (hasArtworkBackground) {
        Color.White.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    var accumulatedDragY by remember { mutableStateOf(0f) }
    val gestureModifier = Modifier.pointerInput(expanded, swipeThresholdPx) {
        detectVerticalDragGestures(
            onDragStart = {
                accumulatedDragY = 0f
            },
            onVerticalDrag = { _, dragAmount ->
                accumulatedDragY += dragAmount
            },
            onDragEnd = {
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
            .then(gestureModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dragHotZoneHeight)
                .align(Alignment.TopCenter)
                .clickable(
                    enabled = !expanded,
                    interactionSource = noRippleInteractionSource,
                    indication = null
                ) {
                    onExpandedChange(true)
                }
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(currentHeight)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .then(
                    if (hasArtworkBackground) {
                        Modifier
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    }
                )
                .clickable(
                    enabled = !expanded,
                    interactionSource = noRippleInteractionSource,
                    indication = null
                ) {
                    onExpandedChange(true)
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight),
                contentAlignment = Alignment.BottomCenter
            ) {
                val playerWidth = maxWidth
                backgroundImageRequest?.let { request ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(expandedHeight)
                            .graphicsLayer {
                                scaleX = coverScale
                                scaleY = coverScale
                            }
                            .blur(16.dp)
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                }
                ExpandedArtwork(
                    imageRequest = coverImageRequest,
                    progress = animationProgress,
                    artworkSize = expandedArtworkSize,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = expandedArtworkTop)
                )
                ExpandedOnlyContent(
                    progress = animationProgress,
                    lyricColor = expandedSecondaryColor,
                    progressTrackColor = progressTrackColor,
                    progressColor = progressColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = expandedLyricTop)
                )
                SharedSongInfo(
                    title = currentSong.title,
                    artist = currentSong.artist,
                    progress = animationProgress,
                    titleColor = titleColor,
                    artistColor = artistColor,
                    playerWidth = playerWidth,
                    collapsedHeight = collapsedHeight,
                    expandedArtworkSize = expandedArtworkSize,
                    expandedTop = expandedMetadataTop,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                )
                SharedPlaybackControls(
                    progress = animationProgress,
                    isPlaying = playbackState.isPlaying,
                    iconColor = controlIconColor,
                    screenWidth = playerWidth,
                    collapsedHeight = collapsedHeight,
                    expandedTop = expandedControlsTop,
                    onPlayPrevious = onPlayPrevious,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                )
            }
        }
    }
}

@Composable
private fun ExpandedOnlyContent(
    progress: Float,
    lyricColor: Color,
    progressTrackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    fun offsetPx(dp: androidx.compose.ui.unit.Dp): Float = with(density) {
        (dp * (1f - progress)).toPx()
    }
    val helperAlpha = 0.85f + 0.15f * progress

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\u266a \u6682\u65e0\u6b4c\u8bcd",
            style = MaterialTheme.typography.bodyMedium,
            color = lyricColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 14.dp)
                .graphicsLayer {
                    alpha = helperAlpha
                    translationY = offsetPx(40.dp)
                }
        )
        FakeProgressBar(
            progress = 0.35f,
            trackColor = progressTrackColor,
            progressColor = progressColor,
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth(0.76f)
                .graphicsLayer {
                    alpha = helperAlpha
                scaleX = 0.92f + (1f - 0.92f) * progress
                translationY = offsetPx(36.dp)
            }
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
    expandedArtworkSize: Dp,
    expandedTop: Dp,
    modifier: Modifier = Modifier
) {
    val metadataGroupHeight = 60.dp
    val collapsedCenterY = collapsedHeight / 2f
    val collapsedMetadataX = 30.dp
    val collapsedControlsReservedWidth = 48.dp * 3f + 8.dp * 2f + 30.dp
    val collapsedMetadataWidth = playerWidth - collapsedMetadataX - collapsedControlsReservedWidth
    val collapsedMetadataY = collapsedCenterY - metadataGroupHeight / 2f
    val expandedMetadataWidth = expandedArtworkSize
    val expandedMetadataX = (playerWidth - expandedArtworkSize) / 2f
    val expandedMetadataY = expandedTop
    val metadataX = lerpDp(collapsedMetadataX, expandedMetadataX, progress)
    val metadataY = lerpDp(collapsedMetadataY, expandedMetadataY, progress)
    val metadataWidth = lerpDp(collapsedMetadataWidth, expandedMetadataWidth, progress)
    val metadataScale = lerpFloat(0.95f, 1.2f, progress)

    Column(
        modifier = modifier
            .width(metadataWidth)
            .height(metadataGroupHeight)
            .graphicsLayer {
                translationX = metadataX.toPx()
                translationY = metadataY.toPx()
                scaleX = metadataScale
                scaleY = metadataScale
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = artistColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
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
    val expandedSpacing = 36.dp
    val previousNextTouchSize = lerpDp(collapsedTouchSize, expandedPreviousNextTouchSize, progress)
    val playPauseTouchSize = lerpDp(collapsedTouchSize, expandedPlayPauseTouchSize, progress)
    val previousNextIconSize = lerpDp(24.dp, 32.dp, progress)
    val playPauseIconSize = lerpDp(28.dp, 42.dp, progress)
    val spacing = lerpDp(collapsedSpacing, expandedSpacing, progress)
    val collapsedControlsWidth = collapsedTouchSize * 3f + collapsedSpacing * 2f
    val expandedControlsWidth = expandedPreviousNextTouchSize * 2f +
        expandedPlayPauseTouchSize +
        expandedSpacing * 2f
    val controlsWidth = previousNextTouchSize * 2f + playPauseTouchSize + spacing * 2f
    val collapsedLeft = screenWidth - collapsedControlsWidth - 30.dp
    val expandedLeft = (screenWidth - expandedControlsWidth) / 2f
    val collapsedControlsY = (collapsedHeight - collapsedTouchSize) / 2f
    val currentLeft = lerpDp(collapsedLeft, expandedLeft, progress)
    val currentTop = lerpDp(collapsedControlsY, expandedTop, progress)

    Row(
        modifier = modifier
            .width(controlsWidth)
            .graphicsLayer {
                translationX = currentLeft.toPx()
                translationY = currentTop.toPx()
            },
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransparentControlButton(
            onClick = onPlayPrevious,
            modifier = Modifier.size(previousNextTouchSize)
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
            modifier = Modifier.size(playPauseTouchSize)
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
            modifier = Modifier.size(previousNextTouchSize)
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
private fun FakeProgressBar(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .height(4.dp)
            .clip(shape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(shape)
                .background(progressColor)
        )
    }
}

@Composable
private fun TransparentControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(52.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(
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
