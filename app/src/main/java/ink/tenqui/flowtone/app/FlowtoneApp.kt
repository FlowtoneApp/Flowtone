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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ink.tenqui.flowtone.permissions.currentAudioPermission
import ink.tenqui.flowtone.permissions.hasAudioPermission
import ink.tenqui.flowtone.ui.player.MiniPlayer
import ink.tenqui.flowtone.ui.player.PlayerUiState
import ink.tenqui.flowtone.ui.library.LibraryScreen
import ink.tenqui.flowtone.viewmodel.MusicViewModel

private const val MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS = 300
private const val FLOWTONE_INSETS_TAG = "FlowtoneInsets"

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
    var permissionDenied by remember {
        mutableStateOf(false)
    }
    var miniPlayerExpanded by rememberSaveable {
        mutableStateOf(false)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlurRadius),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Text(text = "Flowtone")
                    },
                    expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight
                )
            }
        ) { innerPadding ->
            LibraryScreen(
                uiState = uiState,
                currentSong = playerUiState.currentSong,
                permissionDenied = permissionDenied,
                onRequestPermission = {
                    permissionLauncher.launch(currentAudioPermission())
                },
                onSongClick = { song ->
                    musicViewModel.playSong(song)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
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
