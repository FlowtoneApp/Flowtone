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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal val SettingsOptionCornerRadius = 20.dp
internal val SettingsOptionShadowExtent = 16.dp
internal val SettingsOptionSurfaceColor = Color(0xFFF5F8FF)
internal val SettingsGroupSurfaceColor = Color(0xFFE4E9F6)

@Composable
internal fun SettingsOptionSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SettingsOptionCornerRadius),
    surfaceColor: Color? = null,
    border: BorderStroke? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedSurfaceColor = surfaceColor ?: if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        SettingsOptionSurfaceColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = SettingsOptionShadowExtent,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.025f),
                spotColor = Color.Black.copy(alpha = 0.025f)
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
    SettingsOptionIconTile(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun SettingsOptionIconTile(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
