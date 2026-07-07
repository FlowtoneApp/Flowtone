package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem

@Composable
internal fun PlayerQueueItem(
    song: Song,
    isCurrentSong: Boolean,
    onClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    SongListItem(
        song = song,
        isCurrentSong = isCurrentSong,
        onClick = onClick,
        titleColor = Color.White,
        artistColor = Color.White,
        durationColor = Color.White,
        currentSongBackgroundColor = Color.Black.copy(alpha = 0.28f),
        modifier = modifier.padding(vertical = PlayerQueueItemVerticalPadding)
    )
}
