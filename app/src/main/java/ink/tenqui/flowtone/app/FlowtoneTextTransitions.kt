package ink.tenqui.flowtone.app

import androidx.compose.animation.core.tween
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

internal fun <T> flowtonePageTextTween() = tween<T>(
    durationMillis = FlowtoneMotion.DurationMillis,
    easing = FlowtonePageEasing
)
