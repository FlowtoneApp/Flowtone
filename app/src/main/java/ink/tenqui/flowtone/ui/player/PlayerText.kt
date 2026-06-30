package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun SharedSongInfo(
    title: String,
    artist: String,
    currentSongKey: Long?,
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
    val fullscreenTitleScale = lerpFloat(1f, 1.7f, fullscreenProgress)
    val fullscreenArtistScale = lerpFloat(1f, 1.3f, fullscreenProgress)
    val fullscreenArtistAlpha = lerpFloat(1f, 0.8f, fullscreenProgress)
    val fullscreenViewportWidth = ((playerWidth - fullscreenX) / 2f).coerceAtLeast(minMetadataLineWidth)
    val viewportX = lerpDp(defaultViewportX, fullscreenX, fullscreenProgress)
    val viewportY = lerpDp(defaultViewportY, fullscreenTop, fullscreenProgress)
    val viewportWidth = lerpDp(defaultViewportWidth, fullscreenViewportWidth, fullscreenProgress)
    val viewportClipWidth = viewportWidth * fullscreenTitleScale
    val viewportClipHeight = metadataGroupHeight * fullscreenTitleScale
    val lineHorizontalPadding = lerpDp(
        lerpDp(0.dp, metadataLineHorizontalPadding, progress),
        0.dp,
        fullscreenProgress
    )
    val metadataTextAlign = TextAlign.Start
    val metadataState = SongMetadataState(
        key = currentSongKey,
        title = title,
        artist = artist
    )
    val metadataSwitchDistance = 20.dp
    val metadataSwitchDistancePx = with(density) { metadataSwitchDistance.roundToPx() }

    @Composable
    fun MetadataTextBlock(
        blockTitle: String,
        blockArtist: String,
        contentAlpha: Float
    ) {
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

        Column(
            modifier = Modifier
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
            Box(
                modifier = Modifier
                    .width(titleLineBoxWidth)
                    .offset(x = titleX)
                    .graphicsLayer {
                        scaleX = fullscreenTitleScale
                        scaleY = fullscreenTitleScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                Text(
                    text = blockTitle,
                    style = titleStyle,
                    color = titleColor.copy(alpha = titleColor.alpha * contentAlpha),
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
                    .padding(top = fullscreenArtistTopPadding)
                    .graphicsLayer {
                        alpha = artistMinimizedAlpha * fullscreenArtistAlpha
                        scaleX = fullscreenArtistScale
                        scaleY = fullscreenArtistScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                Text(
                    text = blockArtist,
                    style = artistStyle,
                    color = artistColor.copy(alpha = artistColor.alpha * contentAlpha),
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
            .width(viewportClipWidth + metadataSwitchDistance * 2f)
            .height(viewportClipHeight)
            .graphicsLayer {
                translationX = viewportX.toPx() - metadataSwitchDistance.toPx()
                translationY = viewportY.toPx()
            }
            .clipToBounds()
    ) {
        AnimatedSongMetadata(
            state = metadataState,
            switchDirection = switchDirection,
            switchDistancePx = metadataSwitchDistancePx,
            modifier = Modifier
                .offset(x = metadataSwitchDistance)
                .width(viewportWidth)
                .height(metadataGroupHeight)
        ) { state, alpha ->
            MetadataTextBlock(
                blockTitle = state.title,
                blockArtist = state.artist,
                contentAlpha = alpha
            )
        }
    }
}

@Composable
internal fun AnimatedSongMetadata(
    state: SongMetadataState,
    switchDirection: Int,
    switchDistancePx: Int,
    modifier: Modifier = Modifier,
    content: @Composable (SongMetadataState, Float) -> Unit
) {
    var displayedState by remember { mutableStateOf(state) }
    var previousState by remember { mutableStateOf<SongMetadataState?>(null) }
    val switchProgress = remember { Animatable(1f) }

    LaunchedEffect(state) {
        if (state == displayedState) {
            return@LaunchedEffect
        }

        previousState = displayedState
        displayedState = state
        switchProgress.snapTo(0f)
        switchProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 320,
                easing = TrackSwitchProgressEasing
            )
        )
        previousState = null
    }

    val progress = switchProgress.value.coerceIn(0f, 1f)
    val direction = if (switchDirection < 0) {
        -1
    } else {
        1
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        previousState?.let { oldState ->
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset {
                        IntOffset(
                            x = (-switchDistancePx * direction * progress).roundToInt(),
                            y = 0
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                content(oldState, 1f - progress)
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset {
                    IntOffset(
                        x = (switchDistancePx * direction * (1f - progress)).roundToInt(),
                        y = 0
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            content(displayedState, progress)
        }
    }
}

internal data class SongMetadataState(
    val key: Long?,
    val title: String,
    val artist: String
)

