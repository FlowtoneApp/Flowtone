package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal val SettingsOptionCornerRadius = 20.dp
internal val SettingsOptionShadowExtent = 16.dp
internal val SettingsOptionSurfaceColor = Color(0xFFF5F8FF)
internal val SettingsGroupSurfaceColor = Color(0xFFE4E9F6)
private val DarkSettingsOptionSurfaceColor = Color(0xFF282C37)
private val DarkSettingsGroupSurfaceColor = Color(0xFF1C1F27)
private const val DarkIconBackgroundMaxLuminance = 0.18f
private const val DarkIconContentMinLuminance = 0.72f

@Immutable
internal data class SettingsOptionPalette(
    val surfaceColor: Color,
    val groupSurfaceColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val outlineColor: Color,
    val shadowColor: Color,
    val isDarkTheme: Boolean
) {
    fun surfaceColorWithAccent(accent: Color?, blend: Float): Color =
        accent?.let { lerp(surfaceColor, it, blend) } ?: surfaceColor

    fun iconBackgroundColor(color: Color): Color =
        if (isDarkTheme) color.withMaximumLuminance(DarkIconBackgroundMaxLuminance) else color

    fun iconContentColor(color: Color): Color =
        if (isDarkTheme) color.withMinimumLuminance(DarkIconContentMinLuminance) else color
}

// 暗色主题中保持图标底色暗，同时将前景图标提亮以维持清晰对比。
private fun Color.withMaximumLuminance(maximum: Float): Color {
    if (luminance() <= maximum) return this

    var lowerBlend = 0f
    var upperBlend = 1f
    repeat(12) {
        val blend = (lowerBlend + upperBlend) / 2f
        if (lerp(this, Color.Black, blend).luminance() > maximum) {
            lowerBlend = blend
        } else {
            upperBlend = blend
        }
    }
    return lerp(this, Color.Black, upperBlend)
}

private fun Color.withMinimumLuminance(minimum: Float): Color {
    if (luminance() >= minimum) return this

    var lowerBlend = 0f
    var upperBlend = 1f
    repeat(12) {
        val blend = (lowerBlend + upperBlend) / 2f
        if (lerp(this, Color.White, blend).luminance() < minimum) {
            lowerBlend = blend
        } else {
            upperBlend = blend
        }
    }
    return lerp(this, Color.White, upperBlend)
}

/** Resolve card roles from the active Material color scheme, including dynamic dark colors. */
@Composable
internal fun settingsOptionPalette(): SettingsOptionPalette {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.surface.luminance() <= 0.5f
    return SettingsOptionPalette(
        surfaceColor = if (isDarkTheme) DarkSettingsOptionSurfaceColor else SettingsOptionSurfaceColor,
        groupSurfaceColor = if (isDarkTheme) DarkSettingsGroupSurfaceColor else SettingsGroupSurfaceColor,
        contentColor = colorScheme.onSurface,
        secondaryContentColor = colorScheme.onSurfaceVariant,
        outlineColor = if (isDarkTheme) {
            colorScheme.outlineVariant.copy(alpha = 0.42f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        },
        shadowColor = if (isDarkTheme) Color.Transparent else Color.Black.copy(alpha = 0.025f),
        isDarkTheme = isDarkTheme
    )
}

@Composable
internal fun SettingsOptionSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SettingsOptionCornerRadius),
    surfaceColor: Color? = null,
    border: BorderStroke? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = settingsOptionPalette()
    val resolvedSurfaceColor = surfaceColor ?: palette.surfaceColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (palette.isDarkTheme) 0.dp else SettingsOptionShadowExtent,
                shape = shape,
                clip = false,
                ambientColor = palette.shadowColor,
                spotColor = palette.shadowColor
            ),
        shape = shape,
        color = resolvedSurfaceColor,
        contentColor = contentColor,
        shadowElevation = 0.dp,
        border = border
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
internal fun SettingsOptionIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val iconContentColor = settingsOptionPalette().iconContentColor(
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    SettingsOptionIconTile(modifier = modifier, contentColor = iconContentColor) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconContentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun SettingsOptionIconTile(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    themeAwareColorAdjustment: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = settingsOptionPalette()
    val resolvedContainerColor = if (themeAwareColorAdjustment) {
        palette.iconBackgroundColor(containerColor)
    } else {
        containerColor
    }
    val resolvedContentColor = if (themeAwareColorAdjustment) {
        palette.iconContentColor(contentColor)
    } else {
        contentColor
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(resolvedContainerColor),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            content()
        }
    }
}
