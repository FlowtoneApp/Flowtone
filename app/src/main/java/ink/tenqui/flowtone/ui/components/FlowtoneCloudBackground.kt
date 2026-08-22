package ink.tenqui.flowtone.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import ink.tenqui.flowtone.ui.theme.FlowtoneCloudPalette
import ink.tenqui.flowtone.ui.theme.monochromeFlowtoneCloudPalette

@Composable
internal fun Modifier.topLevelPageBackground(
    cloudPalette: FlowtoneCloudPalette,
    cloudAlpha: Float = 1f,
    cloudPlacement: TopLevelBackgroundCloudPlacement = HomeBackgroundCloudPlacement
): Modifier {
    val backgroundColor = MaterialTheme.colorScheme.background
    val safeCloudAlpha = cloudAlpha.coerceIn(0f, 1f)
    return drawBehind {
        drawRect(color = backgroundColor)
        drawTopPageColorCloud(
            cloudPalette = cloudPalette,
            backgroundColor = backgroundColor,
            cloudAlpha = safeCloudAlpha,
            cloudPlacement = cloudPlacement
        )
    }
}

internal data class TopLevelBackgroundCloudPlacement(
    val cloudCenterWidthFraction: Float,
    val cloudCenterRadiusOffsetXFactor: Float,
    val cloudCenterRadiusOffsetYFactor: Float,
    val clearCenterWidthFraction: Float,
    val clearCenterHeightFraction: Float
)

internal val HomeBackgroundCloudPlacement = TopLevelBackgroundCloudPlacement(
    cloudCenterWidthFraction = 0f,
    cloudCenterRadiusOffsetXFactor = -0.08f,
    cloudCenterRadiusOffsetYFactor = 0.08f,
    clearCenterWidthFraction = 1f,
    clearCenterHeightFraction = 1f
)

internal val LibraryBackgroundCloudPlacement = HomeBackgroundCloudPlacement.copy(
    cloudCenterWidthFraction = 0.5f,
    cloudCenterRadiusOffsetXFactor = 0f,
    cloudCenterRadiusOffsetYFactor = -0.12f
)

internal val MineBackgroundCloudPlacement = HomeBackgroundCloudPlacement.copy(
    cloudCenterWidthFraction = 1f,
    cloudCenterRadiusOffsetXFactor = 0.08f,
    clearCenterWidthFraction = 0f
)

internal val SearchBackgroundCloudPlacement = TopLevelBackgroundCloudPlacement(
    cloudCenterWidthFraction = 1f,
    cloudCenterRadiusOffsetXFactor = 0.08f,
    cloudCenterRadiusOffsetYFactor = 0.78f,
    clearCenterWidthFraction = 0f,
    clearCenterHeightFraction = 0f
)

@Composable
internal fun searchCloudPalette(): FlowtoneCloudPalette {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val accent = if (isDarkTheme) {
        Color(0xFF3D7782)
    } else {
        Color(0xFF64B6C2)
    }
    return monochromeFlowtoneCloudPalette(accent)
}

private fun DrawScope.drawTopPageColorCloud(
    cloudPalette: FlowtoneCloudPalette,
    backgroundColor: Color,
    cloudAlpha: Float,
    cloudPlacement: TopLevelBackgroundCloudPlacement
) {
    if (cloudAlpha <= 0f) return
    val cloudDiameter = size.height * TopCloudVisibleHeightFraction * 2f /
        (1f + HomeBackgroundCloudPlacement.cloudCenterRadiusOffsetYFactor)
    if (cloudDiameter <= 0f) return

    val cloudRadius = cloudDiameter / 2f
    val cloudCenter = Offset(
        x = size.width * cloudPlacement.cloudCenterWidthFraction +
            cloudRadius * cloudPlacement.cloudCenterRadiusOffsetXFactor,
        y = cloudRadius * cloudPlacement.cloudCenterRadiusOffsetYFactor
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cloudPalette.primary.copy(alpha = 0.34f * cloudAlpha),
                cloudPalette.secondary.copy(alpha = 0.22f * cloudAlpha),
                cloudPalette.tertiary.copy(alpha = 0.08f * cloudAlpha),
                Color.Transparent
            ),
            center = cloudCenter,
            radius = cloudRadius
        ),
        radius = cloudRadius,
        center = cloudCenter
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.98f * cloudAlpha),
                backgroundColor.copy(alpha = 0.72f * cloudAlpha),
                Color.Transparent
            ),
            center = Offset(
                x = size.width * cloudPlacement.clearCenterWidthFraction,
                y = size.height * cloudPlacement.clearCenterHeightFraction
            ),
            radius = size.minDimension * BottomRightClearRadiusFraction
        )
    )
}

private const val TopCloudVisibleHeightFraction = 1.30f
private const val BottomRightClearRadiusFraction = 0.82f
