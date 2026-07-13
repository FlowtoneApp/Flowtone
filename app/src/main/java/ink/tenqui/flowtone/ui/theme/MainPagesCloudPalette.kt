package ink.tenqui.flowtone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
internal data class MainPagesCloudPalette(
    val homeAccent: Color,
    val libraryAccent: Color,
    val mineAccent: Color
)

internal val LightMainPagesCloudPalette = MainPagesCloudPalette(
    homeAccent = Color(0xFF7898F5),
    libraryAccent = Color(0xFFA77BDD),
    mineAccent = Color(0xFFD783A5)
)

internal val DarkMainPagesCloudPalette = MainPagesCloudPalette(
    homeAccent = Color(0xFF405986),
    libraryAccent = Color(0xFF604675),
    mineAccent = Color(0xFF7A465B)
)

internal val LocalMainPagesCloudPalette = staticCompositionLocalOf {
    LightMainPagesCloudPalette
}

internal fun mainPagesCloudPalette(isDarkTheme: Boolean): MainPagesCloudPalette {
    return if (isDarkTheme) {
        DarkMainPagesCloudPalette
    } else {
        LightMainPagesCloudPalette
    }
}

internal fun MainPagesCloudPalette.accentAt(pagePosition: Float): Color {
    val safePosition = pagePosition.coerceIn(0f, MainPagesLastPagePosition)
    return if (safePosition <= 1f) {
        lerp(homeAccent, libraryAccent, safePosition)
    } else {
        lerp(libraryAccent, mineAccent, safePosition - 1f)
    }
}

private const val MainPagesLastPagePosition = 2f
