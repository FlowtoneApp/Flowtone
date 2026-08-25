package ink.tenqui.flowtone.ui.library

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.PageTransitionScope

// Header、content 与页面根层分别参与现有转场和折叠布局，不能合并为单一 Modifier。
@SuppressLint("ModifierParameter")
@Composable
internal fun AlbumDetailScreen(
    albumId: Long,
    album: LocalAlbum?,
    currentSong: Song?,
    isPlaying: Boolean,
    pendingTrackIdentityKey: String? = null,
    songSort: PlaylistSongSort = PlaylistSongSort(),
    onSongClick: (List<Song>, Int) -> Unit,
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
    val vinylMotionActive = currentSong?.albumId == albumId && isPlaying
    val title = album?.title ?: "\u4e13\u8f91"
    val artist = album?.artist ?: "\u672a\u77e5\u827a\u672f\u5bb6"
    val listState = remember(albumId) { LazyListState() }
    val albumSongs = remember(albumId, album?.songs, songSort) {
        album?.songs.orEmpty()
            .map { song ->
                SelectablePlaylistSong(
                    selectionKey = "album:$albumId:${song.id}:${song.uri}",
                    song = song
                )
            }
            .sortedForPlaylist(songSort)
    }
    val songsById = remember(album?.songs) {
        album?.songs.orEmpty().associateBy { song -> song.id.toString() }
    }

    if (albumSongs.isEmpty()) {
        PlaylistDetailCollapsingHeaderScaffold(
            title = title,
            listState = null,
            showContentHeader = false,
            onCollapseProgressStateChange = onCollapseProgressStateChange,
            headerModifier = headerModifier,
            contentModifier = contentModifier,
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AlbumMetadataHeader(
                    albumId = albumId,
                    title = title,
                    artist = artist,
                    songCount = 0,
                    artworkUri = album?.artworkUri,
                    vinylMotionActive = vinylMotionActive,
                    modifier = pageTransition.elementModifier(0)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u672a\u627e\u5230\u8be5\u4e13\u8f91\u7684\u672c\u5730\u6b4c\u66f2",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    PlaylistDetailCollapsingHeaderScaffold(
        title = title,
        listState = listState,
        showContentHeader = false,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        contentModifier = contentModifier,
        modifier = modifier
    ) {
        SelectablePlaylistSongList(
            sourceKey = "album:$albumId",
            source = PlaylistSelectionSource.ReadOnly,
            playlistTitle = title,
            entries = albumSongs,
            listState = listState,
            currentSong = currentSong,
            pendingTrackIdentityKey = pendingTrackIdentityKey,
            likedSongKeys = batchActions.likedSongKeys,
            editablePlaylists = batchActions.editablePlaylists,
            clearSelectionRequest = batchActions.clearSelectionRequest,
            onSelectionModeChange = batchActions.onSelectionModeChange,
            onSelectionTopBarStateChange = batchActions.onSelectionTopBarStateChange,
            onSongClick = { tracks, index ->
                val songs = tracks.mapNotNull { track ->
                    (track as? PersistentTrack.Local)?.songId?.let(songsById::get)
                }
                if (songs.size == tracks.size && index in songs.indices) {
                    onSongClick(songs, index)
                }
            },
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
                AlbumMetadataHeader(
                    albumId = albumId,
                    title = title,
                    artist = artist,
                    songCount = albumSongs.size,
                    artworkUri = album?.artworkUri,
                    vinylMotionActive = vinylMotionActive,
                    modifier = pageTransition.elementModifier(0)
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AlbumMetadataHeader(
    albumId: Long,
    title: String,
    artist: String,
    songCount: Int,
    artworkUri: android.net.Uri?,
    vinylMotionActive: Boolean,
    modifier: Modifier = Modifier
) {
    CollectionMetadataHeader(
        title = title,
        supportingText = "$artist \u00b7 $songCount \u9996\u6b4c\u66f2",
        artwork = {
            AlbumArtwork(
                artworkUri = artworkUri,
                vinylMotionActive = vinylMotionActive,
                vinylSeed = albumId
            )
        },
        modifier = modifier
    )
}
