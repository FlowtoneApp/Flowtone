package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerTextLayout(
    title: String,
    artist: String,
    contentAlpha: Float,
    titleColor: Color,
    artistColor: Color,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    viewportWidth: Dp,
    metadataGroupHeight: Dp,
    expandedViewportWidth: Dp,
    expandedViewportCenterX: Dp,
    expandedViewportX: Dp,
    minMetadataLineWidth: Dp,
    metadataLineHorizontalPadding: Dp,
    lineHorizontalPadding: Dp,
    progress: Float,
    fullscreenProgress: Float,
    fullscreenTitleScale: Float,
    fullscreenArtistScale: Float,
    fullscreenArtistAlpha: Float,
    minimizedProgress: Float,
    textAlign: TextAlign,
    canClickArtist: Boolean,
    onArtistClick: ((String) -> Unit)?,
    onTitleBounds: (LayoutCoordinates, FullscreenLayerTransform) -> Unit = { _, _ -> },
    onArtistBounds: (LayoutCoordinates, FullscreenLayerTransform) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val titleWidth = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(title),
            style = titleStyle,
            maxLines = 1
        ).size.width.toDp()
    }
    val artistWidth = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(artist),
            style = artistStyle,
            maxLines = 1
        ).size.width.toDp()
    }
    val maxMetadataLineWidth = lerpDp(expandedViewportWidth, viewportWidth, fullscreenProgress)
    val titleLineBoxWidth = (titleWidth + lineHorizontalPadding * 2f)
        .coerceIn(minMetadataLineWidth, maxMetadataLineWidth)
    val artistLineBoxWidth = (artistWidth + lineHorizontalPadding * 2f)
        .coerceIn(minMetadataLineWidth, maxMetadataLineWidth)
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
    val titleX = lerpDp(
        lerpDp(collapsedTitleX, expandedTitleX, progress),
        0.dp,
        fullscreenProgress
    )
    val artistX = lerpDp(
        lerpDp(collapsedArtistX, expandedArtistX, progress),
        0.dp,
        fullscreenProgress
    )
    val fullscreenArtistTopPadding = lerpDp(4.dp, 14.dp, fullscreenProgress)
    val artistMinimizedAlpha = lerpFloat(minimizedProgress, 1f, progress)
    val metadataContentTranslationY = with(density) {
        (12.dp * (1f - minimizedProgress)).toPx()
    }

    Column(
        modifier = modifier
            .width(viewportWidth)
            .height(metadataGroupHeight)
            .graphicsLayer {
                translationY = with(density) {
                    (12.dp * (1f - minimizedProgress)).toPx()
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        PlayerSongTitle(
            title = title,
            style = titleStyle,
            color = titleColor,
            contentAlpha = contentAlpha,
            lineBoxWidth = titleLineBoxWidth,
            offsetX = titleX,
            lineHorizontalPadding = lineHorizontalPadding,
            scale = fullscreenTitleScale,
            textAlign = textAlign,
            onBounds = { coordinates, transform ->
                onTitleBounds(
                    coordinates,
                    transform.copy(
                        translationY = transform.translationY + metadataContentTranslationY
                    )
                )
            }
        )
        PlayerArtistText(
            artist = artist,
            style = artistStyle,
            color = artistColor,
            contentAlpha = contentAlpha,
            lineBoxWidth = artistLineBoxWidth,
            offsetX = artistX,
            topPadding = fullscreenArtistTopPadding,
            lineHorizontalPadding = lineHorizontalPadding,
            minimizedAlpha = artistMinimizedAlpha,
            fullscreenAlpha = fullscreenArtistAlpha,
            scale = fullscreenArtistScale,
            textAlign = textAlign,
            canClickArtist = canClickArtist,
            onArtistClick = onArtistClick,
            onBounds = { coordinates, transform ->
                onArtistBounds(
                    coordinates,
                    transform.copy(
                        translationY = transform.translationY + metadataContentTranslationY
                    )
                )
            }
        )
    }
}
