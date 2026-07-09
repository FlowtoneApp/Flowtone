package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundModifier = if (drawBackground) {
        Modifier.homeScreenBackground()
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        HomeHeader(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 21.dp, top = 48.dp, end = 20.dp)
        )
    }
}

@Composable
internal fun Modifier.homeScreenBackground(): Modifier {
    val backgroundColor = MaterialTheme.colorScheme.background
    return drawBehind {
        drawRect(color = backgroundColor)
        drawHomeTopColorCloud()
    }
}

private fun DrawScope.drawHomeTopColorCloud() {
    val cloudDiameter = size.height * 0.8f
    if (cloudDiameter <= 0f) return

    val cloudRadius = cloudDiameter / 2f
    val cloudCenter = Offset(
        x = cloudRadius * 0.35f,
        y = cloudRadius * 0.35f
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF4D8DFF).copy(alpha = 0.28f),
                Color(0xFF6BA7FF).copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = cloudCenter,
            radius = cloudRadius
        ),
        radius = cloudRadius,
        center = cloudCenter
    )
}

@Composable
private fun HomeHeader(
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Text(
            text = "声流",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "收藏在设备里的每一段声音",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
