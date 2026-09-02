package ink.tenqui.flowtone.ui.library

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import ink.tenqui.flowtone.core.model.normalizeMusicSourceHost
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.ui.components.SongListItem
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
                    vinylSeed = albumId,
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
                    vinylSeed = albumId,
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

@SuppressLint("ModifierParameter")
@Composable
internal fun ProviderAlbumDetailScreen(
    album: ProviderAlbum,
    songs: List<ProviderSong>,
    currentSong: Song?,
    isPlaying: Boolean,
    pendingTrackIdentityKey: String? = null,
    onSongClick: (List<ProviderSong>, Int) -> Unit,
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier =
        { _, _, _ -> Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val listState = remember(album.identity) { LazyListState() }
    val presentedSongs = remember(songs) { songs.map(ProviderSong::toPresentationSong) }
    val currentUri = currentSong?.uri
    val vinylMotionActive = isPlaying && presentedSongs.any { it.uri == currentUri }

    PlaylistDetailCollapsingHeaderScaffold(
        title = album.title,
        listState = listState,
        showContentHeader = false,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        modifier = modifier
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "provider-album-header:${album.identity.stableKey}") {
                AlbumMetadataHeader(
                    vinylSeed = album.identity.stableKey.hashCode().toLong(),
                    title = album.title,
                    artist = album.artist.ifBlank { "未知艺术家" },
                    songCount = presentedSongs.size.takeIf { it > 0 } ?: album.songCount ?: 0,
                    artworkUri = null,
                    extensionArtwork = album.artwork,
                    vinylMotionActive = vinylMotionActive,
                    modifier = pageTransition.elementModifier(0)
                )
            }
            if (presentedSongs.isEmpty()) {
                item(key = "provider-album-empty") {
                    Text(
                        text = "未找到该专辑的在线歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = presentedSongs,
                    key = { index, _ -> songs[index].identity.stableKey }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrentSong = currentUri == song.uri,
                        isPendingPlayback = pendingTrackIdentityKey != null &&
                            pendingTrackIdentityKey == songs[index].persistentTrackRef?.let {
                                "online:${normalizeMusicSourceHost(it.sourceHost)}:${it.persistentId}"
                            },
                        extensionArtwork = songs[index].artwork,
                        onClick = { onSongClick(songs, index) },
                        modifier = itemModifier(1f, index, presentedSongs.size)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumMetadataHeader(
    vinylSeed: Long,
    title: String,
    artist: String,
    songCount: Int,
    artworkUri: android.net.Uri?,
    extensionArtwork: ExtensionImage? = null,
    vinylMotionActive: Boolean,
    modifier: Modifier = Modifier
) {
    CollectionMetadataHeader(
        title = title,
        supportingText = "$artist \u00b7 $songCount \u9996\u6b4c\u66f2",
        artwork = {
            AlbumArtwork(
                artworkUri = artworkUri,
                extensionArtwork = extensionArtwork,
                vinylMotionActive = vinylMotionActive,
                vinylSeed = vinylSeed
            )
        },
        modifier = modifier
    )
}
