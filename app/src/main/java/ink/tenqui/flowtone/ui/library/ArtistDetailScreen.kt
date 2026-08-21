package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.player.localSongsForArtist

@Composable
fun ArtistDetailScreen(
    artistName: String?,
    allSongs: List<Song>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    itemModifier: (order: Int, orderCount: Int) -> Modifier = { _, _ -> Modifier },
    modifier: Modifier = Modifier
) {
    val displayArtist = artistName?.trim().orEmpty()
    val listState = rememberLazyListState()
    val artistSongs = remember(displayArtist, allSongs) {
        localSongsForArtist(allSongs, displayArtist)
    }
    val viewportSongIndices by remember(listState, artistSongs) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .map { it.index - 1 }
                .filter { it in artistSongs.indices }
                .distinct()
                .sorted()
        }
    }

    if (displayArtist.isBlank()) {
        ArtistDetailEmptyState(
            title = "\u672a\u9009\u62e9\u827a\u672f\u5bb6",
            modifier = modifier
        )
        return
    }

    if (artistSongs.isEmpty()) {
        ArtistDetailEmptyState(
            title = "\u6ca1\u6709\u627e\u5230\u8be5\u827a\u672f\u5bb6\u7684\u672c\u5730\u6b4c\u66f2",
            subtitle = displayArtist,
            modifier = modifier
        )
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
        item(key = "artist-title") {
            Column(
                modifier = itemModifier(0, 1)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artistSongs.size} \u9996\u672c\u5730\u6b4c\u66f2",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        itemsIndexed(
            items = artistSongs,
            key = { _, song -> song.id }
        ) { index, song ->
            val viewportOrder = viewportSongIndices.indexOf(index).coerceAtLeast(0)
            val viewportOrderCount = viewportSongIndices.size.coerceAtLeast(1)
            SongListItem(
                song = song,
                isCurrentSong = currentSong?.id == song.id,
                onClick = {
                    onSongClick(artistSongs, index)
                },
                modifier = itemModifier(viewportOrder, viewportOrderCount)
            )
        }
    }
}

@Composable
private fun ArtistDetailEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
