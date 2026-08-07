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
    fun combinesTheSecondLineAtTheSameTimestampAsTranslation() {
        assertEquals(
            listOf(
                LyricLine(60_000L, "A", translation = "B"),
                LyricLine(61_000L, "")
            ),
            LrcParser.parse("[01:00.00]A\n[01:00.00]B\n[01:01.00]")
        )
    }

    @Test
    fun usesOnlyTheFirstTwoTextLinesAtTheSameTimestamp() {
        assertEquals(
            listOf(LyricLine(60_000L, "Original", translation = "Translated")),
            LrcParser.parse(
                "[01:00.00]Original\n" +
                    "[01:00.00]Translated\n" +
                    "[01:00.00]Extra annotation"
            )
        )
    }

    @Test
    fun removesLeadingWhitespaceAndInvisibleCharacters() {
        assertEquals(
            listOf(
                LyricLine(1_000L, "First  line"),
                LyricLine(2_000L, "Second line"),
                LyricLine(3_000L, "Third line"),
                LyricLine(4_000L, "")
            ),
            LrcParser.parse(
                "[00:01.00]   First  line\n" +
                    "[00:02.00]\u3000Second line\n" +
                    "[00:03.00]\uFEFF\u200BThird line\n" +
                    "[00:04.00]   "
            )
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
