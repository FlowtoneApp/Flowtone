package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderSearchCallResult
import ink.tenqui.flowtone.data.online.ProviderSearchPage
import ink.tenqui.flowtone.data.online.ProviderSearchRequest

/** 将现有 ExtensionManager 的分页入口适配给纯 Kotlin coordinator。 */
internal class ExtensionManagerProviderSearchGateway(
    private val extensionManager: ExtensionManager
) : ProviderSearchGateway {
    override suspend fun searchPage(
        scope: SearchScope,
        request: ProviderSearchRequest
    ): ProviderSearchCallResult = when (scope) {
        SearchScope.All -> extensionManager.searchMusicProviders(request)
        SearchScope.Local -> ProviderSearchCallResult.Success(ProviderSearchPage(emptyList()))
        is SearchScope.Provider -> extensionManager.searchMusicProvider(scope.extensionId, request)
    }
}
