package ink.tenqui.flowtone.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SongMetadataResolverTest {
    @Test
    fun `file metadata takes priority over MediaStore metadata`() {
        val result = resolveSongMetadata(
            fileMetadata = SongFileMetadata(
                title = "文件标题",
                artist = "文件艺术家"
            ),
            mediaStoreTitle = "曲库标题",
            mediaStoreArtist = "曲库艺术家",
            mediaStoreDurationMs = 120_000L
        )

        assertEquals("文件标题", result.title)
        assertEquals("文件艺术家", result.artist)
        assertEquals(120_000L, result.durationMs)
    }

    @Test
    fun `missing file fields fall back independently to MediaStore`() {
        val result = resolveSongMetadata(
            fileMetadata = SongFileMetadata(
                title = "文件标题",
                artist = "  "
            ),
            mediaStoreTitle = "曲库标题",
            mediaStoreArtist = "曲库艺术家",
            mediaStoreDurationMs = 120_000L
        )

        assertEquals("文件标题", result.title)
        assertEquals("曲库艺术家", result.artist)
        assertEquals(120_000L, result.durationMs)
    }

    @Test
    fun `unknown placeholders fall back before using app defaults`() {
        val mediaStoreFallback = resolveSongMetadata(
            fileMetadata = SongFileMetadata(title = "<unknown>", artist = "<UNKNOWN>"),
            mediaStoreTitle = "曲库标题",
            mediaStoreArtist = "曲库艺术家",
            mediaStoreDurationMs = 0L
        )
        val appFallback = resolveSongMetadata(
            fileMetadata = null,
            mediaStoreTitle = " ",
            mediaStoreArtist = "<unknown>",
            mediaStoreDurationMs = -1L
        )

        assertEquals("曲库标题", mediaStoreFallback.title)
        assertEquals("曲库艺术家", mediaStoreFallback.artist)
        assertEquals("未知歌曲", appFallback.title)
        assertEquals("未知艺术家", appFallback.artist)
        assertEquals(0L, appFallback.durationMs)
    }
}
