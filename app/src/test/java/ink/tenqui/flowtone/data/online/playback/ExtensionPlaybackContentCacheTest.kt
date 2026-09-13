package ink.tenqui.flowtone.data.online.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionPlaybackContentCacheTest {
    @Test
    fun `preload byte count follows configured percentage`() {
        assertEquals(0L, preloadByteCount(totalBytes = 1_000L, percentage = 0))
        assertEquals(100L, preloadByteCount(totalBytes = 1_000L, percentage = 10))
        assertEquals(500L, preloadByteCount(totalBytes = 1_000L, percentage = 50))
    }

    @Test
    fun `preload byte count handles small and invalid lengths`() {
        assertEquals(1L, preloadByteCount(totalBytes = 1L, percentage = 10))
        assertEquals(0L, preloadByteCount(totalBytes = 0L, percentage = 10))
        assertEquals(0L, preloadByteCount(totalBytes = -1L, percentage = 10))
    }

    @Test
    fun `preload byte count avoids overflow and caps percentage`() {
        assertEquals(Long.MAX_VALUE / 2L, preloadByteCount(Long.MAX_VALUE, 50))
        assertEquals(Long.MAX_VALUE, preloadByteCount(Long.MAX_VALUE, 200))
    }
}
