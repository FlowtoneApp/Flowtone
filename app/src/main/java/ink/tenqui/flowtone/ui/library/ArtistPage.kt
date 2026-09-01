package ink.tenqui.flowtone.ui.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.graphics.lerp as lerpColor
import coil3.compose.AsyncImage
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rememberArtworkBackgroundColor
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.player.localSongsForArtist

private val ArtistHeaderMinimumContentHeight = 252.dp
private val ArtistToolbarHeight = 64.dp
private val ArtistAvatarSize = 112.dp
private val ArtistCompactAvatarSize = 104.dp
private val ArtistHeaderCornerRadius = 24.dp
private val ArtistHeaderContentTopGap = 16.dp
private val ArtistHeaderAvatarNameGap = 12.dp
private val ArtistHeaderBottomPadding = 24.dp
private val ArtistAlbumArtworkSize = 140.dp
private const val ArtistHeaderCardAnimationIndex = 0
private const val ArtistHeaderAvatarAnimationIndex = 1
private const val ArtistHeaderNameAnimationIndex = 2
private const val ArtistHeaderAliasAnimationIndex = 3
private const val ArtistHeaderBiographyAnimationIndex = 4
private const val ArtistHeaderStatsAnimationIndex = 5
private const val ArtistSongsTitleAnimationIndex = 6
private const val ArtistFirstSongAnimationIndex = 7
private const val ArtistAlbumsTitleAnimationIndex = 11
private const val ArtistAlbumCardsAnimationIndex = 12
internal const val ArtistTransitionOrderCount = ArtistAlbumCardsAnimationIndex + 1
private const val ArtistFirstSongListItemIndex = 2
private const val ArtistLazyAheadViewportFraction = 0.75f
private const val ArtistLazyBehindViewportFraction = 0.25f

internal fun canFocusArtistProfile(biography: String?): Boolean =
    !biography.isNullOrBlank()

internal fun artistBiographyMaxLines(focused: Boolean): Int = if (focused) Int.MAX_VALUE else 1

internal enum class ArtistProfileBackResult { CollapseProfile, NavigateBack }

internal fun artistProfileBackResult(focused: Boolean): ArtistProfileBackResult =
    if (focused) ArtistProfileBackResult.CollapseProfile else ArtistProfileBackResult.NavigateBack

internal enum class ArtistProfileColorMode { Artwork, Material }

internal fun artistProfileColorMode(hasArtwork: Boolean): ArtistProfileColorMode =
    if (hasArtwork) ArtistProfileColorMode.Artwork else ArtistProfileColorMode.Material

internal enum class ArtistBannerLoadState { Loading, Loaded, Failed }

internal fun artistBannerLayerActive(
    bannerKnown: Boolean,
    loadState: ArtistBannerLoadState
): Boolean = bannerKnown && loadState == ArtistBannerLoadState.Loaded

private val ArtistProfileFocusElevation = 6.dp

