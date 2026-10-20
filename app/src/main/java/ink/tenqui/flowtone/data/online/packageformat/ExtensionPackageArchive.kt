package ink.tenqui.flowtone.data.online.packageformat

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class PreparedExtensionPackage(
    val archive: File,
    val unpackedDirectory: File,
    val descriptor: NormalizedExtensionDescriptor
)

/** Installer 与只读 inspector 共用的唯一包复制、解压和验证流程。 */
internal object ExtensionPackageArchive {
    const val MaxArchiveBytes = 5L * 1024 * 1024
    const val MaxExtractedBytes = 10L * 1024 * 1024
    const val MaxFiles = 64
    const val MaxFileBytes = 2L * 1024 * 1024
    const val MaxMainBytes = 1L * 1024 * 1024

    fun prepare(
        fileName: String,
        source: InputStream,
        workspace: File
    ): PreparedExtensionPackage {
        requireSupportedFileName(fileName)
        workspace.mkdirs()
        val archive = workspace.resolve("package.flowtone")
        copyLimited(source, archive, MaxArchiveBytes)
        return validateArchive(archive, workspace.resolve("unpacked"))
    }

    fun validateArchive(archive: File, unpackedDirectory: File): PreparedExtensionPackage {
        require(archive.isFile && archive.length() <= MaxArchiveBytes) { "扩展包不存在或过大" }
        if (unpackedDirectory.exists()) unpackedDirectory.deleteRecursively()
        unpackedDirectory.mkdirs()
        extractSafely(archive, unpackedDirectory)
        val manifestFile = unpackedDirectory.resolve("manifest.json")
        val mainFile = unpackedDirectory.resolve("main.js")
        require(manifestFile.isFile) { "扩展包缺少 manifest.json" }
        require(mainFile.isFile) { "扩展包缺少 main.js" }
        require(mainFile.length() <= MaxMainBytes) { "main.js 过大" }
        val descriptor = ExtensionManifestParser.parseNormalized(
            manifestFile.readText(Charsets.UTF_8)
        )
        return PreparedExtensionPackage(archive, unpackedDirectory, descriptor)
    }

    fun requireSupportedFileName(fileName: String) {
        require(fileName.endsWith(".flowtone", true) || fileName.endsWith(".zip", true)) {
            "不是 Flowtone 扩展包"
        }
    }

    private fun extractSafely(archive: File, destination: File) {
        val rootPath = destination.canonicalFile.toPath()
        val paths = mutableSetOf<String>()
        var entries = 0
        var total = 0L
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MaxFiles) { "扩展包文件数量过大" }
                val normalizedName = entry.name.replace('\\', '/')
                require(normalizedName.isNotBlank() && !normalizedName.startsWith('/') && ':' !in normalizedName) {
                    "扩展包包含非法路径"
                }
                require(paths.add(normalizedName)) { "扩展包包含重复路径" }
                val allowedPath = normalizedName == "manifest.json" || normalizedName == "main.js" ||
                    normalizedName == "assets" || normalizedName.startsWith("assets/")
                require(allowedPath) { "扩展包包含不支持的文件：$normalizedName" }
                require(!normalizedName.lowercase().endsWithAny(".dex", ".jar", ".apk", ".so", ".wasm")) {
                    "扩展包包含禁止的可执行文件"
                }
                val target = File(destination, normalizedName).canonicalFile
                require(target.toPath().startsWith(rootPath)) { "扩展包包含越界路径" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var fileBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            total += read
                            require(fileBytes <= MaxFileBytes) { "扩展包单文件过大" }
                            require(total <= MaxExtractedBytes) { "扩展包解压后过大" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyLimited(source: InputStream, target: File, maximum: Long) {
        source.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maximum) { "扩展包超过 5 MiB" }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    fun replaceAtomically(source: File, target: File) {
        val backup = File(target.parentFile, ".backup-${target.name}-${java.util.UUID.randomUUID()}")
        if (target.exists()) require(target.renameTo(backup)) { "无法暂存旧扩展" }
        try {
            require(source.renameTo(target)) { "无法安装扩展" }
            backup.deleteRecursively()
        } catch (error: Throwable) {
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }
}

private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)
