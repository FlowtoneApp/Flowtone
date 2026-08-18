package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.data.local.PlaylistStorage
import ink.tenqui.flowtone.data.repository.PlaylistRepository
import ink.tenqui.flowtone.data.repository.PlaylistMutationResult
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.LibraryPlaylistEditingBlurRadius
import ink.tenqui.flowtone.ui.library.PlaylistBatchActions
import ink.tenqui.flowtone.ui.library.PlaylistSelectionTopBarState
import ink.tenqui.flowtone.ui.library.PlaylistSongSort
import ink.tenqui.flowtone.ui.library.PlaylistSongSortCriterion
import ink.tenqui.flowtone.ui.library.rememberLibraryPlaylistController
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.player.LocalDarkFlowCloudOverlayEnabled
import kotlinx.coroutines.launch

@Composable
internal fun FlowtoneScaffold(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    mainTabsVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playlistActionMotionDistancePx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val coroutineScope = rememberCoroutineScope()
    val homeScrollState = rememberScrollState()
    val libraryPlaylistController = rememberLibraryPlaylistController()
    val topLevelPageCollapseProgress = rememberTopLevelPageCollapseProgress(
        homeScrollState = homeScrollState,
        libraryListState = libraryPlaylistController.listState
    )
    var detailHeaderCollapseProgressState by remember {
        mutableStateOf<State<Float>?>(null)
    }
    var songSelectionTopBarState by remember {
        mutableStateOf<PlaylistSelectionTopBarState?>(null)
    }
    var clearSongSelectionRequest by remember { mutableStateOf(0) }
    var playlistSongSort by remember { mutableStateOf(PlaylistSongSort()) }
    var playlistSortPanelOpen by remember { mutableStateOf(false) }
    val playlistSortListDismissInteractionSource = remember {
        MutableInteractionSource()
    }
    val playlistSortProgress by animateFloatAsState(
        targetValue = if (playlistSortPanelOpen) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "PlaylistSortOverlayProgress"
    )
    val playlistSortCharacterSectionProgress by animateFloatAsState(
        targetValue = if (playlistSongSort.criterion == PlaylistSongSortCriterion.Title) 1f else 0f,
        animationSpec = tween(200),
        label = "PlaylistSortCharacterSectionProgress"
    )
    val playlistSortPanelHeight = PlaylistSortPanelCollapsedHeight +
        (PlaylistSortPanelHeight - PlaylistSortPanelCollapsedHeight) *
            playlistSortCharacterSectionProgress
    val onDetailHeaderCollapseProgressStateChange = remember {
        { progressState: State<Float>? ->
            detailHeaderCollapseProgressState = progressState
        }
    }
    val playlistRepository = remember(context) {
        PlaylistRepository(PlaylistStorage(context.applicationContext))
    }
    var addToPlaylistDialogBackgroundColor by remember {
        mutableStateOf(Color(0xFF1B1B20))
    }
    val playlistSongEntries by playlistRepository.playlistSongEntries.collectAsState()
    val likedSongCount = state.uiState.likedTracks.size
    val displayedLibraryPlaylists = remember(libraryPlaylistController.playlists, likedSongCount) {
        flowtoneDisplayedLibraryPlaylists(
            playlists = libraryPlaylistController.playlists,
            likedSongCount = likedSongCount
        )
    }
    val playlistIdsContainingCurrentSong = remember(
        playlistSongEntries,
        state.playerUiState.currentSong?.id,
        state.playerUiState.currentSong?.uri,
        state.playerUiState.currentTrack?.identityKey,
        state.likedSongKeys
    ) {
        flowtonePlaylistIdsContainingCurrentSong(
            playlistSongEntries = playlistSongEntries,
            currentSong = state.playerUiState.currentSong,
            currentTrack = state.playerUiState.currentTrack,
            likedSongKeys = state.likedSongKeys
        )
    }
    val playlistEditingProgress by animateFloatAsState(
        targetValue = if (libraryPlaylistController.editingPlaylistId == null) 0f else 1f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "LibraryPlaylistEditingProgress"
    )
    val playlistEditingBlurRadius =
        LibraryPlaylistEditingBlurRadius * playlistEditingProgress
    val scaffoldBlurRadius = if (playlistEditingBlurRadius > state.backgroundBlurRadius) {
        playlistEditingBlurRadius
    } else {
        state.backgroundBlurRadius
    }

    val libraryPlaylistSyncKey = remember(libraryPlaylistController.playlists) {
        libraryPlaylistController.playlists.map(LibraryPlaylistCard::repositorySyncKey)
    }
    LaunchedEffect(libraryPlaylistSyncKey) {
        playlistRepository.syncLibraryPlaylistCards(libraryPlaylistController.playlists)
    }

    LaunchedEffect(playlistSongEntries) {
        libraryPlaylistController.applySongCounts(playlistSongEntries)
        libraryPlaylistController.savePlaylistsIfRequested()
        playlistRepository.syncLibraryPlaylistCards(libraryPlaylistController.playlists)
    }

    LaunchedEffect(
        state.rootPage,
        state.selectedTopLevelPage,
        state.secondaryPage,
        state.searchActive
    ) {
        val editingAllowed = state.rootPage == FlowtoneRootPage.MainTabs &&
            state.selectedTopLevelPage == TopLevelPage.Library &&
            state.secondaryPage == null &&
            !state.searchActive
        if (!editingAllowed) {
            libraryPlaylistController.clearPlaylistEditing()
        }
        if (
            state.secondaryPage != SecondaryPage.Playlist &&
            state.secondaryPage != SecondaryPage.LocalLibrary
        ) {
            playlistSortPanelOpen = false
        }
    }

    LaunchedEffect(libraryPlaylistController.editingPlaylistId) {
        if (libraryPlaylistController.editingPlaylistId != null) {
            libraryPlaylistController.listState.stopScroll()
        }
    }

    LaunchedEffect(songSelectionTopBarState) {
        if (songSelectionTopBarState != null) {
            playlistSortPanelOpen = false
        }
    }

    fun refreshLibraryPlaylistsFromRepository(createdPlaylistId: String? = null) {
        libraryPlaylistController.applyRepositoryPlaylists(
            repositoryPlaylists = playlistRepository.playlists.value,
            entries = playlistRepository.playlistSongEntries.value,
            createdPlaylistId = createdPlaylistId
        )
    }
    val playlistBatchActions = PlaylistBatchActions(
        likedSongKeys = state.likedSongKeys,
        editablePlaylists = displayedLibraryPlaylists,
        clearSelectionRequest = clearSongSelectionRequest,
        onSelectionModeChange = {},
        onSelectionTopBarStateChange = { selectionState ->
            songSelectionTopBarState = selectionState
        },
        onRequestClearSelection = { clearSongSelectionRequest += 1 },
        onAddSongsNext = callbacks.onAddSongsToNext,
        onAppendSongsToQueue = callbacks.onAppendSongsToQueue,
        onSetSongsLiked = callbacks.onSetTracksLiked,
        onDeleteSongs = callbacks.onDeleteSongs,
        onAddSongsToPlaylists = { playlistIds, tracks, done ->
            coroutineScope.launch {
                val userPlaylistIds = playlistIds - LikedSongsPlaylistId
                val addToLikedSongs = LikedSongsPlaylistId in playlistIds
                val currentEntries = playlistRepository.playlistSongEntries.value
                val duplicateTrackKeys = tracks
                    .filter { track ->
                        (addToLikedSongs && track.identityKey in state.likedSongKeys) ||
                            currentEntries.any { entry ->
                                entry.playlistId in userPlaylistIds &&
                                    entry.track.identityKey == track.identityKey
                            }
                    }
                    .mapTo(mutableSetOf()) { track -> track.identityKey }

                playlistRepository.syncLibraryPlaylistCards(
                    libraryPlaylistController.playlists
                )
                var succeeded = true
                userPlaylistIds.forEach { playlistId ->
                    tracks.forEach { track ->
                        val result = playlistRepository.addTrackToPlaylist(playlistId, track)
                        if (result is PlaylistMutationResult.Failure) succeeded = false
                    }
                }
                if (succeeded) {
                    if (addToLikedSongs) {
                        callbacks.onSetTracksLiked(tracks, true)
                    }
                    refreshLibraryPlaylistsFromRepository()
                    done(true, duplicateTrackKeys.size)
                } else {
                    done(false, 0)
                }
            }
        },
        onRemoveEntries = { playlistId, entryIds, done ->
            coroutineScope.launch {
                val result = playlistRepository.removeEntriesFromPlaylist(
                    playlistId = playlistId,
                    entryIds = entryIds
                )
                if (result is PlaylistMutationResult.Success) {
                    refreshLibraryPlaylistsFromRepository()
                    done(true)
                } else {
                    done(false)
                }
            }
        }
    )

    CompositionLocalProvider(
        LocalDarkFlowCloudOverlayEnabled provides state.darkFlowCloudOverlayEnabled
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .blur(scaffoldBlurRadius),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                FlowtoneScaffoldTopLayer(
                    state = state,
                    callbacks = callbacks,
                    detailHeaderCollapseProgressState = detailHeaderCollapseProgressState,
                    songSelectionState = songSelectionTopBarState,
                    onCloseSongSelection = { clearSongSelectionRequest += 1 },
                    playlistSortProgress = playlistSortProgress
                )
            }
        ) { innerPadding ->
            val contentInnerPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() +
                    playlistSortPanelHeight * playlistSortProgress.coerceIn(0f, 1f),
                bottom = innerPadding.calculateBottomPadding()
            )
            FlowtoneScaffoldContent(
                state = state,
                callbacks = callbacks,
                mainTabsVisible = mainTabsVisible,
                homeScrollState = homeScrollState,
                topLevelPageCollapseProgress = topLevelPageCollapseProgress,
                libraryPlaylistController = libraryPlaylistController,
                playlistSongEntries = playlistSongEntries,
                playlistBatchActions = playlistBatchActions,
                likedSongCount = likedSongCount,
                onDetailHeaderCollapseProgressStateChange =
                    onDetailHeaderCollapseProgressStateChange,
                playlistSongSort = playlistSongSort,
                playlistSortPanelOpen = playlistSortPanelOpen,
                onClosePlaylistSortPanel = { playlistSortPanelOpen = false },
                innerPadding = contentInnerPadding,
                modifier = Modifier.blur(
                    PlaylistSortContentBlurRadius *
                        playlistSortProgress.coerceIn(0f, 1f)
                )
            )
        }
        val playlistSortAvailable = (
            state.secondaryPage == SecondaryPage.LocalLibrary ||
                (state.secondaryPage == SecondaryPage.Playlist &&
                    state.selectedPlaylistId != null)
            ) && songSelectionTopBarState == null && !state.searchActive
        if (playlistSortPanelOpen || playlistSortProgress > 0f) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = playlistSortListDismissInteractionSource,
                        indication = null,
                        onClick = { playlistSortPanelOpen = false }
                    )
                    .rightSwipeBackGesture { playlistSortPanelOpen = false }
            )
        }
        AnimatedVisibility(
            visible = playlistSortAvailable || playlistSortPanelOpen || playlistSortProgress > 0f,
            enter = fadeIn(
                tween(durationMillis = 180, easing = FlowtoneMotion.Easing)
            ) + slideInHorizontally(
                animationSpec = tween(durationMillis = 260, easing = FlowtoneMotion.Easing)
            ) { playlistActionMotionDistancePx.coerceAtMost(it) },
            exit = fadeOut(
                tween(durationMillis = 140, easing = FlowtoneMotion.Easing)
            ) + slideOutHorizontally(
                animationSpec = tween(durationMillis = 260, easing = FlowtoneMotion.Easing)
            ) { playlistActionMotionDistancePx.coerceAtMost(it) }
        ) {
            PlaylistSortTopBar(
                visible = playlistSortPanelOpen,
                progress = playlistSortProgress,
                sort = playlistSongSort,
                onSortChange = { playlistSongSort = it },
                onVisibleChange = { playlistSortPanelOpen = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    // 排序入口位于 Scaffold 外的独立覆盖层，需要显式继承页面的模糊效果。
                    .blur(scaffoldBlurRadius)
                    .rightSwipeBackGesture { playlistSortPanelOpen = false }
            )
        }
        FlowtoneScaffoldOverlays(
            state = state,
            callbacks = callbacks,
            fullscreenHeight = maxHeight - state.miniPlayerBottomProtection,
            libraryPlaylistController = libraryPlaylistController,
            playlistRepository = playlistRepository,
            coroutineScope = coroutineScope,
            displayedLibraryPlaylists = displayedLibraryPlaylists,
            playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
            addToPlaylistDialogBackgroundColor = addToPlaylistDialogBackgroundColor,
            playlistEditingProgress = playlistEditingProgress,
            playlistEditingBlurRadius = playlistEditingBlurRadius,
            onAddToPlaylistDialogBackgroundColorChange = { color ->
                addToPlaylistDialogBackgroundColor = color
            },
            onRefreshLibraryPlaylistsFromRepository = { createdPlaylistId ->
                refreshLibraryPlaylistsFromRepository(createdPlaylistId)
            }
        )
        }
    }
}

private data class LibraryPlaylistRepositorySyncKey(
    val id: String,
    val title: String,
    val subtitle: String,
    val order: Int,
    val customArtworkUri: String?
)

private fun LibraryPlaylistCard.repositorySyncKey(): LibraryPlaylistRepositorySyncKey {
    return LibraryPlaylistRepositorySyncKey(
        id = id,
        title = title,
        subtitle = subtitle,
        order = order,
        customArtworkUri = customArtworkUri?.toString()
    )
}
