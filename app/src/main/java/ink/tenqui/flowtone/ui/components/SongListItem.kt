package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song

@Composable
fun SongListItem(
    song: Song,
    isCurrentSong: Boolean,
    onClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    artistColor: Color? = null,
    durationColor: Color? = null,
    currentSongBackgroundColor: Color? = null,
    compact: Boolean = false,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    isPreviousSelected: Boolean = false,
    isNextSelected: Boolean = false,
    selectionSlotPadding: Dp = 0.dp,
    onLongClick: ((Song) -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val itemIndication = if (selectionMode) null else LocalIndication.current
    val itemMinHeight = if (compact) 64.dp else 72.dp
    val itemVerticalPadding = if (compact) 6.dp else 8.dp
    val artworkSize = if (compact) 48.dp else 56.dp
    val artistTopPadding = if (compact) 0.dp else 2.dp
    val contentColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val rowShape = MaterialTheme.shapes.medium
    val selectionPosition = selectionGroupPosition(
        isSelected = isSelected,
        isPreviousSelected = isPreviousSelected,
        isNextSelected = isNextSelected
    )
    val selectionTransition = updateTransition(
        targetState = SongSelectionVisualState(
            selectionMode = selectionMode,
            position = selectionPosition
        ),
        label = "SongSelection"
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val selectionAlpha = if (isDarkTheme) {
        0.16f
    } else {
        0.12f
    }
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = selectionAlpha)
    val selectionBorderColor = MaterialTheme.colorScheme.primary.copy(
        alpha = if (isDarkTheme) 0.34f else 0.24f
    )
    val selectionBackground by selectionTransition.animateColor(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionBackground"
    ) { state ->
        if (state.position == SelectionGroupPosition.None) Color.Transparent else selectionColor
    }
    val topConnectionProgress by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionTopConnection"
    ) { state ->
        if (state.position.connectsTop) 1f else 0f
    }
    val bottomConnectionProgress by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionBottomConnection"
    ) { state ->
        if (state.position.connectsBottom) 1f else 0f
    }
    val topCornerRadius by selectionTransition.animateDp(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionTopCorner"
    ) { state ->
        if (state.position.connectsTop) 0.dp else SelectionCornerRadius
    }
    val bottomCornerRadius by selectionTransition.animateDp(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionBottomCorner"
    ) { state ->
        if (state.position.connectsBottom) 0.dp else SelectionCornerRadius
    }
    val selectionBorderAlpha by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionBorderAlpha"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) 1f else 0f
    }
    val topBorderEdgeAlpha by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionTopBorderEdge"
    ) { state ->
        if (
            state.position != SelectionGroupPosition.None &&
            !state.position.connectsTop
        ) {
            1f
        } else {
            0f
        }
    }
    val bottomBorderEdgeAlpha by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionBottomBorderEdge"
    ) { state ->
        if (
            state.position != SelectionGroupPosition.None &&
            !state.position.connectsBottom
        ) {
            1f
        } else {
            0f
        }
    }
    val currentSongBackground by animateColorAsState(
        targetValue = if (isCurrentSong && !isSelected) {
            currentSongBackgroundColor ?: MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 170),
        label = "CurrentSongBackground"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = itemMinHeight + selectionSlotPadding * 2)
    ) {
        val selectionTopInset =
            selectionSlotPadding * (1f - topConnectionProgress)
        val selectionBottomInset =
            selectionSlotPadding * (1f - bottomConnectionProgress)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    top = selectionTopInset,
                    bottom = selectionBottomInset
                )
                .background(
                    color = selectionBackground,
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .selectionGroupBorder(
                    color = selectionBorderColor,
                    alpha = selectionBorderAlpha,
                    topEdgeAlpha = topBorderEdgeAlpha,
                    bottomEdgeAlpha = bottomBorderEdgeAlpha,
                    topConnectionProgress = topConnectionProgress,
                    bottomConnectionProgress = bottomConnectionProgress,
                    topCornerRadius = topCornerRadius,
                    bottomCornerRadius = bottomCornerRadius
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = selectionSlotPadding)
                .clip(rowShape)
                .background(currentSongBackground)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = itemIndication,
                    onClick = { onClick(song) },
                    onLongClick = if (!selectionMode && onLongClick != null) {
                        {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick(song)
                        }
                    } else {
                        null
                    }
                )
                .semantics {
                    if (selectionMode) {
                        selected = isSelected
                        onClick(
                            label = if (isSelected) "点按取消选择" else "点按选择"
                        ) {
                            onClick(song)
                            true
                        }
                    }
                }
                .heightIn(min = itemMinHeight)
                .padding(horizontal = 12.dp, vertical = itemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(artworkSize)) {
                AlbumArtwork(
                    song = song,
                    isCurrentSong = isCurrentSong,
                    modifier = Modifier.matchParentSize()
                )
                SongSelectionIndicator(
                    transition = selectionTransition,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor ?: contentColor,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = artistColor ?: if (isCurrentSong) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = artistTopPadding)
                )
            }
            Box(
                modifier = Modifier.width(96.dp)
            ) {
                if (isCurrentSong) {
                    Text(
                        text = "\u64ad\u653e\u4e2d",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = formatDuration(song.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = durationColor ?: if (isCurrentSong) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

private fun Modifier.selectionGroupBorder(
    color: Color,
    alpha: Float,
    topEdgeAlpha: Float,
    bottomEdgeAlpha: Float,
    topConnectionProgress: Float,
    bottomConnectionProgress: Float,
    topCornerRadius: Dp,
    bottomCornerRadius: Dp
): Modifier = drawBehind {
    if (alpha <= 0f) return@drawBehind

    val strokeWidth = 1.dp.toPx()
    val halfStroke = strokeWidth / 2f
    val left = halfStroke
    val right = size.width - halfStroke
    val top = halfStroke
    val bottom = size.height - halfStroke
    val topRadius = topCornerRadius.toPx().coerceAtMost((right - left) / 2f)
    val bottomRadius = bottomCornerRadius.toPx().coerceAtMost((right - left) / 2f)
    val sideTop = (top + topRadius) * (1f - topConnectionProgress)
    val sideBottom = (bottom - bottomRadius) * (1f - bottomConnectionProgress) +
        size.height * bottomConnectionProgress
    val stroke = Stroke(width = strokeWidth)
    val sideColor = color.copy(alpha = color.alpha * alpha)

    drawLine(
        color = sideColor,
        start = androidx.compose.ui.geometry.Offset(left, sideTop),
        end = androidx.compose.ui.geometry.Offset(left, sideBottom),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = sideColor,
        start = androidx.compose.ui.geometry.Offset(right, sideTop),
        end = androidx.compose.ui.geometry.Offset(right, sideBottom),
        strokeWidth = strokeWidth
    )

    if (topEdgeAlpha > 0f) {
        val topPath = Path().apply {
            moveTo(left, top + topRadius)
            quadraticTo(left, top, left + topRadius, top)
            lineTo(right - topRadius, top)
            quadraticTo(right, top, right, top + topRadius)
        }
        drawPath(
            path = topPath,
            color = color.copy(alpha = color.alpha * alpha * topEdgeAlpha),
            style = stroke
        )
    }

    if (bottomEdgeAlpha > 0f) {
        val bottomPath = Path().apply {
            moveTo(left, bottom - bottomRadius)
            quadraticTo(left, bottom, left + bottomRadius, bottom)
            lineTo(right - bottomRadius, bottom)
            quadraticTo(right, bottom, right, bottom - bottomRadius)
        }
        drawPath(
            path = bottomPath,
            color = color.copy(alpha = color.alpha * alpha * bottomEdgeAlpha),
            style = stroke
        )
    }
}

@Composable
private fun SongSelectionIndicator(
    transition: Transition<SongSelectionVisualState>,
    modifier: Modifier = Modifier
) {
    val pageBackground = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val indicatorOutlineColor =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val indicatorAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorAlpha"
    ) { state ->
        if (state.selectionMode) 1f else 0f
    }
    val indicatorFill by transition.animateColor(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorFill"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) {
            primaryColor
        } else {
            Color.Transparent
        }
    }
    val indicatorBorder by transition.animateColor(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorBorder"
    ) { state ->
        if (state.position == SelectionGroupPosition.None) {
            indicatorOutlineColor
        } else {
            Color.Transparent
        }
    }
    val indicatorScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorScale"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) 1f else 0.9f
    }
    val checkAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionCheckAlpha"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) 1f else 0f
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                alpha = indicatorAlpha
                scaleX = indicatorScale
                scaleY = indicatorScale
            }
            // Outer page-colored ring keeps the control legible over any album art.
            .background(pageBackground, CircleShape)
            .padding(2.dp)
            .background(indicatorFill, CircleShape)
            .border(
                width = 1.5.dp,
                color = indicatorBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    alpha = checkAlpha
                    scaleX = 0.72f + 0.28f * checkAlpha
                    scaleY = 0.72f + 0.28f * checkAlpha
                }
        )
    }
}

private data class SongSelectionVisualState(
    val selectionMode: Boolean,
    val position: SelectionGroupPosition
)

private const val SelectionColorDurationMillis = 180
private const val SelectionShapeDurationMillis = 220
private val SelectionCornerRadius = 20.dp

@Composable
private fun AlbumArtwork(
    song: Song,
    isCurrentSong: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest: ImageRequest? = remember(song.artworkUri, context) {
        song.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(96, 96)
                .build()
        }
    }
    val shape = MaterialTheme.shapes.medium
    val isSystemDark = isSystemInDarkTheme()
    val placeholderColor = if (isSystemDark) {
        Color.Black
    } else {
        Color.White
    }
    val iconColor = if (isSystemDark) {
        Color.White.copy(alpha = 0.78f)
    } else {
        Color.Black.copy(alpha = 0.72f)
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = iconColor
        )
        imageRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = "\u4e13\u8f91\u5c01\u9762",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
