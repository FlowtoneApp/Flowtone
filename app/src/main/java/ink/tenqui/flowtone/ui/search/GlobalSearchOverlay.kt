package ink.tenqui.flowtone.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.SearchArtist
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.search.searchPlaybackQueueStartIndex
import ink.tenqui.flowtone.ui.components.ArtistListItem
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageProgressElement
import coil3.compose.AsyncImage

@Composable
internal fun GlobalSearchOverlay(
    searchUiState: GlobalSearchUiState,
    currentSong: Song?,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    pendingTrackIdentityKey: String? = null,
    onArtistClick: (SearchArtist) -> Unit,
    onExitSearch: () -> Unit,
    interactionsEnabled: Boolean,
    reentryProgress: Float,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val contentProgress = reentryProgress.coerceIn(0f, 1f)
    val backGestureModifier = if (interactionsEnabled) {
        Modifier.rightSwipeBackGesture(onExitSearch)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backGestureModifier)
            .clickable(
                enabled = interactionsEnabled,
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                }
            )
    ) {
        when {
            searchUiState.isEmptyQuery -> SearchMessage(
                text = "\u641c\u7d22\u672c\u5730\u6b4c\u66f2\u6216\u827a\u672f\u5bb6",
                modifier = Modifier
                    .align(Alignment.Center)
                    .staggeredPageProgressElement(0, contentProgress)
            )

            searchUiState.hasNoResults -> SearchMessage(
                text = "\u6ca1\u6709\u627e\u5230\u76f8\u5173\u6b4c\u66f2\u6216\u827a\u672f\u5bb6",
                modifier = Modifier
                    .align(Alignment.Center)
                    .staggeredPageProgressElement(0, contentProgress)
            )

            else -> SearchResultList(
                searchUiState = searchUiState,
                currentSong = currentSong,
                listState = listState,
                onSongClick = onSongClick,
                onOnlineSongClick = onOnlineSongClick,
                pendingTrackIdentityKey = pendingTrackIdentityKey,
                onArtistClick = { artist ->
                    if (interactionsEnabled) {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        onArtistClick(artist)
                    }
                },
                interactionsEnabled = interactionsEnabled,
                reentryProgress = contentProgress,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SearchResultList(
    searchUiState: GlobalSearchUiState,
    currentSong: Song?,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    pendingTrackIdentityKey: String?,
    onArtistClick: (SearchArtist) -> Unit,
    interactionsEnabled: Boolean,
    reentryProgress: Float,
    modifier: Modifier = Modifier
) {
    var nextItemIndex = 0
    LazyColumn(
        state = listState,
        userScrollEnabled = interactionsEnabled,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (searchUiState.isSearching) {
            val itemIndex = nextItemIndex
            nextItemIndex += 1
            item(key = "searching") {
                SearchInlineLoading(
                    modifier = Modifier
                        .staggeredPageProgressElement(
                            visibleSearchAnimationIndex(
                                itemIndex = itemIndex,
                                listState = listState
                            ),
                            reentryProgress
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }

        if (searchUiState.songResults.isNotEmpty()) {
            val headerIndex = nextItemIndex
            nextItemIndex += 1
            item(key = "songs-header") {
                SearchSectionHeader(
                    title = "\u6b4c\u66f2",
                    modifier = Modifier
                        .staggeredPageProgressElement(
                            visibleSearchAnimationIndex(
                                itemIndex = headerIndex,
                                listState = listState
                            ),
                            reentryProgress
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            val firstSongIndex = nextItemIndex
            nextItemIndex += searchUiState.songResults.size
            itemsIndexed(
                items = searchUiState.songResults,
                key = { index, song -> "song:${song.id}:${song.uri}:$index" }
            ) { index, song ->
                val itemIndex = firstSongIndex + index
                SongListItem(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                    onClick = {
                        if (interactionsEnabled) {
                            val startIndex = searchPlaybackQueueStartIndex(
                                songResults = searchUiState.songResults,
                                clickedSong = song
                            ).takeIf { it != -1 } ?: index
                            onSongClick(searchUiState.songResults, startIndex)
                        }
                    },
                    modifier = Modifier.staggeredPageProgressElement(
                        visibleSearchAnimationIndex(
                            itemIndex = itemIndex,
                            listState = listState
                        ),
                        reentryProgress
                    )
                )
            }
        }

        if (searchUiState.artistResults.isNotEmpty()) {
            val headerIndex = nextItemIndex
            nextItemIndex += 1
            item(key = "artists-header") {
                SearchSectionHeader(
                    title = "\u827a\u672f\u5bb6",
                    modifier = Modifier
                        .staggeredPageProgressElement(
                            visibleSearchAnimationIndex(
                                itemIndex = headerIndex,
                                listState = listState
                            ),
                            reentryProgress
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            val firstArtistIndex = nextItemIndex
            itemsIndexed(
                items = searchUiState.artistResults,
                key = { _, artist -> "artist:${artist.id}" }
            ) { index, artist ->
                val itemIndex = firstArtistIndex + index
                ArtistListItem(
                    artist = artist,
                    onClick = onArtistClick,
                    modifier = Modifier.staggeredPageProgressElement(
                        visibleSearchAnimationIndex(
                            itemIndex = itemIndex,
                            listState = listState
                        ),
                        reentryProgress
                    )
                )
            }
        }

        if (searchUiState.onlineSongResults.isNotEmpty()) {
            val headerIndex = nextItemIndex
            nextItemIndex += 1
            item(key = "online-sources-header") {
                SearchSectionHeader(
                    title = "在线来源",
                    modifier = Modifier
                        .staggeredPageProgressElement(
                            visibleSearchAnimationIndex(headerIndex, listState),
                            reentryProgress
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            val firstOnlineIndex = nextItemIndex
            nextItemIndex += searchUiState.onlineSongResults.size
            itemsIndexed(
                items = searchUiState.onlineSongResults,
                key = { _, song -> "online:${song.trackRef.extensionId}:${song.id}" }
            ) { index, song ->
                OnlineSongListItem(
                    song = song,
                    isPending = song.persistentTrackRef?.let { ref ->
                        PersistentTrack.Online(ref.sourceHost, ref.persistentId, "", "").identityKey == pendingTrackIdentityKey
                    } == true,
                    onClick = { if (interactionsEnabled) onOnlineSongClick(song) },
                    modifier = Modifier.staggeredPageProgressElement(
                        visibleSearchAnimationIndex(firstOnlineIndex + index, listState),
                        reentryProgress
                    )
                )
            }
        }
    }
}

@Composable
internal fun OnlineSongListItem(
    song: ProviderSong,
    isPending: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    var artworkLoaded by remember(song.artwork) { mutableStateOf(false) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (artworkLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "OnlineSearchArtworkAlpha"
    )
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("online-song:${song.trackRef.extensionId}:${song.trackRef.opaqueId}")
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
        ) {
            if (song.artwork != null) {
                AsyncImage(
                    model = song.artwork,
                    imageLoader = extensionManager.extensionImageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { artworkLoaded = true },
                    onError = { artworkLoaded = false },
                    modifier = Modifier.alpha(artworkAlpha)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isPending) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SearchInlineLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
private fun SearchMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 28.dp)
    )
}

private fun visibleSearchAnimationIndex(
    itemIndex: Int,
    listState: LazyListState
): Int {
    val visibleRank = listState.layoutInfo.visibleItemsInfo.indexOfFirst { itemInfo ->
        itemInfo.index == itemIndex
    }
    if (visibleRank != -1) {
        return visibleRank.coerceAtMost(10)
    }
    return (itemIndex - listState.firstVisibleItemIndex)
        .coerceAtLeast(0)
        .coerceAtMost(10)
}
