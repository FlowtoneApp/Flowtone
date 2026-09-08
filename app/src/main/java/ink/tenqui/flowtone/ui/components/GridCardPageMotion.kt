package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

internal data class GridViewportItem(
    val key: Any,
    val index: Int,
    val x: Int,
    val y: Int
)

internal fun gridViewportKeys(items: List<GridViewportItem>): List<Any> = items
    .sortedWith(compareBy(GridViewportItem::y, GridViewportItem::x, GridViewportItem::index))
    .map(GridViewportItem::key)
    .distinct()

@Composable
internal fun rememberGridCardPageMotion(
    sessionKey: Any,
    gridState: LazyGridState,
    pageTransition: PageTransitionScope
): PageViewportStaggerMotionScope {
    val visibleKeys by remember(gridState) {
        derivedStateOf {
            gridViewportKeys(
                gridState.layoutInfo.visibleItemsInfo.map { item ->
                    GridViewportItem(
                        key = item.key,
                        index = item.index,
                        x = item.offset.x,
                        y = item.offset.y
                    )
                }
            )
        }
    }
    return rememberPageViewportStaggerMotion(
        sessionKey = sessionKey,
        visibleKeys = visibleKeys,
        pageTransition = pageTransition
    )
}
