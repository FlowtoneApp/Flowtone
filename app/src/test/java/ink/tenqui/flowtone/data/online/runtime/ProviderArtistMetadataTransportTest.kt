package ink.tenqui.flowtone.data.online.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderArtistMetadataTransportTest {
    @Test
    fun oldArtistPayloadWithoutSongOrderRemainsCompatible() {
        assertNull(providerArtistSongOrderFromJson(JSONObject()))
    }

    @Test
    fun artistSongOrderIsOptionalDescriptiveCollectionMetadata() {
        val order = providerArtistSongOrderFromJson(
            JSONObject().put(
                "artistSongOrder",
                JSONObject().put("id", " time ").put("title", " 时间排序 ")
            )
        )

        assertEquals("time", order?.id)
        assertEquals("时间排序", order?.title)
    }

    @Test
    fun malformedOrBlankArtistSongOrderFallsBackToMissing() {
        assertNull(
            providerArtistSongOrderFromJson(
                JSONObject().put("artistSongOrder", "time")
            )
        )
        assertNull(
            providerArtistSongOrderFromJson(
                JSONObject().put("artistSongOrder", JSONObject().put("title", "  "))
            )
        )
    }

    @Test
    fun legacyPayloadKeepsOptionalBannerNull() {
        assertNull(providerArtistMetadataFromJson(JSONObject().put("songCount", 1), "Artist")?.banner)
    }

    @Test
    fun providerBannerUsesExtensionBoundImageReference() {
        val metadata = providerArtistMetadataFromJson(
            JSONObject().put("bannerUrl", "https://example.com/banner.jpg"),
            "Artist",
            "provider.fixture"
        )
        assertEquals("provider.fixture", metadata?.banner?.extensionId)
        assertEquals("https://example.com/banner.jpg", metadata?.banner?.url)
    }
    @Test
    fun legacyProviderArtistPayloadWithoutProfileFieldsRemainsCompatible() {
        assertNull(providerArtistMetadataFromJson(JSONObject(), "Kou!"))
    }

    @Test
    fun profilePayloadKeepsMetadataAndExplicitZeroCounts() {
        val metadata = providerArtistMetadataFromJson(
            JSONObject()
                .put("aliases", JSONArray().put(" Kou! ").put("Kou K"))
                .put("biography", " Biography ")
                .put("songCount", 0)
                .put("albumCount", 10),
            "Kou!"
        )

        assertEquals(listOf("Kou K"), metadata?.aliases)
        assertEquals("Biography", metadata?.biography)
        assertEquals(0, metadata?.songCount)
        assertEquals(10, metadata?.albumCount)
    }

    @Test
    fun negativeCountsBecomeUnknown() {
        val metadata = providerArtistMetadataFromJson(
            JSONObject().put("songCount", -1).put("albumCount", -2),
            "Kou!"
        )

        assertNull(metadata)
    }
}
