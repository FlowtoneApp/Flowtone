package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.online.ArtistMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.ui.components.ArtworkPaletteMemoryCache
import ink.tenqui.flowtone.ui.components.artworkPaletteCacheIdentity
import ink.tenqui.flowtone.core.online.ExtensionImage

class ArtistPageContentTest {
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

    @Test fun profileColorModeUsesSingleArtworkColorOrMaterial() {
        assertEquals(ArtistProfileColorMode.Artwork, artistProfileColorMode(true))
        assertEquals(ArtistProfileColorMode.Material, artistProfileColorMode(false))
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
        assertEquals(0.4f, artistBannerEffectiveAlpha(state, 0.4f, 0f))
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

        assertEquals(0.5f, artistBannerEffectiveAlpha(state, 0.5f, 0f))
        assertEquals(0.3f, artistBannerEffectiveAlpha(state, 0.3f, 1f))
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

}
