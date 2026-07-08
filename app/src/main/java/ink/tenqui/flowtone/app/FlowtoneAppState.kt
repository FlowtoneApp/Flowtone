package ink.tenqui.flowtone.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.player.QueueDisplayOrder
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicUiState

internal enum class SongRecordThresholdDialogState {
    Idle,
    Editing,
    Closing
}

internal enum class FlowCloudSpeedDialogState {
    Idle,
    Editing,
    Closing
}

internal class FlowtoneAppState(
    permissionDeniedState: MutableState<Boolean>,
    miniPlayerExpandedState: MutableState<Boolean>,
    miniPlayerFullscreenState: MutableState<Boolean>,
    miniPlayerFullscreenEnteredFromCollapsedState: MutableState<Boolean>,
    miniPlayerMinimizedState: MutableState<Boolean>,
    showSwipeHintState: MutableState<Boolean>,
    secondaryPageState: MutableState<SecondaryPage?>,
    artistRootPageArtistNameState: MutableState<String?>,
    artistRootReturnInProgressState: MutableState<Boolean>,
    selectedPlaylistIdState: MutableState<String?>,
    selectedPlaylistTitleState: MutableState<String?>,
    selectedArtistNameState: MutableState<String?>,
    settingsBackActionState: MutableState<(() -> Unit)?>,
    openSourceBackActionState: MutableState<(() -> Unit)?>,
    secondaryPathSegmentsState: MutableState<List<String>>,
    hideSecondaryBackButtonState: MutableState<Boolean>,
    resumePlaybackAfterCallState: MutableState<Boolean>,
    allowFullscreenFromCollapsedState: MutableState<Boolean>,
    disablePausedArtworkTiltState: MutableState<Boolean>,
    strictProgressBarState: MutableState<Boolean>,
    preloadSongMetadataCountState: MutableState<Int>,
    songRecordThresholdSecondsState: MutableState<Int>,
    songRecordThresholdDialogStateState: MutableState<SongRecordThresholdDialogState>,
    flowCloudSpeedState: MutableState<Float>,
    flowCloudSpeedDialogStateState: MutableState<FlowCloudSpeedDialogState>,
    playbackQueueDisplayOrderState: MutableState<QueueDisplayOrder>,
    likedSongKeysState: MutableState<List<String>>
) {
    var permissionDenied by permissionDeniedState
    var miniPlayerExpanded by miniPlayerExpandedState
    var miniPlayerFullscreen by miniPlayerFullscreenState
    var miniPlayerFullscreenEnteredFromCollapsed by miniPlayerFullscreenEnteredFromCollapsedState
    var miniPlayerMinimized by miniPlayerMinimizedState
    var showSwipeHint by showSwipeHintState
    var secondaryPage by secondaryPageState
    var artistRootPageArtistName by artistRootPageArtistNameState
    var artistRootReturnInProgress by artistRootReturnInProgressState
    var selectedPlaylistId by selectedPlaylistIdState
    var selectedPlaylistTitle by selectedPlaylistTitleState
    var selectedArtistName by selectedArtistNameState
    var settingsBackAction by settingsBackActionState
    var openSourceBackAction by openSourceBackActionState
    var secondaryPathSegments by secondaryPathSegmentsState
    var hideSecondaryBackButton by hideSecondaryBackButtonState
    var resumePlaybackAfterCall by resumePlaybackAfterCallState
    var allowFullscreenFromCollapsed by allowFullscreenFromCollapsedState
    var disablePausedArtworkTilt by disablePausedArtworkTiltState
    var strictProgressBar by strictProgressBarState
    var preloadSongMetadataCount by preloadSongMetadataCountState
    var songRecordThresholdSeconds by songRecordThresholdSecondsState
    var songRecordThresholdDialogState by songRecordThresholdDialogStateState
    var flowCloudSpeed by flowCloudSpeedState
    var flowCloudSpeedDialogState by flowCloudSpeedDialogStateState
    var playbackQueueDisplayOrder by playbackQueueDisplayOrderState
    var likedSongKeys by likedSongKeysState
}

