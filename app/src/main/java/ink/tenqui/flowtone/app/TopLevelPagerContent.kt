package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.screens.MineScreen

@Composable
internal fun TopLevelPagerContent(
    pagerState: PagerState,
    homeGallery: HomeGallerySelection?,
    homeScrollState: ScrollState,
    homeSongs: List<Song>,
    minimalHomeSongs: List<Song>,
    fastInteractionTestSelection: FastInteractionTestSelection?,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onOpenFastInteractionTestPlaylist: (List<Song>) -> Unit,
    showSwipeHint: Boolean,
    pageScope: PageTransitionScope,
    userScrollEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    cloudAccent: Color,
    modifier: Modifier = Modifier
) {
    val pagerFlingBehavior = PagerDefaults.flingBehavior(pagerState)
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            // Never change this flag during an active pointer gesture. Cancelling a
            // running Pager drag can leave it between pages until the next swipe.
            userScrollEnabled = userScrollEnabled,
            flingBehavior = pagerFlingBehavior,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (TopLevelPage.entries[pageIndex]) {
                TopLevelPage.Home -> HomeGalleryContent(
                    selection = homeGallery,
                    scrollState = homeScrollState,
                    pagerState = pagerState,
                    pagerFlingBehavior = pagerFlingBehavior,
                    pagerScrollEnabled = userScrollEnabled,
                    pageScope = pageScope,
                    songs = homeSongs,
                    minimalSongs = minimalHomeSongs,
                    currentSong = currentSong,
                    onSongClick = onSongClick,
                    modifier = Modifier.fillMaxSize()
                )

                TopLevelPage.Library -> {
                    val selection = fastInteractionTestSelection
                    if (selection != null) {
                        FastLibraryInteractionContent(
                            selection = selection,
                            currentSong = currentSong,
                            onSongClick = onSongClick,
                            onOpenTestPlaylist = onOpenFastInteractionTestPlaylist,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }

                TopLevelPage.Mine -> MineScreen(
                    cloudAccent = cloudAccent,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    pageScope = pageScope,
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
            text = "左右滑动切换页面",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
