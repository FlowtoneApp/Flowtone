package ink.tenqui.flowtone.ui.search

import android.os.Build

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import ink.tenqui.flowtone.data.search.ProviderSearchCategoryState
import ink.tenqui.flowtone.data.search.providerCategoryState
import ink.tenqui.flowtone.data.search.SearchArtist
import ink.tenqui.flowtone.data.search.SearchResult
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.pageElementKeyDiff
import ink.tenqui.flowtone.ui.components.rememberPageElementExitScope
import ink.tenqui.flowtone.ui.components.rememberPageElementEnterScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.SearchBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.searchCloudPalette
import ink.tenqui.flowtone.ui.components.topLevelPageBackground
import ink.tenqui.flowtone.ui.library.ExperimentalArtistAvatarImage
import ink.tenqui.flowtone.ui.library.rememberExperimentalArtistAvatarImage
import kotlinx.coroutines.flow.distinctUntilChanged
import ink.tenqui.flowtone.R
import androidx.compose.ui.res.stringResource

@Composable
internal fun GlobalSearchContent(
    searchUiState: GlobalSearchUiState,
    currentSong: Song?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    pendingTrackIdentityKey: String? = null,
    onArtistClick: (SearchArtist) -> Unit,
    onProviderArtistClick: (ProviderSong) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onExitSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onCategoryChange: (ProviderSearchCategory) -> Unit,
    onLoadMore: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    interactionsEnabled: Boolean,
    reentryProgress: Float,
    pageTransition: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val retainedInterfaces = listOf(listState, pendingTrackIdentityKey)
    val transitionElementCount = 6
    var sourceSwitcherState by remember { mutableStateOf<SearchSourceSwitcherState>(SearchSourceSwitcherState.Collapsed) }
    val selectedResultCategory = SearchResultCategory.from(searchUiState.selectedProviderCategory)
    val sourceSwitcherExpanded = sourceSwitcherState !is SearchSourceSwitcherState.Collapsed
    val sourceFocusProgress by animateFloatAsState(
        targetValue = if (sourceSwitcherExpanded) 1f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "SearchSourceFocusProgress"
    )
    val sourceContentBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(14.dp * sourceFocusProgress)
    } else {
        Modifier
    }
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
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(sourceContentBlurModifier)
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pageTransition.backgroundModifier())
                .topLevelPageBackground(
                    cloudPalette = searchCloudPalette(),
                    cloudPlacement = SearchBackgroundCloudPlacement
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .height(FlowtoneTopBarContentHeight)
                .fillMaxWidth()
                .zIndex(2f)
                .then(pageTransition.elementModifier(0, transitionElementCount))
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
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .then(pageTransition.elementModifier(1, transitionElementCount))
            )
            SearchResultCategorySelector(
                selectedCategory = selectedResultCategory,
                onCategorySelected = { onCategoryChange(it.providerCategory) },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .then(pageTransition.elementModifier(2, transitionElementCount))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    )
                    .then(pageTransition.elementModifier(3, transitionElementCount))
            )
            AnimatedContent(
                targetState = searchUiState.query.isBlank,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                label = "SearchLandingContent",
                modifier = Modifier.then(
                    pageTransition.elementModifier(4, transitionElementCount)
                )
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
                        onProviderArtistClick = onProviderArtistClick,
                        onAlbumClick = onAlbumClick,
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
        }
        if (sourceSwitcherExpanded || sourceFocusProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(
                        Color.Black.copy(
                            alpha = sourceFocusProgress *
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.12f else 0.18f
                        )
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        sourceSwitcherState = SearchSourceSwitcherState.Collapsed
                    }
            )
        }
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
                .then(pageTransition.elementModifier(5, transitionElementCount))
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

private data class SearchResultEntrySessionKey(
    val generation: Long,
    val category: SearchResultCategory,
    val scope: SearchScope
)

internal data class SearchResultPresentationIdentityUpdate(
    val currentId: Long,
    val outgoingId: Long?,
    val lastAssignedId: Long
)

internal fun searchResultPresentationIdentityUpdate(
    currentId: Long,
    lastAssignedId: Long,
    hasExitingElements: Boolean
): SearchResultPresentationIdentityUpdate {
    if (!hasExitingElements) {
        return SearchResultPresentationIdentityUpdate(
            currentId = currentId,
            outgoingId = null,
            lastAssignedId = lastAssignedId
        )
    }
    val nextId = lastAssignedId + 1
    return SearchResultPresentationIdentityUpdate(
        currentId = nextId,
        outgoingId = currentId,
        lastAssignedId = nextId
    )
}

