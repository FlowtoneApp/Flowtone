package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.likedSongsPlaylistCard
import ink.tenqui.flowtone.data.local.isSongLiked

internal fun flowtoneLikedSongCount(
    songs: List<Song>,
    likedSongKeys: List<String>
): Int {
    return songs.count { song -> isSongLiked(song, likedSongKeys) }
}

internal fun flowtoneDisplayedLibraryPlaylists(
    playlists: List<LibraryPlaylistCard>,
    likedSongCount: Int
): List<LibraryPlaylistCard> {
    return listOf(likedSongsPlaylistCard(likedSongCount)) + playlists
}

internal fun flowtonePlaylistIdsContainingCurrentSong(
    playlistSongEntries: List<PlaylistSongEntry>,
    currentSong: Song?,
    currentTrack: PersistentTrack?,
    likedSongKeys: List<String>
): Set<String> {
    if (currentSong == null || currentTrack == null) {
        return emptySet()
    }

    val normalPlaylistIds = playlistSongEntries
        .filter { entry -> entry.track.identityKey == currentTrack.identityKey }
        .mapTo(mutableSetOf()) { entry -> entry.playlistId }
    return if (currentTrack.identityKey in likedSongKeys) {
        normalPlaylistIds + LikedSongsPlaylistId
    } else {
        normalPlaylistIds
    }
}
