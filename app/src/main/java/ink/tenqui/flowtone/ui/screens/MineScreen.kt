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
import ink.tenqui.flowtone.ui.components.StaggeredPageElement

private val MineListeningRecordCardHeight = 132.dp

@Composable
internal fun MineScreen(
    listeningStats: ListeningStatsSnapshot,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    secondaryOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        StaggeredPageElement(
            visible = !secondaryOpen,
            animationIndex = 0
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MineListeningRecordCard(
                    title = "今日听歌",
                    value = "${listeningStats.todaySongCount} 首",
                    subtitle = "今日播放记录",
                    icon = Icons.Rounded.History,
                    modifier = Modifier.weight(1f)
                )
                MineListeningRecordCard(
                    title = "累计时长",
                    value = formatListeningDuration(listeningStats.totalListeningDurationMs),
                    subtitle = "本地记录统计",
                    icon = Icons.Rounded.Schedule,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        MineMenuItem(
            title = "\u8bbe\u7f6e",
            icon = Icons.Rounded.Settings,
            visible = !secondaryOpen,
            animationIndex = 1,
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 16.dp)
        )
        MineMenuItem(
            title = "\u5173\u4e8e",
            icon = Icons.Rounded.Info,
            visible = !secondaryOpen,
            animationIndex = 2,
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
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(MineListeningRecordCardHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun formatListeningDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    if (totalMinutes < 60L) {
        return "$totalMinutes 分钟"
    }

    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (minutes == 0L) {
        "$hours 小时"
    } else {
        "$hours 小时 $minutes 分"
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
