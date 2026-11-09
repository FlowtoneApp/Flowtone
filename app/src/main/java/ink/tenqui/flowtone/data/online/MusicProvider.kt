package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet

interface UserPlaylistProvider {
    /** 用户账号下可访问的歌单；null 表示未声明此能力，空列表表示当前没有歌单。 */
    suspend fun getUserPlaylists(): List<ProviderPlaylist>? = null
}

interface HomePlaylistRecommendationProvider {
    /** 首页在线流中的歌单推荐；null 表示未声明此能力，空列表表示暂无推荐。 */
    suspend fun getHomePlaylistRecommendations(): List<ProviderPlaylist>? = null
}

interface MusicProvider : UserPlaylistProvider, HomePlaylistRecommendationProvider {
    /** manifest 声明的服务身份，独立于网络访问白名单。 */
    val musicSources: Set<String>

    /** 此 Provider runtime 被允许执行的唯一能力真相源。 */
    val capabilities: CanonicalAtomicCapabilitySet

    /**
     * 搜索一个明确分类的一页结果。cursor 由 Provider 定义，Host 不会解析或修改它。
     */
    suspend fun searchPage(request: ProviderSearchRequest): ProviderSearchPage

    /** 可选：Provider 的空搜索词首页；未实现时返回 null。 */
    suspend fun getSearchLanding(): ProviderSearchLanding? = null

    /** 无参数全量歌曲 collection；null 表示 capability unavailable。 */
    suspend fun getSongs(): List<ProviderSong>? = null

    /** 无参数全量专辑 collection；null 表示 capability unavailable。 */
    suspend fun getAlbums(): List<ProviderAlbum>? = null

    /** 可选的只读歌单曲目接口；未声明 capability 时保持 null。 */
    suspend fun getPlaylistSongs(playlistId: String): List<ProviderSong>? = null

    /** 当前账号喜欢的歌曲。空列表表示读取成功且没有喜欢的歌曲。 */
    suspend fun getLikedSongs(): List<ProviderSong>? = null

    /** 查询当前账号对明确歌曲身份的喜欢状态；缺失的身份由 Host 视为未知。 */
    suspend fun getLikedSongStates(trackRefs: List<ExtensionTrackRef>): Map<ExtensionTrackRef, Boolean>? = null

    /** Returns true only when the provider confirms the requested change. */
    suspend fun setLikedSongState(trackRef: ExtensionTrackRef, liked: Boolean): Boolean = false

    suspend fun resolvePersistentSong(persistentId: String): ProviderSong?

    suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource?
}
