package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMetadataPersistentCacheBannerTest {
    @Test
    fun oldRecordWithoutBannerRemainsReadable() {
        val root = Files.createTempDirectory("artist-metadata-old").toFile()
        try {
            val directory = root.resolve("provider").resolve("artist-metadata-results").apply { mkdirs() }
            val key = ArtistMetadataPersistentCache.cacheKey("provider", "Artist")
            directory.resolve("entries.json").writeText(
                """{"format":2,"extensionId":"provider","entries":[{"cacheKey":${jsonString(key)},"aliases":[],"biography":"Bio","songCount":1,"albumCount":2}]}"""
            )

            val metadata = ArtistMetadataPersistentCache(root).get("provider", "Artist")

            assertEquals("Bio", metadata?.biography)
            assertNull(metadata?.banner)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun bannerReferenceRoundTripsWithoutImageBytes() {
        val root = Files.createTempDirectory("artist-metadata-banner").toFile()
        try {
            val banner = ExtensionImage("provider", "https://example.com/banner.jpg")
            ArtistMetadataPersistentCache(root).put(
                "provider",
                "Artist",
                ArtistMetadata(biography = "Bio", banner = banner)
            )

            val restored = ArtistMetadataPersistentCache(root).get("provider", "Artist")
            val json = root.resolve("provider/artist-metadata-results/entries.json").readText()

            assertEquals(banner, restored?.banner)
            assertTrue(json.contains("https://example.com/banner.jpg"))
            assertFalse(json.contains("imageBytes"))
            assertFalse(json.contains("base64", ignoreCase = true))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun jsonString(value: String): String = org.json.JSONObject.quote(value)
}
