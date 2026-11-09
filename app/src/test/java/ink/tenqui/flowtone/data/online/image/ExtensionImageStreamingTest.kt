package ink.tenqui.flowtone.data.online.image

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExtensionImageStreamingTest {
    @Test fun writeFailureStopsReadingFurtherChunks() {
        val input = object : ByteArrayInputStream(ByteArray(64 * 1024)) {
            var reads = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                return super.read(b, off, len)
            }
        }
        assertThrows(java.io.IOException::class.java) {
            copyImage(input) { _, _ -> throw java.io.IOException("write failed") }
        }
        assertEquals(1, input.reads)
    }

    @Test fun exactImageLimitIsAccepted() {
        var written = 0
        copyImage(ByteArrayInputStream(ByteArray(MaxImageResponseBytes))) { _, count -> written += count }
        assertEquals(MaxImageResponseBytes, written)
    }
    @Test fun cancellationAfterReadDoesNotWriteChunk() {
        var checks = 0
        var written = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            copyImage(ByteArrayInputStream(ByteArray(32)), {
                if (++checks == 2) throw java.util.concurrent.CancellationException()
            }) { _, count -> written += count }
        }
        assertEquals(0, written)
    }

    @Test fun readFailureStopsCopy() {
        var written = 0
        val input = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("read failed")
        }
        assertThrows(java.io.IOException::class.java) { copyImage(input) { _, count -> written += count } }
        assertEquals(0, written)
    }
    @Test fun acceptsImageLargerThanOrdinaryHttpLimit() {
        val size = 3 * 1024 * 1024
        var written = 0
        copyImage(ByteArrayInputStream(ByteArray(size))) { _, count -> written += count }
        assertEquals(size, written)
    }

    @Test fun rejectsActualBytesOverImageLimit() {
        var written = 0
        assertThrows(ImageResponseTooLargeException::class.java) {
            copyImage(ByteArrayInputStream(ByteArray(MaxImageResponseBytes + 1))) { _, count -> written += count }
        }
        assertEquals(MaxImageResponseBytes, written)
    }
}
