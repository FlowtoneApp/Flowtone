package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionImage

/** Host 持有的、受限的 Provider 搜索首页内容模型。 */
data class ProviderSearchLanding(
    val blocks: List<SearchLandingBlock>
)

sealed interface SearchLandingBlock {
    val title: String?

    data class Chips(
        override val title: String?,
        val items: List<SearchLandingItem>
    ) : SearchLandingBlock

    data class TileGrid(
        override val title: String?,
        val items: List<SearchLandingItem>
    ) : SearchLandingBlock

    data class MediaRow(
        override val title: String?,
        val items: List<SearchLandingItem>
    ) : SearchLandingBlock

    data class Text(
        override val title: String?,
        val text: String
    ) : SearchLandingBlock
}

data class SearchLandingItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val artwork: ExtensionImage? = null,
    val action: SearchLandingAction? = null
)

sealed interface SearchLandingAction {
    data class Search(val query: String) : SearchLandingAction
    data class ProviderAction(val opaqueActionId: String) : SearchLandingAction
}

/** 异步 Landing 的 UI 状态；错误不会影响正常 searchSongs。 */
sealed interface ProviderSearchLandingState {
    data object Idle : ProviderSearchLandingState
    data object Loading : ProviderSearchLandingState
    data class Loaded(val landing: ProviderSearchLanding?) : ProviderSearchLandingState
    data object Error : ProviderSearchLandingState
}
