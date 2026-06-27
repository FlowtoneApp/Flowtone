package ink.tenqui.flowtone.app

import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.permissions.currentAudioPermission
import ink.tenqui.flowtone.permissions.hasAudioPermission
import ink.tenqui.flowtone.ui.library.LibraryScreen
import ink.tenqui.flowtone.ui.library.LocalLibraryScreen
import ink.tenqui.flowtone.ui.player.MiniPlayer
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.screens.AboutScreen
import ink.tenqui.flowtone.ui.screens.OpenSourceScreen
import ink.tenqui.flowtone.viewmodel.MusicUiState
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS = 300
private const val FLOWTONE_INSETS_TAG = "FlowtoneInsets"
private val FlowtonePageEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private enum class SecondaryPage(
    val title: String,
    val secondaryTitle: String = title
) {
    Settings("\u8bbe\u7f6e"),
    About("\u5173\u4e8e"),
    LocalLibrary("\u672c\u5730\u66f2\u5e93"),
    OpenSource("\u5f00\u6e90\u7ec4\u4ef6", secondaryTitle = "\u5173\u4e8e")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowtoneApp(
    musicViewModel: MusicViewModel = viewModel(),
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
    var showSwipeHint by rememberSaveable {
        mutableStateOf(true)
    }
    var secondaryPage by rememberSaveable {
        mutableStateOf<SecondaryPage?>(null)
    }
    val pagerState = rememberPagerState(
        initialPage = defaultStartPage.index,
        pageCount = { TopLevelPage.entries.size }
    )
    val selectedTopLevelPage = TopLevelPage.entries[pagerState.currentPage]
    val secondaryOpen = secondaryPage != null
    val secondaryProgress by animateFloatAsState(
        targetValue = if (secondaryOpen) 1f else 0f,
        animationSpec = tween(300, easing = FlowtonePageEasing),
        label = "SecondaryPageProgress"
    )
    val tertiaryProgress by animateFloatAsState(
        targetValue = if (secondaryPage == SecondaryPage.OpenSource) 1f else 0f,
        animationSpec = tween(300, easing = FlowtonePageEasing),
        label = "TertiaryPageProgress"
    )
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
        targetValue = if (hasCurrentSong && miniPlayerExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerBackgroundBlurProgress"
    )
    val backgroundBlurRadius by animateDpAsState(
        targetValue = if (hasCurrentSong && miniPlayerExpanded) 12.dp else 0.dp,
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        musicViewModel.setPermissionStatus(granted)
        permissionDenied = !granted
        if (granted) {
            musicViewModel.scanSongs()
        }
    }

    BackHandler(enabled = hasCurrentSong && miniPlayerExpanded) {
        miniPlayerExpanded = false
    }

    BackHandler(enabled = secondaryPage != null) {
        secondaryPage = when (secondaryPage) {
            SecondaryPage.OpenSource -> SecondaryPage.About
            SecondaryPage.Settings,
            SecondaryPage.About,
            SecondaryPage.LocalLibrary -> null
            null -> null
        }
    }

    LaunchedEffect(selectedTopLevelPage, secondaryPage) {
        contentScrollOffsetPx = 0f
    }

    LaunchedEffect(playerUiState.currentSong) {
        if (playerUiState.currentSong == null) {
            miniPlayerExpanded = false
        }
    }

    LaunchedEffect(openExpandedPlayerRequest, hasCurrentSong, uiState.hasScanned, uiState.songs) {
        if (openExpandedPlayerRequest == 0) {
            return@LaunchedEffect
        }

        if (hasCurrentSong) {
            if (!miniPlayerExpanded) {
                miniPlayerExpanded = true
            }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    secondaryProgress = secondaryProgress,
                    tertiaryProgress = tertiaryProgress,
                    backgroundAlpha = topBarBackgroundAlpha,
                    onBack = { secondaryPage = null }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarScrollConnection)
                    .padding(innerPadding)
            ) {
                TopLevelPagerContent(
                    pagerState = pagerState,
                    uiState = uiState,
                    playerUiState = playerUiState,
                    permissionDenied = permissionDenied,
                    showSwipeHint = showSwipeHint,
                    secondaryOpen = secondaryOpen,
                    onRequestPermission = {
                        permissionLauncher.launch(currentAudioPermission())
                    },
                    onSongClick = { song ->
                        musicViewModel.playSong(song)
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
                    modifier = Modifier.fillMaxSize()
                )
                AnimatedContent(
                    targetState = secondaryPage,
                    transitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None
                    },
                    label = "SecondaryContentTransition"
                ) { page ->
                    fun elementModifier(index: Int): Modifier = Modifier.animateEnterExit(
                        enter = fadeIn(
                            tween(
                                durationMillis = 220,
                                delayMillis = 90 + index * 45,
                                easing = FlowtonePageEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 240,
                                delayMillis = 90 + index * 45,
                                easing = FlowtonePageEasing
                            )
                        ) { it / 6 },
                        exit = fadeOut(
                            tween(
                                durationMillis = 180,
                                delayMillis = index * 45,
                                easing = FlowtonePageEasing
                            )
                        ) + slideOutVertically(
                            animationSpec = tween(
                                durationMillis = 240,
                                delayMillis = index * 45,
                                easing = FlowtonePageEasing
                            )
                        ) { -it / 6 }
                    )
                    when (page) {
                        SecondaryPage.Settings -> SettingsScreen(
                            appPreferences = appPreferences,
                            onBack = { secondaryPage = null },
                            elementModifier = ::elementModifier,
                            modifier = Modifier.fillMaxSize()
                        )

                        SecondaryPage.About -> AboutScreen(
                            onOpenSource = { secondaryPage = SecondaryPage.OpenSource },
                            onBack = { secondaryPage = null },
                            elementModifier = ::elementModifier,
                            modifier = Modifier.fillMaxSize()
                        )

                        SecondaryPage.OpenSource -> OpenSourceScreen(
                            onBack = { secondaryPage = SecondaryPage.About },
                            elementModifier = ::elementModifier,
                            modifier = Modifier.fillMaxSize()
                        )

                        SecondaryPage.LocalLibrary -> LocalLibraryScreen(
                            uiState = uiState,
                            currentSong = playerUiState.currentSong,
                            permissionDenied = permissionDenied,
                            onRequestPermission = {
                                permissionLauncher.launch(currentAudioPermission())
                            },
                            onSongClick = { song -> musicViewModel.playSong(song) },
                            modifier = elementModifier(0)
                                .fillMaxSize()
                                .rightSwipeToGoBack { secondaryPage = null }
                        )

                        null -> Box(modifier = Modifier.fillMaxSize())
                    }
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
                        indication = null
                    ) {
                        miniPlayerExpanded = false
                    }
            )
        }
        MiniPlayer(
            playerUiState = playerUiState,
            expanded = miniPlayerExpanded,
            onExpandedChange = { miniPlayerExpanded = it },
            onTogglePlayPause = musicViewModel::togglePlayPause,
            onPlayPrevious = musicViewModel::playPrevious,
            onPlayNext = musicViewModel::playNext,
            onSeekTo = musicViewModel::seekTo,
            onTogglePlaybackOrderMode = musicViewModel::togglePlaybackOrderMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = miniPlayerBottomProtection)
        )
    }
}

