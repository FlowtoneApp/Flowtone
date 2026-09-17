package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityId
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityStatus
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.data.online.permission.NetworkHostScope
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionManifestV2ParserTest {
    @Test
    fun v1IsRejectedWithDeprecatedFormatMessage() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(v1Manifest())
        }

        assertTrue(error.message.orEmpty().contains("已经废弃"))
    }

    @Test
    fun v2ParsesCanonicalCapabilitiesConfigurationCredentialsAndOrigins() {
        val descriptor = ExtensionManifestParser.parseNormalized(v2Manifest())

        assertEquals(2, descriptor.manifest.formatVersion)
        assertEquals(
            setOf(
                AtomicCapabilityId.SearchSongPage,
                AtomicCapabilityId.PlaybackResourceResolve,
                AtomicCapabilityId.PlaylistSongsRead
            ),
            descriptor.canonicalCapabilities.values
        )
        assertEquals(listOf("future.discovery.mode"), descriptor.unknownAtomicCapabilityIds)
        assertEquals(4, descriptor.configurationSchema.fields.size)
        assertEquals(ConfigurationFieldType.Url, descriptor.configurationSchema.fields[0].type)
        assertEquals(ConfigurationFieldType.Secret, descriptor.configurationSchema.fields[2].type)
        assertEquals(
            ConfigurationValue.StringValue("high"),
            descriptor.configurationSchema.fields[3].defaultValue
        )
        assertEquals(1, descriptor.configurationSchema.actions.size)
        assertEquals(CredentialType.WebDav, descriptor.credentialRequests.single().credentialType)
        assertEquals(3, descriptor.networkPermissions.size)
        assertTrue(descriptor.networkPermissions.any { it.security == NetworkSecurity.Insecure })
        assertTrue(descriptor.networkPermissions.any { it.hostScope == NetworkHostScope.SubdomainsOnly })
    }

    @Test
    fun unknownAtomicIdIsRetainedForDiagnosticsButGrantsNoKnownCapability() {
        val descriptor = ExtensionManifestParser.parseNormalized(
            v2Manifest(capabilities = listOf("future.unknown.capability"))
        )

        assertTrue(descriptor.canonicalCapabilities.values.isEmpty())
        assertEquals(listOf("future.unknown.capability"), descriptor.unknownAtomicCapabilityIds)
        val preview = ExtensionInstallPreviewBuilder.fromDescriptor(descriptor)
        assertTrue(preview.summaryCapabilities.all { it.status == SummaryCapabilityStatus.Unsupported })
    }

    @Test
    fun v2LegacyCapabilityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(v2Manifest(capabilities = listOf("music_provider")))
        }
    }

    @Test
    fun secretDefaultIsRejectedThroughSharedSchemaValidator() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(
                v2Manifest(
                    fields = """
                        [{"id":"password","type":"secret","label":"密码","defaultValue":"bad"}]
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun invalidChoiceAndDuplicateFieldIdAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(
                v2Manifest(
                    fields = """
                        [{"id":"quality","type":"choice","label":"品质","choices":[],"defaultValue":"x"}]
                    """.trimIndent()
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(
                v2Manifest(
                    fields = """
                        [
                          {"id":"server","type":"text","label":"服务器"},
                          {"id":"server","type":"url","label":"地址"}
                        ]
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun unknownCredentialTypeIsRejectedInsteadOfSilentlyIgnored() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(
                v2Manifest(
                    credentialRequests = """
                        [{"id":"future","type":"oauth2","label":"未来凭证","required":true}]
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun v2RejectsLegacyNetworkHostsKey() {
        val manifest = v2Manifest().replace(
            "\"origins\":[",
            "\"hosts\":["
        )

        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(manifest)
        }
    }

    @Test
    fun v2PreviewUsesCanonicalSummary() {
        val preview = ExtensionInstallPreviewBuilder.fromDescriptor(
            ExtensionManifestParser.parseNormalized(v2Manifest())
        )

        assertEquals(
            SummaryCapabilityStatus.Supported,
            preview.summaryCapabilities.single { it.id == SummaryCapabilityId.Playback }.status
        )
        assertTrue(preview.requiresConfiguration)
        assertEquals(listOf("WebDAV 地址", "用户名", "密码"),
            preview.configurationSummary.requiredFields.map { it.label })
    }

    @Test
    fun configurationSummaryIsHostBounded() {
        val descriptor = ExtensionManifestParser.parseNormalized(
            v2Manifest(
                fields = """
                    [
                      {"id":"one","type":"text","label":"一","required":true},
                      {"id":"two","type":"text","label":"二","required":true},
                      {"id":"three","type":"text","label":"三","required":true},
                      {"id":"four","type":"text","label":"四","required":true}
                    ]
                """.trimIndent()
            )
        )

        val summary = ExtensionInstallPreviewBuilder.fromDescriptor(descriptor).configurationSummary

        assertEquals(listOf("一", "二", "三"), summary.requiredFields.map { it.label })
        assertEquals(1, summary.omittedFieldCount)
    }

    private fun v1Manifest() = """
        {
          "formatVersion": 1,
          "id": "example.v1",
          "name": "V1 Example",
          "version": "1.0",
          "author": "Test",
          "entry": "main.js",
          "capabilities": ["search.song.page"],
          "permissions": {"network":{"hosts":["example.com"]}}
        }
    """.trimIndent()

    private fun v2Manifest(
        capabilities: List<String> = listOf(
            "search.song.page",
            "playback.resource.resolve",
            "playlist.songs.read",
            "future.discovery.mode"
        ),
        fields: String = """
            [
              {"id":"server","type":"url","label":"WebDAV 地址","required":true},
              {"id":"username","type":"text","label":"用户名","required":true},
              {"id":"password","type":"secret","label":"密码","required":true},
              {"id":"quality","type":"choice","label":"品质","defaultValue":"high",
               "choices":[{"value":"high","label":"高品质"},{"value":"lossless","label":"无损"}]}
            ]
        """.trimIndent(),
        credentialRequests: String = """
            [{"id":"webdav","type":"webdav","label":"WebDAV 凭证","required":false}]
        """.trimIndent()
    ): String {
        val capabilityJson = capabilities.joinToString(",") { "\"$it\"" }
        return """
            {
              "formatVersion": 2,
              "id": "example.v2",
              "name": "V2 Example",
              "version": "2.0",
              "author": "Test",
              "description": "Example extension",
              "entry": "main.js",
              "capabilities": [$capabilityJson],
              "configuration": {
                "fields": $fields,
                "actions": [{"id":"testConnection","label":"测试连接","requiresValidConfig":true}]
              },
              "credentialRequests": $credentialRequests,
              "permissions": {"network":{"origins":[
                "https://api.example.com",
                "https://*.example.com:8443",
                "http://192.168.1.20:8080"
              ]}},
              "musicSources": ["music.example.com"],
              "color": "#112233",
              "icon": "assets/icon.svg",
              "iconColor": "#445566"
            }
        """.trimIndent()
    }
}
