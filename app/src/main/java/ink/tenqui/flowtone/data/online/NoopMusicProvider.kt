package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

class NoopMusicProvider : MusicProvider {
    override val musicSources: Set<String> = emptySet()

    override suspend fun searchPage(request: ProviderSearchRequest) = ProviderSearchPage(emptyList())

    override suspend fun resolvePersistentSong(persistentId: String): ProviderSong? = null

    override suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource? {
        return null
    }
}
