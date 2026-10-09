package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.credential.CredentialGrantEvaluation
import ink.tenqui.flowtone.data.online.credential.CredentialSourceMatch
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ink.tenqui.flowtone.app.SecondaryDestination
import ink.tenqui.flowtone.app.SecondaryPage
import ink.tenqui.flowtone.app.SecondaryStackEntry
import ink.tenqui.flowtone.app.secondaryStackBreadcrumbs

class ExtensionSettingsPresentationTest {
    @Test
    fun settingsPathRestoresItsNamedSection() {
        assertEquals(
            SettingsSection.General,
            settingsSectionForPathSegments(listOf("通用"))
        )
        assertEquals(null, settingsSectionForPathSegments(emptyList()))
    }

    @Test
    fun everyBackSourceCanUseTheSameCleanAndDirtyGuardDecision() {
        val baseline = mapOf("text" to ink.tenqui.flowtone.data.online.configuration.ConfigurationValue.StringValue("saved"))
        val dirty = baseline + ("text" to ink.tenqui.flowtone.data.online.configuration.ConfigurationValue.StringValue("draft"))

        listOf("topBar", "androidBack", "edgeSwipe").forEach {
            assertEquals(ExtensionSettingsBackResult.PerformBack, extensionSettingsBackResult(baseline, baseline))
            assertEquals(ExtensionSettingsBackResult.ConfirmDiscard, extensionSettingsBackResult(dirty, baseline))
        }
    }

    @Test
    fun settingsUsesItsActualSecondaryStackForStandardBreadcrumbs() {
        val installed = installed()
        val breadcrumbs = secondaryStackBreadcrumbs(
            entries = listOf(
                SecondaryStackEntry(0, SecondaryDestination.Standard(SecondaryPage.Settings)),
                SecondaryStackEntry(1, SecondaryDestination.Standard(SecondaryPage.OnlineExtensions)),
                SecondaryStackEntry(2, SecondaryDestination.ExtensionSettings(installed)),
                SecondaryStackEntry(3, SecondaryDestination.ExtensionNetworkAccess(emptySet()))
            ),
            settingsPathSegments = listOf("通用")
        )

        assertEquals(
            listOf("设置", "通用", "在线扩展", "SoundCloud", "网络详情"),
            breadcrumbs
        )
    }

    @Test
    fun usesHostCapabilityPresentationAndCanonicalNetworkRuleCount() {
        val installed = installed(
            capabilities = "\"search.song.page\",\"playback.resource.resolve\"",
            origins = "\"https://soundcloud.com\",\"https://*.sndcdn.com\""
        )

        val presentation = extensionSettingsPresentation(installed)

        assertEquals(listOf("搜索与发现", "歌曲播放", "歌手", "专辑", "歌单"), presentation.capabilities.map { it.label })
        assertEquals(2, installed.descriptor.networkPermissions.size)
        assertEquals("此扩展无需配置", presentation.configurationStatus)
        assertTrue(presentation.credentialLabels.isEmpty())
    }

    @Test
    fun configurationAndCredentialStatusReflectSavedMetadata() {
        val installed = installed(
            configuration = "{\"fields\":[{\"id\":\"url\",\"type\":\"text\",\"label\":\"服务器\",\"required\":true}],\"actions\":[]}",
            credentials = "[{\"id\":\"webdav\",\"type\":\"webdav\",\"label\":\"WebDAV 凭证\"}]"
        )

        val presentation = extensionSettingsPresentation(installed)

        assertEquals("尚未配置", presentation.configurationStatus)
        assertEquals(listOf("WebDAV 凭证"), presentation.credentialLabels)
        assertFalse(presentation.configurationStatus == "已配置")
    }

    @Test
    fun unsavedConfigurationDoesNotClaimTheDraftIsConfigured() {
        assertEquals(
            "有未保存的更改",
            extensionConfigurationDisplayStatus("已配置", dirty = true, saving = false, loading = false)
        )
        assertEquals(
            "正在保存…",
            extensionConfigurationDisplayStatus("尚未配置", dirty = true, saving = true, loading = false)
        )
        assertEquals(
            "正在读取配置…",
            extensionConfigurationDisplayStatus("尚未配置", dirty = false, saving = false, loading = true)
        )
    }

    @Test
    fun grantSummarySeparatesAuthorizationFromReadiness() {
        val summary = extensionGrantSummary(
            listOf(
                CredentialGrantEvaluation(null, false),
                CredentialGrantEvaluation(null, true, sourceMatch = CredentialSourceMatch(true, true, true)),
                CredentialGrantEvaluation(null, true)
            )
        )
        assertEquals(3, summary.requestCount)
        assertEquals(2, summary.authorizedCount)
        assertEquals(1, summary.readyCount)
        assertEquals("2 项已授权 · 1 项未授权 · 1 项凭据未就绪", summary.label)
    }

    private fun installed(
        capabilities: String = "\"search.song.page\"",
        origins: String = "",
        configuration: String = "{\"fields\":[],\"actions\":[]}",
        credentials: String = "[]"
    ): InstalledExtension {
        val descriptor = ExtensionManifestParser.parseNormalized(
            """{"formatVersion":2,"id":"example.extension","name":"SoundCloud","version":"1.0","author":"Flowtone","description":"Description","entry":"main.js","capabilities":[$capabilities],"configuration":$configuration,"credentialRequests":$credentials,"permissions":{"network":{"origins":[$origins]}}}"""
        )
        return InstalledExtension(descriptor, Files.createTempDirectory("extension-settings").toFile(), true)
    }
}
