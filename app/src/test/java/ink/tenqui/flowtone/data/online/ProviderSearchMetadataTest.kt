package ink.tenqui.flowtone.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderSearchMetadataTest {
    private val labels = ProviderSearchMetadataLabels("首", "次播放")

    @Test fun trackCountIsFormattedByHost() {
        assertEquals("24首", formatProviderSearchMetadata(
            ProviderSearchMetadata("track_count", value = 24), labels
        ))
    }

    @Test fun playCountUsesChineseCompactNumber() {
        assertEquals("18.6万次播放", formatProviderSearchMetadata(
            ProviderSearchMetadata("play_count", value = 186234), labels
        ))
        assertEquals("1次播放", formatProviderSearchMetadata(
            ProviderSearchMetadata("play_count", value = 1), labels
        ))
        assertEquals("1.2千次播放", formatProviderSearchMetadata(
            ProviderSearchMetadata("play_count", value = 1200), labels
        ))
    }

    @Test fun creatorAndTextAreDisplayed() {
        assertEquals("abc", formatProviderSearchMetadata(
            ProviderSearchMetadata("creator", text = " abc "), labels
        ))
        assertEquals("官方歌单", formatProviderSearchMetadata(
            ProviderSearchMetadata("text", text = " 官方歌单 "), labels
        ))
    }

    @Test fun orderAndPartialFieldsArePreserved() {
        val metadata = listOf(
            ProviderSearchMetadata("track_count", value = 24),
            ProviderSearchMetadata("play_count", value = 186234),
            ProviderSearchMetadata("creator", text = "abc")
        )
        assertEquals("24首 · 18.6万次播放 · abc", formatProviderSearchMetadataLine(metadata, labels))
        assertEquals("24首 · abc", formatProviderSearchMetadataLine(metadata.filterNot { it.type == "play_count" }, labels))
    }

    @Test fun emptyOrInvalidValuesAreOmitted() {
        assertNull(formatProviderSearchMetadataLine(emptyList(), labels))
        assertNull(formatProviderSearchMetadata(ProviderSearchMetadata("track_count", value = -1), labels))
        assertNull(formatProviderSearchMetadata(ProviderSearchMetadata("creator", text = "  "), labels))
    }

    @Test fun unknownTypeWithTextIsForwardCompatible() {
        assertEquals("官方", formatProviderSearchMetadata(
            ProviderSearchMetadata("soundcloud_special", text = "官方"), labels
        ))
        assertNull(formatProviderSearchMetadata(ProviderSearchMetadata("soundcloud_special"), labels))
    }
}
