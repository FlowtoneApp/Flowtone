package ink.tenqui.flowtone.data.online.packageformat

import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ExtensionPackageInstaller(private val extensionsRoot: File) {
    fun install(fileName: String, source: InputStream): InstalledExtension {
        require(fileName.endsWith(".flowtone", true) || fileName.endsWith(".zip", true)) {
            "不是 Flowtone 扩展包"
        }
        extensionsRoot.mkdirs()
        val stagingRoot = File(extensionsRoot, ".staging").apply { mkdirs() }
        val staging = File(stagingRoot, UUID.randomUUID().toString()).apply { mkdirs() }
        val archive = File(staging, "package.flowtone")
        try {
            copyLimited(source, archive, Limits.MaxArchiveBytes)
            val unpacked = File(staging, "unpacked").apply { mkdirs() }
            extractSafely(archive, unpacked)
            val manifestFile = File(unpacked, "manifest.json")
            val mainFile = File(unpacked, "main.js")
            require(manifestFile.isFile) { "扩展包缺少 manifest.json" }
            require(mainFile.isFile) { "扩展包缺少 main.js" }
            require(mainFile.length() <= Limits.MaxMainBytes) { "main.js 过大" }
            val manifest = ExtensionManifestParser.parse(manifestFile.readText(Charsets.UTF_8))
            val finalDirectory = File(extensionsRoot, manifest.id)
            replaceAtomically(unpacked, finalDirectory)
            return InstalledExtension(manifest, finalDirectory, runtimeAvailable = false)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun scan(): List<InstalledExtension> {
        if (!extensionsRoot.isDirectory) return emptyList()
        return extensionsRoot.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith('.') }
            .mapNotNull { directory ->
                runCatching {
                    val manifest = ExtensionManifestParser.parse(File(directory, "manifest.json").readText())
                    require(directory.name == manifest.id && File(directory, "main.js").isFile)
                    InstalledExtension(manifest, directory, runtimeAvailable = false)
                }.getOrNull()
            }
    }

    fun uninstall(extensionId: String): Boolean {
        require(extensionId.matches(Regex("[a-zA-Z0-9._-]+")) && ".." !in extensionId)
        return !File(extensionsRoot, extensionId).exists() || File(extensionsRoot, extensionId).deleteRecursively()
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
                require(entries <= Limits.MaxFiles) { "扩展包文件数量过多" }
                val normalizedName = entry.name.replace('\\', '/')
                require(normalizedName.isNotBlank() && !normalizedName.startsWith('/') && ':' !in normalizedName) {
                    "扩展包包含非法路径"
                }
                require(paths.add(normalizedName)) { "扩展包包含重复路径" }
                val allowedPath = normalizedName == "manifest.json" || normalizedName == "main.js" ||
                    normalizedName == "assets" || normalizedName.startsWith("assets/")
                require(allowedPath) { "v1 扩展包包含不支持的文件：$normalizedName" }
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
                            require(fileBytes <= Limits.MaxFileBytes) { "扩展包单文件过大" }
                            require(total <= Limits.MaxExtractedBytes) { "扩展包解压后过大" }
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

    private fun replaceAtomically(source: File, target: File) {
        val backup = File(target.parentFile, ".backup-${target.name}-${UUID.randomUUID()}")
        if (target.exists()) require(target.renameTo(backup)) { "无法暂存旧扩展" }
        try {
            require(source.renameTo(target)) { "无法安装扩展" }
            backup.deleteRecursively()
        } catch (error: Throwable) {
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    object Limits {
        const val MaxArchiveBytes = 5L * 1024 * 1024
        const val MaxExtractedBytes = 10L * 1024 * 1024
        const val MaxFiles = 64
        const val MaxFileBytes = 2L * 1024 * 1024
        const val MaxMainBytes = 1L * 1024 * 1024
    }
}

private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)
