package ink.tenqui.flowtone.data.online.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderArtistMetadataTransportTest {
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