@Composable
private fun FlowtoneTopBar(
    selectedTopLevelPage: TopLevelPage,
    pagerState: PagerState,
    secondaryPage: SecondaryPage?,
    secondaryProgress: Float,
    tertiaryProgress: Float,
    backgroundAlpha: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inLocalLibrary = secondaryPage == SecondaryPage.LocalLibrary
    var displayedSecondaryTitle by remember {
        mutableStateOf(secondaryPage?.title.orEmpty())
    }
    LaunchedEffect(secondaryPage) {
        secondaryPage?.let { displayedSecondaryTitle = it.secondaryTitle }
    }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleOffsetYPx = with(density) { -3.dp.toPx() }
    val parentOffsetYPx = with(density) { -17.dp.toPx() }
    val parentOffsetXPx = with(density) { 1.dp.toPx() }
    val childRestingOffsetYPx = with(density) { 3.dp.toPx() }
    val childHiddenOffsetYPx = with(density) { 48.dp.toPx() }
    val pathGapPx = with(density) { 2.dp.toPx() }
    val pathBaselineCorrectionPx = with(density) { 1.dp.toPx() }
    val navigationShiftPx = with(density) { 40.dp.toPx() } *
        if (inLocalLibrary) secondaryProgress else 0f
    val parentWidthPx = textMeasurer.measure(
        text = selectedTopLevelPage.title,
        style = MaterialTheme.typography.titleLarge
    ).size.width * 0.65f
    val separatorWidthPx = textMeasurer.measure(
        text = "/",
        style = MaterialTheme.typography.labelLarge
    ).size.width.toFloat()
    val topPathOffsetYPx = titleOffsetYPx + parentOffsetYPx
    val separatorTargetXPx = parentOffsetXPx + parentWidthPx + pathGapPx
    val secondaryPathTargetXPx = separatorTargetXPx + separatorWidthPx + pathGapPx
    val expandedSecondaryStyle = MaterialTheme.typography.headlineSmall
    val compactSecondaryStyle = MaterialTheme.typography.labelLarge
    val secondaryTitleStyle = expandedSecondaryStyle.copy(
        fontSize = (
            expandedSecondaryStyle.fontSize.value +
                (compactSecondaryStyle.fontSize.value - expandedSecondaryStyle.fontSize.value) *
                tertiaryProgress
            ).sp,
        lineHeight = (
            expandedSecondaryStyle.lineHeight.value +
                (compactSecondaryStyle.lineHeight.value - expandedSecondaryStyle.lineHeight.value) *
                tertiaryProgress
            ).sp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = backgroundAlpha)
            )
            .statusBarsPadding()
            .height(56.dp)
            .clipToBounds()
            .padding(start = 20.dp, end = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = inLocalLibrary,
            enter = fadeIn(tween(180, easing = FlowtonePageEasing)) +
                slideInHorizontally(tween(260, easing = FlowtonePageEasing)) { -it },
            exit = fadeOut(tween(140, easing = FlowtonePageEasing)) +
                slideOutHorizontally(tween(240, easing = FlowtonePageEasing)) { -it },
            modifier = Modifier.offset(x = (-16).dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "\u8fd4\u56de",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        ContinuousTopTitle(
            pagerState = pagerState,
            parentPage = selectedTopLevelPage,
            secondaryProgress = secondaryProgress,
            parentOffsetXPx = parentOffsetXPx,
            parentOffsetYPx = parentOffsetYPx,
            modifier = Modifier.graphicsLayer {
                translationX = navigationShiftPx
                translationY = titleOffsetYPx
            }
        )
        AnimatedVisibility(
            visible = secondaryPage != null && !inLocalLibrary,
            enter = fadeIn(
                tween(durationMillis = 220, easing = FlowtonePageEasing)
            ) + slideInVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) { it },
            exit = fadeOut(
                tween(durationMillis = 180, easing = FlowtonePageEasing)
            ) + slideOutVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) { it },
            modifier = Modifier.graphicsLayer {
                translationX = separatorTargetXPx
                translationY = topPathOffsetYPx + pathBaselineCorrectionPx
            }
        ) {
            Text(
                text = "/",
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = displayedSecondaryTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = secondaryTitleStyle,
            fontWeight = FontWeight.Medium,
            color = lerp(
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.onSurfaceVariant,
                tertiaryProgress
            ),
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationX = navigationShiftPx +
                    secondaryPathTargetXPx * tertiaryProgress
                val secondaryOffsetY = childHiddenOffsetYPx +
                    (childRestingOffsetYPx - childHiddenOffsetYPx) * secondaryProgress
                translationY = secondaryOffsetY +
                    (topPathOffsetYPx + pathBaselineCorrectionPx -
                        childRestingOffsetYPx) * tertiaryProgress
                alpha = secondaryProgress
            }
        )
        Text(
            text = SecondaryPage.OpenSource.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                translationY = childHiddenOffsetYPx +
                    (childRestingOffsetYPx - childHiddenOffsetYPx) * tertiaryProgress
                alpha = tertiaryProgress
            }
        )
    }
}

