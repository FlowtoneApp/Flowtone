package ink.tenqui.flowtone.core.online

/** 由扩展返回的远程资源；扩展身份由 Flowtone 绑定，不能由脚本伪造。 */
data class ExtensionImage(
    val extensionId: String,
    val url: String
)

/** 在线来源返回的歌手头像信息。图片由 Flowtone 的受控图片管线下载。 */
data class ArtistAvatar(
    val image: ExtensionImage
)
