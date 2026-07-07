package ink.tenqui.flowtone.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

@Composable
internal fun FlowtoneModalOverlayShell(
    visible: Boolean,
    scrimAlpha: Float,
    panelProgress: Float,
    panelScale: Float,
    shadowSafePadding: Dp,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val clampedProgress = panelProgress.coerceIn(0f, 1f)

    BackHandler(enabled = visible, onBack = onDismissRequest)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val constraintsScope = this

        if (scrimAlpha > 0.001f || visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = onDismissRequest
                    )
            )
        }

        if (visible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = clampedProgress
                            scaleX = panelScale
                            scaleY = panelScale
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                        .padding(shadowSafePadding),
                    contentAlignment = Alignment.Center
                ) {
                    constraintsScope.content()
                }
            }
        }
    }
}
