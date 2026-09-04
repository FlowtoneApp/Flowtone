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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import ink.tenqui.flowtone.app.ArtistAlbumHeaderSnapshot
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
import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.rememberPageElementEnterScope
import ink.tenqui.flowtone.ui.components.rememberPageElementExitScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.SongListItemSkeleton
import ink.tenqui.flowtone.ui.components.canOpenFullTitleOverlay
import ink.tenqui.flowtone.ui.components.topLevelPageBackground
import ink.tenqui.flowtone.ui.components.TopLevelBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.player.localSongsForArtist
import ink.tenqui.flowtone.ui.theme.monochromeFlowtoneCloudPalette

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
private val ArtistCollapsedHeaderMaskHeight = 32.dp
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

internal const val ArtistBiographyOverflowTolerancePx = 1

internal fun artistBiographyExceedsCollapsedViewport(
    measurement: ArtistBiographyMeasurement
): Boolean = measurement.fullTextHeightPx >
    measurement.collapsedViewportHeightPx + ArtistBiographyOverflowTolerancePx

internal fun canFocusArtistProfile(
    biography: String?,
    measurement: ArtistBiographyMeasurement
): Boolean = !biography.isNullOrBlank() &&
    artistBiographyExceedsCollapsedViewport(measurement)

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

