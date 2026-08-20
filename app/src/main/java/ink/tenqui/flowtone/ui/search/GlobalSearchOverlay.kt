package ink.tenqui.flowtone.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderSearchLandingState
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderSearchMetadataLabels
import ink.tenqui.flowtone.data.online.formatProviderSearchMetadataLine
import ink.tenqui.flowtone.data.online.SearchLandingAction
import ink.tenqui.flowtone.data.online.SearchLandingBlock
import ink.tenqui.flowtone.data.online.SearchLandingItem
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.providerCategoryState
import ink.tenqui.flowtone.data.search.SearchArtist
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.library.ExperimentalArtistAvatarImage
import ink.tenqui.flowtone.ui.library.rememberExperimentalArtistAvatarImage
import kotlinx.coroutines.flow.distinctUntilChanged
import ink.tenqui.flowtone.R
import androidx.compose.ui.res.stringResource

@Composable
internal fun GlobalSearchOverlay(
    searchUiState: GlobalSearchUiState,
    currentSong: Song?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    pendingTrackIdentityKey: String? = null,
    onArtistClick: (SearchArtist) -> Unit,
    onExitSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onCategoryChange: (ProviderSearchCategory) -> Unit,
    onLoadMore: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    interactionsEnabled: Boolean,
    reentryProgress: Float,
    revealProgress: Float,
    revealColor: Color,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val retainedInterfaces = listOf(listState, pendingTrackIdentityKey)
    val density = LocalDensity.current
    val revealOriginEndPadding = with(density) { 41.dp.toPx() }
    val revealOriginY = with(density) { WindowInsets.statusBars.getTop(this).toFloat() + 28.dp.toPx() }
    var sourceSwitcherState by remember { mutableStateOf<SearchSourceSwitcherState>(SearchSourceSwitcherState.Collapsed) }
    val selectedResultCategory = SearchResultCategory.from(searchUiState.selectedProviderCategory)
    val sourceSwitcherExpanded = sourceSwitcherState !is SearchSourceSwitcherState.Collapsed
    BackHandler(enabled = sourceSwitcherExpanded) {
        sourceSwitcherState = SearchSourceSwitcherState.Collapsed
    }
    val swipeBackModifier = if (interactionsEnabled) {
        Modifier.rightSwipeBackGesture {
            if (sourceSwitcherExpanded) sourceSwitcherState = SearchSourceSwitcherState.Collapsed
            else onExitSearch()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(swipeBackModifier)
            .drawBehind {
                val origin = Offset(size.width - revealOriginEndPadding, revealOriginY)
                drawCircle(revealColor, searchRevealMaxRadius(origin, size.width, size.height) * revealProgress, origin)
            }
            .drawWithContent {
                val contentScope = this
                val origin = Offset(size.width - revealOriginEndPadding, revealOriginY)
                val radius = searchRevealMaxRadius(origin, size.width, size.height) * revealProgress
                val path = Path().apply { addOval(Rect(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius)) }
                clipPath(path) { contentScope.drawContent() }
            }
    ) {
        if (sourceSwitcherExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 位于页面内容之上、selector 之下：任意空白点击只负责收起。
                    .zIndex(3f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        sourceSwitcherState = SearchSourceSwitcherState.Collapsed
                    }
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .height(FlowtoneTopBarContentHeight)
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            IconButton(
                onClick = {
                    if (sourceSwitcherExpanded) sourceSwitcherState = SearchSourceSwitcherState.Collapsed
                    else onExitSearch()
                },
                enabled = interactionsEnabled,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "搜索",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = FlowtoneTopBarTitleStartPadding + 44.dp)
                    .alpha(reentryProgress.coerceIn(0f, 1f))
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = FlowtoneTopBarContentHeight + 20.dp,
                    bottom = bottomContentPadding
                )
                .zIndex(2f)
        ) {
            SearchInput(
                query = searchUiState.queryText,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            SearchResultCategorySelector(
                selectedCategory = selectedResultCategory,
                onCategorySelected = { onCategoryChange(it.providerCategory) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)))
            AnimatedContent(
                targetState = searchUiState.query.isBlank,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                label = "SearchLandingContent"
            ) { isLanding ->
                if (isLanding) {
                    SearchLandingContent(
                        state = searchUiState,
                        onScopeChange = onScopeChange,
                        onSearchAction = onQueryChange,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SearchResultsContent(
                        state = searchUiState,
                        currentSong = currentSong,
                        onSongClick = onSongClick,
                        onOnlineSongClick = onOnlineSongClick,
                        onArtistClick = onArtistClick,
                        category = selectedResultCategory,
                        listState = listState,
                        onLoadMore = onLoadMore,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        // 视觉上位于搜索框 trailing 区域，但作为页面 overlay，不参与搜索框的测量。
        // 这样展开的来源行可以覆盖 Landing，而不会受输入框圆角或高度裁切。
        SearchSourceSwitcher(
            currentScope = searchUiState.scope,
            providers = searchUiState.providerOptions,
            state = sourceSwitcherState,
            onStateChange = { sourceSwitcherState = it },
            onScopeSelected = onScopeChange,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(
                    top = FlowtoneTopBarContentHeight + 26.dp,
                    end = 26.dp
                )
                .zIndex(4f)
        )
    }
}

private enum class SearchResultCategory(val label: String, val providerCategory: ProviderSearchCategory) {
    Single("单曲", ProviderSearchCategory.Single),
    Playlist("歌单", ProviderSearchCategory.Playlist),
    Album("专辑", ProviderSearchCategory.Album),
    User("用户", ProviderSearchCategory.User);

    companion object {
        fun from(category: ProviderSearchCategory): SearchResultCategory =
            entries.first { it.providerCategory == category }
    }
}

@Composable
private fun SearchResultCategorySelector(
    selectedCategory: SearchResultCategory,
    onCategorySelected: (SearchResultCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        SearchResultCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.07f),
                        RoundedCornerShape(99.dp)
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 17.dp, end = 58.dp)
        ) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isBlank()) Text("搜索歌曲、艺人或专辑", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f))
                BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable private fun SearchScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
        modifier = Modifier.background(if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f), RoundedCornerShape(99.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp).clickable(onClick = onClick))
}

