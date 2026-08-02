package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * Observe a complete tap in the full-width band occupied by the fullscreen cover.
 * The observer never consumes pointer changes, so the player's drag gestures retain priority.
 */
internal fun Modifier.fullscreenContentTapGesture(
    enabled: Boolean,
    contentTop: Dp,
    contentHeight: Dp,
    onTap: () -> Unit
): Modifier = composed {
    val currentOnTap by rememberUpdatedState(onTap)

    pointerInput(enabled, contentTop, contentHeight) {
    val contentTopPx = contentTop.toPx()
    val contentBottomPx = contentTopPx + contentHeight.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled || !down.position.isWithinVerticalRange(contentTopPx, contentBottomPx)) {
            return@awaitEachGesture
        }

        var tapCandidate = true
        val startPosition = down.position

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (event.changes.size != 1) {
                tapCandidate = false
            }
            if ((change.position - startPosition).getDistance() > viewConfiguration.touchSlop) {
                tapCandidate = false
            }
            if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                tapCandidate = false
            }

            if (!change.pressed) {
                if (
                    tapCandidate &&
                    change.position.isWithinVerticalRange(contentTopPx, contentBottomPx)
                ) {
                    currentOnTap()
                }
                break
            }
        }
    }
}
}

@Composable
internal fun FullscreenContentTapAreaSemantics(
    enabled: Boolean,
    contentTop: Dp,
    contentHeight: Dp,
    showingLyrics: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight)
            .offset(y = contentTop)
            .semantics {
                contentDescription = if (showingLyrics) "显示封面" else "显示歌词"
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
    )
}

private fun Offset.isWithinVerticalRange(top: Float, bottom: Float): Boolean {
    return y >= top && y <= bottom
}