@Composable
internal fun rememberFlowtoneAppState(appPreferences: AppPreferences): FlowtoneAppState {
    val permissionDenied = remember {
        mutableStateOf(false)
    }
    val miniPlayerExpanded = rememberSaveable {
        mutableStateOf(false)
    }
    val miniPlayerFullscreen = rememberSaveable {
        mutableStateOf(false)
    }
    val miniPlayerFullscreenEnteredFromCollapsed = rememberSaveable {
        mutableStateOf(false)
    }
    val miniPlayerMinimized = rememberSaveable {
        mutableStateOf(false)
    }
    val showSwipeHint = rememberSaveable {
        mutableStateOf(true)
    }
    val secondaryPage = rememberSaveable {
        mutableStateOf<SecondaryPage?>(null)
    }
    val artistRootPageArtistName = rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val artistRootReturnInProgress = remember {
        mutableStateOf(false)
    }
    val selectedPlaylistId = rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedPlaylistTitle = rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedArtistName = rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val settingsBackAction = remember {
        mutableStateOf<(() -> Unit)?>(null)
    }
    val openSourceBackAction = remember {
        mutableStateOf<(() -> Unit)?>(null)
    }
    val secondaryPathSegments = remember {
        mutableStateOf(emptyList<String>())
    }
    val hideSecondaryBackButton = rememberSaveable {
        mutableStateOf(appPreferences.shouldHideSecondaryBackButton())
    }
    val resumePlaybackAfterCall = rememberSaveable {
        mutableStateOf(appPreferences.shouldResumePlaybackAfterCall())
    }
    val allowFullscreenFromCollapsed = rememberSaveable {
        mutableStateOf(appPreferences.shouldAllowFullscreenFromCollapsed())
    }
    val disablePausedArtworkTilt = rememberSaveable {
        mutableStateOf(appPreferences.shouldDisablePausedArtworkTilt())
    }
    val strictProgressBar = rememberSaveable {
        mutableStateOf(appPreferences.shouldUseStrictProgressBar())
    }
    val preloadSongMetadataCount = rememberSaveable {
        mutableStateOf(appPreferences.getSongMetadataPreloadCount())
    }
    val songRecordThresholdSeconds = rememberSaveable {
        mutableStateOf(appPreferences.getSongRecordThresholdSeconds())
    }
    val songRecordThresholdDialogState = rememberSaveable {
        mutableStateOf(SongRecordThresholdDialogState.Idle)
    }
    val flowCloudSpeed = rememberSaveable {
        mutableStateOf(appPreferences.getFlowCloudSpeed())
    }
    val flowCloudSpeedDialogState = rememberSaveable {
        mutableStateOf(FlowCloudSpeedDialogState.Idle)
    }
    val playbackQueueDisplayOrder = rememberSaveable {
        mutableStateOf(appPreferences.getPlaybackQueueDisplayOrder())
    }
    val likedSongKeys = rememberSaveable {
        mutableStateOf(emptyList<String>())
    }

    return FlowtoneAppState(
        permissionDeniedState = permissionDenied,
        miniPlayerExpandedState = miniPlayerExpanded,
        miniPlayerFullscreenState = miniPlayerFullscreen,
        miniPlayerFullscreenEnteredFromCollapsedState = miniPlayerFullscreenEnteredFromCollapsed,
        miniPlayerMinimizedState = miniPlayerMinimized,
        showSwipeHintState = showSwipeHint,
        secondaryPageState = secondaryPage,
        artistRootPageArtistNameState = artistRootPageArtistName,
        artistRootReturnInProgressState = artistRootReturnInProgress,
        selectedPlaylistIdState = selectedPlaylistId,
        selectedPlaylistTitleState = selectedPlaylistTitle,
        selectedArtistNameState = selectedArtistName,
        settingsBackActionState = settingsBackAction,
        openSourceBackActionState = openSourceBackAction,
        secondaryPathSegmentsState = secondaryPathSegments,
        hideSecondaryBackButtonState = hideSecondaryBackButton,
        resumePlaybackAfterCallState = resumePlaybackAfterCall,
        allowFullscreenFromCollapsedState = allowFullscreenFromCollapsed,
        disablePausedArtworkTiltState = disablePausedArtworkTilt,
        strictProgressBarState = strictProgressBar,
        preloadSongMetadataCountState = preloadSongMetadataCount,
        songRecordThresholdSecondsState = songRecordThresholdSeconds,
        songRecordThresholdDialogStateState = songRecordThresholdDialogState,
        flowCloudSpeedState = flowCloudSpeed,
        flowCloudSpeedDialogStateState = flowCloudSpeedDialogState,
        playbackQueueDisplayOrderState = playbackQueueDisplayOrder,
        likedSongKeysState = likedSongKeys
    )
}

