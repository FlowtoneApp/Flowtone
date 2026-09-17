package ink.tenqui.flowtone.data.online.packageformat

import java.io.File
import java.io.InputStream
import java.util.UUID

class ExtensionPackageInstaller(private val extensionsRoot: File) {
    fun install(fileName: String, source: InputStream): InstalledExtension {
        extensionsRoot.mkdirs()
        val staging = File(extensionsRoot.resolve(".staging"), UUID.randomUUID().toString())
        try {
            val prepared = ExtensionPackageArchive.prepare(fileName, source, staging)
            val finalDirectory = File(extensionsRoot, prepared.descriptor.identity.id)
            ExtensionPackageArchive.replaceAtomically(prepared.unpackedDirectory, finalDirectory)
            return InstalledExtension(
                descriptor = prepared.descriptor,
                directory = finalDirectory,
                runtimeAvailable = false
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    fun scan(): List<InstalledExtension> {
        if (!extensionsRoot.isDirectory) return emptyList()
        return extensionsRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .mapNotNull { directory ->
                runCatching {
                    val descriptor = ExtensionManifestParser.parseNormalized(
                        directory.resolve("manifest.json").readText(Charsets.UTF_8)
                    )
                    require(directory.name == descriptor.identity.id && directory.resolve("main.js").isFile)
                    InstalledExtension(
                        descriptor = descriptor,
                        directory = directory,
                        runtimeAvailable = false
                    )
                }.getOrNull()
            }
    }

    fun uninstall(extensionId: String): Boolean {
        require(extensionId.matches(Regex("[a-zA-Z0-9._-]+")) && ".." !in extensionId)
        val directory = File(extensionsRoot, extensionId)
        return !directory.exists() || directory.deleteRecursively()
    }

    object Limits {
        const val MaxArchiveBytes = ExtensionPackageArchive.MaxArchiveBytes
        const val MaxExtractedBytes = ExtensionPackageArchive.MaxExtractedBytes
        const val MaxFiles = ExtensionPackageArchive.MaxFiles
        const val MaxFileBytes = ExtensionPackageArchive.MaxFileBytes
        const val MaxMainBytes = ExtensionPackageArchive.MaxMainBytes
    }
}
