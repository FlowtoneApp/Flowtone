package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ink.tenqui.flowtone.data.listening.ListeningPeriodStats
import ink.tenqui.flowtone.data.listening.ListeningSongStats
import ink.tenqui.flowtone.data.listening.ListeningSourceStats
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.listening.formatListeningDuration
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture

@Composable
internal fun ListeningRecordsScreen(
    listeningStats: ListeningStatsSnapshot,
    initialTab: ListeningRecordTab,
    onBack: () -> Unit,
    itemModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable {
        mutableStateOf(initialTab)
    }
    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(onBack)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "tabs") {
            ListeningRecordTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = itemModifier(0).fillMaxWidth()
            )
        }

        when (selectedTab) {
            ListeningRecordTab.Today -> todayRecordItems(
                listeningStats = listeningStats,
                itemModifier = itemModifier
            )

            ListeningRecordTab.Total -> totalRecordItems(
                listeningStats = listeningStats,
                itemModifier = itemModifier
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todayRecordItems(
    listeningStats: ListeningStatsSnapshot,
    itemModifier: (Int) -> Modifier
) {
    val hasTodayData = listeningStats.today.effectivePlayCount > 0 ||
        listeningStats.today.distinctSongCount > 0 ||
        listeningStats.today.listeningDurationMs > 0L ||
        listeningStats.todaySources.isNotEmpty()

    if (!hasTodayData) {
        item(key = "today-empty") {
            ListeningRecordEmptyState(
                text = "今天还没有听歌记录",
                modifier = itemModifier(1).fillMaxWidth()
            )
        }
        return
    }

    item(key = "today-summary") {
        ListeningSummarySection(
            title = "今日摘要",
            stats = listeningStats.today,
            modifier = itemModifier(1).fillMaxWidth()
        )
    }

    if (listeningStats.todaySources.isNotEmpty()) {
        item(key = "today-source-title") {
            SectionTitle(
                title = "今日主要来源",
                modifier = itemModifier(2)
            )
        }
        itemsIndexed(
            items = listeningStats.todaySources.take(5),
            key = { _, source -> "today-source-${source.sourceKey}" }
        ) { index, source ->
            SourceStatsRow(
                source = source,
                modifier = itemModifier(index + 3).fillMaxWidth()
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.totalRecordItems(
    listeningStats: ListeningStatsSnapshot,
    itemModifier: (Int) -> Modifier
) {
    val hasTotalData = listeningStats.total.effectivePlayCount > 0 ||
        listeningStats.total.distinctSongCount > 0 ||
        listeningStats.total.listeningDurationMs > 0L ||
        listeningStats.totalSources.isNotEmpty() ||
        listeningStats.totalSongs.isNotEmpty()

    if (!hasTotalData) {
        item(key = "total-empty") {
            ListeningRecordEmptyState(
                text = "还没有听歌记录",
                modifier = itemModifier(1).fillMaxWidth()
            )
        }
        return
    }

    item(key = "total-summary") {
        ListeningSummarySection(
            title = "累计摘要",
            stats = listeningStats.total,
            modifier = itemModifier(1).fillMaxWidth()
        )
    }

    if (listeningStats.totalSources.isNotEmpty()) {
        item(key = "total-source-title") {
            SectionTitle(
                title = "常听来源",
                modifier = itemModifier(2)
            )
        }
        itemsIndexed(
            items = listeningStats.totalSources.take(5),
            key = { _, source -> "total-source-${source.sourceKey}" }
        ) { index, source ->
            SourceStatsRow(
                source = source,
                modifier = itemModifier(index + 3).fillMaxWidth()
            )
        }
    }

    if (listeningStats.totalSongs.isNotEmpty()) {
        item(key = "total-song-title") {
            SectionTitle(
                title = "常听歌曲",
                modifier = itemModifier(8)
            )
        }
        itemsIndexed(
            items = listeningStats.totalSongs.take(10),
            key = { _, song -> "total-song-${song.songKey}" }
        ) { index, song ->
            SongStatsRow(
                song = song,
                modifier = itemModifier(index + 9).fillMaxWidth()
            )
        }
    }

    if (listeningStats.includesLegacyAggregateData) {
        item(key = "legacy-hint") {
            Text(
                text = "歌曲与来源排行自本版本开始统计",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = itemModifier(20)
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ListeningRecordTabs(
    selectedTab: ListeningRecordTab,
    onTabSelected: (ListeningRecordTab) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
    ) {
        ListeningRecordTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(text = tab.title)
                }
            )
        }
    }
}

@Composable
private fun ListeningSummarySection(
    title: String,
    stats: ListeningPeriodStats,
    modifier: Modifier = Modifier
) {
    OptionGroup(
        title = title,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ListeningMetricLine(
                label = "有效播放",
                value = "${stats.effectivePlayCount} 次"
            )
            ListeningMetricLine(
                label = "不同歌曲",
                value = "${stats.distinctSongCount} 首"
            )
            ListeningMetricLine(
                label = "听歌时长",
                value = formatListeningDuration(stats.listeningDurationMs)
            )
            ListeningMetricLine(
                label = "主要来源",
                value = stats.topSource?.displayName?.takeIf { it.isNotBlank() }
                    ?.let { "「$it」" } ?: "暂无主要来源"
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ListeningMetricLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SourceStatsRow(
    source: ListeningSourceStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = source.displayName.ifBlank { "未知来源" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.sourceType.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "${source.effectivePlayCount} 次 · ${formatListeningDuration(source.listeningDurationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

@Composable
private fun SongStatsRow(
    song: ListeningSongStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .heightIn(min = 76.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListeningSongArtwork(
            artworkUri = song.artworkUri,
            modifier = Modifier.size(56.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp)
        ) {
            Text(
                text = song.title.ifBlank { "未知歌曲" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "未知艺术家" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "${song.effectivePlayCount} 次 · ${formatListeningDuration(song.listeningDurationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListeningSongArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.takeIf { it.isNotBlank() }?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(96, 96)
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
            .clip(MaterialTheme.shapes.medium)
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

@Composable
private fun ListeningRecordEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 220.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val ListeningRecordTab.title: String
    get() = when (this) {
        ListeningRecordTab.Today -> "今日"
        ListeningRecordTab.Total -> "累计"
    }