@Composable
private fun visibleSearchResultKeys(
    listState: androidx.compose.foundation.lazy.LazyListState,
    resultEntryKeys: List<Any>
): List<Any> {
    val resultKeySet = remember(resultEntryKeys) { resultEntryKeys.toSet() }
    val visibleKeys by remember(listState, resultKeySet) {
        derivedStateOf {
            visibleSearchResultKeysFromLayout(listState, resultKeySet)
        }
    }
    return visibleKeys
}

private fun visibleSearchResultKeysFromLayout(
    listState: androidx.compose.foundation.lazy.LazyListState,
    resultKeySet: Set<Any>
): List<Any> = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
    item.key.takeIf(resultKeySet::contains)
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
    onProviderArtistClick: (ProviderSong) -> Unit,
    onAlbumClick: (Long) -> Unit,
    category: SearchResultCategory,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localSongs = if (category == SearchResultCategory.Single) state.songResults else emptyList()
    val localArtists = if (category == SearchResultCategory.User) state.artistResults else emptyList()
    val localAlbums = if (category == SearchResultCategory.Album) state.albumResults else emptyList()
    val categoryState = state.providerCategoryState(category.providerCategory)
    val currentSnapshot = remember(
        state.searchGeneration,
        state.scope,
        category,
        localSongs,
        localArtists,
        localAlbums,
        categoryState.items
    ) {
        val sourceSections = searchResultSourceSections(
            scope = state.scope,
            hasLocalResults = localSongs.isNotEmpty() || localArtists.isNotEmpty() || localAlbums.isNotEmpty(),
            hasOnlineResults = categoryState.items.isNotEmpty()
        )
        SearchResultSnapshot(
            sessionKey = SearchResultEntrySessionKey(
                generation = state.searchGeneration,
                category = category,
                scope = state.scope
            ),
            scope = state.scope,
            localSongs = localSongs,
            localArtists = localArtists,
            localAlbums = localAlbums,
            onlineResults = categoryState.items,
            sourceSections = sourceSections,
            elementKeys = searchResultElementKeys(
                sourceSections = sourceSections,
                localSongs = localSongs,
                localArtists = localArtists,
                localAlbums = localAlbums,
                onlineResults = categoryState.items
            )
        )
    }
    var displayedPresentation by remember {
        mutableStateOf(SearchResultPresentation(id = 0L, snapshot = currentSnapshot))
    }
    var outgoingPresentation by remember { mutableStateOf<SearchResultOutgoingPresentation?>(null) }
    var lastAssignedPresentationId by remember { mutableStateOf(0L) }
    var retainedKeysForCurrentSession by remember { mutableStateOf<Set<Any>>(emptySet()) }

    LaunchedEffect(currentSnapshot) {
        val previousPresentation = displayedPresentation
        val previousSnapshot = previousPresentation.snapshot
        val keyDiff = pageElementKeyDiff(previousSnapshot.elementKeys, currentSnapshot.elementKeys)
        val identityUpdate = searchResultPresentationIdentityUpdate(
            currentId = previousPresentation.id,
            lastAssignedId = lastAssignedPresentationId,
            hasExitingElements = keyDiff.exitingKeys.isNotEmpty()
        )
        retainedKeysForCurrentSession = keyDiff.retainedKeys
        if (identityUpdate.outgoingId != null) {
            lastAssignedPresentationId = identityUpdate.lastAssignedId
            outgoingPresentation = SearchResultOutgoingPresentation(
                presentation = previousPresentation,
                exitingKeys = keyDiff.exitingKeys.toSet(),
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                viewportKeys = visibleSearchResultKeysFromLayout(
                    listState = listState,
                    resultKeySet = previousSnapshot.elementKeys.toSet()
                )
            )
            displayedPresentation = SearchResultPresentation(
                id = identityUpdate.currentId,
                snapshot = currentSnapshot
            )
        } else if (outgoingPresentation?.exitingKeys?.any(currentSnapshot.elementKeys::contains) == true) {
            outgoingPresentation = null
            displayedPresentation = previousPresentation.copy(snapshot = currentSnapshot)
        } else {
            displayedPresentation = previousPresentation.copy(snapshot = currentSnapshot)
        }
    }

    val presentedSnapshot = displayedPresentation.snapshot
    val visibleCurrentKeys = visibleSearchResultKeys(listState, presentedSnapshot.elementKeys)
    val resultEnterScope = rememberPageElementEnterScope(
        sessionKey = presentedSnapshot.sessionKey,
        elementKeys = presentedSnapshot.elementKeys,
        viewportKeys = visibleCurrentKeys,
        awaitViewportKeys = true,
        initiallyEnteredKeys = retainedKeysForCurrentSession
    )
    LaunchedEffect(category, state.searchGeneration, categoryState.nextCursor, categoryState.isLoadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index to info.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisible, totalItems) ->
            if (lastVisible != null && totalItems > 0 && lastVisible >= totalItems - 5) onLoadMore()
        }
    }
    val emptyMessage = searchResultEmptyMessage(
        scope = state.scope,
        queryIsBlank = state.query.isBlank,
        isSearching = state.isSearching,
        providerState = categoryState,
        hasCurrentResults = currentSnapshot.elementKeys.isNotEmpty()
    )
    Box(modifier = modifier.padding(horizontal = 8.dp)) {
        val presentationLayers = buildList {
            add(SearchResultPresentationLayer(displayedPresentation, outgoing = null))
            outgoingPresentation?.let { outgoing ->
                add(SearchResultPresentationLayer(outgoing.presentation, outgoing))
            }
        }
        presentationLayers.forEach { layer ->
            // The old layer keeps the same keyed composition while its role changes from live
            // to outgoing. Lazy item image painters and their loaded/loading presentation are
            // therefore not recreated from the model snapshot at the category boundary.
            androidx.compose.runtime.key(layer.presentation.id) {
                val outgoing = layer.outgoing
                val layerListState = if (outgoing == null) {
                    listState
                } else {
                    rememberLazyListState(
                        initialFirstVisibleItemIndex = outgoing.firstVisibleItemIndex,
                        initialFirstVisibleItemScrollOffset = outgoing.firstVisibleItemScrollOffset
                    )
                }
                val exitScope = if (outgoing == null) {
                    null
                } else {
                    rememberPageElementExitScope(
                        sessionKey = outgoing.presentation.id,
                        elementKeys = outgoing.exitingKeys.toList(),
                        viewportKeys = outgoing.viewportKeys,
                        onFinished = {
                            if (outgoingPresentation?.presentation?.id == outgoing.presentation.id) {
                                outgoingPresentation = null
                            }
                        }
                    )
                }
                SearchResultList(
                    snapshot = layer.presentation.snapshot,
                    currentSong = currentSong,
                    listState = layerListState,
                    onSongClick = onSongClick,
                    onArtistClick = onArtistClick,
                    onOnlineSongClick = onOnlineSongClick,
                    onProviderArtistClick = onProviderArtistClick,
                    onAlbumClick = onAlbumClick,
                    elementModifier = { key ->
                        when {
                            outgoing == null -> resultEnterScope.elementModifier(key)
                            key in outgoing.exitingKeys -> checkNotNull(exitScope).elementModifier(key)
                            else -> checkNotNull(exitScope).hiddenModifier()
                        }
                    },
                    showInitialLoading = outgoing == null && state.scope != SearchScope.All &&
                        (state.isSearching || categoryState.isInitialLoading),
                    showOnlineLoading = outgoing == null && state.scope == SearchScope.All &&
                        categoryState.isInitialLoading && presentedSnapshot.onlineResults.isEmpty(),
                    showLoadingMore = outgoing == null && categoryState.isLoadingMore,
                    error = categoryState.error.takeIf { outgoing == null },
                    nextCursorAvailable = outgoing == null && categoryState.nextCursor != null,
                    emptyMessage = emptyMessage.takeIf { outgoing == null },
                    onLoadMore = onLoadMore,
                    interactive = outgoing == null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (outgoing != null) Modifier.zIndex(1f) else Modifier)
                )
            }
        }
    }
}

