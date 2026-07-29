package ink.tenqui.flowtone.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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
    playbackOrderMode: PlaybackOrderMode,
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
    val queueDisplayCache = remember(playbackQueue, sourceQueue) {
        buildQueueDisplayCache(
            playbackQueue = playbackQueue,
            sourceQueue = sourceQueue
        )
    }
    val displayedQueue = queueDisplayCache.queueFor(displayOrder)
    val sheetShape = RoundedCornerShape(
        topStart = PlayerQueueSheetCornerRadius,
        topEnd = PlayerQueueSheetCornerRadius
    )
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val openingCurrentSongKey = remember { currentSong?.queueSongKey() }
    val openingCurrentQueueIndex = remember { currentQueueIndex }
    val openingIndex = queueDisplayCache.indexOf(
        displayOrder = displayOrder,
        currentSongKey = openingCurrentSongKey,
        currentQueueIndex = openingCurrentQueueIndex
    )
    val initialListState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeInitialItemIndex(
            index = openingIndex,
            queueSize = displayedQueue.size
        )
    )
    var queueViewportHeightPx by remember { mutableStateOf(0) }
    var nextSnapshotId by remember { mutableStateOf(1L) }
    var activeSnapshot by remember {
        mutableStateOf(
            buildQueueDisplaySnapshot(
                id = 0L,
                displayOrder = displayOrder,
                cache = queueDisplayCache,
                currentSongKey = openingCurrentSongKey,
                currentQueueIndex = openingCurrentQueueIndex,
                listState = initialListState
            )
        )
    }
    var targetSnapshot by remember { mutableStateOf<QueueDisplaySnapshot?>(null) }
    var targetReady by remember { mutableStateOf(false) }
    var initialPositionApplied by remember { mutableStateOf(false) }
    val transitionProgress = remember { Animatable(1f) }
    val transitionDistancePx = with(LocalDensity.current) {
        PlayerQueueOrderTransitionDistance.toPx()
    }
    val hasVisibleQueue =
        activeSnapshot.songs.isNotEmpty() || targetSnapshot?.songs?.isNotEmpty() == true
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
    LaunchedEffect(queueDisplayCache) {
        activeSnapshot = activeSnapshot.copy(
            songs = queueDisplayCache.queueFor(activeSnapshot.displayOrder),
            targetIndex = queueDisplayCache.indexOf(
                displayOrder = activeSnapshot.displayOrder,
                currentSongKey = activeSnapshot.currentSongKey,
                currentQueueIndex = activeSnapshot.currentQueueIndex
            )
        )
        targetSnapshot = targetSnapshot?.let { snapshot ->
            snapshot.copy(
                songs = queueDisplayCache.queueFor(snapshot.displayOrder),
                targetIndex = queueDisplayCache.indexOf(
                    displayOrder = snapshot.displayOrder,
                    currentSongKey = snapshot.currentSongKey,
                    currentQueueIndex = snapshot.currentQueueIndex
                )
            )
        }
    }
    LaunchedEffect(displayOrder, queueDisplayCache) {
        if (displayOrder == activeSnapshot.displayOrder) {
            targetSnapshot = null
            targetReady = false
            transitionProgress.snapTo(1f)
        } else {
            val targetSongKey = currentSong?.queueSongKey()
            val targetIndex = queueDisplayCache.indexOf(
                displayOrder = displayOrder,
                currentSongKey = targetSongKey,
                currentQueueIndex = currentQueueIndex
            )
            val targetQueue = queueDisplayCache.queueFor(displayOrder)
            val targetState = LazyListState(
                safeInitialItemIndex(
                    index = targetIndex,
                    queueSize = targetQueue.size
                ),
                0
            )
            targetSnapshot = QueueDisplaySnapshot(
                id = nextSnapshotId,
                displayOrder = displayOrder,
                songs = targetQueue,
                currentSongKey = targetSongKey,
                currentQueueIndex = currentQueueIndex,
                targetIndex = targetIndex,
                listState = targetState
            )
            nextSnapshotId += 1L
            targetReady = false
            transitionProgress.snapTo(0f)
        }
    }
    LaunchedEffect(activeSnapshot.id, queueViewportHeightPx) {
        if (initialPositionApplied || activeSnapshot.id != 0L) {
            return@LaunchedEffect
        }
        if (activeSnapshot.songs.isNotEmpty() && queueViewportHeightPx <= 0) {
            return@LaunchedEffect
        }
        scrollQueueSnapshotToTarget(
            snapshot = activeSnapshot,
            viewportHeightPx = queueViewportHeightPx
        )
        initialPositionApplied = true
    }
    LaunchedEffect(targetSnapshot?.id, queueViewportHeightPx) {
        val target = targetSnapshot ?: return@LaunchedEffect
        if (target.songs.isNotEmpty() && queueViewportHeightPx <= 0) {
            return@LaunchedEffect
        }
        scrollQueueSnapshotToTarget(
            snapshot = target,
            viewportHeightPx = queueViewportHeightPx
        )
        snapshotFlow {
            target.songs.isEmpty() || target.listState.layoutInfo.visibleItemsInfo.isNotEmpty()
        }.first { it }
        targetReady = true
    }
    LaunchedEffect(targetSnapshot?.id, targetReady) {
        val target = targetSnapshot ?: return@LaunchedEffect
        if (!targetReady) {
            return@LaunchedEffect
        }
        transitionProgress.snapTo(0f)
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                easing = MiniPlayerEasing
            )
        )
        activeSnapshot = target
        targetSnapshot = null
        targetReady = false
        initialPositionApplied = true
        transitionProgress.snapTo(1f)
    }
    LaunchedEffect(sheetVisible, dismissStarted) {
        if (dismissStarted && !sheetVisible) {
            delay(MINI_PLAYER_ANIMATION_DURATION_MS.toLong())
            onDismiss()
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
                        playbackOrderMode = playbackOrderMode,
                        displayOrder = displayOrder,
                        onDisplayOrderChange = onDisplayOrderChange
                    )

                    if (!hasVisibleQueue) {
                        PlayerQueueEmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        QueueDisplaySnapshotLayers(
                            activeSnapshot = activeSnapshot,
                            targetSnapshot = targetSnapshot,
                            targetReady = targetReady,
                            transitionProgress = transitionProgress.value,
                            transitionDistancePx = transitionDistancePx,
                            playbackQueue = playbackQueue,
                            currentQueueIndex = currentQueueIndex,
                            currentSong = currentSong,
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

@Composable
private fun QueueDisplaySnapshotLayers(
    activeSnapshot: QueueDisplaySnapshot,
    targetSnapshot: QueueDisplaySnapshot?,
    targetReady: Boolean,
    transitionProgress: Float,
    transitionDistancePx: Float,
    playbackQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    dismissStarted: Boolean,
    onViewportHeightChanged: (Int) -> Unit,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTransitioning = targetSnapshot != null && targetReady
    val activeAlpha = if (isTransitioning) {
        1f - transitionProgress
    } else {
        1f
    }
    val activeTranslationY = if (isTransitioning) {
        -transitionDistancePx * transitionProgress * 0.35f
    } else {
        0f
    }

    Box(modifier = modifier) {
        if (targetSnapshot != null) {
            val targetAlpha = if (targetReady) transitionProgress else 0f
            val targetTranslationY = if (targetReady) {
                transitionDistancePx * (1f - transitionProgress)
            } else {
                0f
            }

            PlayerQueueList(
                displayOrder = targetSnapshot.displayOrder,
                displayedQueue = targetSnapshot.songs,
                playbackQueue = playbackQueue,
                currentQueueIndex = currentQueueIndex,
                currentSong = currentSong,
                queueListState = targetSnapshot.listState,
                dismissStarted = dismissStarted,
                interactionEnabled = false,
                onViewportHeightChanged = onViewportHeightChanged,
                onSongClick = onSongClick,
                onDismiss = onDismiss,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(if (targetReady) 2f else 0f)
                    .graphicsLayer {
                        alpha = targetAlpha
                        translationY = targetTranslationY
                    }
            )
        }

        PlayerQueueList(
            displayOrder = activeSnapshot.displayOrder,
            displayedQueue = activeSnapshot.songs,
            playbackQueue = playbackQueue,
            currentQueueIndex = currentQueueIndex,
            currentSong = currentSong,
            queueListState = activeSnapshot.listState,
            dismissStarted = dismissStarted,
            interactionEnabled = targetSnapshot == null || !targetReady,
            onViewportHeightChanged = onViewportHeightChanged,
            onSongClick = onSongClick,
            onDismiss = onDismiss,
            modifier = Modifier
                .matchParentSize()
                .zIndex(1f)
                .graphicsLayer {
                    alpha = activeAlpha
                    translationY = activeTranslationY
                }
        )
    }
}

private fun currentSongTargetScrollOffset(viewportHeightPx: Int): Int {
    return -(viewportHeightPx * CurrentSongViewportFraction).toInt()
}

private fun buildQueueDisplayCache(
    playbackQueue: List<Song>,
    sourceQueue: List<Song>
): QueueDisplayCache {
    val playbackOrderQueue = playbackQueue
    val listOrderQueue = sourceQueue.ifEmpty { playbackQueue }
    return QueueDisplayCache(
        playbackOrderQueue = playbackOrderQueue,
        listOrderQueue = listOrderQueue,
        playbackOrderIndexLookup = buildQueueIndexLookup(playbackOrderQueue),
        listOrderIndexLookup = buildQueueIndexLookup(listOrderQueue)
    )
}

private fun buildQueueIndexLookup(queue: List<Song>): QueueIndexLookup {
    val indicesById = mutableMapOf<Long, Int>()
    val indicesByUri = mutableMapOf<String, Int>()
    queue.forEachIndexed { index, song ->
        indicesById.putIfAbsent(song.id, index)
        indicesByUri.putIfAbsent(song.uri.toString(), index)
    }
    return QueueIndexLookup(
        indicesById = indicesById,
        indicesByUri = indicesByUri
    )
}

private fun buildQueueDisplaySnapshot(
    id: Long,
    displayOrder: QueueDisplayOrder,
    cache: QueueDisplayCache,
    currentSongKey: QueueSongKey?,
    currentQueueIndex: Int,
    listState: LazyListState
): QueueDisplaySnapshot {
    return QueueDisplaySnapshot(
        id = id,
        displayOrder = displayOrder,
        songs = cache.queueFor(displayOrder),
        currentSongKey = currentSongKey,
        currentQueueIndex = currentQueueIndex,
        targetIndex = cache.indexOf(
            displayOrder = displayOrder,
            currentSongKey = currentSongKey,
            currentQueueIndex = currentQueueIndex
        ),
        listState = listState
    )
}

private suspend fun scrollQueueSnapshotToTarget(
    snapshot: QueueDisplaySnapshot,
    viewportHeightPx: Int
) {
    if (snapshot.songs.isEmpty()) {
        return
    }

    val targetIndex = snapshot.targetIndex
    if (targetIndex != null) {
        snapshot.listState.scrollToItem(
            index = safeInitialItemIndex(
                index = targetIndex,
                queueSize = snapshot.songs.size
            ),
            scrollOffset = currentSongTargetScrollOffset(viewportHeightPx)
        )
    } else {
        snapshot.listState.scrollToItem(index = 0)
    }
}

private fun safeInitialItemIndex(index: Int?, queueSize: Int): Int {
    if (queueSize <= 0) {
        return 0
    }
    return (index ?: 0).coerceIn(0, queueSize - 1)
}

private data class QueueDisplaySnapshot(
    val id: Long,
    val displayOrder: QueueDisplayOrder,
    val songs: List<Song>,
    val currentSongKey: QueueSongKey?,
    val currentQueueIndex: Int,
    val targetIndex: Int?,
    val listState: LazyListState
)

private data class QueueDisplayCache(
    val playbackOrderQueue: List<Song>,
    val listOrderQueue: List<Song>,
    val playbackOrderIndexLookup: QueueIndexLookup,
    val listOrderIndexLookup: QueueIndexLookup
) {
    fun queueFor(displayOrder: QueueDisplayOrder): List<Song> {
        return when (displayOrder) {
            QueueDisplayOrder.PlaybackOrder -> playbackOrderQueue
            QueueDisplayOrder.ListOrder -> listOrderQueue
        }
    }

    fun indexOf(
        displayOrder: QueueDisplayOrder,
        currentSongKey: QueueSongKey?,
        currentQueueIndex: Int
    ): Int? {
        val queue = queueFor(displayOrder)
        if (queue.isEmpty()) {
            return null
        }

        if (currentSongKey != null) {
            val lookup = when (displayOrder) {
                QueueDisplayOrder.PlaybackOrder -> playbackOrderIndexLookup
                QueueDisplayOrder.ListOrder -> listOrderIndexLookup
            }
            lookup.indexOf(currentSongKey)?.let { return it }
        }

        return if (
            displayOrder == QueueDisplayOrder.PlaybackOrder &&
            currentQueueIndex in queue.indices
        ) {
            currentQueueIndex
        } else {
            null
        }
    }
}

private data class QueueIndexLookup(
    val indicesById: Map<Long, Int>,
    val indicesByUri: Map<String, Int>
) {
    fun indexOf(songKey: QueueSongKey): Int? {
        return indicesById[songKey.id] ?: indicesByUri[songKey.uri]
    }
}

private data class QueueSongKey(
    val id: Long,
    val uri: String
)

internal fun Song.queueItemKey(): String {
    return "${sourceType.name}:$id:$uri"
}

internal fun Song.queueItemKey(queueIndex: Int): String {
    return "${queueItemKey()}:$queueIndex"
}

private fun Song.queueSongKey(): QueueSongKey {
    return QueueSongKey(
        id = id,
        uri = uri.toString()
    )
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

