package ink.tenqui.flowtone.data.local

import ink.tenqui.flowtone.core.model.PersistentTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikedTracksPolicyTest {
    @Test
    fun `liked state round trip supports local and online identities`() {
        val local = PersistentTrack.Local("1", "Local", "Artist", 100L)
        val online = PersistentTrack.Online("music.example", "2", "Online", "Artist", 100L)

        val liked = updatedLikedTracks(updatedLikedTracks(emptyList(), local, true), online, true)
        val unliked = updatedLikedTracks(liked, online, false)

        assertEquals(listOf(local.identityKey, online.identityKey), liked.map { it.identityKey })
        assertTrue(unliked.any { it.identityKey == local.identityKey })
        assertFalse(unliked.any { it.identityKey == online.identityKey })
    }

    @Test
    fun `liking same identity remains idempotent`() {
        val first = PersistentTrack.Local("1", "Old", "Artist", 100L)
        val sameIdentity = PersistentTrack.Local("1", "New", "Artist", 100L)

        val result = updatedLikedTracks(listOf(first), sameIdentity, true)

        assertEquals(1, result.size)
        assertEquals(first, result.single())
    }
}
