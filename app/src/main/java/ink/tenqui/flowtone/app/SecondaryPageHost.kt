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
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.library.ArtistDetailScreen
import ink.tenqui.flowtone.ui.library.LikedSongsPlaylistScreen
import ink.tenqui.flowtone.ui.library.LocalLibraryScreen
import ink.tenqui.flowtone.ui.library.PlaylistDetailScreen
import ink.tenqui.flowtone.ui.library.PlaylistBatchActions
import ink.tenqui.flowtone.ui.screens.AboutScreen
import ink.tenqui.flowtone.ui.screens.ListeningRecordTab
import ink.tenqui.flowtone.ui.screens.ListeningRecordsScreen
import ink.tenqui.flowtone.ui.screens.OpenSourceScreen
import ink.tenqui.flowtone.ui.screens.SettingsScreen
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.ui.player.lyrics.LyricsBackgroundStyle
import ink.tenqui.flowtone.viewmodel.MusicUiState

@Composable
internal fun SecondaryPageHost(
    secondaryPage: SecondaryPage?,
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    disablePausedArtworkTilt: Boolean,
    onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    strictProgressBar: Boolean,
    onStrictProgressBarChange: (Boolean) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    openExpandedMiniPlayerOnMediaClick: Boolean,
    onOpenExpandedMiniPlayerOnMediaClickChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    songRecordThresholdSeconds: Int,
    onOpenSongRecordThresholdDialog: () -> Unit,
    flowCloudSpeed: Float,
    onOpenFlowCloudSpeedDialog: () -> Unit,
    lyricsBackgroundStyle: LyricsBackgroundStyle,
    onLyricsBackgroundStyleChange: (LyricsBackgroundStyle) -> Unit,
    uiState: MusicUiState,
    currentSong: Song?,
    selectedPlaylistId: String?,
    selectedPlaylistTitle: String?,
    selectedArtistName: String?,
    listeningRecordInitialTab: ListeningRecordTab,
    likedSongKeys: List<String>,
    playlistSongEntries: List<PlaylistSongEntry>,
    playlistBatchActions: PlaylistBatchActions,
    onDetailHeaderCollapseProgressStateChange: (State<Float>?) -> Unit,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaylistSongClick: (List<Song>, Int, PlaybackSource) -> Unit,
    onCloseSecondaryPage: () -> Unit,
    onSettingsBackActionChange: ((() -> Unit)?) -> Unit,
    onSettingsPathSegmentsChange: (List<String>) -> Unit,
    onOpenSource: () -> Unit,
    onOpenSourceBack: () -> Unit,
    onOpenSourceBackActionChange: ((() -> Unit)?) -> Unit,
    onOpenSourcePathSegmentsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var songSelectionActive by remember { mutableStateOf(false) }
    val activeBatchActions = playlistBatchActions.copy(
        onSelectionModeChange = { active ->
            songSelectionActive = active
            playlistBatchActions.onSelectionModeChange(active)
        }
    )
    fun closeSelectionOrPage() {
        if (songSelectionActive) {
            playlistBatchActions.onRequestClearSelection()
        } else {
            onCloseSecondaryPage()
        }
    }
    var retainedPlaylistId by remember { mutableStateOf<String?>(null) }
    var retainedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var retainedArtistName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(secondaryPage, selectedPlaylistId) {
        if (secondaryPage == SecondaryPage.Playlist && selectedPlaylistId != null) {
            retainedPlaylistId = selectedPlaylistId
        }
    }
    LaunchedEffect(secondaryPage, selectedPlaylistTitle) {
        if (secondaryPage == SecondaryPage.Playlist && selectedPlaylistTitle != null) {
            retainedPlaylistTitle = selectedPlaylistTitle
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
                strictProgressBar = strictProgressBar,
                onStrictProgressBarChange = onStrictProgressBarChange,
                onBack = onCloseSecondaryPage,
                onBackActionChange = onSettingsBackActionChange,
                onPathSegmentsChange = onSettingsPathSegmentsChange,
                hideSecondaryBackButton = hideSecondaryBackButton,
                onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                resumePlaybackAfterCall = resumePlaybackAfterCall,
                onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                openExpandedMiniPlayerOnMediaClick = openExpandedMiniPlayerOnMediaClick,
                onOpenExpandedMiniPlayerOnMediaClickChange =
                    onOpenExpandedMiniPlayerOnMediaClickChange,
                preloadSongMetadataCount = preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                songRecordThresholdSeconds = songRecordThresholdSeconds,
                onOpenSongRecordThresholdDialog = onOpenSongRecordThresholdDialog,
                flowCloudSpeed = flowCloudSpeed,
                onOpenFlowCloudSpeedDialog = onOpenFlowCloudSpeedDialog,
                lyricsBackgroundStyle = lyricsBackgroundStyle,
                onLyricsBackgroundStyleChange = onLyricsBackgroundStyleChange,
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
                title = SecondaryPage.LocalLibrary.title,
                uiState = uiState,
                currentSong = currentSong,
                permissionDenied = permissionDenied,
                onRequestPermission = onRequestPermission,
                onSongClick = onSongClick,
                batchActions = activeBatchActions,
                showContentHeader = false,
                itemModifier = ::songItemModifier,
                onCollapseProgressStateChange =
                    onDetailHeaderCollapseProgressStateChange,
                headerModifier = elementModifier(0),
                contentModifier = fadingContainerModifier(),
                modifier = Modifier
                    .fillMaxSize()
                    .rightSwipeBackGesture(::closeSelectionOrPage)
            )

            SecondaryPage.Playlist -> {
                val activePlaylistId = if (secondaryPage == SecondaryPage.Playlist) {
                    selectedPlaylistId
                } else {
                    retainedPlaylistId
                }
                val activePlaylistTitle = if (secondaryPage == SecondaryPage.Playlist) {
                    selectedPlaylistTitle
                } else {
                    retainedPlaylistTitle
                }
                val playlistTitle = activePlaylistTitle ?: SecondaryPage.Playlist.title
                if (activePlaylistId == LikedSongsPlaylistId) {
                    LikedSongsPlaylistScreen(
                        playlistTitle = playlistTitle,
                        allSongs = uiState.songs,
                        likedSongKeys = likedSongKeys,
                        currentSong = currentSong,
                        onSongClick = { songs, index ->
                            onPlaylistSongClick(songs, index, PlaybackSource.LikedSongs)
                        },
                        batchActions = activeBatchActions,
                        itemModifier = ::songItemModifier,
                        onCollapseProgressStateChange =
                            onDetailHeaderCollapseProgressStateChange,
                        headerModifier = elementModifier(0),
                        contentModifier = fadingContainerModifier(),
                        modifier = Modifier
                            .fillMaxSize()
                            .rightSwipeBackGesture(::closeSelectionOrPage)
                    )
                } else {
                    PlaylistDetailScreen(
                        playlistId = activePlaylistId,
                        playlistTitle = playlistTitle,
                        allSongs = uiState.songs,
                        playlistSongEntries = playlistSongEntries,
                        currentSong = currentSong,
                        onSongClick = { songs, index ->
                            onPlaylistSongClick(
                                songs,
                                index,
                                PlaybackSource.userPlaylist(
                                    playlistId = activePlaylistId.orEmpty(),
                                    displayName = activePlaylistTitle.orEmpty()
                                )
                            )
                        },
                        batchActions = activeBatchActions,
                        itemModifier = ::songItemModifier,
                        onCollapseProgressStateChange =
                            onDetailHeaderCollapseProgressStateChange,
                        headerModifier = elementModifier(0),
                        contentModifier = fadingContainerModifier(),
                        suppressEmptyState = secondaryPage != SecondaryPage.Playlist,
                        modifier = Modifier
                            .fillMaxSize()
                            .rightSwipeBackGesture(::closeSelectionOrPage)
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
                onSongClick = { songs, index ->
                    onPlaylistSongClick(
                        songs,
                        index,
                        PlaybackSource.artist(
                            if (secondaryPage == SecondaryPage.Artist) {
                                selectedArtistName.orEmpty()
                            } else {
                                retainedArtistName.orEmpty()
                            }
                        )
                    )
                },
                itemModifier = ::songItemModifier,
                modifier = fadingContainerModifier()
                    .fillMaxSize()
                    .rightSwipeBackGesture(onCloseSecondaryPage)
            )

            SecondaryPage.ListeningRecords -> ListeningRecordsScreen(
                listeningStats = uiState.listeningStats,
                initialTab = listeningRecordInitialTab,
                onBack = onCloseSecondaryPage,
                itemModifier = ::elementModifier,
                modifier = fadingContainerModifier()
                    .fillMaxSize()
            )

            null -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}
