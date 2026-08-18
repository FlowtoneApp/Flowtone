package ink.tenqui.flowtone.core.online

/** Host 绑定的在线曲目引用；id 对 Flowtone 保持不透明。 */
data class ExtensionTrackRef(
    val extensionId: String,
    val opaqueId: String
)
