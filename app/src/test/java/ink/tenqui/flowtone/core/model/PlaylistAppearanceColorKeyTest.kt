package ink.tenqui.flowtone.core.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistAppearanceColorKeyTest {
    @Test
    fun storageValueParsingIsCaseInsensitiveAndRejectsUnknownValues() {
        assertEquals(
            PlaylistAppearanceColorKey.ROSE,
            PlaylistAppearanceColorKey.fromStorageValue(" rose ")
        )
        assertNull(PlaylistAppearanceColorKey.fromStorageValue("not-a-color"))
        assertNull(PlaylistAppearanceColorKey.fromStorageValue(null))
    }

    @Test
    fun legacyColorFallbackIsStableForTheSamePlaylistId() {
        val id = "playlist-with-a-stable-identity"

        val first = playlistAppearanceColorKeyForStableId(id)
        val second = playlistAppearanceColorKeyForStableId(id)

        assertEquals(first, second)
        assertTrue(first in StablePlaylistAppearanceColorKeys)
    }

    @Test
    fun randomColorCanAvoidTheMostRecentlyCreatedPlaylistColor() {
        val avoidedColor = PlaylistAppearanceColorKey.BLUE

        repeat(32) { seed ->
            assertNotEquals(
                avoidedColor,
                randomPlaylistAppearanceColorKey(
                    avoiding = avoidedColor,
                    random = Random(seed)
                )
            )
        }
    }

    @Test
    fun playlistDefaultUsesItsStableIdInsteadOfAListPosition() {
        val id = "legacy-playlist-id"
        val playlist = Playlist(
            id = id,
            title = "Legacy",
            order = 7,
            createdAt = 1L,
            updatedAt = 1L
        )

        assertEquals(
            playlistAppearanceColorKeyForStableId(id),
            playlist.appearanceColorKey
        )
    }
}