private data class SearchResultPresentation(
    val id: Long,
    val snapshot: SearchResultSnapshot
)

private data class SearchResultPresentationLayer(
    val presentation: SearchResultPresentation,
    val outgoing: SearchResultOutgoingPresentation?
)

private data class SearchResultSnapshot(
    val sessionKey: SearchResultEntrySessionKey,
    val scope: SearchScope,
    val localSongs: List<Song>,
    val localArtists: List<SearchArtist>,
    val localAlbums: List<SearchResult.AlbumResult>,
    val onlineResults: List<ProviderSong>,
    val sourceSections: List<SearchResultSourceSection>,
    val elementKeys: List<Any>
)

private data class SearchResultOutgoingPresentation(
    val presentation: SearchResultPresentation,
    val exitingKeys: Set<Any>,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val viewportKeys: List<Any>
)

private fun searchResultElementKeys(
    sourceSections: List<SearchResultSourceSection>,
    localSongs: List<Song>,
    localArtists: List<SearchArtist>,
    localAlbums: List<SearchResult.AlbumResult>,
    onlineResults: List<ProviderSong>
): List<Any> = buildList {
    if (SearchResultSourceSection.Local in sourceSections) add("search-source-local")
    localSongs.forEach { song -> add(localSearchResultItemKey("song", song.uri.toString())) }
    localArtists.forEach { artist -> add(localSearchResultItemKey("artist", artist.id)) }
    localAlbums.forEach { album -> add(localSearchResultItemKey("album", album.albumId.toString())) }
    if (SearchResultSourceSection.Online in sourceSections) add("search-source-online")
    onlineResults.forEach { result ->
        add(
            providerSearchResultItemKey(
                providerId = result.trackRef.extensionId,
                category = result.searchCategory,
                identity = result.trackRef.opaqueId
            )
        )
    }
}

