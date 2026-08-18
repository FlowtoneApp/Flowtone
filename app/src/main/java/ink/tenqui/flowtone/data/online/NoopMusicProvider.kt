package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

class NoopMusicProvider : MusicProvider {
    override suspend fun searchSongs(keyword: String): List<ProviderSong> {
        return emptyList()
    }

    override suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource? {
        return null
    }
}
