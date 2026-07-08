package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

fun AnimatedVisibilityScope.staggeredPageElementModifier(
    animationIndex: Int,
    exitAnimationIndex: Int = animationIndex,
    initialOffsetY: (fullHeight: Int) -> Int = { it / 6 },
    targetOffsetY: (fullHeight: Int) -> Int = { -it / 6 }
): Modifier {
    val delayMillis = FlowtoneMotion.staggerDelayMillis(animationIndex)
    val durationMillis = FlowtoneMotion.staggerDurationMillis(animationIndex)
    val exitDelayMillis = FlowtoneMotion.staggerDelayMillis(exitAnimationIndex)
    val exitDurationMillis = FlowtoneMotion.staggerDurationMillis(exitAnimationIndex)
    return Modifier.animateEnterExit(
        enter = fadeIn(
            tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) { initialOffsetY(it) },
        exit = fadeOut(
            tween(
                durationMillis = exitDurationMillis,
                delayMillis = exitDelayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = exitDurationMillis,
                delayMillis = exitDelayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) { targetOffsetY(it) }
    )
}

fun Modifier.staggeredPageProgressElement(
    animationIndex: Int,
    progress: Float
): Modifier {
    val elementProgress = staggeredPageElementProgress(
        globalProgress = progress,
        animationIndex = animationIndex
    )
    return graphicsLayer {
        alpha = elementProgress
        translationY = size.height / 6f * (1f - elementProgress)
    }
}

fun staggeredPageElementProgress(
    globalProgress: Float,
    animationIndex: Int
): Float {
    if (globalProgress >= 1f) {
        return 1f
    }
    if (globalProgress <= 0f) {
        return 0f
    }
    val delayMillis = FlowtoneMotion.staggerDelayMillis(animationIndex)
    val durationMillis = FlowtoneMotion.staggerDurationMillis(animationIndex)
    val playTimeMillis = FlowtoneMotion.DurationMillis * globalProgress
    return ((playTimeMillis - delayMillis) / durationMillis.toFloat())
        .coerceIn(0f, 1f)
        .let(FlowtoneMotion.Easing::transform)
}

@Composable
fun StaggeredPageElement(
    visible: Boolean,
    animationIndex: Int,
    modifier: Modifier = Modifier,
    applyElementMotion: Boolean = true,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val delayMillis = FlowtoneMotion.staggerDelayMillis(animationIndex)
    val durationMillis = FlowtoneMotion.staggerDurationMillis(animationIndex)
    val enterTransition = if (applyElementMotion) {
        fadeIn(
            tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) { it / 6 }
    } else {
        EnterTransition.None
    }
    val exitTransition = if (applyElementMotion) {
        fadeOut(
            tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FlowtoneMotion.Easing
            )
        ) { -it / 6 }
    } else {
        ExitTransition.None
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier
    ) {
        content()
    }
}
