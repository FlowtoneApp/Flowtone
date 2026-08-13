package ink.tenqui.flowtone.core.online

/**
 * 歌手头像的唯一在线扩展接口。
 *
 * 扩展只负责根据歌手名称查询图片地址，不接触播放器、界面或本地媒体库。
 */
interface ArtistAvatarProvider {
    suspend fun findArtistAvatar(
        songTitle: String,
        artistName: String
    ): ArtistAvatar?
}
