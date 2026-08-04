package ink.tenqui.flowtone.ui.player.lyrics

import ink.tenqui.flowtone.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTimelineHighlightTest {
    private val lines = listOf(
        LyricLine(timestampMs = 1_000L, text = "第一句"),
        LyricLine(timestampMs = 2_500L, text = "第二句"),
        LyricLine(timestampMs = 2_500L, text = "第二句翻译"),
        LyricLine(timestampMs = 4_000L, text = "第三句")
    )

    @Test
    fun noLineIsActiveBeforeFirstTimestamp() {
        assertNull(activeLyricTimestampMs(lines, playbackPositionMs = 999L))
    }

    @Test
    fun firstLineIsTheAnchorButNotActiveBeforeLyricsStart() {
        assertEquals(0, activeLyricAnchorIndex(lines, playbackPositionMs = 999L))
        assertNull(activeLyricTimestampMs(lines, playbackPositionMs = 999L))
    }

    @Test
    fun emptyLyricsHaveNoDefaultAnchor() {
        assertNull(activeLyricAnchorIndex(emptyList(), playbackPositionMs = 0L))
    }

    @Test
    fun currentTimestampRemainsActiveUntilNextTimestamp() {
        assertEquals(1_000L, activeLyricTimestampMs(lines, playbackPositionMs = 2_499L))
        assertEquals(2_500L, activeLyricTimestampMs(lines, playbackPositionMs = 3_999L))
    }

    @Test
    fun duplicateTimestampLinesShareTheActiveTimestamp() {
        assertEquals(2_500L, activeLyricTimestampMs(lines, playbackPositionMs = 2_500L))
    }

    @Test
    fun seekingBackwardRecomputesTheActiveTimestamp() {
        assertEquals(4_000L, activeLyricTimestampMs(lines, playbackPositionMs = 8_000L))
        assertEquals(1_000L, activeLyricTimestampMs(lines, playbackPositionMs = 1_200L))
    }

    @Test
    fun emptyLineKeepsItsOwnCenterAnchor() {
        val linesWithEmptyAnchor = listOf(
            LyricLine(timestampMs = 1_000L, text = "上一句"),
            LyricLine(timestampMs = 2_000L, text = ""),
            LyricLine(timestampMs = 3_000L, text = "下一句")
        )

        assertEquals(
            1,
            activeLyricAnchorIndex(
                lines = linesWithEmptyAnchor,
                playbackPositionMs = 2_500L
            )
        )
    }

    @Test
    fun topContentPaddingIsIncludedInTheVisibleItemPosition() {
        assertEquals(
            802,
            lazyListItemCenterInViewport(
                itemOffset = 356,
                itemSize = 90,
                viewportStartOffset = -401
            )
        )
    }

    @Test
    fun farSeekInitialOffsetIncludesHalfOfTheMeasuredLineHeight() {
        assertEquals(
            45,
            lazyListInitialScrollOffsetForTarget(
                targetYPx = 401,
                viewportStartOffset = -401,
                targetItemSizePx = 90
            )
        )
    }

}
