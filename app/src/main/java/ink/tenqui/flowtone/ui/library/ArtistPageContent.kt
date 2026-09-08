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

/** Provider counts are profile metadata and do not imply loaded song or album entities. */
internal fun artistMetadataStatisticsText(songCount: Int?, albumCount: Int?): String? {
    return listOfNotNull(
        songCount?.let { "$it 首歌曲" },
        albumCount?.let { "$it 张专辑" }
    ).joinToString(" · ").takeIf(String::isNotEmpty)
}

internal data class ArtistPageContentVisibility(
    val showStatistics: Boolean,
    val showSongs: Boolean,
    val showAlbums: Boolean
)

internal enum class ArtistPrimaryContentPresentation {
    Loading,
    Ready,
    Empty
}

internal const val ArtistLoadingSkeletonCount = 7
internal const val ArtistSongPreviewLimit = 10
internal const val ArtistAlbumPreviewLimit = 6

internal fun <T> artistSongPreview(items: List<T>): List<T> =
    items.take(ArtistSongPreviewLimit)

internal fun <T> artistAlbumPreview(items: List<T>): List<T> =
    items.take(ArtistAlbumPreviewLimit)

internal fun artistSongsSectionTitle(providerOrderTitle: String?): String =
    providerOrderTitle?.trim()?.takeIf(String::isNotEmpty) ?: "歌曲"

internal fun artistLoadingContentIdentity(entryKey: String): String =
    "$entryKey:artist-loading-content"

internal fun artistRealContentIdentity(entryKey: String): String =
    "$entryKey:artist-real-content"

internal data class ArtistScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)

internal class ArtistScrollStateOwner internal constructor(
    val entryKey: String
) {
    var position: ArtistScrollPosition = ArtistScrollPosition()
        private set

    fun update(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        position = ArtistScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
        )
    }
}

internal fun artistTintArtworkData(
    banner: Any?,
    avatar: Any?,
    localArtwork: Any?,
    providerArtwork: Any?
): Any? = banner ?: avatar ?: localArtwork ?: providerArtwork

internal class ArtistScrollStateStore {
    private val owners = mutableMapOf<String, ArtistScrollStateOwner>()

    fun ownerFor(entryKey: String): ArtistScrollStateOwner =
        owners.getOrPut(entryKey) { ArtistScrollStateOwner(entryKey) }

    fun retainEntries(entryKeys: Set<String>) {
        owners.keys.retainAll(entryKeys)
    }
}

internal fun artistPrimaryContentPresentation(
    hasLocalContent: Boolean,
    providerSongsLoaded: Boolean,
    hasSongs: Boolean
): ArtistPrimaryContentPresentation = when {
    !hasLocalContent && !providerSongsLoaded -> ArtistPrimaryContentPresentation.Loading
    hasSongs -> ArtistPrimaryContentPresentation.Ready
    else -> ArtistPrimaryContentPresentation.Empty
}

internal fun artistLoadingSkeletonKeys(
    skeletonCount: Int = ArtistLoadingSkeletonCount
): List<String> = List(skeletonCount.coerceAtLeast(0)) { index ->
        "artist-loading-skeleton-$index"
}

internal fun artistPageContentVisibility(
    hasLocalContent: Boolean,
    hasSongs: Boolean = false,
    songsLoading: Boolean = false,
    hasAlbums: Boolean,
    hasStatistics: Boolean = hasLocalContent
): ArtistPageContentVisibility = ArtistPageContentVisibility(
    showStatistics = hasStatistics,
    showSongs = hasLocalContent || hasSongs || songsLoading,
    showAlbums = hasAlbums
)
