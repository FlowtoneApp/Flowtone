package ink.tenqui.flowtone.core.online

/** 扩展解析出的渐进式远程音频；该对象只由 Flowtone Host 绑定扩展身份后创建。 */
data class ExtensionPlaybackResource(
    val extensionId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null
)