@Composable
private fun SearchResultList(
    snapshot: SearchResultSnapshot,
    currentSong: Song?,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    onArtistClick: (SearchArtist) -> Unit,
    onProviderArtistClick: (ProviderSong) -> Unit,
    onAlbumClick: (Long) -> Unit,
    elementModifier: (Any) -> Modifier,
    showInitialLoading: Boolean = false,
    showOnlineLoading: Boolean = false,
    showLoadingMore: Boolean = false,
    error: String? = null,
    nextCursorAvailable: Boolean = false,
    emptyMessage: String? = null,
    onLoadMore: () -> Unit = {},
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (showInitialLoading) item(key = "search-initial-loading") {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        if (SearchResultSourceSection.Local in snapshot.sourceSections) {
            item(key = "search-source-local") {
                SearchResultsSectionTitle(
                    title = "本地",
                    modifier = elementModifier("search-source-local")
                )
            }
        }
        itemsIndexed(snapshot.localSongs, key = { _, song ->
            localSearchResultItemKey("song", song.uri.toString())
        }) { index, song ->
            SongListItem(
                song = song,
                isCurrentSong = currentSong?.uri == song.uri,
                onClick = if (interactive) {
                    { onSongClick(snapshot.localSongs, index) }
                } else {
                    {}
                },
                modifier = elementModifier(localSearchResultItemKey("song", song.uri.toString()))
            )
        }
        items(snapshot.localArtists, key = { artist -> localSearchResultItemKey("artist", artist.id) }) { artist ->
            LocalSearchArtist(
                artist = artist,
                onClick = if (interactive) ({ onArtistClick(artist) }) else ({}),
                alpha = 1f,
                modifier = elementModifier(localSearchResultItemKey("artist", artist.id))
            )
        }
        items(snapshot.localAlbums, key = { album -> localSearchResultItemKey("album", album.albumId.toString()) }) { album ->
            LocalSearchAlbum(
                album = album,
                onClick = if (interactive) ({ onAlbumClick(album.albumId) }) else ({}),
                modifier = elementModifier(localSearchResultItemKey("album", album.albumId.toString()))
            )
        }
        if (SearchResultSourceSection.Online in snapshot.sourceSections) {
            item(key = "search-source-online") {
                SearchResultsSectionTitle(
                    title = "在线",
                    modifier = elementModifier("search-source-online")
                )
            }
        }
        if (showOnlineLoading) item(key = "search-source-online-loading") {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        items(snapshot.onlineResults, key = { song ->
            providerSearchResultItemKey(
                providerId = song.trackRef.extensionId,
                category = song.searchCategory,
                identity = song.trackRef.opaqueId
            )
        }) { song ->
            OnlineSearchSong(
                song = song,
                alpha = 1f,
                onClick = if (!interactive) {
                    null
                } else if (song.searchCategory == ProviderSearchCategory.Single) {
                    { onOnlineSongClick(song) }
                } else if (song.searchCategory == ProviderSearchCategory.User) {
                    { onProviderArtistClick(song) }
                } else {
                    null
                },
                modifier = elementModifier(
                    providerSearchResultItemKey(
                        providerId = song.trackRef.extensionId,
                        category = song.searchCategory,
                        identity = song.trackRef.opaqueId
                    )
                )
            )
        }
        if (showLoadingMore) item(key = "search-loading-more") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        if (snapshot.onlineResults.isNotEmpty() && error != null && !showLoadingMore && nextCursorAvailable) item(
            key = "search-load-more-error"
        ) {
            Text(
                text = "加载失败，重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(16.dp)
            )
        }
        emptyMessage?.let { message ->
            item(key = "search-empty") { LandingMessage(message) }
        }
    }
}

@Composable
private fun SearchResultsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 4.dp)
    )
}

