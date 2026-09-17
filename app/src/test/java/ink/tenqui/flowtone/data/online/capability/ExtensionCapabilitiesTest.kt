package ink.tenqui.flowtone.data.online.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCapabilitiesTest {
    @Test
    fun onlyArtistCapabilitiesDoNotRequireMusicProviderRuntime() {
        val avatar = ExtensionRuntimeCapabilityPolicy.registrationRequirements(
            CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.ArtistAvatarLookup)
        )
        val metadata = ExtensionRuntimeCapabilityPolicy.registrationRequirements(
            CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.ArtistMetadataLookup)
        )

        assertEquals(ExtensionRuntimeRequirements(true, false, false), avatar)
        assertEquals(ExtensionRuntimeRequirements(false, true, false), metadata)
    }

    @Test
    fun everyMusicProviderCapabilityRequiresMusicProviderRuntime() {
        ExtensionRuntimeCapabilityPolicy.musicProviderCapabilities.forEach { capability ->
            assertTrue(
                ExtensionRuntimeCapabilityPolicy.requiresMusicProviderRuntime(
                    CanonicalAtomicCapabilitySet.of(capability)
                )
            )
        }
    }

    @Test
    fun searchCategoriesMapToExactAtomicCapabilities() {
        assertEquals(
            listOf(
                AtomicCapabilityId.SearchSongPage,
                AtomicCapabilityId.SearchPlaylistPage,
                AtomicCapabilityId.SearchAlbumPage,
                AtomicCapabilityId.SearchArtistPage
            ),
            ink.tenqui.flowtone.data.online.ProviderSearchCategory.entries.map(
                ExtensionRuntimeCapabilityPolicy::searchCapability
            )
        )
    }

    @Test
    fun playbackResourceAloneIsSupported() {
        assertSummary(
            SummaryCapabilityId.Playback,
            SummaryCapabilityStatus.Supported,
            AtomicCapabilityId.PlaybackResourceResolve
        )
    }

    @Test
    fun persistentSongResolutionAloneIsPartialPlayback() {
        assertSummary(
            SummaryCapabilityId.Playback,
            SummaryCapabilityStatus.Partial,
            AtomicCapabilityId.SongPersistentResolve
        )
    }

    @Test
    fun artistAvatarAloneIsSupportedArtist() {
        assertSummary(
            SummaryCapabilityId.Artist,
            SummaryCapabilityStatus.Supported,
            AtomicCapabilityId.ArtistAvatarLookup
        )
    }

    @Test
    fun artistSearchAloneIsPartialArtist() {
        assertSummary(
            SummaryCapabilityId.Artist,
            SummaryCapabilityStatus.Partial,
            AtomicCapabilityId.SearchArtistPage
        )
    }

    @Test
    fun playlistRequiresBothCoreCapabilities() {
        assertSummary(
            SummaryCapabilityId.Playlist,
            SummaryCapabilityStatus.Supported,
            AtomicCapabilityId.SearchPlaylistPage,
            AtomicCapabilityId.PlaylistSongsRead
        )
        assertSummary(
            SummaryCapabilityId.Playlist,
            SummaryCapabilityStatus.Partial,
            AtomicCapabilityId.SearchPlaylistPage
        )
    }

    @Test
    fun noRelatedCapabilityIsUnsupported() {
        assertSummary(
            SummaryCapabilityId.Playlist,
            SummaryCapabilityStatus.Unsupported,
            AtomicCapabilityId.PlaybackResourceResolve
        )
    }

    @Test
    fun optionalCapabilityNeverDowngradesSupportedSummary() {
        assertSummary(
            SummaryCapabilityId.SearchAndDiscovery,
            SummaryCapabilityStatus.Supported,
            AtomicCapabilityId.SearchSongPage
        )
    }

    @Test
    fun albumCoreAcceptsSearchOrCatalog() {
        assertSummary(
            SummaryCapabilityId.Album,
            SummaryCapabilityStatus.Supported,
            AtomicCapabilityId.SearchAlbumPage
        )
        assertSummary(
            SummaryCapabilityId.Album,
            SummaryCapabilityStatus.Supported,
            AtomicCapabilityId.CatalogAlbumsList
        )
        assertSummary(
            SummaryCapabilityId.Album,
            SummaryCapabilityStatus.Partial,
            AtomicCapabilityId.CatalogSongsList
        )
    }

    @Test
    fun everyAtomicCapabilityHasOneHostOwnedDefinitionAndUniqueOrder() {
        assertEquals(AtomicCapabilityId.entries.toSet(), AtomicCapabilityDefinitions.all.map { it.id }.toSet())
        assertEquals(
            AtomicCapabilityDefinitions.all.size,
            AtomicCapabilityDefinitions.all.map { it.order }.toSet().size
        )
    }

    private fun assertSummary(
        id: SummaryCapabilityId,
        expected: SummaryCapabilityStatus,
        vararg capabilities: AtomicCapabilityId
    ) {
        val summaries = SummaryCapabilityAggregator.aggregate(
            CanonicalAtomicCapabilitySet.of(*capabilities)
        )
        assertEquals(expected, summaries.single { it.id == id }.status)
    }
}
