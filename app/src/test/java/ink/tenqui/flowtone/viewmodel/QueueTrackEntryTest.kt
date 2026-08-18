package ink.tenqui.flowtone.viewmodel

import android.net.testUri
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSong
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueTrackEntryTest {
    @Test
    fun `runtime provider song takes precedence over persistent recovery`() {
        val song = song(2, SourceType.Online)
        val entry = QueueTrackEntry(
            persistentTrack = PersistentTrack.Online("soundcloud.com", "stable", "B", "Artist"),
            presentation = song,
            runtimeProviderSong = ProviderSong(
                ExtensionTrackRef("provider.a", "runtime-b"),
                "B",
                "Artist"
            )
        )

        assertEquals(QueueTrackPlaybackKind.RuntimeProvider, entry.playbackKind)
    }

    @Test
    fun `restored online entry uses persistent resolution route`() {
        val entry = QueueTrackEntry(
            persistentTrack = PersistentTrack.Online("soundcloud.com", "stable", "B", "Artist"),
            presentation = song(2, SourceType.Online)
        )

        assertEquals(QueueTrackPlaybackKind.PersistentOnline, entry.playbackKind)
    }

    @Test
    fun `mixed collection gives media controller only selected item`() {
        val queue = listOf(
            song(1, SourceType.Local),
            song(2, SourceType.Online),
            song(3, SourceType.Local),
            song(4, SourceType.Online)
        )

        assertEquals(
            listOf(queue[2]),
            mediaControllerQueueForSelection(true, queue[2], queue, queue)
        )
        assertEquals(
            queue,
            mediaControllerQueueForSelection(false, queue[2], queue, queue)
        )
    }

    private fun song(id: Long, source: SourceType) = Song(
        id = id,
        sourceType = source,
        title = "Song $id",
        artist = "Artist",
        durationMs = 100L,
        uri = testUri("song-$id")
    )
}
