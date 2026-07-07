package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerProgressBarLabels(
    displayTimePositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 0.dp)
            .offset(y = PlaybackProgressLabelOffsetY),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val timeColor = Color.White.copy(alpha = 0.90f)
        Text(
            text = formatPlaybackTime(displayTimePositionMs),
            style = MaterialTheme.typography.labelSmall,
            color = timeColor,
            maxLines = 1
        )
        Text(
            text = formatPlaybackTime(durationMs.coerceAtLeast(0L)),
            style = MaterialTheme.typography.labelSmall,
            color = timeColor,
            maxLines = 1
        )
    }
}
