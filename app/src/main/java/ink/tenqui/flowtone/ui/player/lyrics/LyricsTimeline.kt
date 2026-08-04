package ink.tenqui.flowtone.ui.player.lyrics

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import ink.tenqui.flowtone.lyrics.LyricLine
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal const val LyricsReturnToCurrentLineDelayMs = 3_000L
internal const val LyricsActiveLineScreenYFraction = 0.312f

internal fun activeLyricTimestampMs(
    lines: List<LyricLine>,
    playbackPositionMs: Long
): Long? = lines.lastOrNull { line ->
    line.timestampMs <= playbackPositionMs
}?.timestampMs

internal fun activeLyricAnchorIndex(
    lines: List<LyricLine>,
    playbackPositionMs: Long
): Int? {
    val activeTimestampMs = activeLyricTimestampMs(lines, playbackPositionMs)
        ?: return null
    val firstIndex = lines.indexOfFirst { it.timestampMs == activeTimestampMs }
    val lastIndex = lines.indexOfLast { it.timestampMs == activeTimestampMs }
    return if (firstIndex >= 0 && lastIndex >= 0) {
        (firstIndex + lastIndex) / 2
    } else {
        null
    }
}

internal fun lazyListItemCenterInViewport(
    itemOffset: Int,
    itemSize: Int,
    viewportStartOffset: Int
): Int = itemOffset + itemSize / 2 - viewportStartOffset

internal suspend fun LazyListState.animateScrollToItemAtY(
    index: Int,
    targetYPx: Int
) {
    snapshotFlow { layoutInfo.totalItemsCount }.first { itemCount ->
        index in 0 until itemCount
    }

    val viewportHeight = layoutInfo.viewportSize.height
    if (viewportHeight <= 0) return
    val safeTargetYPx = targetYPx.coerceIn(0, viewportHeight)

    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        animateScrollToItem(
            index = index,
            scrollOffset = -safeTargetYPx
        )
    }

    repeat(LyricsAnchorCorrectionPasses) {
        val targetItem = snapshotFlow {
            layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }.first { item -> item != null } ?: return
        val targetCenter = lazyListItemCenterInViewport(
            itemOffset = targetItem.offset,
            itemSize = targetItem.size,
            viewportStartOffset = layoutInfo.viewportStartOffset
        )
        // LazyListItemInfo.offset 与 viewportStartOffset 使用同一列表坐标系。
        // 顶部 contentPadding 会让 viewportStartOffset 为负值，因此屏幕内可见坐标是
        // itemCenter - viewportStartOffset，不能直接拿 itemCenter 与目标 Y 比较。
        val distanceToTarget = targetCenter - safeTargetYPx
        if (abs(distanceToTarget) <= LyricsAnchorTolerancePx) {
            return
        }
        animateScrollBy(distanceToTarget.toFloat())
    }
}

private const val LyricsAnchorCorrectionPasses = 3
private const val LyricsAnchorTolerancePx = 1
