package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.library.ArtistRootPage
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController

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
    SharedTransitionLayout(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(state.topBarScrollConnection)
            .padding(innerPadding)
            .padding(bottom = state.miniPlayerContentBottomPadding)
    ) {
        val sharedTransitionScope = this
        Box(modifier = Modifier.fillMaxSize()) {
            TopLevelPagerContent(
                pagerState = state.pagerState,
                uiState = state.uiState,
                playerUiState = state.playerUiState,
                libraryPlaylistController = libraryPlaylistController,
                permissionDenied = state.permissionDenied,
                showSwipeHint = state.showSwipeHint,
                secondaryOpen = state.secondaryOpen,
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
                onAllowFullscreenFromCollapsedChange = callbacks.onAllowFullscreenFromCollapsedChange,
                preloadSongMetadataCount = state.preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = callbacks.onPreloadSongMetadataCountChange,
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
            FlowtoneArtistRootLayer(
                state = state,
                callbacks = callbacks,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FlowtoneArtistRootLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = state.rootPage as? FlowtoneRootPage.ArtistRootPage,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtonePageEasing
                )
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtonePageEasing
                )
            )
        },
        label = "ArtistRootPageTransition",
        modifier = modifier
    ) { artistRootPage ->
        fun artistPageItemModifier(index: Int): Modifier {
            return staggeredPageElementModifier(index)
        }
        if (artistRootPage != null) {
            ArtistRootPage(
                artistName = artistRootPage.artistName,
                allSongs = state.uiState.songs,
                currentSong = state.playerUiState.currentSong,
                onBack = callbacks.onCloseArtistRootPage,
                onSongClick = { songs, index ->
                    callbacks.onPlaylistSongClick(
                        songs,
                        index,
                        PlaybackSource.artist(artistRootPage.artistName)
                    )
                },
                itemModifier = ::artistPageItemModifier,
                modifier = Modifier
                    .fillMaxSize()
                    .rightSwipeBackGesture(callbacks.onCloseArtistRootPage)
            )
        }
    }
}
