package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.CubicBezierEasing

internal const val PAUSED_ARTWORK_SCALE = 0.965f
internal const val PAUSE_ARTWORK_SCALE_DURATION_MS = 300
internal const val PAUSED_ARTWORK_ROTATION_DEGREES = 3f
internal const val PAUSE_ARTWORK_ROTATION_DURATION_MS = 300

internal const val MINI_PLAYER_SLIDE_ANIMATION_DURATION_MS = 360
internal val MiniPlayerSlideInEasing = CubicBezierEasing(0.05f, 0.85f, 0.18f, 1.0f)
