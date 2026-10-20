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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OptionGroup(
    title: String,
    modifier: Modifier = Modifier,
    surfaceColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = settingsOptionPalette()
    val groupCornerRadius = 24.dp
    val outlineWidth = 1.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val strokeWidth = outlineWidth.toPx()
                // 描边随主题切换为轻墨色或 Material outline，避免深色背景上的黑边。
                drawRoundRect(
                    color = palette.outlineColor,
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
            surfaceColor = surfaceColor ?: palette.groupSurfaceColor
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.contentColor,
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
