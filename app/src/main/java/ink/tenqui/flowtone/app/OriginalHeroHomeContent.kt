package ink.tenqui.flowtone.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionArtworkCard
import ink.tenqui.flowtone.ui.components.FlowtoneContentSectionTitle
import ink.tenqui.flowtone.ui.components.FlowtoneContentCardShape
import ink.tenqui.flowtone.ui.components.FlowtonePageHeader
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderBodyGap
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.SongListItem

/** 按当前会话种子从本地歌曲池构造不重复的首页推荐。 */
@Composable
internal fun OriginalHeroHomeContent(
    selection: HomeGallerySelection,
    scrollState: ScrollState,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val discoverySongs = selection.discoverySongs
    val suggestedSongs = selection.suggestedSongs
    val featuredSong = selection.featuredSong

    Column(modifier = modifier.verticalScroll(scrollState).padding(bottom = 32.dp)) {
        // 与覆盖层使用相同文本和约束，让字体缩放下的占位也覆盖完整标题。
        FlowtonePageHeader(
            title = stringResource(R.string.top_level_home_title),
            subtitle = stringResource(R.string.top_level_home_subtitle),
            modifier = Modifier.fillMaxWidth()
                .padding(
                    start = FlowtonePageHeaderExpandedStartPadding,
                    top = FlowtonePageHeaderExpandedTopPadding,
                    end = FlowtonePageHeaderExpandedEndPadding
                )
                .alpha(0f)
                .clearAndSetSemantics { }
        )
        Spacer(Modifier.height(FlowtonePageHeaderBodyGap))
        if (featuredSong != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                val artworkSize = minOf(132.dp, maxWidth * 0.4f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(artworkSize)
                            .clip(FlowtoneContentCardShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        AsyncImage(
                            model = featuredSong.artworkUri,
                            contentDescription = "封面",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("本地音乐 · 随机发现",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(featuredSong.title,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(featuredSong.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text("播放歌曲",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                onSongClick(featuredSong)
                            }.padding(vertical = 4.dp))
                    }
                }
            }
        } else {
            Text("曲库中暂无音乐", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (discoverySongs.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            FlowtoneContentSectionTitle("发现一些声音", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(discoverySongs, key = { "${it.sourceType.name}:${it.id}" }) { song ->
                    FlowtoneCollectionArtworkCard(
                        title = song.title, subtitle = song.artist, artworkUri = song.artworkUri,
                        onClick = { onSongClick(song) }, modifier = Modifier.width(140.dp))
                }
            }
        }
        if (suggestedSongs.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            FlowtoneContentSectionTitle("听听这些", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestedSongs.forEach { song ->
                    androidx.compose.runtime.key(song.sourceType, song.id) {
                        SongListItem(song = song, isCurrentSong = false, onClick = onSongClick, compact = true)
                    }
                }
            }
        }
    }
}
