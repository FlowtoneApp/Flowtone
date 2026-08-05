package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SharedSongInfo(
    songPresentationTransition: PlayerSongPresentationTransition,
    progress: Float,
    titleColor: Color,
    artistColor: Color,
    playerWidth: Dp,
    minimizedProgress: Float,
    minimizedHeight: Dp,
    collapsedHeight: Dp,
    expandedTop: Dp,
    fullscreenProgress: Float = 0f,
    fullscreenX: Dp = 0.dp,
    fullscreenTop: Dp = 0.dp,
    lyricsMetadataProgress: Float = 0f,
    contentExitProgress: Float = 0f,
    switchDirection: Int,
    artistClickEnabled: Boolean = false,
    onArtistClick: ((String) -> Unit)? = null,
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
    val minimizedViewportX = 30.dp
    val collapsedViewportX = 30.dp
    val minimizedControlsReservedWidth = 40.dp * 3f + 4.dp * 2f + 20.dp
    val collapsedControlsReservedWidth = 48.dp * 3f + 8.dp * 2f + 30.dp
    val minimizedViewportWidth =
        playerWidth - minimizedViewportX - minimizedControlsReservedWidth
    val collapsedViewportWidth =
        playerWidth - collapsedViewportX - collapsedControlsReservedWidth
    val minimizedViewportY = minimizedHeight / 2f - metadataGroupHeight / 2f
    val collapsedViewportY = collapsedCenterY - metadataGroupHeight / 2f
    val expandedViewportWidth = playerWidth * 0.82f
    val expandedViewportX = (playerWidth - expandedViewportWidth) / 2f
    val expandedViewportCenterX = expandedViewportX + expandedViewportWidth / 2f
    val expandedViewportY = expandedTop
    val baseViewportX = lerpDp(minimizedViewportX, collapsedViewportX, minimizedProgress)
    val baseViewportY = lerpDp(minimizedViewportY, collapsedViewportY, minimizedProgress)
    val baseViewportWidth = lerpDp(
        minimizedViewportWidth,
        collapsedViewportWidth,
        minimizedProgress
    )
    val defaultViewportX = lerpDp(baseViewportX, expandedViewportX, progress)
    val defaultViewportY = lerpDp(baseViewportY, expandedViewportY, progress)
    val defaultViewportWidth = lerpDp(baseViewportWidth, expandedViewportWidth, progress)
    val fullscreenTitleScale = lerpFloat(1f, 1.6f, fullscreenProgress)
    val fullscreenArtistScale = lerpFloat(1f, 1.3f, fullscreenProgress)
    val fullscreenArtistAlpha = lerpFloat(1f, 0.8f, fullscreenProgress)
    val fullscreenViewportWidth = ((playerWidth - fullscreenX) / 2f).coerceAtLeast(minMetadataLineWidth)
    val viewportX = lerpDp(defaultViewportX, fullscreenX, fullscreenProgress)
    val viewportY = lerpDp(defaultViewportY, fullscreenTop, fullscreenProgress)
    val viewportWidth = lerpDp(defaultViewportWidth, fullscreenViewportWidth, fullscreenProgress)
    val viewportClipWidth = viewportWidth * fullscreenTitleScale
    val viewportClipHeight = metadataGroupHeight * fullscreenTitleScale
    val fullTextExitProgress = contentExitProgress.coerceIn(0f, 1f)
    val fullTextExitOffsetY = (-24).dp * fullTextExitProgress
    val lyricsTextExitProgress = lyricsMetadataProgress.coerceIn(0f, 1f)
    val lyricsTextExitOffsetY = (-24).dp * lyricsTextExitProgress
    val lineHorizontalPadding = lerpDp(
        lerpDp(0.dp, metadataLineHorizontalPadding, progress),
        0.dp,
        fullscreenProgress
    )
    val metadataTextAlign = TextAlign.Start
    val metadataSwitchDistance = 20.dp
    val metadataSwitchDistancePx = with(density) { metadataSwitchDistance.roundToPx() }
    Box(
        modifier = modifier
            .width(viewportClipWidth + metadataSwitchDistance * 2f)
            .height(viewportClipHeight)
            .graphicsLayer {
                alpha = (1f - fullTextExitProgress) * (1f - lyricsTextExitProgress)
                translationX = viewportX.toPx() - metadataSwitchDistance.toPx()
                translationY = viewportY.toPx() +
                    fullTextExitOffsetY.toPx() +
                    lyricsTextExitOffsetY.toPx()
            }
            .clipToBounds()
    ) {
        PlayerSongPresentationTransitionContent(
            transition = songPresentationTransition,
            switchDirection = switchDirection,
            switchDistancePx = metadataSwitchDistancePx,
            modifier = Modifier
                .offset(x = metadataSwitchDistance)
                .width(viewportWidth)
                .height(metadataGroupHeight)
        ) { presentation, alpha ->
            PlayerTextLayout(
                title = presentation.title,
                artist = presentation.artist,
                contentAlpha = alpha,
                titleColor = titleColor,
                artistColor = artistColor,
                titleStyle = titleStyle,
                artistStyle = artistStyle,
                textMeasurer = textMeasurer,
                density = density,
                viewportWidth = viewportWidth,
                metadataGroupHeight = metadataGroupHeight,
                expandedViewportWidth = expandedViewportWidth,
                expandedViewportCenterX = expandedViewportCenterX,
                expandedViewportX = expandedViewportX,
                minMetadataLineWidth = minMetadataLineWidth,
                metadataLineHorizontalPadding = metadataLineHorizontalPadding,
                lineHorizontalPadding = lineHorizontalPadding,
                progress = progress,
                fullscreenProgress = fullscreenProgress,
                fullscreenTitleScale = fullscreenTitleScale,
                fullscreenArtistScale = fullscreenArtistScale,
                fullscreenArtistAlpha = fullscreenArtistAlpha,
                minimizedProgress = minimizedProgress,
                textAlign = metadataTextAlign,
                canClickArtist = artistClickEnabled &&
                    onArtistClick != null &&
                    isSelectableArtist(presentation.artist),
                onArtistClick = onArtistClick
            )
        }
    }
}

