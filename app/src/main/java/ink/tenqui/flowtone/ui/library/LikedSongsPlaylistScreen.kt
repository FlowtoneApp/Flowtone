package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem

@Composable
fun LikedSongsPlaylistScreen(
    allSongs: List<Song>,
    likedSongKeys: List<String>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val likedSongs = remember(allSongs, likedSongKeys) {
        val likedKeys = likedSongKeys.toSet()
        allSongs.filter { song -> song.id.toString() in likedKeys }
    }

    if (likedSongs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u8fd8\u6ca1\u6709\u559c\u6b22\u7684\u97f3\u4e50",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = likedSongs,
            key = { _, song -> song.id }
        ) { index, song ->
            val visibleAnimationIndex = (
                index - listState.firstVisibleItemIndex
                ).coerceIn(0, 10)
            SongListItem(
                song = song,
                isCurrentSong = currentSong?.id == song.id,
                onClick = {
                    onSongClick(likedSongs, index)
                },
                modifier = itemModifier(visibleAnimationIndex)
            )
        }
    }
}
