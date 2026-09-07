package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.online.ArtistMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.ui.components.ArtworkPaletteMemoryCache
import ink.tenqui.flowtone.ui.components.artworkPaletteCacheIdentity
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionPresentation
import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.HomeBackgroundCloudPlacement

class ArtistPageContentTest {
    @Test
    fun providerCountsWithoutEntitiesDoNotShowSections() {
        val visibility = artistPageContentVisibility(
            hasLocalContent = false,
            hasSongs = false,
            hasAlbums = false,
            hasStatistics = true
        )

        assertEquals(true, visibility.showStatistics)
        assertEquals(false, visibility.showSongs)
        assertEquals(false, visibility.showAlbums)
    }

    @Test
    fun providerSectionsRequireRealEntities() {
        val visibility = artistPageContentVisibility(
            hasLocalContent = false,
            hasSongs = true,
            hasAlbums = true,
            hasStatistics = false
        )

        assertEquals(true, visibility.showSongs)
        assertEquals(true, visibility.showAlbums)
    }
    @Test
    fun singleArtistSongMakesAlbumBelongToArtist() {
        assertEquals(true, artistMatchesAlbumSongs("A", listOf("A")))
    }

    @Test
    fun collaborativeSongMakesAlbumBelongToEachArtist() {
        assertEquals(true, artistMatchesAlbumSongs("A", listOf("A / B")))
        assertEquals(true, artistMatchesAlbumSongs("B", listOf("A / B")))
    }

    @Test
    fun membershipUsesTrimmedCaseInsensitiveArtistIdentity() {
        assertEquals(true, artistMatchesAlbumSongs("artist a", listOf("  Artist A  ")))
    }

    @Test
    fun unrelatedArtistDoesNotMatchAlbum() {
        assertEquals(false, artistMatchesAlbumSongs("B", listOf("A")))
    }

    @Test
    fun albumWithoutSongsDoesNotMatch() {
        assertEquals(false, artistMatchesAlbumSongs("A", emptyList()))
    }

    @Test
    fun statisticsOmitAlbumCountWhenThereAreNoAlbums() {
        assertEquals("18 首歌曲", artistStatisticsText(songCount = 18, albumCount = 0))
        assertEquals("18 首歌曲\n3 张专辑", artistStatisticsText(songCount = 18, albumCount = 3))
    }

    @Test
    fun providerProfileStatisticsKeepZeroDistinctFromUnknown() {
        assertEquals("0 首歌曲\n10 张专辑", artistMetadataStatisticsText(songCount = 0, albumCount = 10))
        assertEquals("0 张专辑", artistMetadataStatisticsText(songCount = null, albumCount = 0))
        assertEquals(null, artistMetadataStatisticsText(songCount = null, albumCount = null))
    }

    @Test
    fun providerOnlyArtistHidesUnavailableContentInsteadOfShowingEmptyLocalSections() {
        assertEquals(
            ArtistPageContentVisibility(
                showStatistics = false,
                showSongs = false,
                showAlbums = false
            ),
            artistPageContentVisibility(hasLocalContent = false, hasAlbums = false)
        )
    }

    @Test
    fun providerProfileCountsShowStatisticsWithoutCreatingEntitySections() {
        assertEquals(
            ArtistPageContentVisibility(
                showStatistics = true,
                showSongs = false,
                showAlbums = false
            ),
            artistPageContentVisibility(
                hasLocalContent = false,
                hasAlbums = false,
                hasStatistics = true
            )
        )
    }

    @Test
    fun completeDestinationMetadataDoesNotNeedResolver() {
        val destination = ArtistMetadata(
            aliases = listOf("Destination Alias"),
            biography = "Destination biography",
            songCount = 12,
            albumCount = 3
        )

        assertFalse(artistMetadataNeedsResolver("Artist", destination))
    }

    @Test
    fun resolverCompletesDestinationCountsWithAliasesAndBiography() {
        val merged = mergeArtistMetadata(
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(songCount = 20, albumCount = 4),
            resolverMetadata = ArtistMetadata(
                aliases = listOf("Registry Alias"),
                biography = "Resolver biography"
            )
        )

        assertEquals(listOf("Registry Alias"), merged?.aliases)
        assertEquals("Resolver biography", merged?.biography)
        assertEquals(20, merged?.songCount)
        assertEquals(4, merged?.albumCount)
    }

    @Test
    fun destinationBiographyAndAliasesTakePrecedenceWhileResolverAliasesAreDeduplicated() {
        val merged = mergeArtistMetadata(
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(
                aliases = listOf("Destination Alias"),
                biography = "Destination biography"
            ),
            resolverMetadata = ArtistMetadata(
                aliases = listOf("destination alias", "Resolver Alias", "Artist"),
                biography = "Resolver biography"
            )
        )

        assertEquals(listOf("Destination Alias", "Resolver Alias"), merged?.aliases)
        assertEquals("Destination biography", merged?.biography)
    }

