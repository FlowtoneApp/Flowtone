package ink.tenqui.flowtone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun FlowtonePathTitle(
    pagerState: PagerState,
    rootPage: TopLevelPage,
    segments: List<String>,
    navigationShiftPx: Float,
    modifier: Modifier = Modifier
) {
    val retainedSegments = remember { mutableStateListOf<String>() }
    val levelAnimations = remember {
        mutableStateListOf<Animatable<Float, AnimationVector1D>>()
    }
    LaunchedEffect(segments) {
        segments.forEachIndexed { index, title ->
            if (index < retainedSegments.size) {
                retainedSegments[index] = title
            } else {
                retainedSegments += title
            }
        }
        while (levelAnimations.size < retainedSegments.size) {
            levelAnimations += Animatable(0f)
        }

        withFrameNanos { }
        coroutineScope {
            levelAnimations.forEachIndexed { index, animation ->
                launch {
                    animation.animateTo(
                        targetValue = if (index < segments.size) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = FlowtoneMotion.DurationMillis,
                            easing = FlowtoneMotion.Easing
                        )
                    )
                }
            }
        }
    }
    val levelProgress = levelAnimations.map { it.value }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val rootProgress = levelProgress.getOrElse(0) { 0f }
    val expandedRootStyle = MaterialTheme.typography.titleLarge
    val compactStyle = MaterialTheme.typography.labelLarge
    val expandedChildStyle = MaterialTheme.typography.headlineSmall
    val rootStyle = interpolateTextStyle(expandedRootStyle, compactStyle, rootProgress)
    val slideDistancePx = with(density) { 36.dp.toPx() }
    val titleBaseOffsetYPx = with(density) { -3.dp.toPx() }
    val ancestorOffsetYPx = with(density) { -17.dp.toPx() }
    val rootOpticalOffsetXPx = with(density) { 1.dp.toPx() }
    val childRestingOffsetYPx = with(density) { 3.dp.toPx() }
    val childHiddenOffsetYPx = with(density) { 48.dp.toPx() }
    val pathBaselineCorrectionPx = with(density) { 1.dp.toPx() }
    val pathGapPx = with(density) { 2.dp.toPx() }
    val separatorEnterDistancePx = with(density) { 16.dp.toPx() }
    val ancestorYPx = titleBaseOffsetYPx + ancestorOffsetYPx + pathBaselineCorrectionPx
    val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val rootCompactWidthPx = textMeasurer.measure(
        text = rootPage.title,
        style = compactStyle
    ).size.width.toFloat()
    val rootExpandedWidthPx = textMeasurer.measure(
        text = rootPage.title,
        style = expandedRootStyle
    ).size.width.toFloat()
    val separatorWidthPx = textMeasurer.measure(
        text = "/",
        style = compactStyle
    ).size.width.toFloat()
    val compactSegmentWidths = retainedSegments.map { title ->
        textMeasurer.measure(text = title, style = compactStyle).size.width.toFloat()
    }
    val separatorTargetX = mutableListOf<Float>()
    val segmentAncestorTargetX = mutableListOf<Float>()
    var pathCursorX = navigationShiftPx + rootOpticalOffsetXPx * rootProgress +
        rootCompactWidthPx
    compactSegmentWidths.forEach { width ->
        val separatorX = pathCursorX + pathGapPx
        val segmentX = separatorX + separatorWidthPx + pathGapPx
        separatorTargetX += separatorX
        segmentAncestorTargetX += segmentX
        pathCursorX = segmentX + width
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart
    ) {
        TopLevelPage.entries.forEach { page ->
            val distance = page.index - pagePosition
            val isRoot = page == rootPage
            val pageAlpha = (1f - abs(distance)).coerceIn(0f, 1f)
            val rootTitleAnimating = isRoot && rootProgress > 0.001f && rootProgress < 0.999f
            Text(
                text = page.title,
                maxLines = 1,
                overflow = if (rootTitleAnimating) {
                    TextOverflow.Clip
                } else {
                    TextOverflow.Ellipsis
                },
                style = if (isRoot) rootStyle else expandedRootStyle,
                fontWeight = FontWeight.Medium,
                color = if (isRoot) {
                    lerp(
                        MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        rootProgress
                    )
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                    translationX = distance * slideDistancePx +
                        if (isRoot) {
                            navigationShiftPx + rootOpticalOffsetXPx * rootProgress
                        } else {
                            0f
                        }
                    translationY = titleBaseOffsetYPx +
                        if (isRoot) ancestorOffsetYPx * rootProgress else 0f
                    alpha = pageAlpha * if (isRoot) 1f else 1f - rootProgress
                }
            )
        }

        retainedSegments.forEachIndexed { index, title ->
            val ownProgress = levelProgress.getOrElse(index) { 0f }
            val promotionProgress = levelProgress.getOrElse(index + 1) { 0f }
            val entering = index < segments.size
            val childOffsetY = childHiddenOffsetYPx +
                (childRestingOffsetYPx - childHiddenOffsetYPx) * ownProgress
            val segmentStyle = interpolateTextStyle(
                expandedChildStyle,
                compactStyle,
                promotionProgress
            )
            val segmentAnimating =
                ownProgress > 0.001f && ownProgress < 0.999f ||
                    promotionProgress > 0.001f && promotionProgress < 0.999f
            val parentBoundSeparatorX: Float
            val parentBoundSeparatorY: Float
            if (index == 0) {
                val parentWidth = rootExpandedWidthPx +
                    (rootCompactWidthPx - rootExpandedWidthPx) * rootProgress
                parentBoundSeparatorX = navigationShiftPx +
                    rootOpticalOffsetXPx * rootProgress +
                    parentWidth +
                    pathGapPx
                parentBoundSeparatorY = titleBaseOffsetYPx +
                    ancestorOffsetYPx * rootProgress +
                    pathBaselineCorrectionPx
            } else {
                val parentOwnProgress = levelProgress.getOrElse(index - 1) { 0f }
                val parentExpandedWidth = textMeasurer.measure(
                    text = retainedSegments[index - 1],
                    style = expandedChildStyle
                ).size.width.toFloat()
                val parentCompactWidth = compactSegmentWidths[index - 1]
                val parentWidth = parentExpandedWidth +
                    (parentCompactWidth - parentExpandedWidth) * ownProgress
                val parentX = navigationShiftPx +
                    (segmentAncestorTargetX[index - 1] - navigationShiftPx) * ownProgress
                val parentChildOffsetY = childHiddenOffsetYPx +
                    (childRestingOffsetYPx - childHiddenOffsetYPx) * parentOwnProgress
                val parentY = parentChildOffsetY +
                    (ancestorYPx - childRestingOffsetYPx) * ownProgress
                parentBoundSeparatorX = parentX + parentWidth + pathGapPx
                parentBoundSeparatorY = parentY
            }
            val separatorX = if (entering) {
                parentBoundSeparatorX - separatorEnterDistancePx * (1f - ownProgress)
            } else {
                parentBoundSeparatorX
            }
            Text(
                text = "/",
                maxLines = 1,
                style = compactStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    translationX = separatorX
                    translationY = parentBoundSeparatorY
                    alpha = ownProgress
                }
            )
            Text(
                text = title,
                maxLines = 1,
                overflow = if (segmentAnimating) {
                    TextOverflow.Clip
                } else {
                    TextOverflow.Ellipsis
                },
                style = segmentStyle,
                fontWeight = FontWeight.Medium,
                color = lerp(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    promotionProgress
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                    translationX = navigationShiftPx +
                        (segmentAncestorTargetX[index] - navigationShiftPx) *
                        promotionProgress
                    translationY = childOffsetY +
                        (ancestorYPx - childRestingOffsetYPx) * promotionProgress
                    alpha = ownProgress
                }
            )
        }
    }
}

private fun interpolateTextStyle(
    from: TextStyle,
    to: TextStyle,
    progress: Float
): TextStyle {
    return from.copy(
        fontSize = (from.fontSize.value + (to.fontSize.value - from.fontSize.value) * progress).sp,
        lineHeight = (
            from.lineHeight.value + (to.lineHeight.value - from.lineHeight.value) * progress
            ).sp,
        textMotion = TextMotion.Animated
    )
}
