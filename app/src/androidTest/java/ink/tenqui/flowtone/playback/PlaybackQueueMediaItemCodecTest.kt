package ink.tenqui.flowtone.playback

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueMediaItemCodecTest {
    @Test
    fun mixedLocalOnlineQueueRoundTripsThroughSessionMediaItems() {
        val source = listOf(local("A", 0), online("B", 1), online("C", 2), local("D", 3))

        val restored = source.map(PlaybackQueueMediaItemCodec::encode)
            .mapNotNull(PlaybackQueueMediaItemCodec::decode)

        assertEquals(source.map { it.queueId }, restored.map { it.queueId })
        assertEquals(source.map { it.sourceIndex }, restored.map { it.sourceIndex })
        assertEquals(listOf(false, true, true, false), restored.map { it.isOnline })
    }

    @Test
    fun providerArtworkAndPersistentIdentitySurviveSessionRoundTrip() {
        val item = online("B", 1)
        val artworkBytes = byteArrayOf(1, 2, 3, 4)

        val mediaItem = PlaybackQueueMediaItemCodec.encode(item, artworkBytes)
        val restored = PlaybackQueueMediaItemCodec.decode(mediaItem)

        assertTrue(mediaItem.mediaMetadata.artworkData!!.contentEquals(artworkBytes))
        assertEquals(item.extensionArtwork, restored?.extensionArtwork)
        assertEquals(item.extensionLargeArtwork, restored?.extensionLargeArtwork)
        assertEquals(item.persistentTrack?.identityKey, restored?.persistentTrack?.identityKey)
    }

    @Test
    fun activityRecreationRestoresFullOnlineQueueWithoutLocalLibraryFallback() {
        val serviceTimeline = listOf(online("A", 0), online("B", 1), online("C", 2))
            .map(PlaybackQueueMediaItemCodec::encode)
        val restored = serviceTimeline.mapNotNull(PlaybackQueueMediaItemCodec::decode)
        val currentIndex = 1

        assertEquals(listOf("A", "B", "C"), restored.map { it.presentation.title })
        assertEquals("B", restored[currentIndex].presentation.title)
        assertTrue(restored.all(PlaybackQueueItem::isOnline))
        assertTrue(restored.all { it.runtimeProviderSong != null })
    }

    @Test
    fun runtimeOnlyOnlineItemDoesNotInventPersistentLikeIdentity() {
        val item = online("B", 0).copy(persistentTrack = null)
        val restored = PlaybackQueueMediaItemCodec.decode(PlaybackQueueMediaItemCodec.encode(item))

        assertNull(restored?.persistentTrack)
        assertNull(likeCommandTarget(restored))
    }

    @Test
    fun onlineLogicalItemsWithoutLocalConfigurationAreAdmittedAsFlowtoneQueue() {
        val onlineMediaItem = PlaybackQueueMediaItemCodec.encode(online("B", 0))

        assertNull(onlineMediaItem.localConfiguration)
        assertTrue(PlaybackQueueMediaItemCodec.isLogicalQueue(listOf(onlineMediaItem)))
    }

    private fun local(title: String, index: Int): PlaybackQueueItem {
        val song = song(title, SourceType.Local)
        return PlaybackQueueItem.local(song, PlaybackSource.LocalLibrary, index)
    }

    private fun online(title: String, index: Int): PlaybackQueueItem {
        val providerSong = ProviderSong(
            trackRef = ExtensionTrackRef("provider", title),
            title = title,
            artist = "Artist",
            durationMs = 1_000L,
            artwork = ExtensionImage("provider", "https://image/$title"),
            largeArtwork = ExtensionImage("provider", "https://image/$title-large"),
            persistentId = "persistent-$title",
            sourceHost = "music.example"
        )
        return PlaybackQueueItem.provider(
            providerSong,
            song(title, SourceType.Online),
            PlaybackSource.Search,
            index
        )
    }

    private fun song(title: String, sourceType: SourceType) = Song(
        id = title.first().code.toLong(),
        sourceType = sourceType,
        title = title,
        artist = "Artist",
        durationMs = 1_000L,
        uri = Uri.parse("content://flowtone/song/$title")
    )
}
