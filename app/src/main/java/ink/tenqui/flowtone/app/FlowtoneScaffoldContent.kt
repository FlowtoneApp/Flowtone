package ink.tenqui.flowtone.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.screens.topLevelPageBackground

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FlowtoneScaffoldContent(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    homeScrollState: ScrollState,
    topLevelPageCollapseProgress: TopLevelPageCollapseProgress,
    libraryPlaylistController: LibraryPlaylistController,
    playlistSongEntries: List<PlaylistSongEntry>,
    likedSongCount: Int,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val rootCloudAlpha by animateFloatAsState(
        targetValue = if (state.secondaryPage == null) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "TopLevelBackgroundCloudAlpha"
    )
    val secondaryBackgroundAlpha = 1f - rootCloudAlpha
    val pagePosition = topLevelContinuousPagePosition(
        currentPage = state.pagerState.currentPage,
        currentPageOffsetFraction = state.pagerState.currentPageOffsetFraction
    )
    val cloudPlacement = topLevelCloudPlacementForPagePosition(pagePosition)

    Box(modifier = modifier.fillMaxSize()) {
        if (state.rootPage == FlowtoneRootPage.MainTabs) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .topLevelPageBackground(
                        accentColor = topLevelPageBackgroundAccent(
                            pagePosition = pagePosition
                        ),
                        cloudAlpha = rootCloudAlpha,
                        cloudPlacement = cloudPlacement
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = secondaryBackgroundAlpha
                        )
                    )
            )
        }

        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(state.topBarScrollConnection)
                .padding(innerPadding)
                .padding(bottom = state.miniPlayerContentBottomPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TopLevelPagerContent(
                    pagerState = state.pagerState,
                    uiState = state.uiState,
                    playerUiState = state.playerUiState,
                    homeScrollState = homeScrollState,
                    libraryPlaylistController = libraryPlaylistController,
                    permissionDenied = state.permissionDenied,
                    showSwipeHint = state.showSwipeHint,
                    secondaryOpen = state.secondaryOpen,
                    userScrollEnabled = !state.searchActive,
                    onRequestPermission = callbacks.onRequestPermission,
                    onSongClick = callbacks.onSongClick,
                    onOpenSettings = callbacks.onOpenSettings,
                    onOpenAbout = callbacks.onOpenAbout,
                    onOpenLocalLibrary = callbacks.onOpenLocalLibrary,
                    onOpenPlaylist = callbacks.onOpenPlaylist,
                    onOpenListeningRecords = callbacks.onOpenListeningRecords,
                    likedSongCount = likedSongCount,
                    modifier = Modifier.fillMaxSize()
                )
                TopLevelSharedPageHeader(
                    pagerState = state.pagerState,
                    collapseProgress = topLevelPageCollapseProgress,
                    visible = state.rootPage == FlowtoneRootPage.MainTabs && !state.secondaryOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 21.dp, top = 48.dp, end = 20.dp)
                )
                SecondaryPageHost(
                    secondaryPage = state.secondaryPage,
                    appPreferences = state.appPreferences,
                    themeMode = state.themeMode,
                    onThemeModeChange = callbacks.onThemeModeChange,
                    disablePausedArtworkTilt = state.disablePausedArtworkTilt,
                    onDisablePausedArtworkTiltChange = callbacks.onDisablePausedArtworkTiltChange,
                    strictProgressBar = state.strictProgressBar,
                    onStrictProgressBarChange = callbacks.onStrictProgressBarChange,
                    hideSecondaryBackButton = state.hideSecondaryBackButton,
                    onHideSecondaryBackButtonChange = callbacks.onHideSecondaryBackButtonChange,
                    resumePlaybackAfterCall = state.resumePlaybackAfterCall,
                    onResumePlaybackAfterCallChange = callbacks.onResumePlaybackAfterCallChange,
                    allowFullscreenFromCollapsed = state.allowFullscreenFromCollapsed,
                    onAllowFullscreenFromCollapsedChange =
                        callbacks.onAllowFullscreenFromCollapsedChange,
                    preloadSongMetadataCount = state.preloadSongMetadataCount,
                    onPreloadSongMetadataCountChange =
                        callbacks.onPreloadSongMetadataCountChange,
                    songRecordThresholdSeconds = state.songRecordThresholdSeconds,
                    onOpenSongRecordThresholdDialog = callbacks.onOpenSongRecordThresholdDialog,
                    flowCloudSpeed = state.flowCloudSpeed,
                    onOpenFlowCloudSpeedDialog = callbacks.onOpenFlowCloudSpeedDialog,
                    uiState = state.uiState,
                    currentSong = state.playerUiState.currentSong,
                    selectedPlaylistId = state.selectedPlaylistId,
                    selectedPlaylistTitle = state.selectedPlaylistTitle,
                    selectedArtistName = state.selectedArtistName,
                    listeningRecordInitialTab = state.listeningRecordInitialTab,
                    likedSongKeys = state.likedSongKeys,
                    playlistSongEntries = playlistSongEntries,
                    permissionDenied = state.permissionDenied,
                    onRequestPermission = callbacks.onRequestPermission,
                    onSongClick = callbacks.onSongClick,
                    onPlaylistSongClick = callbacks.onPlaylistSongClick,
                    onCloseSecondaryPage = callbacks.onCloseSecondaryPage,
                    onSettingsBackActionChange = callbacks.settingsBackActionChange,
                    onSettingsPathSegmentsChange = callbacks.onSettingsPathSegmentsChange,
                    onOpenSource = callbacks.onOpenSource,
                    onOpenSourceBack = callbacks.onOpenSourceBack,
                    onOpenSourceBackActionChange = callbacks.openSourceBackActionChange,
                    onOpenSourcePathSegmentsChange = callbacks.onOpenSourcePathSegmentsChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun topLevelPageBackgroundAccent(pagePosition: Float): Color {
    val colors = listOf(
        Color(0xFF7898F5),
        Color(0xFFA77BDD),
        Color(0xFFD783A5)
    )
    val safePosition = pagePosition.coerceIn(
        minimumValue = 0f,
        maximumValue = (colors.lastIndex).toFloat()
    )
    val startIndex = kotlin.math.floor(safePosition).toInt().coerceIn(0, colors.lastIndex)
    val endIndex = (startIndex + 1).coerceAtMost(colors.lastIndex)
    val fraction = (safePosition - startIndex).coerceIn(0f, 1f)
    return lerp(colors[startIndex], colors[endIndex], fraction)
}

private fun topLevelContinuousPagePosition(
    currentPage: Int,
    currentPageOffsetFraction: Float
): Float {
    return (currentPage + currentPageOffsetFraction).coerceIn(
        minimumValue = 0f,
        maximumValue = TopLevelPage.entries.lastIndex.toFloat()
    )
}
