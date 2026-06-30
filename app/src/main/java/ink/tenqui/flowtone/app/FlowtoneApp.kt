package ink.tenqui.flowtone.app

import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import ink.tenqui.flowtone.permissions.currentAudioPermission
import ink.tenqui.flowtone.permissions.hasAudioPermission
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.player.MiniPlayerCollapsedHeight
import ink.tenqui.flowtone.ui.player.MiniPlayerMinimizedHeight
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

private const val MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS = 300
private const val FLOWTONE_INSETS_TAG = "FlowtoneInsets"
internal val FlowtonePageEasing = FlowtoneMotion.Easing

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
    val defaultStartPage = remember(appPreferences) {
        appPreferences.getDefaultStartPage()
    }
    var permissionDenied by remember {
        mutableStateOf(false)
    }
    var miniPlayerExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    var miniPlayerFullscreen by rememberSaveable {
        mutableStateOf(false)
    }
    var miniPlayerFullscreenEnteredFromCollapsed by rememberSaveable {
        mutableStateOf(false)
    }
    var miniPlayerMinimized by rememberSaveable {
        mutableStateOf(false)
    }
    var showSwipeHint by rememberSaveable {
        mutableStateOf(true)
    }
    var secondaryPage by rememberSaveable {
        mutableStateOf<SecondaryPage?>(null)
    }
    var openSourceBackAction by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }
    var openSourcePathSegments by remember {
        mutableStateOf(emptyList<String>())
    }
    var hideSecondaryBackButton by rememberSaveable {
        mutableStateOf(appPreferences.shouldHideSecondaryBackButton())
    }
    var resumePlaybackAfterCall by rememberSaveable {
        mutableStateOf(appPreferences.shouldResumePlaybackAfterCall())
    }
    var allowFullscreenFromCollapsed by rememberSaveable {
        mutableStateOf(appPreferences.shouldAllowFullscreenFromCollapsed())
    }
    var preloadSongMetadataCount by rememberSaveable {
        mutableStateOf(appPreferences.getSongMetadataPreloadCount())
    }

    val pagerState = rememberPagerState(
        initialPage = defaultStartPage.index,
        pageCount = { TopLevelPage.entries.size }
    )
    val selectedTopLevelPage = TopLevelPage.entries[pagerState.currentPage]
    val secondaryOpen = secondaryPage != null
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
    val backgroundBlurProgress by animateFloatAsState(
        targetValue = if (hasCurrentSong && (miniPlayerExpanded || miniPlayerFullscreen)) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerBackgroundBlurProgress"
    )
    val backgroundBlurRadius by animateDpAsState(
        targetValue = if (hasCurrentSong && (miniPlayerExpanded || miniPlayerFullscreen)) 12.dp else 0.dp,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerBackgroundBlur"
    )
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val navMode = remember(context, configuration) {
        val resourceId = context.resources.getIdentifier(
            "config_navBarInteractionMode",
            "integer",
            "android"
        )
        val resourceNavMode = if (resourceId > 0) {
            context.resources.getInteger(resourceId)
        } else {
            -1
        }
        val secureNavMode = Settings.Secure.getInt(
            context.contentResolver,
            "navigation_mode",
            -1
        )

        if (secureNavMode >= 0) {
            secureNavMode
        } else {
            resourceNavMode
        }
    }
    val isThreeButtonNavigation = navMode == 0
    val isDebuggable = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val miniPlayerBottomProtection = with(density) {
        val tappableBottom = WindowInsets.tappableElement.getBottom(this)
        val navigationBottom = WindowInsets.navigationBars.getBottom(this)
        val bottomProtection = when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> navigationBottom
            isThreeButtonNavigation -> navigationBottom
            else -> tappableBottom
        }
        if (isDebuggable) {
            Log.d(
                FLOWTONE_INSETS_TAG,
                "navMode=$navMode, isThreeButton=$isThreeButtonNavigation, " +
                    "navigationBottom=$navigationBottom, tappableBottom=$tappableBottom, " +
                    "bottomProtection=$bottomProtection"
            )
        }

        bottomProtection.toDp()
    }
    val miniPlayerContentBottomPadding by animateDpAsState(
        targetValue = if (hasCurrentSong) {
            val playerHeight = if (miniPlayerMinimized) {
                MiniPlayerMinimizedHeight
            } else {
                MiniPlayerCollapsedHeight
            }
            playerHeight + miniPlayerBottomProtection
        } else {
            0.dp
        },
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
        permissionDenied = !granted
        if (granted) {
            musicViewModel.scanSongs()
        }
    }

    val navigateBack: () -> Unit = {
        if (secondaryPage == SecondaryPage.OpenSource) {
            val nestedBackAction = openSourceBackAction
            if (nestedBackAction != null) {
                nestedBackAction()
            } else {
                secondaryPage = SecondaryPage.About
            }
        } else {
            secondaryPage = when (secondaryPage) {
                SecondaryPage.Settings,
                SecondaryPage.About,
                SecondaryPage.LocalLibrary -> null
                SecondaryPage.OpenSource -> SecondaryPage.About
                null -> null
            }
        }
    }
    val exitMiniPlayerFullscreen: () -> Unit = {
        miniPlayerFullscreen = false
        if (miniPlayerFullscreenEnteredFromCollapsed) {
            miniPlayerExpanded = false
            miniPlayerMinimized = false
            miniPlayerFullscreenEnteredFromCollapsed = false
        }
    }
    BackHandler(enabled = secondaryPage != null, onBack = navigateBack)
    BackHandler(enabled = hasCurrentSong && (miniPlayerExpanded || miniPlayerFullscreen)) {
        if (miniPlayerFullscreen) {
            exitMiniPlayerFullscreen()
        } else {
            miniPlayerExpanded = false
        }
    }

    LaunchedEffect(selectedTopLevelPage, secondaryPage) {
        contentScrollOffsetPx = 0f
    }

    LaunchedEffect(playerUiState.currentSong) {
        if (playerUiState.currentSong == null) {
            miniPlayerExpanded = false
            miniPlayerFullscreen = false
            miniPlayerFullscreenEnteredFromCollapsed = false
            miniPlayerMinimized = false
        }
    }

    LaunchedEffect(openExpandedPlayerRequest, hasCurrentSong, uiState.hasScanned, uiState.songs) {
        if (openExpandedPlayerRequest == 0) {
            return@LaunchedEffect
        }

        if (hasCurrentSong) {
            if (!miniPlayerExpanded) {
                miniPlayerMinimized = false
                miniPlayerExpanded = true
            }
            miniPlayerFullscreen = false
            onOpenExpandedPlayerRequestConsumed()
        } else if (uiState.hasScanned && uiState.songs.isEmpty()) {
            onOpenExpandedPlayerRequestConsumed()
        }
    }

    LaunchedEffect(context) {
        val granted = hasAudioPermission(context)
        musicViewModel.setPermissionStatus(granted)
        if (granted) {
            musicViewModel.scanSongs()
        }
    }

    LaunchedEffect(Unit) {
        delay(2_000)
        showSwipeHint = false
    }

    LaunchedEffect(preloadSongMetadataCount) {
        musicViewModel.setPreloadSongMetadataCount(preloadSongMetadataCount)
    }

    FlowtoneScaffold(
        uiState = uiState,
        playerUiState = playerUiState,
        appPreferences = appPreferences,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        pagerState = pagerState,
        selectedTopLevelPage = selectedTopLevelPage,
        secondaryPage = secondaryPage,
        openSourcePathSegments = openSourcePathSegments,
        hideSecondaryBackButton = hideSecondaryBackButton,
        onHideSecondaryBackButtonChange = { hide ->
            hideSecondaryBackButton = hide
            appPreferences.setHideSecondaryBackButton(hide)
        },
        resumePlaybackAfterCall = resumePlaybackAfterCall,
        onResumePlaybackAfterCallChange = { resume ->
            resumePlaybackAfterCall = resume
            appPreferences.setResumePlaybackAfterCall(resume)
        },
        allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
        onAllowFullscreenFromCollapsedChange = { allow ->
            allowFullscreenFromCollapsed = allow
            appPreferences.setAllowFullscreenFromCollapsed(allow)
        },
        preloadSongMetadataCount = preloadSongMetadataCount,
        onPreloadSongMetadataCountChange = { count ->
            preloadSongMetadataCount = count
            appPreferences.setSongMetadataPreloadCount(count)
        },
        openSourceBackActionChange = { action ->
            openSourceBackAction = action
        },
        onOpenSourcePathSegmentsChange = { segments ->
            openSourcePathSegments = segments
        },
        permissionDenied = permissionDenied,
        showSwipeHint = showSwipeHint,
        secondaryOpen = secondaryOpen,
        topBarBackgroundAlpha = topBarBackgroundAlpha,
        topBarScrollConnection = topBarScrollConnection,
        backgroundBlurRadius = backgroundBlurRadius,
        backgroundBlurProgress = backgroundBlurProgress,
        miniPlayerContentBottomPadding = miniPlayerContentBottomPadding,
        miniPlayerBottomProtection = miniPlayerBottomProtection,
        miniPlayerExpanded = miniPlayerExpanded,
        miniPlayerFullscreen = miniPlayerFullscreen,
        miniPlayerMinimized = miniPlayerMinimized,
        noRippleInteractionSource = noRippleInteractionSource,
        onNavigateBack = navigateBack,
        onCloseSecondaryPage = {
            secondaryPage = null
        },
        onOpenSettings = {
            secondaryPage = SecondaryPage.Settings
        },
        onOpenAbout = {
            secondaryPage = SecondaryPage.About
        },
        onOpenLocalLibrary = {
            secondaryPage = SecondaryPage.LocalLibrary
        },
        onOpenSource = {
            secondaryPage = SecondaryPage.OpenSource
        },
        onOpenSourceBack = {
            secondaryPage = SecondaryPage.About
        },
        onRequestPermission = {
            permissionLauncher.launch(currentAudioPermission())
        },
        onSongClick = { song ->
            musicViewModel.playSong(song)
        },
        onDismissExpandedPlayer = {
            if (miniPlayerFullscreen) {
                exitMiniPlayerFullscreen()
            } else {
                miniPlayerExpanded = false
            }
        },
        onExpandedChange = { expanded ->
            if (!expanded && miniPlayerFullscreen) {
                exitMiniPlayerFullscreen()
            } else {
                if (expanded) {
                    miniPlayerMinimized = false
                }
                miniPlayerExpanded = expanded
            }
        },
        onFullscreenChange = { fullscreen ->
            if (fullscreen) {
                miniPlayerFullscreenEnteredFromCollapsed = !miniPlayerExpanded
                miniPlayerExpanded = true
                miniPlayerMinimized = false
                miniPlayerFullscreen = true
            } else {
                exitMiniPlayerFullscreen()
            }
        },
        onMinimizedChange = { minimized ->
            if (minimized) {
                miniPlayerFullscreen = false
                miniPlayerExpanded = false
                miniPlayerFullscreenEnteredFromCollapsed = false
            }
            miniPlayerMinimized = minimized
        },
        onTogglePlayPause = musicViewModel::togglePlayPause,
        onPlayPrevious = musicViewModel::playPrevious,
        onPlayNext = musicViewModel::playNext,
        onSeekTo = musicViewModel::seekTo,
        onTogglePlaybackOrderMode = musicViewModel::togglePlaybackOrderMode,
        onPlayQueueSong = musicViewModel::playQueueSong,
        modifier = Modifier.fillMaxSize()
    )
}
