package ink.tenqui.flowtone.ui.player

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class PlaybackProgressSeekAnimation(
    val sequence: Long,
    val songId: Long,
    val targetPositionMs: Long
)

@Composable
internal fun PlaybackProgressBar(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isPlayingForVisualLock: Boolean,
    isPlaybackWaitingForData: Boolean,
    strictProgressBar: Boolean,
    lyricProgressSeekAnimation: PlaybackProgressSeekAnimation?,
    currentSongKey: Long?,
    currentQueueId: String?,
    enabled: Boolean,
    trackColor: Color,
    progressColor: Color,
    onSeekTo: (Long) -> Unit,
    onProgressChanged: () -> Unit,
    onLockPlayPauseVisual: (Boolean) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    enterProgress: Float,
    fullscreenProgress: Float,
    modifier: Modifier = Modifier
) {
    val currentPlaybackIdentity = currentQueueId ?: currentSongKey?.toString()
    var isScrubbing by remember(currentPlaybackIdentity) { mutableStateOf(false) }
    var scrubProgress by remember(currentPlaybackIdentity) { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isTapSeeking by remember(currentPlaybackIdentity) { mutableStateOf(false) }
    var lastHandledLyricSeekSequence by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var pendingSeekPositionMs by remember(currentPlaybackIdentity) { mutableStateOf<Long?>(null) }
    val tapSeekProgress = remember(currentPlaybackIdentity) { Animatable(0f) }
    val tapSeekScope = rememberCoroutineScope()
    var tapSeekJob by remember { mutableStateOf<Job?>(null) }
    val targetBufferedProgress = progressFraction(
        positionMs = bufferedPositionMs,
        durationMs = durationMs
    )
    val animatedBufferedProgress = remember { Animatable(targetBufferedProgress) }
    val waitingTrackPulse = remember { Animatable(0f) }
    var lastPlaybackIdentity by remember { mutableStateOf(currentPlaybackIdentity) }
    var bufferedProgressSongKey by remember { mutableStateOf(currentPlaybackIdentity) }
    var lastRenderedProgress by remember { mutableStateOf(0f) }
    val trackSwitchResetProgress = remember { Animatable(1f) }
    var trackSwitchResetStartProgress by remember { mutableStateOf(0f) }
    var isTrackSwitchResetAnimating by remember { mutableStateOf(false) }
    var trackSwitchResetCancellationSequence by remember { mutableLongStateOf(0L) }
    var trackSwitchResetSuppressedIdentity by remember { mutableStateOf<String?>(null) }
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
    val identityChanged = playbackSongIdentityChanged(lastPlaybackIdentity, currentPlaybackIdentity)
    val trackSwitchResetStartCandidate = lastRenderedProgress.coerceIn(0f, 1f)
    LaunchedEffect(currentPlaybackIdentity, trackSwitchResetCancellationSequence) {
        if (
            !identityChanged ||
            trackSwitchResetSuppressedIdentity == currentPlaybackIdentity
        ) {
            return@LaunchedEffect
        }

        trackSwitchResetStartProgress = trackSwitchResetStartCandidate
        isTrackSwitchResetAnimating = trackSwitchResetStartProgress > 0f
        if (isTrackSwitchResetAnimating) {
            trackSwitchResetProgress.snapTo(0f)
            trackSwitchResetProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PlaybackProgressTrackSwitchResetAnimationMillis,
                    easing = TrackSwitchProgressEasing
                )
            )
        }
        isTrackSwitchResetAnimating = false
    }
    LaunchedEffect(positionMs, durationMs, currentPlaybackIdentity) {
        if (identityChanged) {
            tapSeekJob?.cancel()
            isScrubbing = false
            scrubProgress = 0f
            isTapSeeking = false
            lastRenderedProgress = 0f
            lastPlaybackIdentity = currentPlaybackIdentity
            return@LaunchedEffect
        }
        if (durationMs <= 0L) {
            scrubProgress = 0f
            isScrubbing = false
        }
    }
    LaunchedEffect(currentPlaybackIdentity, targetBufferedProgress) {
        if (playbackSongIdentityChanged(bufferedProgressSongKey, currentPlaybackIdentity)) {
            animatedBufferedProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = PlaybackBufferedTrackSwitchResetMillis,
                    easing = TrackSwitchProgressEasing
                )
            )
            bufferedProgressSongKey = currentPlaybackIdentity
        }
        animatedBufferedProgress.animateTo(
            targetValue = targetBufferedProgress,
            animationSpec = tween(
                durationMillis = PlaybackBufferedProgressAnimationMillis,
                easing = TrackSwitchProgressEasing
            )
        )
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
    LaunchedEffect(strictProgressBar, currentPlaybackIdentity, pendingSeekPositionMs) {
        val pendingPositionMs = pendingSeekPositionMs
        if (!strictProgressBar || pendingPositionMs == null) {
            return@LaunchedEffect
        }

        delay(PlaybackProgressPendingSeekTimeoutMillis)
        if (pendingSeekPositionMs == pendingPositionMs) {
            pendingSeekPositionMs = null
        }
    }

    val authoritativePlaybackProgress = progressFraction(
        positionMs = positionMs,
        durationMs = durationMs
    )
    val trackSwitchResetPending =
        identityChanged &&
            !isTrackSwitchResetAnimating &&
            trackSwitchResetSuppressedIdentity != currentPlaybackIdentity
    val trackSwitchVisualProgress = playedProgressDuringTrackSwitchReset(
        resetIsAnimating = isTrackSwitchResetAnimating || trackSwitchResetPending,
        resetStartProgress = if (trackSwitchResetPending) {
            trackSwitchResetStartCandidate
        } else {
            trackSwitchResetStartProgress
        },
        resetAnimationProgress = if (trackSwitchResetPending) 0f else trackSwitchResetProgress.value,
        currentTrackProgress = playedProgressForIdentity(
            identityChanged = identityChanged,
            currentTrackProgress = authoritativePlaybackProgress
        )
    )
    val visibleProgress = when {
        isScrubbing -> scrubProgress
        isTapSeeking -> tapSeekProgress.value
        strictProgressBar && pendingSeekPositionMs != null -> progressFraction(
            positionMs = pendingSeekPositionMs ?: 0L,
            durationMs = durationMs
        )
        else -> trackSwitchVisualProgress
    }.coerceIn(0f, 1f)
    val progressDiagnosticLogUptimeMs = remember { longArrayOf(0L) }
    SideEffect {
        val now = SystemClock.elapsedRealtime()
        if (
            progressDiagnosticLogUptimeMs[0] == 0L ||
            now - progressDiagnosticLogUptimeMs[0] >= ProgressDiagnosticLogIntervalMillis
        ) {
            progressDiagnosticLogUptimeMs[0] = now
            Log.d(
                ProgressDiagnosticLogTag,
                "progressBar queueId=$currentQueueId identity=$currentPlaybackIdentity " +
                    "positionMs=$positionMs bufferedPositionMs=$bufferedPositionMs " +
                    "durationMs=$durationMs authoritativeProgress=$authoritativePlaybackProgress " +
                    "resetActive=${isTrackSwitchResetAnimating || trackSwitchResetPending} " +
                    "resetValue=${trackSwitchResetProgress.value} isScrubbing=$isScrubbing " +
                    "isTapSeeking=$isTapSeeking pendingSeekPositionMs=$pendingSeekPositionMs " +
                    "identityChanged=$identityChanged awaitingFreshPosition=false " +
                    "finalVisibleProgress=$visibleProgress"
            )
        }
    }
    val currentVisibleProgress by rememberUpdatedState(visibleProgress)
    val currentIsPlayingForVisualLock by rememberUpdatedState(isPlayingForVisualLock)
    val lyricSeekAnimationPending = lyricProgressSeekAnimation?.let { request ->
        request.sequence != lastHandledLyricSeekSequence &&
            request.songId == currentSongKey
    } == true
    SideEffect {
        if (
            !identityChanged &&
            !isScrubbing &&
            !isTapSeeking &&
            !lyricSeekAnimationPending
        ) {
            lastRenderedProgress = visibleProgress.coerceIn(0f, 1f)
        }
    }
    val displayTimePositionMs = when {
        durationMs <= 0L -> 0L
        isScrubbing -> positionFromProgress(durationMs = durationMs, progress = scrubProgress)
        isTapSeeking -> positionFromProgress(durationMs = durationMs, progress = tapSeekProgress.value)
        strictProgressBar && pendingSeekPositionMs != null -> pendingSeekPositionMs
            ?.coerceIn(0L, durationMs.coerceAtLeast(0L))
            ?: 0L
        else -> positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
    val activeProgressColor = progressColor
    val bufferedProgressColor = lerp(trackColor, progressColor, 0.20f)
    LaunchedEffect(isPlaybackWaitingForData) {
        if (!isPlaybackWaitingForData) {
            waitingTrackPulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = PlaybackWaitingTrackSettleMillis,
                    easing = TrackSwitchProgressEasing
                )
            )
            return@LaunchedEffect
        }

        while (isActive) {
            waitingTrackPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PlaybackWaitingTrackHalfCycleMillis,
                    easing = TrackSwitchProgressEasing
                )
            )
            waitingTrackPulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = PlaybackWaitingTrackHalfCycleMillis,
                    easing = TrackSwitchProgressEasing
                )
            )
        }
    }
    val animatedTrackColor = lerp(
        trackColor,
        bufferedProgressColor,
        waitingTrackPulse.value * PlaybackWaitingTrackPeakFraction
    )

    fun updateScrubProgress(x: Float) {
        scrubProgress = progressFromX(
            x = x,
            width = containerSize.width.toFloat()
        )
        onProgressChanged()
    }

    fun cancelTrackSwitchReset() {
        trackSwitchResetSuppressedIdentity = currentPlaybackIdentity
        isTrackSwitchResetAnimating = false
        trackSwitchResetCancellationSequence += 1L
    }

    fun rememberPendingSeek(positionMs: Long) {
        if (strictProgressBar) {
            pendingSeekPositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        }
    }

    LaunchedEffect(lyricProgressSeekAnimation?.sequence, currentSongKey, durationMs) {
        val request = lyricProgressSeekAnimation ?: return@LaunchedEffect
        if (
            request.sequence == lastHandledLyricSeekSequence ||
            request.songId != currentSongKey ||
            durationMs <= 0L
        ) {
            return@LaunchedEffect
        }

        val targetPositionMs = request.targetPositionMs.coerceIn(0L, durationMs)
        val targetProgress = progressFraction(
            positionMs = targetPositionMs,
            durationMs = durationMs
        )
        val startProgress = if (isTapSeeking) {
            tapSeekProgress.value
        } else {
            lastRenderedProgress
        }.coerceIn(0f, 1f)

        cancelTrackSwitchReset()
        tapSeekJob?.cancel()
        lastHandledLyricSeekSequence = request.sequence
        isTapSeeking = true
        rememberPendingSeek(targetPositionMs)
        tapSeekProgress.snapTo(startProgress)
        tapSeekProgress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(
                durationMillis = PlaybackProgressTapSeekAnimationMillis,
                easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
            )
        )
        isTapSeeking = false
    }

    Box(modifier = modifier) {
        PlayerProgressBarContent(
            enterProgress = enterProgress
        ) {
            PlayerProgressBarTrack(
                visibleProgress = visibleProgress,
                bufferedProgress = animatedBufferedProgress.value,
                trackHeight = animatedTrackHeight,
                trackColor = animatedTrackColor,
                bufferedColor = bufferedProgressColor,
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
                cancelTrackSwitchReset()
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
                onProgressChanged()

                cancelTrackSwitchReset()
                tapSeekJob?.cancel()
                isTapSeeking = true
                rememberPendingSeek(targetPositionMs)
                onLockPlayPauseVisual(currentIsPlayingForVisualLock)
                onSeekTo(targetPositionMs)
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
private const val ProgressDiagnosticLogTag = "FlowtoneProgress"
private const val ProgressDiagnosticLogIntervalMillis = 2_000L
