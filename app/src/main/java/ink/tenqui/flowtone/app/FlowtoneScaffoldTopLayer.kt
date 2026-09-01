package ink.tenqui.flowtone.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import ink.tenqui.flowtone.ui.components.flowtoneCollapsingTopBarBackgroundAlpha
import ink.tenqui.flowtone.ui.library.PlaylistSelectionTopBarState
import ink.tenqui.flowtone.ui.library.PlaylistSongSort

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    isArtistToolbarContentVisible: (Long) -> Boolean,
    detailHeaderCollapseProgressState: State<Float>?,
    songSelectionState: PlaylistSelectionTopBarState?,
    onCloseSongSelection: () -> Unit,
    playlistBackAction: (() -> Unit)?,
    artistProfileBackAction: (() -> Unit)?,
    playlistSortProgress: Float,
    descriptionBlurRadius: Dp
) {
    val artistTopBarRoute = artistTopBarRoute(state.secondaryEntries)
    if (artistTopBarRoute != null && songSelectionState == null) {
        key(artistTopBarRoute.artistEntryId) {
            val collapsedArtistToolbarVisible =
                isArtistToolbarContentVisible(artistTopBarRoute.artistEntryId)
            ArtistIdentityTopBar(
                artistName = artistTopBarRoute.artistName,
                hasLocalContent = artistTopBarRoute.hasLocalContent,
                providedAvatar = artistTopBarRoute.avatar,
                destinationTitle = artistTopBarRoute.destinationTitle,
                identityVisible = artistTopBarRoute.destinationTitle != null ||
                    collapsedArtistToolbarVisible,
                artistSurfaceVisible = collapsedArtistToolbarVisible,
                allSongs = state.uiState.songs,
                currentSong = state.playerUiState.currentSong,
                onBack = artistProfileBackAction ?: callbacks.onNavigateBack,
                playlistSortProgress = playlistSortProgress,
                modifier = Modifier.blur(descriptionBlurRadius)
            )
        }
    } else if (state.secondaryPage != SecondaryPage.Artist) {
        val titleVisible = state.secondaryPage != null
        val standardPathSegments = if (artistTopBarRoute != null) {
            listOfNotNull(artistTopBarRoute.destinationTitle)
        } else {
            secondaryDestinationBreadcrumbs(
                current = state.secondaryDestination,
                previous = state.previousSecondaryDestination,
                nestedSegments = state.secondaryPathSegments
            )
        }

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
            playlistSortProgress = playlistSortProgress,
            modifier = Modifier.blur(descriptionBlurRadius)
        )
    }
}

internal data class ArtistTopBarRoute(
    val artistEntryId: Long,
    val artistName: String,
    val hasLocalContent: Boolean,
    val avatar: ink.tenqui.flowtone.core.online.ExtensionImage?,
    val destinationTitle: String?
)

internal fun artistTopBarRoute(entries: List<SecondaryStackEntry>): ArtistTopBarRoute? {
    val currentEntry = entries.lastOrNull() ?: return null
    return when (val current = currentEntry.destination) {
        is SecondaryDestination.Artist -> ArtistTopBarRoute(
            artistEntryId = currentEntry.id,
            artistName = current.name,
            hasLocalContent = current.identity.hasLocalContent,
            avatar = current.identity.avatar,
            destinationTitle = null
        )
        is SecondaryDestination.Album -> {
            val parentEntry = entries.getOrNull(entries.lastIndex - 1) ?: return null
            val parentArtist = parentEntry.destination as? SecondaryDestination.Artist
                ?: return null
            ArtistTopBarRoute(
                artistEntryId = parentEntry.id,
                artistName = parentArtist.name,
                hasLocalContent = parentArtist.identity.hasLocalContent,
                avatar = parentArtist.identity.avatar,
                destinationTitle = current.title
            )
        }
        else -> null
    }
}
