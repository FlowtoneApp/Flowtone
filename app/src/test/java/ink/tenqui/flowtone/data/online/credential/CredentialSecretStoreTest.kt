package ink.tenqui.flowtone.data.online.credential

import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialSecretStoreTest {
    @Test
    fun passwordAndCookieRoundTripAndStoreRecreation() {
        val root = Files.createTempDirectory("credential-secrets-no-backup").toFile()
        val keys = TestKeyProvider()
        val firstStore = CredentialSecretStore(root, keys)

        firstStore.put(SourceA, CredentialFieldId.Password, PasswordValue)
        firstStore.put(SourceA, CredentialFieldId.Cookie, CookieValue)

        val rebuiltStore = CredentialSecretStore(root, keys)
        assertTrue(rebuiltStore.get(SourceA, CredentialFieldId.Password).hasPlaintext(PasswordValue))
        assertTrue(rebuiltStore.get(SourceA, CredentialFieldId.Cookie).hasPlaintext(CookieValue))
        assertTrue(root.resolve("$SourceA/password.secret").isFile)
        assertTrue(root.resolve("$SourceA/cookie.secret").isFile)
        assertTrue(root.walkTopDown().filter(File::isFile).all { it.name.endsWith(".secret") })
    }

    @Test
    fun repeatedWritesUseDifferentRandomIvAndCiphertext() {
        val root = Files.createTempDirectory("credential-secrets-random").toFile()
        val store = CredentialSecretStore(root, TestKeyProvider())
        store.put(SourceA, CredentialFieldId.Password, PasswordValue)
        val first = root.resolve("$SourceA/password.secret").readBytes()
        store.put(SourceA, CredentialFieldId.Password, PasswordValue)
        val second = root.resolve("$SourceA/password.secret").readBytes()

        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(16, 28).contentEquals(second.copyOfRange(16, 28)))
        assertFalse(first.copyOfRange(28, first.size).contentEquals(second.copyOfRange(28, second.size)))
    }

    @Test
    fun ciphertextAndIvTamperingAreUnavailable() {
        val root = Files.createTempDirectory("credential-secrets-tamper").toFile()
        val store = CredentialSecretStore(root, TestKeyProvider())
        val file = root.resolve("$SourceA/password.secret")
        store.put(SourceA, CredentialFieldId.Password, PasswordValue)
        val original = file.readBytes()

        val changedCiphertext = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(changedCiphertext)
        assertTrue(store.get(SourceA, CredentialFieldId.Password) is CredentialSecretReadResult.Unavailable)

        val changedIv = original.copyOf().also { it[16] = (it[16].toInt() xor 1).toByte() }
        file.writeBytes(changedIv)
        assertTrue(store.get(SourceA, CredentialFieldId.Password) is CredentialSecretReadResult.Unavailable)
    }

    @Test
    fun ciphertextIsBoundToSourceAndField() {
        val root = Files.createTempDirectory("credential-secrets-aad").toFile()
        val store = CredentialSecretStore(root, TestKeyProvider())
        store.put(SourceA, CredentialFieldId.Password, PasswordValue)
        val passwordBlob = root.resolve("$SourceA/password.secret").readBytes()

        root.resolve("$SourceB").mkdirs()
        root.resolve("$SourceB/password.secret").writeBytes(passwordBlob)
        root.resolve("$SourceA/cookie.secret").writeBytes(passwordBlob)

        assertTrue(store.get(SourceB, CredentialFieldId.Password) is CredentialSecretReadResult.Unavailable)
        assertTrue(store.get(SourceA, CredentialFieldId.Cookie) is CredentialSecretReadResult.Unavailable)
    }

    @Test
    fun missingKeystoreKeyReturnsUnavailableWithoutCreatingReplacementOnRead() {
        val root = Files.createTempDirectory("credential-secrets-missing-key").toFile()
        CredentialSecretStore(root, TestKeyProvider()).put(SourceA, CredentialFieldId.Password, PasswordValue)
        val missingKeyStore = CredentialSecretStore(root, MissingKeyProvider())

        assertTrue(missingKeyStore.get(SourceA, CredentialFieldId.Password) is CredentialSecretReadResult.Unavailable)
        assertTrue(missingKeyStore.state(SourceA, CredentialFieldId.Password) == CredentialSecretState.Unavailable)
        assertTrue(missingKeyStore.exists(SourceA, CredentialFieldId.Password))

        val wrongKeyStore = CredentialSecretStore(root, TestKeyProvider())
        wrongKeyStore.put(SourceB, CredentialFieldId.Password, "unrelated-test-value")
        assertTrue(wrongKeyStore.get(SourceA, CredentialFieldId.Password) is CredentialSecretReadResult.Unavailable)
    }

    @Test
    fun writingAfterInvalidKeyCreatesKeyForNewValueAndLeavesOldCiphertextUnavailable() {
        val root = Files.createTempDirectory("credential-secrets-invalid-key").toFile()
        val keys = InvalidThenRecoveringKeyProvider()
        val store = CredentialSecretStore(root, keys)
        store.put(SourceA, CredentialFieldId.Password, PasswordValue)
        store.put(SourceA, CredentialFieldId.Cookie, CookieValue)
        keys.invalid = true

        assertTrue(store.state(SourceA, CredentialFieldId.Password) == CredentialSecretState.Unavailable)
        store.put(SourceA, CredentialFieldId.Password, "replacement-unit-value")

        assertTrue(store.state(SourceA, CredentialFieldId.Password) == CredentialSecretState.Configured)
        assertTrue(store.state(SourceA, CredentialFieldId.Cookie) == CredentialSecretState.Unavailable)
    }

    @Test
    fun ordinaryFieldsAreRejectedAndFieldAndSourceDeletionWork() {
        val root = Files.createTempDirectory("credential-secrets-delete").toFile()
        val store = CredentialSecretStore(root, TestKeyProvider())
        val ordinaryFieldResult = runCatching { store.put(SourceA, CredentialFieldId.Username, "public-value") }
        assertTrue(ordinaryFieldResult.isFailure)
        assertFalse(root.resolve(SourceA).exists())

        store.put(SourceA, CredentialFieldId.Password, PasswordValue)
        store.put(SourceA, CredentialFieldId.Cookie, CookieValue)
        store.delete(SourceA, CredentialFieldId.Password)
        assertTrue(store.state(SourceA, CredentialFieldId.Password) == CredentialSecretState.NotConfigured)
        assertTrue(store.state(SourceA, CredentialFieldId.Cookie) == CredentialSecretState.Configured)
        store.deleteAll(SourceA)
        assertFalse(root.resolve(SourceA).exists())
    }

    @Test
    fun sourceIdCannotEscapeSecretRoot() {
        val root = Files.createTempDirectory("credential-secrets-path").toFile()
        val store = CredentialSecretStore(root, TestKeyProvider())

        assertTrue(runCatching {
            store.put("../outside", CredentialFieldId.Password, "path-test-value")
        }.isFailure)
        assertTrue(root.listFiles().isNullOrEmpty())
        assertFalse(root.resolveSibling("outside").exists())
    }

    private fun CredentialSecretReadResult.hasPlaintext(expected: String): Boolean =
        this is CredentialSecretReadResult.Available && plaintext == expected

    private class TestKeyProvider : CredentialSecretKeyProvider {
        private var key: SecretKey? = null

        override fun getExistingKey(): SecretKey? = key

        override fun getOrCreateKey(): SecretKey = key ?: KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
            .also { key = it }

        override fun recreateKey(): SecretKey = KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
            .also { key = it }
    }

    private class MissingKeyProvider : CredentialSecretKeyProvider {
        override fun getExistingKey(): SecretKey? = null
        override fun getOrCreateKey(): SecretKey = error("Read path must not create a key")
        override fun recreateKey(): SecretKey = error("Read path must not recreate a key")
    }

    private class InvalidThenRecoveringKeyProvider : CredentialSecretKeyProvider {
        private var key: SecretKey = newKey()
        var invalid: Boolean = false

        override fun getExistingKey(): SecretKey = if (invalid) SecretKeySpec(ByteArray(8), "AES") else key

        override fun getOrCreateKey(): SecretKey = getExistingKey()

        override fun recreateKey(): SecretKey {
            key = newKey()
            invalid = false
            return key
        }

        private fun newKey(): SecretKey = KeyGenerator.getInstance("AES")
            .apply { init(256) }
            .generateKey()
    }

    private companion object {
        const val SourceA = "cs_source-a123"
        const val SourceB = "cs_source-b123"
        const val PasswordValue = "unit-test-password"
        const val CookieValue = "unit-test-cookie; a=b"
    }
}
