package ink.tenqui.flowtone.ui.library

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun PlaylistNameDialogTextField(
    playlistName: String,
    dialogLocked: Boolean,
    showTitleError: Boolean,
    addToPlaylistStyle: Boolean,
    contentColor: Color,
    secondaryContentColor: Color,
    onPlaylistNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = playlistName,
        onValueChange = onPlaylistNameChange,
        enabled = !dialogLocked,
        placeholder = {
            Text(
                text = "歌单名称",
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
                    text = "已存在同名歌单",
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
        modifier = modifier
    )
}
