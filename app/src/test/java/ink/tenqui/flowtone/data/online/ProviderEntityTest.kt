package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEntityTest {
    @Test
    fun oldProviderWithoutCollectionsRemainsCompatible() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider()

        assertTrue(provider.entityCapabilities.isEmpty())
        assertNull(provider.getSongs())
        assertNull(provider.getAlbums())
    }

    @Test
    fun songsOnlyProviderDeclaresOnlySongCapability() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider(
            entityCapabilities = setOf(ProviderEntityCapability.Song),
            songs = listOf(song("a", "1"))
        )

        assertEquals(setOf(ProviderEntityCapability.Song), provider.entityCapabilities)
        assertEquals(listOf("1"), provider.getSongs()?.map(ProviderSong::id))
        assertNull(provider.getAlbums())
    }

    @Test
    fun albumsOnlyProviderDeclaresOnlyAlbumCapability() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider(
            entityCapabilities = setOf(ProviderEntityCapability.Album),
            albums = listOf(album("a", "1"))
        )

        assertEquals(setOf(ProviderEntityCapability.Album), provider.entityCapabilities)
        assertEquals(listOf("1"), provider.getAlbums()?.map(ProviderAlbum::id))
        assertNull(provider.getSongs())
    }

    @Test
    fun providerCanExposeBothCollections() = kotlinx.coroutines.runBlocking {
        val provider = TestProvider(
            entityCapabilities = ProviderEntityCapability.entries.toSet(),
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
        album: ProviderAlbumRef? = null
    ) = ProviderSong(
        trackRef = ExtensionTrackRef(provider, id),
        title = "Song $id",
        artist = artist,
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
        override val entityCapabilities: Set<ProviderEntityCapability> = emptySet(),
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
