package ink.tenqui.flowtone.core.online

/** 扩展资源的类型只由 Host 在登记 playback session 时决定，不能从 opaque URI 推断。 */
enum class ExtensionPlaybackResourceType {
    Progressive,
    Hls
}

/** 扩展解析出的远程音频；该对象只由 Flowtone Host 绑定扩展身份后创建。 */
data class ExtensionPlaybackResource(
    val extensionId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val type: ExtensionPlaybackResourceType = ExtensionPlaybackResourceType.Progressive
)