@Composable private fun SearchLandingContent(state: GlobalSearchUiState, onScopeChange: (SearchScope) -> Unit, onSearchAction: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        when (val scope = state.scope) {
            SearchScope.All -> {
                LandingSection("搜索来源") {
                    Text("选择一个来源后，可浏览该 Provider 提供的搜索首页。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f))
                    SearchScopeChip("本地音乐", false) { onScopeChange(SearchScope.Local) }
                    state.providerOptions.forEach { option -> SearchScopeChip(option.name, false) { onScopeChange(SearchScope.Provider(option.extensionId)) } }
                }
            }
            SearchScope.Local -> LandingSection("本地音乐") {
                Text("输入关键词即可搜索设备中的歌曲和艺人。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f))
            }
            is SearchScope.Provider -> when (val landing = state.landingState) {
                ProviderSearchLandingState.Idle, ProviderSearchLandingState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                ProviderSearchLandingState.Error -> LandingMessage("此来源暂时无法加载推荐内容")
                is ProviderSearchLandingState.Loaded -> landing.landing?.blocks?.forEach { block -> LandingBlock(block, onSearchAction) } ?: LandingMessage("输入关键词开始搜索")
            }
        }
    }
}

@Composable private fun LandingMessage(message: String) { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f), modifier = Modifier.padding(top = 28.dp)) }
@Composable private fun LandingSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer); content() } }

@Composable private fun LandingBlock(block: SearchLandingBlock, onSearchAction: (String) -> Unit) {
    when (block) {
        is SearchLandingBlock.Chips -> LandingSection(block.title ?: "") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { block.items.forEach { item -> LandingChip(item, onSearchAction) } } }
        is SearchLandingBlock.TileGrid -> LandingSection(block.title ?: "") { block.items.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { item -> LandingTile(item, onSearchAction, Modifier.weight(1f)) }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } }
        is SearchLandingBlock.MediaRow -> LandingSection(block.title ?: "") { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { block.items.forEach { item -> LandingMedia(item, onSearchAction) } } }
        is SearchLandingBlock.Text -> LandingSection(block.title ?: "") { Text(block.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)) }
    }
}