internal enum class SearchResultSourceSection {
    Local,
    Online
}

internal fun searchResultSourceSections(
    scope: SearchScope,
    hasLocalResults: Boolean,
    hasOnlineResults: Boolean
): List<SearchResultSourceSection> {
    if (scope != SearchScope.All) return emptyList()
    return buildList {
        if (hasLocalResults) add(SearchResultSourceSection.Local)
        if (hasOnlineResults) add(SearchResultSourceSection.Online)
    }
}

/**
 * Provider 结果在请求刚启动时会先清空；此时尚未得到“没有结果”的结论。
 * [ProviderSearchCategoryState.hasLoaded] 仅在当前请求成功返回后置位，因此能避免
 * provider/category 切换时把上一代请求的完成状态误用于当前结果集。
 */
internal fun searchResultEmptyMessage(
    scope: SearchScope,
    queryIsBlank: Boolean,
    isSearching: Boolean,
    providerState: ProviderSearchCategoryState,
    hasCurrentResults: Boolean
): String? {
    if (queryIsBlank || isSearching || providerState.isInitialLoading || hasCurrentResults) {
        return null
    }

    if (providerState.error != null) {
        return providerState.error
    }

    return when (scope) {
        is SearchScope.Provider -> if (providerState.hasLoaded) "没有找到相关内容" else null
        else -> "没有找到相关内容"
    }
}

internal fun localSearchResultItemKey(kind: String, identity: String): String =
    "local:${kind.trim()}:${identity.trim()}"

internal fun providerSearchResultItemKey(
    providerId: String,
    category: ProviderSearchCategory,
    identity: String
): String = "online:${providerId.trim()}:${category.name}:${identity.trim()}"

@Composable
private fun LocalSearchArtist(
    artist: SearchArtist,
    onClick: () -> Unit,
    alpha: Float,
    modifier: Modifier = Modifier
) = Row(
    modifier = Modifier.fillMaxWidth().then(modifier).alpha(alpha).clickable(onClick = onClick).padding(16.dp, 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    val avatarImage = rememberExperimentalArtistAvatarImage(
        songTitle = artist.representativeSongTitle,
        artistName = artist.name
    )
    SearchUserAvatar(image = avatarImage)
    Column(Modifier.padding(start = 12.dp)) {
        Text(artist.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text("本地艺人 · ${artist.songCount} 首歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f))
    }
}

@Composable
private fun OnlineSearchSong(
    song: ProviderSong,
    alpha: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
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
        modifier = Modifier.fillMaxWidth().then(modifier).alpha(alpha)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song.searchCategory == ProviderSearchCategory.User) {
            // Online User 只使用 Provider 返回的 artwork；绝不回退到本地 Artist Avatar 服务。
            SearchUserAvatar(image = song.artwork)
        } else {
            if (song.artwork != null) {
                SearchArtwork(song.artwork, loader)
            } else {
                SearchArtworkPlaceholder(song.searchCategory)
            }
        }
        /*
        }
                    // 位于统一 blur visual 之上、selector 之下：任意空白点击只负责收起。
                    .zIndex(3f)
        */
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
    image: ExtensionImage?
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
            image = image,
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

@Composable
private fun LocalSearchAlbum(
    album: SearchResult.AlbumResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Row(
    modifier = Modifier.fillMaxWidth().then(modifier).clickable(onClick = onClick).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    if (album.artworkUri != null) {
        AsyncImage(
            model = album.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
        )
    } else {
        SearchArtworkPlaceholder(ProviderSearchCategory.Album)
    }
    Column(Modifier.padding(start = 12.dp)) {
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${album.artist} · ${album.songCount} 首歌曲",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchArtworkPlaceholder(category: ProviderSearchCategory) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (category) {
                ProviderSearchCategory.Album -> Icons.Rounded.Album
                ProviderSearchCategory.Playlist -> Icons.Rounded.QueueMusic
                else -> Icons.Rounded.MusicNote
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(26.dp)
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
