package ink.tenqui.flowtone.data.search

import android.net.testUri
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryTest {
    @Test
    fun queryTrimsTextAndNormalizesCase() {
        val query = SearchQuery.from("  HeLLo  ")

        assertEquals("HeLLo", query.text)
        assertEquals("hello", query.normalizedText)
        assertFalse(query.isBlank)
        assertTrue(SearchQuery.from("   ").isBlank)
    }

    @Test
    fun emptyQueryReturnsEmptyResults() = runBlocking {
        val repository = repositoryWithSongs(
            listOf(song(id = 1, title = "River", artist = "Tenqui"))
        )

        val results = repository.search(SearchQuery.from(" "))

        assertEquals(emptyList<Song>(), results.songs)
        assertEquals(emptyList<SearchArtist>(), results.artists)
        assertEquals(emptyList<SearchResult.AlbumResult>(), results.albums)
    }

    @Test
    fun searchesSongTitleIgnoringCaseAndSpaces() = runBlocking {
        val repository = repositoryWithSongs(
            listOf(
                song(id = 1, title = "Blue River", artist = "Tenqui"),
                song(id = 2, title = "Morning", artist = "Flow")
            )
        )

        val results = repository.search(SearchQuery.from("  river "))

        assertEquals(listOf("Blue River"), results.songs.map { it.title })
    }

    @Test
    fun searchesSongArtists() = runBlocking {
        val repository = repositoryWithSongs(
            listOf(
                song(id = 1, title = "Blue River", artist = "Tenqui"),
                song(id = 2, title = "Morning", artist = "Flow")
            )
        )

        val results = repository.search(SearchQuery.from("flow"))

        assertEquals(listOf("Morning"), results.songs.map { it.title })
    }

    @Test
    fun searchesAnyArtistInMultiArtistSong() = runBlocking {
        val repository = repositoryWithSongs(
            listOf(
                song(id = 1, title = "Duet", artist = "Alice/Bob"),
                song(id = 2, title = "Solo", artist = "Charlie")
            )
        )

        val results = repository.search(SearchQuery.from("bob"))

        assertEquals(listOf("Duet"), results.songs.map { it.title })
        assertEquals(listOf("Bob"), results.artists.map { it.name })
    }

    @Test
    fun artistsAreDeduplicatedByStableCaseInsensitiveId() = runBlocking {
        val repository = repositoryWithSongs(
            listOf(
                song(id = 1, title = "First", artist = "Alice"),
                song(id = 2, title = "Second", artist = "alice"),
                song(id = 3, title = "Third", artist = "Alice/Bob")
            )
        )

        val results = repository.search(SearchQuery.from("ali"))

        assertEquals(listOf("Alice"), results.artists.map { it.name })
        assertEquals(3, results.artists.first().songCount)
    }

    @Test
    fun sortsByExactThenPrefixThenContainsThenStableOrder() = runBlocking {
        val repository = repositoryWithSongs(
            listOf(
                song(id = 1, title = "My Love", artist = "A"),
                song(id = 2, title = "Love", artist = "B"),
                song(id = 3, title = "Lovely Day", artist = "C"),
                song(id = 4, title = "Another Love", artist = "D")
            )
        )

        val results = repository.search(SearchQuery.from("love"))

        assertEquals(
            listOf("Love", "Lovely Day", "My Love", "Another Love"),
            results.songs.map { it.title }
        )
    }

    @Test
    fun calculatesSearchQueueStartIndexFromSongResults() {
        val songs = listOf(
            song(id = 1, title = "First", artist = "A"),
            song(id = 2, title = "Second", artist = "B"),
            song(id = 3, title = "Third", artist = "C")
        )

        assertEquals(1, searchPlaybackQueueStartIndex(songs, songs[1]))
    }

    @Test
    fun searchesAlbumTitleIgnoringCaseAndSpaces() = runBlocking {
        val repository = repositoryWithLibrary(
            albums = listOf(album(id = 42L, title = "Blue Hour", artist = "Aimer"))
        )

        val results = repository.search(SearchQuery.from("  blue "))

        assertEquals(listOf("Blue Hour"), results.albums.map { it.title })
    }

    @Test
    fun searchesAlbumArtist() = runBlocking {
        val repository = repositoryWithLibrary(
            albums = listOf(album(id = 42L, title = "Blue Hour", artist = "Aimer"))
        )

        val results = repository.search(SearchQuery.from("aimer"))

        assertEquals(listOf(42L), results.albums.map { it.albumId })
    }

    @Test
    fun deduplicatesAlbumsByStableAlbumId() = runBlocking {
        val repository = repositoryWithLibrary(
            albums = listOf(
                album(id = 42L, title = "Blue Hour", artist = "Aimer"),
                album(id = 42L, title = "Blue Hour (Deluxe)", artist = "Aimer")
            )
        )

        val results = repository.search(SearchQuery.from("blue"))

        assertEquals(listOf(42L), results.albums.map { it.albumId })
    }

    @Test
    fun unrelatedQueryDoesNotReturnAlbums() = runBlocking {
        val repository = repositoryWithLibrary(
            albums = listOf(album(id = 42L, title = "Blue Hour", artist = "Aimer"))
        )

        val results = repository.search(SearchQuery.from("ambient"))

        assertTrue(results.albums.isEmpty())
    }

    @Test
    fun albumResultMetadataDoesNotReplaceAlbumNavigationIdentity() = runBlocking {
        val repository = repositoryWithLibrary(
            albums = listOf(
                album(
                    id = 42L,
                    title = "Blue Hour (Deluxe)",
                    artist = "Aimer",
                    artworkUri = testUri("album-artwork")
                )
            )
        )

        val result = repository.search(SearchQuery.from("blue")).albums.single()
        var openedAlbumId: Long? = null
        val onOpenAlbum: (Long) -> Unit = { albumId -> openedAlbumId = albumId }
        onOpenAlbum(result.albumId)

        assertEquals(42L, result.albumId)
        assertEquals("Blue Hour (Deluxe)", result.title)
        assertEquals("Aimer", result.artist)
        assertTrue(result.artworkUri != null)
        assertEquals(42L, openedAlbumId)
    }

    private suspend fun repositoryWithSongs(songs: List<Song>): SearchRepository {
        return repositoryWithLibrary(songs = songs)
    }

    private suspend fun repositoryWithLibrary(
        songs: List<Song> = emptyList(),
        albums: List<LocalAlbum> = emptyList()
    ): SearchRepository {
        val repository = SearchRepository()
        repository.updateLocalLibrary(songs, albums)
        return repository
    }

    private fun album(
        id: Long,
        title: String,
        artist: String,
        artworkUri: android.net.Uri? = null
    ): LocalAlbum = LocalAlbum(
        id = id,
        title = title,
        artist = artist,
        artworkUri = artworkUri,
        songs = emptyList()
    )

    private fun song(
        id: Long,
        title: String,
        artist: String
    ): Song {
        return Song(
            id = id,
            sourceType = SourceType.Local,
            title = title,
            artist = artist,
            durationMs = 180_000L,
            uri = testUri("song-$id")
        )
    }
}
