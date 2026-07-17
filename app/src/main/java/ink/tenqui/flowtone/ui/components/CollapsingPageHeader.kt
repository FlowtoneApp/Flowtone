package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

internal val FlowtonePageHeaderExpandedStartPadding: Dp = 21.dp
internal val FlowtonePageHeaderExpandedTopPadding: Dp = 48.dp
internal val FlowtonePageHeaderExpandedEndPadding: Dp = 20.dp
internal val FlowtonePageHeaderBodyGap: Dp = 30.dp
internal val FlowtonePageHeaderTextSpacing: Dp = 8.dp
internal val FlowtoneHeaderCollapseStartOffset: Dp = 30.dp
internal val FlowtoneHeaderCollapseScrollDistance: Dp = 96.dp
internal val FlowtoneLazyHeaderItemScrollDistance: Dp = 98.dp
internal const val FlowtoneSubtitleFadeEndProgress = 0.46f
internal const val FlowtoneCollapsedTitleFallbackScale = 0.74f

@Composable
internal fun FlowtoneCollapsingPageHeader(
    title: String,
    subtitle: String?,
    reserveSubtitleSpace: Boolean,
    collapseProgress: Float,
    collapsedTravelYPx: Float,
    collapsedTitleScale: Float,
    modifier: Modifier = Modifier,
    pagerAlpha: Float = 1f,
    pagerTranslationX: Float = 0f,
    collapsedTravelXPx: Float = 0f
) {
    val safeProgress = collapseProgress.coerceIn(0f, 1f)
    Column(
        verticalArrangement = Arrangement.spacedBy(FlowtonePageHeaderTextSpacing),
        modifier = modifier
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = pagerTranslationX + collapsedTravelXPx * safeProgress
                    translationY = collapsedTravelYPx * safeProgress
                    val titleScale = 1f + (collapsedTitleScale - 1f) * safeProgress
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 1f)
                    alpha = pagerAlpha
                }
        )
        when {
            subtitle != null -> Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = pagerTranslationX
                        translationY = collapsedTravelYPx * safeProgress
                        alpha = pagerAlpha * flowtoneSubtitleAlpha(safeProgress)
                    }
            )

            reserveSubtitleSpace -> FlowtoneHeaderTextLineSpace(
                style = MaterialTheme.typography.bodyMedium,
                fallbackHeight = 20.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun FlowtonePageHeaderSpace(
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FlowtonePageHeaderTextSpacing),
        modifier = modifier
    ) {
        FlowtoneHeaderTextLineSpace(
            style = MaterialTheme.typography.headlineLarge,
            fallbackHeight = 40.dp,
            modifier = Modifier.fillMaxWidth()
        )
        FlowtoneHeaderTextLineSpace(
            style = MaterialTheme.typography.bodyMedium,
            fallbackHeight = 20.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun FlowtoneHeaderTextLineSpace(
    style: TextStyle,
    fallbackHeight: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textHeight = remember(style, fallbackHeight, density) {
        val textUnit = when {
            style.lineHeight.isSpecified -> style.lineHeight
            style.fontSize.isSpecified -> style.fontSize
            else -> null
        }
        textUnit?.let { with(density) { it.toDp() } } ?: fallbackHeight
    }
    Spacer(modifier = modifier.height(textHeight))
}

@Composable
internal fun rememberFlowtoneScrollHeaderCollapseProgress(
    scrollState: ScrollState,
    startOffset: Dp,
    distance: Dp = FlowtoneHeaderCollapseScrollDistance
): Float {
    val density = LocalDensity.current
    val startOffsetPx = with(density) { startOffset.toPx() }
    val distancePx = with(density) { distance.toPx() }
    val progress by remember(scrollState, startOffsetPx, distancePx) {
        derivedStateOf {
            flowtoneHeaderCollapseProgress(
                scrollOffsetPx = scrollState.value.toFloat(),
                startOffsetPx = startOffsetPx,
                distancePx = distancePx
            )
        }
    }
    return progress
}

@Composable
internal fun rememberFlowtoneLazyHeaderCollapseProgress(
    listState: LazyListState,
    startOffset: Dp,
    headerItemScrollDistance: Dp = FlowtoneLazyHeaderItemScrollDistance,
    distance: Dp = FlowtoneHeaderCollapseScrollDistance
): Float = rememberFlowtoneLazyHeaderCollapseProgressState(
    listState = listState,
    startOffset = startOffset,
    headerItemScrollDistance = headerItemScrollDistance,
    distance = distance
).value

@Composable
internal fun rememberFlowtoneLazyHeaderCollapseProgressState(
    listState: LazyListState,
    startOffset: Dp,
    headerItemScrollDistance: Dp = FlowtoneLazyHeaderItemScrollDistance,
    distance: Dp = FlowtoneHeaderCollapseScrollDistance
): State<Float> {
    val density = LocalDensity.current
    val startOffsetPx = with(density) { startOffset.toPx() }
    val headerItemScrollDistancePx = with(density) { headerItemScrollDistance.toPx() }
    val distancePx = with(density) { distance.toPx() }
    return remember(
        listState,
        startOffsetPx,
        headerItemScrollDistancePx,
        distancePx
    ) {
        derivedStateOf {
            val scrollOffsetPx = if (listState.firstVisibleItemIndex > 0) {
                headerItemScrollDistancePx +
                    listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                listState.firstVisibleItemScrollOffset.toFloat()
            }
            flowtoneHeaderCollapseProgress(
                scrollOffsetPx = scrollOffsetPx,
                startOffsetPx = startOffsetPx,
                distancePx = distancePx
            )
        }
    }
}

@Composable
internal fun rememberFlowtoneCollapsedTitleTravelYPx(
    text: String,
    expandedStyle: TextStyle,
    collapsedStyle: TextStyle,
    collapsedTitleScale: Float,
    collapsedTitleOffsetY: Dp
): Float {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(
        text,
        expandedStyle,
        collapsedStyle,
        collapsedTitleScale,
        collapsedTitleOffsetY,
        density,
        textMeasurer
    ) {
        val measuredExpanded = textMeasurer.measure(
            text = text,
            style = expandedStyle.copy(fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        val measuredCollapsed = textMeasurer.measure(
            text = text,
            style = collapsedStyle.copy(fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        val expandedHeightPx = measuredExpanded.size.height.toFloat()
        val collapsedHeightPx = measuredCollapsed.size.height.toFloat()
        val scaledExpandedBaselinePx = expandedHeightPx +
            (measuredExpanded.firstBaseline - expandedHeightPx) * collapsedTitleScale
        val topBarHeightPx = with(density) { FlowtoneTopBarContentHeight.toPx() }
        val collapsedOffsetYPx = with(density) { collapsedTitleOffsetY.toPx() }
        val targetBaselineFromContentTopPx = -topBarHeightPx +
            (topBarHeightPx - collapsedHeightPx) / 2f +
            collapsedOffsetYPx +
            measuredCollapsed.firstBaseline
        val expandedBaselineFromContentTopPx =
            with(density) { FlowtonePageHeaderExpandedTopPadding.toPx() } +
                scaledExpandedBaselinePx
        targetBaselineFromContentTopPx - expandedBaselineFromContentTopPx
    }
}

internal fun flowtoneCollapsedTitleScale(
    expandedStyle: TextStyle,
    collapsedStyle: TextStyle
): Float {
    val expandedSize = expandedStyle.fontSize.value
    val collapsedSize = collapsedStyle.fontSize.value
    return if (expandedSize.isFinite() && collapsedSize.isFinite() && expandedSize > 0f) {
        (collapsedSize / expandedSize).coerceIn(0.6f, 1f)
    } else {
        FlowtoneCollapsedTitleFallbackScale
    }
}

internal fun flowtoneSubtitleAlpha(progress: Float): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    val fadeProgress = (safeProgress / FlowtoneSubtitleFadeEndProgress).coerceIn(0f, 1f)
    return 1f - fadeProgress
}

internal fun flowtoneCollapsingTopBarBackgroundAlpha(progress: Float): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    return ((safeProgress - 0.12f) / 0.88f).coerceIn(0f, 1f)
}

internal fun flowtoneHeaderCollapseProgress(
    scrollOffsetPx: Float,
    startOffsetPx: Float,
    distancePx: Float
): Float {
    if (distancePx <= 0f) return 0f
    return ((scrollOffsetPx - startOffsetPx) / distancePx).coerceIn(0f, 1f)
}
