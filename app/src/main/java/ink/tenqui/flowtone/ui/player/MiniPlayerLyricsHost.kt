package ink.tenqui.flowtone.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import ink.tenqui.flowtone.ui.player.lyrics.LyricsContent
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MiniPlayerLyricsHost(
    currentSong: Song?,
    lyricsState: LyricsState,
    confirmedPlaybackPosition: StateFlow<PlaybackPositionSnapshot>,
    activeLineTargetY: Dp,
    visibilityProgress: Float,
    onChooseLyricsDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null || visibilityProgress <= 0.001f) {
        return
    }
    val playbackPosition by confirmedPlaybackPosition.collectAsState()
    val playbackPositionMs = playbackPositionForSong(
        songId = currentSong.id,
        playbackPosition = playbackPosition
    )

    Box(modifier = modifier.clipToBounds()) {
        LyricsContent(
            state = lyricsState,
            confirmedPlaybackPositionMs = playbackPositionMs,
            activeLineTargetY = activeLineTargetY,
            visibilityProgress = visibilityProgress,
            onChooseLyricsDirectory = onChooseLyricsDirectory,
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal fun playbackPositionForSong(
    songId: Long,
    playbackPosition: PlaybackPositionSnapshot
): Long? = playbackPosition.positionMs.takeIf {
    playbackPosition.mediaId == songId.toString()
}
