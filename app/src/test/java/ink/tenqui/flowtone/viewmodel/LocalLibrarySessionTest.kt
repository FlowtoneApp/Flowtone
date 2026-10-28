package ink.tenqui.flowtone.viewmodel

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibrarySessionTest {
    @Test
    fun permissionGatesScanningAndManualRefreshPreservesPlaybackIntent() = runBlocking {
        withTimeout(5_000) {
            val calls = AtomicInteger()
            val intents = mutableListOf<Boolean>()
            val session = LocalLibrarySession(
                scope = this,
                loadSongs = { calls.incrementAndGet(); emptyList() },
                onSongsDiscovered = { _, preserve -> intents += preserve },
                onScanCompleted = { _, _, preserve -> intents += preserve }
            )
            session.scanSongs()
            assertFalse(session.refresh())
            assertEquals(0, calls.get())

            session.setPermissionStatus(true)
            assertTrue(session.refresh())
            assertEquals(listOf(true, true), intents)
            assertTrue(session.state.value.hasScanned)
            assertFalse(session.state.value.isLoading)
        }
    }

    @Test
    fun callbacksKeepDiscoveryBeforePublicationAndIndexWorkAfterPublication() = runBlocking {
        withTimeout(5_000) {
            val completed = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            lateinit var session: LocalLibrarySession
            session = LocalLibrarySession(
                scope = this,
                loadSongs = { emptyList() },
                onSongsDiscovered = { _, preserve ->
                    assertFalse(preserve)
                    assertTrue(session.state.value.isLoading)
                    assertFalse(session.state.value.hasScanned)
                    events += "discovered"
                },
                onScanCompleted = { _, _, preserve ->
                    assertFalse(preserve)
                    assertFalse(session.state.value.isLoading)
                    assertTrue(session.state.value.hasScanned)
                    events += "completed"
                    completed.complete(Unit)
                }
            )
            session.setPermissionStatus(true)
            session.scanSongs()
            completed.await()
            assertEquals(listOf("discovered", "completed"), events)
        }
    }

    @Test
    fun nextScanWaitsForPreviousCompletionWorkEvenAfterLoadingFlagClears() = runBlocking {
        withTimeout(5_000) {
            val calls = AtomicInteger()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var completions = 0
            val session = LocalLibrarySession(
                scope = this,
                loadSongs = { calls.incrementAndGet(); emptyList() },
                onSongsDiscovered = { _, _ -> },
                onScanCompleted = { _, _, _ ->
                    completions++
                    if (completions == 1) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            )
            session.setPermissionStatus(true)
            session.scanSongs()
            entered.await()
            assertFalse(session.state.value.isLoading)

            val refresh = async(start = CoroutineStart.UNDISPATCHED) { session.refresh() }
            assertEquals(1, calls.get())
            assertFalse(refresh.isCompleted)
            release.complete(Unit)

            assertTrue(refresh.await())
            assertEquals(2, calls.get())
            assertEquals(2, completions)
        }
    }

    @Test
    fun failedScanKeepsPreviousCatalogAndDoesNotRunCompletionCallbacks() = runBlocking {
        withTimeout(5_000) {
            var fail = false
            var completions = 0
            val session = LocalLibrarySession(
                scope = this,
                loadSongs = { if (fail) error("scan failed") else emptyList() },
                onSongsDiscovered = { _, _ -> },
                onScanCompleted = { _, _, _ -> completions++ }
            )
            session.setPermissionStatus(true)
            assertTrue(session.refresh())
            val before = session.state.value
            fail = true

            assertFalse(session.refresh())
            assertEquals(before.songs, session.state.value.songs)
            assertEquals(before.albums, session.state.value.albums)
            assertEquals("scan failed", session.state.value.errorMessage)
            assertTrue(session.state.value.hasScanned)
            assertFalse(session.state.value.isLoading)
            assertEquals(1, completions)
        }
    }
}
