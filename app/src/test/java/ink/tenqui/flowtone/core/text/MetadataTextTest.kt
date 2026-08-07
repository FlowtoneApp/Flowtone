package ink.tenqui.flowtone.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataTextTest {

    @Test
    fun `repairs common utf8 latin1 mojibake`() {
        assertEquals("Café — 演示", normalizeMetadataText("Caf\u00C3\u00A9 \u00E2\u0080\u0094 \u00E6\u00BC\u0094\u00E7\u00A4\u00BA"))
    }

    @Test
    fun `keeps normal titles and symbols unchanged`() {
        val title = "% & / 中文 (Live)"
        assertEquals(title, normalizeMetadataText(title))
    }

}
