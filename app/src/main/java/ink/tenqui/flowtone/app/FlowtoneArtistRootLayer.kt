package ink.tenqui.flowtone.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.PageTransitionHost
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.library.ArtistRootPage

@Composable
internal fun FlowtoneArtistRootLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    hostMode: ArtistRootNavigationMode,
    modifier: Modifier = Modifier
) {
    val artistRootPage = state.rootPage as? FlowtoneRootPage.ArtistRootPage
    val modeMatches = state.artistRootNavigationMode == hostMode
    val artistVisible = artistRootPage != null &&
        modeMatches &&
        when (hostMode) {
            ArtistRootNavigationMode.MiniPlayer -> true
            ArtistRootNavigationMode.NormalPage ->
                state.searchReturnStage != SearchReturnStage.ArtistExitingToSearch
        }

    PageTransitionHost(
        targetState = artistVisible,
        modifier = modifier.fillMaxSize()
    ) { visible ->
        if (!visible) {
            return@PageTransitionHost
        }
        val pageScope = this
        fun artistPageItemModifier(index: Int): Modifier {
            return pageScope.elementModifier(index, orderCount = 24)
        }
        fun artistHeaderCardModifier(index: Int): Modifier {
            return pageScope.elementModifier(index, orderCount = 4)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            val page = artistRootPage ?: return@Box
            ArtistRootPage(
                artistName = page.artistName,
                allSongs = state.uiState.songs,
                currentSong = state.playerUiState.currentSong,
                onBack = callbacks.onCloseArtistRootPage,
                onSongClick = { songs, index ->
                    callbacks.onPlaylistSongClick(
                        songs,
                        index,
                        PlaybackSource.artist(page.artistName)
                    )
                },
                itemModifier = ::artistPageItemModifier,
                headerCardModifier = ::artistHeaderCardModifier,
                modifier = Modifier
                    .fillMaxSize()
                    .rightSwipeBackGesture(callbacks.onCloseArtistRootPage)
            )
        }
    }
}
