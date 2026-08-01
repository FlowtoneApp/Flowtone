package ink.tenqui.flowtone.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.flowtoneCollapsingTopBarBackgroundAlpha
import ink.tenqui.flowtone.ui.library.PlaylistSelectionTopBarState
import ink.tenqui.flowtone.ui.library.PlaylistSongSort

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    detailHeaderCollapseProgressState: State<Float>?,
    songSelectionState: PlaylistSelectionTopBarState?,
    onCloseSongSelection: () -> Unit,
    playlistSongSort: PlaylistSongSort,
    playlistSortPanelOpen: Boolean,
    onPlaylistSortChange: (PlaylistSongSort) -> Unit,
    onPlaylistSortPanelOpenChange: (Boolean) -> Unit
) {
    if (state.rootPage == FlowtoneRootPage.MainTabs) {
        val isTopLevelRootPage = state.secondaryPage == null
        val usesSharedCloudTopBar = state.secondaryPage == SecondaryPage.Playlist ||
            state.secondaryPage == SecondaryPage.LocalLibrary
        val secondaryBackgroundAlpha by animateFloatAsState(
            targetValue = if (isTopLevelRootPage || usesSharedCloudTopBar) 0f else 1f,
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = FlowtoneMotion.Easing
            ),
            label = "TopBarSecondaryBackgroundAlpha"
        )
        val backgroundAlpha = when {
            isTopLevelRootPage || state.searchActive -> 0f
            usesSharedCloudTopBar -> 0f
            else -> state.topBarBackgroundAlpha * secondaryBackgroundAlpha
        }
        val titleVisible = !isTopLevelRootPage

        FlowtoneTopBar(
            selectedTopLevelPage = state.selectedTopLevelPage,
            pagerState = state.pagerState,
            secondaryPage = state.secondaryPage,
            additionalPathSegments = state.secondaryPathSegments,
            backgroundAlpha = backgroundAlpha,
            titleVisible = titleVisible,
            songSelectionState = songSelectionState,
            hideBackButton = state.hideSecondaryBackButton,
            searchActive = state.searchActive,
            searchQuery = state.searchUiState.queryText,
            searchColors = state.searchColors,
            secondaryBackgroundAlpha = secondaryBackgroundAlpha,
            searchFocusRequest = state.searchFocusRequest,
            searchKeyboardDismissRequest = state.searchKeyboardDismissRequest,
            searchReentryProgress = state.searchReentryProgress,
            onBack = if (songSelectionState != null) {
                onCloseSongSelection
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
            showPlaylistSortButton = (
                state.secondaryPage == SecondaryPage.LocalLibrary ||
                    (state.secondaryPage == SecondaryPage.Playlist &&
                        state.selectedPlaylistId != null)
                ) &&
                songSelectionState == null && !state.searchActive,
            playlistSongSort = playlistSongSort,
            playlistSortPanelOpen = playlistSortPanelOpen,
            onPlaylistSortChange = onPlaylistSortChange,
            onPlaylistSortPanelOpenChange = onPlaylistSortPanelOpenChange
        )
    }
}
