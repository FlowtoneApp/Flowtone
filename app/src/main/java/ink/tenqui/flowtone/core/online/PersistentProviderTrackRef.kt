package ink.tenqui.flowtone.core.online

/** 长期在线歌曲身份；不等同于 runtime 引用或可播放媒体资源。 */
data class PersistentProviderTrackRef(
    val sourceHost: String,
    val persistentId: String
)
