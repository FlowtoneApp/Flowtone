package ink.tenqui.flowtone.data.online

/** Provider 搜索的一次请求。cursor 对 Host 完全透明，只能原样回传给 Provider。 */
data class ProviderSearchRequest(
    val keyword: String,
    val category: ProviderSearchCategory,
    val cursor: String? = null,
    val limit: Int = DefaultProviderSearchPageSize
)

/** Provider 搜索的一页结果。分页状态不属于单个 ProviderSong。 */
data class ProviderSearchPage(
    val results: List<ProviderSong>,
    val nextCursor: String? = null
)

sealed interface ProviderSearchCallResult {
    data class Success(val page: ProviderSearchPage) : ProviderSearchCallResult
    data class Failure(val exception: Throwable) : ProviderSearchCallResult
}

const val DefaultProviderSearchPageSize = 20

internal fun ProviderSearchCategory.toWireValue(): String = when (this) {
    ProviderSearchCategory.Single -> "single"
    ProviderSearchCategory.Playlist -> "playlist"
    ProviderSearchCategory.Album -> "album"
    ProviderSearchCategory.User -> "user"
}
