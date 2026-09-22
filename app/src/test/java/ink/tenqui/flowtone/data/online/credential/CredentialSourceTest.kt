package ink.tenqui.flowtone.data.online.credential

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ink.tenqui.flowtone.ui.screens.credentialSourcePublicFieldErrorKey
import ink.tenqui.flowtone.ui.screens.credentialSourceSummary

class CredentialSourceTest {
    @Test
    fun sourceValidationAllowsOnlyHostOwnedPublicFields() {
        val webDav = source(CredentialType.WebDav, fields = mapOf(
            CredentialFieldId.Endpoint to "https://nas.example.com",
            CredentialFieldId.Username to "tenqui"
        ))
        val accountPassword = source(CredentialType.AccountPassword, fields = mapOf(
            CredentialFieldId.Email to "user@example.com"
        ))

        assertTrue(CredentialSourceValidator.validate(webDav).isEmpty())
        assertTrue(CredentialSourceValidator.validate(accountPassword).isEmpty())
        assertTrue(CredentialSourceValidator.validate(
            webDav.copy(publicFields = webDav.publicFields + (CredentialFieldId.Account to "wrong"))
        ).isNotEmpty())
        assertTrue(CredentialSourceValidator.validate(
            accountPassword.copy(publicFields = mapOf(CredentialFieldId.Endpoint to "https://wrong.example"))
        ).isNotEmpty())
    }

    @Test
    fun webDavEndpointIsRequiredAndMustBeHttpOrHttpsUrl() {
        val emptyEndpoint = CredentialSourceInput(
            credentialType = CredentialType.WebDav,
            label = "NAS",
            publicFields = mapOf(CredentialFieldId.Username to "tenqui")
        )
        val invalidEndpoint = emptyEndpoint.copy(
            publicFields = emptyEndpoint.publicFields + (CredentialFieldId.Endpoint to "nas.example.com")
        )
        val validEndpoint = invalidEndpoint.copy(
            publicFields = invalidEndpoint.publicFields + (CredentialFieldId.Endpoint to "https://nas.example.com/dav")
        )

        assertEquals(
            "请输入 WebDAV 地址",
            CredentialSourceValidator.validateInput(emptyEndpoint)
                .single { it.field == "publicFields.endpoint" }
                .reason
        )
        assertEquals(
            "请输入有效的 HTTP 或 HTTPS 地址",
            CredentialSourceValidator.validateInput(invalidEndpoint)
                .single { it.field == "publicFields.endpoint" }
                .reason
        )
        assertTrue(CredentialSourceValidator.validateInput(validEndpoint).isEmpty())
        assertEquals(
            "publicFields.endpoint",
            credentialSourcePublicFieldErrorKey(CredentialFieldId.Endpoint)
        )

        val root = Files.createTempDirectory("credential-sources-invalid")
        val result = runCatching {
            CredentialSourceStore(root.toFile()).create(invalidEndpoint)
        }
        assertTrue(result.isFailure)
        assertTrue(CredentialSourceStore(root.toFile()).list().isEmpty())
    }

    @Test
    fun genericAccountRequiresRealmAndRejectsStructuredIdentifiers() {
        val missingRealm = source(CredentialType.GenericAccount, fields = mapOf(CredentialFieldId.Account to "user"))
        val foreign = source(
            CredentialType.GenericAccount,
            realm = "music.163.com",
            fields = mapOf(CredentialFieldId.Username to "user")
        )

        assertTrue(CredentialSourceValidator.validate(missingRealm).any { it.field == "realm" })
        assertTrue(CredentialSourceValidator.validate(foreign).any { it.field == "publicFields.username" })
    }

    @Test
    fun storeCreatesUpdatesReadsAndDeletesOnlyPublicMetadata() {
        val root = Files.createTempDirectory("credential-sources").toFile()
        val store = CredentialSourceStore(root, nextId = { "cs_test-source" })
        val created = store.create(
            CredentialSourceInput(
                credentialType = CredentialType.WebDav,
                label = "My NAS",
                publicFields = mapOf(
                    CredentialFieldId.Endpoint to "https://nas.example.com",
                    CredentialFieldId.Username to "tenqui"
                )
            )
        )

        assertEquals(created, store.get(created.id))
        assertEquals(listOf(created), store.list())
        assertEquals(
            created.id,
            store.update(
                created.id,
                CredentialSourceInput(
                    credentialType = CredentialType.WebDav,
                    label = "Renamed NAS",
                    publicFields = created.publicFields
                )
            ).id
        )
        assertFalse(root.resolve("sources.json").readText().contains("password", ignoreCase = true))
        assertFalse(root.resolve("sources.json").readText().contains("cookie", ignoreCase = true))
        assertTrue(store.delete(created.id))
        assertNull(store.get(created.id))
    }