internal data class FlowtoneAppScaffoldState(
    val uiState: MusicUiState,
    val playerUiState: PlayerUiState,
    val appPreferences: AppPreferences,
    val themeMode: AppThemeMode,
    val disablePausedArtworkTilt: Boolean,
    val strictProgressBar: Boolean,
    val pagerState: PagerState,
    val selectedTopLevelPage: TopLevelPage,
    val rootPage: FlowtoneRootPage,
    val secondaryPage: SecondaryPage?,
    val selectedPlaylistId: String?,
    val selectedArtistName: String?,
    val likedSongKeys: List<String>,
    val secondaryPathSegments: List<String>,
    val hideSecondaryBackButton: Boolean,
    val resumePlaybackAfterCall: Boolean,
    val allowFullscreenFromCollapsed: Boolean,
    val preloadSongMetadataCount: Int,
    val songRecordThresholdSeconds: Int,
    val songRecordThresholdDialogState: SongRecordThresholdDialogState,
    val flowCloudSpeed: Float,
    val flowCloudSpeedDialogState: FlowCloudSpeedDialogState,
    val playbackQueueDisplayOrder: QueueDisplayOrder,
    val permissionDenied: Boolean,
    val showSwipeHint: Boolean,
    val secondaryOpen: Boolean,
    val topBarBackgroundAlpha: Float,
    val topBarScrollConnection: NestedScrollConnection,
    val backgroundBlurRadius: Dp,
    val backgroundBlurProgress: Float,
    val miniPlayerContentBottomPadding: Dp,
    val miniPlayerBottomProtection: Dp,
    val miniPlayerExpanded: Boolean,
    val miniPlayerFullscreen: Boolean,
    val miniPlayerMinimized: Boolean,
    val noRippleInteractionSource: MutableInteractionSource
)

internal fun flowtoneAppScaffoldState(
    appState: FlowtoneAppState,
    uiState: MusicUiState,
    playerUiState: PlayerUiState,
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    pagerState: PagerState,
    selectedTopLevelPage: TopLevelPage,
    rootPage: FlowtoneRootPage,
    secondaryOpen: Boolean,
    topBarBackgroundAlpha: Float,
    topBarScrollConnection: NestedScrollConnection,
    backgroundBlurRadius: Dp,
    backgroundBlurProgress: Float,
    miniPlayerContentBottomPadding: Dp,
    miniPlayerBottomProtection: Dp,
    noRippleInteractionSource: MutableInteractionSource
): FlowtoneAppScaffoldState {
    return FlowtoneAppScaffoldState(
        uiState = uiState,
        playerUiState = playerUiState,
        appPreferences = appPreferences,
        themeMode = themeMode,
        disablePausedArtworkTilt = appState.disablePausedArtworkTilt,
        strictProgressBar = appState.strictProgressBar,
        pagerState = pagerState,
        selectedTopLevelPage = selectedTopLevelPage,
        rootPage = rootPage,
        secondaryPage = appState.secondaryPage,
        selectedPlaylistId = appState.selectedPlaylistId,
        selectedArtistName = appState.selectedArtistName,
        likedSongKeys = appState.likedSongKeys,
        secondaryPathSegments = appState.secondaryPathSegments,
        hideSecondaryBackButton = appState.hideSecondaryBackButton,
        resumePlaybackAfterCall = appState.resumePlaybackAfterCall,
        allowFullscreenFromCollapsed = appState.allowFullscreenFromCollapsed,
        preloadSongMetadataCount = appState.preloadSongMetadataCount,
        songRecordThresholdSeconds = appState.songRecordThresholdSeconds,
        songRecordThresholdDialogState = appState.songRecordThresholdDialogState,
        flowCloudSpeed = appState.flowCloudSpeed,
        flowCloudSpeedDialogState = appState.flowCloudSpeedDialogState,
        playbackQueueDisplayOrder = appState.playbackQueueDisplayOrder,
        permissionDenied = appState.permissionDenied,
        showSwipeHint = appState.showSwipeHint,
        secondaryOpen = secondaryOpen,
        topBarBackgroundAlpha = topBarBackgroundAlpha,
        topBarScrollConnection = topBarScrollConnection,
        backgroundBlurRadius = backgroundBlurRadius,
        backgroundBlurProgress = backgroundBlurProgress,
        miniPlayerContentBottomPadding = miniPlayerContentBottomPadding,
        miniPlayerBottomProtection = miniPlayerBottomProtection,
        miniPlayerExpanded = appState.miniPlayerExpanded,
        miniPlayerFullscreen = appState.miniPlayerFullscreen,
        miniPlayerMinimized = appState.miniPlayerMinimized,
        noRippleInteractionSource = noRippleInteractionSource
    )
}
