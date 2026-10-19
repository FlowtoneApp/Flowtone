package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing as AnimationEasing
import androidx.compose.animation.core.LinearEasing
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

    /**
     * 完整进入保持原来的线性主进度；打断后从当前视觉状态沿同一曲线走向目标。
     * 直接倒放主进度会倒放 ease-out 的平缓尾段，让晚期返回长时间近乎不可见。
     * 先在视觉空间插值，再反解主进度，alpha、位移和 blur 仍共享唯一的时钟。
     */
    fun progressAnimationEasing(initialProgress: Float, targetProgress: Float): AnimationEasing {
        if (initialProgress == targetProgress || initialProgress == 0f && targetProgress == 1f) {
            return LinearEasing
        }
        val initialVisualProgress = Easing.transform(initialProgress.coerceIn(0f, 1f))
        val targetVisualProgress = Easing.transform(targetProgress.coerceIn(0f, 1f))
        return AnimationEasing { fraction ->
            when {
                fraction <= 0f -> 0f
                fraction >= 1f -> 1f
                else -> {
                    val visualProgress = initialVisualProgress +
                        (targetVisualProgress - initialVisualProgress) * Easing.transform(fraction)
                    val pageProgress = inverseEasing(visualProgress)
                    ((pageProgress - initialProgress) / (targetProgress - initialProgress))
                        .coerceIn(0f, 1f)
                }
            }
        }
    }

    private fun inverseEasing(visualProgress: Float): Float {
        if (visualProgress <= 0f) return 0f
        if (visualProgress >= 1f) return 1f
        var lower = 0f
        var upper = 1f
        repeat(18) {
            val middle = (lower + upper) * 0.5f
            if (Easing.transform(middle) < visualProgress) lower = middle else upper = middle
        }
        return (lower + upper) * 0.5f
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
