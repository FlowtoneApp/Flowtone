package ink.tenqui.flowtone.data.online.credential

import ink.tenqui.flowtone.data.online.network.GlobalExtensionNetworkLimiter
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class CredentialRequestResolverTest {
    private class Keys : CredentialSecretKeyProvider {
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getExistingKey() = key
        override fun getOrCreateKey() = key
        override fun recreateKey() = key
    }
    private class Fixture(fields: String = defaultFields) {
        val root = Files.createTempDirectory("resolver").toFile()
        val grantStore = CredentialGrantStore(root.resolve("grants"))
        val sourceStore = CredentialSourceStore(root.resolve("sources")) { "cs_test-account" }
        val secrets = CredentialSecretStore(root.resolve("secrets"), Keys())
        val sources = CredentialSourceRepository(sourceStore, secrets, grantStore)
        val account = sourceStore.create(CredentialSourceInput(CredentialType.GenericAccount, "Test", realm = "example.com"))
        val descriptor = ExtensionManifestParser.parseNormalized("""{"formatVersion":2,"id":"test.extension","name":"Test","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"credentialRequests":[{"id":"session","type":"generic_account","label":"Session","realm":"example.com","description":"Test","origin":"https://api.example.com","namedFields":$fields}],"permissions":{"network":{"origins":["https://api.example.com","https://other.example.com"]}}}""")
        var installed = InstalledExtension(descriptor, root, true, UUID.randomUUID().toString())
        val repository = CredentialGrantRepository(grantStore, sources)
        val limiter = GlobalExtensionNetworkLimiter(1, 4)
        val resolver = CredentialRequestResolver(grantStore, sources) { installed }
        fun save(id: String, type: NamedSecretType, value: String) = sources.putNamedSecret(account.id, NamedSecretField(id, type), value)
        fun grant() {
            val snapshot = CredentialRequestContractSnapshot.from(installed.descriptor.credentialRequests.single())!!
            repository.createOrReplace(installed, "session", account.id, snapshot, installed.installationInstanceId!!) { installed }
        }
        fun admission() = limiter.tryAcquireInFlight()!!.also { it.acquireActive() }
        fun commit(a: GlobalExtensionNetworkLimiter.Admission, url: String = "https://api.example.com/path") = resolver.commit(a, "test.extension", installed.installationInstanceId!!, "session", url)
    }

    @Test fun headersAreDeterministicAndRedacted() {
        val f = Fixture()
        f.save("cookie.b", NamedSecretType.Cookie, "second")
        f.save("cookie.a", NamedSecretType.Cookie, "first")
        f.save("oauth.token", NamedSecretType.Token, "secret-token")
        f.grant()
        f.admission().use { a ->
            val result = f.commit(a)
            assertEquals("a=first; b=second", result.forTransport()["Cookie"])
            assertEquals("Bearer secret-token", result.forTransport()["Authorization"])
            assertFalse(result.toString().contains("secret-token"))
            result.close()
            assertTrue(result.forTransport().isEmpty())
        }
    }

    @Test fun missingOptionalUnavailableInvalidAndOriginsFailClosed() {
        val f = Fixture()
        f.save("cookie.a", NamedSecretType.Cookie, "first")
        f.save("cookie.b", NamedSecretType.Cookie, "second")
        f.grant()
        f.admission().use { a ->
            f.commit(a).use { assertFalse(it.forTransport().containsKey("Authorization")) }
            assertError(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED) { f.commit(a, "https://other.example.com/path") }
            assertError(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED) { f.commit(a, "http://api.example.com") }
            f.save("oauth.token", NamedSecretType.Token, "secret-token")
            f.root.resolve("secrets/${f.account.id}/named-v1/oauth.token~Token.secret").writeText("corrupt")
            assertError(CredentialResolutionError.CREDENTIAL_UNAVAILABLE) { f.commit(a) }
            f.sources.deleteNamedSecret(f.account.id, NamedSecretField("oauth.token", NamedSecretType.Token))
            f.save("cookie.a", NamedSecretType.Cookie, "private\r\nsecret")
            val error = assertError(CredentialResolutionError.CREDENTIAL_SCHEME_UNSUPPORTED) { f.commit(a) }
            assertFalse(error.toString().contains("private"))
            f.sources.deleteNamedSecret(f.account.id, NamedSecretField("cookie.a", NamedSecretType.Cookie))
            assertError(CredentialResolutionError.CREDENTIAL_MISSING) { f.commit(a) }
        }
    }

    @Test fun revokedWhileQueuedAndAfterCommit() {
        val f = Fixture()
        f.save("cookie.a", NamedSecretType.Cookie, "first")
        f.save("cookie.b", NamedSecretType.Cookie, "second")
        f.grant()
        val active = f.admission()
        val queued = f.limiter.tryAcquireInFlight()!!
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        try {
            val job = executor.submit<CredentialResolutionError?> {
                started.countDown(); queued.acquireActive()
                try { f.commit(queued).close(); null } catch (e: CredentialResolutionException) { e.code } finally { queued.close() }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(f.repository.revoke("test.extension", "session"))
            active.close()
            assertEquals(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED, job.get(5, TimeUnit.SECONDS))
            f.grant()
            f.admission().use { a ->
                f.commit(a).use { committed ->
                    f.repository.revoke("test.extension", "session")
                    assertEquals("a=first; b=second", committed.forTransport()["Cookie"])
                    assertError(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED) { f.commit(a) }
                }
            }
        } finally { active.close(); queued.close(); executor.shutdownNow() }
        assertEquals(0, f.limiter.snapshot().inFlight)
    }

    @Test fun sourceModificationBeforeCommitAndChangedInstanceAreRejected() {
        val f = Fixture()
        f.save("cookie.a", NamedSecretType.Cookie, "first")
        f.save("cookie.b", NamedSecretType.Cookie, "second")
        f.grant()
        val held = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val updater = pool.submit { CredentialGrantLifecycleLocks.withSource(f.account.id) {
                held.countDown(); release.await(); f.sources.deleteNamedSecret(f.account.id, NamedSecretField("cookie.a", NamedSecretType.Cookie))
            } }
            assertTrue(held.await(5, TimeUnit.SECONDS))
            val a = f.admission()
            val job = pool.submit<CredentialResolutionError?> {
                try { f.commit(a).close(); null } catch (e: CredentialResolutionException) { e.code } finally { a.close() }
            }
            release.countDown(); updater.get(5, TimeUnit.SECONDS)
            assertEquals(CredentialResolutionError.CREDENTIAL_MISSING, job.get(5, TimeUnit.SECONDS))
            f.admission().use { admission ->
                val original = f.installed.installationInstanceId!!
                f.installed = f.installed.copy(installationInstanceId = UUID.randomUUID().toString())
                assertError(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED) { f.resolver.commit(admission, "test.extension", original, "session", "https://api.example.com") }
            }
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun oldGrantCannotAuthorizeNamedSecretsAndUndeclaredOriginIsDenied() {
        val f = Fixture()
        f.save("cookie.a", NamedSecretType.Cookie, "first")
        f.save("cookie.b", NamedSecretType.Cookie, "second")
        f.grant()
        f.admission().use { a ->
            f.installed = f.installed.copy(descriptor = f.installed.descriptor.copy(networkPermissions = emptySet()))
            assertError(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED) { f.commit(a) }
            val legacy = CredentialRequestContractSnapshot(CredentialType.GenericAccount, "example.com", setOf(CredentialFieldId.Cookie), emptySet())
            val previous = f.grantStore.find("test.extension", "session")!!
            f.grantStore.putOrReplace(previous.copy(requestContract = legacy))
            assertError(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED) { f.commit(a) }
        }
    }

    @Test fun duplicateBearerDeclarationIsRejected() {
        val fields = """[{"id":"oauth.one","type":"token","required":true,"injection":"bearer"},{"id":"oauth.two","type":"token","required":true,"injection":"bearer"}]"""
        assertTrue(runCatching { Fixture(fields) }.isFailure)
    }

    private fun assertError(expected: CredentialResolutionError, action: () -> Any): CredentialResolutionException {
        val error = runCatching(action).exceptionOrNull() as CredentialResolutionException
        assertEquals(expected, error.code)
        return error
    }
    companion object {
        const val defaultFields = """[{"id":"cookie.b","type":"cookie","required":true,"injection":"cookie","cookieName":"b"},{"id":"cookie.a","type":"cookie","required":true,"injection":"cookie","cookieName":"a"},{"id":"oauth.token","type":"token","required":false,"injection":"bearer"}]"""
    }
}
