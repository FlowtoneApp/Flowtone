package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

interface MusicProvider {
    /** manifest 声明的服务身份，独立于网络访问白名单。 */
    val musicSources: Set<String>

    /**
     * 搜索一个明确分类的一页结果。cursor 由 Provider 定义，Host 不会解析或修改它。
     */
    suspend fun searchPage(request: ProviderSearchRequest): ProviderSearchPage

    /** 可选：Provider 的空搜索词首页；未实现时返回 null。 */
    suspend fun getSearchLanding(): ProviderSearchLanding? = null

    suspend fun resolvePersistentSong(persistentId: String): ProviderSong?

    suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource?
}
