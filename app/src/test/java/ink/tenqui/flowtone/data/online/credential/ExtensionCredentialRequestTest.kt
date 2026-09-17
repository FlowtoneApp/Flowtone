package ink.tenqui.flowtone.data.online.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCredentialRequestTest {
    @Test
    fun webDavIsTheOnlyV1CredentialType() {
        assertEquals(listOf(CredentialType.WebDav), CredentialType.entries)
        assertEquals("webdav", CredentialType.WebDav.value)
    }

    @Test
    fun duplicateRequestIdsAreRejected() {
        val requests = listOf(
            CredentialRequestDefinition("account", CredentialType.WebDav, "主账号"),
            CredentialRequestDefinition("account", CredentialType.WebDav, "备用账号")
        )

        assertTrue(CredentialRequestValidator.validate(requests).any { it.reason.contains("重复") })
    }
}
