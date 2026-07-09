package ink.tenqui.flowtone.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.screens.homeScreenBackground

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FlowtoneScaffoldContent(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    libraryPlaylistController: LibraryPlaylistController,
    playlistSongEntries: List<PlaylistSongEntry>,
    likedSongCount: Int,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (
            state.rootPage == FlowtoneRootPage.MainTabs &&
            state.selectedTopLevelPage == TopLevelPage.Home &&
            state.secondaryPage == null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .homeScreenBackground()
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
