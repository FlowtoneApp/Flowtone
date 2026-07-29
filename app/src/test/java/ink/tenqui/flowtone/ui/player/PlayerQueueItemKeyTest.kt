package ink.tenqui.flowtone.ui.player

import android.net.testUri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerQueueItemKeyTest {
    @Test
    fun repeatedSongAtDifferentQueuePositionsHasUniqueComposeKey() {
        val song = Song(
            id = 42L,
            sourceType = SourceType.Local,
            title = "重复歌曲",
            artist = "测试艺术家",
            durationMs = 180_000L,
            uri = testUri("repeat.mp3")
        )

        assertNotEquals(song.queueItemKey(1), song.queueItemKey(2))
    }
}
