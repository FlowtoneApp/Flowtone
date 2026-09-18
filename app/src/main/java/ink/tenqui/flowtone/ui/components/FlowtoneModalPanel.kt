package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Flowtone 应用内 modal 共用的标准窗口视觉；Overlay Shell 只负责遮罩和动画。 */
@Composable
internal fun FlowtoneModalPanel(
    modifier: Modifier = Modifier,
    maxWidth: Dp? = 360.dp,
    horizontalPadding: Dp = 28.dp,
    fillContentHeight: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val panelModifier = Modifier
        .padding(horizontal = horizontalPadding)
        .let { base ->
            if (maxWidth != null) base.widthIn(max = maxWidth) else base
        }
        .fillMaxWidth()
        .then(modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {}
        )
    val contentModifier = Modifier
        .fillMaxWidth()
        .then(if (fillContentHeight) Modifier.fillMaxHeight() else Modifier)
        .padding(24.dp)

    Surface(
        modifier = panelModifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    ) {
        Column(modifier = contentModifier, content = content)
    }
}
