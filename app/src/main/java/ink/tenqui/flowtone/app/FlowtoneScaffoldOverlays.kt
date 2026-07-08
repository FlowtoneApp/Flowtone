package ink.tenqui.flowtone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.data.repository.PlaylistMutationResult
import ink.tenqui.flowtone.data.repository.PlaylistRepository
import ink.tenqui.flowtone.ui.library.CreatePlaylistOverlay
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.library.PlaylistDialogVisualStyle
import ink.tenqui.flowtone.ui.player.MiniPlayer
import ink.tenqui.flowtone.ui.screens.FlowCloudSpeedOverlay
import ink.tenqui.flowtone.ui.screens.SongRecordThresholdOverlay
import kotlinx.coroutines.CoroutineScope
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
    onAddToPlaylistDialogBackgroundColorChange: (Color) -> Unit,
    onRefreshLibraryPlaylistsFromRepository: (String?) -> Unit
) {
    if (state.playerUiState.hasCurrentSong && state.backgroundBlurProgress > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f * state.backgroundBlurProgress))
                .clickable(
                    interactionSource = state.noRippleInteractionSource,
                    indication = null,
                    onClick = callbacks.onDismissExpandedPlayer
                )
        )
    }
    MiniPlayer(
        playerUiState = state.playerUiState,
        expanded = state.miniPlayerExpanded,
        onExpandedChange = callbacks.onExpandedChange,
        fullscreen = state.miniPlayerFullscreen,
        onFullscreenChange = callbacks.onFullscreenChange,
        fullscreenHeight = fullscreenHeight,
        allowFullscreenFromCollapsed = state.allowFullscreenFromCollapsed,
        allowFullscreenFromExpanded = true,
        disablePausedArtworkTilt = state.disablePausedArtworkTilt,
        strictProgressBar = state.strictProgressBar,
        flowCloudSpeed = state.flowCloudSpeed,
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
            if (playlist.isLikedSongsPlaylist()) {
                callbacks.onSetSongLiked(currentSong, true)
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
        onOpenArtistRootPage = callbacks.onOpenArtistRootPage,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = state.miniPlayerBottomProtection)
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
                    onRefreshLibraryPlaylistsFromRepository(null)
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
    SongRecordThresholdOverlay(
        dialogState = state.songRecordThresholdDialogState,
        selectedSeconds = state.songRecordThresholdSeconds,
        onDismissRequest = callbacks.onCloseSongRecordThresholdDialog,
        onDismissAnimationFinished = callbacks.onSongRecordThresholdDialogClosed,
        onConfirm = callbacks.onSongRecordThresholdSecondsChange,
        modifier = Modifier.fillMaxSize()
    )
    FlowCloudSpeedOverlay(
        dialogState = state.flowCloudSpeedDialogState,
        selectedSpeed = state.flowCloudSpeed,
        onDismissRequest = callbacks.onCloseFlowCloudSpeedDialog,
        onDismissAnimationFinished = callbacks.onFlowCloudSpeedDialogClosed,
        onConfirm = callbacks.onFlowCloudSpeedChange,
        modifier = Modifier.fillMaxSize()
    )
}
