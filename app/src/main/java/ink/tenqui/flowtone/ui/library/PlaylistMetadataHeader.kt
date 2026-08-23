package ink.tenqui.flowtone.ui.library

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork

private val PlaylistMetadataArtworkSize = 128.dp

internal data class PlaylistDetailMetadata(
    val title: String,
    val creatorName: String? = null,
    val description: String? = null,
    val customArtworkUri: Uri? = null,
    val isDescriptionEditable: Boolean = false
)

@Composable
internal fun PlaylistMetadataHeader(
    metadata: PlaylistDetailMetadata,
    songCount: Int,
    artworkUri: Uri?,
    isDescriptionEditing: Boolean = false,
    onDescriptionEditingChange: (Boolean) -> Unit = {},
    onDescriptionChange: (String?) -> Unit = {},
    onDescriptionEditEndRequestChange: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val creator = metadata.creatorName?.trim()?.ifBlank { null }
    val summary = metadata.description?.trim()?.ifBlank { null }
    var savedDescription by remember(metadata.description) {
        mutableStateOf(summary)
    }
    var draftDescription by remember(metadata.description) {
        val text = summary.orEmpty()
        mutableStateOf(
            TextFieldValue(
                text = text,
                selection = TextRange(text.length)
            )
        )
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val headerInteractionSource = remember { MutableInteractionSource() }
    fun endDescriptionEditing() {
        onDescriptionEditingChange(false)
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    fun saveDescriptionEditing() {
        if (!isDescriptionEditing) return
        val normalizedDescription = draftDescription.text.trim().ifBlank { null }
        val changed = normalizedDescription != savedDescription
        Log.d(
            "FlowtonePlaylistDebug",
            "DESCRIPTION_SAVE_REQUESTED changed=$changed " +
                "oldLength=${savedDescription?.length ?: 0} " +
                "newLength=${normalizedDescription?.length ?: 0}"
        )
        if (changed) {
            savedDescription = normalizedDescription
            onDescriptionChange(normalizedDescription)
        }
        draftDescription = TextFieldValue(
            text = normalizedDescription.orEmpty(),
            selection = TextRange(normalizedDescription?.length ?: 0)
        )
        endDescriptionEditing()
    }
    fun cancelDescriptionEditing() {
        if (!isDescriptionEditing) return
        Log.d("FlowtonePlaylistDebug", "DESCRIPTION_CANCEL_REQUESTED")
        draftDescription = TextFieldValue(
            text = savedDescription.orEmpty(),
            selection = TextRange(savedDescription?.length ?: 0)
        )
        endDescriptionEditing()
    }

    if (metadata.isDescriptionEditable) {
        DisposableEffect(isDescriptionEditing) {
            onDescriptionEditEndRequestChange(
                if (isDescriptionEditing) ::saveDescriptionEditing else null
            )
            onDispose {
                onDescriptionEditEndRequestChange(null)
            }
        }
        LaunchedEffect(isDescriptionEditing) {
            if (isDescriptionEditing) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    } else {
        onDescriptionEditEndRequestChange(null)
    }
    LaunchedEffect(metadata.isDescriptionEditable, isDescriptionEditing, summary != null) {
        Log.d(
            "FlowtonePlaylistDebug",
            "HEADER_COMPOSE editable=${metadata.isDescriptionEditable} " +
                "editing=$isDescriptionEditing hasDescription=${summary != null}"
        )
    }
    LaunchedEffect(isDescriptionEditing) {
        if (isDescriptionEditing) {
            Log.d("FlowtonePlaylistDebug", "DESCRIPTION_EDITOR_COMPOSED")
        }
    }
    val countLabel = "$songCount 首歌曲"
    val infoLabel = listOfNotNull(creator, countLabel).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .then(
                if (isDescriptionEditing) {
                    Modifier.clickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                        onClick = ::saveDescriptionEditing
                    )
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = infoLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (metadata.isDescriptionEditable && isDescriptionEditing) {
                BasicTextField(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            Log.d(
                                "FlowtonePlaylistDebug",
                                "DESCRIPTION_FOCUS_CHANGED focused=${focusState.isFocused}"
                            )
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    decorationBox = { innerTextField ->
                        if (draftDescription.text.isBlank()) {
                            Text(
                                text = "点击以添加简介",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.62f
                                )
                            )
                        }
                        innerTextField()
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    DescriptionActionButton(
                        label = "取消",
                        icon = Icons.Filled.Close,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = ::cancelDescriptionEditing
                    )
                    DescriptionActionButton(
                        label = "保存",
                        icon = Icons.Filled.Check,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = ::saveDescriptionEditing
                    )
                }
            } else if (metadata.isDescriptionEditable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp)
                        .padding(top = 10.dp)
                        .clickable(
                            interactionSource = headerInteractionSource,
                            indication = null,
                            onClick = {
                                Log.d("FlowtonePlaylistDebug", "DESCRIPTION_TEXT_CLICKED")
                                onDescriptionEditingChange(true)
                                Log.d(
                                    "FlowtonePlaylistDebug",
                                    "DESCRIPTION_EDITING_REQUESTED"
                                )
                            }
                        )
                ) {
                    Text(
                        text = summary ?: "点击以添加简介",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (summary == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                summary?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
        FlowtoneArtwork(
            artworkUri = artworkUri,
            modifier = Modifier.size(PlaylistMetadataArtworkSize)
        )
    }
}

@Composable
private fun DescriptionActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}
