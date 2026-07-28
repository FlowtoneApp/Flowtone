package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import ink.tenqui.flowtone.playback.PlaybackOrderMode

@Composable
internal fun PlayerQueueHeader(
    queueSize: Int,
    playbackOrderMode: PlaybackOrderMode,
    displayOrder: QueueDisplayOrder,
    onDisplayOrderChange: (QueueDisplayOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = PlayerQueueHeaderBottomPadding),
        verticalAlignment = Alignment.Bottom
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "\u64ad\u653e\u961f\u5217",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${queueSize}\u9996",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.58f),
                modifier = Modifier.padding(
                    start = PlayerQueueHeaderCountStartPadding,
                    bottom = PlayerQueueHeaderCountBottomPadding
                )
            )
        }
        if (playbackOrderMode == PlaybackOrderMode.Shuffle) {
            QueueDisplayOrderSelector(
                selectedOrder = displayOrder,
                onOrderSelected = onDisplayOrderChange
            )
        }
    }
}
