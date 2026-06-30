package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerQueueBottomSheet(
    queue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    onSongClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current
    val handleDismissThresholdPx = with(density) { 48.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            var handleDragY by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .pointerInput(onDismiss, handleDismissThresholdPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                handleDragY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                handleDragY += dragAmount
                                if (dragAmount > 0f) {
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (handleDragY > handleDismissThresholdPx) {
                                    onDismiss()
                                }
                                handleDragY = 0f
                            },
                            onDragCancel = {
                                handleDragY = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "\u64ad\u653e\u961f\u5217",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (queue.isEmpty()) {
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
                        items = queue,
                        key = { index, song -> "${song.id}-${song.uri}-$index" }
                    ) { index, song ->
                        val isCurrentSong = if (currentQueueIndex in queue.indices) {
                            index == currentQueueIndex
                        } else {
                            currentSong != null &&
                                (song.id == currentSong.id || song.uri == currentSong.uri)
                        }

                        SongListItem(
                            song = song,
                            isCurrentSong = isCurrentSong,
                            onClick = {
                                onSongClick(index)
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
