package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun PlaybackProgressBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isPlayingForVisualLock: Boolean,
    strictProgressBar: Boolean,
    currentSongKey: Long?,
    enabled: Boolean,
    trackColor: Color,
    progressColor: Color,
    onSeekTo: (Long) -> Unit,
    onLockPlayPauseVisual: (Boolean) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    enterProgress: Float,
    fullscreenProgress: Float,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var smoothPositionMs by remember { mutableStateOf(positionMs) }
    var anchorPositionMs by remember { mutableStateOf(positionMs) }
    var anchorFrameTimeNanos by remember { mutableStateOf(0L) }
    var isTapSeeking by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember { mutableStateOf<Long?>(null) }
    val tapSeekProgress = remember { Animatable(0f) }
    val tapSeekScope = rememberCoroutineScope()
    var tapSeekJob by remember { mutableStateOf<Job?>(null) }
    val trackSwitchProgress = remember { Animatable(1f) }
    var lastSongKey by remember { mutableStateOf(currentSongKey) }
    var lastRenderedProgress by remember { mutableStateOf(0f) }
    var trackSwitchStartProgress by remember { mutableStateOf(0f) }
    var isTrackSwitchProgressAnimating by remember { mutableStateOf(false) }
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isScrubbing) {
            PlaybackProgressScrubbingTrackHeight
        } else {
            PlaybackProgressTrackHeight
        },
        animationSpec = tween(
            durationMillis = PlaybackProgressTrackHeightAnimationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "ProgressTrackHeight"
    )
    LaunchedEffect(positionMs, durationMs, currentSongKey) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration)
        if (
            !isPlaying ||
            kotlin.math.abs(smoothPositionMs - safePosition) >
            PlaybackProgressPositionSnapThresholdMs
        ) {
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
        pendingSeekPositionMs = null
    }
    LaunchedEffect(strictProgressBar, durationMs, pendingSeekPositionMs, positionMs) {
        val pendingPositionMs = pendingSeekPositionMs
        if (!strictProgressBar || durationMs <= 0L) {
            pendingSeekPositionMs = null
            return@LaunchedEffect
        }
        if (pendingPositionMs == null) {
            return@LaunchedEffect
        }

        val safeDurationMs = durationMs.coerceAtLeast(0L)
        val safePendingPositionMs = pendingPositionMs.coerceIn(0L, safeDurationMs)
        val safePlaybackPositionMs = positionMs.coerceIn(0L, safeDurationMs)
        if (
            kotlin.math.abs(safePlaybackPositionMs - safePendingPositionMs) <=
            PlaybackProgressPendingSeekToleranceMs
        ) {
            pendingSeekPositionMs = null
        }
    }
    LaunchedEffect(strictProgressBar, currentSongKey, pendingSeekPositionMs) {
        val pendingPositionMs = pendingSeekPositionMs
        if (!strictProgressBar || pendingPositionMs == null) {
            return@LaunchedEffect
        }

        delay(PlaybackProgressPendingSeekTimeoutMillis)
        if (pendingSeekPositionMs == pendingPositionMs) {
            pendingSeekPositionMs = null
        }
    }

    val smoothPlaybackProgress = progressFraction(
        positionMs = smoothPositionMs,
        durationMs = durationMs
    )
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
    val animatedDisplayTimePositionMs = positionFromProgress(
        durationMs = durationMs,
        progress = visibleProgress
    )
    val displayTimePositionMs = if (strictProgressBar) {
        when {
            durationMs <= 0L -> 0L
            isScrubbing -> positionFromProgress(
                durationMs = durationMs,
                progress = scrubProgress
            )
            pendingSeekPositionMs != null -> pendingSeekPositionMs
                ?.coerceIn(0L, durationMs.coerceAtLeast(0L))
                ?: 0L
            else -> positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        }
    } else {
        animatedDisplayTimePositionMs
    }
    val activeProgressColor = progressColor

    fun updateScrubProgress(x: Float) {
        scrubProgress = progressFromX(
            x = x,
            width = containerSize.width.toFloat()
        )
    }

    fun rememberPendingSeek(positionMs: Long) {
        if (strictProgressBar) {
            pendingSeekPositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        }
    }

    Box(modifier = modifier) {
        PlayerProgressBarContent(
            enterProgress = enterProgress
        ) {
            PlayerProgressBarTrack(
                visibleProgress = visibleProgress,
                trackHeight = animatedTrackHeight,
                trackColor = trackColor,
                progressColor = activeProgressColor,
                enterProgress = enterProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlaybackProgressCanvasHeight)
            )
            PlayerProgressBarLabels(
                displayTimePositionMs = displayTimePositionMs,
                durationMs = durationMs
            )
        }
        PlayerProgressBarGestureLayer(
            enabled = enabled,
            durationMs = durationMs,
            containerSize = containerSize,
            onContainerSizeChange = { size ->
                containerSize = size
            },
            onEnterScrubbing = { x ->
                isTapSeeking = false
                pendingSeekPositionMs = null
                isScrubbing = true
                updateScrubProgress(x)
                onLockPlayPauseVisual(currentIsPlayingForVisualLock)
                onScrubbingChange(true)
            },
            onUpdateScrubbing = { x ->
                updateScrubProgress(x)
            },
            onScrubSeek = {
                val targetPositionMs = positionFromProgress(
                    durationMs = durationMs,
                    progress = scrubProgress
                )
                smoothPositionMs = targetPositionMs
                anchorPositionMs = targetPositionMs
                anchorFrameTimeNanos = 0L
                rememberPendingSeek(targetPositionMs)
                onLockPlayPauseVisual(currentIsPlayingForVisualLock)
                onSeekTo(targetPositionMs)
                isScrubbing = false
                onScrubbingChange(false)
            },
            onTapSeek = { x ->
                val targetProgress = progressFromX(
                    x = x,
                    width = containerSize.width.toFloat()
                )
                val targetPositionMs = positionFromProgress(
                    durationMs = durationMs,
                    progress = targetProgress
                )

                tapSeekJob?.cancel()
                isTapSeeking = true
                rememberPendingSeek(targetPositionMs)
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
                            durationMillis = PlaybackProgressTapSeekAnimationMillis,
                            easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
                        )
                    )
                    isTapSeeking = false
                }
            }
        )
    }
}

private const val PlaybackProgressPendingSeekToleranceMs = 500L
private const val PlaybackProgressPendingSeekTimeoutMillis = 1_500L
