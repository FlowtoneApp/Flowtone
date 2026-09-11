package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

interface MusicProvider {
    /** manifest 声明的服务身份，独立于网络访问白名单。 */
    val musicSources: Set<String>

    /** Provider 可提供的结构化 entity collection；不代表播放或详情 endpoint 能力。 */
    val entityCapabilities: Set<ProviderEntityCapability>
        get() = emptySet()

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

    suspend fun resolvePersistentSong(persistentId: String): ProviderSong?

    suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource?
}
