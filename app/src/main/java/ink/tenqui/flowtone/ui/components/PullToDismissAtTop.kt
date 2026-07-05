package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.pullToDismissAtTop(
    listState: LazyListState,
    enabled: Boolean = true,
    threshold: Dp = 64.dp,
    onDismiss: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val currentEnabled = rememberUpdatedState(enabled)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val connection = remember(listState, thresholdPx) {
        var pullDistancePx = 0f
        var dismissStarted = false

        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!currentEnabled.value) {
                    pullDistancePx = 0f
                    dismissStarted = false
                    return Offset.Zero
                }

                val listAtTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0

                if (!dismissStarted && listAtTop && available.y > 0f) {
                    pullDistancePx += available.y
                    if (pullDistancePx >= thresholdPx) {
                        dismissStarted = true
                        currentOnDismiss.value()
                    }
                } else if (!listAtTop || available.y < 0f || consumed.y < 0f) {
                    pullDistancePx = 0f
                }

                return Offset.Zero
            }
        }
    }

    nestedScroll(connection)
}
