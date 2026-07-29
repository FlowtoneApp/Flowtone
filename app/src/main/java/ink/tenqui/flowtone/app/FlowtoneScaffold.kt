package ink.tenqui.flowtone.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import ink.tenqui.flowtone.ui.library.rememberLibraryPlaylistController
import kotlinx.coroutines.launch

@Composable
internal fun FlowtoneScaffold(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    mainTabsVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
    val likedSongCount = remember(state.uiState.songs, state.likedSongKeys) {
        flowtoneLikedSongCount(
            songs = state.uiState.songs,
            likedSongKeys = state.likedSongKeys
        )
    }
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
        state.likedSongKeys
    ) {
        flowtonePlaylistIdsContainingCurrentSong(
            playlistSongEntries = playlistSongEntries,
            currentSong = state.playerUiState.currentSong,
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
    }

    LaunchedEffect(libraryPlaylistController.editingPlaylistId) {
        if (libraryPlaylistController.editingPlaylistId != null) {
            libraryPlaylistController.listState.stopScroll()
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
        onSetSongsLiked = callbacks.onSetSongsLiked,
        onAddSongsToPlaylists = { playlistIds, songs, done ->
            coroutineScope.launch {
                val userPlaylistIds = playlistIds - LikedSongsPlaylistId
                val addToLikedSongs = LikedSongsPlaylistId in playlistIds
                val currentEntries = playlistRepository.playlistSongEntries.value
                val duplicateSongIds = songs
                    .filter { song ->
                        val songId = song.id.toString()
                        (addToLikedSongs && isSongLiked(song, state.likedSongKeys)) ||
                            currentEntries.any { entry ->
                                entry.playlistId in userPlaylistIds &&
                                    entry.songId == songId
                            }
                    }
                    .mapTo(mutableSetOf()) { song -> song.id.toString() }

                playlistRepository.syncLibraryPlaylistCards(
                    libraryPlaylistController.playlists
                )
                val result = playlistRepository.addSongsToPlaylists(userPlaylistIds, songs)
                if (result is PlaylistMutationResult.Success) {
                    if (addToLikedSongs) {
                        callbacks.onSetSongsLiked(songs, true)
                    }
                    refreshLibraryPlaylistsFromRepository()
                    done(true, duplicateSongIds.size)
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
                    onCloseSongSelection = { clearSongSelectionRequest += 1 }
                )
            }
        ) { innerPadding ->
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
                innerPadding = innerPadding
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

private data class LibraryPlaylistRepositorySyncKey(
    val id: String,
    val title: String,
    val subtitle: String,
    val order: Int
)

private fun LibraryPlaylistCard.repositorySyncKey(): LibraryPlaylistRepositorySyncKey {
    return LibraryPlaylistRepositorySyncKey(
        id = id,
        title = title,
        subtitle = subtitle,
        order = order
    )
}
