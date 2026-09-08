package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

internal val HorizontalEndDragActionThreshold = 64.dp

internal class HorizontalEndDragActionState(
    private val thresholdPx: Float
) {
    private var pointerDragActive = false
    private var accumulatedDistancePx = 0f
    private var triggered = false

    fun onDragStart() {
        pointerDragActive = true
        accumulatedDistancePx = 0f
        triggered = false
    }

    fun onDragDelta(
        enabled: Boolean,
        atEnd: Boolean,
        source: NestedScrollSource,
        availableX: Float
    ): Boolean {
        if (!enabled || !pointerDragActive || !atEnd || source != NestedScrollSource.UserInput) {
            accumulatedDistancePx = 0f
            return false
        }
        val towardEndDistance = (-availableX).coerceAtLeast(0f)
        if (towardEndDistance == 0f) {
            accumulatedDistancePx = 0f
            return false
        }
        accumulatedDistancePx += towardEndDistance
        if (!triggered && accumulatedDistancePx >= thresholdPx) {
            triggered = true
            return true
        }
        return false
    }

    fun onDragEnd() {
        reset()
    }

    fun onFling() {
        reset()
    }

    internal fun accumulatedDistanceForTest(): Float = accumulatedDistancePx

    private fun reset() {
        pointerDragActive = false
        accumulatedDistancePx = 0f
        triggered = false
    }
}

internal fun Modifier.horizontalEndDragAction(
    listState: LazyListState,
    enabled: Boolean,
    threshold: Dp = HorizontalEndDragActionThreshold,
    onTriggered: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val actionState = remember(listState, thresholdPx) {
        HorizontalEndDragActionState(thresholdPx)
    }
    val currentOnTriggered by rememberUpdatedState(onTriggered)

    LaunchedEffect(listState, actionState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> actionState.onDragStart()
                is DragInteraction.Stop,
                is DragInteraction.Cancel -> actionState.onDragEnd()
            }
        }
    }

    val connection = remember(listState, actionState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (actionState.onDragDelta(
                        enabled = true,
                        atEnd = !listState.canScrollForward,
                        source = source,
                        availableX = available.x
                    )
                ) {
                    currentOnTriggered()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                actionState.onFling()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                actionState.onFling()
                return Velocity.Zero
            }
        }
    }
    this.nestedScroll(connection)
}
