package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ArtistAvatarExtension
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import ink.tenqui.flowtone.data.online.runtime.ExtensionResultCache
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAvatarExtensionRegistryTest {
    @Test
    fun identicalConcurrentLookupsShareOneProviderInvocation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val extension = object : ArtistAvatarExtension {
            override val id = "test.extension.a"
            override val displayName = "Test"
            override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                return ArtistAvatar(ExtensionImage(id, "https://example.com/avatar.jpg"))
            }
        }
        val registry = testRegistry().apply { install(extension) }

        val results = List(3) { async { registry.findArtistAvatar(" Song ", " Artist ") } }
        started.await()
        assertEquals(1, calls.get())
        release.complete(Unit)

        assertTrue(results.awaitAll().all { it?.image?.url == "https://example.com/avatar.jpg" })
        assertEquals(1, calls.get())
    }

    @Test
    fun failedInvocationIsRemovedFromInFlight() = runBlocking {
        val calls = AtomicInteger()
        val extension = object : ArtistAvatarExtension {
            override val id = "test.extension.a"
            override val displayName = "Test"
            override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
                if (calls.incrementAndGet() == 1) error("first call fails")
                return ArtistAvatar(ExtensionImage(id, "https://example.com/avatar.jpg"))
            }
        }
        val registry = testRegistry().apply { install(extension) }

        assertNull(registry.findArtistAvatar("Song", "Artist"))
        assertEquals("https://example.com/avatar.jpg", registry.findArtistAvatar("Song", "Artist")?.image?.url)
        assertEquals(2, calls.get())
    }

    @Test
    fun oneLookupDoesNotRetryFailedProvider() = runBlocking {
        val calls = AtomicInteger()
        val extension = object : ArtistAvatarExtension {
            override val id = "test.extension.a"
            override val displayName = "Test"
            override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
                calls.incrementAndGet()
                return null
            }
        }

        assertNull(testRegistry().apply { install(extension) }
            .findArtistAvatar("Song", "Artist"))
        assertEquals(1, calls.get())
    }

    @Test
    fun persistentHitSkipsProviderAfterRegistryIsRecreated() = runBlocking {
        val root = Files.createTempDirectory("artist-avatar-results").toFile()
        val calls = AtomicInteger()
        val extension = object : ArtistAvatarExtension {
            override val id = "test.extension.a"
            override val displayName = "Test"
            override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
                calls.incrementAndGet()
                return ArtistAvatar(ExtensionImage(id, "https://image.invalid/avatar-a.png"))
            }
        }

        registryWithPersistentCache(root).apply { install(extension) }
            .findArtistAvatar("Song", "Artist")
        assertEquals(1, calls.get())

        val restored = registryWithPersistentCache(root).apply { install(extension) }
            .findArtistAvatar(" Song ", " ARTIST ")
        assertEquals("https://image.invalid/avatar-a.png", restored?.image?.url)
        assertEquals(1, calls.get())
    }

    @Test
    fun nullAndFailedResultsAreNotPersisted() = runBlocking {
        val root = Files.createTempDirectory("artist-avatar-failures").toFile()
        val nullCalls = AtomicInteger()
        val nullExtension = object : ArtistAvatarExtension {
            override val id = "test.extension.null"
            override val displayName = "Test"
            override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
                nullCalls.incrementAndGet()
                return null
            }
        }
        registryWithPersistentCache(root).apply { install(nullExtension) }
            .findArtistAvatar("Song", "Artist")
        registryWithPersistentCache(root).apply { install(nullExtension) }
            .findArtistAvatar("Song", "Artist")
        assertEquals(2, nullCalls.get())

        val failedCalls = AtomicInteger()
        val failedExtension = object : ArtistAvatarExtension {
            override val id = "test.extension.failed"
            override val displayName = "Test"
            override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
                failedCalls.incrementAndGet()
                error("unavailable")
            }
        }
        registryWithPersistentCache(root).apply { install(failedExtension) }
            .findArtistAvatar("Song", "Artist")
        registryWithPersistentCache(root).apply { install(failedExtension) }
            .findArtistAvatar("Song", "Artist")
        assertEquals(2, failedCalls.get())
    }

    private fun testRegistry() = ArtistAvatarExtensionRegistry(
        logger = ExtensionCoreLogger { _, _ -> }
    )

    private fun registryWithPersistentCache(root: java.io.File) = ArtistAvatarExtensionRegistry(
        logger = ExtensionCoreLogger { _, _ -> },
        resultCache = ExtensionResultCache(),
        persistentCache = ArtistAvatarPersistentCache(root, ExtensionCoreLogger { _, _ -> })
    )
}
