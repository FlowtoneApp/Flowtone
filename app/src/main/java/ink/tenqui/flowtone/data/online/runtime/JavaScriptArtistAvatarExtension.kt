package ink.tenqui.flowtone.data.online.runtime

import androidx.javascriptengine.JavaScriptIsolate
import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ArtistAvatarExtension
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import org.json.JSONObject

/** artist_avatar capability 的轻量 JS 代理；通用 Host API 由 JavaScriptExtensionRuntime 管理。 */
class JavaScriptArtistAvatarExtension private constructor(
    private val runtime: JavaScriptExtensionRuntime,
    private val ownsRuntime: Boolean
) : ArtistAvatarExtension, AutoCloseable {
    constructor(
        installed: InstalledExtension,
        isolate: JavaScriptIsolate,
        network: ExtensionNetworkClient,
        privateCache: ExtensionPrivateCache
    ) : this(JavaScriptExtensionRuntime(installed, isolate, network, privateCache), true)

    internal constructor(runtime: JavaScriptExtensionRuntime) : this(runtime, false)

    override val id: String = runtime.extensionId
    override val displayName: String = runtime.installed.manifest.name

    suspend fun start() = runtime.start()

    override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
        val result = runtime.invokeObject(
            "findArtistAvatar",
            JSONObject().put("songTitle", songTitle).put("artistName", artistName)
        )
        return when (result.optString("type")) {
            "found" -> result.optString("imageUrl").trim().takeIf { it.startsWith("https://") }
                ?.let { ArtistAvatar(ExtensionImage(extensionId = id, url = it)) }
            else -> null
        }
    }

    internal fun bootstrapScript(): String = runtime.bootstrapScript()

    override fun close() { if (ownsRuntime) runtime.close() }
}
