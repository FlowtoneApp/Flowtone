package ink.tenqui.flowtone.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal fun FlowtoneAppBackHandlers(
    secondaryPage: SecondaryPage?,
    hasCurrentSong: Boolean,
    miniPlayerExpanded: Boolean,
    miniPlayerFullscreen: Boolean,
    searchActive: Boolean,
    searchKeyboardVisible: Boolean,
    onNavigateBack: () -> Unit,
    onExitMiniPlayerFullscreen: () -> Unit,
    onCollapseMiniPlayer: () -> Unit,
    onDismissSearchKeyboard: () -> Unit,
    onExitSearch: () -> Unit
) {
    BackHandler(enabled = secondaryPage != null, onBack = onNavigateBack)
    BackHandler(enabled = hasCurrentSong && (miniPlayerExpanded || miniPlayerFullscreen)) {
        if (miniPlayerFullscreen) {
            onExitMiniPlayerFullscreen()
        } else {
            onCollapseMiniPlayer()
        }
    }
    BackHandler(enabled = searchActive && secondaryPage == null) {
        if (searchKeyboardVisible) {
            onDismissSearchKeyboard()
        } else {
            onExitSearch()
        }
    }
}

internal fun closeFlowtoneSecondaryPage(appState: FlowtoneAppState) {
    appState.secondaryNavigation = appState.secondaryNavigation.pop()
    appState.secondaryPathSegments = emptyList()
}

internal fun navigateFlowtoneAppBack(appState: FlowtoneAppState) {
    if (appState.secondaryPage == SecondaryPage.Settings) {
        val nestedBackAction = appState.settingsBackAction
        if (nestedBackAction != null) {
            nestedBackAction()
        } else {
            closeFlowtoneSecondaryPage(appState)
        }
    } else if (appState.secondaryPage == SecondaryPage.OpenSource) {
        val nestedBackAction = appState.openSourceBackAction
        if (nestedBackAction != null) {
            nestedBackAction()
        } else {
            appState.secondaryNavigation = SecondaryNavigationState(
                listOf(SecondaryDestination.Standard(SecondaryPage.About))
            )
        }
    } else {
        closeFlowtoneSecondaryPage(appState)
    }
}
