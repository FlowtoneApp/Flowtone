package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.listening.formatListeningDuration
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderPlaceholder
import ink.tenqui.flowtone.ui.components.StaggeredPageElement

private val MineListeningRecordCardHeight = 164.dp

@Composable
internal fun MineScreen(
    listeningStats: ListeningStatsSnapshot,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenListeningRecords: (ListeningRecordTab) -> Unit,
    secondaryOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val todayTopSource = listeningStats.today.topSource?.displayName?.takeIf { it.isNotBlank() }
    val totalTopSource = listeningStats.total.topSource?.displayName?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 16.dp)
    ) {
        StaggeredPageElement(
            visible = !secondaryOpen,
            animationIndex = 0
        ) {
            FlowtonePageHeaderPlaceholder()
        }
        Spacer(modifier = Modifier.height(30.dp))
        StaggeredPageElement(
            visible = !secondaryOpen,
            animationIndex = 4
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MineListeningRecordCard(
                    title = "今日听歌",
                    value = "${listeningStats.today.effectivePlayCount} 首",
                    subtitle = "共 ${formatListeningDuration(listeningStats.today.listeningDurationMs)}",
                    description = todayTopSource?.let { "主要来自「$it」" } ?: "暂无主要来源",
                    icon = Icons.Rounded.History,
                    onClick = { onOpenListeningRecords(ListeningRecordTab.Today) },
                    modifier = Modifier.weight(1f)
                )
                MineListeningRecordCard(
                    title = "累计时长",
                    value = formatListeningDuration(listeningStats.total.listeningDurationMs),
                    subtitle = "累计有效播放 ${listeningStats.total.effectivePlayCount} 次",
                    description = totalTopSource?.let { "主要来自「$it」" } ?: "暂无主要来源",
                    icon = Icons.Rounded.Schedule,
                    onClick = { onOpenListeningRecords(ListeningRecordTab.Total) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        MineMenuItem(
            title = "\u8bbe\u7f6e",
            icon = Icons.Rounded.Settings,
            visible = !secondaryOpen,
            animationIndex = 8,
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 16.dp)
        )
        MineMenuItem(
            title = "\u5173\u4e8e",
            icon = Icons.Rounded.Info,
            visible = !secondaryOpen,
            animationIndex = 12,
            onClick = onOpenAbout,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun MineListeningRecordCard(
    title: String,
    value: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(MineListeningRecordCardHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun MineMenuItem(
    title: String,
    icon: ImageVector,
    visible: Boolean,
    animationIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StaggeredPageElement(
        visible = visible,
        animationIndex = animationIndex,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
