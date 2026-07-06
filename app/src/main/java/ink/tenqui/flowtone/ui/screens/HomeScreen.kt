package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.player.isSelectableArtist
import ink.tenqui.flowtone.ui.player.parseArtistCandidates

@Composable
internal fun HomeScreen(
    songs: List<Song>,
    currentSong: Song?,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistCount = remember(songs) {
        songs
            .flatMap { song -> parseArtistCandidates(song.artist) }
            .filter { artist -> isSelectableArtist(artist) }
            .distinctBy { artist -> artist.lowercase() }
            .size
    }
    val albumCount = remember(songs) {
        songs
            .mapNotNull { song -> song.albumId }
            .distinct()
            .size
            .takeIf { count -> count > 0 }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 8.dp,
            end = 20.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeHeroCard(
                currentSong = currentSong,
                onOpenLocalLibrary = onOpenLocalLibrary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            HomeSection(title = "快捷入口") {
                HomeActionCard(
                    title = "本地曲库",
                    subtitle = "查看设备中的 ${songs.size} 首歌曲",
                    icon = Icons.Rounded.LibraryMusic,
                    onClick = onOpenLocalLibrary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            HomeSection(title = "本地曲库概览") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeMetricCard(
                        label = "歌曲",
                        value = songs.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    HomeMetricCard(
                        label = "艺术家",
                        value = artistCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    albumCount?.let { count ->
                        HomeMetricCard(
                            label = "专辑",
                            value = count.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    currentSong: Song?,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasCurrentSong = currentSong != null
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (hasCurrentSong) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onOpenLocalLibrary)
                }
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeArtwork(
                song = currentSong,
                modifier = Modifier.size(88.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp)
            ) {
                Text(
                    text = if (hasCurrentSong) "继续聆听" else "开始聆听",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = currentSong?.title ?: "打开本地曲库",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = currentSong?.artist ?: "播放设备中的本地音乐",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (!hasCurrentSong) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeArtwork(
    song: Song?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(song?.artworkUri, context) {
        song?.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(176, 176)
                .build()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp)
        )
        imageRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
