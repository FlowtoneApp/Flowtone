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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.data.online.ProviderPlaylist
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
    onOpenProviderPlaylist: (ProviderPlaylist) -> Unit,
    onRetryProviderPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        userScrollEnabled = editingPlaylistId == null ||
            sourceContent.selectedSource.identity != LibrarySourceIdentity.Local,
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
        verticalArrangement = Arrangement.spacedBy(
            if (sourceContent.selectedSource.identity == LibrarySourceIdentity.Local) 12.dp else 8.dp
        )
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
        item(key = "library-source-header") {
            PageTransitionElement(
                scope = pageScope,
                order = 1,
                orderCount = 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        if (sourceContent.selectedSource.identity == LibrarySourceIdentity.Local) {
                            32.dp
                        } else {
                            24.dp
                        }
                    )
                ) {
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
                    if (sourceContent.selectedSource.identity == LibrarySourceIdentity.Local) {
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
        }

        if (sourceContent.selectedSource.identity != LibrarySourceIdentity.Local) {
            val provider = sourceContent.selectedSource
            val providerPlaylists = sourceContent.providerPlaylists.orEmpty()

            if (!provider.supportsPlaylists) {
                item(key = "library-provider-playlists-unavailable") {
                    LibraryProviderCatalogMessage(
                        message = "此扩展来源不支持用户歌单",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            } else {
                item(key = "library-provider-playlists-header") {
                    Text(
                        text = "歌单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                when (sourceContent.providerPlaylistLoadState) {
                    ProviderCatalogLoadState.Loading, null -> item(key = "library-provider-playlists-loading") {
                        LibraryProviderCatalogLoading(message = "正在读取歌单")
                    }

                    ProviderCatalogLoadState.Failed -> item(key = "library-provider-playlists-failed") {
                        LibraryProviderCatalogFailure(
                            message = "暂时无法读取此来源的歌单",
                            onRetry = onRetryProviderPlaylists
                        )
                    }

                    ProviderCatalogLoadState.Loaded -> {
                        if (providerPlaylists.isEmpty()) {
                            item(key = "library-provider-playlists-empty") {
                                LibraryProviderCatalogMessage(
                                    message = "此来源暂无歌单",
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = providerPlaylists,
                                key = { _, playlist -> "provider-playlist:${playlist.identity.stableKey}" }
                            ) { _, playlist ->
                                LibraryProviderPlaylistListItem(
                                    playlist = playlist,
                                    itemHeight = playlistItemHeight,
                                    flowCloudSpeed = flowCloudSpeed,
                                    isFlowCloudPlaying = isFlowCloudPlaying,
                                    onClick = { onOpenProviderPlaylist(playlist) },
                                    modifier = pageScope.elementModifier(1, 2)
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
