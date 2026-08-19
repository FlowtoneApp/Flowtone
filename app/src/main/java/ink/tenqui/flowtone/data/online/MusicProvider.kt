package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

interface MusicProvider {
    /** manifest 声明的服务身份，独立于网络访问白名单。 */
    val musicSources: Set<String>

    /**
     * 搜索结果中的 category 可为 single、playlist、album 或 user。
     * Host 当前仅展示歌单的封面、标题和创作者，不提供歌单详情操作。
     */
    suspend fun searchSongs(keyword: String): List<ProviderSong>

    /** 可选：Provider 的空搜索词首页；未实现时返回 null。 */
    suspend fun getSearchLanding(): ProviderSearchLanding? = null

    suspend fun resolvePersistentSong(persistentId: String): ProviderSong?

    suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource?
}