    @Test
    fun storeSurvivesRestartAndMalformedData() {
        val root = Files.createTempDirectory("credential-sources").toFile()
        val store = CredentialSourceStore(root, nextId = { "cs_restart-source" })
        val created = store.create(
            CredentialSourceInput(
                credentialType = CredentialType.GenericAccount,
                label = "Music",
                realm = " MUSIC.163.COM ",
                publicFields = mapOf(CredentialFieldId.Account to "tenqui")
            )
        )

        assertEquals(created, CredentialSourceStore(root).get(created.id))
        root.resolve("sources.json").writeText("not json")
        assertTrue(CredentialSourceStore(root).list().isEmpty())
    }

    @Test
    fun updateCannotChangeSourceType() {
        val root = Files.createTempDirectory("credential-sources").toFile()
        val store = CredentialSourceStore(root, nextId = { "cs_type-source" })
        val created = store.create(CredentialSourceInput(CredentialType.AccountPassword, "Account"))

        val result = runCatching {
            store.update(created.id, CredentialSourceInput(CredentialType.WebDav, "Account"))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun storeGeneratesUniqueIds() {
        val root = Files.createTempDirectory("credential-sources").toFile()
        val ids = listOf("cs_first-source", "cs_second-source").iterator()
        val store = CredentialSourceStore(root, nextId = { ids.next() })

        val first = store.create(CredentialSourceInput(CredentialType.AccountPassword, "First"))
        val second = store.create(CredentialSourceInput(CredentialType.AccountPassword, "Second"))

        assertFalse(first.id == second.id)
    }

    @Test
    fun malformedUnknownFieldIsIgnoredAsMalformedSource() {
        val root = Files.createTempDirectory("credential-sources").toFile()
        root.resolve("sources.json").writeText(
            """{"format":1,"sources":[{"id":"cs_bad-source","type":"webdav","label":"Bad","publicFields":{"unknown":"value"}}]}"""
        )

        assertTrue(CredentialSourceStore(root).list().isEmpty())
    }

    @Test
    fun listPresentationDoesNotExposeSecretValues() {
        val source = source(
            CredentialType.GenericAccount,
            realm = "music.163.com",
            fields = mapOf(CredentialFieldId.Account to "tenqui")
        )

        val summary = credentialSourceSummary(source)

        assertTrue(summary.contains("music.163.com"))
        assertTrue(summary.contains("tenqui"))
        assertFalse(summary.contains("password", ignoreCase = true))
        assertTrue(source.secretFieldStates.values.all { it == CredentialSecretState.Unavailable })
    }

    @Test
    fun matcherSeparatesContractMetadataAndSecretReadiness() {
        val webDav = source(CredentialType.WebDav, fields = mapOf(
            CredentialFieldId.Endpoint to "https://nas.example.com",
            CredentialFieldId.Username to "tenqui"
        ))
        val webDavRequest = CredentialRequestDefinition("dav", CredentialType.WebDav, "DAV")
        val account = source(CredentialType.AccountPassword, fields = mapOf(CredentialFieldId.Email to "a@b.com"))
        val accountRequest = CredentialRequestDefinition(
            "account", CredentialType.AccountPassword, "Account",
            identifiers = listOf(CredentialIdentifierType.Email)
        )
        val generic = source(
            CredentialType.GenericAccount,
            realm = "music.163.com",
            fields = mapOf(CredentialFieldId.Account to "tenqui")
        )
        val genericRequest = CredentialRequestDefinition(
            "generic", CredentialType.GenericAccount, "Generic",
            realm = "music.163.com",
            genericFieldIds = listOf(CredentialFieldId.Account, CredentialFieldId.Cookie)
        )

        assertTrue(CredentialSourceMatcher.match(webDav, webDavRequest).metadataCompatible)
        assertFalse(CredentialSourceMatcher.match(webDav, webDavRequest).secretReady)
        assertTrue(CredentialSourceMatcher.match(account, accountRequest).metadataCompatible)
        assertTrue(CredentialSourceMatcher.match(generic, genericRequest).contractCompatible)
        assertFalse(CredentialSourceMatcher.match(generic, genericRequest).secretReady)
        assertFalse(CredentialSourceMatcher.match(
            generic.copy(realm = "soundcloud.com"), genericRequest
        ).contractCompatible)
        assertFalse(CredentialSourceMatcher.match(generic, webDavRequest).contractCompatible)
    }

    private fun source(
        type: CredentialType,
        realm: String? = null,
        fields: Map<CredentialFieldId, String> = emptyMap()
    ) = CredentialSource(
        id = "cs_test-source",
        credentialType = type,
        label = "Source",
        realm = realm,
        publicFields = fields
    )
}
