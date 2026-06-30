package ink.tenqui.flowtone.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.SongListItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class QueueDisplayOrder(val label: String) {
    PlaybackOrder("\u64ad\u653e\u987a\u5e8f"),
    ListOrder("\u5217\u8868\u987a\u5e8f")
}

private const val CurrentSongViewportFraction = 0.312f

@Composable
internal fun PlayerQueueBottomSheet(
    playbackQueue: List<Song>,
    sourceQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    displayOrder: QueueDisplayOrder = QueueDisplayOrder.PlaybackOrder,
    onDisplayOrderChange: (QueueDisplayOrder) -> Unit = {},
    backgroundImageRequest: ImageRequest?,
    cloudColors: List<Color>,
    backgroundProgress: Float,
    isPlaying: Boolean,
    waitForArtworkLoad: Boolean,
    onSongClick: (Song) -> Unit,
    onDismissStart: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedQueue = queueForDisplayOrder(
        displayOrder = displayOrder,
        playbackQueue = playbackQueue,
        sourceQueue = sourceQueue
    )
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val queueListState = rememberLazyListState()
    var queueViewportHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val pullToDismissThresholdPx = with(density) { 64.dp.toPx() }

    fun requestDismiss() {
        if (!dismissStarted) {
            dismissStarted = true
            sheetVisible = false
            onDismissStart()
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
            delay(MINI_PLAYER_ANIMATION_DURATION_MS.toLong())
            onDismiss()
        }
    }
    LaunchedEffect(
        sheetVisible,
        displayOrder,
        currentQueueIndex,
        currentSong?.id,
        currentSong?.uri,
        playbackQueue.size,
        sourceQueue.size,
        queueViewportHeightPx
    ) {
        val queue = queueForDisplayOrder(
            displayOrder = displayOrder,
            playbackQueue = playbackQueue,
            sourceQueue = sourceQueue
        )
        val currentIndex = currentSongIndexInQueue(
            displayOrder = displayOrder,
            queue = queue,
            currentQueueIndex = currentQueueIndex,
            currentSong = currentSong
        )
        if (sheetVisible && queueViewportHeightPx > 0 && currentIndex != null) {
            val targetOffsetPx = (queueViewportHeightPx * CurrentSongViewportFraction).toInt()
            queueListState.scrollToItem(
                index = currentIndex,
                scrollOffset = -targetOffsetPx
            )
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
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                ),
                initialAlpha = 0f
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                ),
                initialOffsetY = { fullHeight -> fullHeight + 80 }
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                ),
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
                            onOrderSelected = onDisplayOrderChange
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
                        AnimatedContent(
                            targetState = displayOrder,
                            transitionSpec = {
                                EnterTransition.None togetherWith ExitTransition.None
                            },
                            label = "QueueDisplayOrderListTransition",
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                                    .onSizeChanged { queueViewportHeightPx = it.height }
                                    .nestedScroll(pullToDismissConnection)
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

                                    SongListItem(
                                        song = song,
                                        isCurrentSong = isCurrentSong,
                                        onClick = onSongClick,
                                        titleColor = Color.White,
                                        artistColor = Color.White,
                                        durationColor = Color.White,
                                        currentSongBackgroundColor = Color.Black.copy(alpha = 0.28f),
                                        modifier = Modifier
                                            .queueItemAnimation(visibleAnimationIndex)
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun queueForDisplayOrder(
    displayOrder: QueueDisplayOrder,
    playbackQueue: List<Song>,
    sourceQueue: List<Song>
): List<Song> {
    return when (displayOrder) {
        QueueDisplayOrder.PlaybackOrder -> playbackQueue
        QueueDisplayOrder.ListOrder -> sourceQueue.ifEmpty { playbackQueue }
    }
}

private fun currentSongIndexInQueue(
    displayOrder: QueueDisplayOrder,
    queue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?
): Int? {
    if (queue.isEmpty()) {
        return null
    }

    if (displayOrder == QueueDisplayOrder.PlaybackOrder && currentQueueIndex in queue.indices) {
        return currentQueueIndex
    }

    if (currentSong != null) {
        val songIndex = queue.indexOfFirst { song ->
            song.id == currentSong.id || song.uri == currentSong.uri
        }
        if (songIndex >= 0) {
            return songIndex
        }
    }

    return null
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
    var fromOrder by remember { mutableStateOf(selectedOrder) }
    var toOrder by remember { mutableStateOf(selectedOrder) }
    val textProgress = remember { Animatable(1f) }
    val iconProgress = remember { Animatable(1f) }
    val iconDelayMillis = 72
    val density = LocalDensity.current
    val textSlotHeight = 18.dp
    val slotHeightPx = with(density) { textSlotHeight.toPx() }

    LaunchedEffect(selectedOrder) {
        if (selectedOrder != fromOrder && selectedOrder != toOrder) {
            fromOrder = toOrder
            toOrder = selectedOrder
            textProgress.snapTo(0f)
            iconProgress.snapTo(0f)
        } else if (fromOrder == toOrder && selectedOrder != toOrder) {
            fromOrder = toOrder
            toOrder = selectedOrder
            textProgress.snapTo(0f)
            iconProgress.snapTo(0f)
        }

        val targetProgress = if (selectedOrder == toOrder) 1f else 0f
        val textDistance = if (targetProgress > textProgress.value) {
            targetProgress - textProgress.value
        } else {
            textProgress.value - targetProgress
        }
        val textDurationMillis = (MINI_PLAYER_ANIMATION_DURATION_MS * textDistance)
            .toInt()
            .coerceAtLeast(1)
        val delayedIconStartMillis = if (textDurationMillis > iconDelayMillis) {
            iconDelayMillis
        } else {
            textDurationMillis / 2
        }
        val iconDurationMillis = (textDurationMillis - delayedIconStartMillis).coerceAtLeast(1)

        coroutineScope {
            launch {
                textProgress.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(
                        durationMillis = textDurationMillis,
                        easing = MiniPlayerEasing
                    )
                )
            }
            launch {
                iconProgress.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(
                        durationMillis = iconDurationMillis,
                        delayMillis = delayedIconStartMillis,
                        easing = MiniPlayerEasing
                    )
                )
            }
        }

        if (selectedOrder == toOrder && textProgress.value >= 0.999f) {
            fromOrder = toOrder
            textProgress.snapTo(1f)
            iconProgress.snapTo(1f)
        } else if (selectedOrder == fromOrder && textProgress.value <= 0.001f) {
            toOrder = fromOrder
            textProgress.snapTo(1f)
            iconProgress.snapTo(1f)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .clickable { onOrderSelected(nextOrder) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(textSlotHeight)
                .clipToBounds()
        ) {
            Text(
                text = fromOrder.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                modifier = Modifier.graphicsLayer {
                    translationY = slotHeightPx * textProgress.value
                }
            )
            Text(
                text = toOrder.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                modifier = Modifier.graphicsLayer {
                    translationY = -slotHeightPx * (1f - textProgress.value)
                }
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
                .clipToBounds()
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "\u5207\u6362\u961f\u5217\u987a\u5e8f",
                tint = Color.Black.copy(alpha = 0.76f),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        translationY = slotHeightPx * iconProgress.value
                    }
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.76f),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        translationY = -slotHeightPx * (1f - iconProgress.value)
                    }
            )
        }
    }
}
