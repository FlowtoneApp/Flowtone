package ink.tenqui.flowtone.data.listening

internal fun <T> rankListeningStats(
    items: Iterable<T>,
    durationSelector: (T) -> Long,
    playCountSelector: (T) -> Int,
    stableOrderSelector: (T) -> Long
): List<T> {
    return items.sortedWith(
        compareByDescending<T> { item -> durationSelector(item) }
            .thenByDescending { item -> playCountSelector(item) }
            .thenBy { item -> stableOrderSelector(item) }
    )
}
