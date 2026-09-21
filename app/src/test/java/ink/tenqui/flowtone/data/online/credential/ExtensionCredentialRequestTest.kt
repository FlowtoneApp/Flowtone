package ink.tenqui.flowtone.data.online.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCredentialRequestTest {
    @Test
    fun v1CredentialTypesAndWebDavContractAreHostDefined() {
        assertEquals(
            listOf(CredentialType.WebDav, CredentialType.AccountPassword),
            CredentialType.entries
        )
        assertEquals("webdav", CredentialType.WebDav.value)
        val contract = CredentialRequestDefinition("webdav", CredentialType.WebDav, "WebDAV").contract
        assertEquals(
            listOf(CredentialFieldId.Endpoint, CredentialFieldId.Username, CredentialFieldId.Password),
            contract.fields.map { it.id }
        )
        assertEquals(contract.fields.map { it.id }.toSet(), contract.requiredFieldIds)
        assertEquals(0, contract.minimumIdentifierCount)
        assertTrue(CredentialFieldDefinitions.Password.isSensitive)
    }

    @Test
    fun duplicateRequestIdsAreRejected() {
        val requests = listOf(
            CredentialRequestDefinition("account", CredentialType.WebDav, "主账号"),
            CredentialRequestDefinition("account", CredentialType.WebDav, "备用账号")
        )

        assertTrue(CredentialRequestValidator.validate(requests).any { it.reason.contains("重复") })
    }

    @Test
    fun accountPasswordAcceptsHostDefinedIdentifierCombinations() {
        val requests = listOf(
            CredentialRequestDefinition(
                "username", CredentialType.AccountPassword, "Account",
                identifiers = listOf(CredentialIdentifierType.Username)
            ),
            CredentialRequestDefinition(
                "email", CredentialType.AccountPassword, "Account",
                identifiers = listOf(CredentialIdentifierType.Username, CredentialIdentifierType.Email)
            ),
            CredentialRequestDefinition(
                "phone", CredentialType.AccountPassword, "Account",
                identifiers = listOf(CredentialIdentifierType.Phone, CredentialIdentifierType.UserId)
            )
        )

        assertTrue(CredentialRequestValidator.validate(requests).isEmpty())
        assertEquals(1, requests.first().contract.minimumIdentifierCount)
    }

    @Test
    fun accountPasswordRequiresNonRepeatedIdentifiers() {
        val empty = CredentialRequestDefinition("empty", CredentialType.AccountPassword, "Account")
        val duplicate = CredentialRequestDefinition(
            "duplicate", CredentialType.AccountPassword, "Account",
            identifiers = listOf(CredentialIdentifierType.Email, CredentialIdentifierType.Email)
        )

        val violations = CredentialRequestValidator.validate(listOf(empty, duplicate))

        assertTrue(violations.any { it.path == "credentialRequests[0].identifiers" })
        assertTrue(violations.any { it.path == "credentialRequests[1].identifiers" })
    }

    @Test
    fun hostFieldsHaveStableUniqueIdsAndOnlyPasswordIsSecret() {
        val fields = CredentialFieldDefinitions.all

        assertEquals(fields.size, fields.map { it.id.value }.toSet().size)
        assertTrue(fields.all { it.label.isNotBlank() })
        assertTrue(CredentialFieldDefinitions.Password.isSensitive)
        assertTrue(fields.filterNot { it.id == CredentialFieldId.Password }.none { it.isSensitive })
    }
}
