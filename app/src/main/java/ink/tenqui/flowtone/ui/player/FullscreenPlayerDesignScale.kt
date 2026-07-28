package ink.tenqui.flowtone.ui.player

import androidx.compose.ui.unit.Dp

internal const val FullscreenPlayerReferenceWidthDp = 480.4f

internal fun fullscreenPlayerLayoutScale(playerWidth: Dp): Float {
    return playerWidth.value / FullscreenPlayerReferenceWidthDp
}
