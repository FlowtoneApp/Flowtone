package ink.tenqui.flowtone.viewmodel

import android.os.SystemClock
import android.util.Log
import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/** 只轮询 Controller 的确认状态，不持有播放器或队列。 */
internal class PlaybackProgressMonitor(
    private val scope: CoroutineScope,
    private val playbackController: PlaybackController,
    private val playbackSettingsStore: PlaybackSettingsStore
) {
    private val playbackState = playbackController.playbackState
    private val _confirmedPlaybackPosition = MutableStateFlow(PlaybackPositionSnapshot())
    val confirmedPosition: StateFlow<PlaybackPositionSnapshot> = _confirmedPlaybackPosition.asStateFlow()
    private var lastProgressDiagnosticLogUptimeMs = 0L
    private val jobs = mutableListOf<Job>()

    fun start() {
        startProgressTicker()
        startPlaybackOrderTicker()
        startConfirmedPlaybackPositionTicker()
    }

    private fun startProgressTicker() {
        jobs += scope.launch {
            while (isActive) {
                updateProgressFromController()
                delay(32L)
            }
        }
    }

    private fun startPlaybackOrderTicker() {
        jobs += scope.launch {
            while (isActive) {
                syncPlaybackOrderModeFromController()
                delay(500L)
            }
        }
    }

    private fun startConfirmedPlaybackPositionTicker() {
        jobs += scope.launch {
            while (isActive) {
                _confirmedPlaybackPosition.value =
                    playbackController.getCurrentPositionSnapshot()
                delay(100)
            }
        }
    }

    private fun updateProgressFromController() {
        val currentSong = playbackState.value.currentSong
        if (currentSong == null) {
            playbackController.updateProgress(
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = 0L
            )
            return
        }

        val controllerSnapshot = playbackController.getProgressSnapshot()
        val controllerDuration = controllerSnapshot.durationMs
            .takeIf { controllerSnapshot.belongsToCurrentLogicalTarget }
            ?: 0L
        val duration = when {
            controllerDuration > 0L -> controllerDuration
            currentSong.durationMs > 0L -> currentSong.durationMs
            else -> 0L
        }
        val position = if (duration > 0L && controllerSnapshot.belongsToCurrentLogicalTarget) {
            controllerSnapshot.positionMs.coerceIn(0L, duration)
        } else {
            0L
        }
        val bufferedPosition = if (duration > 0L && controllerSnapshot.belongsToCurrentLogicalTarget) {
            controllerSnapshot.bufferedPositionMs.coerceIn(0L, duration)
        } else {
            0L
        }

        playbackController.updateProgress(
            positionMs = position,
            bufferedPositionMs = bufferedPosition,
            durationMs = duration
        )
        val now = SystemClock.elapsedRealtime()
        if (
            lastProgressDiagnosticLogUptimeMs == 0L ||
            now - lastProgressDiagnosticLogUptimeMs >= ProgressDiagnosticLogIntervalMillis
        ) {
            lastProgressDiagnosticLogUptimeMs = now
            val state = playbackState.value
            Log.d(
                ProgressDiagnosticLogTag,
                "ticker controllerMediaId=${controllerSnapshot.mediaId} " +
                    "controllerPositionMs=${controllerSnapshot.positionMs} " +
                    "controllerBufferedPositionMs=${controllerSnapshot.bufferedPositionMs} " +
                    "controllerDurationMs=${controllerSnapshot.durationMs} " +
                    "belongsToCurrent=${controllerSnapshot.belongsToCurrentLogicalTarget} " +
                    "stateQueueId=${state.currentQueueId} statePositionMs=${state.positionMs} " +
                    "stateBufferedPositionMs=${state.bufferedPositionMs} " +
                    "stateDurationMs=${state.durationMs}"
            )
        }
    }

    private fun syncPlaybackOrderModeFromController() {
        val playbackOrderMode = playbackController.getPlaybackOrderMode()
        if (playbackState.value.playbackOrderMode != playbackOrderMode) {
            playbackController.updatePlaybackOrderMode(playbackOrderMode)
            playbackSettingsStore.setPlaybackOrderMode(playbackOrderMode)
        }
    }

    fun close() {
        jobs.forEach(Job::cancel)
        jobs.clear()
    }
}

private const val ProgressDiagnosticLogTag = "FlowtoneProgress"
private const val ProgressDiagnosticLogIntervalMillis = 2_000L
