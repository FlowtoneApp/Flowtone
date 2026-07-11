package ink.tenqui.flowtone.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val density = LocalDensity.current
    val homeStartOffsetPx = with(density) { TopLevelHomeHeaderCollapseStartOffset.toPx() }
    val libraryStartOffsetPx = with(density) { TopLevelLibraryHeaderCollapseStartOffset.toPx() }
    val libraryHeaderScrollDistancePx = with(density) {
        TopLevelLibraryHeaderItemScrollDistance.toPx()
    }
    val distancePx = with(density) { TopLevelHeaderCollapseScrollDistance.toPx() }
    val homeProgress by remember(homeScrollState, homeStartOffsetPx, distancePx) {
        derivedStateOf {
            topLevelHeaderCollapseProgress(
                scrollOffsetPx = homeScrollState.value.toFloat(),
                startOffsetPx = homeStartOffsetPx,
                distancePx = distancePx
            )
        }
    }
    val libraryProgress by remember(
        libraryListState,
        libraryStartOffsetPx,
        libraryHeaderScrollDistancePx,
        distancePx
    ) {
        derivedStateOf {
            val scrollOffsetPx = if (libraryListState.firstVisibleItemIndex > 0) {
                libraryHeaderScrollDistancePx +
                    libraryListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                libraryListState.firstVisibleItemScrollOffset.toFloat()
            }
            topLevelHeaderCollapseProgress(
                scrollOffsetPx = scrollOffsetPx,
                startOffsetPx = libraryStartOffsetPx,
                distancePx = distancePx
            )
        }
    }

    return TopLevelPageCollapseProgress(
        home = homeProgress,
        library = libraryProgress,
        mine = 0f
    )
}

internal val TopLevelHomeHeaderCollapseStartOffset: Dp = 30.dp
internal val TopLevelLibraryHeaderCollapseStartOffset: Dp = 18.dp
internal val TopLevelLibraryHeaderItemScrollDistance: Dp = 98.dp
internal val TopLevelHeaderCollapseScrollDistance: Dp = 96.dp
internal val TopLevelHeaderCollapsedTravelY: Dp = (-101).dp
internal const val TopLevelSubtitleFadeEndProgress = 0.46f
internal const val TopLevelCollapsedTitleFallbackScale = 0.74f

private fun topLevelHeaderCollapseProgress(
    scrollOffsetPx: Float,
    startOffsetPx: Float,
    distancePx: Float
): Float {
    if (distancePx <= 0f) return 0f
    return ((scrollOffsetPx - startOffsetPx) / distancePx).coerceIn(0f, 1f)
}
