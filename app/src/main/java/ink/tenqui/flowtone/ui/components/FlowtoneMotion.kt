package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object FlowtoneMotion {
    const val DurationMillis = 400
    const val ExitDurationMillis = 200
    val StaggerOffset: Dp = 24.dp
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun staggerDelayMillis(index: Int): Int {
        return (index.coerceAtLeast(0) * 32).coerceAtMost(180)
    }

    fun staggerDurationMillis(index: Int): Int {
        return DurationMillis - staggerDelayMillis(index)
    }

    fun staggerExitDurationMillis(index: Int): Int {
        return (ExitDurationMillis - staggerDelayMillis(index)).coerceAtLeast(1)
    }
}
