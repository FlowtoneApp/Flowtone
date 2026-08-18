package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

interface MusicProvider {
    /** manifest 声明的服务身份，独立于网络访问白名单。 */
    val musicSources: Set<String>

    suspend fun searchSongs(keyword: String): List<ProviderSong>

    suspend fun resolvePersistentSong(persistentId: String): ProviderSong?

    suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource?
}
