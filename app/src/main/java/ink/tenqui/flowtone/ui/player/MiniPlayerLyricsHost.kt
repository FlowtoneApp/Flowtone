package ink.tenqui.flowtone.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.Song

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun MiniPlayerLyricsHost(
    currentSong: Song?,
    positionMs: Long,
    playbackProgress: Float,
    fullscreenProgress: Float,
    fullscreen: Boolean,
    interactionsEnabled: Boolean,
    onLyricSeekRequested: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
}
