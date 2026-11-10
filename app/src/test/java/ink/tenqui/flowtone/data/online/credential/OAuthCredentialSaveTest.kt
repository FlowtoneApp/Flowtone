package ink.tenqui.flowtone.data.online.credential

import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.nio.file.Files
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class OAuthCredentialSaveTest {
    private class Keys : CredentialSecretKeyProvider {
        var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getExistingKey() = key
        override fun getOrCreateKey() = key ?: error("unavailable")
        override fun recreateKey() = error("unavailable")
    }
    private class Fixture {
        val root = Files.createTempDirectory("oauth-save").toFile()
        val keys = Keys()
        val secrets = CredentialSecretStore(root.resolve("secrets"), keys)
        val grants = CredentialGrantStore(root.resolve("grants"))
        val sources = CredentialSourceStore(root.resolve("sources")) { "cs_oauth-fixture" }
        val repo = CredentialSourceRepository(sources, secrets, grants)
        val source = repo.save(null, CredentialSourceInput(CredentialType.GenericAccount, "Account", "example.test"), emptyMap())
        val descriptor = ExtensionManifestParser.parseNormalized("""{"formatVersion":2,"id":"flowtone.oauth.test","name":"Test","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"credentialAcquisition":[{"methodId":"login","type":"authorization_code_pkce","realm":"example.test","label":"Test","description":"Test","providerProfile":"flowtone-debug-test","outputs":[{"fieldId":"oauth.access_token","type":"token","required":true}],"scopes":[]}],"permissions":{"network":{"origins":[]}}}""")
        val installed = InstalledExtension(descriptor, root, true, UUID.randomUUID().toString())
        val declaration = descriptor.credentialAcquisition.single()
        val binding = OAuthCredentialBinding("flowtone-debug-test", "flowtone-debug-public", "example.test", emptyList())
        val field = NamedSecretField("oauth.access_token", NamedSecretType.Token)
        fun save(value: String = "private-oauth-token", replace: Boolean = false, current: InstalledExtension? = installed,
                 sourceId: String = source.id, contract: CredentialAcquisitionDefinition = declaration,
                 client: OAuthCredentialBinding = binding) = repo.saveOAuthAccessToken(installed.manifest.id, installed.installationInstanceId!!,
            contract, sourceId, client, value, replace) { current }
    }

    @Test fun savesOneEncryptedFieldWithoutGrantOrSecretInMetadata() {
        val f = Fixture(); f.save()
        assertEquals("private-oauth-token", (f.secrets.readNamed(f.source.id, f.field) as CredentialSecretReadResult.Available).plaintext)
        val entry = f.repo.namedSecretFields(f.source.id).single()
        assertEquals(CredentialSecretState.Configured, entry.state)
        assertTrue(entry.oauthRestricted)
        assertTrue(f.grants.grantsForExtension(f.installed.manifest.id).isEmpty())
        assertFalse(f.root.resolve("sources/sources.json").readText().contains("private-oauth-token"))
        assertTrue(f.root.walkTopDown().filter { it.isFile }.none { runCatching { it.readText().contains("private-oauth-token") }.getOrDefault(false) })
        val request = CredentialRequestDefinition("session", CredentialType.GenericAccount, "Session", description = "Test", realm = "example.test",
            named = NamedCredentialRequestContract("https://example.test", setOf(
                NamedCredentialRequestField("oauth.access_token", NamedSecretType.Token, true, CredentialInjection.Bearer))))
        assertTrue(f.repo.matchForExtension(f.source, request, f.installed).fullyReady)
        val other = f.installed.copy(descriptor = f.descriptor.copy(manifest = f.descriptor.manifest.copy(id = "other.extension")))
        assertFalse(f.repo.matchForExtension(f.source, request, other).fullyReady)
        assertTrue(f.repo.matchForExtension(f.source, request, other).restrictedFields.contains("oauth.access_token"))
        val withRequest = f.installed.copy(descriptor = f.descriptor.copy(credentialRequests = listOf(request)))
        val contract = CredentialRequestContractSnapshot.from(request)!!
        val grantRepo = CredentialGrantRepository(f.grants, f.repo)
        val grant = grantRepo.createOrReplace(withRequest, request.id, f.source.id, contract, withRequest.installationInstanceId!!) { withRequest }
        assertEquals(f.source.id, grant.credentialSourceId)
        assertTrue(grantRepo.evaluate(withRequest, request.id).usable)
    }

    @Test fun rejectsStaleAccountContractRealmAndUnconfirmedOrIncompatibleReplacement() {
        val f = Fixture(); f.save()
        assertTrue(runCatching { f.save("new") }.isFailure)
        f.save("new", replace = true)
        assertTrue(runCatching { f.save("other", replace = true, client = f.binding.copy(clientId = "other-client")) }.isFailure)
        assertTrue(runCatching { f.save(current = f.installed.copy(installationInstanceId = UUID.randomUUID().toString()), replace = true) }.isFailure)
        val changed = f.installed.copy(descriptor = f.descriptor.copy(credentialAcquisition = listOf(f.declaration.copy(label = "changed"))))
        assertTrue(runCatching { f.save(current = changed, replace = true) }.isFailure)
        assertTrue(runCatching { f.save(client = f.binding.copy(realm = "other.test"), replace = true) }.isFailure)
        f.sources.update(f.source.id, CredentialSourceInput(CredentialType.GenericAccount, "Account", "other.test"))
        assertTrue(runCatching { f.save(replace = true) }.isFailure)
        f.sources.update(f.source.id, CredentialSourceInput(CredentialType.GenericAccount, "Account", "example.test"))
        assertTrue(f.repo.delete(f.source.id))
        assertTrue(runCatching { f.save(replace = true) }.isFailure)
    }

    @Test fun typeMismatchAndWriteFailureFailClosed() {
        val f = Fixture()
        f.repo.putNamedSecret(f.source.id, f.field.copy(type = NamedSecretType.Cookie), "cookie")
        assertTrue(runCatching { f.save(replace = true) }.isFailure)
        f.repo.deleteNamedSecret(f.source.id, f.field.copy(type = NamedSecretType.Cookie))
        f.keys.key = null
        assertTrue(runCatching { f.save() }.isFailure)
        assertEquals(CredentialSecretState.NotConfigured, f.secrets.namedState(f.source.id, f.field))
        assertTrue(f.secrets.isOAuthRestricted(f.source.id, f.field))
    }

    @Test fun missingProvenanceCannotTurnOAuthTokenIntoUnrestrictedField() {
        val f = Fixture(); f.save()
        assertTrue(f.root.resolve("secrets/${f.source.id}/named-v1/oauth.access_token~Token.oauth").delete())
        assertEquals(CredentialSecretState.Unavailable, f.secrets.namedState(f.source.id, f.field))
        assertTrue(f.secrets.readNamed(f.source.id, f.field) is CredentialSecretReadResult.Unavailable)
    }
}
