package ink.tenqui.flowtone.data.online.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.app.OAuthCallbackActivity
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageInstaller
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.credential.CredentialSourceRepository
import ink.tenqui.flowtone.data.online.credential.CredentialSourceInput
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.data.online.credential.CredentialSecretState
import ink.tenqui.flowtone.data.online.credential.NamedSecretField
import ink.tenqui.flowtone.data.online.credential.NamedSecretType
import ink.tenqui.flowtone.data.online.credential.CredentialSecretReadResult
import ink.tenqui.flowtone.data.online.credential.CredentialGrantStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OAuthBrowserCallbackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val installer = ExtensionPackageInstaller(context.filesDir.resolve("extensions"))
    private var installedByTest = false
    private var testAccountId: String? = null

    @After fun cleanUp() {
        OAuthBrowserHost.cancel()
        testAccountId?.let { CredentialSourceRepository.from(context).delete(it) }
        if (installedByTest) installer.uninstall("flowtone.oauth.test")
    }

    @Test fun debugBrowserAuthorizationReachesCallbackOnce() = runBlocking {
        val extension = installFixture()
        val accountId = createAccount()
        val method = extension.descriptor.credentialAcquisition.single()
        val url = OAuthBrowserHost.prepare(context, extension.manifest.id, extension.installationInstanceId!!, accountId, method)
        val page = java.net.URL(url).openConnection().getInputStream().bufferedReader().use { it.readText() }
        val callback = Regex("href=\"([^\"]+)\"").find(page)!!.groupValues[1].replace("&amp;", "&")
        val intent = Intent(context, OAuthCallbackActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(callback))
        ActivityScenario.launch<OAuthCallbackActivity>(intent).close()
        withTimeout(10_000) {
            while (OAuthBrowserHost.status.value.phase !in setOf(OAuthUiPhase.SAVED, OAuthUiPhase.FAILED)) delay(50)
        }
        assertEquals(OAuthUiPhase.SAVED, OAuthBrowserHost.status.value.phase)
        assertEquals(CredentialSecretState.Configured, CredentialSourceRepository.from(context).namedSecretState(accountId, NamedSecretField("oauth.access_token", NamedSecretType.Token)))
        val stored = CredentialSourceRepository.from(context).readNamedSecret(accountId, NamedSecretField("oauth.access_token", NamedSecretType.Token))
        assertEquals("debug-fixture-token", (stored as CredentialSecretReadResult.Available).plaintext)
        assertEquals(0, CredentialGrantStore.from(context).grantsForExtension(extension.manifest.id).size)
        assertFalse(OAuthBrowserHost.status.value.toString().contains("debug-fixture-token"))
        OAuthBrowserHost.accept(context, callback)
        assertEquals(OAuthUiPhase.SAVED, OAuthBrowserHost.status.value.phase)
    }

    @Test fun changedExtensionRequestRejectsCallbackBeforeExchange() {
        val extension = installFixture()
        val accountId = createAccount()
        val method = extension.descriptor.credentialAcquisition.single()
        val url = OAuthBrowserHost.prepare(context, extension.manifest.id, extension.installationInstanceId!!, accountId, method)
        val state = Uri.parse(url).getQueryParameter("state")!!
        val file = extension.directory.resolve("manifest.json")
        file.writeText(file.readText().replace("本机模拟服务，不连接真实账户", "Changed"))
        OAuthBrowserHost.accept(context, "ink.tenqui.flowtone.oauth://callback/complete?state=$state&code=test")
        assertEquals(OAuthUiPhase.FAILED, OAuthBrowserHost.status.value.phase)
        assertEquals(OAuthFailure.SESSION_INVALID.name, OAuthBrowserHost.status.value.error)
    }

    private fun installFixture(): InstalledExtension {
        assumeTrue(ink.tenqui.flowtone.BuildConfig.DEBUG)
        assumeTrue(installer.scan().none { it.manifest.id == "flowtone.oauth.test" })
        val zip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { archive ->
                for (name in listOf("manifest.json", "main.js")) {
                    archive.putNextEntry(ZipEntry(name))
                    context.assets.open("oauth-fixture/$name").use { it.copyTo(archive) }
                    archive.closeEntry()
                }
            }
        }.toByteArray()
        val extension = installer.install("test.flowtone", ByteArrayInputStream(zip))
        installedByTest = true
        return extension
    }

    private fun createAccount(): String {
        val source = CredentialSourceRepository.from(context).save(null, CredentialSourceInput(CredentialType.GenericAccount, "OAuth test", "example.test"), emptyMap())
        testAccountId = source.id
        return source.id
    }
}
