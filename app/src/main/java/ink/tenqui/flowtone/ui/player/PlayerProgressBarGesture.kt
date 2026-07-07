package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun BoxScope.PlayerProgressBarGestureLayer(
    enabled: Boolean,
    durationMs: Long,
    containerSize: IntSize,
    onContainerSizeChange: (IntSize) -> Unit,
    onEnterScrubbing: (Float) -> Unit,
    onUpdateScrubbing: (Float) -> Unit,
    onScrubSeek: () -> Unit,
    onTapSeek: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlaybackProgressTouchHeight)
            .align(Alignment.TopCenter)
            .onSizeChanged { size ->
                onContainerSizeChange(size)
            }
            .pointerInput(enabled, durationMs, containerSize) {
                if (!enabled || durationMs <= 0L) {
                    return@pointerInput
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragStarted = false
                    var longPressStarted = false
                    var lastX = down.position.x
                    val longPressDeadlineMillis =
                        down.uptimeMillis + PlaybackProgressLongPressTimeoutMillis
                    var lastEventTimeMillis = down.uptimeMillis

                    while (true) {
                        val event = if (!dragStarted && !longPressStarted) {
                            val remainingMillis =
                                longPressDeadlineMillis - lastEventTimeMillis
                            if (remainingMillis <= 0L) {
                                null
                            } else {
                                withTimeoutOrNull(remainingMillis) {
                                    awaitPointerEvent()
                                }
                            }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            longPressStarted = true
                            onEnterScrubbing(lastX)
                            continue
                        }

                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: continue
                        lastEventTimeMillis = change.uptimeMillis

                        if (!change.pressed) {
                            break
                        }

                        lastX = change.position.x
                        val distanceFromDown = (change.position - down.position).getDistance()
                        if (
                            !dragStarted &&
                            !longPressStarted &&
                            distanceFromDown > viewConfiguration.touchSlop
                        ) {
                            dragStarted = true
                            onEnterScrubbing(change.position.x)
                        }

                        if (dragStarted || longPressStarted) {
                            onUpdateScrubbing(change.position.x)
                            change.consume()
                        }
                    }

                    if (dragStarted || longPressStarted) {
                        onScrubSeek()
                    } else {
                        onTapSeek(lastX)
                    }
                }
            }
    )
}
