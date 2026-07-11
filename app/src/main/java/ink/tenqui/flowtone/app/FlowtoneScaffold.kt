package ink.tenqui.flowtone.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ink.tenqui.flowtone.data.local.PlaylistStorage
import ink.tenqui.flowtone.data.repository.PlaylistRepository
import ink.tenqui.flowtone.ui.library.rememberLibraryPlaylistController

@Composable
internal fun FlowtoneScaffold(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
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
                .blur(state.backgroundBlurRadius),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                FlowtoneScaffoldTopLayer(
                    state = state,
                    callbacks = callbacks
                )
            }
        ) { innerPadding ->
            FlowtoneScaffoldContent(
                state = state,
                callbacks = callbacks,
                homeScrollState = homeScrollState,
                topLevelPageCollapseProgress = topLevelPageCollapseProgress,
                libraryPlaylistController = libraryPlaylistController,
                playlistSongEntries = playlistSongEntries,
                likedSongCount = likedSongCount,
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
            onAddToPlaylistDialogBackgroundColorChange = { color ->
                addToPlaylistDialogBackgroundColor = color
            },
            onRefreshLibraryPlaylistsFromRepository = { createdPlaylistId ->
                refreshLibraryPlaylistsFromRepository(createdPlaylistId)
            }
        )
    }
}
