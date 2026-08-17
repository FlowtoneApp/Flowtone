package ink.tenqui.flowtone.data.online.runtime

import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionPrivateCacheTest {
    @Test
    fun namespacesArePrivatePerExtension() = withCache { cache ->
        cache.set("test.extension.a", "foo", "value-a")
        cache.set("test.extension.b", "foo", "value-b")

        assertEquals("value-a", cache.get("test.extension.a", "foo"))
        assertEquals("value-b", cache.get("test.extension.b", "foo"))
    }

    @Test
    fun valuesSurviveNewCacheHost() {
        val root = Files.createTempDirectory("flowtone-cache-persist").toFile()
        try {
            newCache(root).set("test.extension.a", "foo", "bar")
            assertEquals("bar", newCache(root).get("test.extension.a", "foo"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheHitUpdatesMemoryLruWithoutPersistingWholeFile() = withCache { cache ->
        cache.set("test.extension.a", "a", "1")
        val persistedBeforeHit = cache.persistCountForTests
        val accessBeforeHit = cache.lastAccessForTests("test.extension.a", "a")

        assertEquals("1", cache.get("test.extension.a", "a"))

        assertEquals(persistedBeforeHit, cache.persistCountForTests)
        checkNotNull(accessBeforeHit)
        checkNotNull(cache.lastAccessForTests("test.extension.a", "a"))
        org.junit.Assert.assertTrue(cache.lastAccessForTests("test.extension.a", "a")!! > accessBeforeHit)
    }

    @Test
    fun leastRecentlyUsedEntryIsEvictedFirst() = withCache(quotaBytes = 14) { cache ->
        cache.set("test.extension.a", "a", "1111")
        cache.set("test.extension.a", "b", "2222")
        assertEquals("1111", cache.get("test.extension.a", "a"))
        cache.set("test.extension.a", "c", "3333")

        assertEquals("1111", cache.get("test.extension.a", "a"))
        assertNull(cache.get("test.extension.a", "b"))
        assertEquals("3333", cache.get("test.extension.a", "c"))
    }

    @Test
    fun nextSetPersistsLatestInMemoryLruOrder() {
        val root = Files.createTempDirectory("flowtone-cache-lru-persist").toFile()
        try {
            val cache = newCache(root, quotaBytes = 14)
            cache.set("test.extension.a", "a", "1111")
            cache.set("test.extension.a", "b", "2222")
            cache.get("test.extension.a", "a")
            cache.set("test.extension.a", "c", "3333")

            val restored = newCache(root, quotaBytes = 14)
            assertEquals("1111", restored.get("test.extension.a", "a"))
            assertNull(restored.get("test.extension.a", "b"))
            assertEquals("3333", restored.get("test.extension.a", "c"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun accessOnlyMayLoseLruOrderAfterRestart() {
        val root = Files.createTempDirectory("flowtone-cache-access-best-effort").toFile()
        try {
            val cache = newCache(root, quotaBytes = 14)
            cache.set("test.extension.a", "a", "1111")
            cache.set("test.extension.a", "b", "2222")
            cache.get("test.extension.a", "a")

            val restarted = newCache(root, quotaBytes = 14)
            restarted.set("test.extension.a", "c", "3333")

            assertNull(restarted.get("test.extension.a", "a"))
            assertEquals("2222", restarted.get("test.extension.a", "b"))
            assertEquals("3333", restarted.get("test.extension.a", "c"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptedJsonFallsBackToEmptyNamespace() {
        val root = Files.createTempDirectory("flowtone-cache-corrupted").toFile()
        try {
            val file = root.resolve("test.extension.a/cache/entries.json")
            file.parentFile?.mkdirs()
            file.writeText("{not json")

            val cache = newCache(root)
            assertNull(cache.get("test.extension.a", "foo"))
            cache.set("test.extension.a", "foo", "bar")
            assertEquals("bar", newCache(root).get("test.extension.a", "foo"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun explicitUninstallDeletesPersistentNamespace() {
        val root = Files.createTempDirectory("flowtone-cache-uninstall").toFile()
        try {
            newCache(root).apply {
                set("test.extension.a", "foo", "bar")
                deleteForUninstall("test.extension.a")
            }
            assertNull(newCache(root).get("test.extension.a", "foo"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withCache(quotaBytes: Int = 5 * 1024 * 1024, block: (ExtensionPrivateCache) -> Unit) {
        val root = Files.createTempDirectory("flowtone-cache-test").toFile()
        try {
            block(newCache(root, quotaBytes))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun newCache(root: java.io.File, quotaBytes: Int = 5 * 1024 * 1024) = ExtensionPrivateCache(
        root = root,
        quotaBytes = quotaBytes,
        logger = ExtensionCoreLogger { _, _ -> }
    )
}
