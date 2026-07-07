package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
internal fun SongRecordThresholdRow(
    selectedSeconds: Int,
    onSelectedSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val seconds = selectedSeconds.coerceSongRecordThreshold()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsRowCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { showDialog = true }
            .padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = SettingsRowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "歌曲记录阈值",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "当前：$seconds 秒，播放满后才计入今日听歌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "设置歌曲记录阈值",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        SongRecordThresholdDialog(
            selectedSeconds = seconds,
            onDismiss = { showDialog = false },
            onConfirm = { value ->
                onSelectedSecondsChange(value)
                showDialog = false
            }
        )
    }
}
