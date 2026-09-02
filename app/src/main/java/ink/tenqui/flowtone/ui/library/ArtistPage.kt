package ink.tenqui.flowtone.ui.library

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.RectF
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.lerp as lerpColor
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarNavigationTitleShift
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.components.rememberArtworkBackgroundColor
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.providerAlbumsForArtist
import ink.tenqui.flowtone.data.online.providerSongsForArtist
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.player.localSongsForArtist

private val ArtistHeaderMinimumContentHeight = 252.dp
private val ArtistToolbarHeight = 64.dp
private val ArtistDockedAvatarSize = 36.dp
private val ArtistDockedTitleGap = 10.dp
private val ArtistAvatarSize = 112.dp
private val ArtistCompactAvatarSize = 104.dp
private val ArtistHeaderCornerRadius = 24.dp
private val ArtistHeaderContentTopGap = 16.dp
private val ArtistHeaderAvatarNameGap = 12.dp
private val ArtistHeaderBottomPadding = 24.dp
private val ArtistSectionTitleTopSpacing = 24.dp
private val ArtistSectionTitleBottomSpacing = 12.dp
private val ArtistBiographyTopSpacing = 16.dp
private val ArtistBiographyEdgeBlurRadius = 2.5.dp
private const val ArtistBiographyEdgeFadeOutStartProgress = 0.55f
internal val ArtistBiographyContentColor = Color.White
private val ArtistAlbumArtworkSize = 140.dp
private const val ArtistSongsTitleAnimationIndex = 6
private const val ArtistFirstSongAnimationIndex = 7
private const val ArtistAlbumsTitleAnimationIndex = 11
private const val ArtistAlbumCardsAnimationIndex = 12
internal const val ArtistTransitionOrderCount = ArtistAlbumCardsAnimationIndex + 1
private const val ArtistFirstSongListItemIndex = 2
private const val ArtistLazyAheadViewportFraction = 0.75f
private const val ArtistLazyBehindViewportFraction = 0.25f

internal data class ArtistBiographyMeasurement(
    val fullTextHeightPx: Int = 0,
    val collapsedViewportHeightPx: Int = 0
)

internal fun canFocusArtistProfile(
    biography: String?,
    measurement: ArtistBiographyMeasurement
): Boolean = !biography.isNullOrBlank() &&
    measurement.fullTextHeightPx > measurement.collapsedViewportHeightPx

internal fun artistBiographyEdgeEffectEnabled(
    biography: String?,
    measurement: ArtistBiographyMeasurement
): Boolean = canFocusArtistProfile(biography, measurement)

internal fun artistBiographyCollapsedViewportHeight(lineHeight: Dp): Dp =
    lineHeight.coerceAtLeast(0.dp) * 2f

internal fun artistBiographyEdgeBandHeight(collapsedViewportHeight: Dp): Dp =
    collapsedViewportHeight.coerceAtLeast(0.dp) / 2f

internal fun artistBiographyViewportHeight(
    collapsedHeight: Dp,
    expandedHeight: Dp,
    focusProgress: Float
): Dp = lerp(
    collapsedHeight,
    expandedHeight.coerceAtLeast(collapsedHeight),
    focusProgress.coerceIn(0f, 1f)
)

internal fun artistBiographyEdgeStrength(focusProgress: Float): Float {
    val progress = focusProgress.coerceIn(0f, 1f)
    if (progress <= ArtistBiographyEdgeFadeOutStartProgress) return 1f
    val fadeProgress = (
        (progress - ArtistBiographyEdgeFadeOutStartProgress) /
            (1f - ArtistBiographyEdgeFadeOutStartProgress)
        ).coerceIn(0f, 1f)
    return 1f - FlowtoneMotion.Easing.transform(fadeProgress)
}

internal data class ArtistProfileFocusHeightTarget(
    val height: Dp,
    val biographyScrollRequired: Boolean
)

internal fun artistProfileFocusHeightTarget(
    collapsedHeight: Dp,
    requiredFocusedHeight: Dp,
    maxAllowedFocusHeight: Dp
): ArtistProfileFocusHeightTarget {
    val effectiveMaximum = maxAllowedFocusHeight.coerceAtLeast(collapsedHeight)
    return ArtistProfileFocusHeightTarget(
        height = requiredFocusedHeight
            .coerceAtLeast(collapsedHeight)
            .coerceAtMost(effectiveMaximum),
        biographyScrollRequired = requiredFocusedHeight > effectiveMaximum
    )
}

internal enum class ArtistProfileBackResult { CollapseProfile, NavigateBack }

