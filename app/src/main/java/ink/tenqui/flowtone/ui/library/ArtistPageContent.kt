package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.data.local.localArtistStableId
import ink.tenqui.flowtone.ui.player.parseArtistCandidates

internal fun artistAlbumsFor(
    albums: List<LocalAlbum>,
    artistName: String
): List<LocalAlbum> {
    return albums.filter { album ->
        artistMatchesAlbumSongs(
            artistName = artistName,
            songArtists = album.songs.map { it.artist }
        )
    }
}

/** Uses the same local artist identity rules as the song list for album membership. */
internal fun artistMatchesAlbumSongs(
    artistName: String,
    songArtists: List<String>
): Boolean {
    val artistId = artistName.trim().takeIf(String::isNotEmpty)
        ?.let(::localArtistStableId)
        ?: return false

    return songArtists.any { songArtist ->
        parseArtistCandidates(songArtist).any { candidate ->
            localArtistStableId(candidate) == artistId
        }
    }
}

internal fun artistStatisticsText(songCount: Int, albumCount: Int): String {
    val songs = "$songCount 首歌曲"
    return if (albumCount > 0) "$songs · $albumCount 张专辑" else songs
}
