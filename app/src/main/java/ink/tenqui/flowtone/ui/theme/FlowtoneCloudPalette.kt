package ink.tenqui.flowtone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class FlowtoneCloudPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

internal fun monochromeFlowtoneCloudPalette(accent: Color): FlowtoneCloudPalette {
    return FlowtoneCloudPalette(
        primary = accent,
        secondary = accent,
        tertiary = accent
    )
}