internal fun artistProfileElevation(focusProgress: Float): Dp =
    lerp(0.dp, ArtistProfileFocusElevation, focusProgress.coerceIn(0f, 1f))

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistPage(
    artistName: String,
    hasLocalContent: Boolean,
    providedAvatar: ExtensionImage?,
    providedMetadata: ArtistMetadata?,
    allSongs: List<Song>,
    albums: List<LocalAlbum>,
    currentSong: Song?,
    onToolbarContentVisibleChange: (Boolean) -> Unit,
    onProfileBackActionChange: ((() -> Unit)?) -> Unit,
    onNavigateBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier =
        { _, _, _ -> Modifier },
    modifier: Modifier = Modifier
) {
    val displayArtist = artistName.trim()
    val cacheWindow = remember {
        LazyLayoutCacheWindow(
            aheadFraction = ArtistLazyAheadViewportFraction,
            behindFraction = ArtistLazyBehindViewportFraction
        )
    }
    val listState = rememberLazyListState(cacheWindow = cacheWindow)
    val albumListState = rememberLazyListState(cacheWindow = cacheWindow)
    val artistSongs = remember(displayArtist, allSongs, hasLocalContent) {
        if (hasLocalContent) localSongsForArtist(allSongs, displayArtist) else emptyList()
    }
    val artistAlbums = remember(displayArtist, albums, hasLocalContent) {
        if (hasLocalContent) artistAlbumsFor(albums, displayArtist) else emptyList()
    }
    val artistMetadata = rememberArtistMetadata(
        artistName = displayArtist,
        providedMetadata = providedMetadata?.takeUnless { hasLocalContent }
    )
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
    val contentVisibility = remember(hasLocalContent, artistAlbums, statistics) {
        artistPageContentVisibility(
            hasLocalContent = hasLocalContent,
            hasAlbums = artistAlbums.isNotEmpty(),
            hasStatistics = statistics != null
        )
    }
    val artistSongKeys = remember(artistSongs) {
        artistSongs.mapIndexed(::artistSongItemKey)
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
    val isDarkTheme = isSystemInDarkTheme()
    val banner = artistMetadata?.banner
    val paletteArtworkData: Any? = artistAvatarImage ?: artistSongs.firstOrNull()?.artworkUri
    val colorMode = artistProfileColorMode(paletteArtworkData != null)
    val materialBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val resolvedArtistColor = rememberArtworkBackgroundColor(
        artworkData = paletteArtworkData,
        imageLoader = if (artistAvatarImage != null) {
            ExtensionManager.get(context).extensionImageLoader
        } else {
            context.imageLoader
        },
        fallbackColor = materialBackground,
        isDarkTheme = isDarkTheme
    )
    val artistColor by animateColorAsState(
        targetValue = resolvedArtistColor ?: materialBackground,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistProfileArtworkColor"
    )
    val biography = artistMetadata?.biography?.trim()?.takeIf(String::isNotEmpty)
    var artistProfileFocused by remember(displayArtist) { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (artistProfileFocused) 1f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistProfileFocusProgress"
    )
    val focusPresentationActive = artistProfileFocused || focusProgress > 0.001f
    val collapseProfile = remember(displayArtist) { { artistProfileFocused = false } }
    BackHandler(enabled = artistProfileFocused, onBack = collapseProfile)
    DisposableEffect(artistProfileFocused, collapseProfile) {
        onProfileBackActionChange(collapseProfile.takeIf { artistProfileFocused })
        onDispose {
            if (artistProfileFocused) onProfileBackActionChange(null)
        }
    }

    val visibleSongKeys by remember(listState, artistSongKeys) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                artistSongKeys.getOrNull(item.index - ArtistFirstSongListItemIndex)
            }.distinct()
        }
    }
    var frozenTransitionId by remember(displayArtist) { mutableStateOf<Int?>(null) }
    var frozenViewportKeys by remember(displayArtist) {
        mutableStateOf<List<String>>(emptyList())
    }
    var capturedPageProgress by remember(displayArtist) { mutableStateOf(0f) }

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
    val animationOrderByKey = remember(animationGroupKeys) {
        animationGroupKeys.withIndex().associate { (order, key) -> key to order }
    }

    fun fixedItemModifier(index: Int): Modifier {
        return pageTransition.elementModifier(index, ArtistTransitionOrderCount)
    }

    val density = LocalDensity.current
    var pageBoundsInRoot by remember(displayArtist) { mutableStateOf<Rect?>(null) }
    var headerAnchorBoundsInRoot by remember(displayArtist) { mutableStateOf<Rect?>(null) }
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val toolbarHeight = ArtistToolbarHeight + statusBarTop
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val defaultHeaderHeightPx = with(density) {
        (ArtistHeaderMinimumContentHeight + statusBarTop).roundToPx()
    }
    val measuredHeaderHeightPx = headerHeightPx.takeIf { it > 0 } ?: defaultHeaderHeightPx
    val toolbarHeightPx = with(density) { toolbarHeight.roundToPx() }
    val showToolbarContentThresholdPx = (measuredHeaderHeightPx - toolbarHeightPx).coerceAtLeast(0)
    val hideToolbarContentThresholdPx = (showToolbarContentThresholdPx - with(density) {
        24.dp.roundToPx()
    }).coerceAtLeast(0)
    var toolbarContentVisible by remember {
        mutableStateOf(
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset >= showToolbarContentThresholdPx
        )
    }

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        showToolbarContentThresholdPx,
        hideToolbarContentThresholdPx
    ) {
        val hasPassedHeaderRange = listState.firstVisibleItemIndex > 0 ||
            listState.firstVisibleItemScrollOffset >= showToolbarContentThresholdPx
        val hasReturnedToHeaderRange = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset <= hideToolbarContentThresholdPx
        toolbarContentVisible = when {
            !toolbarContentVisible && hasPassedHeaderRange -> true
            toolbarContentVisible && hasReturnedToHeaderRange -> false
            else -> toolbarContentVisible
        }
    }
    LaunchedEffect(toolbarContentVisible) {
        onToolbarContentVisibleChange(toolbarContentVisible)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { pageBoundsInRoot = it.boundsInRoot() }
            .rightSwipeBackGesture {
                if (artistProfileFocused) collapseProfile() else onNavigateBack()
            }
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !focusPresentationActive,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(14.dp * focusProgress)
                    } else Modifier
                ),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "artist-header") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ArtistHeaderMinimumContentHeight + statusBarTop)
                        .onGloballyPositioned { coordinates ->
                            headerAnchorBoundsInRoot = coordinates.boundsInRoot()
                            headerHeightPx = coordinates.size.height
                        }
                )
            }
            if (contentVisibility.showSongs) {
                item(key = "artist-songs-title") {
                    ArtistSectionTitle(
                        title = "歌曲",
                        modifier = fixedItemModifier(ArtistSongsTitleAnimationIndex)
                            .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 8.dp)
                    )
                }
                if (artistSongs.isEmpty()) {
                    item(key = "artist-empty") {
                        Text(
                            text = "没有找到该艺术家的歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = fixedItemModifier(ArtistFirstSongAnimationIndex)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = artistSongs,
                        key = ::artistSongItemKey
                    ) { index, song ->
                        val songKey = artistSongKeys[index]
                        val viewportOrder = animationOrderByKey[songKey] ?: 0
                        val viewportOrderCount = animationGroupKeys.size.coerceAtLeast(1)
                        SongListItem(
                            song = song,
                            isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                            onClick = { onSongClick(artistSongs, index) },
                            modifier = if (enterGroupReady) {
                                itemModifier(listProgress, viewportOrder, viewportOrderCount)
                            } else {
                                Modifier.graphicsLayer { alpha = 0f }
                            }.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            if (contentVisibility.showAlbums) {
                item(key = "artist-albums") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArtistSectionTitle(
                            title = "专辑",
                            modifier = fixedItemModifier(ArtistAlbumsTitleAnimationIndex)
                                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)
                        )
                        LazyRow(
                            state = albumListState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(artistAlbums, key = LocalAlbum::id) { album ->
                                ArtistAlbumCard(
                                    album = album,
                                    onClick = { onOpenAlbum(album.id) },
                                    modifier = fixedItemModifier(ArtistAlbumCardsAnimationIndex)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (focusPresentationActive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = focusProgress *
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.12f else 0.18f
                        )
                    )
                    .clickable(indication = null, interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    }) { collapseProfile() }
            )
        }

        val pageBounds = pageBoundsInRoot
        val anchorBounds = headerAnchorBoundsInRoot
        if (pageBounds != null && anchorBounds != null) {
            val anchorLeft = anchorBounds.left - pageBounds.left
            val anchorTop = anchorBounds.top - pageBounds.top
            val anchorWidth = anchorBounds.width
            val anchorHeight = anchorBounds.height
            val expandedHeightPx = with(density) { (maxHeight * 0.76f).toPx() }
            val cardHeightPx = anchorHeight + (expandedHeightPx - anchorHeight) * focusProgress
            ArtistHeaderCard(
                artistName = displayArtist,
                statistics = statistics.takeIf { contentVisibility.showStatistics },
                avatarImage = artistAvatarImage,
                topPadding = statusBarTop,
                alias = artistMetadata?.aliases?.take(3)?.joinToString(" · "),
                biography = biography,
                banner = banner,
                artistColor = artistColor,
                colorMode = colorMode,
                focused = focusPresentationActive,
                focusProgress = focusProgress,
                onClick = { if (canFocusArtistProfile(biography)) artistProfileFocused = true },
                itemModifier = ::fixedItemModifier,
                onHeightChanged = {},
                modifier = Modifier
                    .offset { IntOffset(anchorLeft.toInt(), anchorTop.toInt()) }
                    .width(with(density) { anchorWidth.toDp() })
                    .height(with(density) { cardHeightPx.toDp() })
            )
        }

    }
}

@Composable
private fun ArtistHeaderCard(
    artistName: String,
    statistics: String?,
    avatarImage: ExtensionImage?,
    topPadding: Dp,
    alias: String? = null,
    biography: String? = null,
    banner: ExtensionImage? = null,
    artistColor: Color,
    colorMode: ArtistProfileColorMode,
    focused: Boolean,
    focusProgress: Float,
    onClick: () -> Unit,
    itemModifier: (Int) -> Modifier,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayAlias = alias?.trim()?.takeIf(String::isNotEmpty)
    val displayBiography = biography?.trim()?.takeIf(String::isNotEmpty)
    val context = LocalContext.current
    val bannerImageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    var bannerLoadState by remember(banner) {
        mutableStateOf(if (banner == null) ArtistBannerLoadState.Failed else ArtistBannerLoadState.Loading)
    }
    val bannerAlpha by animateFloatAsState(
        targetValue = if (artistBannerLayerActive(banner != null, bannerLoadState)) 1f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistProfileBannerAlpha"
    )
    BoxWithConstraints(
        modifier = modifier
            .heightIn(min = ArtistHeaderMinimumContentHeight + topPadding)
            .onSizeChanged { size -> onHeightChanged(size.height) }
            .clickable(
                enabled = biography != null,
                indication = null,
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                onClick = onClick
            )
            .shadow(
                elevation = artistProfileElevation(focusProgress),
                shape = RoundedCornerShape(
                    bottomStart = ArtistHeaderCornerRadius,
                    bottomEnd = ArtistHeaderCornerRadius
                )
            )
            .clip(
                RoundedCornerShape(
                    bottomStart = ArtistHeaderCornerRadius,
                    bottomEnd = ArtistHeaderCornerRadius
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(itemModifier(ArtistHeaderCardAnimationIndex))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        val bottomArtistColor = if (colorMode == ArtistProfileColorMode.Artwork) {
            lerpColor(artistColor, MaterialTheme.colorScheme.surfaceContainerHigh, 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(artistColor, bottomArtistColor))
            )
        )
        banner?.let { image ->
            AsyncImage(
                model = image,
                imageLoader = bannerImageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onLoading = { bannerLoadState = ArtistBannerLoadState.Loading },
                onSuccess = { bannerLoadState = ArtistBannerLoadState.Loaded },
                onError = { bannerLoadState = ArtistBannerLoadState.Failed },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bannerAlpha }
            )
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = bannerAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
        }
        val avatarSize = if (maxWidth < 380.dp) ArtistCompactAvatarSize else ArtistAvatarSize
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = topPadding + ArtistToolbarHeight + ArtistHeaderContentTopGap,
                    end = 20.dp,
                    bottom = ArtistHeaderBottomPadding
                )
        ) {
          Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            ArtistAvatar(
                size = avatarSize,
                image = avatarImage,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = itemModifier(ArtistHeaderAvatarAnimationIndex)
            )
            ArtistHeaderProfileDetails(
                artistName = artistName,
                alias = displayAlias,
                biography = null,
                statistics = statistics,
                avatarSize = avatarSize,
                itemModifier = itemModifier,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ArtistHeaderAvatarNameGap)
            )
          }
          displayBiography?.let { text ->
              Text(
                  text = text,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = artistBiographyMaxLines(focused),
                  overflow = TextOverflow.Ellipsis,
                  modifier = itemModifier(ArtistHeaderBiographyAnimationIndex)
                      .fillMaxWidth()
                      .padding(top = 16.dp)
                      .then(if (focused) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
              )
          }
        }
    }
}

