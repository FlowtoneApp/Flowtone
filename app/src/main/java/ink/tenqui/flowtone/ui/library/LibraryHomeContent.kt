package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionArtworkCard
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.viewmodel.ProviderCatalogLoadState
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderPlaceholder
import ink.tenqui.flowtone.ui.components.PageTransitionElement
import ink.tenqui.flowtone.ui.components.PageTransitionScope

@Composable
internal fun LibraryHomeContent(
    songCount: Int,
    pageScope: PageTransitionScope,
    likedPlaylist: LibraryPlaylistCard,
    playlists: List<LibraryPlaylistCard>,
    playlistItemHeight: Dp,
    libraryCardsProgress: Float,
    playlistItemOffsetYPx: Float,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    listState: LazyListState,
    editingPlaylistId: String?,
    newlyCreatedPlaylistId: String?,
    exitingPlaylistId: String?,
    onOpenLocalLibrary: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onDeleteAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onStartPlaylistEditing: (LibraryPlaylistCard) -> Unit,
    onEditingPlaylistBoundsChanged: (String, Rect) -> Unit,
    onEditingPlaylistBoundsRemoved: (String) -> Unit,
    onLibraryViewportBoundsChanged: (Rect) -> Unit,
    sourceContent: LibrarySourceContentState,
    sourceExpandProgress: Float,
    sourceCardContentVisible: Boolean,
    sourceIconPainters: Map<String, Painter>,
    onSourceIconPainterLoaded: (String, Painter) -> Unit,
    onSourceHeaderClick: () -> Unit,
    onSourceSwipeToIndex: (Int) -> Unit,
    onSourceCardBoundsChanged: (Rect) -> Unit,
    onSourceCardContentBoundsChanged: (Rect) -> Unit,
    onProviderSongClick: (List<ProviderSong>, Int, PlaybackSource) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit,
    onRetryProviderSongs: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        userScrollEnabled = editingPlaylistId == null,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionInRoot()
                onLibraryViewportBoundsChanged(
                    Rect(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = topLeft.x + coordinates.size.width,
                        bottom = topLeft.y + coordinates.size.height
                    )
                )
            },
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 48.dp,
            end = 20.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "library-header") {
            PageTransitionElement(
                scope = pageScope,
                order = 0,
                orderCount = 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    FlowtonePageHeaderPlaceholder(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
        if (sourceContent.selectedSource.identity == LibrarySourceIdentity.Local) {
            item(key = "library-collection-menu") {
                PageTransitionElement(
                    scope = pageScope,
                    order = 1,
                    orderCount = 2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        LibraryProviderHeader(
                            source = sourceContent.selectedSource,
                            sourceIndex = 0,
                            sourceCount = sourceContent.sourceItems.size,
                            canExpand = true,
                            swipeEnabled = sourceContent.swipeEnabled,
                            expanded = sourceContent.switcherState !is LibrarySourceSwitcherState.Collapsed,
                            swipeDirection = sourceContent.swipeDirection,
                            expandProgress = sourceExpandProgress,
                            contentVisible = sourceCardContentVisible,
                            sourceIconPainters = sourceIconPainters,
                            onSourceIconPainterLoaded = onSourceIconPainterLoaded,
                            onClick = onSourceHeaderClick,
                            onSwipeToIndex = onSourceSwipeToIndex,
                            onBoundsChanged = onSourceCardBoundsChanged,
                            onContentBoundsChanged = onSourceCardContentBoundsChanged,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LibraryCollectionMenu(
                            songCount = songCount,
                            likedPlaylist = likedPlaylist,
                            playlists = playlists,
                            playlistItemHeight = playlistItemHeight,
                            libraryCardsProgress = libraryCardsProgress,
                            playlistItemOffsetYPx = playlistItemOffsetYPx,
                            flowCloudSpeed = flowCloudSpeed,
                            isFlowCloudPlaying = isFlowCloudPlaying,
                            editingPlaylistId = editingPlaylistId,
                            newlyCreatedPlaylistId = newlyCreatedPlaylistId,
                            exitingPlaylistId = exitingPlaylistId,
                            onCreateAnimationFinished = onCreateAnimationFinished,
                            onDeleteAnimationFinished = onDeleteAnimationFinished,
                            onOpenLocalLibrary = onOpenLocalLibrary,
                            onCreatePlaylist = onCreatePlaylist,
                            onOpenPlaylist = onOpenPlaylist,
                            onStartPlaylistEditing = onStartPlaylistEditing,
                            onEditingPlaylistBoundsChanged = onEditingPlaylistBoundsChanged,
                            onEditingPlaylistBoundsRemoved = onEditingPlaylistBoundsRemoved,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            item(key = "library-provider-header") {
                PageTransitionElement(
                    scope = pageScope,
                    order = 1,
                    orderCount = 2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        LibraryProviderHeader(
                            source = sourceContent.selectedSource,
                            sourceIndex = sourceContent.sourceItems.indexOfFirst {
                                it.identity == sourceContent.selectedSource.identity
                            }.coerceAtLeast(0),
                            sourceCount = sourceContent.sourceItems.size,
                            canExpand = true,
                            swipeEnabled = sourceContent.swipeEnabled,
                            expanded = sourceContent.switcherState !is LibrarySourceSwitcherState.Collapsed,
                            swipeDirection = sourceContent.swipeDirection,
                            expandProgress = sourceExpandProgress,
                            contentVisible = sourceCardContentVisible,
                            sourceIconPainters = sourceIconPainters,
                            onSourceIconPainterLoaded = onSourceIconPainterLoaded,
                            onClick = onSourceHeaderClick,
                            onSwipeToIndex = onSourceSwipeToIndex,
                            onBoundsChanged = onSourceCardBoundsChanged,
                            onContentBoundsChanged = onSourceCardContentBoundsChanged,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val provider = sourceContent.selectedSource
            val providerId = (provider.identity as? LibrarySourceIdentity.Extension)?.extensionId
            val providerSongs = sourceContent.providerSongs.orEmpty()
            val providerAlbums = sourceContent.providerAlbums.orEmpty()

            if (!provider.supportsSongs && !provider.supportsAlbums) {
                item(key = "library-provider-unavailable") {
                    LibraryProviderCatalogMessage(
                        message = "此扩展来源当前不支持曲库目录浏览",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }

            if (provider.supportsSongs) {
                item(key = "library-provider-songs-header") {
                    Text(
                        text = "歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
                when (sourceContent.providerSongLoadState) {
                    ProviderCatalogLoadState.Loading, null -> item(key = "library-provider-songs-loading") {
                        LibraryProviderCatalogLoading(message = "正在读取歌曲目录")
                    }

                    ProviderCatalogLoadState.Failed -> item(key = "library-provider-songs-failed") {
                        LibraryProviderCatalogFailure(
                            message = "暂时无法读取此来源的歌曲目录",
                            onRetry = onRetryProviderSongs
                        )
                    }

                    ProviderCatalogLoadState.Loaded -> {
                        if (providerSongs.isEmpty()) {
                            item(key = "library-provider-songs-empty") {
                                LibraryProviderCatalogMessage(
                                    message = "此来源暂无可展示的歌曲",
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = providerSongs,
                                key = { _, song -> "provider-song:${song.identity.stableKey}" }
                            ) { index, providerSong ->
                                val song = providerSong.toPresentationSong()
                                SongListItem(
                                    song = song,
                                    isCurrentSong = sourceContent.currentSong?.uri == song.uri,
                                    onClick = {
                                        onProviderSongClick(
                                            providerSongs,
                                            index,
                                            PlaybackSource.providerCatalog(
                                                providerId = requireNotNull(providerId),
                                                displayName = provider.name
                                            )
                                        )
                                    },
                                    extensionArtwork = providerSong.artwork,
                                    modifier = pageScope.elementModifier(1, 2)
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (provider.supportsAlbums) {
                item(key = "library-provider-albums-header") {
                    Text(
                        text = "专辑",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 20.dp)
                    )
                }
                when (sourceContent.providerAlbumLoadState) {
                    ProviderCatalogLoadState.Loading, null -> item(key = "library-provider-albums-loading") {
                        LibraryProviderCatalogLoading(message = "正在读取专辑目录")
                    }

                    ProviderCatalogLoadState.Failed -> item(key = "library-provider-albums-failed") {
                        LibraryProviderCatalogFailure(
                            message = "暂时无法读取此来源的专辑目录",
                            onRetry = onRetryProviderSongs
                        )
                    }

                    ProviderCatalogLoadState.Loaded -> {
                        if (providerAlbums.isEmpty()) {
                            item(key = "library-provider-albums-empty") {
                                LibraryProviderCatalogMessage(
                                    message = "此来源暂无可展示的专辑",
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = providerAlbums,
                                key = { _, album -> "provider-album:${album.identity.stableKey}" }
                            ) { _, album ->
                                FlowtoneCollectionArtworkCard(
                                    title = album.title,
                                    subtitle = album.songCount?.let { "$it 首歌曲" }
                                        ?: album.artist.ifBlank { "未知艺术家" },
                                    extensionArtwork = album.artwork,
                                    onClick = { onOpenProviderAlbum(album) },
                                    titleMaxLines = 2,
                                    modifier = pageScope.elementModifier(1, 2)
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryProviderCatalogLoading(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryProviderCatalogFailure(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        LibraryProviderCatalogMessage(
            message = message,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun LibraryProviderCatalogMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}
