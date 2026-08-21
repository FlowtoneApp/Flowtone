package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object PageMotion {
    const val DurationMillis = 400
    val Offset: Dp = 24.dp
    val PageBlurRadius: Dp = 12.dp
    const val MaxStaggerFraction = 0.32f
    const val DefaultOrderCount = 24
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun elementProgress(
        pageProgress: Float,
        order: Int,
        orderCount: Int
    ): Float {
        val startFraction = staggerStartFraction(order, orderCount)
        val localDuration = 1f - startFraction
        return ((pageProgress.coerceIn(0f, 1f) - startFraction) / localDuration)
            .coerceIn(0f, 1f)
            .let(Easing::transform)
    }

    fun staggerStartFraction(order: Int, orderCount: Int): Float {
        val safeCount = orderCount.coerceAtLeast(1)
        val safeOrder = order.coerceIn(0, safeCount - 1)
        return if (safeCount == 1) {
            0f
        } else {
            safeOrder.toFloat() / (safeCount - 1) * MaxStaggerFraction
        }
    }
}
