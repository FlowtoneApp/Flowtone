package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.data.repository.PlaylistMutationResult
import ink.tenqui.flowtone.data.repository.PlaylistRepository
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.library.CreatePlaylistOverlay
import ink.tenqui.flowtone.ui.library.CreatePlaylistState
import ink.tenqui.flowtone.ui.library.LibraryPlaylistEditingOverlay
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.library.PlaylistDialogVisualStyle
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.player.MiniPlayer
import ink.tenqui.flowtone.ui.screens.FlowCloudSpeedOverlay
import ink.tenqui.flowtone.ui.screens.SongRecordThresholdOverlay
import ink.tenqui.flowtone.ui.search.GlobalSearchOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun BoxScope.FlowtoneScaffoldOverlays(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    fullscreenHeight: Dp,
    libraryPlaylistController: LibraryPlaylistController,
    playlistRepository: PlaylistRepository,
    coroutineScope: CoroutineScope,
    displayedLibraryPlaylists: List<LibraryPlaylistCard>,
    playlistIdsContainingCurrentSong: Set<String>,
    addToPlaylistDialogBackgroundColor: Color,
    playlistEditingProgress: Float,
    playlistEditingBlurRadius: Dp,
    onAddToPlaylistDialogBackgroundColorChange: (Color) -> Unit,
    onRefreshLibraryPlaylistsFromRepository: (String?) -> Unit
) {
    val context = LocalContext.current
    var playlistAppearanceMutationVersion by remember { mutableIntStateOf(0) }
    var playlistAppearanceMutationJob by remember { mutableStateOf<Job?>(null) }
    var searchRevealLayerVisible by remember { mutableStateOf(state.searchActive) }
    val density = LocalDensity.current
    val searchTopPadding = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    } + 56.dp
    val searchRevealProgress by animateFloatAsState(
        targetValue = if (state.searchActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtonePageEasing
        ),
        label = "SearchRevealProgress"
    )
    LaunchedEffect(state.searchActive) {
        if (state.searchActive) {
            searchRevealLayerVisible = true
        } else {
            delay(FlowtoneMotion.DurationMillis.toLong())
            searchRevealLayerVisible = false
        }
    }
    val searchMiniPlayerSpaceProgress by animateFloatAsState(
        targetValue = if (
            state.searchActive &&
            state.searchKeyboardVisible &&
            state.playerUiState.hasCurrentSong
        ) {
            0f
        } else {
            1f
        },
        animationSpec = tween(
            durationMillis = 360,
            easing = FastOutSlowInEasing
        ),
        label = "SearchMiniPlayerBottomSpaceProgress"
    )
    val searchContentBottomPadding =
        state.miniPlayerContentBottomPadding * searchMiniPlayerSpaceProgress
    val searchInteractionsEnabled = state.searchReturnStage == SearchReturnStage.Idle
    val searchLayerProgress = when (state.searchReturnStage) {
        SearchReturnStage.SearchExitingForArtist,
        SearchReturnStage.SearchReentering -> state.searchReentryProgress
        SearchReturnStage.Idle -> 1f
        SearchReturnStage.ArtistVisible,
        SearchReturnStage.ArtistExitingToSearch,
        SearchReturnStage.SearchPreparing -> 0f
    }

    AnimatedVisibility(
        visible = searchRevealLayerVisible,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = Modifier
            .fillMaxSize()
            // 搜索内容覆盖普通页面，但始终位于 MiniPlayer 播放层下方。
            .zIndex(29f)
    ) {
        GlobalSearchOverlay(
            searchUiState = state.searchUiState,
            currentSong = state.playerUiState.currentSong,
            listState = state.searchListState,
            onSongClick = { songs, index ->
                callbacks.onPlaylistSongClick(
                    songs,
                    index,
                    PlaybackSource.Search
                )
            },
            onOnlineSongClick = callbacks.onOnlineSongClick,
            pendingTrackIdentityKey = state.uiState.pendingPlayback?.track?.identityKey,
            onArtistClick = { artist ->
                callbacks.onOpenArtistRootPage(
                    artist.name,
                    ArtistRootNavigationMode.NormalPage
                )
            },
            onExitSearch = callbacks.onExitSearch,
            onQueryChange = callbacks.onSearchQueryChange,
            onScopeChange = callbacks.onSearchScopeChange,
            bottomContentPadding = searchContentBottomPadding,
            interactionsEnabled = searchInteractionsEnabled,
            reentryProgress = searchLayerProgress,
            revealProgress = searchRevealProgress,
            revealColor = state.searchColors.container,
            modifier = Modifier
                .fillMaxSize()
        )
    }

    if (state.playerUiState.hasCurrentSong && state.backgroundBlurProgress > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f)
                .background(Color.Black.copy(alpha = 0.18f * state.backgroundBlurProgress))
                .clickable(
                    interactionSource = state.noRippleInteractionSource,
                    indication = null,
                    onClick = callbacks.onDismissExpandedPlayer
                )
        )
    }
    FlowtoneArtistRootLayer(
        state = state,
        callbacks = callbacks,
        hostMode = ArtistRootNavigationMode.MiniPlayer,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(15f)
    )
    MiniPlayer(
        playerUiState = state.playerUiState,
        isPendingPlayback = state.uiState.pendingPlayback != null,
        songLyricsState = state.songLyricsState,
        expanded = state.miniPlayerExpanded,
        onExpandedChange = callbacks.onExpandedChange,
        fullscreen = state.miniPlayerFullscreen,
        onFullscreenChange = callbacks.onFullscreenChange,
        fullscreenHeight = fullscreenHeight,
        allowFullscreenFromCollapsed = state.allowFullscreenFromCollapsed,
        allowFullscreenFromExpanded = true,
        openExpandedOnMediaClick = state.openExpandedMiniPlayerOnMediaClick,
        disablePausedArtworkTilt = state.disablePausedArtworkTilt,
        strictProgressBar = state.strictProgressBar,
        allowScreenOffOnLyricsPage = state.allowScreenOffOnLyricsPage,
        flowCloudSpeed = state.flowCloudSpeed,
        lyricsBackgroundStyle = state.lyricsBackgroundStyle,
        minimized = state.miniPlayerMinimized,
        onMinimizedChange = callbacks.onMinimizedChange,
        onTogglePlayPause = callbacks.onTogglePlayPause,
        onPlayPrevious = callbacks.onPlayPrevious,
        onPlayNext = callbacks.onPlayNext,
        onSeekTo = callbacks.onSeekTo,
        onTogglePlaybackOrderMode = callbacks.onTogglePlaybackOrderMode,
        libraryPlaylists = displayedLibraryPlaylists,
        playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
        newlyCreatedPlaylistId = libraryPlaylistController.newlyCreatedPlaylistId,
        onNewPlaylistCreateAnimationFinished = {
            libraryPlaylistController.consumeNewlyCreatedPlaylistAnimation(it)
        },
        onAddToPlaylistDialogBackgroundColorChange = { color ->
            onAddToPlaylistDialogBackgroundColorChange(color)
        },
        onCreatePlaylistClick = {
            libraryPlaylistController.startEditing(
                visualStyle = PlaylistDialogVisualStyle.AddToPlaylist
            )
        },
        onAddSongToPlaylist = addSongToPlaylist@{ playlist, onAdded ->
            val currentSong = state.playerUiState.currentSong ?: return@addSongToPlaylist
            val currentTrack = state.playerUiState.currentTrack ?: run {
                Toast.makeText(
                    context,
                    "当前在线来源不支持持久收藏",
                    Toast.LENGTH_SHORT
                ).show()
                return@addSongToPlaylist
            }
            if (playlist.isLikedSongsPlaylist()) {
                callbacks.onSetSongLiked(currentSong, true)
                onAdded()
                return@addSongToPlaylist
            }
            coroutineScope.launch {
                playlistRepository.syncLibraryPlaylistCards(libraryPlaylistController.playlists)
                val result = playlistRepository.addTrackToPlaylist(
                    playlistId = playlist.id,
                    track = currentTrack
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
        sourceQueue = state.uiState.sourceQueue,
        playbackQueue = state.uiState.playbackQueue,
        allSongs = state.uiState.songs,
        currentQueueIndex = state.uiState.currentQueueIndex,
        queueDisplayOrder = state.playbackQueueDisplayOrder,
        onQueueDisplayOrderChange = callbacks.onPlaybackQueueDisplayOrderChange,
        onPlayQueueSong = callbacks.onPlayQueueSong,
        onPlayArtistSongQueue = callbacks.onPlaylistSongClick,
        likedSongKeys = state.likedSongKeys,
        onToggleSongLiked = callbacks.onToggleSongLiked,
        onOpenArtistRootPage = { artistName ->
            callbacks.onOpenArtistRootPage(
                artistName,
                ArtistRootNavigationMode.MiniPlayer
            )
        },
        forceHidden = state.searchActive && state.searchKeyboardVisible,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = state.miniPlayerBottomProtection)
            .blur(playlistEditingBlurRadius)
            .zIndex(30f)
    )
    // confirmed current song 保持不动；这里只给出下一首在线歌正在解析的即时反馈。
    if (state.uiState.pendingPlayback != null) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = state.miniPlayerBottomProtection + 18.dp)
                .zIndex(31f),
            strokeWidth = 2.dp
        )
    }
    FlowtoneArtistRootLayer(
        state = state,
        callbacks = callbacks,
        hostMode = ArtistRootNavigationMode.NormalPage,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(25f)
    )
    val editingPlaylist = libraryPlaylistController.editingPlaylistId?.let { editingId ->
        libraryPlaylistController.playlists.firstOrNull { playlist ->
            playlist.id == editingId && !playlist.isSystem
        }
    }
    LibraryPlaylistEditingOverlay(
        playlist = editingPlaylist,
        cardBounds = libraryPlaylistController.editingPlaylistBounds,
        viewportBounds = libraryPlaylistController.libraryViewportBounds,
        progress = playlistEditingProgress,
        bottomContentPadding = state.miniPlayerContentBottomPadding,
        flowCloudSpeed = state.flowCloudSpeed,
        dialogVisible = libraryPlaylistController.createPlaylistState !=
            CreatePlaylistState.Idle,
        onDismissRequest = libraryPlaylistController::clearPlaylistEditing,
        onLongPressOtherPlaylist = libraryPlaylistController::startPlaylistEditingAt,
        onDeletePlaylist = libraryPlaylistController::startDeletePlaylist,
        onRenamePlaylist = libraryPlaylistController::startRenamePlaylist,
        onAppearanceColorSelected = { playlist, colorKey ->
            playlistAppearanceMutationVersion += 1
            val mutationVersion = playlistAppearanceMutationVersion
            libraryPlaylistController.previewPlaylistAppearanceColor(
                playlistId = playlist.id,
                colorKey = colorKey
            )
            val previousMutationJob = playlistAppearanceMutationJob
            playlistAppearanceMutationJob = coroutineScope.launch {
                previousMutationJob?.join()
                if (mutationVersion != playlistAppearanceMutationVersion) {
                    return@launch
                }
                playlistRepository.updatePlaylistAppearanceColor(
                    id = playlist.id,
                    colorKey = colorKey
                )
                if (mutationVersion == playlistAppearanceMutationVersion) {
                    onRefreshLibraryPlaylistsFromRepository(null)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(40f)
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
                    if (
                        libraryPlaylistController.playlistDialogVisualStyle ==
                            PlaylistDialogVisualStyle.AddToPlaylist
                    ) {
                        val track = state.playerUiState.currentTrack
                        if (track == null) {
                            Toast.makeText(
                                context,
                                "当前在线来源不支持持久收藏",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            playlistRepository.addTrackToPlaylist(result.value.id, track)
                        }
                    }
                    onRefreshLibraryPlaylistsFromRepository(result.value.id)
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
                    onRefreshLibraryPlaylistsFromRepository(null)
                    libraryPlaylistController.clearPlaylistEditing()
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
                    libraryPlaylistController.retainPlaylistForDeleteAnimation(playlistId)
                    onRefreshLibraryPlaylistsFromRepository(null)
                    libraryPlaylistController.clearPlaylistEditing()
                    libraryPlaylistController.closeEditing()
                    delay(FlowtoneMotion.DurationMillis.toLong())
                    libraryPlaylistController.startPlaylistDeleteAnimation()
                } else {
                    libraryPlaylistController.unlockDialog()
                }
            }
        },
        addToPlaylistDialogBackgroundColor = addToPlaylistDialogBackgroundColor,
        modifier = Modifier.fillMaxSize()
            .zIndex(50f)
    )
    SongRecordThresholdOverlay(
        dialogState = state.songRecordThresholdDialogState,
        selectedSeconds = state.songRecordThresholdSeconds,
        onDismissRequest = callbacks.onCloseSongRecordThresholdDialog,
        onDismissAnimationFinished = callbacks.onSongRecordThresholdDialogClosed,
        onConfirm = callbacks.onSongRecordThresholdSecondsChange,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
    )
    FlowCloudSpeedOverlay(
        dialogState = state.flowCloudSpeedDialogState,
        selectedSpeed = state.flowCloudSpeed,
        onDismissRequest = callbacks.onCloseFlowCloudSpeedDialog,
        onDismissAnimationFinished = callbacks.onFlowCloudSpeedDialogClosed,
        onConfirm = callbacks.onFlowCloudSpeedChange,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
    )
}
