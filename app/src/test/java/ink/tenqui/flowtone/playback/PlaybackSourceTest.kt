package ink.tenqui.flowtone.playback

import android.net.testUri
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderLikedSongs
import ink.tenqui.flowtone.data.online.ProviderSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceTest {
    @Test
    fun providerLikedSongsKeepSourceAndTrackIdentityInTheOnlineQueue() {
        val first = ProviderLikedSongs("provider-a")
        val second = ProviderLikedSongs("provider-b")
        val source = PlaybackSource.providerLikedSongs(first)
        val song = ProviderSong(ExtensionTrackRef(first.providerId, "track"), "Track", "Artist")
        val item = PlaybackQueueItem.provider(
            song,
            Song(1L, SourceType.Online, song.title, song.artist, 0L, testUri("provider-track")),
            source,
            0
        )

        assertNotEquals(first.stableKey, second.stableKey)
        assertNotEquals(LikedSongsPlaylistId, source.sourceId)
        assertNotEquals(PlaybackSource.LikedSongs.key, source.key)
        assertNotEquals(PlaybackSource.providerLikedSongs(second).key, source.key)
        assertNotEquals(PlaybackSource.providerPlaylist(first.providerId, "track", ProviderLikedSongs.Title).key, source.key)
        assertEquals(first.stableKey, item.source.key)
        assertEquals(song.trackRef, item.runtimeProviderSong?.trackRef)
        assertTrue(item.isOnline)
    }

    @Test
    fun userPlaylistKeyDoesNotDependOnDisplayName() {
        val first = PlaybackSource.userPlaylist("playlist-1", "夜晚")
        val renamed = PlaybackSource.userPlaylist("playlist-1", "深夜")

        assertEquals("playlist:playlist-1", first.key)
        assertEquals(first.key, renamed.key)
        assertEquals("深夜", renamed.displayName)
    }

    @Test
    fun fixedSourcesUseStableKeys() {
        assertEquals("liked_songs", PlaybackSource.LikedSongs.key)
        assertEquals("local_library", PlaybackSource.LocalLibrary.key)
        assertEquals("search", PlaybackSource.Search.key)
        assertEquals("\u641c\u7d22", PlaybackSource.Search.displayName)
    }

    @Test
    fun blankUserPlaylistFallsBackToUnknown() {
        assertEquals(PlaybackSource.Unknown, PlaybackSource.userPlaylist(" ", "空歌单"))
    }

    @Test
    fun albumKeyUsesStableIdInsteadOfDisplayName() {
        val first = PlaybackSource.album(42L, "Blue")
        val renamed = PlaybackSource.album(42L, "Blue (Deluxe)")

        assertEquals("album:42", first.key)
        assertEquals(first.key, renamed.key)
        assertEquals("Blue (Deluxe)", renamed.displayName)
        assertEquals(PlaybackSource.Unknown, PlaybackSource.album(null, "Blue"))
    }

    @Test
    fun providerPlaylistKeyUsesProviderAndRemoteIdentity() {
        val first = PlaybackSource.providerPlaylist("provider-a", "42", "Morning")
        val renamed = PlaybackSource.providerPlaylist("provider-a", "42", "Morning Mix")

        assertEquals("provider_playlist:provider-a:42", first.key)
        assertEquals(first.key, renamed.key)
        assertEquals("Morning Mix", renamed.displayName)
        assertEquals(
            PlaybackSource.Unknown,
            PlaybackSource.providerPlaylist("provider-a", " ", "Morning")
        )
    }
}
