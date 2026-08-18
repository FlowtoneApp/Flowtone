package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef

data class ProviderSong(
    val trackRef: ExtensionTrackRef,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val artwork: ExtensionImage? = null
) {
    /** 兼容旧展示代码；身份来自 Host 绑定的 trackRef，而非 JS 返回字段。 */
    val providerId: String get() = trackRef.extensionId
    val id: String get() = trackRef.opaqueId
}
