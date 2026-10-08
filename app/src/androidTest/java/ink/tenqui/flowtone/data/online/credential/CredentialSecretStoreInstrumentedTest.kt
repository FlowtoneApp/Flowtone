package ink.tenqui.flowtone.data.online.credential

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.security.KeyStore
import javax.crypto.SecretKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialSecretStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun androidKeystoreKeyAndNoBackupCiphertextSurviveStoreRecreation() {
        val sourceId = "cs_keystore-test123"
        val testSecret = "instrumented-test-secret"
        val first = CredentialSecretStore.from(context)
        first.put(sourceId, CredentialFieldId.Password, testSecret)

        val recreated = CredentialSecretStore.from(context)
        val result = recreated.get(sourceId, CredentialFieldId.Password)
        assertTrue(result is CredentialSecretReadResult.Available && result.plaintext == testSecret)
        assertTrue(recreated.state(sourceId, CredentialFieldId.Password) == CredentialSecretState.Configured)
        assertTrue(context.noBackupFilesDir.resolve("credential-secrets/$sourceId/password.secret").isFile)

        val androidKeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(androidKeyStore.containsAlias(CredentialSecretStore.KeyAlias))
        assertTrue(androidKeyStore.getKey(CredentialSecretStore.KeyAlias, null) is SecretKey)
        assertFalse(context.noBackupFilesDir.resolve("credential-secrets")
            .walkTopDown().filter { it.isFile }.any { it.name.contains("key", ignoreCase = true) })

        recreated.deleteAll(sourceId)
    }
}
