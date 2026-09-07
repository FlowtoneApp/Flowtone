package ink.tenqui.flowtone.ui.library

import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPageContentTest {
    @Test
    fun providerCountsWithoutEntitiesDoNotCreateContentSections() {
        val visibility = artistPageContentVisibility(
            hasLocalContent = false,
            hasSongs = false,
            hasAlbums = false,
            hasStatistics = true
        )

        assertTrue(visibility.showStatistics)
        assertFalse(visibility.showSongs)
        assertFalse(visibility.showAlbums)
    }

    @Test
    fun providerSectionsRequireRealEntities() {
        val visibility = artistPageContentVisibility(
            hasLocalContent = false,
            hasSongs = true,
            hasAlbums = true,
            hasStatistics = false
        )

        assertTrue(visibility.showSongs)
        assertTrue(visibility.showAlbums)
    }

    @Test
    fun collaborativeAlbumMembershipUsesStableArtistIdentity() {
        assertTrue(artistMatchesAlbumSongs("A", listOf("A / B")))
        assertTrue(artistMatchesAlbumSongs("B", listOf("A / B")))
        assertTrue(artistMatchesAlbumSongs("artist a", listOf("  Artist A  ")))
        assertFalse(artistMatchesAlbumSongs("C", listOf("A / B")))
        assertFalse(artistMatchesAlbumSongs("A", emptyList()))
    }

    @Test
    fun weakMetadataCountsUseOneHorizontalRow() {
        assertEquals("18 首歌曲", artistStatisticsText(songCount = 18, albumCount = 0))
        assertEquals(
            "18 首歌曲 · 3 张专辑",
            artistStatisticsText(songCount = 18, albumCount = 3)
        )
        assertEquals(
            "0 首歌曲 · 10 张专辑",
            artistMetadataStatisticsText(songCount = 0, albumCount = 10)
        )
        assertEquals(
            "0 张专辑",
            artistMetadataStatisticsText(songCount = null, albumCount = 0)
        )
        assertNull(artistMetadataStatisticsText(songCount = null, albumCount = null))
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
    fun resolverCompletesMissingMetadataWithoutReplacingDestinationValues() {
        val merged = mergeArtistMetadata(
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(
                aliases = listOf("Destination Alias"),
                biography = "Destination biography",
                songCount = 0
            ),
            resolverMetadata = ArtistMetadata(
                aliases = listOf("destination alias", "Resolver Alias", "Artist"),
                biography = "Resolver biography",
                songCount = 10,
                albumCount = 2
            )
        )

        assertEquals(listOf("Destination Alias", "Resolver Alias"), merged?.aliases)
        assertEquals("Destination biography", merged?.biography)
        assertEquals(0, merged?.songCount)
        assertEquals(2, merged?.albumCount)
    }

    @Test
    fun heroBackgroundHeightUsesWidthRatioWithinPhoneBounds() {
        assertEquals(260.dp, artistHeroGeometry(320.dp).backgroundHeight)
        assertEquals(260.dp, artistHeroGeometry(360.dp).backgroundHeight)
        assertEquals(288.dp, artistHeroGeometry(400.dp).backgroundHeight)
        assertEquals(320.dp, artistHeroGeometry(500.dp).backgroundHeight)
    }

    @Test
    fun cardOverlapsBackgroundAndAvatarCrossesTheCardBoundary() {
        val compact = artistHeroGeometry(360.dp)
        val regular = artistHeroGeometry(400.dp)

        assertEquals(100.dp, compact.avatarSize)
        assertEquals(104.dp, regular.avatarSize)
        assertTrue(compact.infoCardTop < compact.backgroundHeight)
        assertTrue(compact.avatarTop < compact.infoCardTop)
        assertTrue(compact.avatarBottom > compact.infoCardTop)
        assertTrue(compact.avatarTop < compact.backgroundHeight)
        assertEquals(compact.avatarTop + compact.avatarSize, compact.avatarBottom)
    }

    @Test
    fun overlapIsDrivenByTheCombinedCardAndAvatarRelationship() {
        val geometry = artistHeroGeometry(360.dp)

        assertEquals(72.dp, geometry.infoCardOverlap)
        assertEquals(188.dp, geometry.infoCardTop)
        assertEquals(58f, geometry.avatarProtrusionAboveCard.value, 0.001f)
        assertEquals(42f, geometry.avatarDepthInsideCard.value, 0.001f)
        assertEquals(54f, geometry.infoCardContentTopPadding.value, 0.001f)
        assertTrue(geometry.infoCardOverlap > geometry.avatarSize * 0.5f)
        assertTrue(geometry.avatarDepthInsideCard > geometry.avatarSize * 0.2f)
    }

    @Test
    fun biographyIsANarrowerCenteredBlockInsideCardContent() {
        val availableWidth = 360.dp
        val geometry = artistHeroGeometry(availableWidth)

        assertEquals(336.dp, geometry.infoCardWidth(availableWidth))
        assertEquals(304.dp, geometry.infoCardContentWidth(availableWidth))
        assertEquals(288.dp, geometry.biographyWidth(availableWidth))
        assertTrue(
            geometry.biographyWidth(availableWidth) <
                geometry.infoCardContentWidth(availableWidth)
        )
    }

    @Test
    fun heroMeasuredHeightUsesTheRealOverlappedCardBottom() {
        val geometry = artistHeroGeometry(360.dp)
        val infoCardHeight = 220.dp

        assertEquals(408.dp, geometry.measuredHeight(infoCardHeight))
        assertEquals(
            geometry.infoCardTop + infoCardHeight,
            geometry.measuredHeight(infoCardHeight)
        )
        assertTrue(
            geometry.measuredHeight(infoCardHeight) <
                geometry.backgroundHeight + infoCardHeight
        )
    }

    @Test
    fun categoryUsesLightweightLabelsWithoutCountsOrIconPresentation() {
        assertEquals(
            listOf(ArtistContentCategory.Songs, ArtistContentCategory.Albums),
            artistContentCategories(showSongs = true, showAlbums = true)
        )
        assertEquals(listOf("歌曲", "专辑"), ArtistContentCategory.entries.map { it.label })
        assertEquals(
            listOf(ArtistContentCategory.Songs),
            artistContentCategories(showSongs = true, showAlbums = false)
        )
    }

    @Test
    fun bannerAndCloudUseTheSameStaticHeroGeometry() {
        val bannerGeometry = artistHeroGeometry(400.dp)
        val cloudGeometry = artistHeroGeometry(400.dp)

        assertEquals(bannerGeometry, cloudGeometry)
    }

    @Test
    fun socialStatsAreNullableAndNeverFabricated() {
        assertNull(artistSocialStatsText(ArtistSocialStats()))
        assertEquals(
            "12.4万 粉丝",
            artistSocialStatsText(ArtistSocialStats(followers = "12.4万"))
        )
        assertEquals(
            "12.4万 粉丝 · 38 关注",
            artistSocialStatsText(
                ArtistSocialStats(followers = "12.4万", following = "38")
            )
        )
    }

    @Test
    fun biographyExpandOnlyAppearsForMeasuredPreviewOverflow() {
        assertFalse(artistBiographyExpandVisible(null, previewHasVisualOverflow = true))
        assertFalse(artistBiographyExpandVisible("  ", previewHasVisualOverflow = true))
        assertFalse(
            artistBiographyExpandVisible(
                "Exactly three visible lines",
                previewHasVisualOverflow = false
            )
        )
        assertTrue(
            artistBiographyExpandVisible(
                "A biography longer than its three-line preview",
                previewHasVisualOverflow = true
            )
        )
    }

    @Test
    fun incomingHeroNeverStartsFullyVisible() {
        ArtistHeroElement.entries.forEach { element ->
            assertEquals(
                0f,
                artistHeroElementAlpha(PageTransitionPhase.Incoming, 0f, element),
                0f
            )
        }
    }

    @Test
    fun outgoingHeroStartsVisibleAndEndsHiddenWithoutRetainedAnimationState() {
        ArtistHeroElement.entries.forEach { element ->
            assertEquals(
                1f,
                artistHeroElementAlpha(PageTransitionPhase.Outgoing, 0f, element),
                0f
            )
            assertEquals(
                0f,
                artistHeroElementAlpha(PageTransitionPhase.Outgoing, 1f, element),
                0f
            )
        }
    }

    @Test
    fun reverseAndRecompositionUseOnlyTheCurrentPagePresentation() {
        val forward = artistHeroElementAlpha(
            PageTransitionPhase.Outgoing,
            0.62f,
            ArtistHeroElement.Avatar
        )
        val reversed = artistHeroElementAlpha(
            PageTransitionPhase.Outgoing,
            0.41f,
            ArtistHeroElement.Avatar
        )
        val recomposed = artistHeroElementAlpha(
            PageTransitionPhase.Outgoing,
            0.41f,
            ArtistHeroElement.Avatar
        )

        assertTrue(reversed > forward)
        assertEquals(reversed, recomposed, 0f)
    }

    @Test
    fun cloudAndBannerUseTheSameHeroElementTimeline() {
        val cloudAlpha = artistHeroElementAlpha(
            PageTransitionPhase.Incoming,
            0f,
            ArtistHeroElement.Background
        )
        val bannerAlpha = artistHeroElementAlpha(
            PageTransitionPhase.Incoming,
            0f,
            ArtistHeroElement.Background
        )

        assertEquals(0f, cloudAlpha, 0f)
        assertEquals(cloudAlpha, bannerAlpha, 0f)
    }

    @Test
    fun heroStateIsIsolatedAndRetainedByNavigationEntry() {
        val store = ArtistHeroStateStore()
        val first = store.ownerFor(
            entryKey = "artist-1",
            initialAvatar = null,
            initialBackgroundKind = ArtistHeroBackgroundKind.Banner
        )
        val second = store.ownerFor(
            entryKey = "artist-2",
            initialAvatar = null,
            initialBackgroundKind = ArtistHeroBackgroundKind.Cloud
        )

        first.focusRequested = true
        assertTrue(first.focusRequested)
        assertFalse(second.focusRequested)
        assertEquals(first, store.owner("artist-1"))

        store.retainEntries(setOf("artist-2"))
        assertNull(store.owner("artist-1"))
        assertEquals(second, store.owner("artist-2"))
    }

    @Test
    fun loadingUsesSevenStableSkeletonRowsWithSeparateRealContentIdentity() {
        assertEquals(7, artistLoadingSkeletonKeys().size)
        assertEquals(7, artistLoadingSkeletonKeys().distinct().size)
        assertFalse(
            artistLoadingContentIdentity("entry") == artistRealContentIdentity("entry")
        )
    }

    @Test
    fun providerReadyEmptyEndsLoadingWithoutCreatingSongSlot() {
        assertEquals(
            ArtistPrimaryContentPresentation.Loading,
            artistPrimaryContentPresentation(
                hasLocalContent = false,
                providerSongsLoaded = false,
                hasSongs = false
            )
        )
        assertEquals(
            ArtistPrimaryContentPresentation.Empty,
            artistPrimaryContentPresentation(
                hasLocalContent = false,
                providerSongsLoaded = true,
                hasSongs = false
            )
        )
    }

    @Test
    fun artistScrollOwnerSurvivesChildAndNewEntryStartsAtTop() {
        val store = ArtistScrollStateStore()
        val first = store.ownerFor("artist-1")
        first.update(firstVisibleItemIndex = 4, firstVisibleItemScrollOffset = 28)

        assertEquals(ArtistScrollPosition(4, 28), store.ownerFor("artist-1").position)
        assertEquals(ArtistScrollPosition(), store.ownerFor("artist-2").position)
    }

    @Test
    fun topBarThresholdsUseMeasuredHeroHeightAndHysteresis() {
        val thresholds = artistTopBarThresholds(
            heroHeightPx = 520,
            topBarHeightPx = 80,
            hysteresisPx = 24
        )

        assertEquals(440, thresholds.showPx)
        assertEquals(416, thresholds.hidePx)
        assertFalse(artistTopBarVisible(false, 0, 439, thresholds))
        assertTrue(artistTopBarVisible(false, 0, 440, thresholds))
        assertTrue(artistTopBarVisible(true, 0, 430, thresholds))
        assertFalse(artistTopBarVisible(true, 0, 416, thresholds))
    }

    @Test
    fun longTopBarTitleDropsAvatarBeforeEllipsizing() {
        assertTrue(
            artistTopBarTitlePresentation(
                naturalContentWidthPx = 220f,
                availableWidthPx = 260f
            ).showAvatarAndBreadcrumb
        )
        assertFalse(
            artistTopBarTitlePresentation(
                naturalContentWidthPx = 320f,
                availableWidthPx = 260f
            ).showAvatarAndBreadcrumb
        )
    }

    @Test
    fun albumSuffixAndReplacementRemainContinuous() {
        val suffix = artistAlbumSuffixTransition(0.35f)
        assertEquals(0.35f, suffix.alpha, 0f)
        assertEquals(0.65f, suffix.translationXFraction, 0.0001f)

        val replacement = artistAlbumReplacementTransition(0.35f)
        assertEquals(0.65f, replacement.artistAlpha, 0.0001f)
        assertEquals(0.35f, replacement.albumAlpha, 0f)
        assertEquals(-0.35f, replacement.artistTranslationYFraction, 0f)
        assertEquals(0.65f, replacement.albumTranslationYFraction, 0.0001f)
    }
}
