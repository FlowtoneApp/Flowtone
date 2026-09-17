package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEntityTest {
    @Test
    fun providerWithoutCatalogCapabilitiesHasNoCollections() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider()

        assertTrue(provider.capabilities.values.isEmpty())
        assertNull(provider.getSongs())
        assertNull(provider.getAlbums())
    }

    @Test
    fun songsOnlyProviderDeclaresCanonicalSongsCapability() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider(
            capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.CatalogSongsList),
            songs = listOf(song("a", "1"))
        )

        assertEquals(setOf(AtomicCapabilityId.CatalogSongsList), provider.capabilities.values)
        assertEquals(listOf("1"), provider.getSongs()?.map(ProviderSong::id))
        assertNull(provider.getAlbums())
    }

    @Test
    fun albumsOnlyProviderDeclaresCanonicalAlbumsCapability() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider(
            capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.CatalogAlbumsList),
            albums = listOf(album("a", "1"))
        )

        assertEquals(setOf(AtomicCapabilityId.CatalogAlbumsList), provider.capabilities.values)
        assertEquals(listOf("1"), provider.getAlbums()?.map(ProviderAlbum::id))
        assertNull(provider.getSongs())
    }

    @Test
    fun providerCanExposeBothCollections() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider(
            capabilities = CanonicalAtomicCapabilitySet.of(
                AtomicCapabilityId.CatalogSongsList,
                AtomicCapabilityId.CatalogAlbumsList
            ),
            songs = listOf(song("a", "song")),
            albums = listOf(album("a", "album"))
        )

        assertEquals("song", provider.getSongs()?.single()?.id)
        assertEquals("album", provider.getAlbums()?.single()?.id)
    }

    @Test
    fun sameRemoteSongIdAcrossProvidersIsDistinct() {
        assertNotEquals(song("a", "1").identity, song("b", "1").identity)
    }

    @Test
    fun sameRemoteAlbumIdAcrossProvidersIsDistinct() {
        assertNotEquals(album("a", "1").identity, album("b", "1").identity)
    }

    @Test
    fun providerArtistProfilePrefersLargeArtworkAndFallsBackToArtwork() {
        val artwork = ExtensionImage("a", "https://example.test/avatar-small.jpg")
        val largeArtwork = ExtensionImage("a", "https://example.test/avatar-large.jpg")

        assertEquals(
            largeArtwork,
            ProviderArtist(
                identity = ProviderEntityIdentity("a", "42"),
                title = "Artist",
                artwork = artwork,
                largeArtwork = largeArtwork
            ).preferredProfileArtwork()
        )
        assertEquals(
            artwork,
            ProviderArtist(
                identity = ProviderEntityIdentity("a", "42"),
                title = "Artist",
                artwork = artwork
            ).preferredProfileArtwork()
        )
    }

    @Test
    fun formalProviderArtistMetadataWinsAndLegacyOnlyFillsMissingFields() {
        val formalBanner = ExtensionImage("a", "https://example.test/formal-banner.jpg")
        val legacyBanner = ExtensionImage("a", "https://example.test/legacy-banner.jpg")
        val legacy = song(
            provider = "a",
            id = "42",
            artists = listOf(ProviderArtistRef("42", "Artist")),
            searchCategory = ProviderSearchCategory.User,
            artistMetadata = ArtistMetadata(
                aliases = listOf("Legacy alias"),
                biography = "Legacy biography",
                songCount = 99,
                albumCount = 7,
                banner = legacyBanner
            )
        )

        val resolved = resolveProviderArtistProfileMetadata(
            providerId = "a",
            artistId = "42",
            artistName = "Artist",
            destinationMetadata = ArtistMetadata(
                aliases = listOf("Formal alias"),
                biography = "Formal biography",
                songCount = 12,
                banner = formalBanner
            ),
            songs = listOf(legacy)
        )

        assertEquals(listOf("Formal alias", "Legacy alias"), resolved?.aliases)
        assertEquals("Formal biography", resolved?.biography)
        assertEquals(12, resolved?.songCount)
        assertEquals(7, resolved?.albumCount)
        assertEquals(formalBanner, resolved?.banner)
    }

    @Test
    fun legacyProviderArtistMetadataRequiresProviderAndArtistIdMatch() {
        val sameNameWrongArtist = song(
            provider = "a",
            id = "99",
            artists = listOf(ProviderArtistRef("99", "Artist")),
            searchCategory = ProviderSearchCategory.User,
            artistMetadata = ArtistMetadata(biography = "Wrong artist")
        )
        val sameArtistWrongProvider = song(
            provider = "b",
            id = "42",
            artists = listOf(ProviderArtistRef("42", "Artist")),
            searchCategory = ProviderSearchCategory.User,
            artistMetadata = ArtistMetadata(biography = "Wrong provider")
        )
        val nameOnly = song(
            provider = "a",
            id = "42",
            artists = listOf(ProviderArtistRef(name = "Artist")),
            searchCategory = ProviderSearchCategory.User,
            artistMetadata = ArtistMetadata(biography = "Name-only match")
        )

        assertNull(
            resolveProviderArtistProfileMetadata(
                providerId = "a",
                artistId = "42",
                artistName = "Artist",
                destinationMetadata = null,
                songs = listOf(sameNameWrongArtist, sameArtistWrongProvider, nameOnly)
            )
        )
    }

    @Test
    fun multiArtistLegacyMetadataIsNotAttributedToEitherArtist() {
        val ambiguous = song(
            provider = "a",
            id = "a",
            artists = listOf(
                ProviderArtistRef("a", "Artist A"),
                ProviderArtistRef("b", "Artist B")
            ),
            searchCategory = ProviderSearchCategory.User,
            artistMetadata = ArtistMetadata(biography = "Ambiguous biography")
        )

        assertNull(
            resolveProviderArtistProfileMetadata(
                providerId = "a",
                artistId = "a",
                artistName = "Artist A",
                destinationMetadata = null,
                songs = listOf(ambiguous)
            )
        )
        assertNull(
            resolveProviderArtistProfileMetadata(
                providerId = "a",
                artistId = "b",
                artistName = "Artist B",
                destinationMetadata = null,
                songs = listOf(ambiguous)
            )
        )
    }

    @Test
    fun legacyMetadataRequiresSingleArtistUserPayloadIdentity() {
        val normalSong = song(
            provider = "a",
            id = "42",
            artists = listOf(ProviderArtistRef("42", "Artist")),
            artistMetadata = ArtistMetadata(biography = "Normal song metadata")
        )
        val mismatchedUserIdentity = song(
            provider = "a",
            id = "different",
            artists = listOf(ProviderArtistRef("42", "Artist")),
            searchCategory = ProviderSearchCategory.User,
            artistMetadata = ArtistMetadata(biography = "Mismatched User payload")
        )

        assertNull(
            resolveProviderArtistProfileMetadata(
                providerId = "a",
                artistId = "42",
                artistName = "Artist",
                destinationMetadata = null,
                songs = listOf(normalSong, mismatchedUserIdentity)
            )
        )
    }

    @Test
    fun artistFilteringPrefersStableArtistIdAndSupportsMultipleArtists() {
        val matching = song(
            provider = "a",
            id = "1",
            artists = listOf(ProviderArtistRef("other", "Other"), ProviderArtistRef("42", "Kou!"))
        )
        val sameNameWrongId = song(
            provider = "a",
            id = "2",
            artists = listOf(ProviderArtistRef("99", "Kou!"))
        )

        assertEquals(
            listOf("1"),
            providerSongsForArtist(listOf(matching, sameNameWrongId), "a", "42", "Kou!")
                .map(ProviderSong::id)
        )
    }

    @Test
    fun artistFilteringPreservesProviderOrder() {
        val songs = listOf("third", "first", "second").map { id ->
            song(
                provider = "a",
                id = id,
                artists = listOf(ProviderArtistRef("42", "Kou!"))
            )
        }

        assertEquals(
            listOf("third", "first", "second"),
            providerSongsForArtist(songs, "a", "42", "Kou!").map(ProviderSong::id)
        )
    }

    @Test
    fun artistNameFallbackIsNormalizedAndNeverCrossesProvider() {
        val sameProvider = song("a", "1", artist = "Other / KOU!")
        val otherProvider = song("b", "1", artist = "Kou!")

        assertEquals(
            listOf(sameProvider),
            providerSongsForArtist(listOf(sameProvider, otherProvider), "a", "42", " kou! ")
        )
    }

    @Test
    fun albumArtistFilteringSupportsMultipleArtists() {
        val entity = album(
            provider = "a",
            id = "1",
            artists = listOf(ProviderArtistRef("other", "Other"), ProviderArtistRef("42", "Kou!"))
        )

        assertEquals(
            listOf(entity),
            providerAlbumsForArtist(listOf(entity), "a", "42", "Kou!")
        )
    }

    @Test
    fun albumTrackFilteringUsesRemoteIdAndProvider() {
        val target = album("a", "album", title = "Blue")
        val match = song("a", "1", album = ProviderAlbumRef("album", "Blue"))
        val wrongProvider = song("b", "2", album = ProviderAlbumRef("album", "Blue"))
        val wrongIdSameTitle = song("a", "3", album = ProviderAlbumRef("other", "Blue"))

        assertEquals(listOf(match), providerSongsForAlbum(listOf(match, wrongProvider, wrongIdSameTitle), target))
    }

    @Test
    fun albumTrackTitleFallbackStaysInsideProvider() {
        val target = album("a", "album", title = "Blue")
        val match = song("a", "1", album = ProviderAlbumRef(title = " blue "))
        val wrongProvider = song("b", "2", album = ProviderAlbumRef(title = "Blue"))

        assertEquals(listOf(match), providerSongsForAlbum(listOf(match, wrongProvider), target))
    }

    @Test
    fun duplicateRemoteIdsKeepFirstEntity() {
        val firstSong = song("a", "1", artist = "First")
        val firstAlbum = album("a", "1", title = "First")

        assertEquals(listOf(firstSong), dedupeProviderSongs(listOf(firstSong, firstSong.copy(artist = "Second"))))
        assertEquals(listOf(firstAlbum), dedupeProviderAlbums(listOf(firstAlbum, firstAlbum.copy(title = "Second"))))
    }

    private fun song(
        provider: String,
        id: String,
        artist: String = "Artist",
        artists: List<ProviderArtistRef> = emptyList(),
        album: ProviderAlbumRef? = null,
        searchCategory: ProviderSearchCategory = ProviderSearchCategory.Single,
        artistMetadata: ArtistMetadata? = null
    ) = ProviderSong(
        trackRef = ExtensionTrackRef(provider, id),
        title = "Song $id",
        artist = artist,
        searchCategory = searchCategory,
        artistMetadata = artistMetadata,
        artists = artists,
        album = album
    )

    private fun album(
        provider: String,
        id: String,
        title: String = "Album $id",
        artists: List<ProviderArtistRef> = emptyList()
    ) = ProviderAlbum(
        identity = ProviderEntityIdentity(provider, id),
        title = title,
        artist = artists.joinToString(" / ", transform = ProviderArtistRef::name),
        artists = artists
    )

    private class TestProvider(
        override val capabilities: CanonicalAtomicCapabilitySet = CanonicalAtomicCapabilitySet.Empty,
        private val songs: List<ProviderSong>? = null,
        private val albums: List<ProviderAlbum>? = null
    ) : MusicProvider {
        override val musicSources: Set<String> = emptySet()
        override suspend fun searchPage(request: ProviderSearchRequest) = ProviderSearchPage(emptyList())
        override suspend fun getSongs(): List<ProviderSong>? = songs
        override suspend fun getAlbums(): List<ProviderAlbum>? = albums
        override suspend fun resolvePersistentSong(persistentId: String): ProviderSong? = null
        override suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource? = null
    }
}
