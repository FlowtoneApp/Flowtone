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
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.library.LibraryScreen
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.screens.HomeScreen
import ink.tenqui.flowtone.ui.screens.MineScreen
import ink.tenqui.flowtone.viewmodel.MusicUiState

@Composable
internal fun TopLevelPagerContent(
    pagerState: PagerState,
    uiState: MusicUiState,
    homeScrollState: ScrollState,
    libraryPlaylistController: LibraryPlaylistController,
    playlistSongEntries: List<PlaylistSongEntry>,
    permissionDenied: Boolean,
    showSwipeHint: Boolean,
    pageScope: PageTransitionScope,
    userScrollEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onLoadProviderEntityCollections: (String) -> Unit,
    onProviderSongQueueClick: (List<ProviderSong>, Int, PlaybackSource) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    cloudAccent: Color,
    onOpenLocalLibrary: () -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    likedSongCount: Int,
    flowCloudSpeed: Float,
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
            val page = TopLevelPage.entries[pageIndex]
            // 卡片流云只跟随页面可见性，不跟随歌曲播放或暂停状态。
            val isPageFlowCloudActive = pagerState.currentPage == pageIndex
            when (page) {
                TopLevelPage.Home -> HomeScreen(
                    pagerState = pagerState,
                    pagerFlingBehavior = pagerFlingBehavior,
                    songs = uiState.songs,
                    listeningStats = uiState.listeningStats,
                    playlists = flowtoneDisplayedLibraryPlaylists(
                        playlists = libraryPlaylistController.playlists,
                        likedSongCount = likedSongCount
                    ),
                    playlistSongEntries = playlistSongEntries,
                    onSongClick = onSongClick,
                    onOpenPlaylist = onOpenPlaylist,
                    pageScope = pageScope,
                    flowCloudSpeed = flowCloudSpeed,
                    isFlowCloudPlaying = isPageFlowCloudActive,
                    drawBackground = false,
                    scrollState = homeScrollState,
                    modifier = Modifier.fillMaxSize()
                )

                TopLevelPage.Library -> LibraryScreen(
                    songCount = uiState.songs.size,
                    likedSongCount = likedSongCount,
                    libraryProviderOptions = uiState.libraryProviderOptions,
                    providerSongs = uiState.providerSongs,
                    providerAlbums = uiState.providerAlbums,
                    providerSongCatalogLoadStates = uiState.providerSongCatalogLoadStates,
                    providerAlbumCatalogLoadStates = uiState.providerAlbumCatalogLoadStates,
                    currentSong = uiState.playbackQueue.getOrNull(uiState.currentQueueIndex),
                    isPageActive = pagerState.currentPage == pageIndex,
                    onLoadProviderEntityCollections = onLoadProviderEntityCollections,
                    onProviderSongQueueClick = onProviderSongQueueClick,
                    onOpenProviderAlbum = onOpenProviderAlbum,
                    onOpenLocalLibrary = onOpenLocalLibrary,
                    onOpenPlaylist = onOpenPlaylist,
                    pageScope = pageScope,
                    flowCloudSpeed = flowCloudSpeed,
                    isFlowCloudPlaying = isPageFlowCloudActive,
                    playlistController = libraryPlaylistController,
                    modifier = Modifier.fillMaxSize()
                )

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
            text = "\u5de6\u53f3\u6ed1\u52a8\u5207\u6362\u9875\u9762",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
