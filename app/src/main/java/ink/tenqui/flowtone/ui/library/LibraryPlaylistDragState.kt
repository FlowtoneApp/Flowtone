package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

internal enum class LibraryPlaylistGestureState {
    Idle,
    ActionsVisible,
    Dragging
}

internal data class PlaylistDragSlot(
    val playlistId: String,
    val movableIndex: Int,
    val topLeft: Offset,
    val size: IntSize
) {
    val center: Offset
        get() = Offset(
            x = topLeft.x + size.width / 2f,
            y = topLeft.y + size.height / 2f
        )
}

internal data class PlaylistDropResult(
    val orderedPlaylists: List<LibraryPlaylistCard>,
    val changed: Boolean
)

private data class PlaylistItemBounds(
    val topLeft: Offset,
    val size: IntSize
)

internal class LibraryPlaylistDragState {
    var draggingPlaylistId by mutableStateOf<String?>(null)
        private set
    var draggedPlaylist by mutableStateOf<LibraryPlaylistCard?>(null)
        private set
    var previewPlaylists by mutableStateOf<List<LibraryPlaylistCard>?>(null)
        private set
    var overlayOffset by mutableStateOf(Offset.Zero)
        private set
    var overlaySize by mutableStateOf(IntSize.Zero)
        private set
    var settling by mutableStateOf(false)
        private set

    private var rootTopLeftInRoot = Offset.Zero
    private var rootSize = IntSize.Zero
    private val itemBoundsByPlaylistId = mutableStateMapOf<String, PlaylistItemBounds>()
    private var originalPlaylists: List<LibraryPlaylistCard> = emptyList()
    private var pointerOffsetInsideCard = Offset.Zero
    private var currentPointer = Offset.Zero
    private var lastCandidateIndex = -1

    val active: Boolean
        get() = draggingPlaylistId != null || settling

    val currentPointerY: Float
        get() = currentPointer.y

    val rootHeight: Float
        get() = rootSize.height.toFloat()

    fun updateRootLayout(
        topLeftInRoot: Offset,
        size: IntSize
    ) {
        rootTopLeftInRoot = topLeftInRoot
        rootSize = size
    }

    fun updateItemLayout(
        playlistId: String,
        topLeftInRoot: Offset,
        size: IntSize
    ) {
        itemBoundsByPlaylistId[playlistId] = PlaylistItemBounds(
            topLeft = topLeftInRoot - rootTopLeftInRoot,
            size = size
        )
    }

    fun playlistDragSlots(
        movablePlaylists: List<LibraryPlaylistCard>
    ): List<PlaylistDragSlot> {
        val movableIndexById = movablePlaylists
            .mapIndexed { index, playlist -> playlist.id to index }
            .toMap()

        return itemBoundsByPlaylistId.mapNotNull { (playlistId, bounds) ->
            val movableIndex = movableIndexById[playlistId] ?: return@mapNotNull null
            PlaylistDragSlot(
                playlistId = playlistId,
                movableIndex = movableIndex,
                topLeft = bounds.topLeft,
                size = bounds.size
            )
        }
    }

    fun startDrag(
        playlist: LibraryPlaylistCard,
        movablePlaylists: List<LibraryPlaylistCard>,
        downPositionInItem: Offset,
        currentPositionInItem: Offset,
        slots: List<PlaylistDragSlot>,
        hysteresisPx: Float
    ): Boolean {
        if (playlist.isSystem || movablePlaylists.none { item -> item.id == playlist.id }) {
            return false
        }

        val bounds = itemBoundsByPlaylistId[playlist.id] ?: return false

        originalPlaylists = movablePlaylists
        draggingPlaylistId = playlist.id
        draggedPlaylist = playlist
        previewPlaylists = movablePlaylists
        overlaySize = bounds.size
        pointerOffsetInsideCard = downPositionInItem
        currentPointer = bounds.topLeft + currentPositionInItem
        overlayOffset = currentPointer - pointerOffsetInsideCard
        lastCandidateIndex = movablePlaylists.indexOfFirst { item -> item.id == playlist.id }
        updatePreviewFromSlots(slots, hysteresisPx)
        return true
    }

