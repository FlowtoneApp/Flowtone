package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

internal fun Modifier.swipeToChangeSong(
    enabled: Boolean,
    thresholdPx: Float,
    ignoredStartYRangePx: ClosedFloatingPointRange<Float>? = null,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    if (!enabled) {
        return this
    }

    return this.pointerInput(
        enabled,
        thresholdPx,
        ignoredStartYRangePx?.start,
        ignoredStartYRangePx?.endInclusive
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (ignoredStartYRangePx != null && down.position.y in ignoredStartYRangePx) {
                return@awaitEachGesture
            }

            var horizontalLocked = false
            var triggered = false
            val horizontalDominanceRatio = 1.25f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: event.changes.firstOrNull()
                    ?: continue

                if (!change.pressed) {
                    break
                }

                val totalOffset = change.position - down.position
                val totalX = totalOffset.x
                val totalY = totalOffset.y

                if (!horizontalLocked) {
                    val movedEnough =
                        abs(totalX) > viewConfiguration.touchSlop ||
                            abs(totalY) > viewConfiguration.touchSlop
                    if (!movedEnough) {
                        continue
                    }

                    if (abs(totalX) <= abs(totalY) * horizontalDominanceRatio) {
                        break
                    }

                    horizontalLocked = true
                }

                if (horizontalLocked) {
                    val positionChange = change.positionChange()
                    if (positionChange != Offset.Zero) {
                        change.consume()
                    }

                    if (!triggered && abs(totalX) >= thresholdPx) {
                        triggered = true
                        if (totalX < 0f) {
                            onSwipeLeft()
                        } else {
                            onSwipeRight()
                        }
                    }
                }
            }
        }
    }
}
