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
