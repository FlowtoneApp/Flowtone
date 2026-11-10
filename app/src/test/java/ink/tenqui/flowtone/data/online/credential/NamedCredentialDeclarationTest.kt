package ink.tenqui.flowtone.data.online.credential

import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.ui.screens.extensionCredentialContractDescription
import org.junit.Assert.*
import org.junit.Test

class NamedCredentialDeclarationTest {
    private fun manifest(fields: String = validFields, origin: String = "https://API.Example.com:443", realm: String = "example.com") =
        """{"formatVersion":2,"id":"example.extension","name":"Example","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"credentialRequests":[{"id":"session","type":"generic_account","label":"Session","realm":"$realm","description":"同步账户","origin":"$origin","namedFields":$fields}],"permissions":{"network":{"origins":["https://api.example.com"]}}}"""
    private fun parse(fields: String = validFields, origin: String = "https://API.Example.com:443") =
        ExtensionManifestParser.parseNormalized(manifest(fields, origin)).credentialRequests.single()

    @Test fun parsesNormalizesAndSummarizesMultipleFields() {
        val request = parse()
        assertEquals("https://api.example.com", request.named!!.origin)
        assertEquals(2, request.named!!.fields.size)
        assertEquals(request.named, CredentialRequestContractSnapshot.from(request)!!.named)
        val summary = extensionCredentialContractDescription(request)
        assertTrue(summary.contains("cookie.session_key"))
        assertTrue(summary.contains("Bearer"))
        assertTrue(summary.contains("https://api.example.com"))
        assertTrue(summary.contains("必需"))
        assertTrue(summary.contains("可选"))
    }

    @Test fun rejectsInvalidMappingsAndNames() {
        val fields = listOf(
            """[{"id":"cookie.x","type":"token","required":true,"injection":"cookie","cookieName":"x"}]""",
            """[{"id":"oauth.x","type":"opaque","required":true,"injection":"bearer"}]""",
            """[{"id":"../x","type":"token","required":true,"injection":"bearer"}]""",
            """[{"id":"cookie.x","type":"cookie","required":true,"injection":"cookie","cookieName":"bad name"}]""",
            """[{"id":"cookie.x","type":"cookie","required":true,"injection":"cookie","cookieName":"x"},{"id":"cookie.x","type":"cookie","required":false,"injection":"cookie","cookieName":"y"}]"""
        )
        fields.forEach { assertTrue(it, runCatching { parse(it) }.isFailure) }
    }

    @Test fun originsAndContractsAreExact() {
        for (origin in listOf("http://api.example.com", "https://*.example.com", "https://u@api.example.com", "https://api.example.com/path", "https://api.example.com?x=1", "https://api.example.com#x", "https://api.example.com:99999")) {
            assertTrue(origin, runCatching { parse(origin = origin) }.isFailure)
        }
        val base = parse()
        assertNotEquals(base.contract, parse(origin = "https://api.example.com:8443").contract)
        assertNotEquals(base.contract, parse(origin = "https://other.example.com").contract)
        val changed = validFields.replace("\"bearer\"", "\"cookie\"").replace("\"token\"", "\"cookie\"").replace("\"injection\":\"cookie\"}", "\"injection\":\"cookie\",\"cookieName\":\"other\"}")
        assertNotEquals(base.contract, parse(changed).contract)
        assertTrue(runCatching { ExtensionManifestParser.parseNormalized(manifest().replace("\"origin\":\"https://API.Example.com:443\",", "")) }.isFailure)
    }

    @Test fun declarationsShareFieldIdentityAndInvalidInputDoesNotTouchStoredData() {
        val root = java.nio.file.Files.createTempDirectory("declaration-data").toFile()
        val existing = root.resolve("existing.secret").apply { writeText("existing-ciphertext") }
        val one = ExtensionManifestParser.parseNormalized(manifest())
        val two = ExtensionManifestParser.parseNormalized(manifest().replace("example.extension", "another.extension"))
        assertEquals(one.credentialRequests.single().named, two.credentialRequests.single().named)
        assertTrue(runCatching { parse(origin = "http://api.example.com") }.isFailure)
        assertEquals("existing-ciphertext", existing.readText())
        assertEquals(listOf(existing), root.listFiles()!!.toList())
    }

    companion object {
        const val validFields = """[{"id":"cookie.session_key","type":"cookie","required":true,"injection":"cookie","cookieName":"sid"},{"id":"oauth.access_token","type":"token","required":false,"injection":"bearer"}]"""
    }
}
