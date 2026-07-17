package ink.tenqui.flowtone.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.ui.components.FlowtoneCollapsingPageHeader
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarRootTitleOffsetY
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.components.StaggeredPageElement
import ink.tenqui.flowtone.ui.components.flowtoneCollapsedTitleScale
import ink.tenqui.flowtone.ui.components.rememberFlowtoneCollapsedTitleTravelYPx
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
        val expandedTitleStyle = MaterialTheme.typography.headlineLarge
        val collapsedTitleStyle = MaterialTheme.typography.titleLarge
        val collapsedTitleScale = topLevelCollapsedTitleScale(
            expandedStyle = expandedTitleStyle,
            collapsedStyle = collapsedTitleStyle
        )
        val collapsedTravelXPx = with(LocalDensity.current) {
            (FlowtoneTopBarTitleStartPadding - FlowtonePageHeaderExpandedStartPadding).toPx()
        }
        val semanticPage = TopLevelPage.entries[
            pagerState.currentPage.coerceIn(0, TopLevelPage.entries.lastIndex)
        ]

        Box(modifier = Modifier.fillMaxWidth()) {
            headerContents.forEach { content ->
                val title = stringResource(content.titleResId)
                val distance = content.page.index - pagePosition
                val pagerAlpha = topLevelHeaderAlpha(distance)
                val pagerTranslationX = distance * transitionDistancePx
                val pageCollapseProgress = collapseProgress.progressFor(content.page)
                val collapsedTravelYPx = rememberFlowtoneCollapsedTitleTravelYPx(
                    text = title,
                    expandedStyle = expandedTitleStyle,
                    collapsedStyle = collapsedTitleStyle,
                    collapsedTitleScale = collapsedTitleScale,
                    collapsedTitleOffsetY = FlowtoneTopBarRootTitleOffsetY
                )
                val semanticsModifier = if (content.page == semanticPage) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics {}
                }
                FlowtoneCollapsingPageHeader(
                    title = title,
                    subtitle = stringResource(content.subtitleResId),
                    reserveSubtitleSpace = false,
                    pagerAlpha = pagerAlpha,
                    pagerTranslationX = pagerTranslationX,
                    collapseProgress = pageCollapseProgress,
                    collapsedTravelYPx = collapsedTravelYPx,
                    collapsedTitleScale = collapsedTitleScale,
                    collapsedTravelXPx = collapsedTravelXPx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(semanticsModifier)
                )
            }
        }
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

private fun topLevelCollapsedTitleScale(
    expandedStyle: androidx.compose.ui.text.TextStyle,
    collapsedStyle: androidx.compose.ui.text.TextStyle
): Float = flowtoneCollapsedTitleScale(
    expandedStyle = expandedStyle,
    collapsedStyle = collapsedStyle
)