@Composable
private fun ArtistHeaderProfileDetails(
    artistName: String,
    alias: String?,
    biography: String?,
    statistics: String?,
    avatarSize: Dp,
    itemModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = itemModifier(ArtistHeaderNameAnimationIndex).layoutId("name")
            )
            alias?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = itemModifier(ArtistHeaderAliasAnimationIndex).layoutId("alias")
                )
            }
            biography?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = itemModifier(ArtistHeaderBiographyAnimationIndex).layoutId("biography")
                )
            }
            statistics?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = itemModifier(ArtistHeaderStatsAnimationIndex).layoutId("statistics")
                )
            }
        },
        modifier = modifier.height(avatarSize)
    ) { measurables, constraints ->
        val children = measurables.associateBy { it.layoutId }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val statisticsPlaceable = children["statistics"]?.measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        val statisticsGap = 12.dp.roundToPx()
        val textBottom = (height - (statisticsPlaceable?.height ?: 0) - statisticsGap)
            .coerceAtLeast(0)
        val textConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        var nextTextY = 0

        fun measureText(id: String, gapBefore: Int = 0) = children[id]?.let { measurable ->
            val availableHeight = (textBottom - nextTextY - gapBefore).coerceAtLeast(0)
            val placeable = measurable.measure(textConstraints.copy(maxHeight = availableHeight))
            val y = (nextTextY + gapBefore).coerceAtMost(textBottom)
            nextTextY = (y + placeable.height).coerceAtMost(textBottom)
            y to placeable
        }

        val name = measureText("name")
        val alias = measureText("alias", gapBefore = 4.dp.roundToPx())
        val biography = measureText(
            "biography",
            gapBefore = if (alias == null) 12.dp.roundToPx() else 10.dp.roundToPx()
        )

        layout(width, height) {
            name?.let { (y, placeable) -> placeable.placeRelative(0, y) }
            alias?.let { (y, placeable) -> placeable.placeRelative(0, y) }
            biography?.let { (y, placeable) -> placeable.placeRelative(0, y) }
            statisticsPlaceable?.placeRelative(width - statisticsPlaceable.width, height - statisticsPlaceable.height)
        }
    }
}

@Composable
private fun ArtistSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
private fun ArtistAlbumCard(
    album: LocalAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(ArtistAlbumArtworkSize)
            .clickable(onClick = onClick)
    ) {
        FlowtoneArtwork(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(ArtistAlbumArtworkSize)
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "${album.songs.size} 首歌曲",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
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
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
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
