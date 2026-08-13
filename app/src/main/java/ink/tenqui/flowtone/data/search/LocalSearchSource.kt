package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.data.local.isSelectableLocalArtist
import ink.tenqui.flowtone.data.local.localArtistStableId
import ink.tenqui.flowtone.data.local.parseLocalArtistCandidates
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LocalSongResultLimit = 50
private const val LocalArtistResultLimit = 30

class LocalSearchSource : SearchSource {
    private var index = LocalSearchIndex.Empty

    suspend fun updateSongs(songs: List<Song>) {
        index = withContext(Dispatchers.Default) {
            LocalSearchIndex.from(songs)
        }
    }

    override suspend fun search(query: SearchQuery): List<SearchResult> {
        if (query.isBlank) {
            return emptyList()
        }

        return withContext(Dispatchers.Default) {
            index.search(query)
        }
    }
}

private data class LocalSearchIndex(
    val songs: List<IndexedSong>,
    val artists: List<SearchArtist>
) {
    fun search(query: SearchQuery): List<SearchResult> {
        val normalizedQuery = query.normalizedText
        val songResults = songs
            .mapNotNull { indexedSong ->
                indexedSong.matchRank(normalizedQuery)?.let { rank ->
                    RankedSong(indexedSong = indexedSong, rank = rank)
                }
            }
            .sortedWith(
                compareBy<RankedSong> { ranked -> ranked.rank }
                    .thenBy { ranked -> ranked.indexedSong.stableOrder }
            )
            .take(LocalSongResultLimit)
            .map { ranked ->
                SearchResult.SongResult(
                    song = ranked.indexedSong.song,
                    stableOrder = ranked.indexedSong.stableOrder
                )
            }

        val artistResults = artists
            .mapNotNull { artist ->
                matchRank(artist.name.normalized(), normalizedQuery)?.let { rank ->
                    RankedArtist(artist = artist, rank = rank)
                }
            }
            .sortedWith(
                compareBy<RankedArtist> { ranked -> ranked.rank }
                    .thenBy { ranked -> ranked.artist.stableOrder }
            )
            .take(LocalArtistResultLimit)
            .map { ranked ->
                SearchResult.ArtistResult(
                    artist = ranked.artist,
                    stableOrder = ranked.artist.stableOrder
                )
            }

        return songResults + artistResults
    }

    companion object {
        val Empty = LocalSearchIndex(
            songs = emptyList(),
            artists = emptyList()
        )

        fun from(songs: List<Song>): LocalSearchIndex {
            val localSongs = songs
                .withIndex()
                .filter { indexedSong -> indexedSong.value.sourceType == SourceType.Local }

            val indexedSongs = localSongs.map { indexedSong ->
                val artistCandidates = parseLocalArtistCandidates(indexedSong.value.artist)
                IndexedSong(
                    song = indexedSong.value,
                    stableOrder = indexedSong.index,
                    normalizedTitle = indexedSong.value.title.normalized(),
                    normalizedArtists = artistCandidates.map { artist -> artist.normalized() }
                )
            }

            val artistsById = linkedMapOf<String, MutableArtist>()
            localSongs.forEach { indexedSong ->
                parseLocalArtistCandidates(indexedSong.value.artist)
                    .filter { artist -> isSelectableLocalArtist(artist) }
                    .forEach { artist ->
                        val stableId = localArtistStableId(artist)
                        val existing = artistsById[stableId]
                        if (existing == null) {
                            artistsById[stableId] = MutableArtist(
                                id = stableId,
                                name = artist.trim(),
                                stableOrder = indexedSong.index,
                                songCount = 1,
                                representativeSongTitle = indexedSong.value.title
                            )
                        } else {
                            existing.songCount += 1
                        }
                    }
            }

            return LocalSearchIndex(
                songs = indexedSongs,
                artists = artistsById.values.map { artist ->
                    SearchArtist(
                        id = artist.id,
                        name = artist.name,
                        songCount = artist.songCount,
                        stableOrder = artist.stableOrder,
                        representativeSongTitle = artist.representativeSongTitle
                    )
                }
            )
        }
    }
}

private data class IndexedSong(
    val song: Song,
    val stableOrder: Int,
    val normalizedTitle: String,
    val normalizedArtists: List<String>
) {
    fun matchRank(normalizedQuery: String): Int? {
        val titleRank = matchRank(normalizedTitle, normalizedQuery)
        val artistRank = normalizedArtists
            .mapNotNull { artist -> matchRank(artist, normalizedQuery) }
            .minOrNull()

        return listOfNotNull(titleRank, artistRank).minOrNull()
    }
}

private data class MutableArtist(
    val id: String,
    val name: String,
    val stableOrder: Int,
    var songCount: Int,
    val representativeSongTitle: String
)

private data class RankedSong(
    val indexedSong: IndexedSong,
    val rank: Int
)

private data class RankedArtist(
    val artist: SearchArtist,
    val rank: Int
)

private fun matchRank(value: String, normalizedQuery: String): Int? {
    return when {
        value == normalizedQuery -> 0
        value.startsWith(normalizedQuery) -> 1
        value.contains(normalizedQuery) -> 2
        else -> null
    }
}

private fun String.normalized(): String {
    return trim().lowercase(Locale.ROOT)
}
