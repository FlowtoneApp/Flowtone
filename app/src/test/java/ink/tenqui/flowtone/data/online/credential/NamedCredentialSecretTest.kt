package ink.tenqui.flowtone.data.online.credential

import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class NamedCredentialSecretTest {
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
