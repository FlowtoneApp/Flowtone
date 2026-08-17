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