@Composable
private fun TopLevelPagerContent(
    pagerState: PagerState,
    uiState: MusicUiState,
    playerUiState: PlayerUiState,
    permissionDenied: Boolean,
    showSwipeHint: Boolean,
    secondaryOpen: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val page = TopLevelPage.entries[pageIndex]
            when (page) {
                TopLevelPage.Home -> HomeScreen(
                    modifier = Modifier.fillMaxSize()
                )

                TopLevelPage.Library -> LibraryScreen(
                    songCount = uiState.songs.size,
                    onOpenLocalLibrary = onOpenLocalLibrary,
                    modifier = Modifier.fillMaxSize()
                )

                TopLevelPage.Mine -> MineScreen(
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                    secondaryOpen = secondaryOpen,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        SwipePageHint(
            visible = showSwipeHint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u4e3b\u9875\u5360\u4f4d",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MineScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    secondaryOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        MineMenuItem(
            title = "\u8bbe\u7f6e",
            visible = !secondaryOpen,
            animationIndex = 0,
            onClick = onOpenSettings
        )
        MineMenuItem(
            title = "\u5173\u4e8e",
            visible = !secondaryOpen,
            animationIndex = 1,
            onClick = onOpenAbout,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun MineMenuItem(
    title: String,
    visible: Boolean,
    animationIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            tween(
                durationMillis = 220,
                delayMillis = 90 + animationIndex * 45,
                easing = FlowtonePageEasing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = 240,
                delayMillis = 90 + animationIndex * 45,
                easing = FlowtonePageEasing
            )
        ) { it / 6 },
        exit = fadeOut(
            tween(
                durationMillis = 180,
                delayMillis = animationIndex * 45,
                easing = FlowtonePageEasing
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = 240,
                delayMillis = animationIndex * 45,
                easing = FlowtonePageEasing
            )
        ) { -it / 6 },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedStartPage by rememberSaveable {
        mutableStateOf(appPreferences.getDefaultStartPage())
    }
    val pages = listOf(
        TopLevelPage.Home,
        TopLevelPage.Library,
        TopLevelPage.Mine
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onBack) {
                var horizontalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDrag = (horizontalDrag + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (horizontalDrag >= 72.dp.toPx()) {
                            onBack()
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "\u542f\u52a8\u65f6\u9ed8\u8ba4\u8fdb\u5165",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = elementModifier(0)
        )
        Text(
            text = "\u4e0b\u6b21\u6253\u5f00\u5e94\u7528\u65f6\u751f\u6548",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = elementModifier(1).padding(top = 4.dp, bottom = 12.dp)
        )
        pages.forEachIndexed { index, page ->
            Row(
                modifier = elementModifier(index + 2)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        selectedStartPage = page
                        appPreferences.setDefaultStartPage(page)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedStartPage == page,
                    onClick = {
                        selectedStartPage = page
                        appPreferences.setDefaultStartPage(page)
                    }
                )
                Text(
                    text = page.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun Modifier.rightSwipeToGoBack(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    var horizontalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { horizontalDrag = 0f },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            horizontalDrag = (horizontalDrag + dragAmount).coerceAtLeast(0f)
        },
        onDragEnd = {
            if (horizontalDrag >= 72.dp.toPx()) {
                onBack()
            }
            horizontalDrag = 0f
        },
        onDragCancel = { horizontalDrag = 0f }
    )
}

@Composable
private fun SwipePageHint(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 450,
                easing = LinearEasing
            )
        ),
        modifier = modifier
    ) {
        Text(
            text = "\u5de6\u53f3\u6ed1\u52a8\u5207\u6362\u9875\u9762",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ContinuousTopTitle(
    pagerState: PagerState,
    parentPage: TopLevelPage,
    secondaryProgress: Float,
    parentOffsetXPx: Float,
    parentOffsetYPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { 36.dp.toPx() }
    val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction

    Box(
        modifier = modifier.height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        TopLevelPage.entries.forEach { page ->
            val distance = page.index - pagePosition
            val isParent = page == parentPage
            val pageAlpha = (1f - abs(distance)).coerceIn(0f, 1f)
            val scale = if (isParent) 1f - 0.35f * secondaryProgress else 1f
            val titleColor = if (isParent) {
                lerp(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    secondaryProgress
                )
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Text(
                text = page.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                modifier = Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = distance * slideDistancePx +
                        if (isParent) parentOffsetXPx * secondaryProgress else 0f
                    translationY = if (isParent) parentOffsetYPx * secondaryProgress else 0f
                    scaleX = scale
                    scaleY = scale
                    alpha = pageAlpha * if (isParent) 1f else 1f - secondaryProgress
                }
            )
        }
    }
}

@Composable
private fun AnimatedTopTitle(
    page: TopLevelPage,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { 36.dp.roundToPx() }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val direction = when {
                targetState.index > initialState.index -> 1
                targetState.index < initialState.index -> -1
                else -> 0
            }

            if (direction == 0) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(120))
            } else {
                (
                    slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing
                        )
                    ) { direction * slideDistancePx } + fadeIn(tween(220))
                ) togetherWith (
                    slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing
                        )
                    ) { -direction * slideDistancePx } + fadeOut(tween(180))
                )
            }.using(SizeTransform(clip = false))
        },
        label = "TopBarTitleTransition"
    ) { targetPage ->
        Box(
            modifier = modifier.height(36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = targetPage.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}
