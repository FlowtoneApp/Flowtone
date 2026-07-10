package ink.tenqui.flowtone.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.ui.components.FlowtonePageHeader
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
    semanticPage: TopLevelPage,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) {
        return
    }

    val headerContents = remember {
        TopLevelPage.entries.map(::topLevelPageHeaderContent)
    }
    val transitionDistancePx = with(LocalDensity.current) {
        TopLevelHeaderTransitionDistance.toPx()
    }

    Box(modifier = modifier.fillMaxWidth()) {
        headerContents.forEach { content ->
            val semanticsModifier = if (content.page == semanticPage) {
                Modifier
            } else {
                Modifier.clearAndSetSemantics {}
            }
            FlowtonePageHeader(
                title = stringResource(content.titleResId),
                subtitle = stringResource(content.subtitleResId),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(semanticsModifier)
                    .graphicsLayer {
                        val pagePosition = topLevelHeaderPagePosition(pagerState)
                        val distance = content.page.index - pagePosition
                        alpha = topLevelHeaderAlpha(distance)
                        translationX = distance * transitionDistancePx
                    }
            )
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
