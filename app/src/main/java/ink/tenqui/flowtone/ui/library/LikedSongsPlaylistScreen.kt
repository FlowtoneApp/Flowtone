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
internal fun LikedSongsPlaylistScreen(
    playlistTitle: String,
    allSongs: List<Song>,
    likedSongKeys: List<String>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    batchActions: PlaylistBatchActions = PlaylistBatchActions(),
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
            showContentHeader = false,
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
        showContentHeader = false,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        contentModifier = contentModifier,
        modifier = modifier
    ) {
        SelectablePlaylistSongList(
            sourceKey = LikedSongsPlaylistId,
            source = PlaylistSelectionSource.LikedSongs,
            playlistTitle = playlistTitle,
            entries = likedSongs.map { song ->
                SelectablePlaylistSong(
                    selectionKey = "liked:${song.id}:${song.uri}",
                    song = song
                )
            },
            listState = listState,
            currentSong = currentSong,
            likedSongKeys = batchActions.likedSongKeys,
            editablePlaylists = batchActions.editablePlaylists,
            clearSelectionRequest = batchActions.clearSelectionRequest,
            onSelectionModeChange = batchActions.onSelectionModeChange,
            onSelectionTopBarStateChange = batchActions.onSelectionTopBarStateChange,
            onSongClick = onSongClick,
            onAddSongsNext = batchActions.onAddSongsNext,
            onAppendSongsToQueue = batchActions.onAppendSongsToQueue,
            onAddSongsToPlaylists = batchActions.onAddSongsToPlaylists,
            onSetSongsLiked = batchActions.onSetSongsLiked,
            onDeleteSongs = batchActions.onDeleteSongs,
            onRemoveEntries = { _, done -> done(false) },
            itemModifier = itemModifier,
            modifier = Modifier.fillMaxSize()
        )
    }
}
