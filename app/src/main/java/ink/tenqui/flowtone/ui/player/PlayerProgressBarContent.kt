package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerProgressBarContent(
    enterProgress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val progressBarTranslationY = with(density) {
        (300.dp * (1f - enterProgress)).toPx()
    }
    val progressBarScale = lerpFloat(2.6f, 1f, enterProgress)
    val progressBarAlpha = lerpFloat(0.18f, 1f, enterProgress)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progressBarAlpha
                translationY = progressBarTranslationY
                scaleX = progressBarScale
                scaleY = progressBarScale
                transformOrigin = TransformOrigin.Center
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