@Composable private fun LandingChip(item: SearchLandingItem, onSearchAction: (String) -> Unit) = Text(item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 8.dp).clickable { (item.action as? SearchLandingAction.Search)?.let { onSearchAction(it.query) } })
@Composable private fun LandingTile(item: SearchLandingItem, onSearchAction: (String) -> Unit, modifier: Modifier = Modifier) = Column(modifier = modifier.height(112.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).clickable { (item.action as? SearchLandingAction.Search)?.let { onSearchAction(it.query) } }.padding(14.dp), verticalArrangement = Arrangement.Bottom) { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer); item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f)) } }
@Composable private fun LandingMedia(item: SearchLandingItem, onSearchAction: (String) -> Unit) { val loader = ExtensionManager.get(LocalContext.current).extensionImageLoader; Column(Modifier.width(116.dp).clickable { (item.action as? SearchLandingAction.Search)?.let { onSearchAction(it.query) } }) { item.artwork?.let { AsyncImage(it, null, imageLoader = loader, contentScale = ContentScale.Crop, modifier = Modifier.size(116.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f), RoundedCornerShape(12.dp))) }; Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 8.dp)); item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f)) } } }

@Composable private fun SearchResultsContent(
    state: GlobalSearchUiState,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    onArtistClick: (SearchArtist) -> Unit,
    category: SearchResultCategory,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localSongs = if (category == SearchResultCategory.Single) state.songResults else emptyList()
    val localArtists = if (category == SearchResultCategory.User) state.artistResults else emptyList()
    val categoryState = state.providerCategoryState(category.providerCategory)
    val onlineResults = categoryState.items
    val isInitialLoading = categoryState.isInitialLoading
    val hasNoResults = !state.query.isBlank && !state.isSearching && !isInitialLoading &&
        localSongs.isEmpty() && localArtists.isEmpty() && onlineResults.isEmpty()
    val resultSnapshot = SearchResultSnapshot(
        localSongs = localSongs,
        localArtists = localArtists,
        onlineResults = onlineResults,
        hasNoResults = hasNoResults,
        isStale = state.isSearching,
        isLoadingMore = categoryState.isLoadingMore,
        error = categoryState.error
    )
    LaunchedEffect(category, state.searchGeneration, categoryState.nextCursor, categoryState.isLoadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index to info.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisible, totalItems) ->
            if (lastVisible != null && totalItems > 0 && lastVisible >= totalItems - 5) onLoadMore()
        }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        if (state.isSearching || isInitialLoading) item {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        itemsIndexed(localSongs, key = { _, song -> "local:${song.uri}" }) { index, song ->
            SongListItem(song, currentSong?.uri == song.uri, onClick = { onSongClick(localSongs, index) })
        }
        items(localArtists, key = { artist -> "artist:${artist.id}" }) { artist ->
            LocalSearchArtist(artist, onClick = { onArtistClick(artist) }, alpha = 1f)
        }
        items(onlineResults, key = { song -> "online:${song.trackRef.extensionId}:${song.trackRef.opaqueId}" }) { song ->
            OnlineSearchSong(song, alpha = 1f, onClick = if (song.searchCategory == ProviderSearchCategory.Single) {
                { onOnlineSongClick(song) }
            } else null)
        }
        if (categoryState.isLoadingMore) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        if (onlineResults.isNotEmpty() && categoryState.error != null &&
            !categoryState.isLoadingMore && categoryState.nextCursor != null
        ) item {
            Text(
                text = "加载失败，重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(16.dp)
            )
        }
        if (hasNoResults) item { LandingMessage(categoryState.error ?: "没有找到相关内容") }
    }
}

private data class SearchResultSnapshot(
    val localSongs: List<Song>,
    val localArtists: List<SearchArtist>,
    val onlineResults: List<ProviderSong>,
    val hasNoResults: Boolean,
    val isStale: Boolean,
    val isLoadingMore: Boolean,
    val error: String?
)

