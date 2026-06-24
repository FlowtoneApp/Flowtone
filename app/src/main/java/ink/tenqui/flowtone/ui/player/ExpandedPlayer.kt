package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun ExpandedOnlyContent(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isPlayingForVisualLock: Boolean,
    currentSongKey: Long?,
    hasCurrentSong: Boolean,
    progressTrackColor: Color,
    progressColor: Color,
    onSeekTo: (Long) -> Unit,
    onLockPlayPauseVisual: (Boolean) -> Unit,
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
            isPlayingForVisualLock = isPlayingForVisualLock,
            currentSongKey = currentSongKey,
            enabled = hasCurrentSong && durationMs > 0L,
            trackColor = progressTrackColor,
            progressColor = progressColor,
            onSeekTo = onSeekTo,
            onLockPlayPauseVisual = onLockPlayPauseVisual,
            onScrubbingChange = onScrubbingChange,
            enterProgress = progressEnterProgress,
            modifier = Modifier
                .fillMaxWidth(0.76f)
        )
    }
}

