package ink.tenqui.flowtone.data.online.credential

import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.util.UUID

class NamedCredentialSecretTest {
    @Test fun importChecksAccountContractAndReplacementWithoutCreatingGrant() {
        val root = Files.createTempDirectory("manual-import").toFile()
        val ids = listOf("cs_manual-first", "cs_manual-second").iterator()
        val grants = CredentialGrantStore(root.resolve("grants"))
        val secrets = CredentialSecretStore(root.resolve("secrets"), Keys())
        val repository = CredentialSourceRepository(CredentialSourceStore(root.resolve("sources")) { ids.next() }, secrets, grants)
        val a = repository.save(null, input, emptyMap())
        val b = repository.save(null, input.copy(label = "Second"), emptyMap())
        val descriptor = ExtensionManifestParser.parseNormalized("""{"formatVersion":2,"id":"example.ext","name":"Example","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"credentialRequests":[{"id":"session","type":"generic_account","label":"Session","realm":"example.com","description":"Test","origin":"https://example.com","namedFields":[{"id":"cookie.session_key","type":"cookie","required":true,"injection":"cookie","cookieName":"sid"},{"id":"oauth.refresh_token","type":"token","required":false,"injection":"bearer"}]}],"permissions":{"network":{"origins":["https://example.com"]}}}""")
        val installed = InstalledExtension(descriptor, root, true, UUID.randomUUID().toString())
        val request = descriptor.credentialRequests.single()
        fun import(source: String, field: NamedSecretField, value: String, replace: Boolean = false, current: InstalledExtension? = installed) =
            repository.importNamedSecretForRequest(installed.manifest.id, installed.installationInstanceId!!, request.id, request, source, field, value, replace) { current }
        assertFalse(repository.match(a, request).fullyReady)
        import(b.id, first, "private-secret")
        assertTrue(repository.match(b, request).fullyReady)
        assertFalse(repository.match(a, request).fullyReady)
        assertTrue(repository.match(b, request).optionalIssues.contains(second.id))
        assertTrue(grants.grantsForExtension(installed.manifest.id).isEmpty())
        assertTrue(runCatching { import(b.id, first, "replace") }.isFailure)
        assertEquals("private-secret", (secrets.readNamed(b.id, first) as CredentialSecretReadResult.Available).plaintext)
        import(b.id, first, "replacement", true)
        assertTrue(runCatching { repository.importNamedSecret(b.id, first.copy(type = NamedSecretType.Token), "wrong", true) }.isFailure)
        val changed = installed.copy(descriptor = descriptor.copy(credentialRequests = listOf(request.copy(named = request.named!!.copy(origin = "https://other.example.com")))))
        assertTrue(runCatching { import(a.id, first, "stale", current = changed) }.isFailure)
        assertTrue(runCatching { import(a.id, NamedSecretField("cookie.undeclared", NamedSecretType.Cookie), "wrong") }.isFailure)
        assertTrue(repository.delete(a.id))
        assertTrue(runCatching { import(a.id, first, "removed") }.isFailure)
        assertFalse(root.resolve("sources/sources.json").readText().contains("private-secret"))
    }
    private class Keys : CredentialSecretKeyProvider {
        var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getExistingKey() = key
        override fun getOrCreateKey() = key ?: error("unavailable")
        override fun recreateKey() = error("unavailable")
    }
    private val first = NamedSecretField("cookie.session_key", NamedSecretType.Cookie)
    private val second = NamedSecretField("oauth.refresh_token", NamedSecretType.Token)
    private val input = CredentialSourceInput(CredentialType.GenericAccount, "Account", realm = "example.com")