    @Test
    fun destinationZeroSongCountWinsOverResolverCount() {
        val merged = mergeArtistMetadata(
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(songCount = 0),
            resolverMetadata = ArtistMetadata(songCount = 10)
        )

        assertEquals(0, merged?.songCount)
    }

    @Test
    fun resolverFillsUnknownCounts() {
        val merged = mergeArtistMetadata(
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(aliases = listOf("Destination Alias")),
            resolverMetadata = ArtistMetadata(songCount = 10, albumCount = 2)
        )

        assertEquals(10, merged?.songCount)
        assertEquals(2, merged?.albumCount)
        assertTrue(artistMetadataNeedsResolver("Artist", ArtistMetadata(aliases = listOf("Destination Alias"))))
    }

    @Test
    fun countsAreNeverAddedTogether() {
        val merged = mergeArtistMetadata(
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(songCount = 8, albumCount = 1),
            resolverMetadata = ArtistMetadata(songCount = 12, albumCount = 3)
        )

        assertEquals(8, merged?.songCount)
        assertEquals(1, merged?.albumCount)
    }

    @Test fun nullBiographyCannotFocus() {
        val overflowingMeasurement = ArtistBiographyMeasurement(
            fullTextHeightPx = 80,
            collapsedViewportHeightPx = 20
        )

        assertFalse(canFocusArtistProfile(null, overflowingMeasurement))
        assertFalse(canFocusArtistProfile("  ", overflowingMeasurement))
    }

    @Test fun shortBiographyDisablesFocusAndEdgeEffect() {
        val measurement = ArtistBiographyMeasurement(
            fullTextHeightPx = 40,
            collapsedViewportHeightPx = 40
        )

        assertFalse(canFocusArtistProfile("Short biography", measurement))
        assertFalse(artistBiographyEdgeEffectEnabled("Short biography", measurement))
    }

    @Test fun longBiographyEnablesFocusAndEdgeEffect() {
        val measurement = ArtistBiographyMeasurement(
            fullTextHeightPx = 80,
            collapsedViewportHeightPx = 40
        )

        assertTrue(canFocusArtistProfile("Long biography", measurement))
        assertTrue(artistBiographyEdgeEffectEnabled("Long biography", measurement))
    }

    @Test fun collapsedBiographyViewportUsesTwoMeasuredLineHeights() {
        val collapsedViewport = artistBiographyCollapsedViewportHeight(20.dp)

        assertEquals(40.dp, collapsedViewport)
        assertEquals(20.dp, artistBiographyEdgeBandHeight(collapsedViewport))
        assertEquals(40.dp, artistBiographyViewportHeight(collapsedViewport, 300.dp, 0f))
        assertEquals(1f, artistBiographyEdgeStrength(0f))
    }

    @Test fun focusedBiographyUsesExpandedViewportWithoutRevealEdge() {
        assertEquals(300.dp, artistBiographyViewportHeight(40.dp, 300.dp, 1f))
        assertEquals(0f, artistBiographyEdgeStrength(1f))
    }

    @Test fun biographyViewportInterpolatesContinuouslyAtIntermediateProgress() {
        val lateEdgeStrength = artistBiographyEdgeStrength(0.8f)

        assertEquals(170.dp, artistBiographyViewportHeight(40.dp, 300.dp, 0.5f))
        assertEquals(1f, artistBiographyEdgeStrength(0.5f))
        assertTrue(lateEdgeStrength > 0f)
        assertTrue(lateEdgeStrength < 1f)
    }

    @Test fun biographyRevealUsesTheSameProgressInBothDirections() {
        val entering = listOf(0f, 0.35f, 0.7f, 1f).map { progress ->
            artistBiographyViewportHeight(40.dp, 300.dp, progress) to
                artistBiographyEdgeStrength(progress)
        }
        val collapsing = listOf(1f, 0.7f, 0.35f, 0f).map { progress ->
            artistBiographyViewportHeight(40.dp, 300.dp, progress) to
                artistBiographyEdgeStrength(progress)
        }

        assertEquals(entering.reversed(), collapsing)
    }

    @Test fun biographyColorIsAlwaysWhiteAcrossFocusPresentation() {
        val collapsedColor = ArtistBiographyContentColor
        val focusedColor = ArtistBiographyContentColor

        assertEquals(Color.White, collapsedColor)
        assertEquals(collapsedColor, focusedColor)
    }

    @Test fun focusedBackCollapsesBeforeNavigation() {
        assertEquals(ArtistProfileBackResult.CollapseProfile, artistProfileBackResult(true))
        assertEquals(ArtistProfileBackResult.NavigateBack, artistProfileBackResult(false))
    }

    @Test fun artistHeaderFollowsAnchorBeforeDockThreshold() {
        val presentation = artistHeaderScrollPresentation(
            anchorTopPx = 24f,
            dockedTopPx = 8f,
            expandedHeightPx = 300f,
            dockedHeightPx = 80f
        )

        assertEquals(24f, presentation.topPx)
        assertEquals(0f, presentation.collapseProgress)
        assertEquals(300f, presentation.visibleHeightPx)
    }

