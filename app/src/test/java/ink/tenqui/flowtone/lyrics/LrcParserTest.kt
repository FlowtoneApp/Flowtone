package ink.tenqui.flowtone.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesStandardTimestamp() {
        assertEquals(listOf(LyricLine(79_780L, "F")), LrcParser.parse("[01:19.78]F"))
    }

    @Test
    fun preservesTenMillisecondIntervals() {
        assertEquals(
            listOf(84_300L, 84_310L, 84_320L),
            LrcParser.parse("[01:24.30]A\n[01:24.31]B\n[01:24.32]C").map { it.timestampMs }
        )
    }

    @Test
    fun supportsDifferentFractionPrecisions() {
        assertEquals(
            listOf(1_100L, 1_120L, 1_123L),
            LrcParser.parse("[00:01.1]A\n[00:01.12]B\n[00:01.123]C").map { it.timestampMs }
        )
    }

    @Test
    fun expandsMultipleTagsOnOneLine() {
        assertEquals(
            listOf(LyricLine(60_000L, "Same"), LyricLine(120_000L, "Same")),
            LrcParser.parse("[01:00.00][02:00.00]Same")
        )
    }

    @Test
    fun keepsEqualTimestampsAndEmptyText() {
        assertEquals(
            listOf(LyricLine(60_000L, "A"), LyricLine(60_000L, "B"), LyricLine(61_000L, "")),
            LrcParser.parse("[01:00.00]A\n[01:00.00]B\n[01:01.00]")
        )
    }

    @Test
    fun ignoresMetadataAndInvalidText() {
        assertEquals(
            listOf(LyricLine(60_000L, "Lyric")),
            LrcParser.parse("[ar:Artist]\n[ti:Title]\nhello\n[01:00.00]Lyric")
        )
    }

    @Test
    fun returnsEmptyListForOnlyInvalidContent() {
        assertEquals(emptyList<LyricLine>(), LrcParser.parse("hello\nworld"))
    }

    @Test
    fun sortsUnorderedLines() {
        assertEquals(
            listOf(LyricLine(60_000L, "A"), LyricLine(120_000L, "B")),
            LrcParser.parse("[02:00.00]B\n[01:00.00]A")
        )
    }
}
