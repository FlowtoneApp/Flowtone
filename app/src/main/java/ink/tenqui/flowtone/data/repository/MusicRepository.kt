package ink.tenqui.flowtone.data.repository

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.LocalMusicRepository
import ink.tenqui.flowtone.data.online.MusicProvider
import ink.tenqui.flowtone.data.online.NoopMusicProvider
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchRequest

class MusicRepository(
    private val localMusicRepository: LocalMusicRepository,
    private val musicProvider: MusicProvider = NoopMusicProvider()
) {
    fun loadLocalSongs(): List<Song> {
        return localMusicRepository.loadSongs()
    }

    suspend fun searchOnlineSongs(keyword: String): List<ProviderSong> {
        return musicProvider.searchPage(
            ProviderSearchRequest(keyword = keyword, category = ProviderSearchCategory.Single)
        ).results
    }
}
