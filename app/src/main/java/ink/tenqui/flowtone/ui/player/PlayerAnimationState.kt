package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp

internal const val MINI_PLAYER_ANIMATION_DURATION_MS = 400
internal const val FLOWTONE_CLOUD_COLORS_TAG = "FlowtoneCloudColors"
internal val MiniPlayerEasing = CubicBezierEasing(0.12f, 0.34f, 0.16f, 1f)
internal val SoftElementEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
internal val HeavyElementEasing = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)
internal val MiniPlayerMotionEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
internal val TrackSwitchProgressEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

internal fun lerpFloat(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

internal fun lerpDp(start: Dp, end: Dp, progress: Float): Dp {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

internal fun delayedProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) {
        return progress.coerceIn(0f, 1f)
    }
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

internal fun easeMiniPlayerMotion(progress: Float): Float {
    return MiniPlayerMotionEasing.transform(progress.coerceIn(0f, 1f))
}
