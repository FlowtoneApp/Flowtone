package ink.tenqui.flowtone.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.ui.player.lyrics.LyricsContent

@Composable
internal fun MiniPlayerLyricsHost(
    currentSong: Song?,
    lyricsState: LyricsState,
    visibilityProgress: Float,
    onChooseLyricsDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null || visibilityProgress <= 0.001f) {
        return
    }

    Box(modifier = modifier.clipToBounds()) {
        LyricsContent(
            state = lyricsState,
            visibilityProgress = visibilityProgress,
            onChooseLyricsDirectory = onChooseLyricsDirectory,
            modifier = Modifier.fillMaxSize()
        )
    }
}
