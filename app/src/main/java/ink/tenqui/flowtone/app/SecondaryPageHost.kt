package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.library.ArtistDetailScreen
import ink.tenqui.flowtone.ui.library.LikedSongsPlaylistScreen
import ink.tenqui.flowtone.ui.library.LocalLibraryScreen
import ink.tenqui.flowtone.ui.library.PlaylistDetailScreen
import ink.tenqui.flowtone.ui.screens.AboutScreen
import ink.tenqui.flowtone.ui.screens.OpenSourceScreen
import ink.tenqui.flowtone.ui.screens.SettingsScreen
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicUiState

@Composable
internal fun SecondaryPageHost(
    secondaryPage: SecondaryPage?,
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    disablePausedArtworkTilt: Boolean,
    onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    songRecordThresholdSeconds: Int,
    onOpenSongRecordThresholdDialog: () -> Unit,
    uiState: MusicUiState,
    currentSong: Song?,
    selectedPlaylistId: String?,
    selectedArtistName: String?,
    likedSongKeys: List<String>,
    playlistSongEntries: List<PlaylistSongEntry>,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaylistSongClick: (List<Song>, Int) -> Unit,
    onCloseSecondaryPage: () -> Unit,
    onSettingsBackActionChange: ((() -> Unit)?) -> Unit,
    onSettingsPathSegmentsChange: (List<String>) -> Unit,
    onOpenSource: () -> Unit,
    onOpenSourceBack: () -> Unit,
    onOpenSourceBackActionChange: ((() -> Unit)?) -> Unit,
    onOpenSourcePathSegmentsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var retainedPlaylistId by remember { mutableStateOf<String?>(null) }
    var retainedArtistName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(secondaryPage, selectedPlaylistId) {
        if (secondaryPage == SecondaryPage.Playlist && selectedPlaylistId != null) {
            retainedPlaylistId = selectedPlaylistId
        }
    }
    LaunchedEffect(secondaryPage, selectedArtistName) {
        if (secondaryPage == SecondaryPage.Artist && selectedArtistName != null) {
            retainedArtistName = selectedArtistName
        }
    }

    AnimatedContent(
        targetState = secondaryPage,
        transitionSpec = {
            val usesAboutFade =
                (initialState == null && targetState == SecondaryPage.About) ||
                    (initialState == SecondaryPage.About && targetState == null)

            if (usesAboutFade) {
                fadeIn(
                    tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtonePageEasing
                    )
                ) togetherWith fadeOut(
                    tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtonePageEasing
                    )
                )
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "SecondaryContentTransition",
        modifier = modifier
    ) { page ->
        fun elementModifier(index: Int): Modifier {
            return staggeredPageElementModifier(index)
        }
        fun fadingContainerModifier(): Modifier = Modifier.animateEnterExit(
            enter = fadeIn(
                tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtonePageEasing
                )
            ),
            exit = fadeOut(
                tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtonePageEasing
                )
            )
        )
        fun songItemModifier(index: Int): Modifier {
            val delayMillis = FlowtoneMotion.staggerDelayMillis(index)
            val durationMillis = FlowtoneMotion.staggerDurationMillis(index)
            return Modifier.animateEnterExit(
                enter = fadeIn(
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) { it / 6 },
                exit = fadeOut(
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) { -it / 6 }
            )
        }
        when (page) {
            SecondaryPage.Settings -> SettingsScreen(
                appPreferences = appPreferences,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                disablePausedArtworkTilt = disablePausedArtworkTilt,
                onDisablePausedArtworkTiltChange = onDisablePausedArtworkTiltChange,
                onBack = onCloseSecondaryPage,
                onBackActionChange = onSettingsBackActionChange,
                onPathSegmentsChange = onSettingsPathSegmentsChange,
                hideSecondaryBackButton = hideSecondaryBackButton,
                onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                resumePlaybackAfterCall = resumePlaybackAfterCall,
                onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                preloadSongMetadataCount = preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                songRecordThresholdSeconds = songRecordThresholdSeconds,
                onOpenSongRecordThresholdDialog = onOpenSongRecordThresholdDialog,
                elementModifier = ::elementModifier,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.About -> AboutScreen(
                onOpenSource = onOpenSource,
                onBack = onCloseSecondaryPage,
                elementModifier = ::elementModifier,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.OpenSource -> OpenSourceScreen(
                onBack = onOpenSourceBack,
                onBackActionChange = onOpenSourceBackActionChange,
                onPathSegmentsChange = onOpenSourcePathSegmentsChange,
                elementModifier = ::elementModifier,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.LocalLibrary -> LocalLibraryScreen(
                uiState = uiState,
                currentSong = currentSong,
                permissionDenied = permissionDenied,
                onRequestPermission = onRequestPermission,
                onSongClick = onSongClick,
                itemModifier = ::songItemModifier,
                modifier = fadingContainerModifier()
                    .fillMaxSize()
                    .rightSwipeBackGesture(onCloseSecondaryPage)
            )

            SecondaryPage.Playlist -> {
                val activePlaylistId = if (secondaryPage == SecondaryPage.Playlist) {
                    selectedPlaylistId
                } else {
                    retainedPlaylistId
                }
                if (activePlaylistId == LikedSongsPlaylistId) {
                    LikedSongsPlaylistScreen(
                        allSongs = uiState.songs,
                        likedSongKeys = likedSongKeys,
                        currentSong = currentSong,
                        onSongClick = onPlaylistSongClick,
                        itemModifier = ::songItemModifier,
                        modifier = fadingContainerModifier()
                            .fillMaxSize()
                            .rightSwipeBackGesture(onCloseSecondaryPage)
                    )
                } else {
                    PlaylistDetailScreen(
                        playlistId = activePlaylistId,
                        allSongs = uiState.songs,
                        playlistSongEntries = playlistSongEntries,
                        currentSong = currentSong,
                        onSongClick = onPlaylistSongClick,
                        itemModifier = ::songItemModifier,
                        suppressEmptyState = secondaryPage != SecondaryPage.Playlist,
                        modifier = fadingContainerModifier()
                            .fillMaxSize()
                            .rightSwipeBackGesture(onCloseSecondaryPage)
                    )
                }
            }

            SecondaryPage.Artist -> ArtistDetailScreen(
                artistName = if (secondaryPage == SecondaryPage.Artist) {
                    selectedArtistName
                } else {
                    retainedArtistName
                },
                allSongs = uiState.songs,
                currentSong = currentSong,
                onSongClick = onPlaylistSongClick,
                itemModifier = ::songItemModifier,
                modifier = fadingContainerModifier()
                    .fillMaxSize()
                    .rightSwipeBackGesture(onCloseSecondaryPage)
            )

            null -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}