internal fun artistProfileBackResult(focused: Boolean): ArtistProfileBackResult =
    if (focused) ArtistProfileBackResult.CollapseProfile else ArtistProfileBackResult.NavigateBack

internal enum class ArtistProfileBaseColorSource { ArtistArtwork, Banner, Material }

internal fun artistProfileBaseColorSource(
    artistArtworkColorAvailable: Boolean,
    bannerColorAvailable: Boolean,
    bannerState: ArtistBannerPresentationState
): ArtistProfileBaseColorSource = when {
    bannerColorAvailable && artistBannerTargetAlpha(bannerState) == 1f ->
        ArtistProfileBaseColorSource.Banner
    artistArtworkColorAvailable -> ArtistProfileBaseColorSource.ArtistArtwork
    else -> ArtistProfileBaseColorSource.Material
}

internal enum class ArtistBannerPresentationState {
    BannerUnavailable,
    BannerLoading,
    BannerReadyImmediately,
    BannerLoadedLate,
    BannerFailed
}

internal fun initialArtistBannerPresentationState(
    bannerKnown: Boolean,
    drawableReadyImmediately: Boolean
): ArtistBannerPresentationState = when {
    !bannerKnown -> ArtistBannerPresentationState.BannerUnavailable
    drawableReadyImmediately -> ArtistBannerPresentationState.BannerReadyImmediately
    else -> ArtistBannerPresentationState.BannerLoading
}

internal fun artistBannerTargetAlpha(state: ArtistBannerPresentationState): Float = when (state) {
    ArtistBannerPresentationState.BannerReadyImmediately,
    ArtistBannerPresentationState.BannerLoadedLate -> 1f
    else -> 0f
}

internal fun artistBannerSuccessState(
    current: ArtistBannerPresentationState,
    pageEnterComplete: Boolean
): ArtistBannerPresentationState = when {
    current == ArtistBannerPresentationState.BannerReadyImmediately -> current
    pageEnterComplete -> ArtistBannerPresentationState.BannerLoadedLate
    else -> ArtistBannerPresentationState.BannerLoading
}

internal fun artistBannerUsesLateReveal(state: ArtistBannerPresentationState): Boolean =
    state == ArtistBannerPresentationState.BannerLoadedLate

internal fun artistBannerInternalAlpha(
    state: ArtistBannerPresentationState,
    lateRevealAlpha: Float
): Float = when (state) {
    ArtistBannerPresentationState.BannerReadyImmediately -> 1f
    ArtistBannerPresentationState.BannerLoadedLate -> lateRevealAlpha.coerceIn(0f, 1f)
    else -> 0f
}

internal fun artistBannerDisplayMemoryCacheKey(banner: ExtensionImage): String =
    "artist-profile-banner:${banner.extensionId}:${banner.url}"

internal const val ArtistBannerBottomFadeStartFraction = 0.48f
internal const val ArtistBannerDarkScrimAlpha = 0.30f

internal data class ArtistBannerHeroOverlayGeometry(
    val heroHeight: Dp,
    val darkScrimHeight: Dp,
    val bottomBlendHeight: Dp
)

internal fun artistBannerHeroOverlayGeometry(heroHeight: Dp): ArtistBannerHeroOverlayGeometry =
    ArtistBannerHeroOverlayGeometry(
        heroHeight = heroHeight,
        darkScrimHeight = heroHeight,
        bottomBlendHeight = heroHeight
    )

internal fun artistBannerBottomBlendFinalColor(cardBaseColor: Color): Color = cardBaseColor

internal data class ArtistProfilePresentationHeights(
    val cardHeight: Dp,
    val bannerHeroHeight: Dp
)

internal fun artistProfilePresentationHeights(
    collapsedHeight: Dp,
    expandedHeight: Dp,
    focusProgress: Float,
    bannerHeroHeight: Dp = collapsedHeight
): ArtistProfilePresentationHeights = ArtistProfilePresentationHeights(
    cardHeight = lerp(collapsedHeight, expandedHeight, focusProgress.coerceIn(0f, 1f)),
    bannerHeroHeight = bannerHeroHeight
)

internal data class ArtistHeaderScrollPresentation(
    val topPx: Float,
    val visibleHeightPx: Float,
    val expandedContentHeightPx: Float,
    val collapseProgress: Float
)

