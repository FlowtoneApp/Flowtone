package ink.tenqui.flowtone.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.SearchArtist
import ink.tenqui.flowtone.data.search.searchPlaybackQueueStartIndex
import ink.tenqui.flowtone.ui.components.ArtistListItem
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageProgressElement

@Composable
internal fun GlobalSearchOverlay(
    searchUiState: GlobalSearchUiState,
    currentSong: Song?,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
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
