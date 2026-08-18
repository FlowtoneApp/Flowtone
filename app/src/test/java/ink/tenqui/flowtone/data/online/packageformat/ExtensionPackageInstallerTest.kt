package ink.tenqui.flowtone.data.online.packageformat

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
    @Test fun `music provider capability can stand alone or coexist with artist avatar`() {
        val musicOnly = ExtensionManifestParser.parse(manifest(capabilities = "\"music_provider\""))
        val combined = ExtensionManifestParser.parse(
            manifest(capabilities = "\"artist_avatar\",\"music_provider\"")
        )

        assertTrue(musicOnly.supportsMusicProvider)
        assertTrue(!musicOnly.supportsArtistAvatar)
        assertTrue(combined.supportsMusicProvider)
        assertTrue(combined.supportsArtistAvatar)
    }

    @Test fun `musicSources 是独立的服务声明 而非网络权限`() {
        val parsed = ExtensionManifestParser.parse(
            manifest(
                capabilities = "\"music_provider\"",
                musicSources = "\"service.example\""
            )
        )

        assertEquals(listOf("service.example"), parsed.musicSources)
        assertEquals(listOf("example.com"), parsed.networkHosts)
    }

    @Test fun `合法 flowtone 和 zip 可以安装`() {
        listOf("test.flowtone", "test.zip").forEach { fileName ->
            val root = Files.createTempDirectory("flowtone-test").toFile()
            val installed = ExtensionPackageInstaller(root).install(fileName, zip(validEntries()))
            assertEquals("example.avatar", installed.manifest.id)
            assertTrue(installed.directory.resolve("main.js").isFile)
            root.deleteRecursively()
        }
    }

    @Test fun `缺文件 不支持版本 非法 id 和损坏 zip 被拒绝`() {
        val cases = listOf(
            zip(mapOf("main.js" to "x")),
            zip(mapOf("manifest.json" to manifest())),
            zip(validEntries(manifest = manifest(formatVersion = 2))),
            zip(validEntries(manifest = manifest(id = "../bad"))),
            ByteArrayInputStream("not zip".encodeToByteArray())
        )
        cases.forEach { input ->
            val root = Files.createTempDirectory("flowtone-test").toFile()
            assertThrows(Exception::class.java) { ExtensionPackageInstaller(root).install("test.flowtone", input) }
            root.deleteRecursively()
        }
    }

    @Test fun `zip slip 和超大解压被拒绝`() {
        val root = Files.createTempDirectory("flowtone-test").toFile()
        assertThrows(Exception::class.java) {
            ExtensionPackageInstaller(root).install("test.flowtone", zip(validEntries() + ("../escape" to "bad")))
        }
        val huge = "x".repeat((ExtensionPackageInstaller.Limits.MaxMainBytes + 1).toInt())
        assertThrows(Exception::class.java) {
            ExtensionPackageInstaller(root).install("test.flowtone", zip(validEntries(main = huge)))
        }
        val expanded = validEntries().toMutableMap().apply {
            repeat(6) { index -> put("assets/$index.txt", "x".repeat(2 * 1024 * 1024)) }
        }
        assertThrows(Exception::class.java) {
            ExtensionPackageInstaller(root).install("test.flowtone", zip(expanded))
        }
        root.deleteRecursively()
    }

    private fun validEntries(manifest: String = manifest(), main: String = "globalThis.flowtoneExtension={};") =
        mapOf("manifest.json" to manifest, "main.js" to main)

    private fun manifest(
        formatVersion: Int = 1,
        id: String = "example.avatar",
        capabilities: String = "\"artist_avatar\"",
        musicSources: String? = null
    ) = """
        {"formatVersion":$formatVersion,"id":"$id","name":"Example","version":"1","author":"Test",
        "entry":"main.js","capabilities":[$capabilities]${musicSources?.let { ",\"musicSources\":[$it]" }.orEmpty()},"permissions":{"network":{"hosts":["example.com"]}}}
    """.trimIndent()

    private fun zip(entries: Map<String, String>): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(value.encodeToByteArray()); zip.closeEntry()
            }
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
