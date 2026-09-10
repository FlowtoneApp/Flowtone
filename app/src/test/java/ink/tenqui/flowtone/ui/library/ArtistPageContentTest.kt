package ink.tenqui.flowtone.ui.library

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.pageElementVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPageContentTest {
    @Test
    fun artistPreviewSongMotionKeysFollowVisibleLazyOrder() {
        val songKeys = listOf("songA", "songB", "songC")
        val frozenKeys = artistPreviewSongMotionKeys(
            visibleItemKeys = listOf(
                "artist-hero",
                ArtistSongsHeaderItemKey,
                "songA",
                "songB",
                "songC"
            ),
            songKeys = songKeys
        )

        assertEquals(
            listOf(ArtistSongsHeaderItemKey, "songA", "songB", "songC"),
            frozenKeys
        )
        val header = artistPreviewItemMotionOrder(
            ArtistSongsHeaderItemKey,
            frozenKeys,
            PageTransitionPhase.Incoming
        )
        val songA = artistPreviewItemMotionOrder(
            "songA",
            frozenKeys,
            PageTransitionPhase.Incoming
        )
        val songB = artistPreviewItemMotionOrder(
            "songB",
            frozenKeys,
            PageTransitionPhase.Incoming
        )
        assertTrue(header.visibleOrdinal < songA.visibleOrdinal)
        assertTrue(songA.visibleOrdinal < songB.visibleOrdinal)
    }

    @Test
    fun artistPreviewViewportStartingAtSongDoesNotReserveHeaderOrdinal() {
        val frozenKeys = artistPreviewSongMotionKeys(
            visibleItemKeys = listOf("songA", "songB", "songC"),
            songKeys = listOf("songA", "songB", "songC")
        )

        assertEquals(listOf("songA", "songB", "songC"), frozenKeys)
        assertEquals(
            0,
            artistPreviewItemMotionOrder(
                "songA",
                frozenKeys,
                PageTransitionPhase.Incoming
            ).visibleOrdinal
        )
        assertEquals(
            1,
            artistPreviewItemMotionOrder(
                "songB",
                frozenKeys,
                PageTransitionPhase.Incoming
            ).visibleOrdinal
        )
        assertEquals(
            2,
            artistPreviewItemMotionOrder(
                "songC",
                frozenKeys,
                PageTransitionPhase.Incoming
            ).visibleOrdinal
        )
    }

    @Test
    fun artistPreviewHeaderAndFirstSongDoNotCrossDuringPageMotion() {
        val frozenKeys = listOf(ArtistSongsHeaderItemKey, "songA", "songB", "songC")

        listOf(PageTransitionPhase.Incoming, PageTransitionPhase.Outgoing).forEach { phase ->
            val header = artistPreviewItemMotionOrder(
                ArtistSongsHeaderItemKey,
                frozenKeys,
                phase
            )
            val song = artistPreviewItemMotionOrder("songA", frozenKeys, phase)
            listOf(0f, 0.15f, 0.3f, 0.5f, 0.8f, 1f).forEach { pageProgress ->
                val headerProgress = PageMotion.elementProgress(
                    pageProgress,
                    header.order,
                    header.orderCount
                )
                val songProgress = PageMotion.elementProgress(
                    pageProgress,
                    song.order,
                    song.orderCount
                )
                val headerTranslation = pageElementVisualState(
                    phase,
                    headerProgress,
                    signedOffsetYPx = 24f
                ).translationY
                val songTranslation = pageElementVisualState(
                    phase,
                    songProgress,
                    signedOffsetYPx = 24f
                ).translationY

                assertTrue(11f + songTranslation - headerTranslation >= 0f)
            }
        }
    }

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
    fun artistPreviewsKeepSourceOrderAndApplyOnlyTheirLimits() {
        val songs = (0 until 14).toList()
        val albums = (0 until 9).toList()

        assertEquals(songs.take(10), artistSongPreview(songs))
        assertEquals(albums.take(6), artistAlbumPreview(albums))
        assertEquals((0 until 6).toList(), artistSongPreview((0 until 6).toList()))
        assertEquals((0 until 4).toList(), artistAlbumPreview((0 until 4).toList()))
        assertEquals((0 until 14).toList(), songs)
    }

    @Test
    fun albumPreviewAddsTrailingActionOnlyWhenMoreThanSixAlbumsExist() {
        assertFalse(artistAlbumPreviewHasTrailingAction(6))
        assertTrue(artistAlbumPreviewHasTrailingAction(7))
    }

    @Test
    fun artistSongsSectionTitleUsesProviderDescriptionOrSongFallback() {
        assertEquals("时间排序", artistSongsSectionTitle("  时间排序  "))
        assertEquals("歌曲", artistSongsSectionTitle(null))
        assertEquals("歌曲", artistSongsSectionTitle("   "))
    }

    @Test
    fun infoCardTintKeepsTheBaseSurfaceAsTheDominantLayer() {
        assertEquals(0.07f, ArtistInfoCardTintTopAlpha, 0f)
        assertEquals(0.01f, ArtistInfoCardTintBottomAlpha, 0f)
        assertTrue(ArtistInfoCardTintTopAlpha < 0.1f)
        assertTrue(ArtistInfoCardTintBottomAlpha < ArtistInfoCardTintTopAlpha)
    }

    @Test
    fun artistTintPrefersBannerAndNoBannerUsesTheSameArtistFallbackChain() {
        assertEquals(
            "banner",
            artistTintArtworkData(
                banner = "banner",
                avatar = "avatar",
                localArtwork = "local",
                providerArtwork = "provider"
            )
        )
        assertEquals(
            "avatar",
            artistTintArtworkData(
                banner = null,
                avatar = "avatar",
                localArtwork = "local",
                providerArtwork = "provider"
            )
        )
        assertNull(
            artistTintArtworkData(
                banner = null,
                avatar = null,
                localArtwork = null,
                providerArtwork = null
            )
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
    fun biographyPreviewUsesTwoLines() {
        assertEquals(2, ArtistBiographyPreviewMaxLines)
    }

    @Test
    fun shortBiographyDoesNotShowExpand() {
        assertFalse(
            artistBiographyExpandVisible(
                "Short biography",
                previewHasVisualOverflow = false
            )
        )
    }

    @Test
    fun longBiographyShowsExpand() {
        assertTrue(
            artistBiographyExpandVisible(
                "A biography longer than its two-line preview",
                previewHasVisualOverflow = true
            )
        )
    }

    @Test
    fun missingBiographyDoesNotReserveExpandSpace() {
        assertFalse(artistBiographyExpandVisible(null, previewHasVisualOverflow = true))
        assertFalse(artistBiographyExpandVisible("  ", previewHasVisualOverflow = true))
    }

    @Test
    fun avatarOutlineDoesNotChangeTheMeasuredAvatarSize() {
        val presentation = artistAvatarPresentation(100.dp)

        assertEquals(100.dp, presentation.measuredSize)
        assertEquals(1.5.dp, presentation.outlineWidth)
    }

    @Test
    fun incomingBannerUsesCanonicalPageElementStart() {
        val progress = PageMotion.elementProgress(
            pageProgress = 0f,
            order = ArtistHeroElement.Background.order,
            orderCount = ArtistHeroElement.entries.size
        )
        val presentation = pageElementVisualState(
            PageTransitionPhase.Incoming,
            progress,
            signedOffsetYPx = 24f
        )

        assertEquals(0f, presentation.alpha, 0f)
        assertTrue(presentation.translationY > 0f)
    }

    @Test
    fun outgoingBannerUsesCanonicalDownwardFade() {
        val start = pageElementVisualState(PageTransitionPhase.Outgoing, 0f, 24f)
        val end = pageElementVisualState(PageTransitionPhase.Outgoing, 1f, 24f)

        assertEquals(1f, start.alpha, 0f)
        assertEquals(0f, start.translationY, 0f)
        assertEquals(0f, end.alpha, 0f)
        assertTrue(end.translationY > 0f)
    }

    @Test
    fun bannerReverseAndRecompositionFollowOnlyPageMasterProgress() {
        val forward = pageElementVisualState(PageTransitionPhase.Outgoing, 0.62f, 24f)
        val reversed = pageElementVisualState(PageTransitionPhase.Outgoing, 0.41f, 24f)
        val recomposed = pageElementVisualState(PageTransitionPhase.Outgoing, 0.41f, 24f)

        assertTrue(reversed.alpha > forward.alpha)
        assertEquals(reversed, recomposed)
    }

    @Test
    fun artistCloudIsPresentWithAndWithoutBanner() {
        assertTrue(artistCloudVisible(ArtistHeroBackgroundKind.Banner))
        assertTrue(artistCloudVisible(ArtistHeroBackgroundKind.Cloud))
    }

    @Test
    fun artworkReadinessTargetsStayStableUntilTheResourceBecomesReady() {
        assertEquals(0f, artistArtworkReadinessAlphaTarget(ready = false), 0f)
        assertEquals(1f, artistArtworkReadinessAlphaTarget(ready = true), 0f)
        assertEquals(1f, artistArtworkReadinessAlphaTarget(ready = true), 0f)
    }

    @Test
    fun bannerAndCloudReadinessUseIndependentTargets() {
        assertEquals(0f, artistArtworkReadinessAlphaTarget(ready = false), 0f)
        assertEquals(1f, artistArtworkReadinessAlphaTarget(ready = true), 0f)
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

        first.updatePresentation(
            avatar = null,
            backgroundKind = ArtistHeroBackgroundKind.Banner,
            cloudColor = Color.Red
        )
        first.focusRequested = true
        assertTrue(first.focusRequested)
        assertFalse(second.focusRequested)
        assertEquals(Color.Red, store.owner("artist-1")?.resolvedCloudColor)
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
    fun artistScrollOwnerRetainsOneOverviewPosition() {
        val owner = ArtistScrollStateOwner("artist")
        owner.update(
            firstVisibleItemIndex = 8,
            firstVisibleItemScrollOffset = 12
        )

        assertEquals(ArtistScrollPosition(8, 12), owner.position)
    }

    @Test
    fun artistScrollOwnerRetainsAlbumsPreviewPositionPerEntry() {
        val store = ArtistScrollStateStore()
        val artistA = store.ownerFor("artist-a")
        val artistB = store.ownerFor("artist-b")
        val repeatedArtistA = store.ownerFor("artist-a-second-entry")

        artistA.updateAlbumsPreview(4, 19)

        assertEquals(ArtistScrollPosition(4, 19), artistA.albumsPreviewPosition)
        assertEquals(ArtistScrollPosition(), artistB.albumsPreviewPosition)
        assertEquals(ArtistScrollPosition(), repeatedArtistA.albumsPreviewPosition)
        assertEquals(artistA, store.ownerFor("artist-a"))
    }

    @Test
    fun topBarReadabilityUsesSharedProgressOnlyForTheCurrentOwner() {
        val presentation = artistTopBarVisualPresentation(0.4f)

        assertEquals(0.4f, presentation.foregroundAlpha, 0f)
        assertEquals(-0.6f, presentation.foregroundTranslationYFraction, 0.0001f)
        assertEquals(0.4f, artistTopBarContentOcclusionProgress(0.4f, true), 0f)
        assertEquals(0f, artistTopBarContentOcclusionProgress(1f, false), 0f)
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
}
