package ink.tenqui.flowtone.app

import androidx.compose.runtime.Composable

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks
) {
    if (state.rootPage == FlowtoneRootPage.MainTabs) {
        val isPlainHomePage =
            state.selectedTopLevelPage == TopLevelPage.Home &&
            state.secondaryPage == null &&
            !state.searchActive
        val backgroundAlpha = if (isPlainHomePage) {
            0f
        } else {
            state.topBarBackgroundAlpha
        }
        val titleVisible = !isPlainHomePage

        FlowtoneTopBar(
            selectedTopLevelPage = state.selectedTopLevelPage,
            pagerState = state.pagerState,
            secondaryPage = state.secondaryPage,
            additionalPathSegments = state.secondaryPathSegments,
            backgroundAlpha = backgroundAlpha,
            titleVisible = titleVisible,
            hideBackButton = state.hideSecondaryBackButton,
            searchActive = state.searchActive,
            searchQuery = state.searchUiState.queryText,
            searchColors = state.searchColors,
            searchFocusRequest = state.searchFocusRequest,
            searchKeyboardDismissRequest = state.searchKeyboardDismissRequest,
            searchReentryProgress = state.searchReentryProgress,
            onBack = callbacks.onNavigateBack,
            onSearchClick = callbacks.onOpenSearch,
            onSearchQueryChange = callbacks.onSearchQueryChange,
            onExitSearch = callbacks.onExitSearch,
            onClearSearch = callbacks.onClearSearch,
            onSearchFocusRequestConsumed = callbacks.onSearchFocusRequestConsumed,
            onSearchKeyboardDismissRequestConsumed =
                callbacks.onSearchKeyboardDismissRequestConsumed,
            onSearchInputFocusChange = callbacks.onSearchInputFocusChange,
            onSearchImeAction = callbacks.onSearchImeAction
        )
    }
}
