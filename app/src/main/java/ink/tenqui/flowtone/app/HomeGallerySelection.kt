package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.Song
import kotlin.random.Random

internal data class HomeGallerySelection(
    val featuredSong: Song?,
    val discoverySongs: List<Song>,
    val suggestedSongs: List<Song>
)

internal fun selectHomeGallery(songs: List<Song>, seed: Long): HomeGallerySelection {
    val shuffledSongs = songs
        .distinctBy { it.sourceType to it.id }
        .shuffled(Random(seed))
    val remainingSongs = shuffledSongs.drop(1)

    return HomeGallerySelection(
        featuredSong = shuffledSongs.firstOrNull(),
        discoverySongs = remainingSongs.take(10),
        suggestedSongs = remainingSongs.drop(10).take(8)
    )
}
