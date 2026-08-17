package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAvatarPersistentCacheTest {
    @Test
    fun successfulResultSurvivesNewCacheInstance() {
        val root = temporaryRoot()
        ArtistAvatarPersistentCache(root, silentLogger()).put(
            "test.extension.a", " Song ", " Artist ", avatar("test.extension.a", "https://image.invalid/a.png")
        )

        val restored = ArtistAvatarPersistentCache(root, silentLogger())
            .get("test.extension.a", "song", "ARTIST")

        assertEquals("test.extension.a", restored?.image?.extensionId)
        assertEquals("https://image.invalid/a.png", restored?.image?.url)
    }

    @Test
    fun namespacesAreIsolatedAndClearOnlyRemovesRequestedExtension() {
        val root = temporaryRoot()
        val cache = ArtistAvatarPersistentCache(root, silentLogger())
        cache.put("test.extension.a", "song", "artist", avatar("test.extension.a", "https://image.invalid/a.png"))
        cache.put("test.extension.b", "song", "artist", avatar("test.extension.b", "https://image.invalid/b.png"))

        cache.clear("test.extension.a")

        assertNull(ArtistAvatarPersistentCache(root, silentLogger()).get("test.extension.a", "song", "artist"))
        assertEquals(
            "https://image.invalid/b.png",
            ArtistAvatarPersistentCache(root, silentLogger()).get("test.extension.b", "song", "artist")?.image?.url
        )
    }

    @Test
    fun corruptedCacheIsDiscardedAndCanBeUsedAgain() {
        val root = temporaryRoot()
        val file = root.resolve("test.extension.a").resolve("artist-avatar-results").resolve("entries.json")
        requireNotNull(file.parentFile).mkdirs()
        file.writeText("{not-json")

        val cache = ArtistAvatarPersistentCache(root, silentLogger())
        assertNull(cache.get("test.extension.a", "song", "artist"))
        cache.put("test.extension.a", "song", "artist", avatar("test.extension.a", "https://image.invalid/a.png"))

        assertTrue(file.isFile)
        assertEquals("https://image.invalid/a.png", cache.get("test.extension.a", "song", "artist")?.image?.url)
    }

    private fun avatar(extensionId: String, url: String) = ArtistAvatar(ExtensionImage(extensionId, url))

    private fun temporaryRoot() = Files.createTempDirectory("artist-avatar-persistent-cache").toFile()

    private fun silentLogger() = ExtensionCoreLogger { _, _ -> }
}
