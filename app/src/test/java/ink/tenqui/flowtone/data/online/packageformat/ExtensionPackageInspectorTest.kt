package ink.tenqui.flowtone.data.online.packageformat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPackageInspectorTest {
    @Test
    fun inspectBuildsPreviewWithoutWritingExtensionsOrReplacingInstalledFiles() = withRoots { root ->
        val previewRoot = root.resolve("cache/previews")
        val extensionsRoot = root.resolve("files/extensions")
        val installedDirectory = extensionsRoot.resolve("example.extension").apply { mkdirs() }
        val marker = installedDirectory.resolve("marker.txt").apply { writeText("unchanged") }
        val existing = ExtensionManifestParser.parseNormalized(
            manifest(version = "1", origins = listOf("https://old.example.com"))
        )

        val preview = ExtensionPackageInspector(previewRoot).inspect(
            fileName = "example.flowtone",
            source = zipPackage(
                manifest(
                    version = "2",
                    origins = listOf("https://new.example.com")
                )
            ),
            installedExtensions = listOf(existing)
        )

        assertTrue(preview.isUpdate)
        assertEquals("1", preview.existingInstallation?.identity?.version)
        assertNotNull(preview.snapshotHandle)
        assertEquals("unchanged", marker.readText())
        assertEquals(listOf("marker.txt"), installedDirectory.list()?.toList())
        assertEquals(setOf("https://new.example.com"),
            preview.updateDiff?.addedNetworkPermissions?.mapTo(mutableSetOf(), Any::toString))
        assertEquals(setOf("https://old.example.com"),
            preview.updateDiff?.removedNetworkPermissions?.mapTo(mutableSetOf(), Any::toString))
    }

    @Test
    fun inspectNewIdDoesNotCreateExtensionsDirectory() = withRoots { root ->
        val extensionsRoot = root.resolve("files/extensions")

        val preview = ExtensionPackageInspector(root.resolve("cache/previews")).inspect(
            "example.flowtone",
            zipPackage(manifest())
        )

        assertFalse(preview.isUpdate)
        assertFalse(extensionsRoot.exists())
        assertTrue(preview.summaryCapabilities.isNotEmpty())
    }

    @Test
    fun inspectDetectsHttpsToHttpDowngrade() = withRoots { root ->
        val existing = ExtensionManifestParser.parseNormalized(
            manifest(origins = listOf("https://example.com"))
        )

        val preview = ExtensionPackageInspector(root.resolve("previews")).inspect(
            "example.flowtone",
            zipPackage(manifest(version = "2", origins = listOf("http://example.com"))),
            listOf(existing)
        )

        val diff = requireNotNull(preview.updateDiff)
        assertEquals(setOf("http://example.com"), diff.addedNetworkPermissions.mapTo(mutableSetOf(), Any::toString))
        assertEquals(setOf("https://example.com"), diff.removedNetworkPermissions.mapTo(mutableSetOf(), Any::toString))
        assertTrue(diff.hasSecurityDowngrade)
    }

    @Test
    fun sameIdIsUpdateEvenWhenIncomingVersionIsLower() = withRoots { root ->
        val existing = ExtensionManifestParser.parseNormalized(manifest(version = "2"))

        val preview = ExtensionPackageInspector(root.resolve("previews")).inspect(
            "example.flowtone",
            zipPackage(manifest(version = "1")),
            listOf(existing)
        )

        assertTrue(preview.isUpdate)
        assertEquals("1", preview.identity.version)
        assertEquals("2", preview.existingInstallation?.identity?.version)
    }

    @Test
    fun inspectDetectsPermissionRemoval() = withRoots { root ->
        val existing = ExtensionManifestParser.parseNormalized(
            manifest(origins = listOf("https://old.example.com"))
        )

        val preview = ExtensionPackageInspector(root.resolve("previews")).inspect(
            "example.flowtone",
            zipPackage(manifest(version = "2", origins = emptyList())),
            listOf(existing)
        )

        assertEquals(
            setOf("https://old.example.com"),
            preview.updateDiff?.removedNetworkPermissions?.mapTo(mutableSetOf(), Any::toString)
        )
        assertTrue(requireNotNull(preview.updateDiff).hasRemovedPermissions)
    }

    @Test
    fun invalidPackageLeavesNoSnapshotStaging() = withRoots { root ->
        val previewRoot = root.resolve("previews")

        assertThrows(Exception::class.java) {
            ExtensionPackageInspector(previewRoot).inspect(
                "bad.flowtone",
                ByteArrayInputStream("not a zip".encodeToByteArray())
            )
        }

        assertTrue(previewRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun confirmedConsumptionUsesSnapshotInsteadOfChangedSourceBytes() = withRoots { root ->
        val inspector = ExtensionPackageInspector(root.resolve("previews"))
        var sourceBytes = zipPackageBytes(manifest(version = "seen"))
        val preview = inspector.inspect(
            "example.flowtone",
            ByteArrayInputStream(sourceBytes)
        )
        sourceBytes = zipPackageBytes(manifest(version = "changed"))
        assertTrue(sourceBytes.isNotEmpty())

        val installed = inspector.consumeSnapshot(requireNotNull(preview.snapshotHandle)) { name, input ->
            ExtensionPackageInstaller(root.resolve("extensions")).install(name, input)
        }

        assertEquals("seen", installed.manifest.version)
        assertFalse(inspector.hasSnapshot(requireNotNull(preview.snapshotHandle)))
    }

    @Test
    fun discardedOrExpiredSnapshotCannotBeConsumed() = withRoots { root ->
        var now = 1_000_000L
        val inspector = ExtensionPackageInspector(root.resolve("previews")) { now }
        val discarded = inspector.inspect("a.flowtone", zipPackage(manifest()))
        inspector.discard(requireNotNull(discarded.snapshotHandle))
        val discardedError = assertThrows(ExtensionInstallPreviewUnavailableException::class.java) {
            inspector.consumeSnapshot(requireNotNull(discarded.snapshotHandle)) { _, _ -> Unit }
        }
        assertEquals("安装预览已失效，请重新选择扩展包。", discardedError.message)

        val expired = inspector.inspect("b.flowtone", zipPackage(manifest()))
        now += ExtensionPackageInspector.SnapshotTtlMillis + 1L
        assertThrows(ExtensionInstallPreviewUnavailableException::class.java) {
            inspector.consumeSnapshot(requireNotNull(expired.snapshotHandle)) { _, _ -> Unit }
        }
        assertFalse(inspector.hasSnapshot(requireNotNull(expired.snapshotHandle)))
    }

    @Test
    fun modifiedSnapshotIsRejectedByDigest() = withRoots { root ->
        val previewRoot = root.resolve("previews")
        val inspector = ExtensionPackageInspector(previewRoot)
        val preview = inspector.inspect("example.flowtone", zipPackage(manifest()))
        val handle = requireNotNull(preview.snapshotHandle)
        previewRoot.resolve(handle.token).resolve("package.flowtone").appendText("tampered")

        assertThrows(ExtensionInstallPreviewUnavailableException::class.java) {
            inspector.consumeSnapshot(handle) { _, _ -> Unit }
        }
        assertFalse(inspector.hasSnapshot(handle))
    }

    @Test
    fun newInspectorCleansOrphanedPreviewDirectoryAfterProcessRestart() = withRoots { root ->
        val previewRoot = root.resolve("previews").apply { mkdirs() }
        val orphan = previewRoot.resolve("orphan").apply { mkdirs() }
        orphan.resolve("package.flowtone").writeText("stale")

        ExtensionPackageInspector(previewRoot)

        assertFalse(orphan.exists())
    }

    private fun manifest(
        version: String = "1",
        origins: List<String> = listOf("https://example.com")
    ): String {
        val originsJson = origins.joinToString(",") { "\"$it\"" }
        return """
            {
              "formatVersion":2,
              "id":"example.extension",
              "name":"Example",
              "version":"$version",
              "author":"Test",
              "description":"Description",
              "entry":"main.js",
              "capabilities":["search.song.page","playback.resource.resolve"],
              "configuration":{"fields":[
                {"id":"server","type":"url","label":"服务器地址","required":true}
              ],"actions":[]},
              "credentialRequests":[],
              "permissions":{"network":{"origins":[$originsJson]}}
            }
        """.trimIndent()
    }

    private fun zipPackage(manifest: String): ByteArrayInputStream =
        ByteArrayInputStream(zipPackageBytes(manifest))

    private fun zipPackageBytes(manifest: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            mapOf(
                "manifest.json" to manifest,
                "main.js" to "globalThis.flowtoneExtension={};"
            ).forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun withRoots(block: (java.io.File) -> Unit) {
        val root = Files.createTempDirectory("flowtone-inspector-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
