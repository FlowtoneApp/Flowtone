package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    modifier: Modifier = Modifier
) {
    val containerColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 2.dp),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        ListItem(
            modifier = Modifier.clickable { onClick(song) },
            colors = ListItemDefaults.colors(containerColor = containerColor),
            leadingContent = {
                AlbumArtwork(
                    song = song,
                    isCurrentSong = isCurrentSong,
                    modifier = Modifier.offset(x = (-8).dp)
                )
            },
            headlineContent = {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = (-8).dp)
                )
            },
            supportingContent = {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentSong) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = (-8).dp)
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentSong) {
                        Text(
                            text = "\u64ad\u653e\u4e2d",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = formatDuration(song.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentSong) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        )
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
    val placeholderColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
