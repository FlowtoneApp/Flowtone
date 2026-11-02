package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.likedSongsPlaylistCard
import ink.tenqui.flowtone.data.local.parseLocalArtistCandidates
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.*
import ink.tenqui.flowtone.viewmodel.MusicUiState
import ink.tenqui.flowtone.viewmodel.ProviderCatalogLoadState
import kotlin.math.roundToInt

@Composable
internal fun LibraryHomeContent(
    pageTransition: PageTransitionScope,
    uiState: MusicUiState,
    playlistController: LibraryPlaylistController,
    likedSongCount: Int,
    selectedSourceKey: String,
    isPageActive: Boolean,
    onSourceSelected: (String) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onOpenLocalLibrary: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit,
    onOpenProviderPlaylist: (ProviderPlaylistSearchItem) -> Unit,
    onProviderSongQueueClick: (List<ProviderSong>, Int, PlaybackSource) -> Unit,
    onLoadProviderCollections: (String) -> Unit,
    onLoadProviderPlaylists: (String) -> Unit,
    onRefreshLibrary: (String) -> Unit,
    flowCloudSpeed: Float,
    modifier: Modifier = Modifier
) {
    val sources = remember(uiState.libraryProviderOptions) { librarySourceItems(uiState.libraryProviderOptions) }
    val selectedSource = sources.firstOrNull { it.identity.key == selectedSourceKey }
    val provider = (selectedSource?.identity as? LibrarySourceIdentity.Extension)?.extensionId
    val categories = remember(selectedSourceKey, selectedSource) { libraryCategories(selectedSourceKey, selectedSource) }
    var requestedCategory by remember(selectedSourceKey) { mutableStateOf<LibraryCategory?>(null) }
    val category = validLibraryCategory(categories, requestedCategory)
    val playlists = remember(playlistController.playlists, likedSongCount) {
        listOf(likedSongsPlaylistCard(likedSongCount)) + playlistController.playlists
    }
    val hasUserPlaylists = remember(playlists) { playlists.any { !it.isSystem } }
    val artists = remember(uiState.songs, selectedSourceKey) {
        if (selectedSourceKey == LibrarySourceIdentity.Local.key) {
            uiState.songs.asSequence().flatMap { parseLocalArtistCandidates(it.artist).asSequence() }
                .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.toList()
        } else emptyList()
    }

    LaunchedEffect(selectedSourceKey, uiState.libraryProviderSourcesResolved, sources) {
        if (selectedSourceKey != "mine" && selectedSource == null && uiState.libraryProviderSourcesResolved) {
            onSourceSelected("mine")
        }
    }
    LaunchedEffect(provider, selectedSource?.supportsAlbums, selectedSource?.supportsPlaylists) {
        if (provider != null) {
            if (selectedSource.supportsSongs || selectedSource.supportsAlbums) onLoadProviderCollections(provider)
            if (selectedSource.supportsPlaylists) onLoadProviderPlaylists(provider)
        }
    }

    Box(modifier = modifier) {
    LazyColumn(state = playlistController.listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            FlowtonePageHeader(
                title = stringResource(R.string.top_level_library_title),
                subtitle = stringResource(R.string.top_level_library_subtitle),
                modifier = Modifier.fillMaxWidth().padding(start = FlowtonePageHeaderExpandedStartPadding,
                    top = FlowtonePageHeaderExpandedTopPadding, end = FlowtonePageHeaderExpandedEndPadding)
                    .alpha(0f).clearAndSetSemantics { }
            )
            Spacer(Modifier.height(FlowtonePageHeaderBodyGap))
        }
        if (selectedSourceKey != "mine" && categories.isNotEmpty()) item(key = "category-navigation") {
            val bounds = remember(categories) { mutableStateMapOf<LibraryCategory, Pair<Float, Float>>() }
            var parentX by remember(categories) { mutableFloatStateOf(0f) }
            val target = bounds[category]
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .onGloballyPositioned { parentX = it.positionInRoot().x }) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                categories.forEach { item ->
                    val interaction = remember { MutableInteractionSource() }
                    Column(Modifier.heightIn(min = 48.dp).clickable(interactionSource = interaction, indication = null) { requestedCategory = item }
                        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (item == category) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.onGloballyPositioned {
                                bounds[item] = it.positionInRoot().x to it.size.width.toFloat()
                            },
                            color = if (item == category) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
            if (target != null) {
                val indicatorX by animateFloatAsState(target.first - parentX,
                    tween(320, easing = FlowtoneMotion.Easing), label = "CategoryIndicatorX")
                val indicatorWidth by animateFloatAsState(target.second,
                    tween(320, easing = FlowtoneMotion.Easing), label = "CategoryIndicatorWidth")
                val density = androidx.compose.ui.platform.LocalDensity.current
                Box(Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp)
                    .offset { IntOffset(indicatorX.roundToInt(), 0) }
                    .width(with(density) { indicatorWidth.toDp() }).height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
            }
            }
        }
        when {
            selectedSourceKey == "mine" -> {
                item(key = "playlist-section") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 4.dp)
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("歌单", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { playlistController.startEditing() },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("创建")
                        }
                    }
                }
                if (playlists.isNotEmpty()) item(key = "playlist-group-gap") { Spacer(Modifier.height(16.dp)) }
                itemsIndexed(playlists, key = { _, playlist -> "playlist:${playlist.id}" }) { index, playlist ->
                    val groupShape = playlistGroupRowShape(index, playlists.size)
                    val cardModifier = Modifier.animateItem(
                        fadeInSpec = tween(FlowtoneMotion.ShortDurationMillis), placementSpec = null,
                        fadeOutSpec = tween(FlowtoneMotion.ShortDurationMillis)
                    ).then(pageTransition.elementAppearanceModifierAt(pageTransition.progress, index.coerceAtMost(23)))
                        .fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(groupShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.20f))
                        .clickable { onOpenPlaylist(playlist) }
                    Box(cardModifier.padding(top = if (index == 0) 4.dp else 0.dp,
                        bottom = if (index == playlists.lastIndex) 4.dp else 0.dp)) {
                    if (!playlist.isSystem && playlist.customArtworkUri != null) {
                        ListItem(headlineContent = { Text(playlist.title, maxLines = 1) },
                            supportingContent = { Text(playlist.subtitle, maxLines = 1) },
                            leadingContent = { FlowtoneArtwork(artworkUri = playlist.customArtworkUri, modifier = Modifier.size(56.dp)) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        LibraryPlaylistListVisual(playlist = playlist, itemHeight = 72.dp, appearProgress = 1f,
                            clickModifier = Modifier,
                            flowCloudSpeed = flowCloudSpeed, isFlowCloudPlaying = false,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    }
                    }
                }
                if (!hasUserPlaylists) item(key = "empty-playlists") {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("还没有创建其他歌单", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("创建歌单，整理你喜欢的音乐", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            selectedSourceKey == LibrarySourceIdentity.Local.key -> {
                if (category == LibraryCategory.Songs) item(key = "local-songs") {
                    ListItem(headlineContent = { Text("歌曲") }, supportingContent = { Text(if (uiState.hasPermission) "${uiState.songs.size} 首歌曲" else "需要媒体访问权限") },
                        leadingContent = { Icon(Icons.Default.MusicNote, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.clickable(onClick = onOpenLocalLibrary))
                }
                if (category == LibraryCategory.Albums) items(uiState.albums, key = { "album:${it.id}" }) { album ->
                    ListItem(headlineContent = { Text(album.title, maxLines = 1) }, supportingContent = { Text(album.artist, maxLines = 1) },
                        leadingContent = { FlowtoneArtwork(artworkUri = album.artworkUri, modifier = Modifier.size(48.dp)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.clickable { onOpenAlbum(album.id) })
                }
                if (category == LibraryCategory.Artists) items(artists, key = { "artist:$it" }) { artist ->
                    ListItem(headlineContent = { Text(artist) }, leadingContent = { Icon(Icons.Default.Person, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.clickable { onOpenArtist(artist) })
                }
            }
            provider != null -> {
                val providerPlaylists = uiState.providerUserPlaylists[provider].orEmpty()
                val providerAlbums = uiState.providerAlbums[provider].orEmpty()
                val providerSongs = uiState.providerSongs[provider].orEmpty()
                val loadState = when (category) {
                    LibraryCategory.Playlists -> uiState.providerUserPlaylistLoadStates[provider]
                    LibraryCategory.Albums -> uiState.providerAlbumCatalogLoadStates[provider]
                    LibraryCategory.Songs -> uiState.providerSongCatalogLoadStates[provider]
                    else -> null
                }
                val loading = "extension:$provider" in uiState.libraryRefreshingSources || loadState == ProviderCatalogLoadState.Loading
                val failed = loadState == ProviderCatalogLoadState.Failed
                if (category == LibraryCategory.Playlists && providerPlaylists.isNotEmpty()) item(key = "provider-playlist-group-gap") { Spacer(Modifier.height(16.dp)) }
                if (category == LibraryCategory.Playlists) itemsIndexed(providerPlaylists, key = { _, playlist -> "provider-playlist:${playlist.identity.stableKey}" }) { index, playlist ->
                    val groupShape = playlistGroupRowShape(index, providerPlaylists.size)
                    Box(Modifier.animateItem(
                        fadeInSpec = tween(FlowtoneMotion.ShortDurationMillis), placementSpec = null,
                        fadeOutSpec = tween(FlowtoneMotion.ShortDurationMillis)
                    ).then(pageTransition.elementAppearanceModifierAt(pageTransition.progress, index.coerceAtMost(23)))
                        .fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(groupShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.20f))
                        .clickable { onOpenProviderPlaylist(ProviderPlaylistSearchItem(playlist.identity, playlist.title, playlist.creator, playlist.artwork, playlist.metadata)) }
                        .padding(top = if (index == 0) 4.dp else 0.dp,
                            bottom = if (index == providerPlaylists.lastIndex) 4.dp else 0.dp)) {
                    LibraryProviderPlaylistListItem(playlist = playlist, itemHeight = 72.dp, flowCloudSpeed = flowCloudSpeed, isFlowCloudPlaying = false,
                        onClick = { onOpenProviderPlaylist(ProviderPlaylistSearchItem(playlist.identity, playlist.title, playlist.creator, playlist.artwork, playlist.metadata)) },
                        clickModifier = Modifier,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    }
                }
                if (category == LibraryCategory.Albums) items(providerAlbums, key = { "provider-album:${it.identity.stableKey}" }) { album ->
                    ListItem(headlineContent = { Text(album.title, maxLines = 1) }, supportingContent = { Text(album.artist, maxLines = 1) },
                        leadingContent = { FlowtoneArtwork(artworkUri = null, extensionArtwork = album.artwork, modifier = Modifier.size(48.dp)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.clickable { onOpenProviderAlbum(album) })
                }
                if (category == LibraryCategory.Songs) itemsIndexed(providerSongs, key = { _, song -> "provider-song:${song.identity.stableKey}" }) { index, song ->
                    SongListItem(song = song.toPresentationSong(), isCurrentSong = false, compact = true,
                        extensionArtwork = song.artwork,
                        onClick = { onProviderSongQueueClick(providerSongs, index, PlaybackSource.providerCatalog(provider, selectedSource.name)) },
                        modifier = Modifier.padding(horizontal = 12.dp))
                }
                val activeEmpty = when (category) {
                    LibraryCategory.Playlists -> providerPlaylists.isEmpty()
                    LibraryCategory.Albums -> providerAlbums.isEmpty()
                    LibraryCategory.Songs -> providerSongs.isEmpty()
                    else -> false
                }
                if (category == LibraryCategory.Favorites) item(key = "favorites-unavailable") {
                    Text("收藏功能暂不可用", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (activeEmpty && !failed) item(key = "provider-empty") {
                    Text(if (loading) "正在加载来源内容" else "此来源暂无可浏览内容",
                        modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (category != LibraryCategory.Favorites && failed && !loading) item(key = "provider-error") {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text("部分来源内容加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onRefreshLibrary("extension:$provider") }) { Text("重试") }
                    }
                }
            }
            else -> item(key = "source-loading") { Text("正在加载来源", modifier = Modifier.padding(24.dp)) }
        }
    }
    }
}

private fun playlistGroupRowShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 22.dp else 0.dp
    val bottom = if (index == count - 1) 22.dp else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
