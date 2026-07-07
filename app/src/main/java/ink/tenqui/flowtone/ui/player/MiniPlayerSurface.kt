package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

@Composable
internal fun MiniPlayerVisualSurface(
    hostHeight: Dp,
    miniPlayerSlideOffsetY: Dp,
    visibleProgress: Float,
    fullscreenProgress: Float,
    queueSheetBackgroundBlurRadius: Dp,
    animationProgress: Float,
    visualPanelTop: Dp,
    visualPanelHeight: Dp,
    dragHotZoneHeight: Dp,
    handleOffsetY: Dp,
    hasCurrentSong: Boolean,
    expanded: Boolean,
    interactionSource: MutableInteractionSource,
    gestureModifier: Modifier,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    panelContent: @Composable BoxScope.(RoundedCornerShape) -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hostHeight)
            .graphicsLayer {
                translationY = miniPlayerSlideOffsetY.toPx()
                alpha = visibleProgress
                clip = fullscreenProgress > 0.01f
            }
    ) {
        val playerShape = RoundedCornerShape(
            topStart = lerpDp(24.dp, 0.dp, fullscreenProgress),
            topEnd = lerpDp(24.dp, 0.dp, fullscreenProgress),
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        val playerShadowElevation = lerpDp(0.dp, 18.dp, animationProgress)
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(queueSheetBackgroundBlurRadius)
        ) {
            PlayerDragHandle(
                animationProgress = animationProgress,
                hasCurrentSong = hasCurrentSong,
                expanded = expanded,
                interactionSource = interactionSource,
                onActivate = onActivate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dragHotZoneHeight)
                    .graphicsLayer {
                        translationY = handleOffsetY.toPx()
                    }
                    .align(Alignment.TopCenter)
                    .then(gestureModifier)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = visualPanelTop)
                    .fillMaxWidth()
                    .height(visualPanelHeight)
                    .shadow(
                        elevation = playerShadowElevation,
                        shape = playerShape,
                        clip = false
                    )
                    .clickable(
                        enabled = hasCurrentSong && !expanded,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onActivate
                    )
            ) {
                panelContent(playerShape)
            }
        }
        overlayContent()
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
