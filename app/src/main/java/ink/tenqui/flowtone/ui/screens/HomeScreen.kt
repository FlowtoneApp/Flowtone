package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningSourceStats
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.playback.PlaybackSourceType
import ink.tenqui.flowtone.ui.components.FlowtonePageHeader
import ink.tenqui.flowtone.ui.components.StaggeredPageElement

@Composable
internal fun HomeScreen(
    songs: List<Song> = emptyList(),
    listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot(),
    playlists: List<LibraryPlaylistCard> = emptyList(),
    onSongClick: (Song) -> Unit = {},
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit = {},
    visible: Boolean = true,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundModifier = if (drawBackground) {
        Modifier.homeScreenBackground()
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        HomeContent(
            songs = songs,
            listeningStats = listeningStats,
            playlists = playlists,
            onSongClick = onSongClick,
            onOpenPlaylist = onOpenPlaylist,
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 21.dp, top = 48.dp, end = 20.dp)
        )
    }
}

@Composable
internal fun Modifier.homeScreenBackground(): Modifier {
    return topLevelPageBackground(HomeBackgroundAccent)
}

@Composable
internal fun Modifier.topLevelPageBackground(
    accentColor: Color,
    cloudAlpha: Float = 1f
): Modifier {
    val backgroundColor = MaterialTheme.colorScheme.background
    val safeCloudAlpha = cloudAlpha.coerceIn(0f, 1f)
    return drawBehind {
        drawRect(color = backgroundColor)
        drawTopPageColorCloud(
            accentColor = accentColor,
            backgroundColor = backgroundColor,
            cloudAlpha = safeCloudAlpha
        )
    }
}

private fun DrawScope.drawTopPageColorCloud(
    accentColor: Color,
    backgroundColor: Color,
    cloudAlpha: Float
) {
    if (cloudAlpha <= 0f) return
    val cloudDiameter = size.height * TopCloudVisibleHeightFraction * 2f /
        (1f + TopCloudCenterOffsetYFactor)
    if (cloudDiameter <= 0f) return

    val cloudRadius = cloudDiameter / 2f
    val cloudCenter = Offset(
        x = cloudRadius * TopCloudCenterOffsetXFactor,
        y = cloudRadius * TopCloudCenterOffsetYFactor
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.34f * cloudAlpha),
                accentColor.copy(alpha = 0.22f * cloudAlpha),
                accentColor.copy(alpha = 0.08f * cloudAlpha),
                Color.Transparent
            ),
            center = cloudCenter,
            radius = cloudRadius
        ),
        radius = cloudRadius,
        center = cloudCenter
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.98f * cloudAlpha),
                backgroundColor.copy(alpha = 0.72f * cloudAlpha),
                Color.Transparent
            ),
            center = Offset(size.width, size.height),
            radius = size.minDimension * BottomRightClearRadiusFraction
        )
    )
}

private val HomeBackgroundAccent = Color(0xFF7898F5)
private const val TopCloudVisibleHeightFraction = 1.05f
private const val TopCloudCenterOffsetXFactor = -0.08f
private const val TopCloudCenterOffsetYFactor = 0.08f
private const val BottomRightClearRadiusFraction = 0.82f

