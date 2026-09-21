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
    val remainingNavigation = appState.secondaryNavigation.pop()
    appState.secondaryNavigation = remainingNavigation
    if (!remainingNavigation.retainsSettingsPathSegments()) {
        appState.secondaryPathSegments = emptyList()
    }
}

/** The settings subsection is shared by its Online Extensions descendants. */
internal fun SecondaryNavigationState.retainsSettingsPathSegments(): Boolean =
    entries.any { entry ->
        (entry.destination as? SecondaryDestination.Standard)?.page in setOf(
            SecondaryPage.Settings,
            SecondaryPage.OnlineExtensions
        )
    }

internal fun navigateFlowtoneAppBack(appState: FlowtoneAppState) {
    if (appState.secondaryPage == SecondaryPage.Settings ||
        appState.secondaryPage == SecondaryPage.ExtensionSettings
    ) {
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
            appState.secondaryNavigation = appState.secondaryNavigation.replaceWith(
                SecondaryDestination.Standard(SecondaryPage.About)
            )
        }
    } else {
        closeFlowtoneSecondaryPage(appState)
    }
}
