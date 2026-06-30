package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem

internal enum class QueueDisplayOrder(val label: String) {
    PlaybackOrder("\u64ad\u653e\u987a\u5e8f"),
    ListOrder("\u5217\u8868\u987a\u5e8f")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerQueueBottomSheet(
    playbackQueue: List<Song>,
    sourceQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayOrder by rememberSaveable {
        mutableStateOf(QueueDisplayOrder.PlaybackOrder)
    }
    val displayedQueue = when (displayOrder) {
        QueueDisplayOrder.PlaybackOrder -> playbackQueue
        QueueDisplayOrder.ListOrder -> sourceQueue.ifEmpty { playbackQueue }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 20.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u64ad\u653e\u961f\u5217",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                QueueDisplayOrderSelector(
                    selectedOrder = displayOrder,
                    onOrderSelected = { displayOrder = it }
                )
            }

            if (displayedQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u6682\u65e0\u64ad\u653e\u961f\u5217",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(
                        items = displayedQueue,
                        key = { index, song -> "${song.id}-${song.uri}-$index-${displayOrder.name}" }
                    ) { index, song ->
                        val isCurrentSong = when {
                            currentSong != null -> song.id == currentSong.id || song.uri == currentSong.uri
                            displayOrder == QueueDisplayOrder.PlaybackOrder &&
                                currentQueueIndex in playbackQueue.indices -> {
                                index == currentQueueIndex
                            }
                            else -> false
                        }

                        SongListItem(
                            song = song,
                            isCurrentSong = isCurrentSong,
                            onClick = onSongClick,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDisplayOrderSelector(
    selectedOrder: QueueDisplayOrder,
    onOrderSelected: (QueueDisplayOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        QueueDisplayOrder.entries.forEach { order ->
            val selected = selectedOrder == order
            Text(
                text = order.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .clickable { onOrderSelected(order) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
