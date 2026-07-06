package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal fun Modifier.fullscreenContentBackGesture(
    enabled: Boolean,
    thresholdPx: Float,
    canStartBack: () -> Boolean = { true },
    onBack: () -> Unit
): Modifier {
    if (!enabled) {
        return this
    }

    return pointerInput(thresholdPx) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var dragX = 0f
            var dragY = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break

                if (!event.changes.any { pointer -> pointer.pressed }) {
                    break
                }
                if (!canStartBack()) {
                    break
                }

                val delta = change.position - change.previousPosition
                dragX += delta.x
                dragY += delta.y

                val absDragX = abs(dragX)
                val absDragY = abs(dragY)
                val isClearRightSwipe =
                    dragX > thresholdPx && absDragX > absDragY * 1.5f
                val isClearPullDown =
                    dragY > thresholdPx && absDragY > absDragX * 1.25f

                if (isClearRightSwipe || isClearPullDown) {
                    change.consume()
                    onBack()
                    break
                }

                val upwardPassedThreshold = dragY < -thresholdPx
                val horizontalGestureDominates =
                    absDragX > thresholdPx && absDragX >= absDragY
                if (upwardPassedThreshold || horizontalGestureDominates) {
                    break
                }
            }
        }
    }
}
