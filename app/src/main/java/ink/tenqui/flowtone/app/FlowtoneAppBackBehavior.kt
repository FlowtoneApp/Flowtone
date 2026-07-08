package ink.tenqui.flowtone.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal fun FlowtoneAppBackHandlers(
    secondaryPage: SecondaryPage?,
    hasCurrentSong: Boolean,
    miniPlayerExpanded: Boolean,
    miniPlayerFullscreen: Boolean,
    rootPage: FlowtoneRootPage,
    searchActive: Boolean,
    searchKeyboardVisible: Boolean,
    searchReturnStage: SearchReturnStage,
    onNavigateBack: () -> Unit,
    onExitMiniPlayerFullscreen: () -> Unit,
    onCollapseMiniPlayer: () -> Unit,
    onCloseArtistRootPage: () -> Unit,
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
    BackHandler(enabled = searchActive) {
        if (isSearchReturnAnimationStage(searchReturnStage)) {
            return@BackHandler
        }
        if (searchKeyboardVisible) {
            onDismissSearchKeyboard()
        } else {
            onExitSearch()
        }
    }
    BackHandler(enabled = rootPage is FlowtoneRootPage.ArtistRootPage) {
        onCloseArtistRootPage()
    }
}

internal fun navigateFlowtoneAppBack(appState: FlowtoneAppState) {
    if (appState.secondaryPage == SecondaryPage.Settings) {
        val nestedBackAction = appState.settingsBackAction
        if (nestedBackAction != null) {
            nestedBackAction()
        } else {
            appState.secondaryPage = null
        }
    } else if (appState.secondaryPage == SecondaryPage.OpenSource) {
        val nestedBackAction = appState.openSourceBackAction
        if (nestedBackAction != null) {
            nestedBackAction()
        } else {
            appState.secondaryPage = SecondaryPage.About
        }
    } else {
        val closingPage = appState.secondaryPage
        appState.secondaryPage = when (closingPage) {
            SecondaryPage.Settings,
            SecondaryPage.About,
            SecondaryPage.LocalLibrary,
            SecondaryPage.Playlist,
            SecondaryPage.Artist,
            SecondaryPage.ListeningRecords -> null
            SecondaryPage.OpenSource -> SecondaryPage.About
            null -> null
        }
        if (closingPage == SecondaryPage.Artist) {
            appState.selectedArtistName = null
        }
    }
}
