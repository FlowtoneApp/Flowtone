package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import kotlin.math.roundToInt

@Composable
internal fun fullscreenMenuEnterTransition(): EnterTransition {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { FullscreenMoreMenuSlideDistance.toPx().roundToInt() }
    return fadeIn(
        tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) { slideDistancePx }
}

@Composable
internal fun fullscreenMenuExitTransition(): ExitTransition {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { FullscreenMoreMenuSlideDistance.toPx().roundToInt() }
    return fadeOut(
        tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) { slideDistancePx }
}

@Composable
internal fun fullscreenMoreButtonEnterTransition(): EnterTransition {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { FullscreenMoreMenuSlideDistance.toPx().roundToInt() }
    return fadeIn(
        tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) { -slideDistancePx }
}

@Composable
internal fun fullscreenMoreButtonExitTransition(): ExitTransition {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { FullscreenMoreMenuSlideDistance.toPx().roundToInt() }
    return fadeOut(
        tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        )
    ) { -slideDistancePx }
}
