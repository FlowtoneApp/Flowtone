package ink.tenqui.flowtone.data.online.playback

import android.net.Uri
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 用不透明 URI 在 MediaSession/Media3 中携带扩展资源身份，远程 URL 不会退化为普通 URI。 */
class ExtensionPlaybackResourceStore {
    private val resources = ConcurrentHashMap<String, ExtensionPlaybackResource>()

    fun register(resource: ExtensionPlaybackResource): Uri {
        val token = UUID.randomUUID().toString()
        resources[token] = resource
        return Uri.Builder()
            .scheme(Scheme)
            .authority(Authority)
            .appendPath(token)
            .build()
    }

    fun resolve(uri: Uri): ExtensionPlaybackResource? {
        if (!uri.scheme.equals(Scheme, ignoreCase = true) || uri.host != Authority) return null
        val token = uri.pathSegments.singleOrNull() ?: return null
        return resources[token]
    }

    fun clear(extensionId: String) {
        resources.entries.removeIf { it.value.extensionId == extensionId }
    }

    companion object {
        const val Scheme = "flowtone-extension"
        private const val Authority = "media"
    }
}

