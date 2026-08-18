package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.online.ProviderSong

sealed interface SearchResult {
    data class SongResult(
        val song: Song,
        val stableOrder: Int
    ) : SearchResult

    data class ArtistResult(
        val artist: SearchArtist,
        val stableOrder: Int
    ) : SearchResult

    data class AlbumResult(
        val id: String,
        val title: String
    ) : SearchResult

    data class PlaylistResult(
        val id: String,
        val title: String
    ) : SearchResult
}

data class SearchArtist(
    val id: String,
    val name: String,
    val songCount: Int,
    val stableOrder: Int,
    /** 用于在线头像匹配的一首本地代表歌曲。 */
    val representativeSongTitle: String = ""
)

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<SearchArtist> = emptyList()
) {
    companion object {
        val Empty = SearchResults()
    }
}

data class GlobalSearchUiState(
    val queryText: String = "",
    val isSearching: Boolean = false,
    val songResults: List<Song> = emptyList(),
    val artistResults: List<SearchArtist> = emptyList(),
    val onlineSongResults: List<ProviderSong> = emptyList()
) {
    val query: SearchQuery
        get() = SearchQuery.from(queryText)

    val isEmptyQuery: Boolean
        get() = query.isBlank

    val hasNoResults: Boolean
        get() = !isEmptyQuery && !isSearching && songResults.isEmpty() && artistResults.isEmpty() && onlineSongResults.isEmpty()
}
