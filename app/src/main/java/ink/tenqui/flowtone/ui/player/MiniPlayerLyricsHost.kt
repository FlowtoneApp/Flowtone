package ink.tenqui.flowtone.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.player.lyrics.LyricsPlaceholderContent

@Composable
internal fun MiniPlayerLyricsHost(
    currentSong: Song?,
    positionMs: Long,
    playbackProgress: Float,
    fullscreenProgress: Float,
    fullscreen: Boolean,
    visibilityProgress: Float,
    onLyricSeekRequested: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null || visibilityProgress <= 0.001f) {
        return
    }

    Box(modifier = modifier) {
        LyricsPlaceholderContent(
            visibilityProgress = visibilityProgress,
            modifier = Modifier.fillMaxSize()
        )
    }
}