    fun dragToItemPosition(
        positionInItem: Offset,
        slots: List<PlaylistDragSlot>,
        hysteresisPx: Float
    ) {
        val draggedId = draggingPlaylistId
        if (draggedId == null || settling) {
            return
        }

        val bounds = itemBoundsByPlaylistId[draggedId] ?: return
        currentPointer = bounds.topLeft + positionInItem
        overlayOffset = currentPointer - pointerOffsetInsideCard
        updatePreviewFromSlots(slots, hysteresisPx)
    }

    fun updatePreviewFromSlots(
        slots: List<PlaylistDragSlot>,
        hysteresisPx: Float
    ) {
        val draggedId = draggingPlaylistId ?: return
        if (settling || originalPlaylists.isEmpty() || slots.isEmpty()) {
            return
        }

        val center = overlayOffset + Offset(
            x = overlaySize.width / 2f,
            y = overlaySize.height / 2f
        )
        val candidateIndex = nearestStableSlotIndex(
            center = center,
            slots = slots,
            currentIndex = lastCandidateIndex,
            hysteresisPx = hysteresisPx
        ) ?: return

        if (candidateIndex == lastCandidateIndex) {
            return
        }

        lastCandidateIndex = candidateIndex
        previewPlaylists = previewOrder(
            playlists = originalPlaylists,
            draggedPlaylistId = draggedId,
            targetIndex = candidateIndex
        )
    }

    fun beginSettle() {
        settling = true
    }

    fun restoreOriginalPreview() {
        previewPlaylists = originalPlaylists
        lastCandidateIndex = originalPlaylists.indexOfFirst { playlist ->
            playlist.id == draggingPlaylistId
        }
    }

    fun dropResult(): PlaylistDropResult {
        val orderedPlaylists = previewPlaylists ?: originalPlaylists
        return PlaylistDropResult(
            orderedPlaylists = orderedPlaylists,
            changed = orderedPlaylists.map { playlist -> playlist.id } !=
                originalPlaylists.map { playlist -> playlist.id }
        )
    }

    suspend fun animateOverlayTo(targetOffset: Offset) {
        val animation = Animatable(
            initialValue = overlayOffset,
            typeConverter = Offset.VectorConverter
        )
        animation.animateTo(
            targetValue = targetOffset,
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis / 2,
                easing = FlowtoneMotion.Easing
            )
        ) {
            overlayOffset = value
        }
    }

    fun clear() {
        draggingPlaylistId = null
        draggedPlaylist = null
        previewPlaylists = null
        overlayOffset = Offset.Zero
        overlaySize = IntSize.Zero
        settling = false
        originalPlaylists = emptyList()
        pointerOffsetInsideCard = Offset.Zero
        currentPointer = Offset.Zero
        lastCandidateIndex = -1
    }
}

internal fun List<PlaylistDragSlot>.offsetForPlaylist(
    playlistId: String?
): Offset? {
    if (playlistId == null) {
        return null
    }
    return firstOrNull { slot -> slot.playlistId == playlistId }?.topLeft
}

private fun nearestStableSlotIndex(
    center: Offset,
    slots: List<PlaylistDragSlot>,
    currentIndex: Int,
    hysteresisPx: Float
): Int? {
    val nearestSlot = slots.minByOrNull { slot ->
        (slot.center - center).getDistance()
    } ?: return null

    if (nearestSlot.movableIndex == currentIndex) {
        return currentIndex
    }

    val currentSlot = slots.firstOrNull { slot -> slot.movableIndex == currentIndex }
        ?: return nearestSlot.movableIndex
    val nearestDistance = (nearestSlot.center - center).getDistance()
    val currentDistance = (currentSlot.center - center).getDistance()
    return if (nearestDistance + hysteresisPx < currentDistance) {
        nearestSlot.movableIndex
    } else {
        currentIndex
    }
}

private fun previewOrder(
    playlists: List<LibraryPlaylistCard>,
    draggedPlaylistId: String,
    targetIndex: Int
): List<LibraryPlaylistCard> {
    val draggedPlaylist = playlists.firstOrNull { playlist -> playlist.id == draggedPlaylistId }
        ?: return playlists
    val withoutDragged = playlists.filterNot { playlist -> playlist.id == draggedPlaylistId }
    val insertIndex = targetIndex.coerceIn(0, withoutDragged.size)
    return withoutDragged.toMutableList().apply {
        add(insertIndex, draggedPlaylist)
    }
}
