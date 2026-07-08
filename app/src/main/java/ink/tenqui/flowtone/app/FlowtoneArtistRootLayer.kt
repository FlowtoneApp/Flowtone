package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.library.ArtistRootPage

private const val ArtistRootExitLastAnimationIndex = 12

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

    AnimatedVisibility(
        visible = artistVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = FlowtonePageEasing
            ),
            initialAlpha = 1f
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = FlowtonePageEasing
            ),
            targetAlpha = 1f
        ),
        label = "ArtistRootPageVisibility",
        modifier = modifier.fillMaxSize()
    ) {
        fun artistPageItemModifier(index: Int): Modifier {
            if (hostMode == ArtistRootNavigationMode.MiniPlayer) {
                return staggeredPageElementModifier(index)
            }
            return staggeredPageElementModifier(
                animationIndex = index,
                exitAnimationIndex = (ArtistRootExitLastAnimationIndex - index)
                    .coerceAtLeast(0)
            )
        }
        fun artistHeaderCardModifier(index: Int): Modifier {
            if (hostMode == ArtistRootNavigationMode.MiniPlayer) {
                return staggeredPageElementModifier(
                    animationIndex = index,
                    initialOffsetY = { -it / 6 },
                    targetOffsetY = { -it / 6 }
                )
            }
            return staggeredPageElementModifier(
                animationIndex = index,
                exitAnimationIndex = (ArtistRootExitLastAnimationIndex - index)
                    .coerceAtLeast(0),
                initialOffsetY = { -it / 6 },
                targetOffsetY = { -it / 6 }
            )
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
