package ink.tenqui.flowtone.ui.player.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import ink.tenqui.flowtone.lyrics.LyricLine
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal const val LyricsReturnToCurrentLineDelayMs = 3_000L
internal const val LyricsActiveLineScreenYFraction = 0.312f
internal const val LyricsLineTransitionDurationMs = 700
internal const val LyricsInstantTrackingThresholdMs = 300
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

/**
 * 短促歌词应在下一句开始前完成视觉过渡；普通歌词继续使用默认动画时长。
 * 相同时间戳的多行歌词属于同一组，因此查找下一条不同的时间戳。
 */
internal fun lyricVisualTransitionDurationMs(
    lines: List<LyricLine>,
    lineIndex: Int
): Int {
    val currentTimestampMs = lines.getOrNull(lineIndex)?.timestampMs
        ?: return LyricsLineTransitionDurationMs
    val nextTimestampMs = lines
        .asSequence()
        .drop(lineIndex + 1)
        .map(LyricLine::timestampMs)
        .firstOrNull { timestampMs -> timestampMs > currentTimestampMs }
        ?: return LyricsLineTransitionDurationMs
    val timeUntilNextLineMs = nextTimestampMs - currentTimestampMs

    return timeUntilNextLineMs
        .coerceAtMost(LyricsLineTransitionDurationMs.toLong())
        .toInt()
}

/**
 * 纯空行只是歌词间的占位，空行本身以及空行后的第一句都不应触发瞬时定位。
 * 同一时间戳只要存在一行实际文本，就不视为空行组。
 */
internal fun lyricTrackingTransitionDurationMs(
    lines: List<LyricLine>,
    lineIndex: Int
): Int {
    val timestampMs = lines.getOrNull(lineIndex)?.timestampMs
        ?: return LyricsLineTransitionDurationMs
    val timestampHasLyricText = lines.any { line ->
        line.timestampMs == timestampMs && line.text.isNotBlank()
    }
    if (!timestampHasLyricText) return LyricsLineTransitionDurationMs

    val previousTimestampMs = lines
        .asSequence()
        .map(LyricLine::timestampMs)
        .filter { candidateTimestampMs -> candidateTimestampMs < timestampMs }
        .maxOrNull()
    val followsBlankTimestamp = previousTimestampMs != null && lines.none { line ->
        line.timestampMs == previousTimestampMs && line.text.isNotBlank()
    }
    if (followsBlankTimestamp) return LyricsLineTransitionDurationMs

    return lyricVisualTransitionDurationMs(lines, lineIndex)
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

internal fun shouldInstantlyTrackLyric(transitionDurationMs: Int): Boolean =
    transitionDurationMs < LyricsInstantTrackingThresholdMs

internal suspend fun LazyListState.animateScrollToItemAtY(
    index: Int,
    targetYPx: Int,
    targetItemSizePx: Int,
    itemHeightsPx: IntArray,
    itemStartOffsetsPx: IntArray,
    initialSubpixelOffsetPx: Float = 0f,
    onSubpixelOffsetChanged: (Float) -> Unit = {},
    transitionDurationMs: Int = LyricsLineTransitionDurationMs
) {
    snapshotFlow {
        val currentLayout = layoutInfo
        index in 0 until currentLayout.totalItemsCount &&
            currentLayout.viewportSize.height > 0 &&
            currentLayout.visibleItemsInfo.isNotEmpty()
    }.first { layoutIsReady ->
        layoutIsReady
    }

    val viewportHeight = layoutInfo.viewportSize.height
    if (viewportHeight <= 0) return
    val safeTargetYPx = targetYPx.coerceIn(0, viewportHeight)

    val targetWasVisible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!targetWasVisible) {
        val visibleLyrics = layoutInfo.visibleItemsInfo
            .filter { item -> item.index in itemHeightsPx.indices }
        val firstVisible = visibleLyrics.minByOrNull { item -> item.index }
        val lastVisible = visibleLyrics.maxByOrNull { item -> item.index }
        val anchorItem = when {
            firstVisible == null || lastVisible == null -> null
            index < firstVisible.index -> firstVisible
            index > lastVisible.index -> lastVisible
            else -> visibleLyrics.minByOrNull { item -> abs(item.index - index) }
        }
        val distanceToTarget = anchorItem?.let { anchor ->
            fixedScrollDistanceToItem(
                itemHeightsPx = itemHeightsPx,
                anchorIndex = anchor.index,
                anchorOffset = anchor.offset,
                anchorSize = anchor.size,
                targetIndex = index,
                targetYPx = safeTargetYPx,
                viewportStartOffset = layoutInfo.viewportStartOffset
            )
        }
        if (distanceToTarget == null) {
            val initialScrollOffset = lazyListInitialScrollOffsetForTarget(
                targetYPx = safeTargetYPx,
                viewportStartOffset = layoutInfo.viewportStartOffset,
                targetItemSizePx = targetItemSizePx
            )
            scrollToItem(index = index, scrollOffset = initialScrollOffset)
            onSubpixelOffsetChanged(0f)
        } else if (shouldInstantlyTrackLyric(transitionDurationMs)) {
            scrollBy(distanceToTarget)
            onSubpixelOffsetChanged(0f)
        } else {
            animateScrollByWithSubpixelCompensation(
                value = distanceToTarget,
                itemStartOffsetsPx = itemStartOffsetsPx,
                initialSubpixelOffsetPx = initialSubpixelOffsetPx,
                transitionDurationMs = transitionDurationMs,
                onSubpixelOffsetChanged = onSubpixelOffsetChanged
            )
        }
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
        if (shouldInstantlyTrackLyric(transitionDurationMs)) {
            scrollBy(distanceToTarget.toFloat())
            onSubpixelOffsetChanged(0f)
        } else {
            animateScrollByWithSubpixelCompensation(
                value = distanceToTarget.toFloat(),
                itemStartOffsetsPx = itemStartOffsetsPx,
                initialSubpixelOffsetPx = initialSubpixelOffsetPx,
                transitionDurationMs = transitionDurationMs,
                onSubpixelOffsetChanged = onSubpixelOffsetChanged
            )
        }
    } else {
        onSubpixelOffsetChanged(0f)
    }
}