    @Test fun independentFieldsAccountsAndLegacyCompatibility() {
        val root = Files.createTempDirectory("named-secrets").toFile()
        val keys = Keys()
        val ids = listOf("cs_account-a123", "cs_account-b123").iterator()
        val sources = CredentialSourceStore(root.resolve("sources"), { ids.next() })
        val secrets = CredentialSecretStore(root.resolve("secrets"), keys)
        val repository = CredentialSourceRepository(sources, secrets)
        val a = repository.save(null, input, mapOf(CredentialFieldId.Cookie to CredentialSecretMutation.Replace("old-cookie")))
        val b = repository.save(null, input, emptyMap())
        repository.putNamedSecret(a.id, first, "alpha-secret")
        repository.putNamedSecret(a.id, second, "beta-secret")
        repository.putNamedSecret(b.id, first, "other-secret")
        assertEquals(2, repository.namedSecretFields(a.id).size)
        assertEquals("other-secret", (secrets.readNamed(b.id, first) as CredentialSecretReadResult.Available).plaintext)
        repository.putNamedSecret(a.id, first, "replacement-secret")
        assertEquals("beta-secret", (secrets.readNamed(a.id, second) as CredentialSecretReadResult.Available).plaintext)
        repository.deleteNamedSecret(a.id, first)
        assertEquals(CredentialSecretState.NotConfigured, repository.namedSecretState(a.id, first))
        assertEquals(CredentialSecretState.Configured, repository.namedSecretState(a.id, second))
        assertEquals(CredentialSecretState.Configured, repository.namedSecretState(b.id, first))
        assertEquals("old-cookie", (CredentialSecretStore(root.resolve("secrets"), keys).get(a.id, CredentialFieldId.Cookie) as CredentialSecretReadResult.Available).plaintext)
        assertFalse(root.resolve("sources/sources.json").readText().contains("secret"))
        assertFalse(secrets.readNamed(a.id, second).toString().contains("beta-secret"))
        assertTrue(repository.delete(a.id))
        assertFalse(root.resolve("secrets/" + a.id).exists())
        assertEquals(CredentialSecretState.Configured, repository.namedSecretState(b.id, first))
    }

    @Test fun invalidIdsAndTypesCannotCollideWithFixedFields() {
        for (id in listOf("cookie", "../password", "cookie/escape", "cookie..x", "Cookie.x", "a." + "x".repeat(64))) {
            assertTrue(id, runCatching { NamedSecretField(id, NamedSecretType.Cookie) }.isFailure)
        }
        val root = Files.createTempDirectory("named-collision").toFile()
        val store = CredentialSecretStore(root, Keys())
        val source = "cs_account-a123"
        store.put(source, CredentialFieldId.Cookie, "old")
        store.putNamed(source, first, "new")
        assertTrue(runCatching { store.putNamed(source, first.copy(type = NamedSecretType.Token), "other") }.isFailure)
        assertEquals("old", (store.get(source, CredentialFieldId.Cookie) as CredentialSecretReadResult.Available).plaintext)
        val snapshot = CredentialRequestContractSnapshot.from(CredentialRequestDefinition(
            "session", CredentialType.GenericAccount, "Session", realm = "example.com",
            genericFieldIds = listOf(CredentialFieldId.Cookie)
        ))!!
        assertEquals(setOf(CredentialFieldId.Cookie), snapshot.requestedFieldIds)
    }

    @Test fun failedWriteAndMissingKeyDoNotClaimConfigured() {
        val root = Files.createTempDirectory("named-failure").toFile()
        val keys = Keys()
        val store = CredentialSecretStore(root, keys)
        val source = "cs_account-a123"
        store.putNamed(source, first, "secret")
        keys.key = null
        assertEquals(CredentialSecretState.Unavailable, store.namedState(source, first))
        assertTrue(store.readNamed(source, first) is CredentialSecretReadResult.Unavailable)
        assertTrue(runCatching { store.putNamed(source, second, "failed-secret") }.isFailure)
        assertEquals(CredentialSecretState.NotConfigured, store.namedState(source, second))
        assertEquals(1, store.namedFields(source).size)
    }

    @Test fun ciphertextCannotBeMovedBetweenAccountsOrNames() {
        val root = Files.createTempDirectory("named-aad").toFile()
        val store = CredentialSecretStore(root, Keys())
        val a = "cs_account-a123"
        val b = "cs_account-b123"
        store.putNamed(a, first, "secret")
        store.putNamed(b, first, "other")
        store.putNamed(a, second, "other")
        val original = root.resolve("$a/named-v1/${first.id}~${first.type.name}.secret").readBytes()
        root.resolve("$b/named-v1/${first.id}~${first.type.name}.secret").writeBytes(original)
        root.resolve("$a/named-v1/${second.id}~${second.type.name}.secret").writeBytes(original)
        assertEquals(CredentialSecretState.Unavailable, store.namedState(b, first))
        assertEquals(CredentialSecretState.Unavailable, store.namedState(a, second))
    }
}
