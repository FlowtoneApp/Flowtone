package ink.tenqui.flowtone.playback

import android.net.testUri
import androidx.media3.common.C
import androidx.media3.common.Player
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlaybackQueueTest {
    @Test
    fun `mixed logical queue keeps local and online items`() {
        val items = listOf(local("A", 0), online("B", 1), online("C", 2), local("D", 3))
        val queue = SessionPlaybackQueue()

        queue.replace(items, selectedIndex = 1)

        assertEquals(items.map { it.stableIdentity }, queue.sourceItems.map { it.stableIdentity })
        assertEquals(items.map { it.stableIdentity }, queue.playbackItems.map { it.stableIdentity })
        assertEquals("B", queue.currentItem?.presentation?.title)
    }

    @Test
    fun `next and previous traverse the logical mixed queue`() {
        val queue = SessionPlaybackQueue()
        queue.replace(listOf(local("A", 0), online("B", 1), online("C", 2), local("D", 3)), 0)

        val forward = mutableListOf(queue.currentItem?.presentation?.title)
        while (queue.nextIndex != null) forward += queue.select(queue.nextIndex!!)?.presentation?.title
        val reverse = mutableListOf(queue.currentItem?.presentation?.title)
        while (queue.previousIndex != null) reverse += queue.select(queue.previousIndex!!)?.presentation?.title

        assertEquals(listOf("A", "B", "C", "D"), forward)
        assertEquals(listOf("D", "C", "B", "A"), reverse)
    }

    @Test
    fun `shuffle owns effective order and keeps current logical identity`() {
        val queue = SessionPlaybackQueue(shuffle = { it.reversed() })
        queue.replace(listOf(local("A", 0), online("B", 1), online("C", 2), local("D", 3)), 1)

        queue.setOrderMode(PlaybackOrderMode.Shuffle)

        assertEquals(listOf("B", "D", "C", "A"), queue.playbackItems.map { it.presentation.title })
        assertEquals(0, queue.currentIndex)
        assertEquals("D", queue.playbackItems[queue.nextIndex!!].presentation.title)
    }

    @Test
    fun `repeat one changes mode without replacing queue identities`() {
        val items = listOf(local("A", 0), online("B", 1), local("C", 2))
        val queue = SessionPlaybackQueue()
        queue.replace(items, 1)
        val identities = queue.playbackItems.map { it.queueId }

        queue.setOrderMode(PlaybackOrderMode.RepeatOne)

        assertEquals(identities, queue.playbackItems.map { it.queueId })
        assertEquals("B", queue.currentItem?.presentation?.title)
        assertEquals(PlaybackOrderMode.RepeatOne, queue.orderMode)
    }

    @Test
    fun `stale A resolve cannot commit after B and C targets`() {
        val gate = PlaybackRequestGate()
        val a = gate.begin("A")
        gate.begin("B")
        val c = gate.begin("C")
        var committedTarget = "C"
        var playWhenReady = true

        if (gate.isCurrent(a, committedTarget)) {
            committedTarget = "A"
            playWhenReady = false
        }

        assertEquals("C", committedTarget)
        assertTrue(playWhenReady)
        assertFalse(gate.isCurrent(a, "C"))
        assertTrue(gate.isCurrent(c, "C"))
    }

    @Test
    fun `stale artwork cannot replace current artwork`() {
        val gate = PlaybackRequestGate()
        val old = gate.begin("A")
        val current = gate.begin("C")
        var artwork = byteArrayOf(3)

        if (gate.isCurrent(old, "C")) artwork = byteArrayOf(1)
        if (gate.isCurrent(current, "C")) artwork = byteArrayOf(4)

        assertTrue(artwork.contentEquals(byteArrayOf(4)))
    }

    @Test
    fun `system metadata exists before online resource resolution`() {
        val item = online("B", 1)

        val metadata = systemQueueMetadata(item)

        assertEquals(item.queueId, metadata.mediaId)
        assertEquals("B", metadata.title)
        assertEquals("Artist", metadata.artist)
        assertEquals(item.extensionLargeArtwork, metadata.artwork)
    }

    @Test
    fun `heart targets persistent identity and rejects runtime only identity`() {
        val persistent = online("B", 0)
        val runtimeOnly = persistent.copy(persistentTrack = null)

        assertSame(persistent.persistentTrack, likeCommandTarget(persistent))
        assertEquals(null, likeCommandTarget(runtimeOnly))
    }

    @Test
    fun `queue artwork presentation uses provider extension image`() {
        val item = online("B", 0)

        assertEquals("provider", item.extensionArtwork?.extensionId)
        assertEquals("https://image/B", item.extensionArtwork?.url)
        assertEquals("https://image/B-large", item.extensionLargeArtwork?.url)
    }

    @Test
    fun `activity recreation restores playback order from active session`() {
        assertEquals(
            PlaybackOrderConnectionAction.RestoreFromSession,
            playbackOrderConnectionAction(sessionQueueSize = 4, hasPendingRequest = false)
        )
        assertEquals(
            PlaybackOrderConnectionAction.ApplyInitialPreference,
            playbackOrderConnectionAction(sessionQueueSize = 0, hasPendingRequest = false)
        )
        assertEquals(
            PlaybackOrderConnectionAction.ApplyPendingRequest,
            playbackOrderConnectionAction(sessionQueueSize = 4, hasPendingRequest = true)
        )
    }

    @Test
    fun `prepare does not restart the same resolving logical target`() {
        assertFalse(
            shouldActivateForPrepare(
                currentQueueId = "B",
                resolvingQueueId = "B",
                resolvedQueueId = null
            )
        )
        assertTrue(
            shouldActivateForPrepare(
                currentQueueId = "C",
                resolvingQueueId = "B",
                resolvedQueueId = null
            )
        )
    }

    @Test
    fun `old controller snapshot cannot replace expected logical target`() {
        assertFalse(canApplyLogicalSnapshot(expectedQueueId = "B", snapshotQueueId = "A"))
        assertTrue(canApplyLogicalSnapshot(expectedQueueId = "B", snapshotQueueId = "B"))
        assertTrue(canApplyLogicalSnapshot(expectedQueueId = null, snapshotQueueId = "C"))
    }

    @Test
    fun `progress snapshot must belong to current logical queue identity`() {
        assertFalse(playbackProgressBelongsToCurrentTarget("B", "A"))
        assertTrue(playbackProgressBelongsToCurrentTarget("B", "B"))
        assertFalse(playbackProgressBelongsToCurrentTarget(null, "B"))
        assertFalse(playbackProgressBelongsToCurrentTarget("B", null))
    }

    @Test
    fun `reconnect snapshot restores an already playing session without a playback command`() {
        val snapshot = controllerPlaybackSnapshot(
            isPlaying = true,
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            positionMs = 12_000L,
            bufferedPositionMs = 18_000L,
            durationMs = 200_000L
        )

        assertTrue(snapshot.isPlaying)
        assertTrue(snapshot.playWhenReady)
        assertFalse(snapshot.isBuffering)
        assertEquals(12_000L, snapshot.positionMs)
        assertEquals(18_000L, snapshot.bufferedPositionMs)
        assertEquals(200_000L, snapshot.durationMs)
    }

    @Test
    fun `reconnect snapshot preserves an already paused session`() {
        val snapshot = controllerPlaybackSnapshot(
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_BUFFERING,
            positionMs = 5_000L,
            bufferedPositionMs = 6_000L,
            durationMs = C.TIME_UNSET
        )

        assertFalse(snapshot.isPlaying)
        assertFalse(snapshot.playWhenReady)
        assertTrue(snapshot.isBuffering)
        assertEquals(0L, snapshot.durationMs)
    }

    @Test
    fun `unresolved seek keeps the latest position for its logical target`() {
        val first = PendingLogicalSeek("B", desiredSeekPositionMs(60_000L, 240_000L))
        val latest = PendingLogicalSeek("B", desiredSeekPositionMs(180_000L, 240_000L))

        assertEquals(60_000L, pendingSeekForTarget(first, "B"))
        assertEquals(180_000L, pendingSeekForTarget(latest, "B"))
    }

    @Test
    fun `pending seek is isolated from a later logical target`() {
        val pendingForB = PendingLogicalSeek("B", desiredSeekPositionMs(120_000L, 0L))

        assertEquals(120_000L, pendingSeekForTarget(pendingForB, "B"))
        assertEquals(null, pendingSeekForTarget(pendingForB, "C"))
    }

    @Test
    fun `unresolved seek clamps known duration but retains unknown duration position`() {
        assertEquals(1_000L, desiredSeekPositionMs(positionMs = 2_000L, durationMs = 1_000L))
        assertEquals(2_000L, desiredSeekPositionMs(positionMs = 2_000L, durationMs = 0L))
        assertEquals(0L, desiredSeekPositionMs(positionMs = -1L, durationMs = 0L))
    }

    @Test
    fun `handle seek target is retained for unresolved logical item`() {
        val pendingSeek = PendingLogicalSeek(
            queueId = "B",
            positionMs = desiredSeekPositionMs(positionMs = 120_000L, durationMs = 240_000L)
        )

        assertEquals(120_000L, pendingSeekForTarget(pendingSeek, "B"))
    }

    @Test
    fun `resolve commit uses latest pending seek as its initial position`() {
        val latestPendingSeek = PendingLogicalSeek("B", 120_000L)

        assertEquals(120_000L, pendingSeekForTarget(latestPendingSeek, "B"))
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
        uri = testUri("song-$title")
    )
}
