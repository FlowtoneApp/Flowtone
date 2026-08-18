package ink.tenqui.flowtone.app

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.LikedSongsStore
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.permissions.currentAudioPermission
import ink.tenqui.flowtone.permissions.hasAudioPermission
import ink.tenqui.flowtone.permissions.openAppPermissionSettings
import ink.tenqui.flowtone.permissions.shouldOpenAudioPermissionSettings
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.screens.AudioPermissionGateScreen
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PendingSongDeletion(
    val songs: List<Song>,
    val onResult: (Boolean) -> Unit
)

@Composable
fun FlowtoneApp(
    musicViewModel: MusicViewModel = viewModel(),
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    openExpandedPlayerRequest: Int = 0,
    onOpenExpandedPlayerRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val uiState by musicViewModel.uiState.collectAsState()
    val playbackState by musicViewModel.playbackState.collectAsState()
    val songLyricsState by musicViewModel.songLyricsState.collectAsState()
    val searchUiState by musicViewModel.searchUiState.collectAsState()
    val playerUiState = PlayerUiState.from(playbackState)
    val appPreferences = remember(context) {
        AppPreferences(context.applicationContext)
    }
    val likedSongsStore = remember(context) {
        LikedSongsStore(context.applicationContext)
    }
    val defaultStartPage = remember(appPreferences) {
        appPreferences.getDefaultStartPage()
    }
    val appState = rememberFlowtoneAppState(appPreferences)
    val coroutineScope = rememberCoroutineScope()
    val permissionActivity = context as? Activity
    var audioPermissionGranted by remember(context) {
        mutableStateOf(hasAudioPermission(context))
    }
    var mainTabsVisible by remember {
        mutableStateOf(audioPermissionGranted)
    }
    var hasShownPermissionGate by remember {
        mutableStateOf(!audioPermissionGranted)
    }
    var permissionRequestResultVersion by remember { mutableStateOf(0) }
    var pendingSongDeletion by remember { mutableStateOf<PendingSongDeletion?>(null) }

    val pagerState = rememberPagerState(
        initialPage = defaultStartPage.index,
        pageCount = { TopLevelPage.entries.size }
    )
    val selectedTopLevelPage = TopLevelPage.entries[pagerState.currentPage]
    val rootPage = flowtoneRootPage(appState.artistRootPageArtistName)
    val secondaryOpen = appState.secondaryPage != null
    val liveSearchColors = topLevelSearchColorsForPager(pagerState)
    val frozenSearchColors = searchColorSnapshotOrNull(appState)?.toColors()
    val activeSearchColors = frozenSearchColors ?: liveSearchColors
    val topBarRevealDistancePx = with(density) { 24.dp.toPx() }
    var mainContentScrollOffsetPx by remember {
        mutableStateOf(0f)
    }
    var searchReturnJob by remember {
        mutableStateOf<Job?>(null)
    }
    val mainTopBarBackgroundAlpha by animateFloatAsState(
        targetValue = (mainContentScrollOffsetPx / topBarRevealDistancePx).coerceIn(0f, 1f),
        animationSpec = tween(160, easing = FlowtonePageEasing),
        label = "MainTopBarBackgroundAlpha"
    )
    val searchHasScrollableContent = appState.searchActive &&
        !searchUiState.isEmptyQuery &&
        !searchUiState.hasNoResults
    val searchTopBarScrollOffsetPx = if (!searchHasScrollableContent) {
        0f
    } else if (appState.searchListState.firstVisibleItemIndex > 0) {
        topBarRevealDistancePx
    } else {
        appState.searchListState.firstVisibleItemScrollOffset.toFloat()
    }
    val searchTopBarBackgroundAlpha by animateFloatAsState(
        targetValue = (searchTopBarScrollOffsetPx / topBarRevealDistancePx).coerceIn(0f, 1f),
        animationSpec = tween(160, easing = FlowtonePageEasing),
        label = "SearchTopBarBackgroundAlpha"
    )
    val activeTopBarBackgroundAlpha = if (appState.searchActive) {
        searchTopBarBackgroundAlpha
    } else {
        mainTopBarBackgroundAlpha
    }
    val searchReentryProgress by animateFloatAsState(
        targetValue = when (appState.searchReturnStage) {
            SearchReturnStage.SearchExitingForArtist,
            SearchReturnStage.ArtistVisible,
            SearchReturnStage.ArtistExitingToSearch,
            SearchReturnStage.SearchPreparing -> 0f
            SearchReturnStage.Idle,
            SearchReturnStage.SearchReentering -> 1f
        },
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "SearchReentryProgress",
        finishedListener = { finalValue ->
            if (
                finalValue == 1f &&
                appState.searchReturnStage == SearchReturnStage.SearchReentering
            ) {
                appState.searchReturnStage = SearchReturnStage.Idle
            }
        }
    )
    val topBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (appState.searchActive || appState.artistRootPageArtistName != null) {
                    return Offset.Zero
                }
                mainContentScrollOffsetPx =
                    (mainContentScrollOffsetPx - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }

    val hasCurrentSong = playerUiState.hasCurrentSong
    val miniPlayerBackgroundBlurActive = isMiniPlayerBackgroundBlurActive(
        hasCurrentSong = hasCurrentSong,
        miniPlayerExpanded = appState.miniPlayerExpanded,
        miniPlayerFullscreen = appState.miniPlayerFullscreen
    )
    val backgroundBlurProgress by animateFloatAsState(
        targetValue = if (miniPlayerBackgroundBlurActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerBackgroundBlurProgress"
    )
    val backgroundBlurRadius by animateDpAsState(
        targetValue = if (miniPlayerBackgroundBlurActive) 12.dp else 0.dp,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerBackgroundBlur"
    )
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val navMode = remember(context, configuration) {
        resolveFlowtoneNavigationMode(context)
    }
    val isThreeButtonNavigation = isThreeButtonNavigationMode(navMode)
    val isDebuggable = remember(context) {
        isDebuggableApplication(context)
    }
    val miniPlayerBottomProtection = with(density) {
        val tappableBottom = WindowInsets.tappableElement.getBottom(this)
        val navigationBottom = WindowInsets.navigationBars.getBottom(this)
        miniPlayerBottomProtectionPx(
            navMode = navMode,
            isThreeButtonNavigation = isThreeButtonNavigation,
            navigationBottom = navigationBottom,
            tappableBottom = tappableBottom,
            isDebuggable = isDebuggable
        ).toDp()
    }
    val miniPlayerContentBottomPadding by animateDpAsState(
        targetValue = miniPlayerContentBottomPaddingTarget(
            hasCurrentSong = hasCurrentSong,
            miniPlayerMinimized = appState.miniPlayerMinimized,
            miniPlayerBottomProtection = miniPlayerBottomProtection
        ),
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerContentBottomPadding"
    )
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioPermissionGranted = granted
        appState.permissionDenied = !granted
        permissionRequestResultVersion += 1
    }
    val deleteSongsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pendingDeletion = pendingSongDeletion ?: return@rememberLauncherForActivityResult
        pendingSongDeletion = null
        val deleted = result.resultCode == Activity.RESULT_OK
        if (deleted) {
            musicViewModel.handleLocalSongsDeleted(pendingDeletion.songs)
        }
        pendingDeletion.onResult(deleted)
    }
    val requestSongDeletion: (List<Song>, (Boolean) -> Unit) -> Unit = { songs, onResult ->
        if (songs.isEmpty() || pendingSongDeletion != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(false)
        } else {
            val deleteRequest = runCatching {
                MediaStore.createDeleteRequest(
                    context.contentResolver,
                    songs.map(Song::uri).distinct()
                )
            }.getOrNull()
            if (deleteRequest == null) {
                onResult(false)
            } else {
                pendingSongDeletion = PendingSongDeletion(
                    songs = songs,
                    onResult = onResult
                )
                deleteSongsLauncher.launch(
                    IntentSenderRequest.Builder(deleteRequest.intentSender).build()
                )
            }
        }
    }
    val permissionGateRequiresSettings = remember(
        audioPermissionGranted,
        permissionRequestResultVersion
    ) {
        !audioPermissionGranted && shouldOpenAudioPermissionSettings(
            activity = permissionActivity,
            hasRequestedPermissionBefore = appPreferences.hasRequestedAudioPermission()
        )
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                audioPermissionGranted = hasAudioPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(audioPermissionGranted) {
        musicViewModel.setPermissionStatus(audioPermissionGranted)
        if (audioPermissionGranted) {
            musicViewModel.scanSongs()
            if (hasShownPermissionGate) {
                mainTabsVisible = false
                withFrameNanos { }
                mainTabsVisible = true
            }
        } else {
            hasShownPermissionGate = true
            mainTabsVisible = false
        }
    }

    val navigateBack: () -> Unit = {
        navigateFlowtoneAppBack(appState)
    }
    fun clearFrozenSearchColors() {
        appState.searchFrozenAccentArgb = null
        appState.searchFrozenContainerArgb = null
        appState.searchFrozenContentArgb = null
    }
    fun enterSearchMode() {
        if (appState.searchActive) {
            return
        }
        val snapshot = liveSearchColors.snapshot()
        val enteredPageIndex = pagerState.currentPage.coerceIn(0, TopLevelPage.entries.lastIndex)
        appState.searchEnteredPageIndex = enteredPageIndex
        appState.searchFrozenAccentArgb = snapshot.accentArgb
        appState.searchFrozenContainerArgb = snapshot.containerArgb
        appState.searchFrozenContentArgb = snapshot.contentArgb
        appState.searchActive = true
        appState.searchFocusRequest += 1
        appState.searchKeyboardDismissRequest = 0
        coroutineScope.launch {
            pagerState.scrollToPage(enteredPageIndex)
        }
    }
    fun exitSearchMode() {
        if (!appState.searchActive) {
            return
        }
        if (isSearchReturnAnimationStage(appState.searchReturnStage)) {
            return
        }
        val restorePageIndex = appState.searchEnteredPageIndex
            .coerceIn(0, TopLevelPage.entries.lastIndex)
        appState.searchActive = false
        appState.searchInputFocused = false
        appState.searchKeyboardVisible = false
        appState.searchFocusRequest = 0
        appState.searchKeyboardDismissRequest = 0
        searchReturnJob?.cancel()
        searchReturnJob = null
        appState.searchReturnStage = SearchReturnStage.Idle
        appState.searchReturnListIndex = 0
        appState.searchReturnListOffset = 0
        clearFrozenSearchColors()
        musicViewModel.clearSearchQuery()
        coroutineScope.launch {
            pagerState.scrollToPage(restorePageIndex)
        }
    }
    fun setSongLiked(song: Song, liked: Boolean) {
        val nextKeys = nextLikedSongKeys(
            song = song,
            liked = liked,
            currentKeys = appState.likedSongKeys
        )

        if (nextKeys != appState.likedSongKeys) {
            appState.likedSongKeys = nextKeys
            likedSongsStore.saveLikedSongKeys(nextKeys)
        }
    }
    fun toggleSongLiked(song: Song) {
        setSongLiked(song, !isSongLiked(song, appState.likedSongKeys))
    }
    fun setSongsLiked(songs: List<Song>, liked: Boolean) {
        val nextKeys = songs.fold(appState.likedSongKeys) { keys, song ->
            nextLikedSongKeys(song = song, liked = liked, currentKeys = keys)
        }
        if (nextKeys != appState.likedSongKeys) {
            appState.likedSongKeys = nextKeys
            likedSongsStore.saveLikedSongKeys(nextKeys)
        }
    }
    val exitMiniPlayerFullscreen: () -> Unit = {
        appState.miniPlayerFullscreen = false
        if (appState.miniPlayerFullscreenEnteredFromCollapsed) {
            appState.miniPlayerExpanded = false
            appState.miniPlayerMinimized = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
        }
    }
    fun clearArtistRootPage() {
        appState.artistRootPageArtistName = null
        appState.artistRootNavigationMode = null
        appState.artistRootReturnInProgress = false
    }
    fun collapseMiniPlayerToRevealArtistRootPage() {
        appState.miniPlayerFullscreen = false
        appState.miniPlayerExpanded = false
        appState.miniPlayerFullscreenEnteredFromCollapsed = false
        appState.miniPlayerMinimized = false
    }
    suspend fun awaitSearchListPosition(savedPosition: SearchListPosition) {
        repeat(8) {
            withFrameNanos { }
            val firstLaidOutIndex = appState.searchListState
                .layoutInfo
                .visibleItemsInfo
                .firstOrNull()
                ?.index
            if (
                appState.searchListState.firstVisibleItemIndex ==
                savedPosition.firstVisibleItemIndex &&
                firstLaidOutIndex == savedPosition.firstVisibleItemIndex
            ) {
                return
            }
        }
    }
    fun openArtistRootPage(
        artistName: String,
        navigationMode: ArtistRootNavigationMode
    ) {
        val displayArtist = artistName.trim()
        if (displayArtist.isBlank()) {
            return
        }

        if (navigationMode == ArtistRootNavigationMode.MiniPlayer) {
            when (
                miniPlayerArtistOpenDecision(
                    currentArtistName = appState.artistRootPageArtistName,
                    targetArtistName = displayArtist,
                    searchReturnStage = appState.searchReturnStage,
                    artistRootReturnInProgress = appState.artistRootReturnInProgress
                )
            ) {
                MiniPlayerArtistOpenDecision.CollapseMiniPlayer -> {
                    collapseMiniPlayerToRevealArtistRootPage()
                    return
                }
                MiniPlayerArtistOpenDecision.Ignore -> return
                MiniPlayerArtistOpenDecision.OpenArtistPage -> Unit
            }
            appState.artistRootReturnInProgress = false
            appState.artistRootPageArtistName = displayArtist
            appState.artistRootNavigationMode = navigationMode
            collapseMiniPlayerToRevealArtistRootPage()
            return
        }

        if (appState.searchActive) {
            if (appState.searchReturnStage != SearchReturnStage.Idle) {
                return
            }
            appState.searchReturnListIndex = appState.searchListState.firstVisibleItemIndex
            appState.searchReturnListOffset = appState.searchListState.firstVisibleItemScrollOffset
            appState.searchInputFocused = false
            appState.searchFocusRequest = 0
            appState.searchKeyboardDismissRequest += 1
            appState.searchReturnStage = SearchReturnStage.SearchExitingForArtist
            searchReturnJob?.cancel()
            searchReturnJob = coroutineScope.launch {
                delay(FlowtoneMotion.DurationMillis.toLong())
                if (appState.searchReturnStage != SearchReturnStage.SearchExitingForArtist) {
                    return@launch
                }
                appState.artistRootReturnInProgress = false
                appState.artistRootPageArtistName = displayArtist
                appState.artistRootNavigationMode = navigationMode
                appState.searchReturnStage = SearchReturnStage.ArtistVisible
            }
            return
        }

        appState.artistRootReturnInProgress = false
        appState.artistRootPageArtistName = displayArtist
        appState.artistRootNavigationMode = navigationMode
        appState.searchReturnStage = SearchReturnStage.Idle
    }
    fun closeArtistRootPageAsNormalPage() {
        if (
            appState.artistRootPageArtistName == null ||
            appState.artistRootReturnInProgress ||
            isSearchReturnAnimationStage(appState.searchReturnStage)
        ) {
            return
        }
        if (
            shouldRestoreSearchAfterArtistClose(
                searchActive = appState.searchActive,
                navigationMode = appState.artistRootNavigationMode,
                currentStage = appState.searchReturnStage
            )
        ) {
            val savedPosition = SearchListPosition(
                firstVisibleItemIndex = appState.searchReturnListIndex,
                firstVisibleItemScrollOffset = appState.searchReturnListOffset
            )
            searchReturnJob?.cancel()
            searchReturnJob = coroutineScope.launch {
                appState.searchReturnStage = SearchReturnStage.ArtistExitingToSearch
                delay(FlowtoneMotion.DurationMillis.toLong())
                appState.artistRootPageArtistName = null
                appState.artistRootNavigationMode = null
                appState.artistRootReturnInProgress = false
                appState.searchReturnStage = searchReturnStageAfterArtistExit(
                    appState.searchReturnStage
                )
                runCatching {
                    appState.searchListState.scrollToItem(
                        savedPosition.firstVisibleItemIndex,
                        savedPosition.firstVisibleItemScrollOffset
                    )
                }
                awaitSearchListPosition(savedPosition)
                appState.searchReturnStage = searchReturnStageAfterPositionRestored(
                    appState.searchReturnStage
                )
            }
            return
        }
        appState.searchReturnStage = SearchReturnStage.ArtistExitingToSearch
        searchReturnJob?.cancel()
        searchReturnJob = coroutineScope.launch {
            delay(FlowtoneMotion.DurationMillis.toLong())
            clearArtistRootPage()
            appState.searchReturnStage = SearchReturnStage.Idle
        }
    }
    fun closeArtistRootPageThroughMiniPlayer() {
        if (appState.artistRootPageArtistName == null || appState.artistRootReturnInProgress) {
            return
        }
        if (!hasCurrentSong) {
            clearArtistRootPage()
            return
        }
        if (appState.miniPlayerFullscreen) {
            appState.miniPlayerExpanded = true
            appState.miniPlayerMinimized = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
            clearArtistRootPage()
            return
        }

        appState.artistRootReturnInProgress = true
        appState.miniPlayerFullscreenEnteredFromCollapsed = false
        appState.miniPlayerExpanded = true
        appState.miniPlayerMinimized = false
        appState.miniPlayerFullscreen = true
    }
    fun closeArtistRootPage() {
        when (artistRootReturnTarget(appState.artistRootNavigationMode)) {
            ArtistRootReturnTarget.MiniPlayerFullscreen -> closeArtistRootPageThroughMiniPlayer()
            ArtistRootReturnTarget.PreviousPage -> closeArtistRootPageAsNormalPage()
        }
    }

    LaunchedEffect(appState.searchActive, imeVisible) {
        appState.searchKeyboardVisible = appState.searchActive && imeVisible
    }

    FlowtoneAppBackHandlers(
        secondaryPage = appState.secondaryPage,
        hasCurrentSong = hasCurrentSong,
        miniPlayerExpanded = appState.miniPlayerExpanded,
        miniPlayerFullscreen = appState.miniPlayerFullscreen,
        rootPage = rootPage,
        searchActive = appState.searchActive,
        searchKeyboardVisible = appState.searchKeyboardVisible,
        searchReturnStage = appState.searchReturnStage,
        onNavigateBack = navigateBack,
        onExitMiniPlayerFullscreen = exitMiniPlayerFullscreen,
        onCollapseMiniPlayer = {
            appState.miniPlayerExpanded = false
        },
        onCloseArtistRootPage = ::closeArtistRootPage,
        onDismissSearchKeyboard = {
            appState.searchKeyboardDismissRequest += 1
        },
        onExitSearch = ::exitSearchMode
    )

    FlowtoneAppEffects(
        selectedTopLevelPage = selectedTopLevelPage,
        secondaryPage = appState.secondaryPage,
        currentSong = playerUiState.currentSong,
        artistRootReturnInProgress = appState.artistRootReturnInProgress,
        openExpandedPlayerRequest = openExpandedPlayerRequest,
        hasCurrentSong = hasCurrentSong,
        hasScanned = uiState.hasScanned,
        songs = uiState.songs,
        likedSongsStore = likedSongsStore,
        preloadSongMetadataCount = appState.preloadSongMetadataCount,
        preloadLyricsCount = appState.preloadLyricsCount,
        songRecordThresholdSeconds = appState.songRecordThresholdSeconds,
        musicViewModel = musicViewModel,
        onContentScrollOffsetChange = { offset ->
            mainContentScrollOffsetPx = offset
        },
        onClearMiniPlayerState = {
            appState.miniPlayerExpanded = false
            appState.miniPlayerFullscreen = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
            appState.miniPlayerMinimized = false
        },
        onArtistRootReturnCompleted = {
            clearArtistRootPage()
        },
        onOpenExpandedMiniPlayer = {
            if (appState.openExpandedMiniPlayerOnMediaClick) {
                if (!appState.miniPlayerExpanded) {
                    appState.miniPlayerMinimized = false
                    appState.miniPlayerExpanded = true
                }
                appState.miniPlayerFullscreen = false
            } else {
                appState.miniPlayerFullscreenEnteredFromCollapsed =
                    !appState.miniPlayerExpanded
                appState.miniPlayerMinimized = false
                appState.miniPlayerExpanded = true
                appState.miniPlayerFullscreen = true
            }
        },
        onOpenExpandedPlayerRequestConsumed = onOpenExpandedPlayerRequestConsumed,
        onLikedSongKeysLoaded = { keys ->
            appState.likedSongKeys = keys
        },
        onHideSwipeHint = {
            appState.showSwipeHint = false
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        if (!audioPermissionGranted) {
            AudioPermissionGateScreen(
                openSettings = permissionGateRequiresSettings,
                onPrimaryAction = {
                    if (permissionGateRequiresSettings) {
                        openAppPermissionSettings(context)
                    } else {
                        appPreferences.markAudioPermissionRequested()
                        permissionLauncher.launch(currentAudioPermission())
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FlowtoneScaffold(
            mainTabsVisible = mainTabsVisible,
            state = flowtoneAppScaffoldState(
            appState = appState,
            uiState = uiState,
            playerUiState = playerUiState,
            songLyricsState = songLyricsState,
            appPreferences = appPreferences,
            themeMode = themeMode,
            pagerState = pagerState,
            selectedTopLevelPage = selectedTopLevelPage,
            rootPage = rootPage,
            secondaryOpen = secondaryOpen,
            topBarBackgroundAlpha = activeTopBarBackgroundAlpha,
            topBarScrollConnection = topBarScrollConnection,
            backgroundBlurRadius = backgroundBlurRadius,
            backgroundBlurProgress = backgroundBlurProgress,
            miniPlayerContentBottomPadding = miniPlayerContentBottomPadding,
            miniPlayerBottomProtection = miniPlayerBottomProtection,
            noRippleInteractionSource = noRippleInteractionSource,
            searchUiState = searchUiState,
            searchColors = activeSearchColors,
            searchReentryProgress = searchReentryProgress
            ),
            callbacks = flowtoneAppCallbacks(
            appState = appState,
            appPreferences = appPreferences,
            onThemeModeChange = onThemeModeChange,
            onNavigateBack = navigateBack,
            onRequestPermission = {
                permissionLauncher.launch(currentAudioPermission())
            },
            onSongClick = { song ->
                musicViewModel.playSong(song, PlaybackSource.LocalLibrary)
            },
            onOnlineSongClick = musicViewModel::playProviderSong,
            onPlaylistSongClick = { songs, startIndex, source ->
                musicViewModel.playSongQueue(songs, startIndex, source)
            },
            onCloseArtistRootPage = ::closeArtistRootPage,
            onOpenArtistRootPage = ::openArtistRootPage,
            onExitMiniPlayerFullscreen = exitMiniPlayerFullscreen,
            onTogglePlayPause = musicViewModel::togglePlayPause,
            onPlayPrevious = musicViewModel::playPrevious,
            onPlayNext = musicViewModel::playNext,
            onSeekTo = musicViewModel::seekTo,
            onTogglePlaybackOrderMode = musicViewModel::togglePlaybackOrderMode,
            onPlayQueueSong = musicViewModel::playQueueSong,
            onAddSongsToNext = musicViewModel::addSongsToNext,
            onAppendSongsToQueue = musicViewModel::appendSongsToQueue,
            onSetSongLiked = ::setSongLiked,
            onSetSongsLiked = ::setSongsLiked,
            onDeleteSongs = requestSongDeletion,
            onToggleSongLiked = ::toggleSongLiked,
            onOpenSearch = ::enterSearchMode,
            onExitSearch = ::exitSearchMode,
            onSearchQueryChange = musicViewModel::updateSearchQuery,
            onClearSearch = musicViewModel::clearSearchQuery
            ),
            modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun searchColorSnapshotOrNull(
    appState: FlowtoneAppState
): TopLevelSearchColorSnapshot? {
    val accentArgb = appState.searchFrozenAccentArgb ?: return null
    val containerArgb = appState.searchFrozenContainerArgb ?: return null
    val contentArgb = appState.searchFrozenContentArgb ?: return null
    return TopLevelSearchColorSnapshot(
        accentArgb = accentArgb,
        containerArgb = containerArgb,
        contentArgb = contentArgb
    )
}
