package ink.tenqui.flowtone.app

import android.util.Log
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
    onCloseSecondaryPage: () -> Unit,
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
    BackHandler(
        enabled = secondaryPage == SecondaryPage.Album,
        onBack = onCloseSecondaryPage
    )
    BackHandler(enabled = rootPage is FlowtoneRootPage.ArtistRootPage) {
        onCloseArtistRootPage()
    }
}

internal fun closeFlowtoneSecondaryPage(appState: FlowtoneAppState) {
    Log.d("FlowtonePlaylistDebug", "PLAYLIST_CLOSE_REQUESTED")
    appState.secondaryPage = null
    appState.secondaryPathSegments = emptyList()
    appState.selectedPlaylistId = null
    appState.selectedPlaylistTitle = null
    appState.selectedAlbumId = null
    appState.selectedArtistName = null
    Log.d("FlowtonePlaylistDebug", "PLAYLIST_LIVE_SELECTION_CLEARED")
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
            SecondaryPage.Album,
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
