package ink.tenqui.flowtone.data.local

import android.net.testUri
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentCollectionStorageTest {
    @Test
    fun `liked track format reload preserves online metadata`() {
        val online = PersistentTrack.Online(
            "soundcloud.com",
            "https://service.example/track",
            "Online title",
            "Online artist",
            9000L
        )

        val reloaded = PersistentTrackListJsonCodec.decode(
            PersistentTrackListJsonCodec.encode(listOf(online))
        ).single() as PersistentTrack.Online

        assertEquals(online, reloaded)
        assertEquals("Online title", reloaded.cachedTitle)
        assertEquals("Online artist", reloaded.cachedArtist)
        assertEquals(9000L, reloaded.cachedDurationMs)
    }

    @Test
    fun `legacy local liked key migrates without dropping stored online track`() {
        val localSong = localSong(12L, "Local")
        val online = PersistentTrack.Online("soundcloud.com", "stable", "Online", "Artist")

        val migrated = mergeStoredAndLegacyLikedTracks(
            stored = listOf(online),
            legacyKeys = listOf(localSong.uri.toString()),
            localSongs = listOf(localSong)
        )

        assertEquals(listOf(online.identityKey, "local:12"), migrated.map { it.identityKey })
    }

    @Test
    fun `mixed playlist reload preserves exact order`() {
        val entries = listOf(
            entry("a", PersistentTrack.Local("1", "A", "Local"), 1L),
            entry("b", PersistentTrack.Online("soundcloud.com", "b", "B", "Online"), 2L),
            entry("c", PersistentTrack.Local("3", "C", "Local"), 3L),
            entry("d", PersistentTrack.Online("soundcloud.com", "d", "D", "Online"), 4L)
        )

        val reloaded = decodePlaylistSongEntries(encodePlaylistSongEntries(entries))

        assertEquals(listOf("a", "b", "c", "d"), reloaded.map { it.id })
        assertEquals(entries.map { it.track.identityKey }, reloaded.map { it.track.identityKey })
    }

    @Test
    fun `legacy playlist song ids migrate in their original order`() {
        val legacy = JSONArray()
            .put(JSONObject().put("id", "first").put("playlistId", "p").put("songId", 9L).put("addedAt", 1L))
            .put(JSONObject().put("id", "second").put("playlistId", "p").put("songId", 3L).put("addedAt", 2L))

        val migrated = decodePlaylistSongEntries(legacy.toString())

        assertEquals(listOf("9", "3"), migrated.map { (it.track as PersistentTrack.Local).songId })
    }

    @Test
    fun `legacy online playlist record migrates to unified online track`() {
        val online = JSONObject()
            .put("sourceHost", "SoundCloud.com/")
            .put("persistentId", "stable")
            .put("title", "Title")
            .put("artist", "Artist")
            .put("durationMs", 42L)
        val legacy = JSONArray().put(
            JSONObject().put("id", "online").put("playlistId", "p")
                .put("songId", "-123").put("onlineSong", online).put("addedAt", 1L)
        )

        val migrated = decodePlaylistSongEntries(legacy.toString()).single().track

        assertTrue(migrated is PersistentTrack.Online)
        assertEquals("soundcloud.com", (migrated as PersistentTrack.Online).sourceHost)
        assertEquals("stable", migrated.persistentId)
    }

    private fun entry(id: String, track: PersistentTrack, addedAt: Long) =
        PlaylistSongEntry(id, "playlist", track, addedAt)

    private fun localSong(id: Long, title: String) = Song(
        id = id,
        sourceType = SourceType.Local,
        title = title,
        artist = "Artist",
        durationMs = 100L,
        uri = testUri("song-$id")
    )
}