@Composable
private fun SearchResultRows(
    snapshot: SearchResultSnapshot,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    onArtistClick: (SearchArtist) -> Unit,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    val staleAlpha = if (snapshot.isStale) 0.48f else 1f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        snapshot.localSongs.forEachIndexed { index, song ->
            SongListItem(
                song,
                currentSong?.uri == song.uri,
                onClick = { onSongClick(snapshot.localSongs, index) },
                modifier = Modifier.alpha(staleAlpha)
            )
        }
        snapshot.localArtists.forEach { artist ->
            LocalSearchArtist(artist, onClick = { onArtistClick(artist) }, alpha = staleAlpha)
        }
        snapshot.onlineResults.forEach { song ->
            OnlineSearchSong(
                song,
                alpha = staleAlpha,
                onClick = if (song.searchCategory == ProviderSearchCategory.Single) {
                    { onOnlineSongClick(song) }
                } else {
                    null
                }
            )
        }
        if (isLoadingMore) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        if (snapshot.hasNoResults) LandingMessage("没有找到相关内容")
    }
}
@Composable
private fun LocalSearchArtist(artist: SearchArtist, onClick: () -> Unit, alpha: Float) = Row(
    modifier = Modifier.fillMaxWidth().alpha(alpha).clickable(onClick = onClick).padding(16.dp, 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    val avatarImage = rememberExperimentalArtistAvatarImage(
        songTitle = artist.representativeSongTitle,
        artistName = artist.name
    )
    SearchUserAvatar(avatarImage = avatarImage)
    Column(Modifier.padding(start = 12.dp)) {
        Text(artist.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text("本地艺人 · ${artist.songCount} 首歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f))
    }
}

@Composable
private fun OnlineSearchSong(song: ProviderSong, alpha: Float, onClick: (() -> Unit)?) {
    val loader = ExtensionManager.get(LocalContext.current).extensionImageLoader
    val metadataLabels = ProviderSearchMetadataLabels(
        trackCountSuffix = stringResource(R.string.provider_metadata_track_count_suffix),
        playCountSuffix = stringResource(R.string.provider_metadata_play_count_suffix)
    )
    val secondaryText = if (song.searchCategory == ProviderSearchCategory.Playlist) {
        when {
            song.metadata == null -> song.artist
            else -> formatProviderSearchMetadataLine(song.metadata, metadataLabels)
        }
    } else {
        song.artist
    }
    Row(
        modifier = Modifier.fillMaxWidth().alpha(alpha).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song.searchCategory == ProviderSearchCategory.User) {
            SearchUserAvatar(
                avatarImage = rememberExperimentalArtistAvatarImage(song.title, song.artist),
                fallbackImage = song.artwork
            )
        } else {
            song.artwork?.let { artwork ->
                SearchArtwork(artwork, loader)
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            secondaryText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchUserAvatar(
    avatarImage: ExtensionImage?,
    fallbackImage: ExtensionImage? = null
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f),
            modifier = Modifier.size(26.dp)
        )
        ExperimentalArtistAvatarImage(
            image = avatarImage ?: fallbackImage,
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

@Composable
private fun SearchArtwork(artwork: ExtensionImage, loader: coil3.ImageLoader) {
    var imageLoaded by remember(artwork) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(280, easing = FlowtoneMotion.Easing),
        label = "SearchArtworkFade"
    )
    AsyncImage(
        artwork,
        null,
        imageLoader = loader,
        contentScale = ContentScale.Crop,
        onSuccess = { imageLoaded = true },
        onError = { imageLoaded = false },
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            .alpha(imageAlpha)
    )
}
private fun searchRevealMaxRadius(origin: Offset, width: Float, height: Float): Float = listOf(origin.getDistance(), Offset(width, 0f).minus(origin).getDistance(), Offset(0f, height).minus(origin).getDistance(), Offset(width, height).minus(origin).getDistance()).maxOrNull() ?: 0f
