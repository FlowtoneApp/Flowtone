package ink.tenqui.flowtone.core.model

import ink.tenqui.flowtone.data.local.PersistentTrackJsonCodec
import ink.tenqui.flowtone.data.local.PersistentTrackListJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentTrackTest {
    @Test
    fun `local track serializes and deserializes`() {
        val track = PersistentTrack.Local("42", "Local", "Artist", 1234L)

        assertEquals(track, PersistentTrackJsonCodec.decode(PersistentTrackJsonCodec.encode(track)))
    }

    @Test
    fun `online track serializes all persistent identity and cached metadata`() {
        val track = PersistentTrack.Online(
            sourceHost = "SoundCloud.com/",
            persistentId = "urn:stable:track:7",
            cachedTitle = "Title",
            cachedArtist = "Artist",
            cachedDurationMs = 5678L
        )

        val decoded = PersistentTrackJsonCodec.decode(
            PersistentTrackJsonCodec.encode(track)
        ) as PersistentTrack.Online

        assertEquals("soundcloud.com", decoded.sourceHost)
        assertEquals(track.persistentId, decoded.persistentId)
        assertEquals(track.cachedTitle, decoded.cachedTitle)
        assertEquals(track.cachedArtist, decoded.cachedArtist)
        assertEquals(track.cachedDurationMs, decoded.cachedDurationMs)
    }

    @Test
    fun `online equality uses source and persistent id only`() {
        val first = PersistentTrack.Online("SoundCloud.com", "stable-id", "Old", "Artist A")
        val sameIdentity = PersistentTrack.Online("soundcloud.com/", "stable-id", "New", "Artist B")
        val otherSource = PersistentTrack.Online("example.com", "stable-id", "Old", "Artist A")
        val otherId = PersistentTrack.Online("soundcloud.com", "other", "Old", "Artist A")

        assertEquals(first, sameIdentity)
        assertEquals(first.hashCode(), sameIdentity.hashCode())
        assertNotEquals(first, otherSource)
        assertNotEquals(first, otherId)
    }

    @Test
    fun `collection format contains no runtime or playback identity`() {
        val raw = PersistentTrackListJsonCodec.encode(
            listOf(PersistentTrack.Online("soundcloud.com", "stable-id", "Title", "Artist"))
        )

        assertTrue(raw.contains("stable-id"))
        assertFalse(raw.contains("extensionId"))
        assertFalse(raw.contains("opaqueId"))
        assertFalse(raw.contains("flowtone-extension://media"))
    }
}
