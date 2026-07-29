package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.pullToDismissAtTop

@Composable
internal fun PlayerQueueList(
    displayOrder: QueueDisplayOrder,
    displayedQueue: List<Song>,
    playbackQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    queueListState: LazyListState,
    dismissStarted: Boolean,
    interactionEnabled: Boolean,
    onViewportHeightChanged: (Int) -> Unit,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = queueListState,
        userScrollEnabled = interactionEnabled,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { onViewportHeightChanged(it.height) }
            .pullToDismissAtTop(
                listState = queueListState,
                enabled = interactionEnabled && !dismissStarted,
                threshold = PlayerQueueListPullDismissThreshold,
                onDismiss = onDismiss
            )
    ) {
        itemsIndexed(
            items = displayedQueue,
            // 播放队列允许同一首歌曲出现多次，位置参与 key 可避免 Compose 重复键崩溃。
            key = { index, song -> song.queueItemKey(index) }
        ) { index, song ->
            val isCurrentSong = when {
                currentSong != null -> song.id == currentSong.id || song.uri == currentSong.uri
                displayOrder == QueueDisplayOrder.PlaybackOrder &&
                    currentQueueIndex in playbackQueue.indices -> {
                    index == currentQueueIndex
                }
                else -> false
            }

            PlayerQueueItem(
                song = song,
                isCurrentSong = isCurrentSong,
                onClick = if (interactionEnabled) {
                    onSongClick
                } else {
                    { _ -> }
                }
            )
        }
    }
}
