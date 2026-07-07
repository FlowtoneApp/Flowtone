package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CreatePlaylistPanel(
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
                    text = playlistDialogTitleText(mode),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                if (mode == PlaylistDialogMode.Delete) {
                    DeletePlaylistDialogContent(
                        secondaryContentColor = secondaryContentColor,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                } else {
                    PlaylistNameDialogTextField(
                        playlistName = playlistName,
                        dialogLocked = dialogLocked,
                        showTitleError = showTitleError,
                        addToPlaylistStyle = addToPlaylistStyle,
                        contentColor = contentColor,
                        secondaryContentColor = secondaryContentColor,
                        onPlaylistNameChange = onPlaylistNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                PlaylistDialogActions(
                    confirmText = playlistDialogConfirmText(mode),
                    canCreate = canCreate,
                    dialogLocked = dialogLocked,
                    addToPlaylistStyle = addToPlaylistStyle,
                    contentColor = contentColor,
                    onCancel = onCancel,
                    onCreate = onCreate
                )
            }
        }
    }
}

@Composable
private fun PlaylistDialogActions(
    confirmText: String,
    canCreate: Boolean,
    dialogLocked: Boolean,
    addToPlaylistStyle: Boolean,
    contentColor: Color,
    onCancel: () -> Unit,
    onCreate: () -> Unit
) {
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
                text = "取消",
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

private fun playlistDialogTitleText(mode: PlaylistDialogMode): String {
    return when (mode) {
        PlaylistDialogMode.Create -> "创建歌单"
        PlaylistDialogMode.Rename -> "编辑歌单"
        PlaylistDialogMode.Delete -> "删除歌单"
    }
}

private fun playlistDialogConfirmText(mode: PlaylistDialogMode): String {
    return when (mode) {
        PlaylistDialogMode.Create -> "创建"
        PlaylistDialogMode.Rename -> "保存"
        PlaylistDialogMode.Delete -> "删除"
    }
}
