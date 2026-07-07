package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Composable
private fun CreatePlaylistPanel(
    playlistName: String,
    canCreate: Boolean,
    showDuplicateNameMessage: Boolean,
    dialogLocked: Boolean,
    mode: PlaylistDialogMode,
    visualStyle: PlaylistDialogVisualStyle,
    addToPlaylistDialogBackgroundColor: Color,
    onPlaylistNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(CreatePlaylistPanelCornerRadius)
    val addToPlaylistStyle = visualStyle == PlaylistDialogVisualStyle.AddToPlaylist
    val containerColor = if (addToPlaylistStyle) {
        addToPlaylistDialogBackgroundColor.copy(alpha = 1f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (addToPlaylistStyle) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryContentColor = if (addToPlaylistStyle) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val panelBorder = if (addToPlaylistStyle) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    } else {
        null
    }
    val shadowElevation = if (addToPlaylistStyle) 0.dp else 18.dp
    val titleText = when (mode) {
        PlaylistDialogMode.Create -> "\u521b\u5efa\u6b4c\u5355"
        PlaylistDialogMode.Rename -> "\u7f16\u8f91\u6b4c\u5355"
        PlaylistDialogMode.Delete -> "\u5220\u9664\u6b4c\u5355"
    }
    val confirmText = when (mode) {
        PlaylistDialogMode.Create -> "\u521b\u5efa"
        PlaylistDialogMode.Rename -> "\u4fdd\u5b58"
        PlaylistDialogMode.Delete -> "\u5220\u9664"
    }
    val showTitleError = showDuplicateNameMessage && !dialogLocked

    Surface(
        modifier = modifier,
        shape = panelShape,
        color = containerColor,
        border = panelBorder,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                if (mode == PlaylistDialogMode.Delete) {
                    Text(
                        text = "\u786e\u5b9a\u8981\u5220\u9664\u8fd9\u4e2a\u6b4c\u5355\u5417\uff1f",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContentColor,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = onPlaylistNameChange,
                        enabled = !dialogLocked,
                        placeholder = {
                            Text(
                                text = "\u6b4c\u5355\u540d\u79f0",
                                color = secondaryContentColor
                            )
                        },
                        singleLine = true,
                        textStyle = if (addToPlaylistStyle) {
                            LocalTextStyle.current.copy(color = contentColor)
                        } else {
                            LocalTextStyle.current
                        },
                        supportingText = if (showTitleError) {
                            {
                                Text(
                                    text = "\u5df2\u5b58\u5728\u540c\u540d\u6b4c\u5355",
                                    color = if (addToPlaylistStyle) {
                                        secondaryContentColor
                                    } else {
                                        Color.Unspecified
                                    }
                                )
                            }
                        } else {
                            null
                        },
                        isError = showTitleError,
                        colors = if (addToPlaylistStyle) {
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = contentColor,
                                unfocusedTextColor = contentColor,
                                disabledTextColor = contentColor.copy(alpha = 0.72f),
                                errorTextColor = contentColor,
                                focusedBorderColor = contentColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.58f),
                                disabledBorderColor = Color.White.copy(alpha = 0.34f),
                                errorBorderColor = contentColor,
                                focusedPlaceholderColor = secondaryContentColor,
                                unfocusedPlaceholderColor = secondaryContentColor,
                                disabledPlaceholderColor = secondaryContentColor,
                                errorPlaceholderColor = secondaryContentColor,
                                cursorColor = contentColor,
                                errorCursorColor = contentColor,
                                focusedLabelColor = contentColor,
                                unfocusedLabelColor = secondaryContentColor,
                                disabledLabelColor = secondaryContentColor,
                                errorLabelColor = contentColor
                            )
                        } else {
                            OutlinedTextFieldDefaults.colors()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !dialogLocked
                    ) {
                        Text(
                            text = "\u53d6\u6d88",
                            color = if (addToPlaylistStyle) contentColor else Color.Unspecified
                        )
                    }
                    Button(
                        onClick = onCreate,
                        enabled = canCreate && !dialogLocked,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(text = confirmText)
                    }
                }
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
