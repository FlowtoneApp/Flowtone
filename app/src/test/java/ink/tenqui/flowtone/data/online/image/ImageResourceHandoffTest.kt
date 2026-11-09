package ink.tenqui.flowtone.data.online.image

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageResourceHandoffTest {
    @Test fun cancellationDuringCommitClosesSnapshotButRetainsCommittedData() = runBlocking {
        val committing = CountDownLatch(1)
        val finishCommit = CountDownLatch(1)
        val closes = AtomicInteger()
        var committed = false
        val job = launch(Dispatchers.Default) {
            withImageResourceHandoff { owner ->
                committing.countDown()
                finishCommit.await()
                committed = true
                owner.own { closes.incrementAndGet() }
            }
        }
        committing.await()
        job.cancel()
        finishCommit.countDown()
        job.join()
        assertEquals(true, committed)
        assertEquals(1, closes.get())
    }

    @Test fun cancelledBeforeDeliveryCleansResourceOnce() = runBlocking {
        val ready = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val closes = AtomicInteger()
        val job = launch(Dispatchers.Default) {
            withImageResourceHandoff { owner ->
                owner.own { closes.incrementAndGet() }
                ready.countDown()
                finish.await()
            }
        }
        ready.await()
        job.cancel()
        finish.countDown()
        job.join()
        assertEquals(1, closes.get())
    }

    @Test fun successfulDeliveryTransfersCloseResponsibility() = runBlocking {
        val closes = AtomicInteger()
        val closeable = java.io.Closeable { closes.incrementAndGet() }
        val result = withImageResourceHandoff { owner -> owner.own { closeable.close() }; closeable }
        yield()
        assertEquals(0, closes.get())
        result.close()
        assertEquals(1, closes.get())
    }
}
