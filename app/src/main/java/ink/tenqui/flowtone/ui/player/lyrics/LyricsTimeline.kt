package ink.tenqui.flowtone.ui.player.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import ink.tenqui.flowtone.lyrics.LyricLine
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal const val LyricsReturnToCurrentLineDelayMs = 3_000L
internal const val LyricsActiveLineScreenYFraction = 0.312f
internal const val LyricsLineTransitionDurationMs = 700
internal val LyricsLineTransitionEasing = CubicBezierEasing(
    a = 0.2f,
    b = 0f,
    c = 0f,
    d = 1f
)

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
    if (lines.isEmpty()) return null
    val activeTimestampMs = activeLyricTimestampMs(lines, playbackPositionMs)
        ?: return 0
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

internal fun lazyListInitialScrollOffsetForTarget(
    targetYPx: Int,
    viewportStartOffset: Int,
    targetItemSizePx: Int
): Int = -(targetYPx + viewportStartOffset) + targetItemSizePx / 2

internal suspend fun LazyListState.animateScrollToItemAtY(
    index: Int,
    targetYPx: Int,
    targetItemSizePx: Int
) {
    snapshotFlow { layoutInfo.totalItemsCount }.first { itemCount ->
        index in 0 until itemCount
    }

    val viewportHeight = layoutInfo.viewportSize.height
    if (viewportHeight <= 0) return
    val safeTargetYPx = targetYPx.coerceIn(0, viewportHeight)

    val targetWasVisible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!targetWasVisible) {
        // 提前使用目标歌词的真实排版高度计算中心点。远距离 seek 只执行这一次滚动，
        // 不再先定位顶部、再按半行高进行第二次校正。
        val initialScrollOffset = lazyListInitialScrollOffsetForTarget(
            targetYPx = safeTargetYPx,
            viewportStartOffset = layoutInfo.viewportStartOffset,
            targetItemSizePx = targetItemSizePx
        )
        animateScrollToItemWithLyricsTransition(
            index = index,
            scrollOffset = initialScrollOffset
        )
        return
    }

    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return
    val targetCenter = lazyListItemCenterInViewport(
        itemOffset = targetItem.offset,
        itemSize = targetItem.size,
        viewportStartOffset = layoutInfo.viewportStartOffset
    )
    // LazyListItemInfo.offset 与 viewportStartOffset 使用同一列表坐标系。
    val distanceToTarget = targetCenter - safeTargetYPx
    if (abs(distanceToTarget) > LyricsAnchorTolerancePx) {
        animateScrollBy(
            value = distanceToTarget.toFloat(),
            animationSpec = tween(
                durationMillis = LyricsLineTransitionDurationMs,
                easing = LyricsLineTransitionEasing
            )
        )
    }
}

private suspend fun LazyListState.animateScrollToItemWithLyricsTransition(
    index: Int,
    scrollOffset: Int
) {
    scroll {
        val lazyScrollScope = LazyLayoutScrollScope(
            state = this@animateScrollToItemWithLyricsTransition,
            scrollScope = this
        )
        var previousProgress = 0f

        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LyricsLineTransitionDurationMs,
                easing = LyricsLineTransitionEasing
            )
        ) { progress, _ ->
            val remainingProgress = 1f - previousProgress
            if (remainingProgress > 0f) {
                val progressStep = (progress - previousProgress) / remainingProgress
                val remainingDistance = lazyScrollScope.calculateDistanceTo(
                    targetIndex = index,
                    targetOffset = scrollOffset
                )
                lazyScrollScope.scrollBy(remainingDistance * progressStep)
            }
            previousProgress = progress
        }
    }
}

private const val LyricsAnchorTolerancePx = 1