internal fun artistHeaderScrollPresentation(
    anchorTopPx: Float,
    dockedTopPx: Float,
    expandedHeightPx: Float,
    dockedHeightPx: Float
): ArtistHeaderScrollPresentation {
    val expandedHeight = expandedHeightPx.coerceAtLeast(0f)
    val dockedHeight = dockedHeightPx.coerceIn(0f, expandedHeight)
    val collapseDistance = (expandedHeight - dockedHeight).coerceAtLeast(1f)
    val collapseProgress = ((dockedTopPx - anchorTopPx) / collapseDistance).coerceIn(0f, 1f)
    return ArtistHeaderScrollPresentation(
        topPx = maxOf(anchorTopPx, dockedTopPx),
        visibleHeightPx = expandedHeight + (dockedHeight - expandedHeight) * collapseProgress,
        expandedContentHeightPx = expandedHeight,
        collapseProgress = collapseProgress
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistPage(
    artistName: String,
    hasLocalContent: Boolean,
    providedAvatar: ExtensionImage?,
    providedMetadata: ArtistMetadata?,
    allSongs: List<Song>,
    albums: List<LocalAlbum>,
    providerId: String? = null,
    providerArtistId: String? = null,
    providerSongs: List<ProviderSong> = emptyList(),
    providerAlbums: List<ProviderAlbum> = emptyList(),
    currentSong: Song?,
    onNavigateBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onProviderSongClick: (List<ProviderSong>, Int) -> Unit = { _, _ -> },
    onOpenAlbum: (Long) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit = {},
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
    val artistProviderSongs = remember(
        providerId,
        providerArtistId,
        displayArtist,
        providerSongs,
        hasLocalContent
    ) {
        if (!hasLocalContent && providerId != null && providerArtistId != null) {
            providerSongsForArtist(providerSongs, providerId, providerArtistId, displayArtist)
        } else emptyList()
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
        } else emptyList()
    }
    val presentedArtistSongs = remember(artistSongs, artistProviderSongs, hasLocalContent) {
        if (hasLocalContent) artistSongs else artistProviderSongs.map(ProviderSong::toPresentationSong)
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
    val contentVisibility = remember(
        hasLocalContent,
        artistAlbums,
        artistProviderSongs,
        artistProviderAlbums,
        statistics
    ) {
        artistPageContentVisibility(
            hasLocalContent = hasLocalContent,
            hasSongs = artistProviderSongs.isNotEmpty(),
            hasAlbums = artistAlbums.isNotEmpty() || artistProviderAlbums.isNotEmpty(),
            hasStatistics = statistics != null
        )
    }
    val artistSongKeys = remember(presentedArtistSongs, artistProviderSongs, hasLocalContent) {
        if (hasLocalContent) {
            presentedArtistSongs.mapIndexed(::artistSongItemKey)
        } else {
            artistProviderSongs.map { song -> "provider-song:${song.identity.stableKey}" }
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
    val paletteArtworkData: Any? = artistAvatarImage
        ?: artistSongs.firstOrNull()?.artworkUri
        ?: artistProviderSongs.firstOrNull()?.artwork
    val materialBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val extensionImageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    val bannerDisplayCacheKey = remember(banner) {
        banner?.let(::artistBannerDisplayMemoryCacheKey)
    }
    val cachedBannerImage = remember(bannerDisplayCacheKey, extensionImageLoader) {
        bannerDisplayCacheKey?.let { cacheKey ->
            extensionImageLoader.memoryCache?.get(MemoryCache.Key(cacheKey))?.image
        }
    }
    val bannerReadyAtPageEnterKey = remember(displayArtist) {
        bannerDisplayCacheKey?.takeIf { cachedBannerImage != null }
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
    var bannerRequestSucceeded by remember(bannerDisplayCacheKey) { mutableStateOf(false) }
    var bannerPresentationState by remember(bannerDisplayCacheKey, bannerReadyAtPageEnterKey) {
        mutableStateOf(
            initialArtistBannerPresentationState(
                bannerKnown = banner != null,
                drawableReadyImmediately = bannerDisplayCacheKey != null &&
                    bannerDisplayCacheKey == bannerReadyAtPageEnterKey
            )
        )
    }
    val lateBannerRevealAlpha by animateFloatAsState(
        targetValue = if (artistBannerUsesLateReveal(bannerPresentationState)) 1f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistProfileLateBannerRevealAlpha"
    )
    val bannerAlpha = artistBannerInternalAlpha(
        state = bannerPresentationState,
        lateRevealAlpha = lateBannerRevealAlpha
    )
    LaunchedEffect(
        bannerDisplayCacheKey,
        bannerRequestSucceeded,
        pageTransition.phase
    ) {
        if (
            bannerRequestSucceeded &&
            bannerPresentationState == ArtistBannerPresentationState.BannerLoading &&
            pageTransition.phase == PageTransitionPhase.Current
        ) {
            bannerPresentationState = ArtistBannerPresentationState.BannerLoadedLate
        }
    }
    val resolvedArtistArtworkColor = rememberArtworkBackgroundColor(
        artworkData = paletteArtworkData,
        imageLoader = if (artistAvatarImage != null) {
            extensionImageLoader
        } else {
            context.imageLoader
        },
        fallbackColor = materialBackground,
        isDarkTheme = isDarkTheme
    )
    val resolvedBannerColor = rememberArtworkBackgroundColor(
        artworkData = banner,
        imageLoader = extensionImageLoader,
        fallbackColor = materialBackground,
        isDarkTheme = isDarkTheme
    )
    val baseColorSource = artistProfileBaseColorSource(
        artistArtworkColorAvailable = resolvedArtistArtworkColor != null,
        bannerColorAvailable = resolvedBannerColor != null,
        bannerState = bannerPresentationState
    )
    val targetProfileColor = when (baseColorSource) {
        ArtistProfileBaseColorSource.Banner -> checkNotNull(resolvedBannerColor)
        ArtistProfileBaseColorSource.ArtistArtwork -> checkNotNull(resolvedArtistArtworkColor)
        ArtistProfileBaseColorSource.Material -> materialBackground
    }
    val artistColor by animateColorAsState(
        targetValue = targetProfileColor,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistProfileBackgroundColor"
    )
    val biography = artistMetadata?.biography?.trim()?.takeIf(String::isNotEmpty)
    val biographyStyle = MaterialTheme.typography.bodyMedium
    val biographyLineMeasurer = rememberTextMeasurer()
    val biographyLineHeightPx = remember(
        biography,
        biographyStyle,
        biographyLineMeasurer
    ) {
        biographyLineMeasurer.measure(
            text = biography ?: "Ag",
            style = biographyStyle,
            overflow = TextOverflow.Clip,
            maxLines = 1
        ).size.height
    }
    val biographyLineHeight = with(density) { biographyLineHeightPx.toDp() }
    var biographyMeasurement by remember(biography) {
        mutableStateOf(ArtistBiographyMeasurement())
    }
    val biographyCanFocus = canFocusArtistProfile(biography, biographyMeasurement)
    var artistProfileFocused by remember(displayArtist) { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (artistProfileFocused) 1f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "ArtistProfileFocusProgress"
    )
    val focusPresentationActive = artistProfileFocused || focusProgress > 0.001f
    val collapseProfile = remember(displayArtist) { { artistProfileFocused = false } }

    LaunchedEffect(biographyCanFocus) {
        if (artistProfileFocused && !biographyCanFocus) {
            collapseProfile()
        }
    }
    BackHandler(enabled = artistProfileFocused, onBack = collapseProfile)

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
    val firstSongTransitionOrderCount = animationGroupKeys.size.coerceAtLeast(1)

    fun fixedItemModifier(index: Int): Modifier {
        return pageTransition.elementModifier(index, ArtistTransitionOrderCount)
    }

    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .rightSwipeBackGesture {
                if (artistProfileFocused) collapseProfile() else onNavigateBack()
            }
    ) {
        val cardAvatarSize = if (maxWidth < 380.dp) {
            ArtistCompactAvatarSize
        } else {
            ArtistAvatarSize
        }
        val biographyReservedHeight = statusBarTop +
            ArtistToolbarHeight +
            ArtistHeaderContentTopGap +
            cardAvatarSize +
            ArtistBiographyTopSpacing +
            ArtistHeaderBottomPadding
        val collapsedBiographyViewportHeight = if (biography == null) {
            0.dp
        } else {
            artistBiographyCollapsedViewportHeight(biographyLineHeight)
        }
        val collapsedProfileCardHeight = if (biography == null) {
            ArtistHeaderMinimumContentHeight + statusBarTop
        } else {
            maxOf(
                ArtistHeaderMinimumContentHeight + statusBarTop,
                biographyReservedHeight + collapsedBiographyViewportHeight
            )
        }
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
                        .height(collapsedProfileCardHeight)
                )
            }
            if (contentVisibility.showSongs) {
                item(key = "artist-songs-title") {
                    ArtistSectionTitle(
                        title = "歌曲",
                        modifier = fixedItemModifier(ArtistSongsTitleAnimationIndex)
                            .padding(
                                start = 20.dp,
                                top = ArtistSectionTitleTopSpacing,
                                end = 20.dp,
                                bottom = ArtistSectionTitleBottomSpacing
                            )
                    )
                }
                if (presentedArtistSongs.isEmpty()) {
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
                        items = presentedArtistSongs,
                        key = { index, _ -> artistSongKeys[index] }
                    ) { index, song ->
                        val songKey = artistSongKeys[index]
                        val viewportOrder = animationOrderByKey[songKey] ?: 0
                        val viewportOrderCount = animationGroupKeys.size.coerceAtLeast(1)
                        SongListItem(
                            song = song,
                            isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                            onClick = {
                                if (hasLocalContent) onSongClick(artistSongs, index)
                                else onProviderSongClick(artistProviderSongs, index)
                            },
                            extensionArtwork = artistProviderSongs.getOrNull(index)?.artwork,
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
                                .padding(
                                    start = 20.dp,
                                    top = ArtistSectionTitleTopSpacing,
                                    end = 20.dp,
                                    bottom = ArtistSectionTitleBottomSpacing
                                )
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
                            items(artistProviderAlbums, key = { it.identity.stableKey }) { album ->
                                ArtistAlbumCard(
                                    title = album.title,
                                    supportingText = album.songCount?.let { "$it 首歌曲" }
                                        ?: album.artist.ifBlank { "未知艺术家" },
                                    extensionArtwork = album.artwork,
                                    onClick = { onOpenProviderAlbum(album) },
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

        val collapsedHeaderHeightPx = with(density) { collapsedProfileCardHeight.toPx() }
        val dockedHeaderHeight = statusBarTop + FlowtoneTopBarContentHeight
        val dockedHeaderHeightPx = with(density) { dockedHeaderHeight.toPx() }
        val collapsedDistancePx = (collapsedHeaderHeightPx - dockedHeaderHeightPx)
            .coerceAtLeast(0f)
        val anchorTopPx = if (listState.firstVisibleItemIndex == 0) {
            -listState.firstVisibleItemScrollOffset.toFloat()
        } else {
            -collapsedDistancePx
        }
        val headerScrollPresentation = artistHeaderScrollPresentation(
            anchorTopPx = anchorTopPx,
            // Artist owns the full edge-to-edge top region; its content starts below statusBarTop.
            dockedTopPx = 0f,
            expandedHeightPx = collapsedHeaderHeightPx,
            dockedHeightPx = dockedHeaderHeightPx
        )
        val headerCollapseProgress = headerScrollPresentation.collapseProgress
        val maxAllowedFocusHeight = maxHeight * 0.76f
        val fullBiographyHeight = with(density) {
            biographyMeasurement.fullTextHeightPx.toDp()
        }
        val requiredFocusedHeight = biographyReservedHeight + fullBiographyHeight
        val focusHeightTarget = artistProfileFocusHeightTarget(
            collapsedHeight = collapsedProfileCardHeight,
            requiredFocusedHeight = requiredFocusedHeight,
            maxAllowedFocusHeight = maxAllowedFocusHeight
        )
        val presentationHeights = artistProfilePresentationHeights(
            collapsedHeight = collapsedProfileCardHeight,
            expandedHeight = focusHeightTarget.height,
            focusProgress = focusProgress,
            bannerHeroHeight = ArtistHeaderMinimumContentHeight + statusBarTop
        )
        val expandedBiographyViewportHeight =
            (focusHeightTarget.height - biographyReservedHeight).coerceAtLeast(
                collapsedBiographyViewportHeight
            )
        val biographyViewportHeight = artistBiographyViewportHeight(
            collapsedHeight = collapsedBiographyViewportHeight,
            expandedHeight = expandedBiographyViewportHeight,
            focusProgress = focusProgress
        )
        val scrollingHeaderHeight = with(density) {
            headerScrollPresentation.visibleHeightPx.toDp()
        }
        val headerViewportHeight = if (focusPresentationActive) {
            presentationHeights.cardHeight
        } else {
            scrollingHeaderHeight
        }
        ArtistHeaderCard(
                artistName = displayArtist,
                statistics = statistics.takeIf { contentVisibility.showStatistics },
                avatarImage = artistAvatarImage,
                topPadding = statusBarTop,
                alias = artistMetadata?.aliases?.take(3)?.joinToString(" · "),
                biography = biography,
                bannerImageRequest = bannerImageRequest,
                artistColor = artistColor,
                focusRequested = artistProfileFocused,
                focusProgress = focusProgress,
                canFocus = biographyCanFocus,
                biographyScrollRequired = focusHeightTarget.biographyScrollRequired,
                biographyViewportHeight = biographyViewportHeight,
                collapsedBiographyViewportHeight = collapsedBiographyViewportHeight,
                bannerHeroHeight = presentationHeights.bannerHeroHeight,
                bannerAlpha = bannerAlpha,
                collapseProgress = headerCollapseProgress,
                expandedContentHeight = presentationHeights.cardHeight,
                onBannerLoading = {
                    if (
                        bannerPresentationState != ArtistBannerPresentationState.BannerReadyImmediately &&
                        bannerPresentationState != ArtistBannerPresentationState.BannerLoadedLate
                    ) {
                        bannerPresentationState = ArtistBannerPresentationState.BannerLoading
                    }
                },
                onBannerSuccess = {
                    bannerRequestSucceeded = true
                    bannerPresentationState = artistBannerSuccessState(
                        current = bannerPresentationState,
                        pageEnterComplete = pageTransition.phase == PageTransitionPhase.Current
                    )
                },
                onBannerError = {
                    bannerRequestSucceeded = false
                    if (
                        bannerPresentationState !=
                        ArtistBannerPresentationState.BannerReadyImmediately
                    ) {
                        bannerPresentationState = ArtistBannerPresentationState.BannerFailed
                    }
                },
                onBiographyMeasurementChanged = { biographyMeasurement = it },
                onClick = { artistProfileFocused = true },
                onBack = {
                    if (artistProfileFocused) collapseProfile() else onNavigateBack()
                },
                modifier = Modifier
                    .offset {
                        IntOffset(0, headerScrollPresentation.topPx.toInt())
                    }
                    .fillMaxWidth()
                    .height(headerViewportHeight)
                    .then(
                        pageTransition.elementAppearanceModifierAt(
                            pageProgress = listProgress,
                            order = 0,
                            orderCount = firstSongTransitionOrderCount,
                            translationOffsetScale = -0.4f
                        )
                    )
            )

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
    bannerImageRequest: ImageRequest? = null,
    artistColor: Color,
    focusRequested: Boolean,
    focusProgress: Float,
    canFocus: Boolean,
    biographyScrollRequired: Boolean,
    biographyViewportHeight: Dp,
    collapsedBiographyViewportHeight: Dp,
    bannerHeroHeight: Dp,
    bannerAlpha: Float,
    collapseProgress: Float,
    expandedContentHeight: Dp,
    onBannerLoading: () -> Unit,
    onBannerSuccess: () -> Unit,
    onBannerError: () -> Unit,
    onBiographyMeasurementChanged: (ArtistBiographyMeasurement) -> Unit,
    onClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayAlias = alias?.trim()?.takeIf(String::isNotEmpty)
    val displayBiography = biography?.trim()?.takeIf(String::isNotEmpty)
    val context = LocalContext.current
    val bannerImageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    val cardShape = RoundedCornerShape(
        bottomStart = ArtistHeaderCornerRadius,
        bottomEnd = ArtistHeaderCornerRadius
    )
    val clampedCollapseProgress = collapseProgress.coerceIn(0f, 1f)
    val expandedExitProgress = (
        (clampedCollapseProgress - 0.15f) / 0.85f
        ).coerceIn(0f, 1f)
    val dockedEnterProgress = (
        (clampedCollapseProgress - 0.45f) / 0.55f
        ).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - FlowtoneMotion.Easing.transform(expandedExitProgress)
    val dockedContentAlpha = FlowtoneMotion.Easing.transform(dockedEnterProgress)
    val density = LocalDensity.current
    val dockedHeaderHeight = topPadding + FlowtoneTopBarContentHeight
    val expandedContentTranslationYPx = with(density) {
        -(expandedContentHeight - dockedHeaderHeight).coerceAtLeast(0.dp).toPx() *
            clampedCollapseProgress
    }
    val dockedContentTranslationYPx = with(density) {
        8.dp.toPx() * (1f - dockedContentAlpha)
    }
    val heroPrimaryContentColor = lerpColor(
        MaterialTheme.colorScheme.onSurface,
        Color.White.copy(alpha = 0.94f),
        bannerAlpha
    )
    val heroSecondaryContentColor = lerpColor(
        MaterialTheme.colorScheme.onSurfaceVariant,
        Color.White.copy(alpha = 0.80f),
        bannerAlpha
    )
    BoxWithConstraints(
        modifier = modifier
            .clickable(
                enabled = canFocus && clampedCollapseProgress <= 0.001f,
                indication = null,
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(cardShape)
                .background(artistColor)
        )
        val avatarSize = if (maxWidth < 380.dp) ArtistCompactAvatarSize else ArtistAvatarSize
        Layout(
            content = {
                Box(modifier = Modifier.fillMaxSize()) {
                    bannerImageRequest?.let { request ->
                        val overlayGeometry = artistBannerHeroOverlayGeometry(bannerHeroHeight)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(overlayGeometry.heroHeight)
                                .clipToBounds()
                                .graphicsLayer { alpha = bannerAlpha }
                        ) {
                            AsyncImage(
                                model = request,
                                imageLoader = bannerImageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onLoading = { onBannerLoading() },
                                onSuccess = { onBannerSuccess() },
                                onError = { onBannerError() },
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(overlayGeometry.darkScrimHeight)
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0f to Color.Black.copy(
                                                    alpha = ArtistBannerDarkScrimAlpha
                                                ),
                                                0.44f to Color.Black.copy(
                                                    alpha = ArtistBannerDarkScrimAlpha * 0.86f
                                                ),
                                                0.68f to Color.Black.copy(
                                                    alpha = ArtistBannerDarkScrimAlpha * 0.46f
                                                ),
                                                0.88f to Color.Transparent,
                                                1f to Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(overlayGeometry.bottomBlendHeight)
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0f to Color.Transparent,
                                                ArtistBannerBottomFadeStartFraction to
                                                    Color.Transparent,
                                                0.70f to artistColor.copy(alpha = 0.35f),
                                                0.86f to artistColor.copy(alpha = 0.80f),
                                                1f to artistBannerBottomBlendFinalColor(
                                                    artistColor
                                                )
                                            )
                                        )
                                    )
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 20.dp,
                                top = topPadding +
                                    ArtistToolbarHeight +
                                    ArtistHeaderContentTopGap,
                                end = 20.dp,
                                bottom = ArtistHeaderBottomPadding
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArtistAvatar(
                                size = avatarSize,
                                image = avatarImage,
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            ArtistHeaderProfileDetails(
                                artistName = artistName,
                                alias = displayAlias,
                                statistics = statistics,
                                avatarSize = avatarSize,
                                primaryContentColor = heroPrimaryContentColor,
                                secondaryContentColor = heroSecondaryContentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = ArtistHeaderAvatarNameGap)
                            )
                        }
                        displayBiography?.let { text ->
                            BiographyRevealViewport(
                                text = text,
                                focusProgress = focusProgress,
                                focusRequested = focusRequested,
                                canFocus = canFocus,
                                scrollRequired = biographyScrollRequired,
                                collapsedViewportHeight = collapsedBiographyViewportHeight,
                                onMeasurementChanged = onBiographyMeasurementChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = ArtistBiographyTopSpacing)
                                    .height(biographyViewportHeight)
                            )
                        }
                        if (displayBiography == null) {
                            LaunchedEffect(Unit) {
                                onBiographyMeasurementChanged(ArtistBiographyMeasurement())
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .matchParentSize()
                .clip(cardShape)
                .graphicsLayer {
                    alpha = expandedContentAlpha
                    translationY = expandedContentTranslationYPx
                }
        ) { measurables, constraints ->
            // The outer height is only a viewport. Keep Profile content at its natural height so
            // scroll collapse clips/translates large identity elements instead of remeasuring them.
            val contentHeightPx = with(density) { expandedContentHeight.roundToPx() }
            val placeable = measurables.single().measure(
                Constraints.fixed(constraints.maxWidth, contentHeightPx)
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(0, 0)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .height(FlowtoneTopBarContentHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FlowtoneTopBarTitleStartPadding +
                            FlowtoneTopBarNavigationTitleShift,
                        end = 24.dp
                    )
                    .graphicsLayer {
                        alpha = dockedContentAlpha
                        translationY = dockedContentTranslationYPx
                    }
            ) {
                ArtistAvatar(
                    size = ArtistDockedAvatarSize,
                    image = avatarImage,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = heroPrimaryContentColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = ArtistDockedTitleGap)
                )
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .padding(start = 12.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = heroPrimaryContentColor
                )
            }
        }
    }
}

@Composable
private fun BiographyRevealViewport(
    text: String,
    focusProgress: Float,
    focusRequested: Boolean,
    canFocus: Boolean,
    scrollRequired: Boolean,
    collapsedViewportHeight: Dp,
    onMeasurementChanged: (ArtistBiographyMeasurement) -> Unit,
    modifier: Modifier = Modifier
) {
    val biographyStyle = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    LaunchedEffect(focusRequested) {
        if (!focusRequested && scrollState.value != 0) scrollState.scrollTo(0)
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds()
    ) {
        val biographyWidthPx = constraints.maxWidth
        val collapsedViewportHeightPx = with(density) {
            collapsedViewportHeight.roundToPx()
        }
        val fullBiographyLayout = remember(
            text,
            biographyStyle,
            biographyWidthPx,
            textMeasurer
        ) {
            textMeasurer.measure(
                text = text,
                style = biographyStyle,
                overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = biographyWidthPx)
            )
        }
        val measurement = remember(
            fullBiographyLayout.size.height,
            collapsedViewportHeightPx
        ) {
            ArtistBiographyMeasurement(
                fullTextHeightPx = fullBiographyLayout.size.height,
                collapsedViewportHeightPx = collapsedViewportHeightPx
            )
        }
        LaunchedEffect(text, biographyWidthPx, measurement) {
            onMeasurementChanged(measurement)
        }

        val edgeStrength = if (
            artistBiographyEdgeEffectEnabled(text, measurement)
        ) {
            artistBiographyEdgeStrength(focusProgress)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .biographyRevealEdge(
                    strength = edgeStrength,
                    edgeHeight = artistBiographyEdgeBandHeight(collapsedViewportHeight),
                    blurRadius = ArtistBiographyEdgeBlurRadius
                )
                .verticalScroll(
                    state = scrollState,
                    enabled = canFocus && scrollRequired && focusProgress >= 0.999f
                )
        ) {
            Text(
                text = text,
                style = biographyStyle,
                color = ArtistBiographyContentColor,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Modifier.biographyRevealEdge(
    strength: Float,
    edgeHeight: Dp,
    blurRadius: Dp
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val edgeStrength = strength.coerceIn(0f, 1f)
    val edgeHeightPx = edgeHeight.toPx().coerceAtMost(size.height)
    val edgeTop = (size.height - edgeHeightPx).coerceAtLeast(0f)
    val blurRadiusPx = blurRadius.toPx() * edgeStrength
    val blurPaint = NativePaint().apply {
        isAntiAlias = true
        alpha = (255f * 0.38f * edgeStrength).toInt().coerceIn(0, 255)
        if (blurRadiusPx > 0.1f) {
            maskFilter = BlurMaskFilter(
                blurRadiusPx,
                BlurMaskFilter.Blur.NORMAL
            )
        }
    }
    val edgeStartFraction = if (size.height > 0f) {
        (edgeTop / size.height).coerceIn(0f, 1f)
    } else {
        1f
    }
    val alphaMask = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Black,
            edgeStartFraction to Color.Black,
            1f to Color.Black.copy(alpha = 1f - edgeStrength)
        )
    )
    val blurStrengthMask = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black),
        startY = edgeTop,
        endY = size.height
    )

    onDrawWithContent {
        drawContent()
        if (edgeStrength > 0.001f && edgeHeightPx > 0f) {
            val contentDrawScope = this
            clipRect(top = edgeTop) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.saveLayer(
                        RectF(0f, edgeTop, size.width, size.height),
                        blurPaint
                    )
                    contentDrawScope.drawContent()
                    contentDrawScope.drawRect(
                        brush = blurStrengthMask,
                        blendMode = BlendMode.DstIn
                    )
                    canvas.nativeCanvas.restore()
                }
            }
            drawRect(
                brush = alphaMask,
                blendMode = BlendMode.DstIn
            )
        }
    }
}

@Composable
private fun ArtistHeaderProfileDetails(
    artistName: String,
    alias: String?,
    statistics: String?,
    avatarSize: Dp,
    primaryContentColor: Color,
    secondaryContentColor: Color,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineMedium,
                color = primaryContentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.layoutId("name")
            )
            alias?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.layoutId("alias")
                )
            }
            statistics?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.layoutId("statistics")
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
        layout(width, height) {
            name?.let { (y, placeable) -> placeable.placeRelative(0, y) }
            alias?.let { (y, placeable) -> placeable.placeRelative(0, y) }
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
    ArtistAlbumCard(
        title = album.title,
        supportingText = "${album.songs.size} 首歌曲",
        artworkUri = album.artworkUri,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun ArtistAlbumCard(
    title: String,
    supportingText: String,
    artworkUri: android.net.Uri? = null,
    extensionArtwork: ExtensionImage? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(ArtistAlbumArtworkSize)
            .clickable(onClick = onClick)
    ) {
        FlowtoneArtwork(
            artworkUri = artworkUri,
            extensionArtwork = extensionArtwork,
            modifier = Modifier.size(ArtistAlbumArtworkSize)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = supportingText,
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
