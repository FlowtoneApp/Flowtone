package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.online.ArtistMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.ui.components.ArtworkPaletteMemoryCache

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

    @Test fun biographyControlsProfileFocusEntry() {
        assertTrue(canFocusArtistProfile("Biography", biographyNeedsExpansion = true))
        assertFalse(canFocusArtistProfile("Biography", biographyNeedsExpansion = false))
        assertFalse(canFocusArtistProfile(null, biographyNeedsExpansion = true))
        assertFalse(canFocusArtistProfile("  ", biographyNeedsExpansion = true))
    }

    @Test fun expandedTextNoLongerOverwritesCollapsedBiographyEligibility() {
        val collapsedBiographyNeedsExpansion = true
        val expandedTextHasVisualOverflow = false

        assertFalse(expandedTextHasVisualOverflow)
        assertTrue(
            canFocusArtistProfile(
                biography = "A biography that overflowed its collapsed one-line measurement.",
                biographyNeedsExpansion = collapsedBiographyNeedsExpansion
            )
        )
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

    @Test fun profileElevationTracksFocusProgress() {
        assertEquals(0.dp, artistProfileElevation(0f))
        assertEquals(6.dp, artistProfileElevation(1f))
    }

    @Test fun collapsedBiographyUsesOneLineAndEmptyBiographyCannotFocus() {
        assertEquals(1, artistBiographyMaxLines(false))
        assertEquals(Int.MAX_VALUE, artistBiographyMaxLines(true))
        assertFalse(canFocusArtistProfile(null, biographyNeedsExpansion = false))
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

    @Test fun cachedBannerStartsFullyVisibleWithoutArtistColorOnlyRevealState() {
        val state = initialArtistBannerPresentationState(
            bannerKnown = true,
            availableFromCache = true
        )

        assertEquals(ArtistBannerPresentationState.BannerAvailableFromCache, state)
        assertEquals(1f, artistBannerTargetAlpha(state))
        assertEquals(state, artistBannerSuccessState(state))
    }

    @Test fun bannerColorBecomesBaseOnlyAfterBannerIsAvailable() {
        assertEquals(
            ArtistProfileBaseColorSource.ArtistArtwork,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = true,
                bannerState = ArtistBannerPresentationState.BannerLoadingWithoutCachedImage
            )
        )
        assertEquals(
            ArtistProfileBaseColorSource.Banner,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = true,
                bannerState = ArtistBannerPresentationState.BannerLoadedAfterRequest
            )
        )
        assertEquals(
            ArtistProfileBaseColorSource.ArtistArtwork,
            artistProfileBaseColorSource(
                artistArtworkColorAvailable = true,
                bannerColorAvailable = false,
                bannerState = ArtistBannerPresentationState.BannerUnavailable
            )
        )
    }

    @Test fun bannerColorUsesSharedArtworkColorCache() {
        ArtworkPaletteMemoryCache.clearForTest()
        var resolutions = 0
        val bannerKey = "ExtensionImage(extensionId=provider, url=https://example.com/banner.jpg)"

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

    @Test fun uncachedBannerKeepsArtistColorUntilRequestSuccess() {
        val loading = initialArtistBannerPresentationState(
            bannerKnown = true,
            availableFromCache = false
        )
        val loaded = artistBannerSuccessState(loading)

        assertEquals(ArtistBannerPresentationState.BannerLoadingWithoutCachedImage, loading)
        assertEquals(0f, artistBannerTargetAlpha(loading))
        assertEquals(ArtistBannerPresentationState.BannerLoadedAfterRequest, loaded)
        assertEquals(1f, artistBannerTargetAlpha(loaded))
    }

    @Test fun unavailableAndFailedBannerLeaveArtistColorVisible() {
        val unavailable = initialArtistBannerPresentationState(
            bannerKnown = false,
            availableFromCache = false
        )

        assertEquals(ArtistBannerPresentationState.BannerUnavailable, unavailable)
        assertEquals(0f, artistBannerTargetAlpha(unavailable))
        assertEquals(0f, artistBannerTargetAlpha(ArtistBannerPresentationState.BannerFailed))
    }

    @Test fun focusGrowthDoesNotIncreaseBannerHeroHeight() {
        val collapsed = artistProfilePresentationHeights(280.dp, 640.dp, focusProgress = 0f)
        val focused = artistProfilePresentationHeights(280.dp, 640.dp, focusProgress = 1f)

        assertEquals(280.dp, collapsed.cardHeight)
        assertEquals(640.dp, focused.cardHeight)
        assertEquals(280.dp, collapsed.bannerHeroHeight)
        assertEquals(collapsed.bannerHeroHeight, focused.bannerHeroHeight)
    }

    @Test fun bannerBottomFadeOccupiesAStablePartOfHero() {
        assertTrue(ArtistBannerBottomFadeStartFraction > 0f)
        assertTrue(ArtistBannerBottomFadeStartFraction < 1f)
    }

}
