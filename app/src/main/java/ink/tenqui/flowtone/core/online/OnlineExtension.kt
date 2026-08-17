package ink.tenqui.flowtone.core.online

/** 所有在线扩展共用的基础标识。 */
interface OnlineExtension {
    val id: String
    val displayName: String
}

/**
 * 当前版本允许安装的在线扩展类型。
 *
 * 核心只会通过 [ArtistAvatarProvider] 调用扩展，从而限制扩展可参与的功能边界。
 */
interface ArtistAvatarExtension : OnlineExtension, ArtistAvatarProvider
