package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OptionGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val groupSurfaceColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        SettingsGroupSurfaceColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val groupCornerRadius = 24.dp
    val outlineWidth = 1.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val strokeWidth = outlineWidth.toPx()
                // 描边位于透明外沿，半透明墨色会与当前位置的背景混合，随云层明暗变化。
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.08f),
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(groupCornerRadius.toPx()),
                    style = Stroke(width = strokeWidth)
                )
            }
    ) {
        SettingsOptionSurface(
            modifier = Modifier.padding(outlineWidth),
            shape = RoundedCornerShape(groupCornerRadius - outlineWidth),
            surfaceColor = groupSurfaceColor
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}
