package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.toPresentationSong
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.PageTransitionScope

@Composable
internal fun LikedSongsPlaylistScreen(
    metadata: PlaylistDetailMetadata,
    allSongs: List<Song>,
    likedTracks: List<PersistentTrack>,
    currentSong: Song?,
    pendingTrackIdentityKey: String? = null,
    songSort: PlaylistSongSort = PlaylistSongSort(),
    onSongClick: (List<PersistentTrack>, Int) -> Unit,
    playbackErrorMessage: String? = null,
    playbackErrorEventId: Long = 0L,
    batchActions: PlaylistBatchActions = PlaylistBatchActions(),
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier =
        { _, _, _ -> Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val listState = remember(LikedSongsPlaylistId) { LazyListState() }
    val likedSongs = remember(allSongs, likedTracks) {
        likedTracks.mapNotNull { track ->
            track.toPresentationSong(allSongs)?.let { song -> track to song }
        }
    }
    val artworkUri = remember(metadata.customArtworkUri, likedSongs) {
        metadata.customArtworkUri ?: likedSongs
            .asSequence()
            .mapNotNull { (_, song) -> song.artworkUri }
            .firstOrNull()
    }

    if (likedSongs.isEmpty()) {
        PlaylistDetailCollapsingHeaderScaffold(
            title = metadata.title,
            listState = null,
            showContentHeader = false,
            onCollapseProgressStateChange = onCollapseProgressStateChange,
            headerModifier = headerModifier,
            contentModifier = contentModifier,
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlaylistMetadataHeader(
                    metadata = metadata,
                    artworkUri = artworkUri,
                    modifier = pageTransition.elementModifier(0)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
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
        }
        return
    }

    PlaylistDetailCollapsingHeaderScaffold(
        title = metadata.title,
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
            playlistTitle = metadata.title,
            entries = likedSongs.map { (track, song) ->
                SelectablePlaylistSong(
                    selectionKey = "liked:${track.identityKey}",
                    song = song,
                    track = track
                )
            }.sortedForPlaylist(songSort),
            listState = listState,
            currentSong = currentSong,
            pendingTrackIdentityKey = pendingTrackIdentityKey,
            likedSongKeys = batchActions.likedSongKeys,
            editablePlaylists = batchActions.editablePlaylists,
            clearSelectionRequest = batchActions.clearSelectionRequest,
            onSelectionModeChange = batchActions.onSelectionModeChange,
            onSelectionTopBarStateChange = batchActions.onSelectionTopBarStateChange,
            onSongClick = onSongClick,
            externalErrorMessage = playbackErrorMessage,
            externalErrorEventId = playbackErrorEventId,
            onAddSongsNext = batchActions.onAddSongsNext,
            onAppendSongsToQueue = batchActions.onAppendSongsToQueue,
            onAddSongsToPlaylists = batchActions.onAddSongsToPlaylists,
            onSetSongsLiked = batchActions.onSetSongsLiked,
            onDeleteSongs = batchActions.onDeleteSongs,
            onRemoveEntries = { _, done -> done(false) },
            reorderAnimationKey = songSort,
            pageTransition = pageTransition,
            itemModifier = itemModifier,
            headerContent = {
                PlaylistMetadataHeader(
                    metadata = metadata,
                    artworkUri = artworkUri,
                    modifier = pageTransition.elementModifier(0)
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
