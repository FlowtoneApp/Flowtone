package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.lyrics.LyricsFolder
import ink.tenqui.flowtone.ui.components.OptionGroup

@Composable
internal fun LyricsFolderSettingsPage(
    folders: List<LyricsFolder>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (LyricsFolder) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OptionGroup(
            title = "歌词文件夹",
            modifier = elementModifier(0)
        ) {
            Text(
                text = "这些文件夹中的歌词可用于匹配本地歌曲。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (folders.isEmpty()) {
                Text(
                    text = "尚未添加歌词文件夹",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Flowtone 会优先读取歌曲同目录的歌词，也可以从这里添加其他歌词目录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                folders.forEachIndexed { index, folder ->
                    LyricsFolderRow(
                        folder = folder,
                        onRemove = { onRemoveFolder(folder) },
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp)
                    )
                }
            }
            Button(
                onClick = onAddFolder,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加文件夹")
            }
        }
    }
}

@Composable
internal fun LyricsFoldersSettingRow(
    folders: List<LyricsFolder>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inaccessibleCount = folders.count { !it.isAccessible }
    val subtitle = when (folders.size) {
        0 -> "未添加歌词文件夹"
        1 -> "已添加 1 个文件夹"
        else -> "已添加 ${folders.size} 个文件夹"
    }.let { base ->
        if (inaccessibleCount > 0) "$base，$inaccessibleCount 个无法访问" else base
    }
    SettingsSectionRow(
        title = "歌词文件夹",
        subtitle = subtitle,
        icon = Icons.Rounded.Folder,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun LyricsFolderRow(
    folder: LyricsFolder,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(SettingsRowCornerRadius)
            )
            .clickable(enabled = false) {}
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = if (folder.isAccessible) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.displayName, style = MaterialTheme.typography.bodyLarge)
            folder.location?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                if (folder.isAccessible) "已授权" else "需要重新授权",
                style = MaterialTheme.typography.bodySmall,
                color = if (folder.isAccessible) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除文件夹")
        }
    }
}
