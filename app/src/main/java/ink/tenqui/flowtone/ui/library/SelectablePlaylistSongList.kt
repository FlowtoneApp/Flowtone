package ink.tenqui.flowtone.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import androidx.compose.ui.platform.LocalContext
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.ui.components.PlaylistCardSurface
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.player.MINI_PLAYER_ANIMATION_DURATION_MS
import ink.tenqui.flowtone.ui.player.MiniPlayerEasing
import ink.tenqui.flowtone.ui.player.PlayerQueueOrderTransitionDistance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs

private val SelectionSlotPadding = 2.dp
private val SelectionListContentPadding = 14.dp
private val SelectionAutoScrollEdgeSize = 72.dp
private val SelectionAutoScrollStep = 4.dp
private val SelectionDirectionChangeThreshold = 2.dp
private const val SelectionAutoScrollFrameMillis = 32L
private const val SelectionLongPressTimeoutMillis = 300L
private const val SortItemStaggerMillis = 18
private const val SortStaggeredItemCount = 10

private class PlaylistSelectionGestureState {
    var lastToggledKey: String? = null
    var primaryPositionY: Float? = null
    var verticalDirection: Int = 0
    var pendingDirection: Int = 0
    var pendingDirectionDistance: Float = 0f

    fun reset(primaryPositionY: Float? = null) {
        lastToggledKey = null
        this.primaryPositionY = primaryPositionY
        verticalDirection = 0
        pendingDirection = 0
        pendingDirectionDistance = 0f
    }
}

private sealed interface SecondaryScrollCommand {
    data class Drag(val deltaY: Float) : SecondaryScrollCommand
    data class Fling(val velocityY: Float) : SecondaryScrollCommand
}

internal data class SelectablePlaylistSong(
    val selectionKey: String,
    val song: Song,
    val track: PersistentTrack = song.toPersistentTrack(),
    val playlistEntryId: String? = null,
    val playlistAddedAtSeconds: Long? = null
)

