package ink.tenqui.flowtone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPagesCloudPaletteTest {
    @Test
    fun lightPaletteKeepsTheExistingPageColors() {
        assertEquals(Color(0xFF7898F5), LightMainPagesCloudPalette.homeAccent)
        assertEquals(Color(0xFFA77BDD), LightMainPagesCloudPalette.libraryAccent)
        assertEquals(Color(0xFFD783A5), LightMainPagesCloudPalette.mineAccent)
    }

    @Test
    fun darkPaletteUsesDedicatedDarkerPageColors() {
        assertEquals(Color(0xFF405986), DarkMainPagesCloudPalette.homeAccent)
        assertEquals(Color(0xFF604675), DarkMainPagesCloudPalette.libraryAccent)
        assertEquals(Color(0xFF7A465B), DarkMainPagesCloudPalette.mineAccent)

        assertTrue(
            DarkMainPagesCloudPalette.homeAccent.luminance() <
                LightMainPagesCloudPalette.homeAccent.luminance()
        )
        assertTrue(
            DarkMainPagesCloudPalette.libraryAccent.luminance() <
                LightMainPagesCloudPalette.libraryAccent.luminance()
        )
        assertTrue(
            DarkMainPagesCloudPalette.mineAccent.luminance() <
                LightMainPagesCloudPalette.mineAccent.luminance()
        )
    }

    @Test
    fun themeSelectionReturnsTheStablePaletteInstances() {
        assertSame(LightMainPagesCloudPalette, mainPagesCloudPalette(isDarkTheme = false))
        assertSame(DarkMainPagesCloudPalette, mainPagesCloudPalette(isDarkTheme = true))
    }

    @Test
    fun pagePositionInterpolatesBetweenTheSameThreeAnchors() {
        val palette = LightMainPagesCloudPalette

        assertEquals(palette.homeAccent, palette.accentAt(0f))
        assertEquals(lerp(palette.homeAccent, palette.libraryAccent, 0.5f), palette.accentAt(0.5f))
        assertEquals(palette.libraryAccent, palette.accentAt(1f))
        assertEquals(lerp(palette.libraryAccent, palette.mineAccent, 0.5f), palette.accentAt(1.5f))
        assertEquals(palette.mineAccent, palette.accentAt(2f))
    }

    @Test
    fun pagePositionIsClampedToTheAvailablePages() {
        val palette = DarkMainPagesCloudPalette

        assertEquals(palette.homeAccent, palette.accentAt(-1f))
        assertEquals(palette.mineAccent, palette.accentAt(3f))
    }
}
