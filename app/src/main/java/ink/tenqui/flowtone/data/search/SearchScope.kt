package ink.tenqui.flowtone.data.search

/** 搜索 UI 的范围。Provider 使用 extensionId，而不是音乐来源 host。 */
sealed interface SearchScope {
    data object All : SearchScope
    data object Local : SearchScope
    data class Provider(val extensionId: String) : SearchScope
}

data class SearchProviderOption(
    val extensionId: String,
    val name: String,
    /** 来自 manifest 的可选 #RRGGBB 品牌色，UI 决定其具体使用方式。 */
    val color: String? = null,
    val visual: SearchProviderVisual? = null
)
