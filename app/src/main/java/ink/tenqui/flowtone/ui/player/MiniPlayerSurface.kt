package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil3.request.ImageRequest
import kotlin.math.abs

internal sealed interface PlayerBackdropState {
    val key: String
    val colors: List<Color>
    val backgroundImageRequest: ImageRequest?
    val coverImageRequest: ImageRequest?

    data class Artwork(
        override val key: String,
        override val colors: List<Color>,
        override val backgroundImageRequest: ImageRequest,
        override val coverImageRequest: ImageRequest
    ) : PlayerBackdropState

    data class Fallback(
        override val key: String,
        override val colors: List<Color>
    ) : PlayerBackdropState {
        override val backgroundImageRequest: ImageRequest? = null
        override val coverImageRequest: ImageRequest? = null
    }
}

internal fun Modifier.addToPlaylistBackSwipeGesture(
    enabled: Boolean,
    thresholdPx: Float,
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

                val delta = change.position - change.previousPosition
                dragX += delta.x
                dragY += delta.y

                val absDragX = abs(dragX)
                val absDragY = abs(dragY)
                val isClearRightSwipe =
                    dragX > thresholdPx && absDragX > absDragY * 1.5f

                if (isClearRightSwipe) {
                    change.consume()
                    onBack()
                    break
                }

                val verticalGestureDominates =
                    absDragY > thresholdPx && absDragY >= absDragX
                val leftSwipePassedThreshold = dragX < -thresholdPx
                if (verticalGestureDominates || leftSwipePassedThreshold) {
                    break
                }
            }
        }
    }
}

internal fun Modifier.addToPlaylistPullDownBackGesture(
    enabled: Boolean,
    listState: LazyListState,
    thresholdPx: Float,
    onBack: () -> Unit
): Modifier {
    if (!enabled) {
        return this
    }

    return pointerInput(listState, thresholdPx) {
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

                val listAtTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (!listAtTop) {
                    break
                }

                val delta = change.position - change.previousPosition
                dragX += delta.x
                dragY += delta.y

                val absDragX = abs(dragX)
                val absDragY = abs(dragY)
                val isClearPullDown =
                    dragY > thresholdPx && absDragY > absDragX * 1.25f

                if (isClearPullDown) {
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
