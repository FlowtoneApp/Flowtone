package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AddToPlaylistItemSongInfo(
    title: String,
    artist: String,
    progress: Float,
    titleColor: Color,
    artistColor: Color,
    width: Dp,
    modifier: Modifier = Modifier
) {
    val itemProgress = progress.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .width(width)
            .height(56.dp)
            .graphicsLayer {
                alpha = itemProgress
                translationY = (16.dp * (1f - itemProgress)).toPx()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = artistColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
internal fun LyricsPlaceholderSongInfo(
    title: String,
    artist: String,
    visibilityProgress: Float,
    titleColor: Color,
    artistColor: Color,
    playerWidth: Dp,
    lyricsContentStart: Dp,
    addToPlaylistProgress: Float = 0f,
    addToPlaylistTextStart: Dp = 24.dp,
    addToPlaylistTextWidth: Dp = (playerWidth - 48.dp).coerceAtLeast(0.dp),
    artistClickable: Boolean = false,
    onArtistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (visibilityProgress <= 0.001f) return

    val density = LocalDensity.current
    val safeTopPadding = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val contentProgress = visibilityProgress.coerceIn(0f, 1f)
    val addProgress = addToPlaylistProgress.coerceIn(0f, 1f)
    val lyricsTextStart = lyricsContentStart + 56.dp + 12.dp
    val lyricsTextWidth = (
        playerWidth - lyricsTextStart - lyricsContentStart
        ).coerceAtLeast(80.dp)
    val metadataX = lerpDp(lyricsTextStart, addToPlaylistTextStart, addProgress)
    val metadataWidth = lerpDp(
        lyricsTextWidth,
        addToPlaylistTextWidth,
        addProgress
    )

    Column(
        modifier = modifier
            .offset(x = metadataX, y = safeTopPadding + 56.dp)
            .width(metadataWidth)
            .height(56.dp)
            .graphicsLayer {
                alpha = contentProgress
                translationY = (16.dp * (1f - contentProgress)).toPx()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = artistColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable(
                    enabled = artistClickable && onArtistClick != null,
                    onClick = { onArtistClick?.invoke() }
                )
        )
    }
}

@Composable
internal fun FullscreenCollapseArrow(
    progress: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleProgress = SoftElementEasing.transform(progress.coerceIn(0f, 1f))
    val density = LocalDensity.current
    val safeTopPadding = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val arrowOffsetY = lerpDp((-32).dp, safeTopPadding + 18.dp, visibleProgress)

    Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = "\u6536\u8d77\u5168\u5c4f\u64ad\u653e\u5668",
        tint = Color.White,
        modifier = modifier
            .offset(y = arrowOffsetY)
            .size(width = 42.dp, height = 30.dp)
            .clickable(
                enabled = progress > 0.72f,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                alpha = visibleProgress
                scaleX = lerpFloat(0.72f, 1f, visibleProgress)
                scaleY = lerpFloat(0.72f, 1f, visibleProgress)
            }
    )
}
