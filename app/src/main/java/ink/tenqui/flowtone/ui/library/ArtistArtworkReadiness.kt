package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

internal fun artistArtworkReadinessAlphaTarget(ready: Boolean): Float = if (ready) 1f else 0f

@Composable
internal fun rememberArtistArtworkReadinessAlpha(
    ready: Boolean,
    label: String
): Float {
    val alpha by animateFloatAsState(
        targetValue = artistArtworkReadinessAlphaTarget(ready),
        animationSpec = tween(
            durationMillis = FlowtoneMotion.ShortDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = label
    )
    return alpha
}
