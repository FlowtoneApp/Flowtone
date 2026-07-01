package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.library.LibraryScreen
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.screens.HomeScreen
import ink.tenqui.flowtone.ui.screens.MineScreen
import ink.tenqui.flowtone.viewmodel.MusicUiState

@Composable
internal fun TopLevelPagerContent(
    pagerState: PagerState,
    uiState: MusicUiState,
    playerUiState: PlayerUiState,
    permissionDenied: Boolean,
    showSwipeHint: Boolean,
    secondaryOpen: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val page = TopLevelPage.entries[pageIndex]
            when (page) {
                TopLevelPage.Home -> HomeScreen(
                    modifier = Modifier.fillMaxSize()
                )

                TopLevelPage.Library -> LibraryScreen(
                    songCount = uiState.songs.size,
                    onOpenLocalLibrary = onOpenLocalLibrary,
                    visible = !secondaryOpen,
                    modifier = Modifier.fillMaxSize()
                )

                TopLevelPage.Mine -> MineScreen(
                    listeningStats = uiState.listeningStats,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    secondaryOpen = secondaryOpen,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        SwipePageHint(
            visible = showSwipeHint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun SwipePageHint(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 450,
                easing = LinearEasing
            )
        ),
        modifier = modifier
    ) {
        Text(
            text = "\u5de6\u53f3\u6ed1\u52a8\u5207\u6362\u9875\u9762",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
