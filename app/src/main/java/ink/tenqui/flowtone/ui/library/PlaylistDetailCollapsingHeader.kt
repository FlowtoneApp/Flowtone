package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneCollapsingPageHeader
import ink.tenqui.flowtone.ui.components.FlowtoneHeaderCollapseStartOffset
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderBodyGap
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderSpace
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarChildTitleOffsetY
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarNavigationTitleShift
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.components.flowtoneCollapsedTitleScale
import ink.tenqui.flowtone.ui.components.rememberFlowtoneCollapsedTitleTravelYPx
import ink.tenqui.flowtone.ui.components.rememberFlowtoneLazyHeaderCollapseProgressState

private val PlaylistDetailHeaderItemBottomSpacer = FlowtonePageHeaderBodyGap - 4.dp
private val PlaylistDetailCollapsedTitleTravelX =
    FlowtoneTopBarTitleStartPadding +
        FlowtoneTopBarNavigationTitleShift -
        FlowtonePageHeaderExpandedStartPadding

@Composable
internal fun PlaylistDetailCollapsingHeaderScaffold(
    title: String,
    listState: LazyListState?,
    showContentHeader: Boolean = true,
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val collapseProgressState = if (listState == null) {
        remember { derivedStateOf { 0f } }
    } else {
        rememberFlowtoneLazyHeaderCollapseProgressState(
            listState = listState,
            startOffset = FlowtoneHeaderCollapseStartOffset
        )
    }
    DisposableEffect(collapseProgressState, onCollapseProgressStateChange) {
        onCollapseProgressStateChange(collapseProgressState)
        onDispose {
            onCollapseProgressStateChange(null)
        }
    }
    val collapseProgress by collapseProgressState
    val density = LocalDensity.current
    val collapsedTravelXPx = with(density) { PlaylistDetailCollapsedTitleTravelX.toPx() }
    val expandedTitleStyle = MaterialTheme.typography.headlineLarge
    val collapsedTitleStyle = MaterialTheme.typography.headlineSmall
    val collapsedTitleScale = flowtoneCollapsedTitleScale(
        expandedStyle = expandedTitleStyle,
        collapsedStyle = collapsedTitleStyle
    )
    val collapsedTravelYPx = rememberFlowtoneCollapsedTitleTravelYPx(
        text = title,
        expandedStyle = expandedTitleStyle,
        collapsedStyle = collapsedTitleStyle,
        collapsedTitleScale = collapsedTitleScale,
        collapsedTitleOffsetY = FlowtoneTopBarChildTitleOffsetY
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(modifier = contentModifier.fillMaxSize()) {
            content()
        }
        if (showContentHeader) {
            FlowtoneCollapsingPageHeader(
                title = title,
                subtitle = null,
                reserveSubtitleSpace = true,
                collapseProgress = collapseProgress,
                collapsedTravelYPx = collapsedTravelYPx,
                collapsedTitleScale = collapsedTitleScale,
                collapsedTravelXPx = collapsedTravelXPx,
                modifier = headerModifier
                    .fillMaxWidth()
                    .padding(
                        start = FlowtonePageHeaderExpandedStartPadding,
                        top = FlowtonePageHeaderExpandedTopPadding,
                        end = FlowtonePageHeaderExpandedEndPadding
                    )
            )
        }
    }
}

@Composable
internal fun PlaylistDetailHeaderListItem(
    showContentHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showContentHeader) return
    Column(modifier = modifier.fillMaxWidth()) {
        FlowtonePageHeaderSpace(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(PlaylistDetailHeaderItemBottomSpacer))
    }
}
