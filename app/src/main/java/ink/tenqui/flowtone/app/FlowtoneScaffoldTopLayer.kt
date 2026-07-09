package ink.tenqui.flowtone.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks
) {
    if (state.rootPage == FlowtoneRootPage.MainTabs) {
        val isTopLevelRootPage = state.secondaryPage == null
        val secondaryBackgroundAlpha by animateFloatAsState(
            targetValue = if (isTopLevelRootPage) 0f else 1f,
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = FlowtoneMotion.Easing
            ),
            label = "TopBarSecondaryBackgroundAlpha"
        )
        val backgroundAlpha = if (isTopLevelRootPage || state.searchActive) {
            0f
        } else {
            state.topBarBackgroundAlpha
        }
        val titleVisible = !isTopLevelRootPage

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
            secondaryBackgroundAlpha = secondaryBackgroundAlpha,
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
