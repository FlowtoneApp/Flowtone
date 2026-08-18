package ink.tenqui.flowtone.ui.library

import android.net.Uri
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song

internal fun buildLibraryPlaylistCoverCollages(
    playlists: List<LibraryPlaylistCard>,
    songs: List<Song>,
    playlistSongEntries: List<PlaylistSongEntry>
): Map<String, List<Uri>> {
    if (playlists.isEmpty() || songs.isEmpty() || playlistSongEntries.isEmpty()) {
        return emptyMap()
    }

    val songsById = songs.associateBy { song -> song.id.toString() }
    val entriesByPlaylist = playlistSongEntries
        .groupBy { entry -> entry.playlistId }
        .mapValues { (_, entries) -> entries.sortedBy { entry -> entry.addedAt } }

    return playlists
        .asSequence()
        .filterNot { playlist -> playlist.isSystem }
        .distinctBy { playlist -> playlist.id }
        .associate { playlist ->
            playlist.id to entriesByPlaylist[playlist.id]
                .orEmpty()
                .asSequence()
                .take(MaxPlaylistCoverCollageSongSamples)
                .mapNotNull { entry ->
                    (entry.track as? ink.tenqui.flowtone.core.model.PersistentTrack.Local)
                        ?.songId?.let(songsById::get)
                }
                .mapNotNull { song -> song.toPlaylistCoverCandidate() }
                .distinctBy { candidate -> candidate.key }
                .take(PlaylistCoverCollageSize)
                .map { candidate -> candidate.uri }
                .toList()
        }
}

private fun Song.toPlaylistCoverCandidate(): PlaylistCoverCandidate? {
    val uri = artworkUri ?: return null
    val key = albumId?.let { albumId -> "album:$albumId" } ?: uri.toString()
    return PlaylistCoverCandidate(
        key = key,
        uri = uri
    )
}

private data class PlaylistCoverCandidate(
    val key: String,
    val uri: Uri
)

internal const val PlaylistCoverCollageSize = 4
private const val MaxPlaylistCoverCollageSongSamples = 48
