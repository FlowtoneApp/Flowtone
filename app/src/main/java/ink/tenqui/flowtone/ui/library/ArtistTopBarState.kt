package ink.tenqui.flowtone.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class ArtistTopBarThresholds(
    val showPx: Int,
    val hidePx: Int
)

internal fun artistTopBarThresholds(
    headerHeightPx: Int,
    topBarHeightPx: Int,
    hysteresisPx: Int
): ArtistTopBarThresholds {
    val show = (headerHeightPx - topBarHeightPx).coerceAtLeast(0)
    return ArtistTopBarThresholds(
        showPx = show,
        hidePx = (show - hysteresisPx.coerceAtLeast(0)).coerceAtLeast(0)
    )
}

internal fun artistTopBarVisible(
    wasVisible: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    thresholds: ArtistTopBarThresholds
): Boolean {
    val index = firstVisibleItemIndex.coerceAtLeast(0)
    val offset = firstVisibleItemScrollOffset.coerceAtLeast(0)
    val passedShowThreshold = index > 0 || offset >= thresholds.showPx
    val returnedPastHideThreshold = index == 0 && offset <= thresholds.hidePx
    return when {
        !wasVisible && passedShowThreshold -> true
        wasVisible && returnedPastHideThreshold -> false
        else -> wasVisible
    }
}

@Stable
internal class ArtistTopBarStateOwner internal constructor(
    val entryKey: String,
    initialScrollPosition: ArtistScrollPosition
) {
    var visible by mutableStateOf(initialScrollPosition.firstVisibleItemIndex > 0)
        private set

    fun update(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        thresholds: ArtistTopBarThresholds
    ) {
        visible = artistTopBarVisible(
            wasVisible = visible,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            thresholds = thresholds
        )
    }
}

internal class ArtistTopBarStateStore {
    private val owners = mutableMapOf<String, ArtistTopBarStateOwner>()

    fun ownerFor(
        entryKey: String,
        initialScrollPosition: ArtistScrollPosition = ArtistScrollPosition()
    ): ArtistTopBarStateOwner = owners.getOrPut(entryKey) {
        ArtistTopBarStateOwner(entryKey, initialScrollPosition)
    }

    fun owner(entryKey: String?): ArtistTopBarStateOwner? = entryKey?.let(owners::get)

    fun retainEntries(entryKeys: Set<String>) {
        owners.keys.retainAll(entryKeys)
    }
}
