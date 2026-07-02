package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    artistColor: Color? = null,
    durationColor: Color? = null,
    currentSongBackgroundColor: Color? = null
) {
    val contentColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val rowBackground = if (isCurrentSong) {
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(currentSongBackgroundColor ?: MaterialTheme.colorScheme.secondaryContainer)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(rowBackground)
            .clickable { onClick(song) }
            .heightIn(min = 72.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(
            song = song,
            isCurrentSong = isCurrentSong
        )
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
                modifier = Modifier.padding(top = 2.dp)
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
