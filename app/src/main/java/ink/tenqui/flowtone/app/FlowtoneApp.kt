package ink.tenqui.flowtone.app

import android.app.Activity
import android.widget.Toast
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
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.data.online.ProviderSong
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
    val likedTracks by musicViewModel.likedTracks.collectAsState()
    val playerUiState = PlayerUiState.from(playbackState)
    val appPreferences = remember(context) {
        AppPreferences(context.applicationContext)
    }
    val defaultStartPage = remember(appPreferences) {
        appPreferences.getDefaultStartPage()
    }
    val appState = rememberFlowtoneAppState(appPreferences)
    LaunchedEffect(likedTracks) {
        appState.likedSongKeys = likedTracks.map { it.identityKey }
    }
    val coroutineScope = rememberCoroutineScope()
    val permissionActivity = context as? Activity
    var audioPermissionGranted by remember(context) {
        mutableStateOf(hasAudioPermission(context))
    }
    var permissionRequestResultVersion by remember { mutableStateOf(0) }
    var pendingSongDeletion by remember { mutableStateOf<PendingSongDeletion?>(null) }
    val pagerState = rememberPagerState(
        initialPage = defaultStartPage.index,
        pageCount = { TopLevelPage.entries.size }
    )
    val selectedTopLevelPage = TopLevelPage.entries[pagerState.currentPage]
    val liveSearchColors = topLevelSearchColorsForPager(pagerState)
    val frozenSearchColors = searchColorSnapshotOrNull(appState)?.toColors()
    val activeSearchColors = frozenSearchColors ?: liveSearchColors
    val topBarRevealDistancePx = with(density) { 24.dp.toPx() }
    var mainContentScrollOffsetPx by remember {
        mutableStateOf(0f)
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
    val topBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (appState.searchActive) {
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
        musicViewModel.refreshSearchSources()
        // 当前搜索页仅展示占位内容，因此不自动唤起旧输入框和键盘。
        appState.searchFocusRequest = 0
        appState.searchKeyboardDismissRequest = 0
        coroutineScope.launch {
            pagerState.scrollToPage(enteredPageIndex)
        }
    }
    fun exitSearchMode() {
        if (!appState.searchActive) {
            return
        }
        val restorePageIndex = appState.searchEnteredPageIndex
            .coerceIn(0, TopLevelPage.entries.lastIndex)
        appState.searchActive = false
        appState.searchInputFocused = false
        appState.searchKeyboardVisible = false
        appState.searchFocusRequest = 0
        appState.searchKeyboardDismissRequest = 0
        clearFrozenSearchColors()
        // 保留搜索结果到圆形返回裁切完成，避免 AnimatedContent 先切到空 Landing
        // 而让列表主动渐出或重新排布。
        coroutineScope.launch {
            delay(FlowtoneMotion.DurationMillis.toLong())
            if (!appState.searchActive) {
                musicViewModel.clearSearchQuery()
            }
        }
        coroutineScope.launch {
            pagerState.scrollToPage(restorePageIndex)
        }
    }
    fun setSongLiked(song: Song, liked: Boolean) {
        val track = playbackState.currentTrack.takeIf { playbackState.currentSong == song }
            ?: song.takeIf { it.sourceType == SourceType.Local }?.toPersistentTrack()
        if (track == null) {
            Toast.makeText(context, "当前在线来源不支持持久收藏", Toast.LENGTH_SHORT).show()
            return
        }
        musicViewModel.setTrackLiked(track, liked)
    }
    fun toggleSongLiked(song: Song) {
        val track = playbackState.currentTrack.takeIf { playbackState.currentSong == song }
            ?: song.takeIf { it.sourceType == SourceType.Local }?.toPersistentTrack()
        if (track == null) {
            Toast.makeText(context, "当前在线来源不支持持久收藏", Toast.LENGTH_SHORT).show()
            return
        }
        setSongLiked(song, track.identityKey !in appState.likedSongKeys)
    }
    fun setSongsLiked(songs: List<Song>, liked: Boolean) {
        songs.forEach { song -> musicViewModel.setTrackLiked(song.toPersistentTrack(), liked) }
    }
    val exitMiniPlayerFullscreen: () -> Unit = {
        appState.miniPlayerFullscreen = false
        if (appState.miniPlayerFullscreenEnteredFromCollapsed) {
            appState.miniPlayerExpanded = false
            appState.miniPlayerMinimized = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
        }
    }
    fun openArtist(artistName: String) {
        val displayArtist = artistName.trim()
        if (displayArtist.isBlank()) {
            return
        }
        if (appState.searchActive) {
            appState.searchInputFocused = false
            appState.searchFocusRequest = 0
            appState.searchKeyboardDismissRequest += 1
        }
        appState.secondaryNavigation = appState.secondaryNavigation.push(
            SecondaryDestination.Artist(displayArtist)
        )
    }

    fun openProviderArtist(artist: ProviderSong) {
        val displayName = artist.title.trim()
        val providerId = artist.trackRef.extensionId.trim()
        val artistId = artist.trackRef.opaqueId.trim()
        if (displayName.isBlank() || providerId.isBlank() || artistId.isBlank()) {
            return
        }
        if (appState.searchActive) {
            appState.searchInputFocused = false
            appState.searchFocusRequest = 0
            appState.searchKeyboardDismissRequest += 1
        }
        appState.secondaryNavigation = appState.secondaryNavigation.push(
            SecondaryDestination.Artist(
                ArtistDestinationIdentity.Provider(
                    providerId = providerId,
                    artistId = artistId,
                    displayName = displayName,
                    avatar = artist.artwork ?: artist.largeArtwork
                )
            )
        )
    }

    LaunchedEffect(appState.searchActive, imeVisible) {
        appState.searchKeyboardVisible = appState.searchActive && imeVisible
    }

    FlowtoneAppBackHandlers(
        secondaryPage = appState.secondaryPage,
        hasCurrentSong = hasCurrentSong,
        miniPlayerExpanded = appState.miniPlayerExpanded,
        miniPlayerFullscreen = appState.miniPlayerFullscreen,
        searchActive = appState.searchActive,
        searchKeyboardVisible = appState.searchKeyboardVisible,
        onNavigateBack = navigateBack,
        onExitMiniPlayerFullscreen = exitMiniPlayerFullscreen,
        onCollapseMiniPlayer = {
            appState.miniPlayerExpanded = false
        },
        onDismissSearchKeyboard = {
            appState.searchKeyboardDismissRequest += 1
        },
        onExitSearch = ::exitSearchMode
    )

    FlowtoneAppEffects(
        selectedTopLevelPage = selectedTopLevelPage,
        secondaryPage = appState.secondaryPage,
        currentSong = playerUiState.currentSong,
        openExpandedPlayerRequest = openExpandedPlayerRequest,
        hasCurrentSong = hasCurrentSong,
        hasScanned = uiState.hasScanned,
        songs = uiState.songs,
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
            state = flowtoneAppScaffoldState(
            appState = appState,
            uiState = uiState,
            playerUiState = playerUiState,
            songLyricsState = songLyricsState,
            appPreferences = appPreferences,
            themeMode = themeMode,
            pagerState = pagerState,
            selectedTopLevelPage = selectedTopLevelPage,
            topBarBackgroundAlpha = activeTopBarBackgroundAlpha,
            topBarScrollConnection = topBarScrollConnection,
            backgroundBlurRadius = backgroundBlurRadius,
            backgroundBlurProgress = backgroundBlurProgress,
            miniPlayerContentBottomPadding = miniPlayerContentBottomPadding,
            miniPlayerBottomProtection = miniPlayerBottomProtection,
            noRippleInteractionSource = noRippleInteractionSource,
            searchUiState = searchUiState,
            searchColors = activeSearchColors
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
            onPersistentTrackQueueClick = { tracks, startIndex, source ->
                musicViewModel.playPersistentTrackQueue(tracks, startIndex, source)
            },
            onOpenAlbum = { albumId ->
                val album = uiState.albums.firstOrNull { candidate -> candidate.id == albumId }
                if (album != null) {
                    if (appState.searchActive) {
                        appState.searchInputFocused = false
                        appState.searchFocusRequest = 0
                        appState.searchKeyboardDismissRequest += 1
                    }
                    val destination = SecondaryDestination.Album(album.id, album.title)
                    appState.secondaryNavigation = appState.secondaryNavigation.push(destination)
                }
            },
            onOpenArtist = { artistName -> openArtist(artistName) },
            onOpenProviderArtist = ::openProviderArtist,
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
            onSetTracksLiked = { tracks, liked ->
                tracks.forEach { track -> musicViewModel.setTrackLiked(track, liked) }
            },
            onDeleteSongs = requestSongDeletion,
            onToggleSongLiked = ::toggleSongLiked,
            onOpenSearch = ::enterSearchMode,
            onExitSearch = ::exitSearchMode,
            onSearchQueryChange = musicViewModel::updateSearchQuery,
            onSearchScopeChange = musicViewModel::selectSearchScope,
            onSearchCategoryChange = musicViewModel::selectProviderSearchCategory,
            onLoadMoreSearchResults = musicViewModel::loadMoreProviderSearchResults,
            onRefreshSearchSources = musicViewModel::refreshSearchSources,
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
