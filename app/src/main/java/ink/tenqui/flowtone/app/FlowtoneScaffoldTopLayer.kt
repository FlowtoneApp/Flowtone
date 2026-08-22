package ink.tenqui.flowtone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
    playlistSortProgress: Float
) {
    if (state.rootPage == FlowtoneRootPage.MainTabs) {
        val titleVisible = state.secondaryPage != null

        FlowtoneTopBar(
            selectedTopLevelPage = state.selectedTopLevelPage,
            pagerState = state.pagerState,
            secondaryPage = state.secondaryPage,
            additionalPathSegments = state.secondaryPathSegments,
            titleVisible = titleVisible,
            songSelectionState = songSelectionState,
            hideBackButton = state.hideSecondaryBackButton,
            searchActive = state.searchActive,
            searchQuery = state.searchUiState.queryText,
            searchColors = state.searchColors,
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
            playlistSortProgress = playlistSortProgress
        )
    }
}
