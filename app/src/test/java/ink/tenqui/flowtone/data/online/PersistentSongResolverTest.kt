package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentSongResolverTest {
    @Test
    fun `provider selection uses normalized music sources and deterministic id order`() {
        val second = FakeProvider(setOf("soundcloud.com"))
        val first = FakeProvider(setOf("SoundCloud.com/"))

        val selected = selectMusicProviderForSource(
            linkedMapOf("provider.b" to second, "provider.a" to first),
            "SOUNDCLOUD.COM/"
        )

        assertSame(first, selected)
    }

    @Test
    fun `missing provider is distinct from unresolved persistent id`() = runBlocking {
        val track = onlineTrack()
        val missing = resolvePersistentSongWithProviders(emptyMap(), track)
        val unresolvedProvider = FakeProvider(setOf("soundcloud.com"), resolvedSong = null)
        val unresolved = resolvePersistentSongWithProviders(
            mapOf("provider" to unresolvedProvider),
            track
        )

        assertTrue(missing is PersistentSongResolution.ProviderMissing)
        assertTrue(unresolved is PersistentSongResolution.Unresolved)
        assertEquals(listOf("stable-id"), unresolvedProvider.resolvedIds)
    }

    @Test
    fun `persistent id is passed opaque and resolved song keeps current runtime ref`() = runBlocking {
        val runtimeSong = ProviderSong(
            trackRef = ExtensionTrackRef("provider.b", "runtime-opaque"),
            title = "Title",
            artist = "Artist",
            persistentId = "stable-id",
            sourceHost = "soundcloud.com"
        )
        val provider = FakeProvider(setOf("soundcloud.com"), runtimeSong)

        val result = resolvePersistentSongWithProviders(mapOf("provider.b" to provider), onlineTrack())

        assertTrue(result is PersistentSongResolution.Resolved)
        assertEquals("stable-id", provider.resolvedIds.single())
        assertEquals("provider.b", (result as PersistentSongResolution.Resolved).song.trackRef.extensionId)
        assertEquals(0, provider.playbackRequests)
    }

    private fun onlineTrack() = PersistentTrack.Online(
        "soundcloud.com",
        "stable-id",
        "Cached",
        "Artist"
    )

    private class FakeProvider(
        override val musicSources: Set<String>,
        private val resolvedSong: ProviderSong? = null
    ) : MusicProvider {
        val resolvedIds = mutableListOf<String>()
        var playbackRequests = 0

        override suspend fun searchSongs(keyword: String): List<ProviderSong> = emptyList()

        override suspend fun resolvePersistentSong(persistentId: String): ProviderSong? {
            resolvedIds += persistentId
            return resolvedSong
        }

        override suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource? {
            playbackRequests++
            return null
        }
    }
}