private suspend fun LazyListState.animateScrollByWithSubpixelCompensation(
    value: Float,
    itemStartOffsetsPx: IntArray,
    initialSubpixelOffsetPx: Float,
    transitionDurationMs: Int,
    onSubpixelOffsetChanged: (Float) -> Unit
) {
    val initialAbsolutePosition = absoluteLazyListScrollPositionPx(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        itemStartOffsetsPx = itemStartOffsetsPx
    )
    if (initialAbsolutePosition == null) {
        onSubpixelOffsetChanged(0f)
        animateScrollBy(
            value = value,
            animationSpec = tween(
                durationMillis = transitionDurationMs,
                easing = LyricsLineTransitionEasing
            )
        )
        return
    }

    var consumedValue = 0f
    scroll {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = transitionDurationMs,
                easing = LyricsLineTransitionEasing
            )
        ) { animationProgress, _ ->
            val idealValue = value * animationProgress
            consumedValue += scrollBy(idealValue - consumedValue)
            val actualAbsolutePosition = absoluteLazyListScrollPositionPx(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                itemStartOffsetsPx = itemStartOffsetsPx
            )
            if (actualAbsolutePosition != null) {
                onSubpixelOffsetChanged(
                    lyricTrackingSubpixelOffsetPx(
                        initialAbsolutePositionPx = initialAbsolutePosition,
                        actualAbsolutePositionPx = actualAbsolutePosition,
                        idealScrollPositionPx = idealValue,
                        initialSubpixelOffsetPx = initialSubpixelOffsetPx,
                        animationProgress = animationProgress
                    )
                )
            }
        }
    }
    // 正常结束时回到整数目标；取消时保留当前值，由下一条动画连续接管。
    onSubpixelOffsetChanged(0f)
}

internal fun absoluteLazyListScrollPositionPx(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    itemStartOffsetsPx: IntArray
): Float? = itemStartOffsetsPx
    .getOrNull(firstVisibleItemIndex)
    ?.plus(firstVisibleItemScrollOffset)
    ?.toFloat()

internal fun lyricTrackingSubpixelOffsetPx(
    initialAbsolutePositionPx: Float,
    actualAbsolutePositionPx: Float,
    idealScrollPositionPx: Float,
    initialSubpixelOffsetPx: Float = 0f,
    animationProgress: Float = 1f
): Float = (
    initialSubpixelOffsetPx * (1f - animationProgress.coerceIn(0f, 1f)) +
        actualAbsolutePositionPx - initialAbsolutePositionPx - idealScrollPositionPx
    ).coerceIn(-LyricsMaxSubpixelCompensationPx, LyricsMaxSubpixelCompensationPx)

internal fun fixedScrollDistanceToItem(
    itemHeightsPx: IntArray,
    anchorIndex: Int,
    anchorOffset: Int,
    anchorSize: Int,
    targetIndex: Int,
    targetYPx: Int,
    viewportStartOffset: Int
): Float? {
    if (
        anchorIndex !in itemHeightsPx.indices ||
        targetIndex !in itemHeightsPx.indices
    ) {
        return null
    }

    var targetCenter = lazyListItemCenterInViewport(
        itemOffset = anchorOffset,
        itemSize = anchorSize,
        viewportStartOffset = viewportStartOffset
    ).toFloat()
    when {
        targetIndex > anchorIndex -> {
            targetCenter += anchorSize / 2f
            for (itemIndex in (anchorIndex + 1) until targetIndex) {
                targetCenter += itemHeightsPx[itemIndex]
            }
            targetCenter += itemHeightsPx[targetIndex] / 2f
        }
        targetIndex < anchorIndex -> {
            targetCenter -= anchorSize / 2f
            for (itemIndex in (targetIndex + 1) until anchorIndex) {
                targetCenter -= itemHeightsPx[itemIndex]
            }
            targetCenter -= itemHeightsPx[targetIndex] / 2f
        }
    }
    return targetCenter - targetYPx
}

private const val LyricsAnchorTolerancePx = 1
private const val LyricsMaxSubpixelCompensationPx = 0.5f
