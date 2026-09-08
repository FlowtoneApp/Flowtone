package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionArtworkCard
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.SongListItemSkeleton
import ink.tenqui.flowtone.ui.components.rememberGridCardPageMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture

private val ArtistCollectionHorizontalPadding = 20.dp
private val ArtistCollectionTopGap = 12.dp
private val ArtistCollectionBottomPadding = 28.dp

@Composable
internal fun ArtistSongsPage(
    hasLocalContent: Boolean,
    localSongs: List<Song>,
    providerSongs: List<ProviderSong>,
    providerSongsLoaded: Boolean,
    orderTitle: String?,
    currentSong: Song?,
    onLocalSongClick: (List<Song>, Int) -> Unit,
    onProviderSongClick: (List<ProviderSong>, Int) -> Unit,
    onBack: () -> Unit,
    pageTransition: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val songs = remember(localSongs, providerSongs, hasLocalContent) {
        if (hasLocalContent) localSongs else providerSongs.map(ProviderSong::toPresentationSong)
    }
    val density = LocalDensity.current
    val topPadding = with(density) { WindowInsets.statusBars.getTop(this).toDp() } +
        FlowtoneTopBarContentHeight + ArtistCollectionTopGap

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topPadding, bottom = ArtistCollectionBottomPadding),
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(onBack)
    ) {
        val displayOrderTitle = orderTitle?.trim()?.takeIf(String::isNotEmpty)
        if (displayOrderTitle != null) {
            item(key = "artist-songs-order") {
                Text(
                    text = displayOrderTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = pageTransition.elementModifier(0)
                        .padding(
                            start = ArtistCollectionHorizontalPadding,
                            end = ArtistCollectionHorizontalPadding,
                            bottom = 8.dp
                        )
                )
            }
        }

        if (!hasLocalContent && !providerSongsLoaded) {
            items(ArtistLoadingSkeletonCount, key = { index -> "artist-songs-loading-$index" }) {
                index ->
                SongListItemSkeleton(
                    modifier = pageTransition.elementModifier(index + 1)
                        .padding(horizontal = 8.dp)
                )
            }
        } else if (songs.isEmpty()) {
            item(key = "artist-songs-empty") {
                ArtistCollectionEmptyText(
                    text = "没有找到该艺术家的歌曲",
                    modifier = pageTransition.elementModifier(1)
                )
            }
        } else {
            itemsIndexed(
                items = songs,
                key = { index, song ->
                    if (hasLocalContent) "local-song:${song.id}:$index"
                    else "provider-song:${providerSongs[index].identity.stableKey}"
                }
            ) { index, song ->
                SongListItem(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                    onClick = {
                        if (hasLocalContent) onLocalSongClick(localSongs, index)
                        else onProviderSongClick(providerSongs, index)
                    },
                    extensionArtwork = providerSongs.getOrNull(index)?.artwork
                        .takeIf { !hasLocalContent },
                    modifier = pageTransition.elementModifier(index + 1)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun ArtistAlbumsPage(
    entryKey: String,
    artistCloudColor: Color,
    hasLocalContent: Boolean,
    localAlbums: List<LocalAlbum>,
    providerAlbums: List<ProviderAlbum>,
    onOpenAlbum: (Long) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit,
    onBack: () -> Unit,
    pageTransition: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val gridMotion = rememberGridCardPageMotion(
        sessionKey = entryKey,
        gridState = gridState,
        pageTransition = pageTransition
    )
    val density = LocalDensity.current
    val topPadding = with(density) { WindowInsets.statusBars.getTop(this).toDp() } +
        FlowtoneTopBarContentHeight + ArtistCollectionTopGap

    Box(
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(onBack)
    ) {
        ArtistCloudBackground(accentColor = artistCloudColor)
        if (localAlbums.isEmpty() && providerAlbums.isEmpty()) {
            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
            ) {
                ArtistCollectionEmptyText(
                    text = "没有找到该艺术家的专辑",
                    modifier = pageTransition.elementModifier(0)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                state = gridState,
                contentPadding = PaddingValues(
                    start = ArtistCollectionHorizontalPadding,
                    top = topPadding,
                    end = ArtistCollectionHorizontalPadding,
                    bottom = ArtistCollectionBottomPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (hasLocalContent) {
                    itemsIndexed(localAlbums, key = { _, album -> album.id }) { _, album ->
                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = gridMotion.itemModifier(album.id).fillMaxWidth()
                        ) {
                            FlowtoneCollectionArtworkCard(
                                title = album.title,
                                subtitle = "${album.songs.size} 首歌曲",
                                artworkUri = album.artworkUri,
                                onClick = { onOpenAlbum(album.id) },
                                titleMaxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        providerAlbums,
                        key = { _, album -> album.identity.stableKey }
                    ) { _, album ->
                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = gridMotion.itemModifier(
                                album.identity.stableKey
                            ).fillMaxWidth()
                        ) {
                            FlowtoneCollectionArtworkCard(
                                title = album.title,
                                subtitle = album.songCount?.let { "$it 首歌曲" }
                                    ?: album.artist.ifBlank { "未知艺术家" },
                                extensionArtwork = album.artwork,
                                onClick = { onOpenProviderAlbum(album) },
                                titleMaxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCollectionEmptyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ArtistCollectionHorizontalPadding, vertical = 18.dp)
    )
}
