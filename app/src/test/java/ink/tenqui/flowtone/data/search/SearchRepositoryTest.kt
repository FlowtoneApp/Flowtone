package ink.tenqui.flowtone.data.search

import android.net.testUri
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

    private suspend fun repositoryWithSongs(songs: List<Song>): SearchRepository {
        val repository = SearchRepository()
        repository.updateLocalSongs(songs)
        return repository
    }

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
