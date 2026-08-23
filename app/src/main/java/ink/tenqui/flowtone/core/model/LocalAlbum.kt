package ink.tenqui.flowtone.core.model

import android.net.Uri
import java.util.Locale

data class LocalAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val songs: List<Song>
)

fun localAlbumsFrom(songs: List<Song>): List<LocalAlbum> {
    return songs
        .asSequence()
        .filter { song -> song.sourceType == SourceType.Local && (song.albumId ?: 0L) > 0L }
        .groupBy { song -> checkNotNull(song.albumId) }
        .map { (albumId, albumSongs) ->
            val albumArtists = albumSongs
                .mapNotNull { song -> usableAlbumMetadataText(song.albumArtist) }
                .distinctBy { artist -> artist.lowercase(Locale.ROOT) }
            val songArtists = albumSongs
                .mapNotNull { song -> usableAlbumMetadataText(song.artist) }
                .distinctBy { artist -> artist.lowercase(Locale.ROOT) }
            LocalAlbum(
                id = albumId,
                title = albumSongs
                    .firstNotNullOfOrNull { song -> usableAlbumMetadataText(song.albumTitle) }
                    ?: "\u672a\u77e5\u4e13\u8f91",
                artist = when {
                    albumArtists.isNotEmpty() -> albumArtists.joinToString(" / ")
                    songArtists.size == 1 -> songArtists.single()
                    songArtists.size > 1 -> "\u591a\u4f4d\u827a\u672f\u5bb6"
                    else -> "\u672a\u77e5\u827a\u672f\u5bb6"
                },
                artworkUri = albumSongs.firstNotNullOfOrNull(Song::artworkUri),
                songs = albumSongs
            )
        }
        .sortedBy { album -> album.title.lowercase(Locale.ROOT) }
}

private fun usableAlbumMetadataText(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf { text -> text.isNotEmpty() && !text.equals("<unknown>", ignoreCase = true) }
}
