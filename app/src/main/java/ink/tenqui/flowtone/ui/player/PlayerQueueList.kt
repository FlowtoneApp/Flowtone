package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.pullToDismissAtTop

@Composable
internal fun PlayerQueueList(
    displayOrder: QueueDisplayOrder,
    playbackQueue: List<Song>,
    sourceQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    queueListState: LazyListState,
    dismissStarted: Boolean,
    onViewportHeightChanged: (Int) -> Unit,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = displayOrder,
        transitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        label = "QueueDisplayOrderListTransition",
        modifier = modifier
    ) { animatedOrder ->
        val animatedQueue = when (animatedOrder) {
            QueueDisplayOrder.PlaybackOrder -> playbackQueue
            QueueDisplayOrder.ListOrder -> sourceQueue.ifEmpty { playbackQueue }
        }

        fun Modifier.queueItemAnimation(animationIndex: Int): Modifier {
            val delayMillis = FlowtoneMotion.staggerDelayMillis(animationIndex)
            val durationMillis = FlowtoneMotion.staggerDurationMillis(animationIndex)
            return animateEnterExit(
                enter = fadeIn(
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtoneMotion.Easing
                    )
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtoneMotion.Easing
                    )
                ) { it / 6 },
                exit = fadeOut(
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtoneMotion.Easing
                    )
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtoneMotion.Easing
                    )
                ) { -it / 6 }
            )
        }

        LazyColumn(
            state = queueListState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { onViewportHeightChanged(it.height) }
                .pullToDismissAtTop(
                    listState = queueListState,
                    enabled = !dismissStarted,
                    threshold = PlayerQueueListPullDismissThreshold,
                    onDismiss = onDismiss
                )
        ) {
            itemsIndexed(
                items = animatedQueue,
                key = { index, song -> "${song.id}-${song.uri}-$index-${animatedOrder.name}" }
            ) { index, song ->
                val visibleAnimationIndex = (
                    index - queueListState.firstVisibleItemIndex
                    ).coerceIn(0, 10)
                val isCurrentSong = when {
                    currentSong != null -> song.id == currentSong.id || song.uri == currentSong.uri
                    animatedOrder == QueueDisplayOrder.PlaybackOrder &&
                        currentQueueIndex in playbackQueue.indices -> {
                        index == currentQueueIndex
                    }
                    else -> false
                }

                PlayerQueueItem(
                    song = song,
                    isCurrentSong = isCurrentSong,
                    onClick = onSongClick,
                    modifier = Modifier.queueItemAnimation(visibleAnimationIndex)
                )
            }
        }
    }
}