internal data class PlaylistBatchActions(
    val likedSongKeys: List<String> = emptyList(),
    val editablePlaylists: List<LibraryPlaylistCard> = emptyList(),
    val clearSelectionRequest: Int = 0,
    val onSelectionModeChange: (Boolean) -> Unit = {},
    val onSelectionTopBarStateChange: (PlaylistSelectionTopBarState?) -> Unit = {},
    val onRequestClearSelection: () -> Unit = {},
    val onAddSongsNext: (List<Song>) -> Boolean = { false },
    val onAppendSongsToQueue: (List<Song>) -> Boolean = { false },
    val onAddSongsToPlaylists:
        (Set<String>, List<PersistentTrack>, (Boolean, Int) -> Unit) -> Unit =
        { _, _, done -> done(false, 0) },
    val onSetSongsLiked: (List<PersistentTrack>, Boolean) -> Unit = { _, _ -> },
    val onDeleteSongs: (List<Song>, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    val onRemoveEntries:
        (String, Set<String>, (Boolean) -> Unit) -> Unit = { _, _, done -> done(false) }
)

@Composable
internal fun SelectablePlaylistSongList(
    sourceKey: String,
    source: PlaylistSelectionSource,
    playlistTitle: String,
    entries: List<SelectablePlaylistSong>,
    listState: LazyListState,
    currentSong: Song?,
    pendingTrackIdentityKey: String? = null,
    likedSongKeys: List<String>,
    editablePlaylists: List<LibraryPlaylistCard>,
    clearSelectionRequest: Int,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionTopBarStateChange: (PlaylistSelectionTopBarState?) -> Unit,
    onSongClick: (List<PersistentTrack>, Int) -> Unit,
    externalErrorMessage: String? = null,
    externalErrorEventId: Long = 0L,
    onAddSongsNext: (List<Song>) -> Boolean,
    onAppendSongsToQueue: (List<Song>) -> Boolean,
    onAddSongsToPlaylists:
        (Set<String>, List<PersistentTrack>, (Boolean, Int) -> Unit) -> Unit,
    onSetSongsLiked: (List<PersistentTrack>, Boolean) -> Unit,
    onDeleteSongs: (List<Song>, (Boolean) -> Unit) -> Unit,
    onRemoveEntries: (Set<String>, (Boolean) -> Unit) -> Unit,
    reorderAnimationKey: Any? = null,
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedKeys by rememberSaveable(sourceKey) { mutableStateOf(emptyList<String>()) }
    var busy by remember(sourceKey) { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var suppressLongPressReleaseClick by remember(sourceKey) { mutableStateOf(false) }
    var selectionGestureActive by remember(sourceKey) { mutableStateOf(false) }
    var primarySelectionPointerId by remember(sourceKey) { mutableStateOf<PointerId?>(null) }
    var primarySelectionObserverOwnsGesture by remember(sourceKey) { mutableStateOf(false) }
    var secondaryPointerIntervened by remember(sourceKey) { mutableStateOf(false) }
    var secondaryScrollOwnsGesture by remember(sourceKey) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(externalErrorEventId) {
        externalErrorMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val flingBehavior = ScrollableDefaults.flingBehavior()
    val defaultViewConfiguration = LocalViewConfiguration.current
    val selectionViewConfiguration = remember(defaultViewConfiguration) {
        object : ViewConfiguration by defaultViewConfiguration {
            override val longPressTimeoutMillis: Long = SelectionLongPressTimeoutMillis
        }
    }
    val selectionGestureState = remember(sourceKey) { PlaylistSelectionGestureState() }
    val selectionMode = selectedKeys.isNotEmpty()
    var lastReorderAnimationKey by remember(sourceKey) {
        mutableStateOf(reorderAnimationKey)
    }
    var renderedEntries by remember(sourceKey) { mutableStateOf(entries) }
    var outgoingEntries by remember(sourceKey) {
        mutableStateOf<List<SelectablePlaylistSong>?>(null)
    }
    var outgoingListState by remember(sourceKey) {
        mutableStateOf<LazyListState?>(null)
    }
    val reorderProgress = remember(sourceKey) { Animatable(1f) }
    val reorderAnimationActive = outgoingEntries != null
    val reorderDistancePx = with(density) { PlayerQueueOrderTransitionDistance.toPx() }
    val reorderTotalDurationMillis = MINI_PLAYER_ANIMATION_DURATION_MS +
        SortItemStaggerMillis * SortStaggeredItemCount
    val entriesByKey = renderedEntries.associateBy { it.selectionKey }
    val entryKeys = remember(renderedEntries) { renderedEntries.map { it.selectionKey } }
    val latestEntries by rememberUpdatedState(renderedEntries)
    val selectedKeySet = remember(selectedKeys) { selectedKeys.toSet() }
    // 按 selectionKey 被加入集合的先后生成操作列表。
    val selectedEntries = selectedKeys.mapNotNull(entriesByKey::get)
    val selectedSongs = selectedEntries.map { it.song }
    val selectedTracks = selectedEntries.map { it.track }
    var frozenTransitionId by remember(sourceKey) { mutableStateOf<Int?>(null) }
    var frozenViewportKeys by remember(sourceKey) { mutableStateOf<List<String>>(emptyList()) }
    var capturedPageProgress by remember(sourceKey) { mutableStateOf(0f) }
    val visibleSongKeys by remember(listState, renderedEntries) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { item -> renderedEntries.getOrNull(item.index)?.selectionKey }
                .distinct()
        }
    }
    LaunchedEffect(
        pageTransition.transitionId,
        pageTransition.phase,
        visibleSongKeys
    ) {
        if (pageTransition.phase == PageTransitionPhase.Current) {
            frozenTransitionId = null
            frozenViewportKeys = emptyList()
            capturedPageProgress = 0f
        } else if (
            frozenTransitionId != pageTransition.transitionId &&
            visibleSongKeys.isNotEmpty()
        ) {
            frozenTransitionId = pageTransition.transitionId
            frozenViewportKeys = visibleSongKeys
            capturedPageProgress = pageTransition.progress.coerceIn(0f, 1f)
        }
    }

    val animationGroupKeys = if (pageTransition.phase == PageTransitionPhase.Current) {
        visibleSongKeys
    } else if (pageTransition.phase == PageTransitionPhase.Incoming) {
        // Enter waits for the first real layout. A synthetic group would remap
        // an item that has already started animating.
        frozenViewportKeys
    } else {
        frozenViewportKeys
            .ifEmpty { visibleSongKeys }
    }

    val listProgress = when {
        pageTransition.phase != PageTransitionPhase.Incoming -> pageTransition.progress
        frozenViewportKeys.isEmpty() -> 0f
        else -> {
            val remaining = (1f - capturedPageProgress).coerceAtLeast(0.0001f)
            ((pageTransition.progress - capturedPageProgress) / remaining)
                .coerceIn(0f, 1f)
        }
    }
    val enterGroupReady = pageTransition.phase != PageTransitionPhase.Incoming ||
        frozenViewportKeys.isNotEmpty()

    val animationOrderByKey = remember(animationGroupKeys) {
        animationGroupKeys.withIndex().associate { (order, key) -> key to order }
    }

    fun animationOrderFor(key: String): Pair<Int, Int> {
        val order = animationOrderByKey[key] ?: 0
        return order to animationGroupKeys.size.coerceAtLeast(1)
    }

    LaunchedEffect(entries) {
        // 曲库扫描或歌单内容变更不应被误认为一次排序切换。
        if (reorderAnimationKey == lastReorderAnimationKey && outgoingEntries == null) {
            renderedEntries = entries
        }
    }

    LaunchedEffect(reorderAnimationKey) {
        if (reorderAnimationKey == lastReorderAnimationKey) {
            renderedEntries = entries
            return@LaunchedEffect
        }

        val previousEntries = renderedEntries
        lastReorderAnimationKey = reorderAnimationKey
        // 旧层必须从当前精确位置接管；默认 LazyListState 会从索引 0 绘制，造成闪帧。
        outgoingListState = LazyListState(
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
        )
        // 取消上一轮时不清理旧层。下一轮会先接管这层，避免中间露出空白帧。
        outgoingEntries = previousEntries
        reorderProgress.snapTo(0f)
        renderedEntries = entries
        // 数据换序后再请求回顶，覆盖 LazyColumn 根据稳定 key 保持原可见歌曲的行为。
        listState.requestScrollToItem(0, 0)
        // 旧层保持完全可见，先让新列表完成重组与测量。
        withFrameNanos { }
        withFrameNanos { }
        // 在新顺序已经进入 LazyColumn 后再次回顶，避免稳定 key 的位置保持覆盖请求。
        listState.scrollToItem(0, 0)
        withFrameNanos { }
        reorderProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = reorderTotalDurationMillis,
                easing = LinearEasing
            )
        )
        outgoingEntries = null
        outgoingListState = null
        reorderProgress.snapTo(1f)
    }

    fun entryAt(y: Float): SelectablePlaylistSong? {
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            y.toInt() in item.offset until (item.offset + item.size)
        } ?: return null
        val slotPaddingPx = with(density) { SelectionSlotPadding.toPx() }
        val positionInItem = y - visibleItem.offset
        if (
            positionInItem < slotPaddingPx ||
            positionInItem >= visibleItem.size - slotPaddingPx
        ) {
            return null
        }
        return latestEntries.getOrNull(visibleItem.index)
    }

    fun toggleSelectionAt(y: Float, force: Boolean = false) {
        val entry = entryAt(y) ?: return
        if (force || entry.selectionKey != selectionGestureState.lastToggledKey) {
            selectionGestureState.lastToggledKey = entry.selectionKey
            selectedKeys = if (entry.selectionKey in selectedKeys) {
                selectedKeys - entry.selectionKey
            } else {
                selectedKeys + entry.selectionKey
            }
        }
    }

    fun handlePrimarySelectionDrag(y: Float, deltaY: Float) {
        val direction = when {
            deltaY > 0f -> 1
            deltaY < 0f -> -1
            else -> 0
        }
        var forceToggle = false
        if (direction != 0) {
            if (direction == selectionGestureState.pendingDirection) {
                selectionGestureState.pendingDirectionDistance += abs(deltaY)
            } else {
                selectionGestureState.pendingDirection = direction
                selectionGestureState.pendingDirectionDistance = abs(deltaY)
            }
            val thresholdPx = with(density) { SelectionDirectionChangeThreshold.toPx() }
            if (
                selectionGestureState.pendingDirectionDistance >= thresholdPx &&
                direction != selectionGestureState.verticalDirection
            ) {
                forceToggle = selectionGestureState.verticalDirection != 0
                selectionGestureState.verticalDirection = direction
                selectionGestureState.pendingDirectionDistance = 0f
            }
        }
        toggleSelectionAt(y = y, force = forceToggle)
    }

    fun clearSelection() {
        selectedKeys = emptyList()
        onSelectionModeChange(false)
        onSelectionTopBarStateChange(null)
    }

    fun finishWith(message: String) {
        busy = false
        clearSelection()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BackHandler(
        enabled = selectionMode || showPlaylistDialog || showRemoveDialog || showDeleteDialog
    ) {
        when {
            showPlaylistDialog -> showPlaylistDialog = false
            showRemoveDialog -> showRemoveDialog = false
            showDeleteDialog -> showDeleteDialog = false
            else -> clearSelection()
        }
    }

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }
    LaunchedEffect(selectedKeys, busy, source) {
        if (!selectionMode) {
            onSelectionTopBarStateChange(null)
        } else {
            onSelectionTopBarStateChange(
                PlaylistSelectionTopBarState(
                    selectedCount = selectedEntries.size,
                    busy = busy,
                    onAddNext = {
                        if (!busy) {
                            busy = true
                            if (onAddSongsNext(selectedSongs)) {
                                finishWith("已添加 ${selectedSongs.size} 首到下一首")
                            } else {
                                busy = false
                            }
                        }
                    },
                    onAddToPlaylist = {
                        if (!busy) showPlaylistDialog = true
                    },
                    onDelete = when (source) {
                        PlaylistSelectionSource.LocalLibrary -> {
                            { if (!busy) showDeleteDialog = true }
                        }

                        PlaylistSelectionSource.LikedSongs -> {
                            {
                                if (!busy) {
                                    busy = true
                                    onSetSongsLiked(selectedTracks, false)
                                    finishWith(
                                        "已从“我喜欢的音乐”移除 ${selectedSongs.size} 首"
                                    )
                                }
                            }
                        }

                        PlaylistSelectionSource.UserPlaylist -> {
                            { if (!busy) showRemoveDialog = true }
                        }

                        PlaylistSelectionSource.ReadOnly -> null
                    }
                )
            )
        }
    }
    LaunchedEffect(clearSelectionRequest) {
        if (clearSelectionRequest > 0) clearSelection()
    }
    LaunchedEffect(entries.map { it.selectionKey }) {
        val validKeys = entries.mapTo(mutableSetOf()) { it.selectionKey }
        selectedKeys = selectedKeys.filter { it in validKeys }
        if (selectedKeys.isEmpty()) {
            onSelectionModeChange(false)
            onSelectionTopBarStateChange(null)
        }
    }
    DisposableEffect(sourceKey) {
        onDispose {
            onSelectionModeChange(false)
            onSelectionTopBarStateChange(null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalViewConfiguration provides selectionViewConfiguration
        ) {
            LazyColumn(
            state = listState,
            userScrollEnabled = !selectionGestureActive && !reorderAnimationActive,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sourceKey, entryKeys) {
                    awaitEachGesture {
                        var secondaryPointerId: PointerId? = null
                        var secondaryDragDistance = 0f
                        var pointersPressed: Boolean
                        val secondaryVelocityTracker = VelocityTracker()
                        var secondaryScrollChannel: Channel<SecondaryScrollCommand>? = null

                        fun startSecondaryScrollSession(): Channel<SecondaryScrollCommand> {
                            secondaryScrollChannel?.close()
                            return Channel<SecondaryScrollCommand>(Channel.UNLIMITED).also { channel ->
                                secondaryScrollChannel = channel
                                scope.launch {
                                    val followSelectionJob = launch {
                                        snapshotFlow {
                                            listState.firstVisibleItemIndex to
                                                listState.firstVisibleItemScrollOffset
                                        }.collect {
                                            selectionGestureState.primaryPositionY
                                                ?.let(::toggleSelectionAt)
                                        }
                                    }
                                    try {
                                        // Once the second pointer takes over, incidental movement
                                        // from the held primary pointer must not cancel the fling.
                                        listState.scroll(MutatePriority.PreventUserInput) {
                                            for (command in channel) {
                                                when (command) {
                                                    is SecondaryScrollCommand.Drag ->
                                                        scrollBy(-command.deltaY)

                                                    is SecondaryScrollCommand.Fling -> {
                                                        with(flingBehavior) {
                                                            performFling(-command.velocityY)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        followSelectionJob.cancel()
                                    }
                                }
                            }
                        }

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressedChanges = event.changes.filter { it.pressed }
                            pointersPressed = pressedChanges.isNotEmpty()

                            if (primarySelectionPointerId == null) {
                                primarySelectionPointerId = pressedChanges.firstOrNull()?.id
                            }

                            if (selectionGestureActive || secondaryScrollOwnsGesture) {
                                if (secondaryPointerId == null) {
                                    secondaryPointerId = pressedChanges.firstOrNull { change ->
                                        change.id != primarySelectionPointerId
                                    }?.id
                                    secondaryVelocityTracker.resetTracking()
                                }

                                val secondaryChange = event.changes.firstOrNull { change ->
                                    change.id == secondaryPointerId
                                }
                                if (secondaryChange != null) {
                                    val delta = secondaryChange.positionChange()
                                    secondaryVelocityTracker.addPosition(
                                        secondaryChange.uptimeMillis,
                                        secondaryChange.position
                                    )
                                    if (delta.y != 0f) {
                                        secondaryPointerIntervened = true
                                        secondaryChange.consume()
                                    }
                                    if (!secondaryScrollOwnsGesture) {
                                        secondaryDragDistance += delta.y
                                        if (abs(secondaryDragDistance) >= viewConfiguration.touchSlop) {
                                            secondaryScrollOwnsGesture = true
                                            startSecondaryScrollSession()
                                        }
                                    }
                                    if (secondaryScrollOwnsGesture && delta.y != 0f) {
                                        val channel = secondaryScrollChannel
                                            ?: startSecondaryScrollSession()
                                        channel.trySend(SecondaryScrollCommand.Drag(delta.y))
                                    }
                                    if (!secondaryChange.pressed) {
                                        if (secondaryScrollOwnsGesture) {
                                            val velocityY = secondaryVelocityTracker
                                                .calculateVelocity()
                                                .y
                                            secondaryScrollChannel?.trySend(
                                                SecondaryScrollCommand.Fling(velocityY)
                                            )
                                            secondaryScrollChannel?.close()
                                            secondaryScrollChannel = null
                                        }
                                        secondaryPointerId = null
                                        secondaryDragDistance = 0f
                                    }
                                }

                                if (
                                    primarySelectionObserverOwnsGesture ||
                                    secondaryPointerIntervened
                                ) {
                                    val primaryChange = event.changes.firstOrNull { change ->
                                        change.id == primarySelectionPointerId
                                    }
                                    if (primaryChange != null) {
                                        val primaryDelta = primaryChange.positionChange()
                                        if (primaryChange.pressed && primaryDelta.y != 0f) {
                                            selectionGestureState.primaryPositionY =
                                                primaryChange.position.y
                                            handlePrimarySelectionDrag(
                                                y = primaryChange.position.y,
                                                deltaY = primaryDelta.y
                                            )
                                            primaryChange.consume()
                                        }
                                        if (!primaryChange.pressed) {
                                            selectionGestureActive = false
                                            primarySelectionObserverOwnsGesture = false
                                            selectionGestureState.primaryPositionY = null
                                            suppressLongPressReleaseClick = false
                                        }
                                    }
                                }
                            }
                        } while (pointersPressed)

                        selectionGestureActive = false
                        primarySelectionPointerId = null
                        primarySelectionObserverOwnsGesture = false
                        secondaryPointerIntervened = false
                        secondaryScrollOwnsGesture = false
                        secondaryScrollChannel?.close()
                    }
                }
                .pointerInput(sourceKey, entryKeys) {
                    var autoScrollJob: Job? = null
                    var autoScrollDirection = 0

                    fun stopAutoScroll() {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        autoScrollDirection = 0
                    }

                    fun updateAutoScroll(direction: Int) {
                        if (secondaryPointerIntervened) {
                            stopAutoScroll()
                            return
                        }
                        if (direction == 0) {
                            stopAutoScroll()
                            return
                        }
                        if (
                            autoScrollDirection == direction &&
                            autoScrollJob?.isActive == true
                        ) {
                            return
                        }
                        stopAutoScroll()
                        autoScrollDirection = direction

                        autoScrollJob = scope.launch {
                            val stepPx = SelectionAutoScrollStep.toPx() * direction
                            while (isActive && !secondaryPointerIntervened) {
                                listState.scrollBy(stepPx)
                                selectionGestureState.primaryPositionY?.let(::toggleSelectionAt)
                                delay(SelectionAutoScrollFrameMillis)
                            }
                        }
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            selectionGestureState.reset(position.y)
                            selectionGestureActive = true
                            primarySelectionObserverOwnsGesture = false
                            secondaryPointerIntervened = false
                            secondaryScrollOwnsGesture = false
                            // Long press starts selection immediately. The child click recognizer can
                            // still receive the eventual finger-up event, so suppress that one release.
                            suppressLongPressReleaseClick = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggleSelectionAt(position.y)
                        },
                        onDragEnd = {
                            selectionGestureState.reset()
                            selectionGestureActive = false
                            primarySelectionObserverOwnsGesture = false
                            stopAutoScroll()
                            scope.launch {
                                delay(180)
                                suppressLongPressReleaseClick = false
                            }
                        },
                        onDragCancel = {
                            stopAutoScroll()
                            if (selectionGestureActive) {
                                // Continue tracking the held primary pointer after Compose
                                // cancels the original long-press drag recognizer.
                                primarySelectionObserverOwnsGesture = true
                            } else if (!secondaryPointerIntervened) {
                                selectionGestureState.reset()
                                selectionGestureActive = false
                                suppressLongPressReleaseClick = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            selectionGestureState.primaryPositionY = change.position.y
                            handlePrimarySelectionDrag(
                                y = change.position.y,
                                deltaY = dragAmount.y
                            )
                            val edgeSize = SelectionAutoScrollEdgeSize.toPx()
                            val scrollDirection = when {
                                change.position.y < edgeSize -> -1
                                change.position.y > size.height - edgeSize -> 1
                                else -> 0
                            }
                            updateAutoScroll(scrollDirection)
                        }
                    )
                },
            // The original 4.dp gap is split between adjacent fixed slots. Song content
            // keeps the same coordinates while selection backgrounds can meet in the slot.
            contentPadding = PaddingValues(
                top = SelectionListContentPadding,
                bottom = SelectionListContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(renderedEntries, key = { _, entry -> entry.selectionKey }) { index, entry ->
                val (viewportOrder, viewportOrderCount) = animationOrderFor(entry.selectionKey)
                val elapsedMillis = reorderProgress.value * reorderTotalDurationMillis
                val itemDelayMillis = viewportOrder * SortItemStaggerMillis
                val itemRawProgress = if (reorderAnimationActive) {
                    ((elapsedMillis - itemDelayMillis) / MINI_PLAYER_ANIMATION_DURATION_MS)
                        .coerceIn(0f, 1f)
                } else {
                    1f
                }
                val itemReorderProgress = MiniPlayerEasing.transform(itemRawProgress)
                val selected = entry.selectionKey in selectedKeySet
                val hydratedArtwork = rememberOnlineArtwork(entry.track)
                val isPreviousSelected = index > 0 &&
                    renderedEntries[index - 1].selectionKey in selectedKeySet
                val isNextSelected = index < renderedEntries.lastIndex &&
                    renderedEntries[index + 1].selectionKey in selectedKeySet
                SongListItem(
                    song = entry.song,
                    isCurrentSong = currentSong?.id == entry.song.id,
                    isPendingPlayback = entry.track.identityKey == pendingTrackIdentityKey,
                    extensionArtwork = hydratedArtwork,
                    selectionMode = selectionMode,
                    isSelected = selected,
                    isPreviousSelected = isPreviousSelected,
                    isNextSelected = isNextSelected,
                    selectionSlotPadding = SelectionSlotPadding,
                    onClick = {
                        if (suppressLongPressReleaseClick) {
                            suppressLongPressReleaseClick = false
                            return@SongListItem
                        }
                        if (selectionMode) {
                            selectedKeys = if (selected) {
                                selectedKeys - entry.selectionKey
                            } else {
                                selectedKeys + entry.selectionKey
                            }
                            if (selectedKeys.isEmpty()) {
                                onSelectionModeChange(false)
                                onSelectionTopBarStateChange(null)
                            }
                        } else {
                            onSongClick(renderedEntries.map { it.track }, index)
                        }
                    },
                    // 长按与拖动统一由 LazyColumn 识别，避免松手时再触发播放。
                    onLongClick = null,
                    modifier = if (enterGroupReady) {
                        itemModifier(listProgress, viewportOrder, viewportOrderCount)
                    } else {
                        Modifier.graphicsLayer { alpha = 0f }
                    }
                        .graphicsLayer {
                            alpha = itemReorderProgress
                            translationY = reorderDistancePx * (1f - itemReorderProgress)
                        }
                        .padding(horizontal = 8.dp)
                )
                }
            }

            outgoingEntries?.let { previousEntries ->
                val previousListState = outgoingListState ?: return@let
                val elapsedMillis = reorderProgress.value * reorderTotalDurationMillis
                val outgoingRawProgress =
                    (elapsedMillis / MINI_PLAYER_ANIMATION_DURATION_MS).coerceIn(0f, 1f)
                val outgoingProgress = MiniPlayerEasing.transform(outgoingRawProgress)
                LazyColumn(
                    state = previousListState,
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(
                        top = SelectionListContentPadding,
                        bottom = SelectionListContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - outgoingProgress
                            translationY = -reorderDistancePx * outgoingProgress * 0.35f
                        }
                ) {
                    itemsIndexed(
                        items = previousEntries,
                        key = { _, entry -> "sort-outgoing:${entry.selectionKey}" }
                    ) { _, entry ->
                        SongListItem(
                            song = entry.song,
                            isCurrentSong = currentSong?.id == entry.song.id,
                            selectionMode = false,
                            isSelected = false,
                            isPreviousSelected = false,
                            isNextSelected = false,
                            selectionSlotPadding = SelectionSlotPadding,
                            onClick = {},
                            onLongClick = null,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showRemoveDialog = false },
            title = { Text("从歌单中移除？") },
            text = {
                Text(
                    "将从“$playlistTitle”中移除 ${selectedEntries.size} 首歌曲，" +
                        "不会删除设备中的音乐文件。"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDialog = false },
                    enabled = !busy
                ) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        onRemoveEntries(
                            selectedEntries.mapNotNull { it.playlistEntryId }.toSet()
                        ) { success ->
                            showRemoveDialog = false
                            if (success) {
                                finishWith("已从歌单移除 ${selectedEntries.size} 首")
                            } else {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("移除")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除 ${selectedEntries.size} 首本地歌曲？") },
            text = {
                Text(
                    "文件将从设备存储中删除，且可能无法撤销。" +
                        "接下来将由系统再次确认删除操作。"
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        showDeleteDialog = false
                        onDeleteSongs(selectedSongs) { success ->
                            if (success) {
                                finishWith("已删除 ${selectedSongs.size} 首本地歌曲")
                            } else {
                                busy = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "未删除本地歌曲：操作被取消、被系统拒绝或设备不支持"
                                    )
                                }
                            }
                        }
                    },
                    enabled = !busy
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showPlaylistDialog) {
        AddSelectedSongsToPlaylistsDialog(
            songCount = selectedSongs.size,
            playlists = editablePlaylists.filter { playlist ->
                !playlist.isSystem || playlist.id == LikedSongsPlaylistId
            },
            busy = busy,
            onDismiss = { showPlaylistDialog = false },
            onConfirm = { playlistIds ->
                busy = true
                onAddSongsToPlaylists(playlistIds, selectedTracks) {
                        success,
                        duplicateCount ->
                    showPlaylistDialog = false
                    if (success) {
                        val duplicateMessage = if (duplicateCount > 0) {
                            "，有 $duplicateCount 首歌曲在歌单中重复"
                        } else {
                            ""
                        }
                        finishWith(
                            "已将 ${selectedSongs.size} 首歌曲添加到歌单$duplicateMessage"
                        )
                    } else {
                        busy = false
                    }
                }
            }
        )
    }
}

@Composable
private fun rememberOnlineArtwork(track: PersistentTrack): ExtensionImage? {
    val context = LocalContext.current
    var artwork by remember(track.identityKey) { mutableStateOf<ExtensionImage?>(null) }
    LaunchedEffect(track.identityKey) {
        val online = track as? PersistentTrack.Online ?: return@LaunchedEffect
        artwork = ExtensionManager.get(context).hydratePersistentPresentation(online)?.artwork
    }
    return artwork
}

@Composable
private fun AddSelectedSongsToPlaylistsDialog(
    songCount: Int,
    playlists: List<LibraryPlaylistCard>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("添加 $songCount 首歌曲到歌单") },
        text = {
            if (playlists.isEmpty()) {
                Text("暂无可编辑歌单")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(playlists, key = LibraryPlaylistCard::id) { playlist ->
                        val selected = playlist.id in selectedIds
                        PlaylistDestinationCard(
                            playlist = playlist,
                            selected = selected,
                            enabled = !busy,
                            onClick = {
                                if (selected) selectedIds.remove(playlist.id)
                                else selectedIds.add(playlist.id)
                            }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedIds.toSet()) },
                enabled = selectedIds.isNotEmpty() && !busy
            ) {
                Text(if (busy) "处理中…" else "添加")
            }
        }
    )
}

@Composable
private fun PlaylistDestinationCard(
    playlist: LibraryPlaylistCard,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.small
    val outlineColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    }
    val visualType = if (playlist.isLikedSongsPlaylist()) {
        PlaylistCardVisualType.LikedMusic
    } else {
        PlaylistCardVisualType.UserPlaylist
    }

    PlaylistCardSurface(
        visualType = visualType,
        appearanceColorKey = playlist.appearanceColorKey,
        shape = shape,
        contentPadding = PaddingValues(12.dp),
        isFlowCloudPlaying = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = outlineColor,
                shape = shape
            ),
        clickModifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) { colors ->
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = colors.iconColor,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
