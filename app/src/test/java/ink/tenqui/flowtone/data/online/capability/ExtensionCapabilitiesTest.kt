package ink.tenqui.flowtone.data.online.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCapabilitiesTest {
    @Test
    fun legacyArtistAvatarMapsToCanonicalLookup() {
        val result = LegacyCapabilityCanonicalizer.canonicalize(listOf("artist_avatar"))

        assertEquals(setOf(AtomicCapabilityId.ArtistAvatarLookup), result.values)
    }

    @Test
    fun legacyMusicProviderMapsOnlyConservativeCoreCapabilities() {
        val result = LegacyCapabilityCanonicalizer.canonicalize(listOf("music_provider"))

        assertEquals(
            setOf(
                AtomicCapabilityId.SearchSongPage,
                AtomicCapabilityId.PlaybackResourceResolve
            ),
            result.values
        )
    }

    @Test
    fun legacyMusicProviderAndSongMapsCatalogSongs() {
        val result = LegacyCapabilityCanonicalizer.canonicalize(listOf("music_provider", "song"))

        assertTrue(AtomicCapabilityId.CatalogSongsList in result)
    }

    @Test
    fun legacyMusicProviderAndAlbumMapsCatalogAlbums() {
        val result = LegacyCapabilityCanonicalizer.canonicalize(listOf("music_provider", "album"))

        assertTrue(AtomicCapabilityId.CatalogAlbumsList in result)
    }

    @Test
    fun musicSourcesEnablePersistentSongResolutionOnlyForMusicProvider() {
        val provider = LegacyCapabilityCanonicalizer.canonicalize(
            legacyCapabilities = listOf("music_provider"),
            musicSources = listOf("music.example")
        )
        val unrelated = LegacyCapabilityCanonicalizer.canonicalize(
            legacyCapabilities = listOf("artist_avatar"),
            musicSources = listOf("music.example")
        )

        assertTrue(AtomicCapabilityId.SongPersistentResolve in provider)
        assertFalse(AtomicCapabilityId.SongPersistentResolve in unrelated)
    }

    @Test
    fun unknownLegacyCapabilityDoesNotGrantCanonicalCapabilities() {
        val result = LegacyCapabilityCanonicalizer.canonicalize(listOf("future_unknown"))

        assertTrue(result.values.isEmpty())
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