internal fun artistEffectiveHeaderColor(
    retainedResolvedColor: Color?,
    candidateColor: Color,
    candidateSource: ArtistProfileBaseColorSource
): Color = if (candidateSource == ArtistProfileBaseColorSource.Material) {
    retainedResolvedColor ?: candidateColor
} else {
    candidateColor
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

internal enum class ArtistHeaderVariant { Banner, Cloud }

internal fun artistHeaderVariant(hasBanner: Boolean): ArtistHeaderVariant =
    if (hasBanner) ArtistHeaderVariant.Banner else ArtistHeaderVariant.Cloud

internal enum class ArtistCloudBackgroundOwner { Page, None }

internal fun artistCloudBackgroundOwner(variant: ArtistHeaderVariant): ArtistCloudBackgroundOwner =
    if (variant == ArtistHeaderVariant.Cloud) ArtistCloudBackgroundOwner.Page
    else ArtistCloudBackgroundOwner.None

internal val ArtistPageTopCloudPlacement = TopLevelBackgroundCloudPlacement(
    cloudCenterWidthFraction = 0.5f,
    cloudCenterRadiusOffsetXFactor = 0f,
    cloudCenterRadiusOffsetYFactor = -0.72f,
    clearCenterWidthFraction = 0.5f,
    clearCenterHeightFraction = 1f
)

internal fun artistHeaderUsesRoundedBiographyEdge(variant: ArtistHeaderVariant): Boolean = when (
    variant
) {
    ArtistHeaderVariant.Banner,
    ArtistHeaderVariant.Cloud -> true
}

internal fun artistExpandedContentOffsetPx(
    expandedHeightPx: Float,
    visibleHeightPx: Float
): Float = visibleHeightPx.coerceAtMost(expandedHeightPx) - expandedHeightPx

internal data class ArtistHeaderTitleTransition(
    val artistAlpha: Float = 1f,
    val albumSuffixAlpha: Float,
    val albumSuffixTranslationFraction: Float
)

internal fun artistHeaderTitleTransition(progress: Float): ArtistHeaderTitleTransition {
    val localProgress = progress.coerceIn(0f, 1f)
    return ArtistHeaderTitleTransition(
        albumSuffixAlpha = localProgress,
        albumSuffixTranslationFraction = 1f - localProgress
    )
}

internal fun artistCloudHeaderSurfaceAlpha(
    collapseProgress: Float,
    focusProgress: Float
): Float = maxOf(collapseProgress, focusProgress).coerceIn(0f, 1f)

internal data class ArtistCollapsedSoftMaskBounds(
    val topPx: Float,
    val bottomPx: Float
)

internal fun artistCollapsedSoftMaskBounds(
    visibleHeightPx: Float,
    maskHeightPx: Float,
    progress: Float
): ArtistCollapsedSoftMaskBounds {
    val height = visibleHeightPx.coerceAtLeast(0f)
    val bandHeight = maskHeightPx.coerceAtLeast(0f)
    val bottom = height * (1f - progress.coerceIn(0f, 1f))
    return ArtistCollapsedSoftMaskBounds(
        topPx = bottom - bandHeight,
        bottomPx = bottom
    )
}

internal fun artistDockedContentTarget(collapseProgress: Float): Float =
    if (collapseProgress >= 1f) 1f else 0f

@Stable
internal class ArtistHeaderPresentationHostState {
    private val contentBySession = mutableStateMapOf<String, @Composable () -> Unit>()

    internal fun publish(
        sessionKey: String,
        content: @Composable () -> Unit
    ) {
        contentBySession[sessionKey] = content
    }

    internal fun retainSessions(sessionKeys: Set<String>) {
        contentBySession.keys.toList().forEach { sessionKey ->
            if (sessionKey !in sessionKeys) contentBySession.remove(sessionKey)
        }
    }

    @Composable
    internal fun Render(sessionKey: String?) {
        sessionKey?.let(contentBySession::get)?.invoke()
    }

    internal fun hasPresentation(sessionKey: String): Boolean =
        contentBySession.containsKey(sessionKey)
}

@Composable
internal fun rememberArtistHeaderPresentationHostState(): ArtistHeaderPresentationHostState =
    remember { ArtistHeaderPresentationHostState() }

internal data class ArtistDockedTitlePresentation(
    val showAvatarAndBreadcrumb: Boolean,
    val showTitle: Boolean = true,
    val currentTitleUsesEllipsis: Boolean = true
)

internal fun artistDockedTitlePresentation(
    naturalContentWidthPx: Float,
    availableWidthPx: Float
): ArtistDockedTitlePresentation = ArtistDockedTitlePresentation(
    showAvatarAndBreadcrumb = naturalContentWidthPx <= availableWidthPx.coerceAtLeast(0f)
)

internal enum class ArtistDockedAlbumTitleLayout { Breadcrumb, AlbumOnly }

internal fun artistDockedAlbumTitleLayout(
    breadcrumbNaturalWidthPx: Float,
    availableWidthPx: Float
): ArtistDockedAlbumTitleLayout = if (
    breadcrumbNaturalWidthPx <= availableWidthPx.coerceAtLeast(0f)
) {
    ArtistDockedAlbumTitleLayout.Breadcrumb
} else {
    ArtistDockedAlbumTitleLayout.AlbumOnly
}

internal fun artistTransitionCollapseProgress(
    scrollCollapseProgress: Float,
    pagePhase: PageTransitionPhase,
    pageProgress: Float,
    albumBridgeActive: Boolean
): Float {
    val scroll = scrollCollapseProgress.coerceIn(0f, 1f)
    if (!albumBridgeActive) return scroll
    val transition = artistAlbumHeaderLocalProgress(pagePhase, pageProgress)
    val bridgeTarget = when (pagePhase) {
        PageTransitionPhase.Outgoing -> transition
        PageTransitionPhase.Incoming -> transition
        PageTransitionPhase.Current -> 0f
    }
    return scroll + (1f - scroll) * bridgeTarget
}

internal fun artistAlbumHeaderLocalProgress(
    pagePhase: PageTransitionPhase,
    pageProgress: Float
): Float {
    val itemLocalProgress = PageMotion.elementProgress(
        pageProgress = pageProgress,
        order = 0,
        orderCount = ArtistTransitionOrderCount
    )
    return when (pagePhase) {
        PageTransitionPhase.Outgoing -> itemLocalProgress
        PageTransitionPhase.Incoming -> 1f - itemLocalProgress
        PageTransitionPhase.Current -> 0f
    }
}

internal fun artistAlbumBreadcrumbProgress(
    pagePhase: PageTransitionPhase,
    pageProgress: Float,
    albumBridgeActive: Boolean
): Float {
    if (!albumBridgeActive) return 0f
    return when (pagePhase) {
        PageTransitionPhase.Outgoing,
        PageTransitionPhase.Incoming -> artistAlbumHeaderLocalProgress(
            pagePhase = pagePhase,
            pageProgress = pageProgress
        )
        // The parent Artist is still Current for the composition in which Album is pushed.
        // Starting at zero prevents the new suffix from drawing at its destination for one frame
        // before PageTransitionHost installs the outgoing/incoming pair.
        PageTransitionPhase.Current -> 0f
    }
}

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
    headerOwnerKey: String,
    headerPresentationHostState: ArtistHeaderPresentationHostState,
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
    providerSongsLoaded: Boolean = true,
    currentSong: Song?,
    onNavigateBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onProviderSongClick: (List<ProviderSong>, Int) -> Unit = { _, _ -> },
    onOpenAlbum: (Long) -> Unit,
    onOpenProviderAlbum: (ProviderAlbum) -> Unit = {},
    albumTransitionSnapshot: ArtistAlbumHeaderSnapshot? = null,
    onFullTitleRequest: (String) -> Unit = {},
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
    val primaryContentPresentation = artistPrimaryContentPresentation(
        hasLocalContent = hasLocalContent,
        providerSongsLoaded = providerSongsLoaded,
        hasSongs = presentedArtistSongs.isNotEmpty()
    )
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
        primaryContentPresentation,
        statistics
    ) {
        artistPageContentVisibility(
            hasLocalContent = hasLocalContent,
            hasSongs = artistProviderSongs.isNotEmpty(),
            songsLoading = primaryContentPresentation ==
                ArtistPrimaryContentPresentation.Loading,
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
    val primaryContentPresentationKeys = remember(
        primaryContentPresentation,
        artistSongKeys
    ) {
        artistPrimaryContentPresentationKeys(primaryContentPresentation, artistSongKeys)
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
    val headerVariant = artistHeaderVariant(banner != null)
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
    var retainedResolvedHeaderColor by remember(headerOwnerKey) {
        mutableStateOf<Color?>(null)
    }
    LaunchedEffect(baseColorSource, targetProfileColor) {
        if (baseColorSource != ArtistProfileBaseColorSource.Material) {
            retainedResolvedHeaderColor = targetProfileColor
        }
    }
    val effectiveHeaderColor = artistEffectiveHeaderColor(
        retainedResolvedColor = retainedResolvedHeaderColor,
        candidateColor = targetProfileColor,
        candidateSource = baseColorSource
    )
    val artistColor by animateColorAsState(
        targetValue = effectiveHeaderColor,
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

    var displayedPrimaryContentPresentation by remember(headerOwnerKey) {
        mutableStateOf(primaryContentPresentation)
    }
    val latestPrimaryContentPresentation by rememberUpdatedState(primaryContentPresentation)
    val skeletonReplacementExitActive =
        displayedPrimaryContentPresentation == ArtistPrimaryContentPresentation.Loading &&
            primaryContentPresentation != ArtistPrimaryContentPresentation.Loading &&
            pageTransition.phase == PageTransitionPhase.Current
    LaunchedEffect(primaryContentPresentation, pageTransition.phase) {
        if (
            displayedPrimaryContentPresentation != primaryContentPresentation &&
            displayedPrimaryContentPresentation != ArtistPrimaryContentPresentation.Loading
        ) {
            displayedPrimaryContentPresentation = primaryContentPresentation
        }
    }
    val displayedPrimaryContentPresentationKeys = remember(
        displayedPrimaryContentPresentation,
        primaryContentPresentationKeys
    ) {
        if (displayedPrimaryContentPresentation == ArtistPrimaryContentPresentation.Loading) {
            artistPrimaryContentPresentationKeys(
                ArtistPrimaryContentPresentation.Loading,
                readySongKeys = emptyList()
            )
        } else {
            primaryContentPresentationKeys
        }
    }

    val visibleSongKeys by remember(listState, displayedPrimaryContentPresentationKeys) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                displayedPrimaryContentPresentationKeys.getOrNull(
                    item.index - ArtistFirstSongListItemIndex
                )
            }.distinct().ifEmpty {
                displayedPrimaryContentPresentationKeys
                    .take(ArtistLoadingSkeletonCount)
            }
        }
    }
    val skeletonExitScope = if (skeletonReplacementExitActive) {
        rememberPageElementExitScope(
            sessionKey = "$headerOwnerKey:skeleton-replacement",
            elementKeys = displayedPrimaryContentPresentationKeys,
            viewportKeys = visibleSongKeys,
            onFinished = {
                displayedPrimaryContentPresentation = latestPrimaryContentPresentation
            }
        )
    } else {
        null
    }
    val initiallyReadySongKeys = remember(headerOwnerKey) {
        if (primaryContentPresentation == ArtistPrimaryContentPresentation.Ready) {
            primaryContentPresentationKeys.toSet()
        } else {
            emptySet()
        }
    }
    val readySongEnterScope = rememberPageElementEnterScope(
        sessionKey = "$headerOwnerKey:ready-songs",
        elementKeys = if (
            pageTransition.phase == PageTransitionPhase.Current &&
            displayedPrimaryContentPresentation == ArtistPrimaryContentPresentation.Ready
        ) {
            primaryContentPresentationKeys
        } else {
            emptyList()
        },
        viewportKeys = visibleSongKeys,
        awaitViewportKeys = true,
        initiallyEnteredKeys = initiallyReadySongKeys
    )
    if (
        pageTransition.phase != PageTransitionPhase.Current &&
        displayedPrimaryContentPresentation == ArtistPrimaryContentPresentation.Ready
    ) {
        SideEffect { readySongEnterScope.markEntered(primaryContentPresentationKeys) }
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

    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .rightSwipeBackGesture {
                if (artistProfileFocused) collapseProfile() else onNavigateBack()
            }
    ) {
        if (artistCloudBackgroundOwner(headerVariant) == ArtistCloudBackgroundOwner.Page) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .topLevelPageBackground(
                        cloudPalette = monochromeFlowtoneCloudPalette(effectiveHeaderColor),
                        cloudPlacement = ArtistPageTopCloudPlacement
                    )
            )
        }
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
            if (
                contentVisibility.showSongs ||
                displayedPrimaryContentPresentation == ArtistPrimaryContentPresentation.Loading
            ) {
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
                if (
                    displayedPrimaryContentPresentation ==
                    ArtistPrimaryContentPresentation.Loading
                ) {
                    items(
                        count = ArtistLoadingSkeletonCount,
                        key = { index -> displayedPrimaryContentPresentationKeys[index] }
                    ) { index ->
                        val itemKey = displayedPrimaryContentPresentationKeys[index]
                        val viewportOrder = animationOrderByKey[itemKey] ?: index
                        SongListItemSkeleton(
                            modifier = if (skeletonExitScope != null) {
                                skeletonExitScope.elementModifier(itemKey)
                            } else if (enterGroupReady) {
                                itemModifier(
                                    listProgress,
                                    viewportOrder,
                                    animationGroupKeys.size.coerceAtLeast(1)
                                )
                            } else {
                                Modifier.graphicsLayer { alpha = 0f }
                            }.padding(horizontal = 8.dp)
                        )
                    }
                } else if (presentedArtistSongs.isEmpty()) {
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
                            modifier = if (pageTransition.phase == PageTransitionPhase.Current) {
                                readySongEnterScope.elementModifier(songKey)
                            } else if (enterGroupReady) {
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
        val headerCollapseProgress = artistTransitionCollapseProgress(
            scrollCollapseProgress = headerScrollPresentation.collapseProgress,
            pagePhase = pageTransition.phase,
            pageProgress = pageTransition.progress,
            albumBridgeActive = albumTransitionSnapshot != null
        )
        val scrollDockedContentProgress by animateFloatAsState(
            targetValue = artistDockedContentTarget(
                headerScrollPresentation.collapseProgress
            ),
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = FlowtoneMotion.Easing
            ),
            label = "ArtistHeaderDockedContentProgress"
        )
        val albumHeaderProgress = if (albumTransitionSnapshot == null) {
            0f
        } else {
            artistAlbumHeaderLocalProgress(
                pagePhase = pageTransition.phase,
                pageProgress = pageTransition.progress
            )
        }
        val dockedContentProgress = maxOf(
            scrollDockedContentProgress,
            albumHeaderProgress
        ).coerceIn(0f, 1f)
        val albumBreadcrumbProgress = artistAlbumBreadcrumbProgress(
            pagePhase = pageTransition.phase,
            pageProgress = pageTransition.progress,
            albumBridgeActive = albumTransitionSnapshot != null
        )
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
        val scrollingHeaderHeight = lerp(
            collapsedProfileCardHeight,
            dockedHeaderHeight,
            headerCollapseProgress
        )
        val headerViewportHeight = if (focusPresentationActive) {
            presentationHeights.cardHeight
        } else {
            scrollingHeaderHeight
        }
        val headerPageModifier = if (albumTransitionSnapshot != null) {
            Modifier
        } else {
            pageTransition.elementAppearanceModifierAt(
                pageProgress = pageTransition.progress,
                order = ArtistPrimaryContentTimingOrder,
                orderCount = ArtistLoadingSkeletonCount,
                translationOffsetScale = -0.4f
            )
        }
        val headerContent: @Composable () -> Unit = {
            ArtistHeaderHost(
                variant = headerVariant,
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
                dockedContentProgress = dockedContentProgress,
                albumTitle = albumTransitionSnapshot?.albumTitle,
                albumPresentationKey = albumTransitionSnapshot?.albumEntryKey,
                albumBreadcrumbProgress = albumBreadcrumbProgress,
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
                onFullTitleRequest = onFullTitleRequest,
                modifier = Modifier
                    .offset {
                        IntOffset(0, headerScrollPresentation.topPx.toInt())
                    }
                    .fillMaxWidth()
                    .height(headerViewportHeight)
                    .then(headerPageModifier)
            )
        }
        val currentHeaderContent by rememberUpdatedState(headerContent)
        val stableHeaderContent: @Composable () -> Unit = remember(headerOwnerKey) {
            { currentHeaderContent() }
        }
        SideEffect {
            headerPresentationHostState.publish(
                sessionKey = headerOwnerKey,
                content = stableHeaderContent
            )
        }

    }
}

