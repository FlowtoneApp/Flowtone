package ink.tenqui.flowtone.data.local

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.data.repository.PlaylistMutationResult
import ink.tenqui.flowtone.data.repository.PlaylistRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentCollectionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearStores() {
        context.getSharedPreferences("flowtone_liked_songs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("flowtone_library_playlists", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun onlineLikeSurvivesStoreRecreation() {
        val track = PersistentTrack.Online(
            "soundcloud.com",
            "stable-id",
            "Cached title",
            "Cached artist",
            123L
        )
        LikedSongsStore(context).saveLikedTracks(listOf(track))

        val reloaded = LikedSongsStore(context).loadLikedTracks(emptyList()).single()

        assertEquals(track, reloaded)
        assertEquals("Cached title", reloaded.cachedTitle)
    }

    @Test
    fun legacyLocalLikeMigratesWhenLocalLibraryIsAvailable() {
        val song = Song(
            id = 7L,
            sourceType = SourceType.Local,
            title = "Local",
            artist = "Artist",
            durationMs = 1L,
            uri = Uri.parse("content://media/7")
        )
        LikedSongsStore(context).saveLikedSongKeys(listOf(song.uri.toString()))

        val reloaded = LikedSongsStore(context).loadLikedTracks(listOf(song))

        assertEquals(listOf("local:7"), reloaded.map(PersistentTrack::identityKey))
    }

    @Test
    fun mixedPlaylistSurvivesStorageRecreationInOrder() {
        val entries = listOf(
            entry("a", PersistentTrack.Local("1", "A", "Local"), 1L),
            entry("b", PersistentTrack.Online("soundcloud.com", "b", "B", "Online"), 2L),
            entry("c", PersistentTrack.Local("3", "C", "Local"), 3L)
        )
        assertTrue(PlaylistStorage(context).savePlaylistSongEntries(entries))

        val reloaded = PlaylistStorage(context).loadPlaylistSongEntries()

        assertEquals(listOf("a", "b", "c"), reloaded.map { it.id })
        assertEquals(entries.map { it.track.identityKey }, reloaded.map { it.track.identityKey })
    }

    @Test
    fun repositoryCreatesPlaylistAndAddsAndRemovesOnlineTrack() = runBlocking {
        val repository = PlaylistRepository(PlaylistStorage(context))
        val created = repository.createPlaylist("Mixed") as PlaylistMutationResult.Success
        val track = PersistentTrack.Online("soundcloud.com", "stable", "Title", "Artist")

        assertTrue(repository.addTrackToPlaylist(created.value.id, track) is PlaylistMutationResult.Success)
        assertEquals(track.identityKey, repository.playlistSongEntries.value.single().track.identityKey)

        val entryId = repository.playlistSongEntries.value.single().id
        assertTrue(
            repository.removeEntriesFromPlaylist(created.value.id, setOf(entryId))
                is PlaylistMutationResult.Success
        )
        assertTrue(repository.playlistSongEntries.value.isEmpty())
    }

    private fun entry(id: String, track: PersistentTrack, addedAt: Long) =
        PlaylistSongEntry(id, "playlist", track, addedAt)
}
