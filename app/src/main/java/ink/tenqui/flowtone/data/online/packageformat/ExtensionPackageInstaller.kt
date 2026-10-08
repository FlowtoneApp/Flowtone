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
            val instanceId = readInstallationInstanceId(finalDirectory) ?: UUID.randomUUID().toString()
            File(prepared.unpackedDirectory, InstallInstanceFile).writeText(instanceId, Charsets.UTF_8)
            ExtensionPackageArchive.replaceAtomically(prepared.unpackedDirectory, finalDirectory)
            return InstalledExtension(
                descriptor = prepared.descriptor,
                directory = finalDirectory,
                runtimeAvailable = false,
                installationInstanceId = instanceId
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
                    val instanceId = readInstallationInstanceId(directory) ?:
                        createInstallationInstanceId(directory)
                    InstalledExtension(
                        descriptor = descriptor,
                        directory = directory,
                        runtimeAvailable = false,
                        installationInstanceId = instanceId
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

    private fun readInstallationInstanceId(directory: File): String? {
        val identityFile = File(directory, InstallInstanceFile)
        val value = runCatching { identityFile.readText(Charsets.UTF_8).trim() }.getOrNull()
            ?: return null
        return runCatching { UUID.fromString(value).toString().takeIf { it == value } }.getOrNull()
    }

    private fun createInstallationInstanceId(directory: File): String? = runCatching {
        val identityFile = File(directory, InstallInstanceFile)
        val temporary = File(directory, "$InstallInstanceFile.tmp")
        val instanceId = UUID.randomUUID().toString()
        temporary.writeText(instanceId, Charsets.UTF_8)
        if (!temporary.renameTo(identityFile)) {
            temporary.delete()
            null
        } else {
            instanceId
        }
    }.getOrNull()

    private companion object {
        const val InstallInstanceFile = ".flowtone-install-instance"
    }
}
