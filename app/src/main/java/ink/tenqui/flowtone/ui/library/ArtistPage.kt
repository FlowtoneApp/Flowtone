package ink.tenqui.flowtone.ui.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.providerAlbumsForArtist
import ink.tenqui.flowtone.data.online.providerSongsForArtist
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionArtworkCard
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionCardWidth
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionTrailingActionCard
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.SongListItemSkeleton
import ink.tenqui.flowtone.ui.components.StandardSongListItemSpacing
import ink.tenqui.flowtone.ui.components.rememberArtworkBackgroundColor
import ink.tenqui.flowtone.ui.components.rememberHorizontalCardPageMotion
import ink.tenqui.flowtone.ui.components.rememberPageElementEnterScope
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.player.localSongsForArtist
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val ArtistInfoCardShape = RoundedCornerShape(
    topStart = 12.dp,
    topEnd = 12.dp,
    bottomEnd = 8.dp,
    bottomStart = 8.dp
)
private val ArtistInfoCardSectionGap = 8.dp
private val ArtistSectionHeaderTopSpacing = 12.dp
private val ArtistSectionHeaderBottomSpacing = 8.dp
private const val ArtistFirstSongListItemIndex = 2
private const val ArtistLazyAheadViewportFraction = 0.75f
private const val ArtistLazyBehindViewportFraction = 0.25f
private const val ArtistHeroMotionOrderCount = 7
private const val ArtistAlbumsMoreCardKey = "artist-albums-more"

internal enum class ArtistHeroElement(val order: Int) {
    Background(0),
    CardSurface(1),
    Avatar(2),
    Name(3),
    Metadata(4),
    Biography(5),
    Content(6)
}

