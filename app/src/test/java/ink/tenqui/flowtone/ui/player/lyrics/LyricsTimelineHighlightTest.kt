package ink.tenqui.flowtone.ui.player.lyrics

import ink.tenqui.flowtone.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTimelineHighlightTest {

    @Test
    fun subpixelCompensationFollowsIdealPositionBetweenRoundedListSteps() {
        assertEquals(
            -0.4f,
            lyricTrackingSubpixelOffsetPx(
                initialAbsolutePositionPx = 100f,
                actualAbsolutePositionPx = 100f,
                idealScrollPositionPx = 0.4f
            ),
            0.001f
        )
        assertEquals(
            0.4f,
            lyricTrackingSubpixelOffsetPx(
                initialAbsolutePositionPx = 100f,
                actualAbsolutePositionPx = 101f,
                idealScrollPositionPx = 0.6f
            ),
            0.001f
        )
    }

    @Test
    fun replacementAnimationInheritsAndThenRemovesPreviousCompensation() {
        assertEquals(
            0.4f,
            lyricTrackingSubpixelOffsetPx(
                initialAbsolutePositionPx = 100f,
                actualAbsolutePositionPx = 100f,
                idealScrollPositionPx = 0f,
                initialSubpixelOffsetPx = 0.4f,
                animationProgress = 0f
            ),
            0.001f
        )
        assertEquals(
            0.4f,
            lyricTrackingSubpixelOffsetPx(
                initialAbsolutePositionPx = 100f,
                actualAbsolutePositionPx = 101f,
                idealScrollPositionPx = 0.8f,
                initialSubpixelOffsetPx = 0.4f,
                animationProgress = 0.5f
            ),
            0.001f
        )
        assertEquals(
            0f,
            lyricTrackingSubpixelOffsetPx(
                initialAbsolutePositionPx = 100f,
                actualAbsolutePositionPx = 101f,
                idealScrollPositionPx = 1f,
                initialSubpixelOffsetPx = 0.4f,
                animationProgress = 1f
            ),
            0.001f
        )
    }

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

    @Test
    fun fixedForwardSeekUsesMeasuredHeightsOnlyOnce() {
        assertEquals(
            900f,
            fixedScrollDistanceToItem(
                itemHeightsPx = intArrayOf(100, 200, 300, 400),
                anchorIndex = 1,
                anchorOffset = 500,
                anchorSize = 200,
                targetIndex = 3,
                targetYPx = 400,
                viewportStartOffset = -100
            )
        )
    }

    @Test
    fun fixedBackwardSeekUsesMeasuredHeightsOnlyOnce() {
        assertEquals(
            -300f,
            fixedScrollDistanceToItem(
                itemHeightsPx = intArrayOf(100, 200, 300, 400),
                anchorIndex = 2,
                anchorOffset = 200,
                anchorSize = 300,
                targetIndex = 0,
                targetYPx = 300,
                viewportStartOffset = -50
            )
        )
    }

    @Test
    fun fixedSeekRejectsAnInvalidTargetIndex() {
        assertNull(
            fixedScrollDistanceToItem(
                itemHeightsPx = intArrayOf(100, 200),
                anchorIndex = 0,
                anchorOffset = 0,
                anchorSize = 100,
                targetIndex = 2,
                targetYPx = 100,
                viewportStartOffset = 0
            )
        )
    }

    @Test
    fun shortLyricUsesTimeUntilNextLineAsVisualTransitionDuration() {
        val shortLines = listOf(
            LyricLine(timestampMs = 1_000L, text = "短促歌词"),
            LyricLine(timestampMs = 1_010L, text = "下一句")
        )

        assertEquals(10, lyricVisualTransitionDurationMs(shortLines, lineIndex = 0))
    }

    @Test
    fun duplicateTimestampsShareDurationToNextDistinctTimestamp() {
        val translatedLines = listOf(
            LyricLine(timestampMs = 1_000L, text = "歌词"),
            LyricLine(timestampMs = 1_000L, text = "翻译"),
            LyricLine(timestampMs = 1_180L, text = "下一句")
        )

        assertEquals(180, lyricVisualTransitionDurationMs(translatedLines, lineIndex = 0))
        assertEquals(180, lyricVisualTransitionDurationMs(translatedLines, lineIndex = 1))
    }

    @Test
    fun normalAndFinalLyricsKeepDefaultVisualTransitionDuration() {
        assertEquals(LyricsLineTransitionDurationMs, lyricVisualTransitionDurationMs(lines, 0))
        assertEquals(LyricsLineTransitionDurationMs, lyricVisualTransitionDurationMs(lines, 3))
    }

    @Test
    fun trackingSnapsOnlyBelowThreeHundredMilliseconds() {
        assertEquals(true, shouldInstantlyTrackLyric(299))
        assertEquals(false, shouldInstantlyTrackLyric(300))
    }

    @Test
    fun shortBlankPlaceholderKeepsNormalTrackingDuration() {
        val linesWithShortBlank = listOf(
            LyricLine(timestampMs = 1_000L, text = "上一句"),
            LyricLine(timestampMs = 2_000L, text = ""),
            LyricLine(timestampMs = 2_010L, text = "下一句")
        )

        assertEquals(
            LyricsLineTransitionDurationMs,
            lyricTrackingTransitionDurationMs(linesWithShortBlank, lineIndex = 1)
        )
    }

    @Test
    fun realShortLyricStillUsesInstantTrackingDuration() {
        val extremeLines = listOf(
            LyricLine(timestampMs = 1_000L, text = "短句"),
            LyricLine(timestampMs = 1_010L, text = "下一句")
        )

        assertEquals(10, lyricTrackingTransitionDurationMs(extremeLines, lineIndex = 0))
    }

    @Test
    fun timestampGroupWithTextIsNotMistakenForBlankPlaceholder() {
        val groupedLines = listOf(
            LyricLine(timestampMs = 1_000L, text = ""),
            LyricLine(timestampMs = 1_000L, text = "实际歌词"),
            LyricLine(timestampMs = 1_120L, text = "下一句")
        )

        assertEquals(120, lyricTrackingTransitionDurationMs(groupedLines, lineIndex = 0))
    }

}
