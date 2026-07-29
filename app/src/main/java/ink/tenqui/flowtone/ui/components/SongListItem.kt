package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onLongClick: ((Song) -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
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
    val targetBackground = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isCurrentSong -> currentSongBackgroundColor
            ?: MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val rowBackground by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 170),
        label = "SongSelectionBackground"
    )
    val targetBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val rowBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 170),
        label = "SongSelectionBorder"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBackground)
            .border(width = 1.dp, color = rowBorderColor, shape = rowShape)
            .combinedClickable(
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
            if (selectionMode) {
                SongSelectionIndicator(
                    selected = isSelected,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
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

@Composable
private fun SongSelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val pageBackground = MaterialTheme.colorScheme.background
    val indicatorFill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 170),
        label = "SongSelectionIndicatorFill"
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = tween(durationMillis = 170),
        label = "SongSelectionIndicatorScale"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = indicatorScale
                scaleY = indicatorScale
            }
            // Outer page-colored ring keeps the control legible over any album art.
            .background(pageBackground, CircleShape)
            .padding(2.dp)
            .background(indicatorFill, CircleShape)
            .border(
                width = if (selected) 0.dp else 1.5.dp,
                color = if (selected) Color.Transparent else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(170)) + scaleIn(
                initialScale = 0.72f,
                animationSpec = tween(170)
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.72f,
                animationSpec = tween(140)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

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
