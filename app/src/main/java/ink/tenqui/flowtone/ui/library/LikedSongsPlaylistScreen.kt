package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.SongListItem

@Composable
fun LikedSongsPlaylistScreen(
    playlistTitle: String,
    allSongs: List<Song>,
    likedSongKeys: List<String>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val listState = remember(LikedSongsPlaylistId) { LazyListState() }
    val likedSongs = remember(allSongs, likedSongKeys) {
        allSongs.filter { song -> isSongLiked(song, likedSongKeys) }
    }

    if (likedSongs.isEmpty()) {
        PlaylistDetailCollapsingHeaderScaffold(
            title = playlistTitle,
            listState = null,
            onCollapseProgressStateChange = onCollapseProgressStateChange,
            headerModifier = headerModifier,
            contentModifier = contentModifier,
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u8fd8\u6ca1\u6709\u559c\u6b22\u7684\u97f3\u4e50",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    PlaylistDetailCollapsingHeaderScaffold(
        title = playlistTitle,
        listState = listState,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        contentModifier = contentModifier,
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = FlowtonePageHeaderExpandedTopPadding,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "playlist-detail-header") {
                PlaylistDetailHeaderListItem(
                    modifier = Modifier.padding(
                        start = FlowtonePageHeaderExpandedStartPadding,
                        end = FlowtonePageHeaderExpandedEndPadding
                    )
                )
            }
            itemsIndexed(
                items = likedSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                val firstVisibleSongIndex = (listState.firstVisibleItemIndex - 1)
                    .coerceAtLeast(0)
                val visibleAnimationIndex = (index - firstVisibleSongIndex)
                    .coerceIn(0, 10)
                SongListItem(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id,
                    onClick = {
                        onSongClick(likedSongs, index)
                    },
                    modifier = itemModifier(visibleAnimationIndex)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}
