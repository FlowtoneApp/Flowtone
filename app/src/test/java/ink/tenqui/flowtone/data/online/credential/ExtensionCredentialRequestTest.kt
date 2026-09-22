package ink.tenqui.flowtone.data.online.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCredentialRequestTest {
    @Test
    fun v1CredentialTypesAndWebDavContractAreHostDefined() {
        assertEquals(
            listOf(
                CredentialType.WebDav,
                CredentialType.AccountPassword,
                CredentialType.GenericAccount
            ),
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
    fun hostFieldsHaveStableUniqueIdsAndGenericAccountSensitivity() {
        val fields = CredentialFieldDefinitions.all

        assertEquals(fields.size, fields.map { it.id.value }.toSet().size)
        assertTrue(fields.all { it.label.isNotBlank() })
        assertTrue(!CredentialFieldDefinitions.Account.isSensitive)
        assertTrue(CredentialFieldDefinitions.Password.isSensitive)
        assertTrue(CredentialFieldDefinitions.Cookie.isSensitive)
        assertTrue(
            fields.filterNot {
                it.id == CredentialFieldId.Password || it.id == CredentialFieldId.Cookie
            }.none { it.isSensitive }
        )
    }

    @Test
    fun genericAccountUsesOnlyItsHostDefinedFields() {
        val request = CredentialRequestDefinition(
            id = "account",
            credentialType = CredentialType.GenericAccount,
            label = "Account",
            realm = "music.163.com",
            genericFieldIds = listOf(
                CredentialFieldId.Cookie,
                CredentialFieldId.Account,
                CredentialFieldId.Password
            )
        )

        assertTrue(CredentialRequestValidator.validate(listOf(request)).isEmpty())
        assertEquals("通用账户凭证", CredentialType.GenericAccount.label)
        assertEquals(
            listOf(CredentialFieldId.Account, CredentialFieldId.Password, CredentialFieldId.Cookie),
            request.contract.fields.map { it.id }
        )
    }

    @Test
    fun genericAccountRejectsInvalidRealmAndFields() {
        val invalidRequests = listOf(
            CredentialRequestDefinition(
                "missingRealm", CredentialType.GenericAccount, "Account",
                genericFieldIds = listOf(CredentialFieldId.Account)
            ),
            CredentialRequestDefinition(
                "pathRealm", CredentialType.GenericAccount, "Account",
                realm = "music.163.com/path",
                genericFieldIds = listOf(CredentialFieldId.Account)
            ),
            CredentialRequestDefinition(
                "emptyFields", CredentialType.GenericAccount, "Account",
                realm = "music.163.com"
            ),
            CredentialRequestDefinition(
                "duplicateFields", CredentialType.GenericAccount, "Account",
                realm = "music.163.com",
                genericFieldIds = listOf(CredentialFieldId.Account, CredentialFieldId.Account)
            ),
            CredentialRequestDefinition(
                "foreignField", CredentialType.GenericAccount, "Account",
                realm = "music.163.com",
                genericFieldIds = listOf(CredentialFieldId.Username)
            )
        )

        val violations = CredentialRequestValidator.validate(invalidRequests)

        assertTrue(violations.any { it.path == "credentialRequests[0].realm" })
        assertTrue(violations.any { it.path == "credentialRequests[1].realm" })
        assertTrue(violations.any { it.path == "credentialRequests[2].fields" })
        assertTrue(violations.any { it.path == "credentialRequests[3].fields" })
        assertTrue(violations.any { it.path == "credentialRequests[4].fields" })
    }
}
