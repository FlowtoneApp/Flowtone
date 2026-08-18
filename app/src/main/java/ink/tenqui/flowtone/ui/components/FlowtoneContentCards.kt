package ink.tenqui.flowtone.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.player.DefaultFlowCloudSpeed

internal val FlowtoneContentCardShape = RoundedCornerShape(8.dp)

@Composable
internal fun FlowtoneContentSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** 首页“随便听听”使用的大封面歌曲卡。点击反馈会被圆角边界裁切。 */
@Composable
internal fun FlowtoneSongArtworkCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(FlowtoneContentCardShape)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        FlowtoneArtwork(song = song)
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** 保留“常听歌单”的强调卡与标题层级，供该区块恢复时直接使用。 */
@Composable
internal fun FlowtoneFrequentPlaylistCard(
    playlist: LibraryPlaylistCard,
    artworkUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    flowCloudSpeed: Float = DefaultFlowCloudSpeed,
    isFlowCloudPlaying: Boolean = true
) {
    PlaylistCardSurface(
        visualType = if (playlist.id == LikedSongsPlaylistId) {
            PlaylistCardVisualType.LikedMusic
        } else {
            PlaylistCardVisualType.Default
        },
        appearanceColorKey = playlist.appearanceColorKey,
        shape = FlowtoneContentCardShape,
        contentPadding = PaddingValues(0.dp),
        clickModifier = Modifier.clickable(onClick = onClick),
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        modifier = modifier.height(height)
    ) { contentColors ->
        Box(modifier = Modifier.fillMaxSize()) {
            artworkUri?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = "歌单封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.44f)
                                )
                            )
                        )
                )
            }
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (artworkUri != null) Color.White else contentColors.titleColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/**
 * Provider 歌单的普通封面卡。信息层级与歌曲大封面卡一致，只将歌名/曲师替换为歌单名/创建者。
 */
@Composable
internal fun FlowtoneProviderPlaylistCard(
    title: String,
    creator: String,
    artworkUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(FlowtoneContentCardShape)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        FlowtoneArtwork(artworkUri = artworkUri)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = creator,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
internal fun FlowtoneArtwork(
    song: Song,
    modifier: Modifier = Modifier
) {
    FlowtoneArtwork(artworkUri = song.artworkUri, modifier = modifier)
}

@Composable
internal fun FlowtoneArtwork(
    artworkUri: Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(264, 264)
                .build()
        }
    }
    val isSystemDark = isSystemInDarkTheme()
    val placeholderColor = if (isSystemDark) Color.Black else Color.White
    val iconColor = if (isSystemDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(FlowtoneContentCardShape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        imageRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = "封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
