package ink.tenqui.flowtone.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SongFileMetadataReaderTest {
    @Test
    fun `maps TagLib title and all artists from the audio file`() {
        val metadata = songFileMetadataFrom(
            mapOf(
                "title" to arrayOf(" 文件标题 "),
                "ARTIST" to arrayOf("艺术家 A", " ", "艺术家 B")
            )
        )

        assertEquals("文件标题", metadata.title)
        assertEquals("艺术家 A/艺术家 B", metadata.artist)
    }
}
