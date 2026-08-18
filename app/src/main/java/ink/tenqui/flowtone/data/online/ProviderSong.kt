package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.core.online.PersistentProviderTrackRef

data class ProviderSong(
    val trackRef: ExtensionTrackRef,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val artwork: ExtensionImage? = null,
    val largeArtwork: ExtensionImage? = null,
    val persistentId: String? = null,
    val sourceHost: String? = null
) {
    /** 兼容旧展示代码；身份来自 Host 绑定的 trackRef，而非 JS 返回字段。 */
    val providerId: String get() = trackRef.extensionId
    val id: String get() = trackRef.opaqueId
    val nowPlayingArtwork: ExtensionImage? get() = largeArtwork ?: artwork
    val persistentTrackRef: PersistentProviderTrackRef?
        get() = persistentId?.takeIf(String::isNotBlank)?.let { id ->
            sourceHost?.takeIf(String::isNotBlank)?.let { source ->
                PersistentProviderTrackRef(source, id)
            }
        }
}
