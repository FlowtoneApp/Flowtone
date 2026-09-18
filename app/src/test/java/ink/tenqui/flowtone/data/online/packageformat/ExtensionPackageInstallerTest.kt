package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.permission.ExtensionNetworkAccessPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPackageInstallerTest {
    @Test
    fun manifestV2ParsesCanonicalDeclarations() {
        val descriptor = ExtensionManifestParser.parseNormalized(
            manifest(
                capabilities = "\"search.song.page\",\"catalog.songs.list\"",
                musicSources = "\"service.example\"",
                color = "#1A73E8"
            )
        )

        assertEquals(2, descriptor.manifest.formatVersion)
        assertEquals(listOf("service.example"), descriptor.manifest.musicSources)
        assertEquals("#1A73E8", descriptor.manifest.color)
        assertEquals(
            setOf(AtomicCapabilityId.SearchSongPage, AtomicCapabilityId.CatalogSongsList),
            descriptor.canonicalCapabilities.values
        )
        assertEquals("https://example.com", descriptor.networkPermissions.single().toString())
    }

    @Test
    fun formatVersionOneIsRejectedWithDeprecatedFormatMessage() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(manifest(formatVersion = 1))
        }

        assertTrue(error.message.orEmpty().contains("已经废弃"))
    }

    @Test
    fun unknownFormatVersionIsRejectedSeparately() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parseNormalized(manifest(formatVersion = 3))
        }

        assertTrue(error.message.orEmpty().contains("不支持的扩展包版本"))
    }

    @Test
    fun manifestRejectsAmbiguousProviderSelectorColors() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionManifestParser.parse(manifest(color = "rgba(1,2,3,0.5)"))
        }
    }

    @Test
    fun validFlowtoneAndZipCanInstall() {
        listOf("test.flowtone", "test.zip").forEach { fileName ->
            val root = Files.createTempDirectory("flowtone-test").toFile()
            try {
                val installed = ExtensionPackageInstaller(root).install(fileName, zip(validEntries()))
                assertEquals("example.provider", installed.manifest.id)
                assertEquals(2, installed.descriptor.manifest.formatVersion)
                assertTrue(installed.directory.resolve("main.js").isFile)
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun replacementScanAndUninstallReflectCurrentDiskState() {
        val root = Files.createTempDirectory("flowtone-reload-test").toFile()
        try {
            val installer = ExtensionPackageInstaller(root)
            installer.install("test.flowtone", zip(validEntries(main = "globalThis.version='old';")))
            installer.install("test.flowtone", zip(validEntries(main = "globalThis.version='new';")))

            val afterUpdate = installer.scan()
            assertEquals(1, afterUpdate.size)
            assertEquals(
                "globalThis.version='new';",
                afterUpdate.single().directory.resolve("main.js").readText()
            )
            assertTrue(installer.uninstall("example.provider"))
            assertTrue(installer.scan().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun replacementDescriptorImmediatelyRevokesRemovedOriginFromRuntimePolicy() {
        val root = Files.createTempDirectory("flowtone-permission-update-test").toFile()
        try {
            val installer = ExtensionPackageInstaller(root)
            installer.install(
                "old.flowtone",
                zip(validEntries(manifest = manifest(origins = listOf("https://old.example.com"))))
            )
            val oldPolicy = ExtensionNetworkAccessPolicy(
                installer.scan().single().descriptor.networkPermissions
            )
            oldPolicy.requireAllowed("https://old.example.com/data")

            installer.install(
                "new.flowtone",
                zip(validEntries(manifest = manifest(origins = emptyList())))
            )
            val reloadedPolicy = ExtensionNetworkAccessPolicy(
                installer.scan().single().descriptor.networkPermissions
            )

            assertThrows(SecurityException::class.java) {
                reloadedPolicy.requireAllowed("https://old.example.com/data")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingFilesDeprecatedFormatInvalidIdAndBrokenZipAreRejected() {
        val cases = listOf(
            zip(mapOf("main.js" to "x")),
            zip(mapOf("manifest.json" to manifest())),
            zip(validEntries(manifest = manifest(formatVersion = 1))),
            zip(validEntries(manifest = manifest(id = "../bad"))),
            ByteArrayInputStream("not zip".encodeToByteArray())
        )
        cases.forEach { input ->
            val root = Files.createTempDirectory("flowtone-test").toFile()
            try {
                assertThrows(Exception::class.java) {
                    ExtensionPackageInstaller(root).install("test.flowtone", input)
                }
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun zipSlipAndOversizedExtractionAreRejected() {
        val root = Files.createTempDirectory("flowtone-test").toFile()
        try {
            assertThrows(Exception::class.java) {
                ExtensionPackageInstaller(root).install(
                    "test.flowtone",
                    zip(validEntries() + ("../escape" to "bad"))
                )
            }
            val huge = "x".repeat((ExtensionPackageInstaller.Limits.MaxMainBytes + 1).toInt())
            assertThrows(Exception::class.java) {
                ExtensionPackageInstaller(root).install(
                    "test.flowtone",
                    zip(validEntries(main = huge))
                )
            }
            val expanded = validEntries().toMutableMap().apply {
                repeat(6) { index -> put("assets/$index.txt", "x".repeat(2 * 1024 * 1024)) }
            }
            assertThrows(Exception::class.java) {
                ExtensionPackageInstaller(root).install("test.flowtone", zip(expanded))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun validEntries(
        manifest: String = manifest(),
        main: String = "globalThis.flowtoneExtension={};"
    ) = mapOf("manifest.json" to manifest, "main.js" to main)

    private fun manifest(
        formatVersion: Int = 2,
        id: String = "example.provider",
        capabilities: String = "\"artist.avatar.lookup\"",
        musicSources: String? = null,
        color: String? = null,
        origins: List<String> = listOf("https://example.com")
    ) = """
        {"formatVersion":$formatVersion,"id":"$id","name":"Example","version":"1","author":"Test",
        "entry":"main.js","capabilities":[$capabilities]${musicSources?.let { ",\"musicSources\":[$it]" }.orEmpty()}${color?.let { ",\"color\":\"$it\"" }.orEmpty()},"permissions":{"network":{"origins":[${origins.joinToString(",") { "\"$it\"" }}]}}}
    """.trimIndent()

    private fun zip(entries: Map<String, String>): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
