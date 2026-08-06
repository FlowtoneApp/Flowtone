package ink.tenqui.flowtone.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerKeepScreenOnTest {
    @Test
    fun `lyrics page keeps screen on when screen off is not allowed`() {
        assertTrue(
            shouldKeepScreenOnForLyricsPage(
                lyricsModeActive = true,
                allowScreenOffOnLyricsPage = false
            )
        )
    }

    @Test
    fun `lyrics page follows system timeout when screen off is allowed`() {
        assertFalse(
            shouldKeepScreenOnForLyricsPage(
                lyricsModeActive = true,
                allowScreenOffOnLyricsPage = true
            )
        )
    }

    @Test
    fun `non lyrics content never requests keeping screen on`() {
        assertFalse(
            shouldKeepScreenOnForLyricsPage(
                lyricsModeActive = false,
                allowScreenOffOnLyricsPage = false
            )
        )
    }
}
