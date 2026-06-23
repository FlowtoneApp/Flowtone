package ink.tenqui.flowtone.data.online

import android.net.Uri

class NoopMusicProvider : MusicProvider {
    override fun searchSongs(keyword: String): List<ProviderSong> {
        return emptyList()
    }

    override fun getPlayableUri(song: ProviderSong): Uri? {
        return null
    }
}
