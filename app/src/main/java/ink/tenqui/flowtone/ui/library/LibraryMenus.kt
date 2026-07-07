package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

@Composable
internal fun CreatePlaylistOverlay(
    playlistController: LibraryPlaylistController,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    addToPlaylistDialogBackgroundColor: Color = Color(0xFF1B1B20),
    modifier: Modifier = Modifier
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val createPlaylistState = playlistController.createPlaylistState
    val scrimMaxAlpha = if (
        playlistController.playlistDialogVisualStyle == PlaylistDialogVisualStyle.AddToPlaylist
    ) {
        0.34f
    } else {
        CreatePlaylistScrimMaxAlpha
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (createPlaylistState == CreatePlaylistState.Editing) {
            scrimMaxAlpha
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "CreatePlaylistScrim"
    )

    LaunchedEffect(createPlaylistState) {
        when (createPlaylistState) {
            CreatePlaylistState.Editing -> {
                playlistController.panelProgress.snapTo(0f)
                playlistController.panelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
            }

            CreatePlaylistState.Closing -> {
                playlistController.panelProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis / 2,
                        easing = FlowtoneMotion.Easing
                    )
                )
                if (playlistController.createPlaylistState == CreatePlaylistState.Closing) {
                    playlistController.resetCreateState()
                    playlistController.panelProgress.snapTo(0f)
                    playlistController.savePlaylistsIfRequested()
                }
            }

            CreatePlaylistState.Idle -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        if (scrimAlpha > 0.001f || createPlaylistState != CreatePlaylistState.Idle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = {
                            if (!playlistController.dialogLocked) {
                                playlistController.closeEditing()
                            }
                        }
                    )
            )
        }

        if (
            createPlaylistState == CreatePlaylistState.Editing ||
            createPlaylistState == CreatePlaylistState.Closing
        ) {
            val availableWidth = maxWidth - CreatePlaylistShadowSafePadding -
                CreatePlaylistShadowSafePadding
            val panelWidth = when {
                availableWidth < CreatePlaylistPanelMinWidth -> availableWidth
                availableWidth > CreatePlaylistPanelMaxWidth -> CreatePlaylistPanelMaxWidth
                else -> availableWidth
            }

            val panelMinScale = if (createPlaylistState == CreatePlaylistState.Closing) {
                CreatePlaylistPanelExitScale
            } else {
                CreatePlaylistPanelStartScale
            }
            val panelScale = lerpFloat(
                start = panelMinScale,
                stop = 1f,
                fraction = playlistController.panelProgress.value.coerceIn(0f, 1f)
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = playlistController.panelProgress.value.coerceIn(0f, 1f)
                            scaleX = panelScale
                            scaleY = panelScale
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                        .padding(CreatePlaylistShadowSafePadding),
                    contentAlignment = Alignment.Center
                ) {
                    CreatePlaylistPanel(
                        playlistName = playlistController.playlistName,
                        canCreate = playlistController.canCreatePlaylist,
                        showDuplicateNameMessage = playlistController.duplicatePlaylistName,
                        dialogLocked = playlistController.dialogLocked,
                        mode = playlistController.playlistDialogMode,
                        visualStyle = playlistController.playlistDialogVisualStyle,
                        addToPlaylistDialogBackgroundColor =
                            addToPlaylistDialogBackgroundColor,
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
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
