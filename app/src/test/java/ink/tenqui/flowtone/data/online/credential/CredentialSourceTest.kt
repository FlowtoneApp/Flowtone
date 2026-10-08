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
        assertTrue(source.secretFieldStates.isEmpty())
    }

    @Test
    fun matcherSeparatesContractMetadataAndSecretReadiness() {
        val webDav = source(CredentialType.WebDav, fields = mapOf(
            CredentialFieldId.Endpoint to "https://nas.example.com",
            CredentialFieldId.Username to "tenqui"
        )).copy(secretFieldStates = mapOf(CredentialFieldId.Password to CredentialSecretState.Configured))
        val webDavRequest = CredentialRequestDefinition("dav", CredentialType.WebDav, "DAV")
        val account = source(CredentialType.AccountPassword, fields = mapOf(CredentialFieldId.Email to "a@b.com"))
            .copy(secretFieldStates = mapOf(CredentialFieldId.Password to CredentialSecretState.Configured))
        val accountRequest = CredentialRequestDefinition(
            "account", CredentialType.AccountPassword, "Account",
            identifiers = listOf(CredentialIdentifierType.Email)
        )
        val generic = source(
            CredentialType.GenericAccount,
            realm = "music.163.com",
            fields = mapOf(CredentialFieldId.Account to "tenqui")
        ).copy(secretFieldStates = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Configured))
        val genericRequest = CredentialRequestDefinition(
            "generic", CredentialType.GenericAccount, "Generic",
            realm = "music.163.com",
            genericFieldIds = listOf(CredentialFieldId.Account, CredentialFieldId.Cookie)
        )

        assertTrue(CredentialSourceMatcher.match(webDav, webDavRequest).metadataCompatible)
        assertTrue(CredentialSourceMatcher.match(webDav, webDavRequest).secretReady)
        assertTrue(CredentialSourceMatcher.match(account, accountRequest).metadataCompatible)
        assertTrue(CredentialSourceMatcher.match(account, accountRequest).secretReady)
        assertTrue(CredentialSourceMatcher.match(generic, genericRequest).contractCompatible)
        assertTrue(CredentialSourceMatcher.match(generic, genericRequest).secretReady)
        assertFalse(CredentialSourceMatcher.match(
            generic.copy(realm = "soundcloud.com"), genericRequest
        ).contractCompatible)
        assertFalse(CredentialSourceMatcher.match(generic, webDavRequest).contractCompatible)
    }

    @Test
    fun matcherRequiresOnlySecretsRequestedByGenericContract() {
        val accountAndCookieRequest = CredentialRequestDefinition(
            "account-cookie", CredentialType.GenericAccount, "Generic",
            realm = "music.163.com",
            genericFieldIds = listOf(CredentialFieldId.Account, CredentialFieldId.Cookie)
        )
        val cookieOnlyRequest = accountAndCookieRequest.copy(
            id = "cookie-only",
            genericFieldIds = listOf(CredentialFieldId.Cookie)
        )
        val base = source(
            CredentialType.GenericAccount,
            realm = "music.163.com",
            fields = mapOf(CredentialFieldId.Account to "tenqui")
        ).copy(secretFieldStates = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Configured))

        assertTrue(CredentialSourceMatcher.match(base, accountAndCookieRequest).fullyReady)
        assertTrue(CredentialSourceMatcher.match(base, cookieOnlyRequest).fullyReady)
        assertFalse(CredentialSourceMatcher.match(
            base.copy(secretFieldStates = mapOf(CredentialFieldId.Password to CredentialSecretState.Configured)),
            cookieOnlyRequest
        ).secretReady)
    }

    @Test
    fun repositoryKeepReplaceDeleteAndSourceDeletionCoordinateStores() {
        val root = Files.createTempDirectory("credential-source-repository").toFile()
        val secretRoot = root.resolve("noBackupFilesDir/credential-secrets")
        val keyProvider = TestKeyProvider()
        val repository = CredentialSourceRepository(
            CredentialSourceStore(root.resolve("files/credential-sources"), nextId = { "cs_repository-source" }),
            CredentialSecretStore(secretRoot, keyProvider)
        )
        val input = CredentialSourceInput(CredentialType.AccountPassword, "Account")

        val created = repository.save(
            null,
            input,
            mapOf(CredentialFieldId.Password to CredentialSecretMutation.Replace("first-unit-secret"))
        )
        assertTrue(created.secretFieldStates[CredentialFieldId.Password] == CredentialSecretState.Configured)
        assertFalse(root.resolve("files/credential-sources/sources.json").readText().contains("first-unit-secret"))

        val kept = repository.save(
            created.id,
            input.copy(label = "Renamed"),
            mapOf(CredentialFieldId.Password to CredentialSecretMutation.Keep)
        )
        assertTrue(CredentialSecretStore(secretRoot, keyProvider)
            .get(created.id, CredentialFieldId.Password).hasPlaintext("first-unit-secret"))
        assertTrue(kept.secretFieldStates[CredentialFieldId.Password] == CredentialSecretState.Configured)
        assertTrue(CredentialSecretDraft().input.isEmpty())

        repository.save(
            created.id,
            input.copy(label = "Renamed"),
            mapOf(CredentialFieldId.Password to CredentialSecretMutation.Replace("second-unit-secret"))
        )
        assertTrue(CredentialSecretStore(secretRoot, keyProvider)
            .get(created.id, CredentialFieldId.Password).hasPlaintext("second-unit-secret"))

        repository.save(
            created.id,
            input.copy(label = "Renamed"),
            mapOf(CredentialFieldId.Password to CredentialSecretMutation.Delete)
        )
        assertTrue(CredentialSecretStore(secretRoot, keyProvider)
            .state(created.id, CredentialFieldId.Password) == CredentialSecretState.NotConfigured)

        repository.save(
            created.id,
            input.copy(label = "Renamed"),
            mapOf(CredentialFieldId.Password to CredentialSecretMutation.Replace("delete-with-source-secret"))
        )
        assertTrue(repository.delete(created.id))
        assertFalse(root.resolve("files/credential-sources/sources.json").readText().contains("delete-with-source-secret"))
        assertFalse(secretRoot.resolve(created.id).exists())
        assertNull(CredentialSourceStore(root.resolve("files/credential-sources")).get(created.id))
    }

    @Test
    fun sourceNameValidationUsesCredentialNameWording() {
        val invalid = CredentialSourceInput(CredentialType.AccountPassword, "  ")
        assertEquals(
            "请输入凭证名称",
            CredentialSourceValidator.validateInput(invalid).single { it.field == "label" }.reason
        )
    }

    @Test
    fun secretDraftStartsBlankAndKeepReplaceDeleteAreDistinct() {
        val fresh = CredentialSecretDraft()
        assertTrue(fresh.input.isEmpty())
        assertTrue(fresh.mutation == CredentialSecretMutation.Keep)

        val replacement = fresh.edit("new-unit-secret")
        assertTrue(replacement.mutation == CredentialSecretMutation.Replace("new-unit-secret"))
        assertTrue(replacement.edit("").mutation == CredentialSecretMutation.Keep)
        assertTrue(fresh.clear().mutation == CredentialSecretMutation.Delete)
    }

    @Test
    fun genericPasswordAndCookieStayOutOfMetadataJson() {
        val root = Files.createTempDirectory("credential-source-generic").toFile()
        val secretRoot = root.resolve("noBackupFilesDir/credential-secrets")
        val repository = CredentialSourceRepository(
            CredentialSourceStore(root.resolve("files/credential-sources"), nextId = { "cs_generic-source123" }),
            CredentialSecretStore(secretRoot, TestKeyProvider())
        )
        val passwordValue = "generic-password-unit"
        val cookieValue = "generic-cookie-unit; sid=x"
        val source = repository.save(
            null,
            CredentialSourceInput(
                CredentialType.GenericAccount,
                "Music",
                realm = "music.example.com",
                publicFields = mapOf(CredentialFieldId.Account to "account")
            ),
            mapOf(
                CredentialFieldId.Password to CredentialSecretMutation.Replace(passwordValue),
                CredentialFieldId.Cookie to CredentialSecretMutation.Replace(cookieValue)
            )
        )
        val metadataJson = root.resolve("files/credential-sources/sources.json").readText()

        assertFalse(metadataJson.contains(passwordValue))
        assertFalse(metadataJson.contains(cookieValue))
        assertTrue(secretRoot.resolve("${source.id}/password.secret").isFile)
        assertTrue(secretRoot.resolve("${source.id}/cookie.secret").isFile)
        assertTrue(source.secretFieldStates[CredentialFieldId.Password] == CredentialSecretState.Configured)
        assertTrue(source.secretFieldStates[CredentialFieldId.Cookie] == CredentialSecretState.Configured)
    }

    @Test
    fun secretSaveFailureKeepsMetadataAndDeletionFailureKeepsSource() {
        val root = Files.createTempDirectory("credential-source-partial-save").toFile()
        val sourceRoot = root.resolve("files/credential-sources")
        val secretRoot = root.resolve("noBackupFilesDir/credential-secrets")
        val failingRepository = CredentialSourceRepository(
            CredentialSourceStore(sourceRoot, nextId = { "cs_partial-save123" }),
            CredentialSecretStore(secretRoot, FailingKeyProvider())
        )
        val input = CredentialSourceInput(CredentialType.AccountPassword, "Account")
        val saveResult = runCatching {
            failingRepository.save(
                null,
                input,
                mapOf(CredentialFieldId.Password to CredentialSecretMutation.Replace("partial-save-test-secret"))
            )
        }

        assertTrue(saveResult.exceptionOrNull() is CredentialSourceSaveException)
        assertTrue(CredentialSourceStore(sourceRoot).get("cs_partial-save123") != null)

        val normalKeyProvider = TestKeyProvider()
        val sourceStore = CredentialSourceStore(sourceRoot)
        val existing = sourceStore.get("cs_partial-save123")!!
        val obstructedDirectory = secretRoot.resolve(existing.id)
        obstructedDirectory.mkdirs()
        obstructedDirectory.resolve("unexpected-entry").writeText("")
        val repository = CredentialSourceRepository(
            sourceStore,
            CredentialSecretStore(secretRoot, normalKeyProvider)
        )

        assertTrue(runCatching { repository.delete(existing.id) }.isFailure)
        assertTrue(sourceStore.get(existing.id) != null)
    }

    private fun CredentialSecretReadResult.hasPlaintext(expected: String): Boolean =
        this is CredentialSecretReadResult.Available && plaintext == expected

    private class TestKeyProvider : CredentialSecretKeyProvider {
        private var key: javax.crypto.SecretKey? = null

        override fun getExistingKey(): javax.crypto.SecretKey? = key

        override fun getOrCreateKey(): javax.crypto.SecretKey = key ?: javax.crypto.KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
            .also { key = it }

        override fun recreateKey(): javax.crypto.SecretKey = javax.crypto.KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
            .also { key = it }
    }

    private class FailingKeyProvider : CredentialSecretKeyProvider {
        override fun getExistingKey(): javax.crypto.SecretKey? = null
        override fun getOrCreateKey(): javax.crypto.SecretKey = error("Key is unavailable")
        override fun recreateKey(): javax.crypto.SecretKey = error("Key is unavailable")
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
