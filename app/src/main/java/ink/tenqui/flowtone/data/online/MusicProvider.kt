package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource

interface MusicProvider {
    suspend fun searchSongs(keyword: String): List<ProviderSong>

    suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource?
}