    @Test fun artistHeaderPinsAndBecomesDockedAtCollapseDistance() {
        val presentation = artistHeaderScrollPresentation(
            anchorTopPx = -212f,
            dockedTopPx = 8f,
            expandedHeightPx = 300f,
            dockedHeightPx = 80f
        )

        assertEquals(8f, presentation.topPx)
        assertEquals(1f, presentation.collapseProgress)
        assertEquals(80f, presentation.visibleHeightPx)
    }

    @Test fun scrollingFurtherKeepsArtistHeaderPinnedAndDocked() {
        val presentation = artistHeaderScrollPresentation(
            anchorTopPx = -640f,
            dockedTopPx = 8f,
            expandedHeightPx = 300f,
            dockedHeightPx = 80f
        )

        assertEquals(8f, presentation.topPx)
        assertEquals(1f, presentation.collapseProgress)
        assertEquals(80f, presentation.visibleHeightPx)
    }

    @Test fun collapsingViewportDoesNotChangeExpandedHeaderContentHeight() {
        val presentation = artistHeaderScrollPresentation(
            anchorTopPx = -102f,
            dockedTopPx = 8f,
            expandedHeightPx = 300f,
            dockedHeightPx = 80f
        )

        assertEquals(190f, presentation.visibleHeightPx)
        assertEquals(300f, presentation.expandedContentHeightPx)
    }

    @Test fun restoredArtistScrollDerivesDockedHeaderWithoutSavedFlag() {
        val restored = artistHeaderScrollPresentation(
            anchorTopPx = -500f,
            dockedTopPx = 0f,
            expandedHeightPx = 280f,
            dockedHeightPx = 72f
        )
        val newEntry = artistHeaderScrollPresentation(
            anchorTopPx = 0f,
            dockedTopPx = 0f,
            expandedHeightPx = 280f,
            dockedHeightPx = 72f
        )

        assertEquals(1f, restored.collapseProgress)
        assertEquals(0f, newEntry.collapseProgress)
    }

    @Test fun destinationBannerWinsResolverBannerAndNullRemainsValidFallback() {
        val destination = ink.tenqui.flowtone.core.online.ExtensionImage("provider", "https://example.com/destination.jpg")
        val resolver = ink.tenqui.flowtone.core.online.ExtensionImage("provider", "https://example.com/resolver.jpg")
        assertEquals(destination, mergeArtistMetadata("Artist", ArtistMetadata(banner = destination), ArtistMetadata(banner = resolver))?.banner)
        assertEquals(null, mergeArtistMetadata("Artist", ArtistMetadata(biography = "Bio"), null)?.banner)
    }

    @Test fun contentDrivenFocusHeightStopsAtRequiredBiographyHeight() {
        val target = artistProfileFocusHeightTarget(
            collapsedHeight = 280.dp,
            requiredFocusedHeight = 380.dp,
            maxAllowedFocusHeight = 640.dp
        )

        assertEquals(380.dp, target.height)
        assertFalse(target.biographyScrollRequired)
    }

    @Test fun oversizedBiographyUsesMaximumFocusHeightAndInternalScroll() {
        val target = artistProfileFocusHeightTarget(
            collapsedHeight = 280.dp,
            requiredFocusedHeight = 820.dp,
            maxAllowedFocusHeight = 640.dp
        )

        assertEquals(640.dp, target.height)
        assertTrue(target.biographyScrollRequired)
    }

    @Test fun artworkColorCacheReusesSameKeyAndSeparatesTheme() {
        ArtworkPaletteMemoryCache.clearForTest()
        var resolutions = 0
        fun resolve(color: Color) = color.also { resolutions += 1 }
        val lightFirst = ArtworkPaletteMemoryCache.resolveColorForTest("artwork", false) {
            resolve(Color.Red)
        }
        val lightSecond = ArtworkPaletteMemoryCache.resolveColorForTest("artwork", false) {
            resolve(Color.Blue)
        }
        val dark = ArtworkPaletteMemoryCache.resolveColorForTest("artwork", true) {
            resolve(Color.Green)
        }
        assertEquals(Color.Red, lightFirst)
        assertEquals(Color.Red, lightSecond)
        assertEquals(Color.Green, dark)
        assertEquals(2, resolutions)
    }

    @Test fun bannerReadyAtPageEnterUsesPageAlphaWithoutIndependentReveal() {
        val state = initialArtistBannerPresentationState(
            bannerKnown = true,
            drawableReadyImmediately = true
        )

        assertEquals(ArtistBannerPresentationState.BannerReadyImmediately, state)
        assertEquals(1f, artistBannerTargetAlpha(state))
        assertFalse(artistBannerUsesLateReveal(state))
        assertEquals(state, artistBannerSuccessState(state, pageEnterComplete = false))
    }

