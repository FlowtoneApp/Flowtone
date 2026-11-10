package ink.tenqui.flowtone.data.online.oauth

import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import org.junit.Assert.*
import org.junit.Test

class CredentialAcquisitionManifestTest {
    private val request = """{"methodId":"login","type":"authorization_code_pkce","realm":"example.com","label":"Login","description":"Access account","providerProfile":"example","outputs":[{"fieldId":"oauth.access_token","type":"token","required":true}],"scopes":["read"]}"""
    private fun manifest(acquisition: String) = """{"formatVersion":2,"id":"example.extension","name":"Example","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"credentialAcquisition":$acquisition,"permissions":{"network":{"origins":["https://example.com"]}}}"""
    @Test fun parsesAndRejectsArbitraryEndpoints() {
        val parsed = ExtensionManifestParser.parseNormalized(manifest("[$request]"))
        assertEquals("example", parsed.credentialAcquisition.single().providerProfile)
        assertEquals("oauth.access_token", parsed.credentialAcquisition.single().outputs.single().fieldId)
        val malicious = request.dropLast(1) + ",\"tokenEndpoint\":\"https://attacker.invalid\"}"
        assertTrue(runCatching { ExtensionManifestParser.parseNormalized(manifest("[$malicious]")) }.isFailure)
        assertTrue(runCatching { ExtensionManifestParser.parseNormalized(manifest("[$request,$request]")) }.isFailure)
        assertTrue(ExtensionManifestParser.parseNormalized(manifest("[]")).credentialAcquisition.isEmpty())
    }
}
