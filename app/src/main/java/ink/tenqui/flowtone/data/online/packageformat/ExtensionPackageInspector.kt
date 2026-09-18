package ink.tenqui.flowtone.data.online.packageformat

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class ExtensionInstallPreviewUnavailableException : IllegalArgumentException(
    "安装预览已失效，请重新选择扩展包。"
)

class ExtensionPackageSnapshotHandle internal constructor(
    internal val token: String,
    internal val sha256: String
) {
    override fun equals(other: Any?): Boolean = other is ExtensionPackageSnapshotHandle &&
        token == other.token && sha256 == other.sha256

    override fun hashCode(): Int = 31 * token.hashCode() + sha256.hashCode()

    override fun toString(): String = "ExtensionPackageSnapshotHandle($token)"
}

/** cacheDir 中短生命周期、可丢失的安装预览快照。不会接触正式 extensions 目录。 */
class ExtensionPackageInspector(
    private val previewRoot: File,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    init {
        cleanupOrphanedSnapshotsOnStartup()
    }

    fun inspect(
        fileName: String,
        source: InputStream,
        installedExtensions: Collection<NormalizedExtensionDescriptor> = emptyList()
    ): ExtensionInstallPreview {
        cleanupExpiredSnapshots()
        val token = UUID.randomUUID().toString()
        val snapshotDirectory = previewRoot.resolve(token)
        try {
            val prepared = ExtensionPackageArchive.prepare(fileName, source, snapshotDirectory)
            val digest = prepared.archive.sha256()
            prepared.unpackedDirectory.deleteRecursively()
            snapshotDirectory.setLastModified(clockMillis())
            val handle = ExtensionPackageSnapshotHandle(token, digest)
            val existing = installedExtensions.firstOrNull {
                it.identity.id == prepared.descriptor.identity.id
            }
            return ExtensionInstallPreviewBuilder.fromDescriptor(
                incoming = prepared.descriptor,
                existingInstallation = existing,
                snapshotHandle = handle
            )
        } catch (error: Throwable) {
            snapshotDirectory.deleteRecursively()
            throw error
        }
    }

    fun discard(handle: ExtensionPackageSnapshotHandle) {
        snapshotDirectory(handle).deleteRecursively()
    }

    /**
     * 后续确认安装只能通过此入口消费快照；摘要不一致、超时或被系统清理都会失败，
     * 调用方不得回退到重新读取原 URI。
     */
    internal fun <T> consumeSnapshot(
        handle: ExtensionPackageSnapshotHandle,
        block: (fileName: String, source: InputStream) -> T
    ): T {
        val directory = snapshotDirectory(handle)
        try {
            if (isExpired(directory)) throw ExtensionInstallPreviewUnavailableException()
            val archive = directory.resolve(SnapshotFileName)
            if (!archive.isFile || archive.sha256() != handle.sha256) {
                throw ExtensionInstallPreviewUnavailableException()
            }
            return archive.inputStream().buffered().use { input ->
                block("preview.flowtone", input)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    internal fun hasSnapshot(handle: ExtensionPackageSnapshotHandle): Boolean =
        snapshotDirectory(handle).resolve(SnapshotFileName).isFile

    fun cleanupExpiredSnapshots() {
        if (!previewRoot.isDirectory) return
        previewRoot.listFiles().orEmpty().forEach { candidate ->
            if (!candidate.isDirectory || isExpired(candidate)) candidate.deleteRecursively()
        }
    }

    private fun cleanupOrphanedSnapshotsOnStartup() {
        if (!previewRoot.isDirectory) return
        previewRoot.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    private fun snapshotDirectory(handle: ExtensionPackageSnapshotHandle): File {
        require(SafeToken.matches(handle.token)) { "非法扩展安装预览 handle" }
        val root = previewRoot.canonicalFile
        val directory = previewRoot.resolve(handle.token).canonicalFile
        require(directory.toPath().startsWith(root.toPath())) { "扩展安装预览路径越界" }
        return directory
    }

    private fun isExpired(directory: File): Boolean =
        !directory.isDirectory || clockMillis() - directory.lastModified() > SnapshotTtlMillis

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val SnapshotTtlMillis = 30L * 60L * 1000L
        private const val SnapshotFileName = "package.flowtone"
        private val SafeToken = Regex("[a-f0-9-]{36}")
    }
}
