package ink.tenqui.flowtone.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import ink.tenqui.flowtone.ui.components.FlowtoneHeaderCollapseScrollDistance
import ink.tenqui.flowtone.ui.components.FlowtoneHeaderCollapseStartOffset
import ink.tenqui.flowtone.ui.components.FlowtoneLazyHeaderItemScrollDistance
import ink.tenqui.flowtone.ui.components.FlowtoneSubtitleFadeEndProgress
import ink.tenqui.flowtone.ui.components.rememberFlowtoneScrollHeaderCollapseProgress

@Immutable
internal data class TopLevelPageCollapseProgress(
    val home: Float,
    val library: Float,
    val mine: Float
) {
    fun progressFor(page: TopLevelPage): Float {
        return when (page) {
            TopLevelPage.Home -> home
            TopLevelPage.Library -> library
            TopLevelPage.Mine -> mine
        }
    }
}

@Composable
internal fun rememberTopLevelPageCollapseProgress(
    homeScrollState: ScrollState,
    libraryListState: LazyListState
): TopLevelPageCollapseProgress {
    val homeProgress = rememberFlowtoneScrollHeaderCollapseProgress(
        scrollState = homeScrollState,
        startOffset = TopLevelHomeHeaderCollapseStartOffset,
        distance = TopLevelHeaderCollapseScrollDistance
    )
    val density = LocalDensity.current
    val libraryProgress = libraryHeaderCollapseProgress(
        firstIndex = libraryListState.firstVisibleItemIndex,
        firstOffset = libraryListState.firstVisibleItemScrollOffset.toFloat(),
        headerHeight = libraryListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat(),
        start = with(density) { TopLevelLibraryHeaderCollapseStartOffset.toPx() },
        distance = with(density) { TopLevelHeaderCollapseScrollDistance.toPx() }
    )

    return TopLevelPageCollapseProgress(
        home = homeProgress,
        library = libraryProgress,
        mine = 0f
    )
}

internal fun libraryHeaderCollapseProgress(firstIndex: Int, firstOffset: Float, headerHeight: Float?, start: Float, distance: Float): Float {
    if (firstIndex > 0) return 1f
    val available = headerHeight?.let { (it - start).coerceAtLeast(1f) } ?: distance
    return ((firstOffset - start) / minOf(distance, available).coerceAtLeast(1f)).coerceIn(0f, 1f)
}

internal val TopLevelHomeHeaderCollapseStartOffset: Dp = FlowtoneHeaderCollapseStartOffset
internal val TopLevelLibraryHeaderCollapseStartOffset: Dp = 18.dp
internal val TopLevelLibraryHeaderItemScrollDistance: Dp = FlowtoneLazyHeaderItemScrollDistance
internal val TopLevelHeaderCollapseScrollDistance: Dp = FlowtoneHeaderCollapseScrollDistance
internal const val TopLevelSubtitleFadeEndProgress = FlowtoneSubtitleFadeEndProgress
internal const val TopLevelCollapsedTitleFallbackScale = 0.74f
