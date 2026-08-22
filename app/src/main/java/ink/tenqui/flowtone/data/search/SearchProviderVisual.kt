package ink.tenqui.flowtone.data.search

import java.io.File

/** Provider 在搜索来源选择器中的可选视觉资源。 */
data class SearchProviderVisual(
    val iconFile: File? = null,
    val iconColor: String? = null
)
