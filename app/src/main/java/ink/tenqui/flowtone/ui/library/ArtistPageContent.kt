package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.data.local.localArtistStableId
import ink.tenqui.flowtone.ui.components.PageMotion
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
    return if (albumCount > 0) "$songs\n$albumCount 张专辑" else songs
}

/** Provider counts are profile metadata and do not imply loaded song or album entities. */
internal fun artistMetadataStatisticsText(songCount: Int?, albumCount: Int?): String? {
    return listOfNotNull(
        songCount?.let { "$it 首歌曲" },
        albumCount?.let { "$it 张专辑" }
    ).joinToString("\n").takeIf(String::isNotEmpty)
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
internal const val ArtistPrimaryContentTimingOrder = 0

internal fun artistPrimaryContentPresentation(
    hasLocalContent: Boolean,
    providerSongsLoaded: Boolean,
    hasSongs: Boolean
): ArtistPrimaryContentPresentation = when {
    !hasLocalContent && !providerSongsLoaded -> ArtistPrimaryContentPresentation.Loading
    hasSongs -> ArtistPrimaryContentPresentation.Ready
    else -> ArtistPrimaryContentPresentation.Empty
}

internal fun artistPrimaryContentPresentationKeys(
    presentation: ArtistPrimaryContentPresentation,
    readySongKeys: List<String>,
    skeletonCount: Int = ArtistLoadingSkeletonCount
): List<String> = when (presentation) {
    ArtistPrimaryContentPresentation.Loading -> List(skeletonCount.coerceAtLeast(0)) { index ->
        "artist-loading-skeleton-$index"
    }
    ArtistPrimaryContentPresentation.Ready -> readySongKeys
    ArtistPrimaryContentPresentation.Empty -> emptyList()
}

internal fun artistPrimaryContentPresentationProgress(pageProgress: Float): Float =
    PageMotion.elementProgress(
        pageProgress = pageProgress,
        order = ArtistPrimaryContentTimingOrder,
        orderCount = ArtistLoadingSkeletonCount
    )

internal data class ArtistSkeletonAnimationChannels(
    val pagePresentationProgress: Float,
    val breathingProgress: Float
)

internal fun artistSkeletonAnimationChannels(
    pagePresentationProgress: Float,
    breathingProgress: Float
): ArtistSkeletonAnimationChannels = ArtistSkeletonAnimationChannels(
    pagePresentationProgress = pagePresentationProgress.coerceIn(0f, 1f),
    breathingProgress = breathingProgress.coerceIn(0f, 1f)
)

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
