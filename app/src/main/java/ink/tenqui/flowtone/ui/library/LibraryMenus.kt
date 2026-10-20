package ink.tenqui.flowtone.ui.library


import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import kotlinx.coroutines.launch

@Composable
internal fun CreatePlaylistOverlay(
    playlistController: LibraryPlaylistController,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val createPlaylistState = playlistController.createPlaylistState
    val coroutineScope = rememberCoroutineScope()
    val scrimMaxAlpha = if (
        playlistController.playlistDialogVisualStyle == PlaylistDialogVisualStyle.AddToPlaylist
    ) {
        0.34f
    } else {
        CreatePlaylistScrimMaxAlpha
    }
    FlowtoneModalOverlayShell(
        visible = createPlaylistState == CreatePlaylistState.Editing,
        scrimMaxAlpha = scrimMaxAlpha,
        shadowSafePadding = CreatePlaylistShadowSafePadding,
        onExitAnimationFinished = {
            if (playlistController.createPlaylistState == CreatePlaylistState.Closing) {
                playlistController.resetCreateState()
                coroutineScope.launch {
                    playlistController.savePlaylistsIfRequested()
                }
            }
        },
        onDismissRequest = {
            if (!playlistController.dialogLocked) {
                playlistController.closeEditing()
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        val availableWidth = maxWidth - CreatePlaylistShadowSafePadding -
            CreatePlaylistShadowSafePadding
        val panelWidth = when {
            availableWidth < CreatePlaylistPanelMinWidth -> availableWidth
            availableWidth > CreatePlaylistPanelMaxWidth -> CreatePlaylistPanelMaxWidth
            else -> availableWidth
        }

        CreatePlaylistPanel(
            playlistName = playlistController.playlistName,
            canCreate = playlistController.canCreatePlaylist,
            showDuplicateNameMessage = playlistController.duplicatePlaylistName,
            dialogLocked = playlistController.dialogLocked,
            mode = playlistController.playlistDialogMode,
            visualStyle = playlistController.playlistDialogVisualStyle,
            onPlaylistNameChange = { value ->
                playlistController.playlistName = value
            },
            onCancel = {
                playlistController.closeEditing()
            },
            onCreate = {
                when (playlistController.playlistDialogMode) {
                    PlaylistDialogMode.Create -> {
                        val title = playlistController.playlistName.trim()
                        if (playlistController.canCreatePlaylist) {
                            playlistController.lockDialog()
                            onCreatePlaylist(title)
                        }
                    }
                    PlaylistDialogMode.Rename -> {
                        val playlist = playlistController.dialogPlaylist
                        val title = playlistController.playlistName.trim()
                        if (
                            playlist != null &&
                            playlistController.canCreatePlaylist
                        ) {
                            playlistController.lockDialog()
                            onRenamePlaylist(playlist.id, title)
                        }
                    }
                    PlaylistDialogMode.Delete -> {
                        if (playlistController.canCreatePlaylist) {
                            playlistController.lockDialog()
                        }
                        playlistController.dialogPlaylist?.let { playlist ->
                            onDeletePlaylist(playlist.id)
                        }
                    }
                }
            },
            modifier = Modifier
                .width(panelWidth)
                .height(CreatePlaylistPanelHeight)
        )
    }
}