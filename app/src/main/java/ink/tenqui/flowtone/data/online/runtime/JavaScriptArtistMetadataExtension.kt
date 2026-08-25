package ink.tenqui.flowtone.data.online.runtime

import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ArtistMetadataExtension
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import androidx.javascriptengine.JavaScriptIsolate
import org.json.JSONObject

/** Lightweight JS proxy for the optional artist_metadata capability. */
class JavaScriptArtistMetadataExtension private constructor(
    private val runtime: JavaScriptExtensionRuntime,
    private val ownsRuntime: Boolean
) : ArtistMetadataExtension, AutoCloseable {
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

    override suspend fun findArtistMetadata(artistName: String): ArtistMetadata? {
        val result = runtime.invokeObject(
            "findArtistMetadata",
            JSONObject().put("artistName", artistName)
        )
        if (result.optString("type") != "found") return null
        val aliases = result.optJSONArray("aliases")?.let { array ->
            List(array.length()) { array.optString(it) }
        }.orEmpty()
        val biography = result.optString("biography").trim().takeIf(String::isNotEmpty)
        return ArtistMetadata(aliases = aliases, biography = biography)
    }

    internal fun bootstrapScript(): String = runtime.bootstrapScript()

    override fun close() {
        if (ownsRuntime) runtime.close()
    }
}