    @Test fun bannerLoadingAtPageEnterKeepsArtistColorVisible() {
        val state = initialArtistBannerPresentationState(
            bannerKnown = true,
            drawableReadyImmediately = false
        )

        assertEquals(ArtistBannerPresentationState.BannerLoading, state)
        assertEquals(0f, artistBannerInternalAlpha(state, lateRevealAlpha = 1f))
        assertEquals(
            ArtistProfileBaseColorSource.ArtistArtwork,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = true,
                bannerState = state
            )
        )
    }

    @Test fun bannerSuccessAfterPageEnterAllowsOneLateReveal() {
        val loading = initialArtistBannerPresentationState(
            bannerKnown = true,
            drawableReadyImmediately = false
        )
        val successDuringEnter = artistBannerSuccessState(
            current = loading,
            pageEnterComplete = false
        )
        val successAfterEnter = artistBannerSuccessState(
            current = successDuringEnter,
            pageEnterComplete = true
        )

        assertEquals(ArtistBannerPresentationState.BannerLoading, successDuringEnter)
        assertEquals(ArtistBannerPresentationState.BannerLoadedLate, successAfterEnter)
        assertTrue(artistBannerUsesLateReveal(successAfterEnter))
        assertEquals(0.35f, artistBannerInternalAlpha(successAfterEnter, 0.35f))
    }

    @Test fun pageEnterReverseKeepsReadyBannerOnCurrentPagePresentation() {
        val state = ArtistBannerPresentationState.BannerReadyImmediately

    }

    @Test fun bannerColorCacheHitIsInitialCardBaseColor() {
        assertEquals(
            ArtistProfileBaseColorSource.Banner,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = true,
                bannerState = ArtistBannerPresentationState.BannerReadyImmediately
            )
        )
    }

    @Test fun bannerColorMissKeepsArtistColorUntilResolved() {
        assertEquals(
            ArtistProfileBaseColorSource.ArtistArtwork,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = false,
                bannerState = ArtistBannerPresentationState.BannerReadyImmediately
            )
        )
    }

    @Test fun resolvedBannerColorBecomesFinalCardBase() {
        assertEquals(
            ArtistProfileBaseColorSource.Banner,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = true,
                bannerState = ArtistBannerPresentationState.BannerLoadedLate
            )
        )
    }

    @Test fun bannerColorUsesSharedArtworkColorCache() {
        ArtworkPaletteMemoryCache.clearForTest()
        var resolutions = 0
        val banner = ExtensionImage("provider", "https://example.com/banner.jpg")
        val bannerKey = artworkPaletteCacheIdentity(banner)

        val first = ArtworkPaletteMemoryCache.resolveColorForTest(bannerKey, false) {
            resolutions += 1
            Color.Magenta
        }
        val second = ArtworkPaletteMemoryCache.resolveColorForTest(bannerKey, false) {
            resolutions += 1
            Color.Cyan
        }

        assertEquals(Color.Magenta, first)
        assertEquals(Color.Magenta, second)
        assertEquals(1, resolutions)
    }

    @Test fun bannerImageAndPaletteKeysIncludeProviderAndUrlIdentity() {
        val first = ExtensionImage("provider-a", "https://example.com/banner.jpg")
        val second = ExtensionImage("provider-b", "https://example.com/banner.jpg")

        assertEquals(
            "artist-profile-banner:provider-a:https://example.com/banner.jpg",
            artistBannerDisplayMemoryCacheKey(first)
        )
        assertEquals(
            "extension-image:provider-a:https://example.com/banner.jpg",
            artworkPaletteCacheIdentity(first)
        )
        assertFalse(artistBannerDisplayMemoryCacheKey(first) == artistBannerDisplayMemoryCacheKey(second))
    }

    @Test fun unavailableAndFailedBannerLeaveArtistColorVisible() {
        val unavailable = initialArtistBannerPresentationState(
            bannerKnown = false,
            drawableReadyImmediately = false
        )

        assertEquals(ArtistBannerPresentationState.BannerUnavailable, unavailable)
        assertEquals(0f, artistBannerTargetAlpha(unavailable))
        assertEquals(0f, artistBannerTargetAlpha(ArtistBannerPresentationState.BannerFailed))
    }

    @Test fun focusGrowthDoesNotIncreaseBannerHeroHeight() {
        val collapsed = artistProfilePresentationHeights(
            collapsedHeight = 300.dp,
            expandedHeight = 420.dp,
            focusProgress = 0f,
            bannerHeroHeight = 280.dp
        )
        val focused = artistProfilePresentationHeights(
            collapsedHeight = 300.dp,
            expandedHeight = 420.dp,
            focusProgress = 1f,
            bannerHeroHeight = 280.dp
        )
        val collapsedOverlay = artistBannerHeroOverlayGeometry(collapsed.bannerHeroHeight)
        val focusedOverlay = artistBannerHeroOverlayGeometry(focused.bannerHeroHeight)

        assertEquals(300.dp, collapsed.cardHeight)
        assertEquals(420.dp, focused.cardHeight)
        assertEquals(280.dp, collapsed.bannerHeroHeight)
        assertEquals(collapsed.bannerHeroHeight, focused.bannerHeroHeight)
        assertEquals(collapsedOverlay, focusedOverlay)
    }

    @Test fun bannerBottomBlendFinishesAtTheExactCardBaseColor() {
        val bannerColor = Color(0xff315b68)

        assertEquals(bannerColor, artistBannerBottomBlendFinalColor(bannerColor))
        assertTrue(ArtistBannerBottomFadeStartFraction in 0.45f..0.55f)
    }

    @Test fun darkScrimAndBottomBlendAreConfinedToFixedHeroGeometry() {
        val geometry = artistBannerHeroOverlayGeometry(280.dp)

        assertEquals(280.dp, geometry.heroHeight)
        assertEquals(geometry.heroHeight, geometry.darkScrimHeight)
        assertEquals(geometry.heroHeight, geometry.bottomBlendHeight)
        assertTrue(ArtistBannerDarkScrimAlpha > 0f)
        assertTrue(ArtistBannerDarkScrimAlpha < 0.5f)
    }

    @Test fun loadingAlwaysProvidesSevenIndependentSkeletonRows() {
        val presentation = artistPrimaryContentPresentation(
            hasLocalContent = false,
            providerSongsLoaded = false,
            hasSongs = false
        )
        val keys = artistLoadingSkeletonKeys()

        assertEquals(ArtistPrimaryContentPresentation.Loading, presentation)
        assertEquals(7, ArtistLoadingSkeletonCount)
        assertEquals(ArtistLoadingSkeletonCount, keys.size)
        assertEquals("artist-loading-skeleton-0", keys.first())
    }

    @Test fun readySongsNeverReuseSkeletonLayoutOrElementIdentity() {
        val loadingKeys = artistLoadingSkeletonKeys()
        val readyKeys = listOf("real-first", "real-second")

        assertTrue(loadingKeys.toSet().intersect(readyKeys.toSet()).isEmpty())
        assertEquals("real-first", readyKeys.first())
        assertEquals("real-second", readyKeys[1])
        assertNotEquals(
            artistLoadingContentIdentity("artist"),
            artistRealContentIdentity("artist")
        )
    }

    @Test fun headerPrimaryTimingIsIndependentFromSkeletonAndSongKeys() {
        val beforeReplacement = artistHeaderTimingProgress(0.42f)
        val afterReplacement = artistHeaderTimingProgress(0.42f)

        assertEquals(beforeReplacement, afterReplacement)
        assertEquals(
            PageMotion.elementProgress(
                pageProgress = 0.42f,
                order = ArtistHeaderTimingOrder,
                orderCount = ArtistTransitionOrderCount
            ),
            beforeReplacement
        )
    }

    @Test fun providerReadyEmptyEndsLoadingWithoutCreatingSongSlot() {
        val presentation = artistPrimaryContentPresentation(
            hasLocalContent = false,
            providerSongsLoaded = true,
            hasSongs = false
        )

        assertEquals(ArtistPrimaryContentPresentation.Empty, presentation)
        assertTrue(presentation != ArtistPrimaryContentPresentation.Loading)
        assertFalse(
            artistPageContentVisibility(
                hasLocalContent = false,
                hasSongs = false,
                songsLoading = false,
                hasAlbums = false
            ).showSongs
        )
    }

    @Test fun sevenSkeletonRowsUseStableKeysAndCanonicalStaggerProgress() {
        val keys = artistLoadingSkeletonKeys()
        val firstProgress = PageMotion.elementProgress(0.4f, 0, keys.size)
        val secondProgress = PageMotion.elementProgress(0.4f, 1, keys.size)

        assertEquals(7, keys.distinct().size)
        assertEquals("artist-loading-skeleton-0", keys.first())
        assertTrue(firstProgress > secondProgress)
        assertEquals(FlowtoneMotion.DurationMillis, PageMotion.DurationMillis)
        assertEquals(240, FlowtoneMotion.ShortDurationMillis)
    }

    @Test fun bannerPresenceSelectsHeaderVariant() {
        assertEquals(ArtistHeaderVariant.Banner, artistHeaderVariant(hasBanner = true))
        assertEquals(ArtistHeaderVariant.Cloud, artistHeaderVariant(hasBanner = false))
    }

    @Test fun expandedHeaderContentUsesTheSameScrollDisplacementAsItsVisibleHeight() {
        assertEquals(0f, artistExpandedContentOffsetPx(320f, 320f))
        assertEquals(-120f, artistExpandedContentOffsetPx(320f, 200f))
        assertEquals(-240f, artistExpandedContentOffsetPx(320f, 80f))
    }

    @Test fun noBannerCloudBelongsToPageWhileExpandedAndFocusSurfacesStayTransparent() {
        assertEquals(
            ArtistCloudBackgroundOwner.None,
            artistCloudBackgroundOwner(ArtistHeaderVariant.Banner)
        )
        assertEquals(
            ArtistCloudBackgroundOwner.Page,
            artistCloudBackgroundOwner(ArtistHeaderVariant.Cloud)
        )
        assertEquals(HomeBackgroundCloudPlacement, ArtistPageTopCloudPlacement)
        assertEquals(1f, artistFocusCloudEffects().alpha)
        assertEquals(0f, artistFocusCloudEffects().blurRadiusDp)
        assertTrue(artistFocusContentAlpha(1f) < artistFocusContentAlpha(0f))
        assertTrue(artistHeaderUsesRoundedBiographyEdge(ArtistHeaderVariant.Banner))
        assertTrue(artistHeaderUsesRoundedBiographyEdge(ArtistHeaderVariant.Cloud))
        assertEquals(
            ArtistExpandedHeaderSurface.TransparentOverCloud,
            artistExpandedHeaderSurface(ArtistHeaderVariant.Cloud)
        )
        assertEquals(0f, artistHeaderSolidSurfaceAlpha(ArtistHeaderVariant.Cloud, 0f))
        assertEquals(0.5f, artistHeaderSolidSurfaceAlpha(ArtistHeaderVariant.Cloud, 0.5f))
        assertEquals(1f, artistHeaderSolidSurfaceAlpha(ArtistHeaderVariant.Cloud, 1f))
        assertEquals(1f, artistHeaderSolidSurfaceAlpha(ArtistHeaderVariant.Banner, 0f))
    }

    @Test fun stackOwnedHeaderCleanupCannotClearANewerArtistEntry() {
        val store = ArtistHeaderStateStore()
        val first = store.ownerFor("secondary-entry:1:Artist", "Artist", null, null)
        val second = store.ownerFor("secondary-entry:2:Artist", "Artist", null, null)

        store.retainEntries(setOf(second.entryKey))

        assertEquals(null, store.owner(first.entryKey))
        assertTrue(store.owner(second.entryKey) === second)
        assertEquals(null, second.renderModel)
    }

    @Test fun artistAlbumBackKeepsTheSameEntryHeaderOwner() {
        val store = ArtistHeaderStateStore()
        val owner = store.ownerFor("secondary-entry:7:Artist", "Artist", null, null)

        store.retainEntries(setOf("secondary-entry:7:Artist", "secondary-entry:8:Album"))

        assertTrue(store.owner(owner.entryKey) === owner)
    }

    @Test fun longDockedTitleDropsAvatarBeforeEllipsizing() {
        val fits = artistDockedTitlePresentation(220f, 260f)
        val overflows = artistDockedTitlePresentation(320f, 260f)

        assertTrue(fits.showAvatarAndBreadcrumb)
        assertTrue(fits.showTitle)
        assertFalse(overflows.showAvatarAndBreadcrumb)
        assertTrue(overflows.showTitle)
        assertTrue(overflows.currentTitleUsesEllipsis)
    }

    @Test fun normalAlbumKeepsArtistAnchorStableAndMovesSuffixFromTheRight() {
        val start = artistAlbumSuffixTransition(0f)
        val middle = artistAlbumSuffixTransition(0.5f)
        val end = artistAlbumSuffixTransition(1f)

        assertEquals(0f, start.alpha)
        assertEquals(1f, start.translationXFraction)
        assertEquals(1f, start.blurFraction)
        assertEquals(0.5f, middle.alpha)
        assertEquals(0.5f, middle.blurFraction)
        assertEquals(1f, end.alpha)
        assertEquals(0f, end.translationXFraction)
        assertEquals(0f, end.blurFraction)
    }

    @Test fun albumSuffixIsHiddenOnThePreTransitionCurrentFrame() {
        val progress = artistAlbumBreadcrumbProgress(
            pagePhase = PageTransitionPhase.Current,
            pageProgress = 1f,
            albumBridgeActive = true
        )

        assertEquals(0f, progress)
        val suffix = artistAlbumSuffixTransition(progress)
        assertEquals(0f, suffix.alpha)
        assertEquals(1f, suffix.translationXFraction)
    }

    @Test fun normalAlbumSuffixExitsRightAcrossTheFullReverseProgress() {
        val start = artistAlbumSuffixTransition(1f)
        val end = artistAlbumSuffixTransition(0f)

        assertEquals(1f, start.alpha)
        assertEquals(0f, start.translationXFraction, 0.0001f)
        assertEquals(0f, end.alpha)
        assertEquals(1f, end.translationXFraction)
    }

    @Test fun normalAlbumBreadcrumbStaggersSeparatorBeforeTitleAndReversesExactly() {
        val separatorForward = artistAlbumBreadcrumbElementProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.35f,
            order = ArtistAlbumSeparatorOrder,
            albumBridgeActive = true
        )
        val titleForward = artistAlbumBreadcrumbElementProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.35f,
            order = ArtistAlbumTitleOrder,
            albumBridgeActive = true
        )
        val separatorReverse = artistAlbumBreadcrumbElementProgress(
            PageTransitionPhase.Incoming,
            pageProgress = 0.65f,
            order = ArtistAlbumSeparatorOrder,
            albumBridgeActive = true
        )
        val titleReverse = artistAlbumBreadcrumbElementProgress(
            PageTransitionPhase.Incoming,
            pageProgress = 0.65f,
            order = ArtistAlbumTitleOrder,
            albumBridgeActive = true
        )

        assertTrue(separatorForward > titleForward)
        assertEquals(separatorForward, separatorReverse, 0.0001f)
        assertEquals(titleForward, titleReverse, 0.0001f)
    }

    @Test fun normalAlbumReverseDropsTitleBeforeSeparator() {
        val separator = artistAlbumBreadcrumbElementProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.8f,
            order = ArtistAlbumSeparatorOrder,
            albumBridgeActive = true
        )
        val title = artistAlbumBreadcrumbElementProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.8f,
            order = ArtistAlbumTitleOrder,
            albumBridgeActive = true
        )

        assertTrue(title < separator)
    }

    @Test fun artistScrollOwnerSurvivesChildAndNewEntryStartsAtTop() {
        val store = ArtistScrollStateStore()
        val firstEntry = store.ownerFor("secondary-entry:1:Artist")
        firstEntry.update(8, 37)

        store.retainEntries(setOf(firstEntry.entryKey))
        val afterAlbumBack = store.ownerFor(firstEntry.entryKey)
        assertTrue(firstEntry === afterAlbumBack)
        assertEquals(ArtistScrollPosition(8, 37), afterAlbumBack.position)

        store.retainEntries(emptySet())
        val secondEntry = store.ownerFor("secondary-entry:2:Artist")
        assertEquals(ArtistScrollPosition(), secondEntry.position)
    }

    @Test fun requestedHeaderExistsBeforeItsArtistPageComposition() {
        val first = "secondary-entry:1:Artist"
        val second = "secondary-entry:2:Artist"
        val store = ArtistHeaderStateStore()
        store.ownerFor(first, "Artist", null, null)
        val secondOwner = store.ownerFor(second, "Artist", null, null)

        assertTrue(store.owner(second) === secondOwner)
        assertEquals(null, secondOwner.renderModel)
        assertEquals(
            0f,
            secondOwner.diagnosticPresentationAlpha(
                PageTransitionPresentation(
                    phase = PageTransitionPhase.Incoming,
                    progress = 0f,
                    transitionId = 1
                )
            )
        )
        secondOwner.updateMeasuredSize(widthPx = 1080, heightPx = 320)
        assertTrue(secondOwner.measuredWidthPx > 0)
        assertTrue(secondOwner.measuredHeightPx > 0)
        store.retainEntries(setOf(second))
        assertTrue(store.owner(second) === secondOwner)
    }

    @Test fun longAlbumFallsBackToBackAndAlbumOnlyTitle() {
        assertEquals(
            ArtistDockedAlbumTitleLayout.Breadcrumb,
            artistDockedAlbumTitleLayout(240f, 280f)
        )
        assertEquals(
            ArtistDockedAlbumTitleLayout.AlbumOnly,
            artistDockedAlbumTitleLayout(420f, 280f)
        )
    }

    @Test fun longAlbumReplacementMovesOldUpAndNewInFromBelowSymmetrically() {
        val artist = artistAlbumReplacementTransition(0f)
        val album = artistAlbumReplacementTransition(1f)

        assertEquals(1f, artist.artistAlpha)
        assertEquals(0f, artist.artistTranslationYFraction, 0.0001f)
        assertEquals(0f, artist.albumAlpha)
        assertEquals(1f, artist.albumTranslationYFraction)
        assertEquals(0f, album.artistAlpha)
        assertEquals(-1f, album.artistTranslationYFraction)
        assertEquals(1f, album.albumAlpha)
        assertEquals(0f, album.albumTranslationYFraction)
    }

    @Test fun collapsedSoftMaskLeavesTheHeaderCompletelyAtProgressOne() {
        val start = artistCollapsedSoftMaskBounds(
            visibleHeightPx = 120f,
            maskHeightPx = 32f,
            progress = 0f
        )
        val end = artistCollapsedSoftMaskBounds(
            visibleHeightPx = 120f,
            maskHeightPx = 32f,
            progress = 1f
        )

        assertEquals(88f, start.topPx)
        assertEquals(120f, start.bottomPx)
        assertEquals(-32f, end.topPx)
        assertEquals(0f, end.bottomPx)
    }

    @Test fun collapsedSoftMaskOnlyStartsAtTheFullyDockedThreshold() {
        assertEquals(0f, artistDockedContentTarget(0f))
        assertEquals(0f, artistDockedContentTarget(0.999f))
        assertEquals(1f, artistDockedContentTarget(1f))
    }

    @Test fun oneAndExactlyTwoLineBiographyHaveNoRevealOrFocus() {
        val oneLine = ArtistBiographyMeasurement(20, 40)
        val exactlyTwoLines = ArtistBiographyMeasurement(40, 40)
        val onePixelRounding = ArtistBiographyMeasurement(41, 40)

        listOf(oneLine, exactlyTwoLines, onePixelRounding).forEach { measurement ->
            assertFalse(canFocusArtistProfile("Biography", measurement))
            assertFalse(artistBiographyEdgeEffectEnabled("Biography", measurement))
        }
    }

    @Test fun biographyBeyondTwoLinesEnablesRevealAfterRoundingTolerance() {
        val measurement = ArtistBiographyMeasurement(42, 40)

        assertTrue(canFocusArtistProfile("Biography", measurement))
        assertTrue(artistBiographyEdgeEffectEnabled("Biography", measurement))
    }

    @Test fun resolvedArtistHeaderColorDoesNotRegressToMaterialDuringCollapseOrAlbum() {
        val bannerColor = Color(0xff245c73)
        val materialColor = Color(0xffeeeeee)

        assertEquals(
            bannerColor,
            artistEffectiveHeaderColor(
                retainedResolvedColor = bannerColor,
                candidateColor = materialColor,
                candidateSource = ArtistProfileBaseColorSource.Material
            )
        )
        assertEquals(
            bannerColor,
            artistEffectiveHeaderColor(
                retainedResolvedColor = null,
                candidateColor = bannerColor,
                candidateSource = ArtistProfileBaseColorSource.Banner
            )
        )
    }

    @Test fun sharedHeaderCollapseStartsAtTheRealScrollPresentationAndReversesExactly() {
        val scroll = 0.22f
        val start = artistSharedHeaderCollapseProgress(scroll, 0f)
        val middle = artistSharedHeaderCollapseProgress(scroll, 0.5f)
        val docked = artistSharedHeaderCollapseProgress(scroll, 1f)

        assertEquals(scroll, start, 0.0001f)
        assertTrue(middle > scroll)
        assertEquals(1f, docked, 0.0001f)
        assertEquals(start, artistSharedHeaderCollapseProgress(scroll, 0f), 0.0001f)
    }

    @Test fun noBannerSurfaceColorMorphsWithoutAMaterialFallback() {
        val artistColor = Color(0xff285b67)
        val expanded = artistHeaderSurfaceColor(ArtistHeaderVariant.Cloud, artistColor, 0f)
        val middle = artistHeaderSurfaceColor(ArtistHeaderVariant.Cloud, artistColor, 0.5f)
        val docked = artistHeaderSurfaceColor(ArtistHeaderVariant.Cloud, artistColor, 1f)

        assertEquals(0f, expanded.alpha, 0.0001f)
        assertEquals(0.5f, middle.alpha, 0.01f)
        assertEquals(1f, docked.alpha, 0.0001f)
        assertEquals(artistColor.copy(alpha = 1f), docked)
        assertEquals(0.08f, ArtistCloudReadabilityOverlayAlpha, 0.0001f)
    }

    @Test fun inactiveFocusDoesNotCreateTheArtistOnlyParentRenderLayer() {
        assertFalse(artistFocusLayerRequired(0f))
        assertFalse(artistFocusLayerRequired(0.001f))
        assertTrue(artistFocusLayerRequired(0.5f))
    }

    @Test fun albumBreadcrumbUsesTheSameCanonicalMappingInReverse() {
        val forward = artistAlbumBreadcrumbProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.38f,
            albumBridgeActive = true
        )
        val reversed = artistAlbumBreadcrumbProgress(
            PageTransitionPhase.Incoming,
            pageProgress = 0.62f,
            albumBridgeActive = true
        )

        assertEquals(forward, reversed, 0.0001f)
    }

    @Test fun albumHeaderUsesOrderZeroItemWindowAndEasing() {
        val start = PageMotion.staggerStartFraction(0, ArtistTransitionOrderCount)
        val itemProgress = PageMotion.elementProgress(
            pageProgress = 0.42f,
            order = 0,
            orderCount = ArtistTransitionOrderCount
        )
        val headerProgress = artistAlbumHeaderLocalProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.42f
        )

        assertEquals(0f, start)
        assertEquals(1f, 1f - start)
        assertEquals(itemProgress, headerProgress)
        assertTrue(headerProgress > 0.42f)
    }

    @Test fun shortHeaderAndContentSwitchUsesTheEstablishedFastMotionToken() {
        assertEquals(FlowtoneMotion.DurationMillis, PageMotion.DurationMillis)
        assertEquals(240, FlowtoneMotion.ShortDurationMillis)
        assertTrue(FlowtoneMotion.ShortDurationMillis < PageMotion.DurationMillis)
        assertEquals(
            FlowtoneMotion.Easing.transform(0.42f),
            PageMotion.Easing.transform(0.42f)
        )
    }

    @Test fun albumHeaderReverseUsesTheSameLocalMapping() {
        val forward = artistAlbumHeaderLocalProgress(
            PageTransitionPhase.Outgoing,
            pageProgress = 0.38f
        )
        val back = artistAlbumHeaderLocalProgress(
            PageTransitionPhase.Incoming,
            pageProgress = 0.62f
        )

        assertEquals(forward, back, 0.0001f)
    }

}
