package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreviewBuilder
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageSnapshotHandle
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionInstallPresentationTest {
    @Test
    fun openDocumentInspectionProducesPendingPreview() = runBlocking {
        val expected = preview(version = "1")

        val result = inspectReplacingExtensionPreview(
            current = null,
            discard = {},
            inspect = { expected }
        )

        assertSame(expected, result.getOrThrow())
    }

    @Test
    fun inspectionFailureProducesNoPendingPreview() = runBlocking {
        val result = inspectReplacingExtensionPreview(
            current = null,
            discard = {},
            inspect = { error("broken package") }
        )

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }

    @Test
    fun cancelDiscardsPendingSnapshot() {
        val preview = preview(version = "1")
        var discarded: ExtensionPackageSnapshotHandle? = null

        discardExtensionPreview(preview) { discarded = it }

        assertEquals(preview.snapshotHandle, discarded)
    }

    @Test
    fun replacingPendingPreviewDiscardsPreviousBeforeInspectingNext() = runBlocking {
        val first = preview(version = "1")
        val second = preview(version = "2")
        val events = mutableListOf<String>()

        val result = inspectReplacingExtensionPreview(
            current = first,
            discard = { events += "discard:${it.token}" },
            inspect = {
                events += "inspect"
                second
            }
        )

        assertSame(second, result.getOrThrow())
        assertEquals(
            listOf("discard:${first.snapshotHandle?.token}", "inspect"),
            events
        )
    }

    @Test
    fun confirmConsumesOnlyPreviewSnapshotHandle() = runBlocking {
        val preview = preview(version = "1")
        var consumed: ExtensionPackageSnapshotHandle? = null
        val directory = Files.createTempDirectory("extension-confirm-test").toFile()
        try {
            val result = confirmExtensionPreview(preview) { handle ->
                consumed = handle
                InstalledExtension(preview.incoming, directory, runtimeAvailable = true)
            }

            assertTrue(result.isSuccess)
            assertEquals(preview.snapshotHandle, consumed)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedSnapshotInstallReturnsFailureForCallerToClearPendingState() = runBlocking {
        val result = confirmExtensionPreview(preview(version = "1")) {
            error("安装预览已失效，请重新选择扩展包。")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("已失效"))
    }

    @Test
    fun updatePresentationUsesUpdateTitleVersionTransitionAndHidesEmptyDiff() {
        val existing = descriptor(version = "1")
        val incoming = descriptor(version = "2")
        val preview = ExtensionInstallPreviewBuilder.fromDescriptor(
            incoming = incoming,
            existingInstallation = existing,
            snapshotHandle = handle("update")
        )

        val presentation = extensionInstallOverlayPresentation(preview)

        assertEquals("更新扩展？", presentation.title)
        assertEquals("更新", presentation.actionLabel)
        assertEquals("1 → 2", presentation.versionLine)
        assertFalse(presentation.hasUpdateChanges)
    }

    @Test
    fun updateDiffUsesHostLabelsAndSeparatesAddedRemovedAndDowngrade() {
        val existing = descriptor(
            version = "1",
            capabilities = listOf("search.song.page", "playback.resource.resolve"),
            origins = listOf("https://old.example.com", "https://example.com"),
            credentialRequests = webDavCredential("legacyCredential", "旧 WebDAV 凭证"),
            fields = requiredField("legacyServer", "旧服务器地址")
        )
        val incoming = descriptor(
            version = "2",
            capabilities = listOf("search.album.page", "playback.resource.resolve"),
            origins = listOf("https://new.example.com", "http://example.com"),
            credentialRequests = webDavCredential("newCredential", "新 WebDAV 凭证"),
            fields = requiredField("newServer", "新服务器地址")
        )
        val presentation = extensionInstallOverlayPresentation(
            ExtensionInstallPreviewBuilder.fromDescriptor(incoming, existing, handle("diff"))
        )

        assertEquals(listOf("搜索专辑"), presentation.added.capabilities)
        assertEquals(listOf("搜索歌曲"), presentation.removed.capabilities)
        assertEquals(listOf("https://new.example.com"), presentation.added.networkPermissions)
        assertEquals(listOf("https://old.example.com"), presentation.removed.networkPermissions)
        assertEquals(listOf("新 WebDAV 凭证"), presentation.added.credentialRequests)
        assertEquals(listOf("旧 WebDAV 凭证"), presentation.removed.credentialRequests)
        assertEquals(listOf("新服务器地址"), presentation.added.configurationFields)
        assertEquals(listOf("旧服务器地址"), presentation.removed.configurationFields)
        assertEquals(1, presentation.securityDowngrades.size)
        assertEquals("https://example.com", presentation.securityDowngrades.single().previousOrigin)
        assertEquals("http://example.com", presentation.securityDowngrades.single().incomingOrigin)
        assertTrue(presentation.hasInsecureNetworkPermissions)
        assertTrue(presentation.hasUpdateChanges)
    }

    @Test
    fun requiredConfigurationSummaryIsBoundedAndHttpIsMarkedInsecure() {
        val preview = ExtensionInstallPreviewBuilder.fromDescriptor(
            incoming = descriptor(
                version = "1",
                origins = listOf("http://example.com"),
                fields = listOf("一", "二", "三", "四").mapIndexed { index, label ->
                    "{\"id\":\"field$index\",\"type\":\"text\",\"label\":\"$label\",\"required\":true}"
                }.joinToString(prefix = "[", postfix = "]")
            ),
            snapshotHandle = handle("http")
        )

        val presentation = extensionInstallOverlayPresentation(preview)

        assertEquals("一、二、三 等 4 项", presentation.configurationSummary)
        assertTrue(presentation.hasInsecureNetworkPermissions)
    }

    private fun preview(version: String): ExtensionInstallPreview =
        ExtensionInstallPreviewBuilder.fromDescriptor(
            incoming = descriptor(version),
            snapshotHandle = handle(version)
        )

    private fun descriptor(
        version: String,
        capabilities: List<String> = listOf("search.song.page", "playback.resource.resolve"),
        origins: List<String> = listOf("https://example.com"),
        credentialRequests: String = "[]",
        fields: String = "[]"
    ) = ExtensionManifestParser.parseNormalized(
        """
            {
              "formatVersion":2,
              "id":"example.extension",
              "name":"Example Extension",
              "version":"$version",
              "author":"Flowtone",
              "description":"Description",
              "entry":"main.js",
              "capabilities":[${capabilities.joinToString(",") { "\"$it\"" }}],
              "configuration":{"fields":$fields,"actions":[]},
              "credentialRequests":$credentialRequests,
              "permissions":{"network":{"origins":[${origins.joinToString(",") { "\"$it\"" }}]}}
            }
        """.trimIndent()
    )

    private fun requiredField(id: String, label: String) =
        "[{\"id\":\"$id\",\"type\":\"url\",\"label\":\"$label\",\"required\":true}]"

    private fun webDavCredential(id: String, label: String) =
        "[{\"id\":\"$id\",\"type\":\"webdav\",\"label\":\"$label\",\"required\":false}]"

    private fun handle(seed: String) = ExtensionPackageSnapshotHandle(
        token = "00000000-0000-0000-0000-${seed.hashCode().toUInt().toString(16).padStart(12, '0').takeLast(12)}",
        sha256 = seed.padEnd(64, '0').take(64)
    )
}
