package ink.tenqui.flowtone.data.online

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkGateway
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageInstaller
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.runtime.ExtensionResultCache
import ink.tenqui.flowtone.data.online.runtime.JavaScriptArtistAvatarExtension
import ink.tenqui.flowtone.data.online.runtime.JavaScriptSandboxHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 安装、扫描、运行和卸载外部脚本扩展的应用级所有者。 */
class ExtensionManager private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val installer = ExtensionPackageInstaller(appContext.filesDir.resolve("extensions"))
    private val sandboxHost = JavaScriptSandboxHost(appContext)
    private val gateway = ExtensionNetworkGateway()
    private val cache = ExtensionResultCache()
    private val mutex = Mutex()
    private val runtimes = mutableMapOf<String, JavaScriptArtistAvatarExtension>()
    private var initialized = false
    val artistAvatarRegistry = ArtistAvatarExtensionRegistry()

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        installer.scan().forEach { load(it) }
        initialized = true
    }

    suspend fun install(uri: Uri): InstalledExtension = mutex.withLock {
        val name = requireNotNull(displayName(uri)) { "无法确认扩展包文件名" }
        val installed = withContext(Dispatchers.IO) {
            val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取扩展包" }
            installer.install(name, input)
        }
        stop(installed.manifest.id)
        load(installed)
        installed.copy(runtimeAvailable = runtimes.containsKey(installed.manifest.id))
    }

    fun installedExtensions(): List<InstalledExtension> = installer.scan().map {
        it.copy(runtimeAvailable = runtimes.containsKey(it.manifest.id))
    }

    suspend fun uninstall(extensionId: String): Boolean = mutex.withLock {
        stop(extensionId)
        withContext(Dispatchers.IO) { installer.uninstall(extensionId) }
    }

    private suspend fun load(installed: InstalledExtension) {
        if (!installed.manifest.supportsArtistAvatar) return
        val isolate = sandboxHost.createIsolate() ?: return
        val extension = JavaScriptArtistAvatarExtension(
            installed = installed,
            isolate = isolate,
            network = gateway.createClientFor(
                extensionId = installed.manifest.id,
                capability = "artist_avatar",
                allowedHosts = installed.manifest.networkHosts
            ),
            cache = cache
        )
        runCatching { extension.start() }
            .onSuccess {
                runtimes[installed.manifest.id] = extension
                artistAvatarRegistry.install(extension)
            }
            .onFailure { extension.close() }
    }

    private fun stop(id: String) {
        artistAvatarRegistry.uninstall(id)
        runtimes.remove(id)?.close()
        cache.clear(id)
    }

    private fun displayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    override fun close() {
        runtimes.keys.toList().forEach(::stop)
        sandboxHost.close()
    }

    companion object {
        @Volatile private var instance: ExtensionManager? = null
        fun get(context: Context): ExtensionManager = instance ?: synchronized(this) {
            instance ?: ExtensionManager(context).also { instance = it }
        }
    }
}
