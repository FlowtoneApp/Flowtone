package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.ProviderSongLikedStatus
import ink.tenqui.flowtone.ui.components.PlaylistCardSurface
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType
import kotlinx.coroutines.launch

@Composable
internal fun FavoriteManagementDialog(
    localLiked: Boolean,
    localAvailable: Boolean,
    providerName: String?,
    providerColor: String?,
    providerStatus: ProviderSongLikedStatus?,
    providerWritable: Boolean,
    onSetLocal: (Boolean) -> Unit,
    onSetProvider: suspend (Boolean) -> Boolean,
    onDismiss: () -> Unit
) {
    var localBase by remember { mutableStateOf(localLiked) }
    var localSelected by remember { mutableStateOf(localLiked) }
    var remoteBase by remember { mutableStateOf(providerStatus.toLikedBoolean()) }
    var remoteSelected by remember { mutableStateOf(remoteBase) }
    var remoteEdited by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(providerStatus) {
        if (!remoteEdited && !busy) {
            remoteBase = providerStatus.toLikedBoolean()
            remoteSelected = remoteBase
        }
    }
    val providerEditable = providerWritable && providerStatus.toLikedBoolean() != null
    val changes = favoriteChanges(localBase, localSelected, localAvailable, remoteBase, remoteSelected, providerEditable)
    val localChanged = changes.local
    val remoteChanged = changes.remote
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("收藏这首歌曲") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FavoriteDestinationCard("声流", localSelected, localAvailable && !busy, null, Modifier.weight(1f)) { localSelected = !localSelected }
                    if (providerName != null) {
                        val parsedColor = remember(providerColor) {
                            runCatching { Color(android.graphics.Color.parseColor(providerColor)) }.getOrNull()
                        }
                        FavoriteDestinationCard(providerName, remoteSelected == true, providerEditable && remoteBase != null && !busy, parsedColor ?: MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            remoteSelected = remoteSelected != true
                            remoteEdited = true
                        }
                    }
                }
                if (!localAvailable) Text("当前歌曲没有可持久收藏的身份", style = MaterialTheme.typography.bodySmall)
                if (providerName != null && (!providerEditable || remoteBase == null)) {
                    Text(if (providerStatus.toLikedBoolean() == null) "来源喜欢状态未知" else "来源喜欢状态为只读", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
        confirmButton = {
            Button(enabled = !busy && (localChanged || remoteChanged), onClick = {
                if (localChanged) {
                    onSetLocal(localSelected)
                    localBase = localSelected
                }
                if (remoteChanged) {
                    busy = true
                    error = null
                    val target = remoteSelected == true
                    scope.launch {
                        if (onSetProvider(target)) {
                            remoteBase = target
                            remoteEdited = false
                            busy = false
                            onDismiss()
                        } else {
                            busy = false
                            error = "来源收藏更新失败，请重试"
                        }
                    }
                } else onDismiss()
            }) { Text(if (busy) "处理中…" else "确定") }
        }
    )
}

internal data class FavoriteChanges(val local: Boolean, val remote: Boolean)

internal fun favoriteChanges(localBase: Boolean, localSelected: Boolean, localAvailable: Boolean,
    remoteBase: Boolean?, remoteSelected: Boolean?, remoteWritable: Boolean): FavoriteChanges =
    FavoriteChanges(localAvailable && localSelected != localBase,
        remoteWritable && remoteBase != null && remoteSelected != remoteBase)

private fun ProviderSongLikedStatus?.toLikedBoolean(): Boolean? = when (this) {
    ProviderSongLikedStatus.Liked -> true
    ProviderSongLikedStatus.NotLiked -> false
    else -> null
}

@Composable
private fun FavoriteDestinationCard(title: String, selected: Boolean, enabled: Boolean, color: Color?, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    PlaylistCardSurface(
        visualType = PlaylistCardVisualType.LikedMusic,
        shape = shape,
        contentPadding = PaddingValues(12.dp),
        isFlowCloudPlaying = false,
        cloudBaseColor = color,
        modifier = modifier.height(94.dp).border(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), shape),
        clickModifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) { colors ->
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = colors.titleColor, modifier = Modifier.align(Alignment.BottomStart))
        if (selected) Icon(Icons.Default.Check, contentDescription = "已选择", tint = colors.iconColor, modifier = Modifier.align(Alignment.TopEnd))
    }
}
