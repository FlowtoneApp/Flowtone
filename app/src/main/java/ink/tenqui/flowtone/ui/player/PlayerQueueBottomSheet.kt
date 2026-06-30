package ink.tenqui.flowtone.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem
import kotlinx.coroutines.delay

internal enum class QueueDisplayOrder(val label: String) {
    PlaybackOrder("\u64ad\u653e\u987a\u5e8f"),
    ListOrder("\u5217\u8868\u987a\u5e8f")
}

@Composable
internal fun PlayerQueueBottomSheet(
    playbackQueue: List<Song>,
    sourceQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    backgroundImageRequest: ImageRequest?,
    cloudColors: List<Color>,
    backgroundProgress: Float,
    isPlaying: Boolean,
    waitForArtworkLoad: Boolean,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayOrder by rememberSaveable {
        mutableStateOf(QueueDisplayOrder.PlaybackOrder)
    }
    val displayedQueue = when (displayOrder) {
        QueueDisplayOrder.PlaybackOrder -> playbackQueue
        QueueDisplayOrder.ListOrder -> sourceQueue.ifEmpty { playbackQueue }
    }
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val queueListState = rememberLazyListState()
    val density = LocalDensity.current
    val pullToDismissThresholdPx = with(density) { 64.dp.toPx() }

    fun requestDismiss() {
        if (!dismissStarted) {
            dismissStarted = true
            sheetVisible = false
        }
    }

    BackHandler(enabled = true) {
        requestDismiss()
    }

    LaunchedEffect(Unit) {
        sheetVisible = true
    }
    LaunchedEffect(sheetVisible, dismissStarted) {
        if (dismissStarted && !sheetVisible) {
            delay(220L)
            onDismiss()
        }
    }
    val pullToDismissConnection = remember(queueListState, pullToDismissThresholdPx) {
        var pullDistancePx = 0f

        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val listAtTop = queueListState.firstVisibleItemIndex == 0 &&
                    queueListState.firstVisibleItemScrollOffset == 0

                if (!dismissStarted && listAtTop && available.y > 0f) {
                    pullDistancePx += available.y
                    if (pullDistancePx >= pullToDismissThresholdPx) {
                        requestDismiss()
                    }
                } else if (!listAtTop || available.y < 0f || consumed.y < 0f) {
                    pullDistancePx = 0f
                }

                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = { requestDismiss() }
            )
    ) {
        val sheetHeight = maxHeight * 0.688f

        AnimatedVisibility(
            visible = sheetVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialAlpha = 0f
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> fullHeight + 80 }
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) + slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { fullHeight -> fullHeight + 80 }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .clip(sheetShape)
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = {}
                    )
            ) {
                PlayerQueueGlassBackground(
                    imageRequest = backgroundImageRequest,
                    cloudColors = cloudColors,
                    progress = backgroundProgress,
                    isPlaying = isPlaying,
                    waitForArtworkLoad = waitForArtworkLoad,
                    shape = sheetShape,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .padding(start = 16.dp, top = 20.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                                text = "${displayedQueue.size}\u9996",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.58f),
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                        }
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
                                color = Color.White
                            )
                        }
                    } else {
                        LazyColumn(
                            state = queueListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .nestedScroll(pullToDismissConnection)
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
                                    titleColor = Color.White,
                                    artistColor = Color.White,
                                    durationColor = Color.White,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerQueueGlassBackground(
    imageRequest: ImageRequest?,
    cloudColors: List<Color>,
    progress: Float,
    isPlaying: Boolean,
    waitForArtworkLoad: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        BlurredArtworkBackground(
            imageRequest = imageRequest,
            alpha = lerpFloat(0.78f, 0f, progress),
            waitForArtworkLoad = waitForArtworkLoad,
            modifier = Modifier.matchParentSize()
        )
        CrossfadeFlowCloudBackground(
            colors = cloudColors,
            progress = progress,
            isPlaying = isPlaying,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, progress)))
        )
    }
}

@Composable
private fun QueueDisplayOrderSelector(
    selectedOrder: QueueDisplayOrder,
    onOrderSelected: (QueueDisplayOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextOrder = when (selectedOrder) {
        QueueDisplayOrder.PlaybackOrder -> QueueDisplayOrder.ListOrder
        QueueDisplayOrder.ListOrder -> QueueDisplayOrder.PlaybackOrder
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .clickable { onOrderSelected(nextOrder) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selectedOrder.label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Black
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "\u5207\u6362\u961f\u5217\u987a\u5e8f",
            tint = Color.Black.copy(alpha = 0.76f),
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
        )
    }
}
