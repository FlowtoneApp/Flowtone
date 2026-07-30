package ink.tenqui.flowtone.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SelectionSlotPadding = 2.dp
private val SelectionListContentPadding = 14.dp

internal data class SelectablePlaylistSong(
    val selectionKey: String,
    val song: Song,
    val playlistEntryId: String? = null
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
        (Set<String>, List<Song>, (Boolean, Int) -> Unit) -> Unit =
        { _, _, done -> done(false, 0) },
    val onSetSongsLiked: (List<Song>, Boolean) -> Unit = { _, _ -> },
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
    likedSongKeys: List<String>,
    editablePlaylists: List<LibraryPlaylistCard>,
    clearSelectionRequest: Int,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionTopBarStateChange: (PlaylistSelectionTopBarState?) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onAddSongsNext: (List<Song>) -> Boolean,
    onAppendSongsToQueue: (List<Song>) -> Boolean,
    onAddSongsToPlaylists: (Set<String>, List<Song>, (Boolean, Int) -> Unit) -> Unit,
    onSetSongsLiked: (List<Song>, Boolean) -> Unit,
    onRemoveEntries: (Set<String>, (Boolean) -> Unit) -> Unit,
    itemModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedKeys by rememberSaveable(sourceKey) { mutableStateOf(emptyList<String>()) }
    var busy by remember(sourceKey) { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var suppressLongPressReleaseClick by remember(sourceKey) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val selectionMode = selectedKeys.isNotEmpty()
    val entriesByKey = entries.associateBy { it.selectionKey }
    val selectedKeySet = remember(selectedKeys) { selectedKeys.toSet() }
    // 按 selectionKey 被加入集合的先后生成操作列表。
    val selectedEntries = selectedKeys.mapNotNull(entriesByKey::get)
    val selectedSongs = selectedEntries.map { it.song }

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
                                    onSetSongsLiked(selectedSongs, false)
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(entries) {
                    var visitedKeys = emptySet<String>()

                    fun entryAt(y: Float): SelectablePlaylistSong? {
                        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            y.toInt() in item.offset until (item.offset + item.size)
                        } ?: return null
                        val slotPaddingPx = SelectionSlotPadding.toPx()
                        val positionInItem = y - visibleItem.offset
                        if (
                            positionInItem < slotPaddingPx ||
                            positionInItem >= visibleItem.size - slotPaddingPx
                        ) {
                            return null
                        }
                        return entries.getOrNull(visibleItem.index)
                    }

                    fun selectAt(y: Float) {
                        val entry = entryAt(y) ?: return
                        if (entry.selectionKey !in visitedKeys) {
                            visitedKeys = visitedKeys + entry.selectionKey
                            if (entry.selectionKey !in selectedKeys) {
                                selectedKeys = selectedKeys + entry.selectionKey
                            }
                        }
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            visitedKeys = emptySet()
                            // Long press starts selection immediately. The child click recognizer can
                            // still receive the eventual finger-up event, so suppress that one release.
                            suppressLongPressReleaseClick = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectAt(position.y)
                        },
                        onDragEnd = {
                            visitedKeys = emptySet()
                            scope.launch {
                                delay(180)
                                suppressLongPressReleaseClick = false
                            }
                        },
                        onDragCancel = {
                            visitedKeys = emptySet()
                            suppressLongPressReleaseClick = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            selectAt(change.position.y)
                            val edgeSize = 72.dp.toPx()
                            val scrollDelta = when {
                                change.position.y < edgeSize -> -24.dp.toPx()
                                change.position.y > size.height - edgeSize -> 24.dp.toPx()
                                else -> 0f
                            }
                            if (scrollDelta != 0f) {
                                scope.launch { listState.scrollBy(scrollDelta) }
                            }
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
            itemsIndexed(entries, key = { _, entry -> entry.selectionKey }) { index, entry ->
                val firstVisibleSongIndex = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                val animationIndex = (index - firstVisibleSongIndex).coerceIn(0, 10)
                val selected = entry.selectionKey in selectedKeySet
                val isPreviousSelected = index > 0 &&
                    entries[index - 1].selectionKey in selectedKeySet
                val isNextSelected = index < entries.lastIndex &&
                    entries[index + 1].selectionKey in selectedKeySet
                SongListItem(
                    song = entry.song,
                    isCurrentSong = currentSong?.id == entry.song.id,
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
                            onSongClick(entries.map { it.song }, index)
                        }
                    },
                    // 长按与拖动统一由 LazyColumn 识别，避免松手时再触发播放。
                    onLongClick = null,
                    modifier = itemModifier(animationIndex).padding(horizontal = 8.dp)
                )
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
                        "当前版本尚未接入安全的系统删除授权流程。"
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
                        showDeleteDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("暂不支持安全删除本地文件")
                        }
                    }
                ) {
                    Text("暂不可用", color = MaterialTheme.colorScheme.error)
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
                onAddSongsToPlaylists(playlistIds, selectedSongs) {
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
            androidx.compose.foundation.layout.Column {
                playlists.forEach { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = playlist.id in selectedIds,
                            onCheckedChange = { checked ->
                                if (checked) selectedIds.add(playlist.id)
                                else selectedIds.remove(playlist.id)
                            }
                        )
                        Text(playlist.title)
                    }
                }
                if (playlists.isEmpty()) {
                    Text("暂无可编辑歌单")
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
