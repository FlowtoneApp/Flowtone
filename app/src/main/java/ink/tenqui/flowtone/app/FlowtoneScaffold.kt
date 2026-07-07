package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.core.model.likedSongsPlaylistCard
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.data.local.PlaylistStorage
import ink.tenqui.flowtone.data.repository.PlaylistMutationResult
import ink.tenqui.flowtone.data.repository.PlaylistRepository
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.library.CreatePlaylistOverlay
import ink.tenqui.flowtone.ui.library.PlaylistDialogVisualStyle
import ink.tenqui.flowtone.ui.library.ArtistRootPage
import ink.tenqui.flowtone.ui.player.MiniPlayer
import ink.tenqui.flowtone.ui.library.rememberLibraryPlaylistController
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FlowtoneScaffold(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    modifier: Modifier = Modifier
) {
    val uiState = state.uiState
    val playerUiState = state.playerUiState
    val appPreferences = state.appPreferences
    val themeMode = state.themeMode
    val disablePausedArtworkTilt = state.disablePausedArtworkTilt
    val pagerState = state.pagerState
    val selectedTopLevelPage = state.selectedTopLevelPage
    val rootPage = state.rootPage
    val secondaryPage = state.secondaryPage
    val selectedPlaylistId = state.selectedPlaylistId
    val selectedArtistName = state.selectedArtistName
    val likedSongKeys = state.likedSongKeys
    val secondaryPathSegments = state.secondaryPathSegments
    val hideSecondaryBackButton = state.hideSecondaryBackButton
    val resumePlaybackAfterCall = state.resumePlaybackAfterCall
    val allowFullscreenFromCollapsed = state.allowFullscreenFromCollapsed
    val preloadSongMetadataCount = state.preloadSongMetadataCount
    val songRecordThresholdSeconds = state.songRecordThresholdSeconds
    val playbackQueueDisplayOrder = state.playbackQueueDisplayOrder
    val permissionDenied = state.permissionDenied
    val showSwipeHint = state.showSwipeHint
    val secondaryOpen = state.secondaryOpen
    val topBarBackgroundAlpha = state.topBarBackgroundAlpha
    val topBarScrollConnection = state.topBarScrollConnection
    val backgroundBlurRadius = state.backgroundBlurRadius
    val backgroundBlurProgress = state.backgroundBlurProgress
    val miniPlayerContentBottomPadding = state.miniPlayerContentBottomPadding
    val miniPlayerBottomProtection = state.miniPlayerBottomProtection
    val miniPlayerExpanded = state.miniPlayerExpanded
    val miniPlayerFullscreen = state.miniPlayerFullscreen
    val miniPlayerMinimized = state.miniPlayerMinimized
    val noRippleInteractionSource = state.noRippleInteractionSource
    val onThemeModeChange = callbacks.onThemeModeChange
    val onDisablePausedArtworkTiltChange = callbacks.onDisablePausedArtworkTiltChange
    val onHideSecondaryBackButtonChange = callbacks.onHideSecondaryBackButtonChange
    val onResumePlaybackAfterCallChange = callbacks.onResumePlaybackAfterCallChange
    val onAllowFullscreenFromCollapsedChange = callbacks.onAllowFullscreenFromCollapsedChange
    val onPreloadSongMetadataCountChange = callbacks.onPreloadSongMetadataCountChange
    val onSongRecordThresholdSecondsChange = callbacks.onSongRecordThresholdSecondsChange
    val onPlaybackQueueDisplayOrderChange = callbacks.onPlaybackQueueDisplayOrderChange
    val settingsBackActionChange = callbacks.settingsBackActionChange
    val onSettingsPathSegmentsChange = callbacks.onSettingsPathSegmentsChange
    val openSourceBackActionChange = callbacks.openSourceBackActionChange
    val onOpenSourcePathSegmentsChange = callbacks.onOpenSourcePathSegmentsChange
    val onNavigateBack = callbacks.onNavigateBack
    val onCloseSecondaryPage = callbacks.onCloseSecondaryPage
    val onOpenSettings = callbacks.onOpenSettings
    val onOpenAbout = callbacks.onOpenAbout
    val onOpenLocalLibrary = callbacks.onOpenLocalLibrary
    val onOpenPlaylist = callbacks.onOpenPlaylist
    val onOpenArtistRootPage = callbacks.onOpenArtistRootPage
    val onCloseArtistRootPage = callbacks.onCloseArtistRootPage
    val onOpenSource = callbacks.onOpenSource
    val onOpenSourceBack = callbacks.onOpenSourceBack
    val onRequestPermission = callbacks.onRequestPermission
    val onSongClick = callbacks.onSongClick
    val onDismissExpandedPlayer = callbacks.onDismissExpandedPlayer
    val onExpandedChange = callbacks.onExpandedChange
    val onFullscreenChange = callbacks.onFullscreenChange
    val onMinimizedChange = callbacks.onMinimizedChange
    val onTogglePlayPause = callbacks.onTogglePlayPause
    val onPlayPrevious = callbacks.onPlayPrevious
    val onPlayNext = callbacks.onPlayNext
    val onSeekTo = callbacks.onSeekTo
    val onTogglePlaybackOrderMode = callbacks.onTogglePlaybackOrderMode
    val onPlayQueueSong = callbacks.onPlayQueueSong
    val onPlaylistSongClick = callbacks.onPlaylistSongClick
    val onSetSongLiked = callbacks.onSetSongLiked
    val onToggleSongLiked = callbacks.onToggleSongLiked
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasCurrentSong = playerUiState.hasCurrentSong
    val libraryPlaylistController = rememberLibraryPlaylistController()
    val playlistRepository = remember(context) {
        PlaylistRepository(PlaylistStorage(context.applicationContext))
    }
    var addToPlaylistDialogBackgroundColor by remember {
        mutableStateOf(Color(0xFF1B1B20))
    }
    val playlistSongEntries by playlistRepository.playlistSongEntries.collectAsState()
    val likedSongCount = remember(uiState.songs, likedSongKeys) {
        uiState.songs.count { song -> isSongLiked(song, likedSongKeys) }
    }
    val displayedLibraryPlaylists = remember(libraryPlaylistController.playlists, likedSongCount) {
        listOf(likedSongsPlaylistCard(likedSongCount)) + libraryPlaylistController.playlists
    }
    val playlistIdsContainingCurrentSong = remember(
        playlistSongEntries,
        playerUiState.currentSong?.id,
        playerUiState.currentSong?.uri,
        likedSongKeys
    ) {
        val currentSong = playerUiState.currentSong
        val currentSongId = currentSong?.id?.toString()
        if (currentSong == null || currentSongId == null) {
            emptySet()
        } else {
            val normalPlaylistIds = playlistSongEntries
                .filter { entry -> entry.songId == currentSongId }
                .mapTo(mutableSetOf()) { entry -> entry.playlistId }
            if (isSongLiked(currentSong, likedSongKeys)) {
                normalPlaylistIds + LikedSongsPlaylistId
            } else {
                normalPlaylistIds
            }
        }
    }

    LaunchedEffect(libraryPlaylistController.playlists) {
        playlistRepository.syncLibraryPlaylistCards(libraryPlaylistController.playlists)
    }

    LaunchedEffect(playlistSongEntries) {
        libraryPlaylistController.applySongCounts(playlistSongEntries)
        libraryPlaylistController.savePlaylistsIfRequested()
        playlistRepository.syncLibraryPlaylistCards(libraryPlaylistController.playlists)
    }

    fun refreshLibraryPlaylistsFromRepository(createdPlaylistId: String? = null) {
        libraryPlaylistController.applyRepositoryPlaylists(
            repositoryPlaylists = playlistRepository.playlists.value,
            entries = playlistRepository.playlistSongEntries.value,
            createdPlaylistId = createdPlaylistId
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlurRadius),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                if (rootPage == FlowtoneRootPage.MainTabs) {
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
                        libraryPlaylistController = libraryPlaylistController,
                        permissionDenied = permissionDenied,
                        showSwipeHint = showSwipeHint,
                        secondaryOpen = secondaryOpen,
                        onRequestPermission = onRequestPermission,
                        onSongClick = onSongClick,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onOpenLocalLibrary = onOpenLocalLibrary,
                        onOpenPlaylist = onOpenPlaylist,
                        likedSongCount = likedSongCount,
                        modifier = Modifier.fillMaxSize()
                    )
                    SecondaryPageHost(
                        secondaryPage = secondaryPage,
                        appPreferences = appPreferences,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        disablePausedArtworkTilt = disablePausedArtworkTilt,
                        onDisablePausedArtworkTiltChange = onDisablePausedArtworkTiltChange,
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
                        selectedPlaylistId = selectedPlaylistId,
                        selectedArtistName = selectedArtistName,
                        likedSongKeys = likedSongKeys,
                        playlistSongEntries = playlistSongEntries,
                        permissionDenied = permissionDenied,
                        onRequestPermission = onRequestPermission,
                        onSongClick = onSongClick,
                        onPlaylistSongClick = onPlaylistSongClick,
                        onCloseSecondaryPage = onCloseSecondaryPage,
                        onSettingsBackActionChange = settingsBackActionChange,
                        onSettingsPathSegmentsChange = onSettingsPathSegmentsChange,
                        onOpenSource = onOpenSource,
                        onOpenSourceBack = onOpenSourceBack,
                        onOpenSourceBackActionChange = openSourceBackActionChange,
                        onOpenSourcePathSegmentsChange = onOpenSourcePathSegmentsChange,
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedContent(
                        targetState = rootPage as? FlowtoneRootPage.ArtistRootPage,
                        transitionSpec = {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = FlowtoneMotion.DurationMillis,
                                    easing = FlowtonePageEasing
                                )
                            ) togetherWith fadeOut(
                                animationSpec = tween(
                                    durationMillis = FlowtoneMotion.DurationMillis,
                                    easing = FlowtonePageEasing
                                )
                            )
                        },
                        label = "ArtistRootPageTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { artistRootPage ->
                        fun artistPageItemModifier(index: Int): Modifier {
                            return staggeredPageElementModifier(index)
                        }
                        if (artistRootPage != null) {
                            ArtistRootPage(
                                artistName = artistRootPage.artistName,
                                allSongs = uiState.songs,
                                currentSong = playerUiState.currentSong,
                                onBack = onCloseArtistRootPage,
                                onSongClick = onPlaylistSongClick,
                                itemModifier = ::artistPageItemModifier,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rightSwipeBackGesture(onCloseArtistRootPage)
                            )
                        }
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
            disablePausedArtworkTilt = disablePausedArtworkTilt,
            minimized = miniPlayerMinimized,
            onMinimizedChange = onMinimizedChange,
            onTogglePlayPause = onTogglePlayPause,
            onPlayPrevious = onPlayPrevious,
            onPlayNext = onPlayNext,
            onSeekTo = onSeekTo,
            onTogglePlaybackOrderMode = onTogglePlaybackOrderMode,
            libraryPlaylists = displayedLibraryPlaylists,
            playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
            newlyCreatedPlaylistId = libraryPlaylistController.newlyCreatedPlaylistId,
            onNewPlaylistCreateAnimationFinished = {
                libraryPlaylistController.consumeNewlyCreatedPlaylistAnimation(it)
            },
            onAddToPlaylistDialogBackgroundColorChange = { color ->
                addToPlaylistDialogBackgroundColor = color
            },
            onCreatePlaylistClick = {
                libraryPlaylistController.startEditing(
                    visualStyle = PlaylistDialogVisualStyle.AddToPlaylist
                )
            },
            onAddSongToPlaylist = addSongToPlaylist@{ playlist, onAdded ->
                val currentSong = playerUiState.currentSong ?: return@addSongToPlaylist
                if (playlist.isLikedSongsPlaylist()) {
                    onSetSongLiked(currentSong, true)
                    onAdded()
                    return@addSongToPlaylist
                }
                coroutineScope.launch {
                    playlistRepository.syncLibraryPlaylistCards(libraryPlaylistController.playlists)
                    val result = playlistRepository.addSongToPlaylist(
                        playlistId = playlist.id,
                        song = currentSong
                    )
                    if (result is PlaylistMutationResult.Success) {
                        libraryPlaylistController.applySongCounts(
                            playlistRepository.playlistSongEntries.value
                        )
                        libraryPlaylistController.savePlaylistsIfRequested()
                        playlistRepository.syncLibraryPlaylistCards(
                            libraryPlaylistController.playlists
                        )
                        onAdded()
                    }
                }
            },
            sourceQueue = uiState.sourceQueue,
            playbackQueue = uiState.playbackQueue,
            allSongs = uiState.songs,
            currentQueueIndex = uiState.currentQueueIndex,
            queueDisplayOrder = playbackQueueDisplayOrder,
            onQueueDisplayOrderChange = onPlaybackQueueDisplayOrderChange,
            onPlayQueueSong = onPlayQueueSong,
            onPlayArtistSongQueue = onPlaylistSongClick,
            likedSongKeys = likedSongKeys,
            onToggleSongLiked = onToggleSongLiked,
            onOpenArtistRootPage = onOpenArtistRootPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = miniPlayerBottomProtection)
        )
        CreatePlaylistOverlay(
            playlistController = libraryPlaylistController,
            onCreatePlaylist = { title ->
                coroutineScope.launch {
                    playlistRepository.syncLibraryPlaylistCards(
                        libraryPlaylistController.playlists
                    )
                    val result = playlistRepository.createPlaylist(title)
                    if (result is PlaylistMutationResult.Success) {
                        refreshLibraryPlaylistsFromRepository(
                            createdPlaylistId = result.value.id
                        )
                        libraryPlaylistController.closeEditing()
                    } else {
                        libraryPlaylistController.unlockDialog()
                    }
                }
            },
            onRenamePlaylist = { playlistId, title ->
                coroutineScope.launch {
                    playlistRepository.syncLibraryPlaylistCards(
                        libraryPlaylistController.playlists
                    )
                    val result = playlistRepository.renamePlaylist(
                        id = playlistId,
                        newTitle = title
                    )
                    if (result is PlaylistMutationResult.Success) {
                        refreshLibraryPlaylistsFromRepository()
                        libraryPlaylistController.clearPlaylistActions()
                        libraryPlaylistController.closeEditing()
                    } else {
                        libraryPlaylistController.unlockDialog()
                    }
                }
            },
            onDeletePlaylist = { playlistId ->
                coroutineScope.launch {
                    playlistRepository.syncLibraryPlaylistCards(
                        libraryPlaylistController.playlists
                    )
                    val result = playlistRepository.deletePlaylist(playlistId)
                    if (result is PlaylistMutationResult.Success) {
                        refreshLibraryPlaylistsFromRepository()
                        libraryPlaylistController.clearPlaylistActions()
                        libraryPlaylistController.closeEditing()
                    } else {
                        libraryPlaylistController.unlockDialog()
                    }
                }
            },
            addToPlaylistDialogBackgroundColor = addToPlaylistDialogBackgroundColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}
