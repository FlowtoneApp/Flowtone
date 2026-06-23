package ink.tenqui.flowtone.data.online

import android.net.Uri

interface MusicProvider {
    fun searchSongs(keyword: String): List<ProviderSong>

    fun getPlayableUri(song: ProviderSong): Uri?
}
