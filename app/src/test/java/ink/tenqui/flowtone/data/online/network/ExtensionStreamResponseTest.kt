package ink.tenqui.flowtone.data.online.network

import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionStreamResponseTest {
    @Test fun concurrentCloseReleasesResponseExactlyOnce() {
        val bodyCloses = AtomicInteger()
        val releases = AtomicInteger()
        val body = object : ByteArrayInputStream(byteArrayOf()) {
            override fun close() { bodyCloses.incrementAndGet(); super.close() }
        }
        val response = ExtensionStreamResponse(200, emptyMap(), body, "https://example.test/") {
            releases.incrementAndGet()
        }
        val start = CountDownLatch(1)
        val threads = List(16) { Thread { start.await(); response.close() }.apply { start() } }
        start.countDown()
        threads.forEach(Thread::join)
        assertEquals(1, bodyCloses.get())
        assertEquals(1, releases.get())
    }
}