internal fun artistBannerDisplayMemoryCacheKey(banner: ExtensionImage): String =
    "artist-hero-banner:${banner.extensionId}:${banner.url}"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistPage(
    entryKey: String,
    scrollStateOwner: ArtistScrollStateOwner,
    heroStateOwner: ArtistHeroStateOwner,
    artistTopBarStateOwner: ArtistTopBarStateOwner,
    artistTopBarOcclusionProgress: Float,
    artistName: String,
    hasLocalContent: Boolean,
    providedAvatar: ExtensionImage?,
    providedMetadata: ink.tenqui.flowtone.core.online.ArtistMetadata?,
    allSongs: List<Song>,
    albums: List<LocalAlbum>,
    providerId: String? = null,
    providerArtistId: String? = null,
    providerSongs: List<ProviderSong> = emptyList(),
    providerAlbums: List<ProviderAlbum> = emptyList(),
    providerSongsLoaded: Boolean = true,
    songOrderTitle: String? = null,
    currentSong: Song?,
    onNavigateBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onProviderSongClick: (List<ProviderSong>, Int) -> Unit = { _, _ -> },
    onOpenAlbum: (Long) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit = {},
    onOpenAllSongs: () -> Unit,
    onOpenAllAlbums: () -> Unit,
    pageTransition: PageTransitionScope,
    socialStats: ArtistSocialStats = ArtistSocialStats(),
    modifier: Modifier = Modifier
) {
    val displayArtist = artistName.trim()
    val cacheWindow = remember {
        LazyLayoutCacheWindow(
            aheadFraction = ArtistLazyAheadViewportFraction,
            behindFraction = ArtistLazyBehindViewportFraction
        )
    }
    val initialScrollPosition = scrollStateOwner.position
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.firstVisibleItemScrollOffset,
        cacheWindow = cacheWindow
    )
    val initialAlbumsPreviewPosition = scrollStateOwner.albumsPreviewPosition
    val albumPreviewState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialAlbumsPreviewPosition.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset =
            initialAlbumsPreviewPosition.firstVisibleItemScrollOffset,
        cacheWindow = cacheWindow
    )
    val artistSongs = remember(displayArtist, allSongs, hasLocalContent) {
        if (hasLocalContent) localSongsForArtist(allSongs, displayArtist) else emptyList()
    }
    val artistAlbums = remember(displayArtist, albums, hasLocalContent) {
        if (hasLocalContent) artistAlbumsFor(albums, displayArtist) else emptyList()
    }
    val artistProviderSongs = remember(
        providerId,
        providerArtistId,
        displayArtist,
        providerSongs,
        hasLocalContent
    ) {
        if (!hasLocalContent && providerId != null && providerArtistId != null) {
            providerSongsForArtist(providerSongs, providerId, providerArtistId, displayArtist)
        } else {
            emptyList()
        }
    }
    val artistProviderAlbums = remember(
        providerId,
        providerArtistId,
        displayArtist,
        providerAlbums,
        hasLocalContent
    ) {
        if (!hasLocalContent && providerId != null && providerArtistId != null) {
            providerAlbumsForArtist(providerAlbums, providerId, providerArtistId, displayArtist)
        } else {
            emptyList()
        }
    }
    val presentedArtistSongs = remember(artistSongs, artistProviderSongs, hasLocalContent) {
        if (hasLocalContent) artistSongs else artistProviderSongs.map(ProviderSong::toPresentationSong)
    }
    val primaryContentPresentation = artistPrimaryContentPresentation(
        hasLocalContent = hasLocalContent,
        providerSongsLoaded = providerSongsLoaded,
        hasSongs = presentedArtistSongs.isNotEmpty()
    )

    LaunchedEffect(entryKey, listState, scrollStateOwner) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            scrollStateOwner.update(index, offset)
        }
    }
    LaunchedEffect(entryKey, albumPreviewState, scrollStateOwner) {
        snapshotFlow {
            albumPreviewState.firstVisibleItemIndex to
                albumPreviewState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            scrollStateOwner.updateAlbumsPreview(index, offset)
        }
    }

    val artistMetadata = rememberArtistMetadata(
        artistName = displayArtist,
        providedMetadata = providedMetadata?.takeUnless { hasLocalContent }
    )
    val biography = artistMetadata?.biography?.trim()?.takeIf(String::isNotEmpty)
    val statistics = remember(
        hasLocalContent,
        artistSongs.size,
        artistAlbums.size,
        artistMetadata?.songCount,
        artistMetadata?.albumCount
    ) {
        if (hasLocalContent) {
            artistStatisticsText(artistSongs.size, artistAlbums.size)
        } else {
            artistMetadataStatisticsText(artistMetadata?.songCount, artistMetadata?.albumCount)
        }
    }
    val contentVisibility = remember(
        hasLocalContent,
        artistProviderSongs,
        artistAlbums,
        artistProviderAlbums,
        primaryContentPresentation,
        statistics
    ) {
        artistPageContentVisibility(
            hasLocalContent = hasLocalContent,
            hasSongs = artistProviderSongs.isNotEmpty(),
            songsLoading = primaryContentPresentation == ArtistPrimaryContentPresentation.Loading,
            hasAlbums = artistAlbums.isNotEmpty() || artistProviderAlbums.isNotEmpty(),
            hasStatistics = statistics != null
        )
    }
    val songCount = artistMetadata?.songCount ?: presentedArtistSongs.size
    val albumCount = artistMetadata?.albumCount
        ?: if (hasLocalContent) artistAlbums.size else artistProviderAlbums.size

    val artistSongKeys = remember(presentedArtistSongs, artistProviderSongs, hasLocalContent) {
        if (hasLocalContent) {
            presentedArtistSongs.mapIndexed(::artistSongItemKey)
        } else {
            artistProviderSongs.map { song -> "provider-song:${song.identity.stableKey}" }
        }
    }
    val previewSongs = remember(presentedArtistSongs) {
        artistSongPreview(presentedArtistSongs)
    }
    val previewProviderSongs = remember(artistProviderSongs) {
        artistSongPreview(artistProviderSongs)
    }
    val previewSongKeys = remember(artistSongKeys) {
        artistSongPreview(artistSongKeys)
    }
    val previewLocalAlbums = remember(artistAlbums) { artistAlbumPreview(artistAlbums) }
    val previewProviderAlbums = remember(artistProviderAlbums) {
        artistAlbumPreview(artistProviderAlbums)
    }
    val hasMoreAlbums = artistAlbumPreviewHasTrailingAction(
        artistAlbums.size + artistProviderAlbums.size
    )
    val albumPreviewMotion = rememberHorizontalCardPageMotion(
        sessionKey = "$entryKey:albums-preview",
        listState = albumPreviewState,
        pageTransition = pageTransition
    )
    val songsSectionTitle = remember(songOrderTitle) {
        artistSongsSectionTitle(songOrderTitle)
    }
    val realSongPresentationKeys = remember(primaryContentPresentation, previewSongKeys) {
        if (primaryContentPresentation == ArtistPrimaryContentPresentation.Ready) {
            previewSongKeys
        } else {
            emptyList()
        }
    }
    val avatarLookupSongTitle = remember(artistSongs, currentSong) {
        val currentArtistSong = currentSong?.takeIf { playingSong ->
            artistSongs.any { artistSong ->
                artistSong.id == playingSong.id || artistSong.uri == playingSong.uri
            }
        }
        (currentArtistSong ?: artistSongs.firstOrNull())?.title.orEmpty()
    }
    val resolvedLocalAvatar = if (hasLocalContent) {
        rememberExperimentalArtistAvatarImage(
            songTitle = avatarLookupSongTitle,
            artistName = displayArtist
        )
    } else {
        null
    }
    val artistAvatarImage = providedAvatar ?: resolvedLocalAvatar
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDarkTheme = isSystemInDarkTheme()
    val banner = artistMetadata?.banner
    val backgroundKind = if (banner == null) {
        ArtistHeroBackgroundKind.Cloud
    } else {
        ArtistHeroBackgroundKind.Banner
    }
    val extensionImageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    val bannerDisplayCacheKey = remember(banner) {
        banner?.let(::artistBannerDisplayMemoryCacheKey)
    }
    val cachedBannerImage = remember(bannerDisplayCacheKey, extensionImageLoader) {
        bannerDisplayCacheKey?.let { cacheKey ->
            extensionImageLoader.memoryCache?.get(MemoryCache.Key(cacheKey))?.image
        }
    }
    val bannerImageRequest = remember(
        banner,
        bannerDisplayCacheKey,
        cachedBannerImage,
        context
    ) {
        if (banner == null || bannerDisplayCacheKey == null) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(banner)
                .memoryCacheKey(bannerDisplayCacheKey)
                .placeholderMemoryCacheKey(bannerDisplayCacheKey)
                .apply {
                    cachedBannerImage?.let { cachedImage ->
                        placeholder(cachedImage)
                        error(cachedImage)
                    }
                }
                .crossfade(false)
                .build()
        }
    }
    val paletteArtworkData = artistTintArtworkData(
        banner = banner,
        avatar = artistAvatarImage,
        localArtwork = artistSongs.firstOrNull()?.artworkUri,
        providerArtwork = artistProviderSongs.firstOrNull()?.artwork
    )
    val resolvedArtistColor = rememberArtworkBackgroundColor(
        artworkData = paletteArtworkData,
        imageLoader = if (paletteArtworkData is ExtensionImage) {
            extensionImageLoader
        } else {
            context.imageLoader
        },
        fallbackColor = MaterialTheme.colorScheme.primaryContainer,
        isDarkTheme = isDarkTheme
    ) ?: MaterialTheme.colorScheme.primaryContainer
    val artistColor by animateColorAsState(
        targetValue = resolvedArtistColor,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistHeroCloudColor"
    )
    SideEffect {
        heroStateOwner.updatePresentation(
            avatar = artistAvatarImage,
            backgroundKind = backgroundKind,
            cloudColor = artistColor
        )
    }
    val heroFocused = heroStateOwner.focusRequested
    val focusProgress by animateFloatAsState(
        targetValue = if (heroFocused) 1f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistBiographyFocusProgress"
    )
    val focusPresentationActive = heroFocused || focusProgress > 0.001f
    val dismissBiography = remember(heroStateOwner) {
        { heroStateOwner.focusRequested = false }
    }
    BackHandler(enabled = heroFocused, onBack = dismissBiography)

    val visibleSongKeys by remember(listState, realSongPresentationKeys) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                realSongPresentationKeys.getOrNull(
                    item.index - ArtistFirstSongListItemIndex
                )
            }.distinct().ifEmpty {
                realSongPresentationKeys.take(ArtistLoadingSkeletonCount)
            }
        }
    }
    val initiallyReadySongKeys = remember(entryKey) {
        if (primaryContentPresentation == ArtistPrimaryContentPresentation.Ready) {
            realSongPresentationKeys.toSet()
        } else {
            emptySet()
        }
    }
    val readySongEnterScope = rememberPageElementEnterScope(
        sessionKey = "$entryKey:ready-songs",
        elementKeys = if (
            pageTransition.phase == PageTransitionPhase.Current &&
            primaryContentPresentation == ArtistPrimaryContentPresentation.Ready
        ) {
            realSongPresentationKeys
        } else {
            emptyList()
        },
        viewportKeys = visibleSongKeys,
        awaitViewportKeys = true,
        initiallyEnteredKeys = initiallyReadySongKeys,
        durationMillis = FlowtoneMotion.ShortDurationMillis
    )
    if (
        pageTransition.phase != PageTransitionPhase.Current &&
        primaryContentPresentation == ArtistPrimaryContentPresentation.Ready
    ) {
        SideEffect { readySongEnterScope.markEntered(realSongPresentationKeys) }
    }
    var frozenTransitionId by remember(entryKey) { mutableStateOf<Int?>(null) }
    var frozenViewportKeys by remember(entryKey) {
        mutableStateOf<List<String>>(emptyList())
    }
    var capturedPageProgress by remember(entryKey) { mutableStateOf(0f) }
    LaunchedEffect(
        pageTransition.transitionId,
        pageTransition.phase,
        visibleSongKeys
    ) {
        if (pageTransition.phase == PageTransitionPhase.Current) {
            frozenTransitionId = null
            frozenViewportKeys = emptyList()
            capturedPageProgress = 0f
        } else if (
            frozenTransitionId != pageTransition.transitionId &&
            visibleSongKeys.isNotEmpty()
        ) {
            frozenTransitionId = pageTransition.transitionId
            frozenViewportKeys = visibleSongKeys
            capturedPageProgress = pageTransition.progress.coerceIn(0f, 1f)
        }
    }
    val animationGroupKeys = if (pageTransition.phase == PageTransitionPhase.Current) {
        visibleSongKeys
    } else if (pageTransition.phase == PageTransitionPhase.Incoming) {
        frozenViewportKeys
    } else {
        frozenViewportKeys.ifEmpty { visibleSongKeys }
    }
    val listProgress = when {
        pageTransition.phase != PageTransitionPhase.Incoming -> pageTransition.progress
        frozenViewportKeys.isEmpty() -> 0f
        else -> {
            val remaining = (1f - capturedPageProgress).coerceAtLeast(0.0001f)
            ((pageTransition.progress - capturedPageProgress) / remaining).coerceIn(0f, 1f)
        }
    }
    val enterGroupReady = pageTransition.phase != PageTransitionPhase.Incoming ||
        frozenViewportKeys.isNotEmpty()

    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val artistTopBarHeight = statusBarTop + FlowtoneTopBarContentHeight
    val artistTopBarHeightPx = with(density) { artistTopBarHeight.toPx() }
    var measuredHeroHeightPx by remember(entryKey) { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .rightSwipeBackGesture {
                if (heroFocused) dismissBiography() else onNavigateBack()
            }
    ) {
        val heroGeometry = artistHeroGeometry(maxWidth)
        if (artistCloudVisible(backgroundKind)) {
            ArtistCloudBackground(accentColor = artistColor)
        }
        LazyColumn(
            state = listState,
            userScrollEnabled = !focusPresentationActive,
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(StandardSongListItemSpacing),
            modifier = Modifier
                .fillMaxSize()
                .artistTopBarContentOcclusion(
                    topBarHeight = artistTopBarHeight,
                    progress = artistTopBarOcclusionProgress
                )
                .then(
                    if (focusPresentationActive) {
                        Modifier
                            .graphicsLayer { alpha = 1f - 0.18f * focusProgress }
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(14.dp * focusProgress)
                                } else {
                                    Modifier
                                }
                            )
                    } else {
                        Modifier
                    }
                )
        ) {
            item(key = "artist-hero") {
                ArtistHero(
                    backgroundKind = backgroundKind,
                    bannerImageRequest = bannerImageRequest,
                    extensionImageLoader = extensionImageLoader,
                    bannerFallbackColor = artistColor,
                    accentColor = artistColor,
                    geometry = heroGeometry,
                    artistName = displayArtist,
                    avatarImage = artistAvatarImage,
                    socialStats = socialStats,
                    statistics = statistics,
                    biography = biography,
                    pageTransition = pageTransition,
                    onExpandBiography = { heroStateOwner.focusRequested = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { measuredHeroHeightPx = it.height }
                )
            }
            if (contentVisibility.showSongs) {
                item(key = "artist-songs-header") {
                    ArtistPreviewSectionHeader(
                        title = songsSectionTitle,
                        actionTitle = "全部歌曲".takeIf {
                            presentedArtistSongs.size > ArtistSongPreviewLimit
                        },
                        onActionClick = onOpenAllSongs,
                        modifier = pageTransition.elementModifier(
                            order = ArtistHeroElement.Content.order,
                            orderCount = ArtistHeroMotionOrderCount
                        ).padding(
                            start = 20.dp,
                            top = ArtistSectionHeaderTopSpacing,
                            end = 20.dp,
                            bottom = ArtistSectionHeaderBottomSpacing
                        )
                    )
                }
                when (primaryContentPresentation) {
                    ArtistPrimaryContentPresentation.Loading -> items(
                        items = artistLoadingSkeletonKeys(),
                        key = { skeletonKey -> skeletonKey }
                    ) { skeletonKey ->
                        val index = skeletonKey.substringAfterLast('-').toIntOrNull() ?: 0
                        SongListItemSkeleton(
                            modifier = pageTransition.elementModifierAt(
                                pageProgress = pageTransition.progress,
                                order = index,
                                orderCount = ArtistLoadingSkeletonCount
                            ).padding(horizontal = 8.dp)
                        )
                    }

                    ArtistPrimaryContentPresentation.Empty -> item(key = "artist-empty") {
                        Text(
                            text = "没有找到该艺术家的歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        )
                    }

                    ArtistPrimaryContentPresentation.Ready -> itemsIndexed(
                        items = previewSongs,
                        key = { index, _ -> previewSongKeys[index] }
                    ) { index, song ->
                        val songKey = previewSongKeys[index]
                        val animationOrder = songListAnimationOrder(
                            songKey = songKey,
                            animationGroupKeys = animationGroupKeys
                        )
                        SongListItem(
                            song = song,
                            isCurrentSong = currentSong?.id == song.id ||
                                currentSong?.uri == song.uri,
                            onClick = {
                                if (hasLocalContent) onSongClick(artistSongs, index)
                                else onProviderSongClick(artistProviderSongs, index)
                            },
                            extensionArtwork = previewProviderSongs.getOrNull(index)?.artwork,
                            modifier = if (pageTransition.phase == PageTransitionPhase.Current) {
                                readySongEnterScope.elementMotionModifier(songKey)
                            } else if (enterGroupReady) {
                                pageTransition.elementModifierAt(
                                    pageProgress = listProgress,
                                    order = animationOrder.order,
                                    orderCount = animationOrder.orderCount
                                )
                            } else {
                                Modifier.graphicsLayer { alpha = 0f }
                            }.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            if (contentVisibility.showAlbums) {
                item(key = "artist-albums-preview") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArtistPreviewSectionHeader(
                            title = "专辑",
                            modifier = pageTransition.elementModifier(
                                order = ArtistHeroElement.Content.order,
                                orderCount = ArtistHeroMotionOrderCount
                            ).padding(
                                start = 20.dp,
                                top = ArtistSectionHeaderTopSpacing,
                                end = 20.dp,
                                bottom = ArtistSectionHeaderBottomSpacing
                            )
                        )
                        LazyRow(
                            state = albumPreviewState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(previewLocalAlbums, key = LocalAlbum::id) { album ->
                                FlowtoneCollectionArtworkCard(
                                    title = album.title,
                                    subtitle = "${album.songs.size} 首歌曲",
                                    artworkUri = album.artworkUri,
                                    onClick = { onOpenAlbum(album.id) },
                                    titleMaxLines = 2,
                                    modifier = albumPreviewMotion.itemModifier(album.id)
                                        .width(FlowtoneCollectionCardWidth)
                                )
                            }
                            items(
                                previewProviderAlbums,
                                key = { album -> album.identity.stableKey }
                            ) { album ->
                                FlowtoneCollectionArtworkCard(
                                    title = album.title,
                                    subtitle = album.songCount?.let { "$it 首歌曲" }
                                        ?: album.artist.ifBlank { "未知艺术家" },
                                    extensionArtwork = album.artwork,
                                    onClick = { onOpenProviderAlbum(album) },
                                    titleMaxLines = 2,
                                    modifier = albumPreviewMotion.itemModifier(
                                        album.identity.stableKey
                                    ).width(FlowtoneCollectionCardWidth)
                                )
                            }
                            if (hasMoreAlbums) {
                                item(key = ArtistAlbumsMoreCardKey) {
                                    FlowtoneCollectionTrailingActionCard(
                                        label = "查看更多",
                                        onClick = onOpenAllAlbums,
                                        modifier = albumPreviewMotion.itemModifier(
                                            ArtistAlbumsMoreCardKey
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val fallbackHeroHeightPx = with(density) { heroGeometry.backgroundHeight.toPx() }
        val heroHeightPx = measuredHeroHeightPx.takeIf { it > 0 }?.toFloat()
            ?: fallbackHeroHeightPx
        val topBarThresholds = remember(heroHeightPx, artistTopBarHeightPx, density) {
            artistTopBarThresholds(
                heroHeightPx = heroHeightPx.roundToInt(),
                topBarHeightPx = artistTopBarHeightPx.roundToInt(),
                hysteresisPx = with(density) { 24.dp.roundToPx() }
            )
        }
        LaunchedEffect(artistTopBarStateOwner, listState, topBarThresholds) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.distinctUntilChanged().collect { (index, offset) ->
                artistTopBarStateOwner.update(
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = offset,
                    thresholds = topBarThresholds
                )
            }
        }

        if (focusPresentationActive && biography != null) {
            ArtistBiographyFocus(
                artistName = displayArtist,
                biography = biography,
                progress = focusProgress,
                onDismiss = dismissBiography,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ArtistHero(
    backgroundKind: ArtistHeroBackgroundKind,
    bannerImageRequest: ImageRequest?,
    extensionImageLoader: coil3.ImageLoader,
    bannerFallbackColor: Color,
    accentColor: Color,
    geometry: ArtistHeroGeometry,
    artistName: String,
    avatarImage: ExtensionImage?,
    socialStats: ArtistSocialStats,
    statistics: String?,
    biography: String?,
    pageTransition: PageTransitionScope,
    onExpandBiography: () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            ArtistHeroBackground(
                kind = backgroundKind,
                bannerImageRequest = bannerImageRequest,
                extensionImageLoader = extensionImageLoader,
                bannerFallbackColor = bannerFallbackColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(geometry.backgroundHeight)
                    .then(
                        if (backgroundKind == ArtistHeroBackgroundKind.Banner) {
                            pageTransition.elementModifier(
                                order = ArtistHeroElement.Background.order,
                                orderCount = ArtistHeroMotionOrderCount
                            )
                        } else {
                            Modifier
                        }
                    )
            )
            ArtistInfoCard(
                artistName = artistName,
                socialStats = socialStats,
                statistics = statistics,
                biography = biography,
                accentColor = accentColor,
                geometry = geometry,
                pageTransition = pageTransition,
                onExpandBiography = onExpandBiography,
                modifier = Modifier.fillMaxWidth()
            )
            ArtistAvatar(
                size = geometry.avatarSize,
                image = avatarImage,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = pageTransition.elementModifier(
                    order = ArtistHeroElement.Avatar.order,
                    orderCount = ArtistHeroMotionOrderCount,
                    translationOffsetScale = 0.45f
                )
            )
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val backgroundHeight = geometry.backgroundHeight.roundToPx()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val cardMargin = geometry.infoCardHorizontalMargin.roundToPx()
        val cardWidth = (width - cardMargin * 2).coerceAtLeast(0)
        val avatarSize = geometry.avatarSize.roundToPx()
        val backgroundPlaceable = measurables[0].measure(
            constraints.copy(
                minWidth = width,
                maxWidth = width,
                minHeight = backgroundHeight,
                maxHeight = backgroundHeight
            )
        )
        val infoCardPlaceable = measurables[1].measure(
            constraints.copy(
                minWidth = cardWidth,
                maxWidth = cardWidth,
                minHeight = 0
            )
        )
        val avatarPlaceable = measurables[2].measure(
            constraints.copy(
                minWidth = avatarSize,
                maxWidth = avatarSize,
                minHeight = avatarSize,
                maxHeight = avatarSize
            )
        )
        val infoCardTop = geometry.infoCardTop.roundToPx()
        val avatarTop = geometry.avatarTop.roundToPx()
        val heroHeight = maxOf(
            backgroundPlaceable.height,
            infoCardTop + infoCardPlaceable.height,
            avatarTop + avatarPlaceable.height
        )

        layout(width, heroHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            backgroundPlaceable.placeRelative(0, 0)
            infoCardPlaceable.placeRelative(cardMargin, infoCardTop)
            avatarPlaceable.placeRelative((width - avatarPlaceable.width) / 2, avatarTop)
        }
    }
}

@Composable
private fun ArtistHeroBackground(
    kind: ArtistHeroBackgroundKind,
    bannerImageRequest: ImageRequest?,
    extensionImageLoader: coil3.ImageLoader,
    bannerFallbackColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.then(
            if (kind == ArtistHeroBackgroundKind.Banner) {
                Modifier.background(bannerFallbackColor)
            } else {
                Modifier
            }
        )
    ) {
        if (kind == ArtistHeroBackgroundKind.Banner && bannerImageRequest != null) {
            AsyncImage(
                model = bannerImageRequest,
                imageLoader = extensionImageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ArtistInfoCard(
    artistName: String,
    socialStats: ArtistSocialStats,
    statistics: String?,
    biography: String?,
    accentColor: Color,
    geometry: ArtistHeroGeometry,
    pageTransition: PageTransitionScope,
    onExpandBiography: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previewHasVisualOverflow by remember(biography) { mutableStateOf(false) }
    val socialText = remember(socialStats) { artistSocialStatsText(socialStats) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    pageTransition.elementModifier(
                        order = ArtistHeroElement.CardSurface.order,
                        orderCount = ArtistHeroMotionOrderCount
                    )
                )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = ArtistInfoCardShape,
                modifier = Modifier.fillMaxSize()
            ) {}
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ArtistInfoCardShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = ArtistInfoCardTintTopAlpha),
                                accentColor.copy(alpha = ArtistInfoCardTintBottomAlpha)
                            )
                        )
                    )
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = geometry.infoCardHorizontalPadding,
                    top = geometry.infoCardContentTopPadding,
                    end = geometry.infoCardHorizontalPadding,
                    bottom = geometry.infoCardBottomPadding
                )
        ) {
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = pageTransition.elementModifier(
                    order = ArtistHeroElement.Name.order,
                    orderCount = ArtistHeroMotionOrderCount
                )
            )
            if (socialText != null || statistics != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = pageTransition.elementModifier(
                        order = ArtistHeroElement.Metadata.order,
                        orderCount = ArtistHeroMotionOrderCount
                    ).padding(top = ArtistInfoCardSectionGap)
                ) {
                    socialText?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                    statistics?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            biography?.let { text ->
                Column(
                    modifier = pageTransition.elementModifier(
                        order = ArtistHeroElement.Biography.order,
                        orderCount = ArtistHeroMotionOrderCount
                    )
                        .fillMaxWidth()
                        .padding(
                            top = ArtistInfoCardSectionGap,
                            start = geometry.biographyHorizontalInset,
                            end = geometry.biographyHorizontalInset
                        )
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        maxLines = ArtistBiographyPreviewMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            previewHasVisualOverflow = result.hasVisualOverflow
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (artistBiographyExpandVisible(text, previewHasVisualOverflow)) {
                        Text(
                            text = "展开",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable(onClick = onExpandBiography)
                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistPreviewSectionHeader(
    title: String,
    actionTitle: String? = null,
    onActionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        if (actionTitle != null) {
            Text(
                text = actionTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ArtistBiographyFocus(
    artistName: String,
    biography: String,
    progress: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outsideInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.34f * progress.coerceIn(0f, 1f)))
            .clickable(
                interactionSource = outsideInteractionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = ArtistInfoCardShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = maxHeight * 0.76f)
                .graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    translationY = 16.dp.toPx() * (1f - progress.coerceIn(0f, 1f))
                }
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "艺人简介",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = biography,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun artistSongItemKey(index: Int, song: Song): String =
    "${song.id}-${song.uri}-$index"

@Composable
internal fun ArtistAvatar(
    size: Dp,
    image: ExtensionImage?,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val presentation = artistAvatarPresentation(size)
    Box(
        modifier = modifier
            .size(presentation.measuredSize)
            .border(presentation.outlineWidth, MaterialTheme.colorScheme.surface, CircleShape)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(size * 0.54f)
        )
        ExperimentalArtistAvatarImage(image = image, modifier = Modifier.fillMaxSize())
    }
}
