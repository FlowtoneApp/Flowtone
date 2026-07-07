package ink.tenqui.flowtone.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.LikedSongsStore
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.permissions.currentAudioPermission
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicViewModel

@Composable
fun FlowtoneApp(
    musicViewModel: MusicViewModel = viewModel(),
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    openExpandedPlayerRequest: Int = 0,
    onOpenExpandedPlayerRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val uiState by musicViewModel.uiState.collectAsState()
    val playbackState by musicViewModel.playbackState.collectAsState()
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

    val pagerState = rememberPagerState(
        initialPage = defaultStartPage.index,
        pageCount = { TopLevelPage.entries.size }
    )
    val selectedTopLevelPage = TopLevelPage.entries[pagerState.currentPage]
    val rootPage = flowtoneRootPage(appState.artistRootPageArtistName)
    val secondaryOpen = appState.secondaryPage != null
    val topBarRevealDistancePx = with(density) { 24.dp.toPx() }
    var contentScrollOffsetPx by remember {
        mutableStateOf(0f)
    }
    val topBarBackgroundAlpha by animateFloatAsState(
        targetValue = (contentScrollOffsetPx / topBarRevealDistancePx).coerceIn(0f, 1f),
        animationSpec = tween(160, easing = FlowtonePageEasing),
        label = "TopBarBackgroundAlpha"
    )
    val topBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                contentScrollOffsetPx = (contentScrollOffsetPx - consumed.y).coerceAtLeast(0f)
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        musicViewModel.setPermissionStatus(granted)
        appState.permissionDenied = !granted
        if (granted) {
            musicViewModel.scanSongs()
        }
    }

    val navigateBack: () -> Unit = {
        navigateFlowtoneAppBack(appState)
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
    val exitMiniPlayerFullscreen: () -> Unit = {
        appState.miniPlayerFullscreen = false
        if (appState.miniPlayerFullscreenEnteredFromCollapsed) {
            appState.miniPlayerExpanded = false
            appState.miniPlayerMinimized = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
        }
    }
    fun closeArtistRootPageThroughMiniPlayer() {
        if (appState.artistRootPageArtistName == null || appState.artistRootReturnInProgress) {
            return
        }
        if (!hasCurrentSong) {
            appState.artistRootPageArtistName = null
            appState.artistRootReturnInProgress = false
            return
        }
        if (appState.miniPlayerFullscreen) {
            appState.miniPlayerExpanded = true
            appState.miniPlayerMinimized = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
            appState.artistRootPageArtistName = null
            appState.artistRootReturnInProgress = false
            return
        }

        appState.artistRootReturnInProgress = true
        appState.miniPlayerFullscreenEnteredFromCollapsed = false
        appState.miniPlayerExpanded = true
        appState.miniPlayerMinimized = false
        appState.miniPlayerFullscreen = true
    }

    FlowtoneAppBackHandlers(
        secondaryPage = appState.secondaryPage,
        hasCurrentSong = hasCurrentSong,
        miniPlayerExpanded = appState.miniPlayerExpanded,
        miniPlayerFullscreen = appState.miniPlayerFullscreen,
        rootPage = rootPage,
        onNavigateBack = navigateBack,
        onExitMiniPlayerFullscreen = exitMiniPlayerFullscreen,
        onCollapseMiniPlayer = {
            appState.miniPlayerExpanded = false
        },
        onCloseArtistRootPage = ::closeArtistRootPageThroughMiniPlayer
    )

    FlowtoneAppEffects(
        selectedTopLevelPage = selectedTopLevelPage,
        secondaryPage = appState.secondaryPage,
        rootPage = rootPage,
        currentSong = playerUiState.currentSong,
        artistRootReturnInProgress = appState.artistRootReturnInProgress,
        openExpandedPlayerRequest = openExpandedPlayerRequest,
        hasCurrentSong = hasCurrentSong,
        hasScanned = uiState.hasScanned,
        songs = uiState.songs,
        context = context,
        likedSongsStore = likedSongsStore,
        preloadSongMetadataCount = appState.preloadSongMetadataCount,
        songRecordThresholdSeconds = appState.songRecordThresholdSeconds,
        musicViewModel = musicViewModel,
        onContentScrollOffsetChange = { offset ->
            contentScrollOffsetPx = offset
        },
        onClearMiniPlayerState = {
            appState.miniPlayerExpanded = false
            appState.miniPlayerFullscreen = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
            appState.miniPlayerMinimized = false
        },
        onArtistRootReturnCompleted = {
            appState.artistRootPageArtistName = null
            appState.artistRootReturnInProgress = false
        },
        onOpenExpandedMiniPlayer = {
            if (!appState.miniPlayerExpanded) {
                appState.miniPlayerMinimized = false
                appState.miniPlayerExpanded = true
            }
            appState.miniPlayerFullscreen = false
        },
        onOpenExpandedPlayerRequestConsumed = onOpenExpandedPlayerRequestConsumed,
        onLikedSongKeysLoaded = { keys ->
            appState.likedSongKeys = keys
        },
        onHideSwipeHint = {
            appState.showSwipeHint = false
        }
    )

    FlowtoneScaffold(
        state = flowtoneAppScaffoldState(
            appState = appState,
            uiState = uiState,
            playerUiState = playerUiState,
            appPreferences = appPreferences,
            themeMode = themeMode,
            pagerState = pagerState,
            selectedTopLevelPage = selectedTopLevelPage,
            rootPage = rootPage,
            secondaryOpen = secondaryOpen,
            topBarBackgroundAlpha = topBarBackgroundAlpha,
            topBarScrollConnection = topBarScrollConnection,
            backgroundBlurRadius = backgroundBlurRadius,
            backgroundBlurProgress = backgroundBlurProgress,
            miniPlayerContentBottomPadding = miniPlayerContentBottomPadding,
            miniPlayerBottomProtection = miniPlayerBottomProtection,
            noRippleInteractionSource = noRippleInteractionSource
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
                musicViewModel.playSong(song)
            },
            onPlaylistSongClick = { songs, startIndex ->
                musicViewModel.playSongQueue(songs, startIndex)
            },
            onCloseArtistRootPage = ::closeArtistRootPageThroughMiniPlayer,
            onExitMiniPlayerFullscreen = exitMiniPlayerFullscreen,
            onTogglePlayPause = musicViewModel::togglePlayPause,
            onPlayPrevious = musicViewModel::playPrevious,
            onPlayNext = musicViewModel::playNext,
            onSeekTo = musicViewModel::seekTo,
            onTogglePlaybackOrderMode = musicViewModel::togglePlaybackOrderMode,
            onPlayQueueSong = musicViewModel::playQueueSong,
            onSetSongLiked = ::setSongLiked,
            onToggleSongLiked = ::toggleSongLiked
        ),
        modifier = Modifier.fillMaxSize()
    )
}