@Composable
private fun HomeContent(
    songs: List<Song>,
    listeningStats: ListeningStatsSnapshot,
    playlists: List<LibraryPlaylistCard>,
    onSongClick: (Song) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val recommendedSongs = remember(songs) {
        songs.shuffled().take(HomeRecommendationCount)
    }
    val frequentPlaylists = remember(listeningStats.totalSources, playlists) {
        buildFrequentPlaylistCards(
            sources = listeningStats.totalSources,
            playlists = playlists
        )
    }
    val recentlyAddedSongs = remember(songs) {
        songs.sortedWith(
            compareByDescending<Song> { song -> song.dateAddedSeconds }
                .thenByDescending { song -> song.id }
        ).take(HomeRecentlyAddedCount)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        StaggeredPageElement(
            visible = visible,
            animationIndex = 0
        ) {
            FlowtonePageHeader(
                title = "声流",
                subtitle = "收藏在设备里的每一段声音"
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
        StaggeredPageElement(
            visible = visible,
            animationIndex = 4
        ) {
            HomeRecommendationSection(
                title = "随便听听",
                songs = recommendedSongs,
                onSongClick = onSongClick
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        StaggeredPageElement(
            visible = visible,
            animationIndex = 8
        ) {
            FrequentPlaylistSection(
                playlists = frequentPlaylists,
                onOpenPlaylist = onOpenPlaylist
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        StaggeredPageElement(
            visible = visible,
            animationIndex = 12
        ) {
            RecentlyAddedSection(
                title = "最近新增",
                songs = recentlyAddedSongs,
                onSongClick = onSongClick
            )
        }
    }
}

@Composable
private fun HomeRecommendationSection(
    title: String,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (songs.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                items(
                    items = songs,
                    key = { song -> "${song.sourceType}:${song.id}:${song.uri}" }
                ) { song ->
                    RecommendationSongCard(
                        song = song,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequentPlaylistSection(
    playlists: List<FrequentPlaylistCard>,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "常听歌单",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (playlists.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                FrequentPlaylistPlaceholderCard(
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            return
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            playlists.chunked(2).forEach { rowPlaylists ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowPlaylists.forEach { playlist ->
                        FrequentPlaylistCardItem(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.card) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowPlaylists.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequentPlaylistPlaceholderCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FrequentPlaylistCardItem(
    playlist: FrequentPlaylistCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = playlist.card.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun RecentlyAddedSection(
    title: String,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (songs.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                items(
                    items = songs.chunked(HomeRecentlyAddedRows),
                    key = { columnSongs ->
                        columnSongs.joinToString(separator = "|") { song ->
                            "${song.sourceType}:${song.id}:${song.uri}"
                        }
                    }
                ) { columnSongs ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.width(260.dp)
                    ) {
                        columnSongs.forEach { song ->
                            RecentlyAddedSongItem(
                                song = song,
                                onClick = { onSongClick(song) }
                            )
                        }
                        if (columnSongs.size == 1) {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecommendationArtwork(
            song = song,
            modifier = Modifier.size(56.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
}

@Composable
private fun RecommendationSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(132.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        RecommendationArtwork(song = song)
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

@Composable
private fun RecommendationArtwork(
    song: Song,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest: ImageRequest? = remember(song.artworkUri, context) {
        song.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(264, 264)
                .build()
        }
    }
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
            .size(132.dp)
            .clip(RoundedCornerShape(8.dp))
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
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private const val HomeRecommendationCount = 8
private const val HomeRecentlyAddedCount = 5
private const val HomeRecentlyAddedRows = 2

private data class FrequentPlaylistCard(
    val card: LibraryPlaylistCard,
    val subtitle: String
)

private fun buildFrequentPlaylistCards(
    sources: List<ListeningSourceStats>,
    playlists: List<LibraryPlaylistCard>
): List<FrequentPlaylistCard> {
    val playlistsById = playlists.associateBy { playlist -> playlist.id }
    val listenedPlaylists = sources
        .asSequence()
        .filter { source ->
            source.sourceType == PlaybackSourceType.UserPlaylist ||
                source.sourceType == PlaybackSourceType.LikedSongs
        }
        .mapNotNull { source ->
            val sourceId = source.sourceId ?: return@mapNotNull null
            val playlist = playlistsById[sourceId] ?: return@mapNotNull null
            FrequentPlaylistCard(
                card = playlist,
                subtitle = "${source.effectivePlayCount} 次播放"
            )
        }
        .distinctBy { playlist -> playlist.card.id }
        .toList()
    val likedSongsPlaylist = playlistsById[LikedSongsPlaylistId]?.let { playlist ->
        FrequentPlaylistCard(
            card = playlist,
            subtitle = playlist.subtitle
        )
    }

    return (listenedPlaylists + listOfNotNull(likedSongsPlaylist))
        .distinctBy { playlist -> playlist.card.id }
        .take(HomeFrequentPlaylistCount)
}

private const val HomeFrequentPlaylistCount = 4
