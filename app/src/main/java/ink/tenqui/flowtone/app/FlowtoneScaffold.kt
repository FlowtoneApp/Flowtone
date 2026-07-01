package ink.tenqui.flowtone.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.player.MiniPlayer
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.player.QueueDisplayOrder
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicUiState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FlowtoneScaffold(
    uiState: MusicUiState,
    playerUiState: PlayerUiState,
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    pagerState: PagerState,
    selectedTopLevelPage: TopLevelPage,
    secondaryPage: SecondaryPage?,
    secondaryPathSegments: List<String>,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    songRecordThresholdSeconds: Int,
    onSongRecordThresholdSecondsChange: (Int) -> Unit,
    playbackQueueDisplayOrder: QueueDisplayOrder,
    onPlaybackQueueDisplayOrderChange: (QueueDisplayOrder) -> Unit,
    settingsBackActionChange: ((() -> Unit)?) -> Unit,
    onSettingsPathSegmentsChange: (List<String>) -> Unit,
    openSourceBackActionChange: ((() -> Unit)?) -> Unit,
    onOpenSourcePathSegmentsChange: (List<String>) -> Unit,
    permissionDenied: Boolean,
    showSwipeHint: Boolean,
    secondaryOpen: Boolean,
    topBarBackgroundAlpha: Float,
    topBarScrollConnection: NestedScrollConnection,
    backgroundBlurRadius: Dp,
    backgroundBlurProgress: Float,
    miniPlayerContentBottomPadding: Dp,
    miniPlayerBottomProtection: Dp,
    miniPlayerExpanded: Boolean,
    miniPlayerFullscreen: Boolean,
    miniPlayerMinimized: Boolean,
    noRippleInteractionSource: MutableInteractionSource,
    onNavigateBack: () -> Unit,
    onCloseSecondaryPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLocalLibrary: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSourceBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onDismissExpandedPlayer: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onMinimizedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    onPlayQueueSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasCurrentSong = playerUiState.hasCurrentSong

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlurRadius),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                FlowtoneTopBar(
                    selectedTopLevelPage = selectedTopLevelPage,
                    pagerState = pagerState,
                    secondaryPage = secondaryPage,
                    additionalPathSegments = secondaryPathSegments,
                    backgroundAlpha = topBarBackgroundAlpha,
                    hideBackButton = hideSecondaryBackButton,
                    onBack = onNavigateBack
                )
            }
        ) { innerPadding ->
            SharedTransitionLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarScrollConnection)
                    .padding(innerPadding)
                    .padding(bottom = miniPlayerContentBottomPadding)
            ) {
                val sharedTransitionScope = this
                Box(modifier = Modifier.fillMaxSize()) {
                    TopLevelPagerContent(
                        pagerState = pagerState,
                        uiState = uiState,
                        playerUiState = playerUiState,
                        permissionDenied = permissionDenied,
                        showSwipeHint = showSwipeHint,
                        secondaryOpen = secondaryOpen,
                        onRequestPermission = onRequestPermission,
                        onSongClick = onSongClick,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onOpenLocalLibrary = onOpenLocalLibrary,
                        modifier = Modifier.fillMaxSize()
                    )
                    SecondaryPageHost(
                        secondaryPage = secondaryPage,
                        appPreferences = appPreferences,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        hideSecondaryBackButton = hideSecondaryBackButton,
                        onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                        resumePlaybackAfterCall = resumePlaybackAfterCall,
                        onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                        allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                        onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                        preloadSongMetadataCount = preloadSongMetadataCount,
                        onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                        songRecordThresholdSeconds = songRecordThresholdSeconds,
                        onSongRecordThresholdSecondsChange = onSongRecordThresholdSecondsChange,
                        uiState = uiState,
                        currentSong = playerUiState.currentSong,
                        permissionDenied = permissionDenied,
                        onRequestPermission = onRequestPermission,
                        onSongClick = onSongClick,
                        onCloseSecondaryPage = onCloseSecondaryPage,
                        onSettingsBackActionChange = settingsBackActionChange,
                        onSettingsPathSegmentsChange = onSettingsPathSegmentsChange,
                        onOpenSource = onOpenSource,
                        onOpenSourceBack = onOpenSourceBack,
                        onOpenSourceBackActionChange = openSourceBackActionChange,
                        onOpenSourcePathSegmentsChange = onOpenSourcePathSegmentsChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (hasCurrentSong && backgroundBlurProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f * backgroundBlurProgress))
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = onDismissExpandedPlayer
                    )
            )
        }
        MiniPlayer(
            playerUiState = playerUiState,
            expanded = miniPlayerExpanded,
            onExpandedChange = onExpandedChange,
            fullscreen = miniPlayerFullscreen,
            onFullscreenChange = onFullscreenChange,
            fullscreenHeight = maxHeight - miniPlayerBottomProtection,
            allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
            allowFullscreenFromExpanded = true,
            minimized = miniPlayerMinimized,
            onMinimizedChange = onMinimizedChange,
            onTogglePlayPause = onTogglePlayPause,
            onPlayPrevious = onPlayPrevious,
            onPlayNext = onPlayNext,
            onSeekTo = onSeekTo,
            onTogglePlaybackOrderMode = onTogglePlaybackOrderMode,
            sourceQueue = uiState.sourceQueue,
            playbackQueue = uiState.playbackQueue,
            currentQueueIndex = uiState.currentQueueIndex,
            queueDisplayOrder = playbackQueueDisplayOrder,
            onQueueDisplayOrderChange = onPlaybackQueueDisplayOrderChange,
            onPlayQueueSong = onPlayQueueSong,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = miniPlayerBottomProtection)
        )
    }
}
