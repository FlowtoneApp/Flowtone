package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
internal fun SharedSongInfo(
    title: String,
    artist: String,
    progress: Float,
    titleColor: Color,
    artistColor: Color,
    playerWidth: Dp,
    collapsedHeight: Dp,
    expandedTop: Dp,
    switchDirection: Int,
    modifier: Modifier = Modifier
) {
    val metadataGroupHeight = 60.dp
    val collapsedCenterY = collapsedHeight / 2f
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleMedium
    val artistStyle = MaterialTheme.typography.bodyMedium
    val metadataLineHorizontalPadding = 6.dp
    val minMetadataLineWidth = 48.dp
    val collapsedViewportX = 30.dp
    val collapsedControlsReservedWidth = 48.dp * 3f + 8.dp * 2f + 30.dp
    val collapsedViewportWidth = playerWidth - collapsedViewportX - collapsedControlsReservedWidth
    val collapsedViewportY = collapsedCenterY - metadataGroupHeight / 2f
    val expandedViewportWidth = playerWidth * 0.82f
    val expandedViewportX = (playerWidth - expandedViewportWidth) / 2f
    val expandedViewportCenterX = expandedViewportX + expandedViewportWidth / 2f
    val expandedViewportY = expandedTop
    val viewportX = lerpDp(collapsedViewportX, expandedViewportX, progress)
    val viewportY = lerpDp(collapsedViewportY, expandedViewportY, progress)
    val viewportWidth = lerpDp(collapsedViewportWidth, expandedViewportWidth, progress)
    val lineHorizontalPadding = lerpDp(0.dp, metadataLineHorizontalPadding, progress)
    val metadataTextAlign = TextAlign.Start
    val metadataState = remember(title, artist) {
        CollapsedMetadataState(title = title, artist = artist)
    }
    val shouldAnimateCollapsedMetadataChange = progress <= 0.01f
    val metadataSwitchDistancePx = with(density) { 20.dp.roundToPx() }

    @Composable
    fun MetadataTextBlock(blockTitle: String, blockArtist: String) {
        val titleWidth = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(blockTitle),
                style = titleStyle,
                maxLines = 1
            ).size.width.toDp()
        }
        val artistWidth = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(blockArtist),
                style = artistStyle,
                maxLines = 1
            ).size.width.toDp()
        }
        val titleLineBoxWidth = (titleWidth + metadataLineHorizontalPadding * 2f)
            .coerceIn(minMetadataLineWidth, expandedViewportWidth)
        val artistLineBoxWidth = (artistWidth + metadataLineHorizontalPadding * 2f)
            .coerceIn(minMetadataLineWidth, expandedViewportWidth)
        val collapsedTitleX = 0.dp
        val expandedTitleContentWidth = titleWidth.coerceAtMost(
            expandedViewportWidth - metadataLineHorizontalPadding * 2f
        )
        val expandedTitleAbsoluteX =
            expandedViewportCenterX - expandedTitleContentWidth / 2f - metadataLineHorizontalPadding
        val expandedTitleRelativeX = expandedTitleAbsoluteX - expandedViewportX
        val expandedTitleX = if (expandedTitleRelativeX < 0.dp) {
            0.dp
        } else {
            expandedTitleRelativeX
        }
        val collapsedArtistX = 0.dp
        val expandedArtistContentWidth = artistWidth.coerceAtMost(
            expandedViewportWidth - metadataLineHorizontalPadding * 2f
        )
        val expandedArtistAbsoluteX =
            expandedViewportCenterX - expandedArtistContentWidth / 2f - metadataLineHorizontalPadding
        val expandedArtistRelativeX = expandedArtistAbsoluteX - expandedViewportX
        val expandedArtistX = if (expandedArtistRelativeX < 0.dp) {
            0.dp
        } else {
            expandedArtistRelativeX
        }
        val titleX = lerpDp(collapsedTitleX, expandedTitleX, progress)
        val artistX = lerpDp(collapsedArtistX, expandedArtistX, progress)

        Column(
            modifier = Modifier
                .width(viewportWidth)
                .height(metadataGroupHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .width(titleLineBoxWidth)
                    .offset(x = titleX)
            ) {
                Text(
                    text = blockTitle,
                    style = titleStyle,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = metadataTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = lineHorizontalPadding)
                )
            }
            Box(
                modifier = Modifier
                    .width(artistLineBoxWidth)
                    .offset(x = artistX)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = blockArtist,
                    style = artistStyle,
                    color = artistColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = metadataTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = lineHorizontalPadding)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .width(viewportWidth)
            .height(metadataGroupHeight)
            .graphicsLayer {
                translationX = viewportX.toPx()
                translationY = viewportY.toPx()
            }
            .clipToBounds()
    ) {
        if (shouldAnimateCollapsedMetadataChange) {
            AnimatedContent(
                targetState = metadataState,
                modifier = Modifier
                    .width(viewportWidth)
                    .height(metadataGroupHeight),
                transitionSpec = {
                    val direction = if (switchDirection < 0) {
                        -1
                    } else {
                        1
                    }
                    val animationSpec = tween<IntOffset>(
                        durationMillis = 260,
                        easing = TrackSwitchProgressEasing
                    )
                    (
                        slideInHorizontally(
                            animationSpec = animationSpec,
                            initialOffsetX = { metadataSwitchDistancePx * direction }
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 260,
                                easing = TrackSwitchProgressEasing
                            )
                        )
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = animationSpec,
                            targetOffsetX = { -metadataSwitchDistancePx * direction }
                        ) + fadeOut(
                            animationSpec = tween(
                                durationMillis = 260,
                                easing = TrackSwitchProgressEasing
                            )
                        )
                    )
                },
                contentAlignment = Alignment.CenterStart,
                label = "CollapsedMetadataSwitch"
            ) { state ->
                MetadataTextBlock(
                    blockTitle = state.title,
                    blockArtist = state.artist
                )
            }
        } else {
            MetadataTextBlock(
                blockTitle = title,
                blockArtist = artist
            )
        }
    }
}

internal data class CollapsedMetadataState(
    val title: String,
    val artist: String
)

