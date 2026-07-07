package ink.tenqui.flowtone.app

import androidx.compose.runtime.Composable

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks
) {
    if (state.rootPage == FlowtoneRootPage.MainTabs) {
        FlowtoneTopBar(
            selectedTopLevelPage = state.selectedTopLevelPage,
            pagerState = state.pagerState,
            secondaryPage = state.secondaryPage,
            additionalPathSegments = state.secondaryPathSegments,
            backgroundAlpha = state.topBarBackgroundAlpha,
            hideBackButton = state.hideSecondaryBackButton,
            onBack = callbacks.onNavigateBack
        )
    }
}
