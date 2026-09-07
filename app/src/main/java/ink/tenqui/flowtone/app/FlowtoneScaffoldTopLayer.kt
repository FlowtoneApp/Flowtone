package ink.tenqui.flowtone.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.ui.library.ArtistHeaderStateStore
import ink.tenqui.flowtone.ui.library.ArtistScrollStateStore
import ink.tenqui.flowtone.ui.library.ArtistTopBarStateStore
import ink.tenqui.flowtone.ui.library.artistHeaderVariant
import ink.tenqui.flowtone.ui.library.PlaylistSelectionTopBarState

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    detailHeaderCollapseProgressState: State<Float>?,
    songSelectionState: PlaylistSelectionTopBarState?,
    onCloseSongSelection: () -> Unit,
    playlistBackAction: (() -> Unit)?,
    playlistSortProgress: Float,
    descriptionBlurRadius: Dp,
    onFullTitleRequest: (String) -> Unit,
    artistHeaderStateStore: ArtistHeaderStateStore,
    artistScrollStateStore: ArtistScrollStateStore,
    artistTopBarStateStore: ArtistTopBarStateStore
) {
    val artistRoute = artistTopBarRoute(state.secondaryEntries)
    if (artistRoute != null) {
        val topBarStateOwner = artistTopBarStateStore.ownerFor(
            entryKey = artistRoute.artistEntryKey,
            initialScrollPosition = artistScrollStateStore.ownerFor(
                artistRoute.artistEntryKey
            ).position
        )
        val headerOwner = artistHeaderStateStore.ownerFor(
            entryKey = artistRoute.artistEntryKey,
            artistName = artistRoute.artist.name,
            avatarImage = artistRoute.artist.identity.avatar,
            profileMetadata = artistRoute.artist.identity.profileMetadata
        )
        key(artistRoute.artistEntryKey) {
            val headerModel = headerOwner.renderModel
            ArtistIdentityTopBar(
                artistName = artistRoute.artist.name,
                avatarImage = headerModel?.avatarImage
                    ?: headerOwner.avatarImage
                    ?: artistRoute.artist.identity.avatar,
                variant = headerModel?.variant ?: artistHeaderVariant(
                    artistRoute.artist.identity.profileMetadata?.banner != null
                ),
                artistColor = headerModel?.artistColor ?: Color.Transparent,
                identityVisible = artistTopBarIdentityVisible(
                    route = artistRoute,
                    scrollIdentityVisible = topBarStateOwner.visible
                ),
                albumEntryKey = artistRoute.albumEntryKey,
                albumTitle = artistRoute.albumTitle,
                onBack = {
                    if (headerOwner.focusRequested) {
                        headerOwner.focusRequested = false
                    } else {
                        callbacks.onCloseSecondaryPage()
                    }
                },
                onFullTitleRequest = onFullTitleRequest
            )
        }
    } else {
        val titleVisible = state.secondaryPage != null
        val standardPathSegments = secondaryDestinationBreadcrumbs(
            current = state.secondaryDestination,
            previous = state.previousSecondaryDestination,
            nestedSegments = state.secondaryPathSegments
        )

        FlowtoneTopBar(
            selectedTopLevelPage = state.selectedTopLevelPage,
            pagerState = state.pagerState,
            secondaryPage = state.secondaryPage,
            additionalPathSegments = standardPathSegments,
            titleVisible = titleVisible,
            songSelectionState = songSelectionState,
            hideBackButton = state.hideSecondaryBackButton,
            searchActive = state.searchActive,
            searchQuery = state.searchUiState.queryText,
            searchColors = state.searchColors,
            searchFocusRequest = state.searchFocusRequest,
            searchKeyboardDismissRequest = state.searchKeyboardDismissRequest,
            onBack = if (songSelectionState != null) {
                onCloseSongSelection
            } else if (state.secondaryPage == SecondaryPage.Playlist) {
                playlistBackAction ?: callbacks.onCloseSecondaryPage
            } else {
                callbacks.onNavigateBack
            },
            onSearchClick = callbacks.onOpenSearch,
            onSearchQueryChange = callbacks.onSearchQueryChange,
            onExitSearch = callbacks.onExitSearch,
            onClearSearch = callbacks.onClearSearch,
            onSearchFocusRequestConsumed = callbacks.onSearchFocusRequestConsumed,
            onSearchKeyboardDismissRequestConsumed =
                callbacks.onSearchKeyboardDismissRequestConsumed,
            onSearchInputFocusChange = callbacks.onSearchInputFocusChange,
            onSearchImeAction = callbacks.onSearchImeAction,
            onFullTitleRequest = onFullTitleRequest,
            playlistSortProgress = playlistSortProgress,
            modifier = Modifier.blur(descriptionBlurRadius)
        )
    }
}
