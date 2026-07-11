package ink.tenqui.flowtone.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.ui.components.StaggeredPageElement
import kotlin.math.abs

internal data class TopLevelPageHeaderContent(
    val page: TopLevelPage,
    @param:StringRes val titleResId: Int,
    @param:StringRes val subtitleResId: Int
)

internal fun topLevelPageHeaderContent(page: TopLevelPage): TopLevelPageHeaderContent {
    return when (page) {
        TopLevelPage.Home -> TopLevelPageHeaderContent(
            page = page,
            titleResId = R.string.top_level_home_title,
            subtitleResId = R.string.top_level_home_subtitle
        )

        TopLevelPage.Library -> TopLevelPageHeaderContent(
            page = page,
            titleResId = R.string.top_level_library_title,
            subtitleResId = R.string.top_level_library_subtitle
        )

        TopLevelPage.Mine -> TopLevelPageHeaderContent(
            page = page,
            titleResId = R.string.top_level_mine_title,
            subtitleResId = R.string.top_level_mine_subtitle
        )
    }
}

@Composable
internal fun TopLevelSharedPageHeader(
    pagerState: PagerState,
    collapseProgress: TopLevelPageCollapseProgress,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    StaggeredPageElement(
        visible = visible,
        animationIndex = 0,
        modifier = modifier.fillMaxWidth()
    ) {
        val headerContents = remember {
            TopLevelPage.entries.map(::topLevelPageHeaderContent)
        }
        val pagePosition = topLevelHeaderPagePosition(pagerState)
        val transitionDistancePx = with(LocalDensity.current) {
            TopLevelHeaderTransitionDistance.toPx()
        }
        val collapsedTravelYPx = with(LocalDensity.current) {
            TopLevelHeaderCollapsedTravelY.toPx()
        }
        val collapsedTitleScale = topLevelCollapsedTitleScale(
            expandedStyle = MaterialTheme.typography.headlineLarge,
            collapsedStyle = MaterialTheme.typography.titleLarge
        )
        val semanticPage = TopLevelPage.entries[
            pagerState.currentPage.coerceIn(0, TopLevelPage.entries.lastIndex)
        ]

        Box(modifier = Modifier.fillMaxWidth()) {
            headerContents.forEach { content ->
                val distance = content.page.index - pagePosition
                val pagerAlpha = topLevelHeaderAlpha(distance)
                val pagerTranslationX = distance * transitionDistancePx
                val pageCollapseProgress = collapseProgress.progressFor(content.page)
                val semanticsModifier = if (content.page == semanticPage) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics {}
                }
                TopLevelCollapsingPageHeader(
                    title = stringResource(content.titleResId),
                    subtitle = stringResource(content.subtitleResId),
                    pagerAlpha = pagerAlpha,
                    pagerTranslationX = pagerTranslationX,
                    collapseProgress = pageCollapseProgress,
                    collapsedTravelYPx = collapsedTravelYPx,
                    collapsedTitleScale = collapsedTitleScale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(semanticsModifier)
                )
            }
        }
    }
}

@Composable
private fun TopLevelCollapsingPageHeader(
    title: String,
    subtitle: String,
    pagerAlpha: Float,
    pagerTranslationX: Float,
    collapseProgress: Float,
    collapsedTravelYPx: Float,
    collapsedTitleScale: Float,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    translationX = pagerTranslationX
                    translationY = collapsedTravelYPx * collapseProgress
                    val titleScale = 1f + (collapsedTitleScale - 1f) * collapseProgress
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 1f)
                    alpha = pagerAlpha
                }
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = pagerTranslationX
                    alpha = pagerAlpha * topLevelSubtitleAlpha(collapseProgress)
                }
        )
    }
}

private val TopLevelHeaderTransitionDistance = 48.dp

private fun topLevelHeaderPagePosition(pagerState: PagerState): Float {
    return (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(
        minimumValue = 0f,
        maximumValue = TopLevelPage.entries.lastIndex.toFloat()
    )
}

private fun topLevelHeaderAlpha(distance: Float): Float {
    return (1f - abs(distance)).coerceIn(0f, 1f)
}

private fun topLevelSubtitleAlpha(progress: Float): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    val fadeProgress = (
        safeProgress / TopLevelSubtitleFadeEndProgress
        ).coerceIn(0f, 1f)
    return 1f - fadeProgress
}

private fun topLevelCollapsedTitleScale(
    expandedStyle: TextStyle,
    collapsedStyle: TextStyle
): Float {
    val expandedSize = expandedStyle.fontSize.value
    val collapsedSize = collapsedStyle.fontSize.value
    return if (expandedSize.isFinite() && collapsedSize.isFinite() && expandedSize > 0f) {
        (collapsedSize / expandedSize).coerceIn(0.6f, 1f)
    } else {
        TopLevelCollapsedTitleFallbackScale
    }
}
