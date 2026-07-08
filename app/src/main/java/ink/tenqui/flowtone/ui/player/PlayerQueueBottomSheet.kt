package ink.tenqui.flowtone.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.delay

enum class QueueDisplayOrder(val label: String) {
    PlaybackOrder("\u64ad\u653e\u987a\u5e8f"),
    ListOrder("\u5217\u8868\u987a\u5e8f")
}

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
    flowCloudSpeed: Float = DefaultFlowCloudSpeed,
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
    val sheetShape = RoundedCornerShape(
        topStart = PlayerQueueSheetCornerRadius,
        topEnd = PlayerQueueSheetCornerRadius
    )
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val queueListState = rememberLazyListState()
    var queueViewportHeightPx by remember { mutableStateOf(0) }
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = { requestDismiss() }
            )
    ) {
        val sheetHeight = maxHeight * PlayerQueueSheetHeightFraction

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
                    flowCloudSpeed = flowCloudSpeed,
                    waitForArtworkLoad = waitForArtworkLoad,
                    shape = sheetShape,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .padding(
                            start = PlayerQueueSheetHorizontalPadding,
                            top = PlayerQueueSheetTopPadding,
                            end = PlayerQueueSheetHorizontalPadding
                        )
                ) {
                    PlayerQueueHeader(
                        queueSize = displayedQueue.size,
                        displayOrder = displayOrder,
                        onDisplayOrderChange = onDisplayOrderChange
                    )

                    if (displayedQueue.isEmpty()) {
                        PlayerQueueEmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        PlayerQueueList(
                            displayOrder = displayOrder,
                            playbackQueue = playbackQueue,
                            sourceQueue = sourceQueue,
                            currentQueueIndex = currentQueueIndex,
                            currentSong = currentSong,
                            queueListState = queueListState,
                            dismissStarted = dismissStarted,
                            onViewportHeightChanged = { height ->
                                queueViewportHeightPx = height
                            },
                            onSongClick = onSongClick,
                            onDismiss = { requestDismiss() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
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
    flowCloudSpeed: Float,
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
            flowCloudSpeed = flowCloudSpeed,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, progress)))
        )
    }
}