@Composable
private fun ArtistHeaderHost(
    variant: ArtistHeaderVariant,
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
    dockedContentProgress: Float,
    albumTitle: String?,
    albumPresentationKey: String?,
    albumBreadcrumbProgress: Float,
    expandedContentHeight: Dp,
    onBannerLoading: () -> Unit,
    onBannerSuccess: () -> Unit,
    onBannerError: () -> Unit,
    onBiographyMeasurementChanged: (ArtistBiographyMeasurement) -> Unit,
    onClick: () -> Unit,
    onBack: () -> Unit,
    onFullTitleRequest: (String) -> Unit,
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
    val dockedContentAlpha = dockedContentProgress.coerceIn(0f, 1f)
    val density = LocalDensity.current
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
            .then(
                if (artistHeaderUsesRoundedBiographyEdge(variant)) {
                    Modifier.clip(cardShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                enabled = canFocus && clampedCollapseProgress <= 0.001f,
                indication = null,
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                onClick = onClick
            )
    ) {
        val expandedContentOffsetYPx = artistExpandedContentOffsetPx(
            expandedHeightPx = with(density) { expandedContentHeight.toPx() },
            visibleHeightPx = with(density) { maxHeight.toPx() }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (variant == ArtistHeaderVariant.Banner) {
                        Modifier
                            .background(artistColor)
                    } else {
                        Modifier.background(
                            artistColor.copy(
                                alpha = artistCloudHeaderSurfaceAlpha(
                                    collapseProgress = clampedCollapseProgress,
                                    focusProgress = focusProgress
                                )
                            )
                        )
                    }
                )
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
                                contentColor = if (variant == ArtistHeaderVariant.Banner) {
                                    ArtistBiographyContentColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
                .then(
                    if (variant == ArtistHeaderVariant.Banner) {
                        Modifier.artistCollapsedContentSoftMask(
                            progress = dockedContentAlpha,
                            maskHeight = ArtistCollapsedHeaderMaskHeight,
                            blurRadius = ArtistBiographyEdgeBlurRadius
                        )
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    translationY = expandedContentOffsetYPx
                    alpha = 1f - dockedContentAlpha
                }
        ) { measurables, constraints ->
            // The outer height is only a viewport. Keep expanded content at its natural height;
            // the moving bottom reveal hides it without translating its coordinate space.
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
            ArtistDockedIdentityRow(
                artistName = artistName,
                avatarImage = avatarImage,
                albumTitle = albumTitle,
                albumPresentationKey = albumPresentationKey,
                albumBreadcrumbProgress = albumBreadcrumbProgress,
                contentColor = heroPrimaryContentColor,
                onFullTitleRequest = onFullTitleRequest,
                interactionEnabled = dockedContentAlpha >= 0.999f,
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
            )
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
private fun ArtistDockedIdentityRow(
    artistName: String,
    avatarImage: ExtensionImage?,
    albumTitle: String?,
    albumPresentationKey: String?,
    albumBreadcrumbProgress: Float,
    contentColor: Color,
    onFullTitleRequest: (String) -> Unit,
    interactionEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bridgeProgress = albumBreadcrumbProgress.coerceIn(0f, 1f)
    val titleTransition = artistHeaderTitleTransition(bridgeProgress)
    val pathMotionDistancePx = with(density) { 10.dp.toPx() }
    var artistHasVisualOverflow by remember(artistName) { mutableStateOf(false) }
    var albumHasVisualOverflow by remember(albumTitle) { mutableStateOf(false) }
    val artistInteractionSource = remember(artistName) {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val albumInteractionSource = remember(albumTitle) {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val avatarAndGapWidthPx = with(density) {
            ArtistDockedAvatarSize.toPx() + ArtistDockedTitleGap.toPx()
        }
        val artistWidthPx = textMeasurer.measure(artistName, textStyle).size.width.toFloat()
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val artistPresentation = artistDockedTitlePresentation(
            naturalContentWidthPx = avatarAndGapWidthPx + artistWidthPx,
            availableWidthPx = availableWidthPx
        )
        if (albumTitle == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (artistPresentation.showAvatarAndBreadcrumb) {
                    ArtistAvatar(
                        size = ArtistDockedAvatarSize,
                        image = avatarImage,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = artistName,
                    style = textStyle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        artistHasVisualOverflow = result.hasVisualOverflow
                    },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (artistPresentation.showAvatarAndBreadcrumb) {
                                Modifier.padding(start = ArtistDockedTitleGap)
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            enabled = interactionEnabled &&
                                canOpenFullTitleOverlay(artistHasVisualOverflow),
                            interactionSource = artistInteractionSource,
                            indication = null
                        ) { onFullTitleRequest(artistName) }
                )
            }
        } else {
            val separatorWidthPx = textMeasurer.measure(" / ", textStyle).size.width.toFloat()
            val albumWidthPx = textMeasurer.measure(albumTitle, textStyle).size.width.toFloat()
            val albumLayout = artistDockedAlbumTitleLayout(
                breadcrumbNaturalWidthPx = avatarAndGapWidthPx + artistWidthPx +
                    separatorWidthPx + albumWidthPx,
                availableWidthPx = availableWidthPx
            )
            key(checkNotNull(albumPresentationKey)) {
                if (albumLayout == ArtistDockedAlbumTitleLayout.Breadcrumb) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ArtistAvatar(
                            size = ArtistDockedAvatarSize,
                            image = avatarImage,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = artistName,
                            style = textStyle,
                            color = contentColor,
                            maxLines = 1,
                            modifier = Modifier.padding(start = ArtistDockedTitleGap)
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = titleTransition.albumSuffixAlpha
                                    translationX = pathMotionDistancePx *
                                        titleTransition.albumSuffixTranslationFraction
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = " / ",
                                style = textStyle,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 1
                            )
                            Text(
                                text = albumTitle,
                                style = textStyle,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    albumHasVisualOverflow = result.hasVisualOverflow
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        enabled = interactionEnabled &&
                                            titleTransition.albumSuffixAlpha >= 0.999f &&
                                            canOpenFullTitleOverlay(albumHasVisualOverflow),
                                        interactionSource = albumInteractionSource,
                                        indication = null
                                    ) { onFullTitleRequest(albumTitle) }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = 1f - bridgeProgress
                                    translationX = -pathMotionDistancePx * bridgeProgress
                                }
                        ) {
                            if (artistPresentation.showAvatarAndBreadcrumb) {
                                ArtistAvatar(
                                    size = ArtistDockedAvatarSize,
                                    image = avatarImage,
                                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = artistName,
                                style = textStyle,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (artistPresentation.showAvatarAndBreadcrumb) {
                                            Modifier.padding(start = ArtistDockedTitleGap)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                        Text(
                            text = albumTitle,
                            style = textStyle,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                albumHasVisualOverflow = result.hasVisualOverflow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = titleTransition.albumSuffixAlpha
                                    translationX = pathMotionDistancePx *
                                        titleTransition.albumSuffixTranslationFraction
                                }
                                .clickable(
                                    enabled = interactionEnabled &&
                                        titleTransition.albumSuffixAlpha >= 0.999f &&
                                        canOpenFullTitleOverlay(albumHasVisualOverflow),
                                    interactionSource = albumInteractionSource,
                                    indication = null
                                ) { onFullTitleRequest(albumTitle) }
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.artistCollapsedContentSoftMask(
    progress: Float,
    maskHeight: Dp,
    blurRadius: Dp
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val transitionProgress = progress.coerceIn(0f, 1f)
    val bounds = artistCollapsedSoftMaskBounds(
        visibleHeightPx = size.height,
        maskHeightPx = maskHeight.toPx(),
        progress = transitionProgress
    )
    val blurPaint = NativePaint().apply {
        isAntiAlias = true
        alpha = (255f * 0.38f).toInt()
        val radiusPx = blurRadius.toPx()
        if (radiusPx > 0.1f) {
            maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    val alphaMask = Brush.verticalGradient(
        colors = listOf(Color.Black, Color.Transparent),
        startY = bounds.topPx,
        endY = bounds.bottomPx.coerceAtLeast(bounds.topPx + 1f)
    )
    val blurStrengthMask = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black),
        startY = bounds.topPx,
        endY = bounds.bottomPx.coerceAtLeast(bounds.topPx + 1f)
    )

    onDrawWithContent {
        drawContent()
        if (transitionProgress > 0.001f) {
            val visibleTop = bounds.topPx.coerceAtLeast(0f)
            val visibleBottom = bounds.bottomPx.coerceAtMost(size.height)
            if (visibleBottom > visibleTop) {
                val contentDrawScope = this
                clipRect(top = visibleTop, bottom = visibleBottom) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.saveLayer(
                            RectF(0f, visibleTop, size.width, visibleBottom),
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
            }
            drawRect(brush = alphaMask, blendMode = BlendMode.DstIn)
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
    contentColor: Color,
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
                color = contentColor,
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
