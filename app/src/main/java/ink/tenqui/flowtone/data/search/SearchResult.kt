package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchPage
import ink.tenqui.flowtone.data.online.ProviderSearchLandingState

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
    val scope: SearchScope = SearchScope.All,
    val providerOptions: List<SearchProviderOption> = emptyList(),
    val landingState: ProviderSearchLandingState = ProviderSearchLandingState.Idle,
    val isSearching: Boolean = false,
    val songResults: List<Song> = emptyList(),
    val artistResults: List<SearchArtist> = emptyList(),
    val selectedProviderCategory: ProviderSearchCategory = ProviderSearchCategory.Single,
    val providerCategoryStates: Map<ProviderSearchCategory, ProviderSearchCategoryState> =
        ProviderSearchCategory.entries.associateWith { ProviderSearchCategoryState() },
    val searchGeneration: Long = 0L
) {
    val query: SearchQuery
        get() = SearchQuery.from(queryText)

    val isEmptyQuery: Boolean
        get() = query.isBlank

    val hasNoResults: Boolean
        get() = !isEmptyQuery && !isSearching && songResults.isEmpty() && artistResults.isEmpty() &&
            providerCategoryStates.values.all { it.items.isEmpty() }
}

data class ProviderSearchCategoryState(
    val items: List<ProviderSong> = emptyList(),
    val nextCursor: String? = null,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    /** 避免同一 cursor 在滚动 snapshotFlow 中同时或反复请求。 */
    val loadingCursor: String? = null
)

fun GlobalSearchUiState.providerCategoryState(category: ProviderSearchCategory): ProviderSearchCategoryState =
    providerCategoryStates[category] ?: ProviderSearchCategoryState()

internal fun ProviderSearchCategoryState.startRequest(cursor: String?): ProviderSearchCategoryState = copy(
    isInitialLoading = cursor == null,
    isLoadingMore = cursor != null,
    loadingCursor = cursor,
    error = null
)

internal fun ProviderSearchCategoryState.acceptPage(
    page: ProviderSearchPage,
    allowNextPage: Boolean
): ProviderSearchCategoryState = copy(
    items = (items + page.results).distinctBy { "${it.trackRef.extensionId}:${it.trackRef.opaqueId}" },
    nextCursor = if (allowNextPage) page.nextCursor else null,
    isInitialLoading = false,
    isLoadingMore = false,
    hasLoaded = true,
    error = null,
    loadingCursor = null
)

internal fun ProviderSearchCategoryState.acceptFailure(error: String): ProviderSearchCategoryState = copy(
    isInitialLoading = false,
    isLoadingMore = false,
    error = error,
    loadingCursor = null
)
