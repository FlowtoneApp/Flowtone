package ink.tenqui.flowtone.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.player.QueueDisplayOrder
import ink.tenqui.flowtone.ui.player.lyrics.LyricsBackgroundStyle
import ink.tenqui.flowtone.ui.screens.ListeningRecordTab
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicUiState
import ink.tenqui.flowtone.lyrics.LyricsState

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
    artistRootNavigationModeState: MutableState<ArtistRootNavigationMode?>,
    artistRootReturnInProgressState: MutableState<Boolean>,
    selectedPlaylistIdState: MutableState<String?>,
    selectedPlaylistTitleState: MutableState<String?>,
    selectedArtistNameState: MutableState<String?>,
    listeningRecordInitialTabState: MutableState<ListeningRecordTab>,
    settingsBackActionState: MutableState<(() -> Unit)?>,
    openSourceBackActionState: MutableState<(() -> Unit)?>,
    secondaryPathSegmentsState: MutableState<List<String>>,
    hideSecondaryBackButtonState: MutableState<Boolean>,
    resumePlaybackAfterCallState: MutableState<Boolean>,
    allowFullscreenFromCollapsedState: MutableState<Boolean>,
    openExpandedMiniPlayerOnMediaClickState: MutableState<Boolean>,
    disablePausedArtworkTiltState: MutableState<Boolean>,
    strictProgressBarState: MutableState<Boolean>,
    preloadSongMetadataCountState: MutableState<Int>,
    preloadLyricsCountState: MutableState<Int>,
    songRecordThresholdSecondsState: MutableState<Int>,
    songRecordThresholdDialogStateState: MutableState<SongRecordThresholdDialogState>,
    flowCloudSpeedState: MutableState<Float>,
    lyricsBackgroundStyleState: MutableState<LyricsBackgroundStyle>,
    flowCloudSpeedDialogStateState: MutableState<FlowCloudSpeedDialogState>,
    playbackQueueDisplayOrderState: MutableState<QueueDisplayOrder>,
    likedSongKeysState: MutableState<List<String>>,
    searchActiveState: MutableState<Boolean>,
    searchEnteredPageIndexState: MutableState<Int>,
    searchFrozenAccentArgbState: MutableState<Int?>,
    searchFrozenContainerArgbState: MutableState<Int?>,
    searchFrozenContentArgbState: MutableState<Int?>,
    searchInputFocusedState: MutableState<Boolean>,
    searchKeyboardVisibleState: MutableState<Boolean>,
    searchFocusRequestState: MutableState<Int>,
    searchKeyboardDismissRequestState: MutableState<Int>,
    searchReturnStageState: MutableState<SearchReturnStage>,
    searchReturnListIndexState: MutableState<Int>,
    searchReturnListOffsetState: MutableState<Int>,
    val searchListState: LazyListState
) {
    var permissionDenied by permissionDeniedState
    var miniPlayerExpanded by miniPlayerExpandedState
    var miniPlayerFullscreen by miniPlayerFullscreenState
    var miniPlayerFullscreenEnteredFromCollapsed by miniPlayerFullscreenEnteredFromCollapsedState
    var miniPlayerMinimized by miniPlayerMinimizedState
    var showSwipeHint by showSwipeHintState
    var secondaryPage by secondaryPageState
    var artistRootPageArtistName by artistRootPageArtistNameState
    var artistRootNavigationMode by artistRootNavigationModeState
    var artistRootReturnInProgress by artistRootReturnInProgressState
    var selectedPlaylistId by selectedPlaylistIdState
    var selectedPlaylistTitle by selectedPlaylistTitleState
    var selectedArtistName by selectedArtistNameState
    var listeningRecordInitialTab by listeningRecordInitialTabState
    var settingsBackAction by settingsBackActionState
    var openSourceBackAction by openSourceBackActionState
    var secondaryPathSegments by secondaryPathSegmentsState
    var hideSecondaryBackButton by hideSecondaryBackButtonState
    var resumePlaybackAfterCall by resumePlaybackAfterCallState
    var allowFullscreenFromCollapsed by allowFullscreenFromCollapsedState
    var openExpandedMiniPlayerOnMediaClick by openExpandedMiniPlayerOnMediaClickState
    var disablePausedArtworkTilt by disablePausedArtworkTiltState
    var strictProgressBar by strictProgressBarState
    var preloadSongMetadataCount by preloadSongMetadataCountState
    var preloadLyricsCount by preloadLyricsCountState
    var songRecordThresholdSeconds by songRecordThresholdSecondsState
    var songRecordThresholdDialogState by songRecordThresholdDialogStateState
    var flowCloudSpeed by flowCloudSpeedState
    var lyricsBackgroundStyle by lyricsBackgroundStyleState
    var flowCloudSpeedDialogState by flowCloudSpeedDialogStateState
    var playbackQueueDisplayOrder by playbackQueueDisplayOrderState
    var likedSongKeys by likedSongKeysState
    var searchActive by searchActiveState
    var searchEnteredPageIndex by searchEnteredPageIndexState
    var searchFrozenAccentArgb by searchFrozenAccentArgbState
    var searchFrozenContainerArgb by searchFrozenContainerArgbState
    var searchFrozenContentArgb by searchFrozenContentArgbState
    var searchInputFocused by searchInputFocusedState
    var searchKeyboardVisible by searchKeyboardVisibleState
    var searchFocusRequest by searchFocusRequestState
    var searchKeyboardDismissRequest by searchKeyboardDismissRequestState
    var searchReturnStage by searchReturnStageState
    var searchReturnListIndex by searchReturnListIndexState
    var searchReturnListOffset by searchReturnListOffsetState
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
    val artistRootNavigationMode = rememberSaveable {
        mutableStateOf<ArtistRootNavigationMode?>(null)
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
    val listeningRecordInitialTab = rememberSaveable {
        mutableStateOf(ListeningRecordTab.Today)
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
    val openExpandedMiniPlayerOnMediaClick = rememberSaveable {
        mutableStateOf(appPreferences.shouldOpenExpandedMiniPlayerOnMediaClick())
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
    val preloadLyricsCount = rememberSaveable {
        mutableStateOf(appPreferences.getLyricsPreloadCount())
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
    val lyricsBackgroundStyle = rememberSaveable {
        mutableStateOf(appPreferences.getLyricsBackgroundStyle())
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
    val searchActive = rememberSaveable {
        mutableStateOf(false)
    }
    val searchEnteredPageIndex = rememberSaveable {
        mutableStateOf(0)
    }
    val searchFrozenAccentArgb = rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    val searchFrozenContainerArgb = rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    val searchFrozenContentArgb = rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    val searchInputFocused = remember {
        mutableStateOf(false)
    }
    val searchKeyboardVisible = remember {
        mutableStateOf(false)
    }
    val searchFocusRequest = remember {
        mutableStateOf(0)
    }
    val searchKeyboardDismissRequest = remember {
        mutableStateOf(0)
    }
    val searchReturnStage = rememberSaveable {
        mutableStateOf(SearchReturnStage.Idle)
    }
    val searchReturnListIndex = rememberSaveable {
        mutableStateOf(0)
    }
    val searchReturnListOffset = rememberSaveable {
        mutableStateOf(0)
    }
    val searchListState = rememberLazyListState()

    return FlowtoneAppState(
        permissionDeniedState = permissionDenied,
        miniPlayerExpandedState = miniPlayerExpanded,
        miniPlayerFullscreenState = miniPlayerFullscreen,
        miniPlayerFullscreenEnteredFromCollapsedState = miniPlayerFullscreenEnteredFromCollapsed,
        miniPlayerMinimizedState = miniPlayerMinimized,
        showSwipeHintState = showSwipeHint,
        secondaryPageState = secondaryPage,
        artistRootPageArtistNameState = artistRootPageArtistName,
        artistRootNavigationModeState = artistRootNavigationMode,
        artistRootReturnInProgressState = artistRootReturnInProgress,
        selectedPlaylistIdState = selectedPlaylistId,
        selectedPlaylistTitleState = selectedPlaylistTitle,
        selectedArtistNameState = selectedArtistName,
        listeningRecordInitialTabState = listeningRecordInitialTab,
        settingsBackActionState = settingsBackAction,
        openSourceBackActionState = openSourceBackAction,
        secondaryPathSegmentsState = secondaryPathSegments,
        hideSecondaryBackButtonState = hideSecondaryBackButton,
        resumePlaybackAfterCallState = resumePlaybackAfterCall,
        allowFullscreenFromCollapsedState = allowFullscreenFromCollapsed,
        openExpandedMiniPlayerOnMediaClickState = openExpandedMiniPlayerOnMediaClick,
        disablePausedArtworkTiltState = disablePausedArtworkTilt,
        strictProgressBarState = strictProgressBar,
        preloadSongMetadataCountState = preloadSongMetadataCount,
        preloadLyricsCountState = preloadLyricsCount,
        songRecordThresholdSecondsState = songRecordThresholdSeconds,
        songRecordThresholdDialogStateState = songRecordThresholdDialogState,
        flowCloudSpeedState = flowCloudSpeed,
        lyricsBackgroundStyleState = lyricsBackgroundStyle,
        flowCloudSpeedDialogStateState = flowCloudSpeedDialogState,
        playbackQueueDisplayOrderState = playbackQueueDisplayOrder,
        likedSongKeysState = likedSongKeys,
        searchActiveState = searchActive,
        searchEnteredPageIndexState = searchEnteredPageIndex,
        searchFrozenAccentArgbState = searchFrozenAccentArgb,
        searchFrozenContainerArgbState = searchFrozenContainerArgb,
        searchFrozenContentArgbState = searchFrozenContentArgb,
        searchInputFocusedState = searchInputFocused,
        searchKeyboardVisibleState = searchKeyboardVisible,
        searchFocusRequestState = searchFocusRequest,
        searchKeyboardDismissRequestState = searchKeyboardDismissRequest,
        searchReturnStageState = searchReturnStage,
        searchReturnListIndexState = searchReturnListIndex,
        searchReturnListOffsetState = searchReturnListOffset,
        searchListState = searchListState
    )
}

internal data class FlowtoneAppScaffoldState(
    val uiState: MusicUiState,
    val playerUiState: PlayerUiState,
    val lyricsState: LyricsState,
    val appPreferences: AppPreferences,
    val themeMode: AppThemeMode,
    val disablePausedArtworkTilt: Boolean,
    val strictProgressBar: Boolean,
    val pagerState: PagerState,
    val selectedTopLevelPage: TopLevelPage,
    val rootPage: FlowtoneRootPage,
    val artistRootNavigationMode: ArtistRootNavigationMode?,
    val secondaryPage: SecondaryPage?,
    val selectedPlaylistId: String?,
    val selectedPlaylistTitle: String?,
    val selectedArtistName: String?,
    val listeningRecordInitialTab: ListeningRecordTab,
    val likedSongKeys: List<String>,
    val secondaryPathSegments: List<String>,
    val hideSecondaryBackButton: Boolean,
    val resumePlaybackAfterCall: Boolean,
    val allowFullscreenFromCollapsed: Boolean,
    val openExpandedMiniPlayerOnMediaClick: Boolean,
    val preloadSongMetadataCount: Int,
    val preloadLyricsCount: Int,
    val songRecordThresholdSeconds: Int,
    val songRecordThresholdDialogState: SongRecordThresholdDialogState,
    val flowCloudSpeed: Float,
    val lyricsBackgroundStyle: LyricsBackgroundStyle,
    val flowCloudSpeedDialogState: FlowCloudSpeedDialogState,
    val playbackQueueDisplayOrder: QueueDisplayOrder,
    val permissionDenied: Boolean,
    val showSwipeHint: Boolean,
    val artistRootReturnInProgress: Boolean,
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
    val noRippleInteractionSource: MutableInteractionSource,
    val searchActive: Boolean,
    val searchUiState: GlobalSearchUiState,
    val searchColors: TopLevelSearchColors,
    val searchKeyboardVisible: Boolean,
    val searchInputFocused: Boolean,
    val searchFocusRequest: Int,
    val searchKeyboardDismissRequest: Int,
    val searchReturnStage: SearchReturnStage,
    val searchReentryProgress: Float,
    val searchListState: LazyListState
)

internal fun flowtoneAppScaffoldState(
    appState: FlowtoneAppState,
    uiState: MusicUiState,
    playerUiState: PlayerUiState,
    lyricsState: LyricsState,
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
    noRippleInteractionSource: MutableInteractionSource,
    searchUiState: GlobalSearchUiState,
    searchColors: TopLevelSearchColors,
    searchReentryProgress: Float
): FlowtoneAppScaffoldState {
    return FlowtoneAppScaffoldState(
        uiState = uiState,
        playerUiState = playerUiState,
        lyricsState = lyricsState,
        appPreferences = appPreferences,
        themeMode = themeMode,
        disablePausedArtworkTilt = appState.disablePausedArtworkTilt,
        strictProgressBar = appState.strictProgressBar,
        pagerState = pagerState,
        selectedTopLevelPage = selectedTopLevelPage,
        rootPage = rootPage,
        artistRootNavigationMode = appState.artistRootNavigationMode,
        secondaryPage = appState.secondaryPage,
        selectedPlaylistId = appState.selectedPlaylistId,
        selectedPlaylistTitle = appState.selectedPlaylistTitle,
        selectedArtistName = appState.selectedArtistName,
        listeningRecordInitialTab = appState.listeningRecordInitialTab,
        likedSongKeys = appState.likedSongKeys,
        secondaryPathSegments = appState.secondaryPathSegments,
        hideSecondaryBackButton = appState.hideSecondaryBackButton,
        resumePlaybackAfterCall = appState.resumePlaybackAfterCall,
        allowFullscreenFromCollapsed = appState.allowFullscreenFromCollapsed,
        openExpandedMiniPlayerOnMediaClick = appState.openExpandedMiniPlayerOnMediaClick,
        preloadSongMetadataCount = appState.preloadSongMetadataCount,
        preloadLyricsCount = appState.preloadLyricsCount,
        songRecordThresholdSeconds = appState.songRecordThresholdSeconds,
        songRecordThresholdDialogState = appState.songRecordThresholdDialogState,
        flowCloudSpeed = appState.flowCloudSpeed,
        lyricsBackgroundStyle = appState.lyricsBackgroundStyle,
        flowCloudSpeedDialogState = appState.flowCloudSpeedDialogState,
        playbackQueueDisplayOrder = appState.playbackQueueDisplayOrder,
        permissionDenied = appState.permissionDenied,
        showSwipeHint = appState.showSwipeHint,
        artistRootReturnInProgress = appState.artistRootReturnInProgress,
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
        noRippleInteractionSource = noRippleInteractionSource,
        searchActive = appState.searchActive,
        searchUiState = searchUiState,
        searchColors = searchColors,
        searchKeyboardVisible = appState.searchKeyboardVisible,
        searchInputFocused = appState.searchInputFocused,
        searchFocusRequest = appState.searchFocusRequest,
        searchKeyboardDismissRequest = appState.searchKeyboardDismissRequest,
        searchReturnStage = appState.searchReturnStage,
        searchReentryProgress = searchReentryProgress,
        searchListState = appState.searchListState
    )
}
