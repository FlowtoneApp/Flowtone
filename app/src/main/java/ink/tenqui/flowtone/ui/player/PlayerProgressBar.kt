package ink.tenqui.flowtone.ui.player

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun PlaybackProgressBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isPlayingForVisualLock: Boolean,
    currentSongKey: Long?,
    enabled: Boolean,
    trackColor: Color,
    progressColor: Color,
    onSeekTo: (Long) -> Unit,
    onLockPlayPauseVisual: (Boolean) -> Unit,
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
    val currentIsPlayingForVisualLock by rememberUpdatedState(isPlayingForVisualLock)
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
                        var longPressStarted = false
                        var lastX = down.position.x
                        val progressLongPressTimeoutMillis = 260L
                        val longPressDeadlineMillis =
                            down.uptimeMillis + progressLongPressTimeoutMillis
                        var lastEventTimeMillis = down.uptimeMillis

                        fun enterScrubbing(x: Float) {
                            isTapSeeking = false
                            isScrubbing = true
                            updateScrubProgress(x)
                            onLockPlayPauseVisual(currentIsPlayingForVisualLock)
                            onScrubbingChange(true)
                        }

                        while (true) {
                            val event = if (!dragStarted && !longPressStarted) {
                                val remainingMillis =
                                    longPressDeadlineMillis - lastEventTimeMillis
                                if (remainingMillis <= 0L) {
                                    null
                                } else {
                                    withTimeoutOrNull(remainingMillis) {
                                        awaitPointerEvent()
                                    }
                                }
                            } else {
                                awaitPointerEvent()
                            }

                            if (event == null) {
                                longPressStarted = true
                                enterScrubbing(lastX)
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: continue
                            lastEventTimeMillis = change.uptimeMillis

                            if (!change.pressed) {
                                break
                            }

                            lastX = change.position.x
                            val distanceFromDown = (change.position - down.position).getDistance()
                            if (
                                !dragStarted &&
                                !longPressStarted &&
                                distanceFromDown > viewConfiguration.touchSlop
                            ) {
                                dragStarted = true
                                enterScrubbing(change.position.x)
                            }

                            if (dragStarted || longPressStarted) {
                                updateScrubProgress(change.position.x)
                                change.consume()
                            }
                        }

                        if (dragStarted || longPressStarted) {
                            val targetPositionMs = (durationMs * scrubProgress)
                                .toLong()
                                .coerceIn(0L, durationMs)
                            smoothPositionMs = targetPositionMs
                            anchorPositionMs = targetPositionMs
                            anchorFrameTimeNanos = 0L
                            onLockPlayPauseVisual(currentIsPlayingForVisualLock)
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
                            onLockPlayPauseVisual(currentIsPlayingForVisualLock)
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

internal fun formatPlaybackTime(positionMs: Long): String {
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

