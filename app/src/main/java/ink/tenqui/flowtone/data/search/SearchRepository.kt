package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.core.model.Song

class SearchRepository(
    private val localSearchSource: LocalSearchSource = LocalSearchSource(),
    private val sources: List<SearchSource> = listOf(localSearchSource)
) {
    suspend fun updateLocalSongs(songs: List<Song>) {
        localSearchSource.updateSongs(songs)
    }

    suspend fun search(query: SearchQuery): SearchResults {
        if (query.isBlank) {
            return SearchResults.Empty
        }

        val results = sources.flatMap { source -> source.search(query) }
        return SearchResults(
            songs = results
                .filterIsInstance<SearchResult.SongResult>()
                .map { result -> result.song },
            artists = results
                .filterIsInstance<SearchResult.ArtistResult>()
                .map { result -> result.artist }
        )
    }
}

fun searchPlaybackQueueStartIndex(
    songResults: List<Song>,
    clickedSong: Song
): Int {
    return songResults.indexOfFirst { song ->
        if (clickedSong.id > 0L) {
            song.id == clickedSong.id
        } else {
            song.uri == clickedSong.uri
        }
    }
}
