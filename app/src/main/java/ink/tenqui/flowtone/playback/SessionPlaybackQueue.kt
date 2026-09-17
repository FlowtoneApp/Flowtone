package ink.tenqui.flowtone.playback

/** Service-owned logical queue. The exposed list is already in effective playback order. */
internal class SessionPlaybackQueue(
    private val shuffle: (List<PlaybackQueueItem>) -> List<PlaybackQueueItem> = { it.shuffled() }
) {
    var sourceItems: List<PlaybackQueueItem> = emptyList()
        private set

    var playbackItems: List<PlaybackQueueItem> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var orderMode: PlaybackOrderMode = PlaybackOrderMode.Sequence
        private set

    val currentItem: PlaybackQueueItem?
        get() = playbackItems.getOrNull(currentIndex)

    val previousIndex: Int?
        get() = (currentIndex - 1).takeIf { it in playbackItems.indices }

    val nextIndex: Int?
        get() = (currentIndex + 1).takeIf { it in playbackItems.indices }

    fun replace(
        items: List<PlaybackQueueItem>,
        selectedIndex: Int,
        mode: PlaybackOrderMode = orderMode
    ) {
        val normalized = items.mapIndexed { index, item -> item.withSourceIndex(index) }
        sourceItems = normalized
        orderMode = mode
        val selected = normalized.getOrNull(selectedIndex)
        rebuild(selected?.queueId)
    }

    fun clear() {
        sourceItems = emptyList()
        playbackItems = emptyList()
        currentIndex = -1
    }

    fun select(index: Int): PlaybackQueueItem? {
        if (index !in playbackItems.indices) return null
        currentIndex = index
        return playbackItems[index]
    }

    fun updateItem(updated: PlaybackQueueItem) {
        sourceItems = sourceItems.map { item ->
            if (item.queueId == updated.queueId) updated.withSourceIndex(item.sourceIndex) else item
        }
        playbackItems = playbackItems.map { item ->
            if (item.queueId == updated.queueId) updated.withSourceIndex(item.sourceIndex) else item
        }
    }

    fun setOrderMode(mode: PlaybackOrderMode) {
        if (mode == orderMode) return
        val currentQueueId = currentItem?.queueId
        orderMode = mode
        rebuild(currentQueueId)
    }

    fun append(items: List<PlaybackQueueItem>) {
        if (items.isEmpty()) return
        val currentQueueId = currentItem?.queueId
        sourceItems = (sourceItems + items).mapIndexed { index, item -> item.withSourceIndex(index) }
        rebuild(currentQueueId)
    }

    fun addNext(items: List<PlaybackQueueItem>) {
        if (items.isEmpty()) return
        val current = currentItem
        if (current == null) {
            replace(items, 0, orderMode)
            return
        }
        val sourceInsertionIndex = (current.sourceIndex + 1).coerceIn(0, sourceItems.size)
        sourceItems = sourceItems.toMutableList().apply {
            addAll(sourceInsertionIndex, items)
        }.mapIndexed { index, item -> item.withSourceIndex(index) }

        val currentQueueId = current.queueId
        if (orderMode == PlaybackOrderMode.Shuffle) {
            val normalizedById = sourceItems.associateBy(PlaybackQueueItem::queueId)
            val retained = playbackItems.mapNotNull { normalizedById[it.queueId] }.toMutableList()
            val playbackInsertionIndex = (currentIndex + 1).coerceIn(0, retained.size)
            retained.addAll(playbackInsertionIndex, items.mapIndexed { index, item ->
                item.withSourceIndex(sourceInsertionIndex + index)
            })
            playbackItems = retained
            currentIndex = playbackItems.indexOfFirst { it.queueId == currentQueueId }
        } else {
            rebuild(currentQueueId)
        }
    }

    fun removePlaybackRange(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in 0..playbackItems.size || toIndex !in 0..playbackItems.size) return
        if (fromIndex >= toIndex) return
        val oldCurrentId = currentItem?.queueId
        val removedIds = playbackItems.subList(fromIndex, toIndex)
            .mapTo(mutableSetOf(), PlaybackQueueItem::queueId)
        val remainingSource = sourceItems.filterNot { it.queueId in removedIds }
        if (remainingSource.isEmpty()) {
            clear()
            return
        }
        val fallbackPlaybackIndex = fromIndex.coerceAtMost(playbackItems.size - removedIds.size - 1)
        val fallbackId = playbackItems.filterNot { it.queueId in removedIds }
            .getOrNull(fallbackPlaybackIndex)
            ?.queueId
        val selectedId = oldCurrentId?.takeUnless { it in removedIds } ?: fallbackId
        sourceItems = remainingSource.mapIndexed { index, item -> item.withSourceIndex(index) }
        rebuild(selectedId)
    }

    private fun rebuild(currentQueueId: String?) {
        playbackItems = when (orderMode) {
            PlaybackOrderMode.Sequence,
            PlaybackOrderMode.RepeatOne -> sourceItems
            PlaybackOrderMode.Shuffle -> {
                val current = sourceItems.firstOrNull { it.queueId == currentQueueId }
                val rest = sourceItems.filterNot { it.queueId == currentQueueId }
                if (current == null) shuffle(rest) else listOf(current) + shuffle(rest)
            }
        }
        currentIndex = when {
            playbackItems.isEmpty() -> -1
            currentQueueId == null -> 0
            else -> playbackItems.indexOfFirst { it.queueId == currentQueueId }
                .takeIf { it >= 0 } ?: 0
        }
    }
}
