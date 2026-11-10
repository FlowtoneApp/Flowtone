package ink.tenqui.flowtone.data.online.credential

import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.nio.file.Files
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialGrantTest {
    @Test
    fun namedGrantsRequireReadyFieldsAndReevaluateWithoutRevoking() {
        val root = Files.createTempDirectory("named-grants").toFile()
        val grants = CredentialGrantStore(root.resolve("grants"))
        val ids = listOf("cs_account-one", "cs_account-two").iterator()
        val sourceStore = CredentialSourceStore(root.resolve("sources")) { ids.next() }
        val secrets = CredentialSecretStore(root.resolve("secrets"), TestKeyProvider())
        val sources = CredentialSourceRepository(sourceStore, secrets, grants)
        val first = sourceStore.create(CredentialSourceInput(CredentialType.GenericAccount, "One", realm = "example.com"))
        val second = sourceStore.create(CredentialSourceInput(CredentialType.GenericAccount, "Two", realm = "example.com"))
        val field = NamedCredentialRequestField("cookie.sid", NamedSecretType.Cookie, true, CredentialInjection.Cookie, "sid")
        val optional = NamedCredentialRequestField("oauth.token", NamedSecretType.Token, false, CredentialInjection.Bearer)
        val request = CredentialRequestDefinition("login", CredentialType.GenericAccount, "Login", description = "Use", realm = "example.com",
            named = NamedCredentialRequestContract("https://example.com", setOf(field, optional)))
        val base = installed()
        val installed = base.copy(descriptor = base.descriptor.copy(credentialRequests = listOf(request)))
        val snapshot = CredentialRequestContractSnapshot.from(request)!!
        val repo = CredentialGrantRepository(grants, sources) { 42L }
        assertThrows(IllegalArgumentException::class.java) {
            repo.createOrReplace(installed, "login", first.id, snapshot, installed.installationInstanceId!!) { installed }
        }
        sources.putNamedSecret(first.id, NamedSecretField(field.id, field.type), "fixture-private-value")
        val created = repo.createOrReplace(installed, "login", first.id, snapshot, installed.installationInstanceId!!) { installed }
        assertTrue(repo.evaluate(installed, "login").usable)
        assertEquals(listOf(optional.id), repo.evaluate(installed, "login").sourceMatch!!.optionalIssues)
        assertThrows(IllegalArgumentException::class.java) {
            repo.createOrReplace(installed, "login", second.id, snapshot, installed.installationInstanceId!!) { installed }
        }
        val other = installed(extensionId = "extension.two").let { it.copy(descriptor = it.descriptor.copy(credentialRequests = listOf(request))) }
        repo.createOrReplace(other, "login", first.id, snapshot, other.installationInstanceId!!) { other }
        assertEquals(2, listOf(repo.evaluate(installed, "login"), repo.evaluate(other, "login")).count { it.usable })
        assertFalse(root.resolve("grants/grants.json").readText().contains("fixture-private-value"))
        val changed = request.copy(named = request.named!!.copy(origin = "https://other.example.com"))
        assertEquals(CredentialGrantInvalidReason.RequestContractChanged, repo.evaluate(installed.copy(descriptor = installed.descriptor.copy(credentialRequests = listOf(changed))), "login").invalidReason)
        assertEquals(CredentialGrantInvalidReason.ExtensionInstallationChanged, repo.evaluate(installed.copy(installationInstanceId = uuid()), "login").invalidReason)
        root.resolve("secrets/${first.id}/named-v1/${field.id}~Cookie.secret").writeText("damaged")
        assertTrue(repo.evaluate(installed, "login").authorizationValid)
        assertFalse(repo.evaluate(installed, "login").sourceReady)
        assertEquals(listOf(field.id), repo.evaluate(installed, "login").sourceMatch!!.unavailableRequiredFields)
        assertTrue(repo.revoke(installed.manifest.id, "login"))
        assertEquals(CredentialGrantInvalidReason.NotGranted, repo.evaluate(installed, "login").invalidReason)
        assertEquals(created.requestContract, snapshot)
    }

    @Test
    fun versionOneMigratesAndMixedRecordsFailClosed() {
        val root = Files.createTempDirectory("grant-migration").toFile()
        val store = CredentialGrantStore(root)
        val old = grant()
        store.putOrReplace(old)
        val file = root.resolve("grants.json")
        val legacy = org.json.JSONObject(file.readText())
        legacy.put("formatVersion", 1)
        legacy.getJSONArray("grants").getJSONObject(0).getJSONObject("requestContract").remove("named")
        file.writeText(legacy.toString())
        assertEquals(old, CredentialGrantStore(root).find(old.extensionId, old.credentialRequestId))
        val named = NamedCredentialRequestContract("https://example.com", setOf(NamedCredentialRequestField("oauth.token", NamedSecretType.Token, true, CredentialInjection.Bearer)))
        val snapshot = CredentialRequestContractSnapshot(CredentialType.GenericAccount, "example.com", emptySet(), emptySet(), named)
        val newer = old.copy(extensionId = "extension.two", requestContract = snapshot)
        store.putOrReplace(newer)
        assertEquals(old, CredentialGrantStore(root).find(old.extensionId, old.credentialRequestId))
        assertEquals(newer, CredentialGrantStore(root).find(newer.extensionId, newer.credentialRequestId))
        val corrupted = file.readText().replace("\"Bearer\"", "\"Unknown\"")
        file.writeText(corrupted)
        assertThrows(CredentialGrantStoreException::class.java) { store.revoke(old.extensionId, old.credentialRequestId) }
        assertEquals(corrupted, file.readText())
    }
    @Test
    fun namedContractCannotUseOrSilentlyRemoveLegacyGrant() {
        val store = CredentialGrantStore(Files.createTempDirectory("grant-named").toFile())
        val old = grant()
        store.putOrReplace(old)
        val request = CredentialRequestDefinition("login", CredentialType.GenericAccount, "Account",
            realm = "example.com", description = "Test", named = NamedCredentialRequestContract("https://example.com", setOf(
                NamedCredentialRequestField("oauth.token", NamedSecretType.Token, true, CredentialInjection.Bearer)
            )))
        assertTrue(CredentialRequestContractSnapshot.from(request) != old.requestContract)
        assertEquals(0, store.reconcileExtension(old.extensionId, old.extensionInstanceId, listOf(request)))
        assertEquals(old, store.find(old.extensionId, "login"))
    }
    @Test
    fun grantStorePersistsAcrossInstancesAndIsolatesExtensionAndRequest() {
        val root = Files.createTempDirectory("credential-grants").toFile()
        val store = CredentialGrantStore(root)
        val first = grant(extensionId = "extension.one", requestId = "login")
        val otherRequest = grant(extensionId = "extension.one", requestId = "backup")
        val otherExtension = grant(extensionId = "extension.two", requestId = "login")
        store.putOrReplace(first)
        store.putOrReplace(otherRequest)
        store.putOrReplace(otherExtension)

        val reopened = CredentialGrantStore(root)
        assertEquals(first, reopened.find("extension.one", "login"))
        assertEquals(2, reopened.grantsForExtension("extension.one").size)
        assertNull(reopened.find("extension.two", "backup"))
        assertEquals(1, reopened.grantsForExtension("extension.two").size)
        assertFalse(root.resolve("grants.json").readText().contains("fixture-secret-value"))
    }

    @Test
    fun revokeAndBulkCleanupRemoveOnlyMatchingGrants() {
        val store = CredentialGrantStore(Files.createTempDirectory("grant-cleanup").toFile())
        store.putOrReplace(grant("extension.one", "login", "cs_source-one"))
        store.putOrReplace(grant("extension.one", "backup", "cs_source-two"))
        store.putOrReplace(grant("extension.two", "login", "cs_source-one"))

        assertTrue(store.revoke("extension.one", "login"))
        assertEquals(1, store.deleteForCredentialSource("cs_source-one"))
        assertEquals(1, store.deleteForExtension("extension.one"))
        assertTrue(store.grantsForExtension("extension.one").isEmpty())
        assertTrue(store.grantsForExtension("extension.two").isEmpty())
    }

    @Test
    fun malformedUnknownFieldsAndInvalidReferencesFailClosed() {
        val root = Files.createTempDirectory("grant-corrupt").toFile()
        val store = CredentialGrantStore(root)
        store.putOrReplace(grant())
        val file = root.resolve("grants.json")
        file.writeText(file.readText().replace("\"createdAtEpochMillis\":1234", "\"createdAtEpochMillis\":1234,\"secret\":\"fixture\""))

        assertThrows(CredentialGrantStoreException::class.java) {
            CredentialGrantStore(root).find("extension.one", "login")
        }
        assertThrows(CredentialGrantStoreException::class.java) {
            store.revoke("extension.one", "login")
        }
        assertTrue(file.readText().contains("\"secret\""))
    }

    @Test
    fun malformedJsonDoesNotTurnIntoAnEmptyWritableStore() {
        val root = Files.createTempDirectory("grant-malformed").toFile()
        root.resolve("grants.json").writeText("not-json")
        val store = CredentialGrantStore(root)

        assertThrows(CredentialGrantStoreException::class.java) { store.grantsForExtension("extension.one") }
        assertThrows(CredentialGrantStoreException::class.java) { store.deleteForExtension("extension.one") }
        assertEquals("not-json", root.resolve("grants.json").readText())
    }

    @Test
    fun updateKeepsUnchangedRequestButRevokesChangedOrRemovedContracts() {
        val store = CredentialGrantStore(Files.createTempDirectory("grant-update").toFile())
        val unchanged = grant()
        store.putOrReplace(unchanged)
        assertEquals(0, store.reconcileExtension(
            unchanged.extensionId,
            unchanged.extensionInstanceId,
            listOf(CredentialRequestDefinition("login", CredentialType.AccountPassword, "renamed", identifiers = listOf(CredentialIdentifierType.Email)))
        ))
        assertEquals(1, store.grantsForExtension(unchanged.extensionId).size)

        assertEquals(1, store.reconcileExtension(
            unchanged.extensionId,
            unchanged.extensionInstanceId,
            listOf(CredentialRequestDefinition("login", CredentialType.AccountPassword, "changed", identifiers = listOf(CredentialIdentifierType.Phone)))
        ))
        assertTrue(store.grantsForExtension(unchanged.extensionId).isEmpty())

        store.putOrReplace(unchanged)
        assertEquals(1, store.reconcileExtension(unchanged.extensionId, unchanged.extensionInstanceId, emptyList()))
        store.putOrReplace(unchanged)
        assertEquals(1, store.reconcileExtension(unchanged.extensionId, uuid(), listOf(
            CredentialRequestDefinition("login", CredentialType.AccountPassword, "same", identifiers = listOf(CredentialIdentifierType.Email))
        )))
    }

    @Test
    fun normalizedContractCapturesTypeRealmFieldsAndIdentityConstraints() {
        val account = CredentialRequestContractSnapshot.from(
            CredentialRequestDefinition(
                "login", CredentialType.AccountPassword, "Account", identifiers = listOf(CredentialIdentifierType.Email)
            )
        )!!
        val accountLabelChanged = CredentialRequestContractSnapshot.from(
            CredentialRequestDefinition(
                "login", CredentialType.AccountPassword, "Renamed", identifiers = listOf(CredentialIdentifierType.Email)
            )
        )!!
        assertEquals(account, accountLabelChanged)
        assertFalse(account == CredentialRequestContractSnapshot.from(
            CredentialRequestDefinition(
                "login", CredentialType.AccountPassword, "Account", identifiers = listOf(CredentialIdentifierType.Phone)
            )
        ))

        val genericCookie = CredentialRequestContractSnapshot.from(
            CredentialRequestDefinition(
                "session", CredentialType.GenericAccount, "Session",
                realm = "music.example.com",
                genericFieldIds = listOf(CredentialFieldId.Cookie)
            )
        )!!
        val genericPassword = CredentialRequestContractSnapshot.from(
            CredentialRequestDefinition(
                "session", CredentialType.GenericAccount, "Session",
                realm = "music.example.com",
                genericFieldIds = listOf(CredentialFieldId.Password)
            )
        )!!
        val otherRealm = CredentialRequestContractSnapshot.from(
            CredentialRequestDefinition(
                "session", CredentialType.GenericAccount, "Session",
                realm = "other.example.com",
                genericFieldIds = listOf(CredentialFieldId.Cookie)
            )
        )!!
        assertFalse(genericCookie == genericPassword)
        assertFalse(genericCookie == otherRealm)
        assertFalse(genericCookie == account)
    }

    @Test
    fun repositorySeparatesAuthorizationFromSourceReadinessAndBindsContract() {
        val root = Files.createTempDirectory("grant-evaluation").toFile()
        val grantStore = CredentialGrantStore(root.resolve("noBackupFilesDir/credential-grants"))
        val sourceRoot = root.resolve("files/credential-sources")
        val secretRoot = root.resolve("noBackupFilesDir/credential-secrets")
        val sourceStore = CredentialSourceStore(sourceRoot, nextId = { "cs_account-source" })
        val source = sourceStore.create(
            CredentialSourceInput(
                CredentialType.AccountPassword,
                "Test account",
                publicFields = mapOf(CredentialFieldId.Email to "fixture@example.test")
            )
        )
        val sourceRepository = CredentialSourceRepository(
            sourceStore,
            CredentialSecretStore(secretRoot, TestKeyProvider()),
            grantStore
        )
        val repository = CredentialGrantRepository(grantStore, sourceRepository) { 1234L }
        val installed = installed()
        val missing = repository.evaluate(installed, "login")
        assertFalse(missing.authorizationValid)
        assertEquals(CredentialGrantInvalidReason.NotGranted, missing.invalidReason)
        assertEquals(CredentialGrantInvalidReason.ExtensionNotInstalled, repository.evaluate(null, "login").invalidReason)

        val contract = CredentialRequestContractSnapshot.from(installed.descriptor.credentialRequests.single())!!
        val savedGrant = grant(
            extensionId = installed.manifest.id,
            requestId = "login",
            sourceId = source.id,
            contract = contract,
            instanceId = installed.installationInstanceId!!
        )
        grantStore.putOrReplace(savedGrant)

        val evaluation = repository.evaluate(installed, "login")
        assertTrue(evaluation.authorizationValid)
        assertFalse(evaluation.sourceReady)
        assertFalse(evaluation.usable)
        assertEquals("Test account", evaluation.sourceLabel)
        secretRoot.resolve(source.id).mkdirs()
        secretRoot.resolve(source.id).resolve("password.secret").writeText("damaged envelope")
        val unavailableSecret = repository.evaluate(installed, "login")
        assertTrue(unavailableSecret.authorizationValid)
        assertFalse(unavailableSecret.sourceReady)
        assertEquals(CredentialGrantInvalidReason.NotGranted, repository.evaluate(installed, "new-request").invalidReason)
        val addedRequest = CredentialRequestDefinition(
            "added", CredentialType.AccountPassword, "Added", identifiers = listOf(CredentialIdentifierType.UserId)
        )
        val updatedWithNewRequest = installed.copy(
            descriptor = installed.descriptor.copy(credentialRequests = installed.descriptor.credentialRequests + addedRequest)
        )
        assertEquals(CredentialGrantInvalidReason.NotGranted, repository.evaluate(updatedWithNewRequest, "added").invalidReason)
        assertEquals(
            CredentialGrantInvalidReason.NotGranted,
            repository.evaluate(installed(extensionId = "other.extension"), "login").invalidReason
        )
        assertEquals(
            CredentialGrantInvalidReason.ExtensionInstallationChanged,
            repository.evaluate(installed.copy(installationInstanceId = uuid()), "login").invalidReason
        )

        val changedContract = installed(requestIdentifier = "phone")
        assertEquals(
            CredentialGrantInvalidReason.RequestContractChanged,
            repository.evaluate(changedContract.copy(installationInstanceId = installed.installationInstanceId), "login").invalidReason
        )
        sourceStore.delete(source.id)
        assertEquals(
            CredentialGrantInvalidReason.CredentialSourceMissing,
            repository.evaluate(installed, "login").invalidReason
        )
    }

    @Test
    fun creatingGrantRequiresReadySourceAndConfirmedCurrentContract() {
        val root = Files.createTempDirectory("grant-create").toFile()
        val grants = CredentialGrantStore(root.resolve("credential-grants"))
        val sourceStore = CredentialSourceStore(root.resolve("credential-sources"), nextId = { "cs_ready-source" })
        val keys = TestKeyProvider()
        val secretStore = CredentialSecretStore(root.resolve("credential-secrets"), keys)
        val sourceRepository = CredentialSourceRepository(sourceStore, secretStore, grants)
        val source = sourceStore.create(
            CredentialSourceInput(
                CredentialType.AccountPassword,
                "Ready fixture",
                publicFields = mapOf(CredentialFieldId.Email to "fixture@example.test")
            )
        )
        val repo = CredentialGrantRepository(grants, sourceRepository) { 2000L }
        val installed = installed()
        val snapshot = CredentialRequestContractSnapshot.from(installed.descriptor.credentialRequests.single())!!

        assertThrows(IllegalArgumentException::class.java) {
            repo.createOrReplace(installed, "login", source.id, snapshot, installed.installationInstanceId!!) { installed }
        }
        secretStore.put(source.id, CredentialFieldId.Password, "unit-only-fixture")
        assertThrows(IllegalArgumentException::class.java) {
            repo.createOrReplace(
                installed, "login", source.id, snapshot, installed.installationInstanceId!!
            ) { null }
        }
        assertTrue(grants.grantsForExtension(installed.manifest.id).isEmpty())
        val created = repo.createOrReplace(installed, "login", source.id, snapshot, installed.installationInstanceId!!) { installed }

        assertEquals(1, grants.grantsForExtension(installed.manifest.id).size)
        assertEquals(2000L, created.createdAtEpochMillis)
        assertFalse(root.resolve("credential-grants/grants.json").readText().contains("unit-only-fixture"))
        assertThrows(IllegalArgumentException::class.java) {
            repo.createOrReplace(installed, "login", source.id, installed(requestIdentifier = "phone").let {
                CredentialRequestContractSnapshot.from(it.descriptor.credentialRequests.single())!!
            }, installed.installationInstanceId!!) { installed }
        }
    }

    @Test
    fun sourceRepositoryDeletionAlsoRevokesAssociatedGrants() {
        val root = Files.createTempDirectory("grant-source-delete").toFile()
        val grantStore = CredentialGrantStore(root.resolve("grants"))
        val sourceStore = CredentialSourceStore(root.resolve("sources"), nextId = { "cs_delete-source" })
        val sources = CredentialSourceRepository(
            sourceStore,
            CredentialSecretStore(root.resolve("secrets"), TestKeyProvider()),
            grantStore
        )
        val source = sourceStore.create(CredentialSourceInput(CredentialType.AccountPassword, "Account"))
        grantStore.putOrReplace(grant(sourceId = source.id))

        assertTrue(sources.delete(source.id))
        assertTrue(grantStore.grantsForExtension("extension.one").isEmpty())
        assertNull(sourceStore.get(source.id))
    }

    @Test
    fun failedGrantCleanupAbortsSourceDeletion() {
        val root = Files.createTempDirectory("grant-source-delete-failure").toFile()
        val grantsDirectory = root.resolve("grants")
        grantsDirectory.mkdirs()
        grantsDirectory.resolve("grants.json").writeText("damaged")
        val sourceStore = CredentialSourceStore(root.resolve("sources"), nextId = { "cs_keep-source" })
        val source = sourceStore.create(CredentialSourceInput(CredentialType.AccountPassword, "Keep source"))
        val repository = CredentialSourceRepository(
            sourceStore,
            CredentialSecretStore(root.resolve("secrets"), TestKeyProvider()),
            CredentialGrantStore(grantsDirectory)
        )

        assertThrows(CredentialGrantStoreException::class.java) { repository.delete(source.id) }
        assertEquals(source, sourceStore.get(source.id))
    }

    @Test
    fun authorizationGateIsOneShotAndRejectsCanceledOrStaleCallback() {
        val gate = CredentialGrantAuthorizationGate()
        val binding = grant().let {
            CredentialGrantAuthorizationBinding(
                it.extensionId,
                it.extensionInstanceId,
                it.credentialRequestId,
                it.credentialSourceId,
                it.requestContract
            )
        }
        var commits = 0
        val canceled = gate.begin(binding)!!
        gate.cancel(canceled)
        assertNull(gate.commitIfCurrent(canceled) { commits++ })
        assertEquals(0, commits)

        val active = gate.begin(binding)!!
        assertNull(gate.begin(binding))
        gate.markKeyguardConfirmation(active)
        assertTrue(gate.isKeyguardConfirmationPending())
        gate.finishKeyguardConfirmation(active)
        assertFalse(gate.isKeyguardConfirmationPending())
        assertEquals(1, gate.commitIfCurrent(active) { ++commits })
        assertEquals(1, commits)
        assertNull(gate.commitIfCurrent(active) { ++commits })
        gate.close()
    }

    private fun grant(
        extensionId: String = "extension.one",
        requestId: String = "login",
        sourceId: String = "cs_source-one",
        contract: CredentialRequestContractSnapshot = CredentialRequestContractSnapshot(
            credentialType = CredentialType.AccountPassword,
            realm = null,
            requestedFieldIds = setOf(CredentialFieldId.Email, CredentialFieldId.Password),
            identityConstraints = setOf(CredentialIdentifierType.Email)
        ),
        instanceId: String = InstallInstance
    ) = CredentialGrant(extensionId, instanceId, requestId, sourceId, contract, 1234L)

    private fun installed(
        extensionId: String = "extension.one",
        requestIdentifier: String = "email"
    ): InstalledExtension {
        val descriptor = ExtensionManifestParser.parseNormalized(
            """{"formatVersion":2,"id":"$extensionId","name":"Test extension","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"credentialRequests":[{"id":"login","type":"account_password","label":"Sign in","identifiers":["$requestIdentifier"]}]}"""
        )
        return InstalledExtension(descriptor, Files.createTempDirectory("installed-extension").toFile(), false, InstallInstance)
    }

    private fun uuid(): String = UUID.randomUUID().toString()

    private class TestKeyProvider : CredentialSecretKeyProvider {
        private var key: SecretKey? = null
        override fun getExistingKey(): SecretKey? = key
        override fun getOrCreateKey(): SecretKey = key ?: KeyGenerator.getInstance("AES")
            .apply { init(256) }.generateKey().also { key = it }
        override fun recreateKey(): SecretKey = getOrCreateKey()
    }

    private companion object {
        const val InstallInstance = "00000000-0000-0000-0000-000000000001"
    }
}
