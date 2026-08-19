package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.data.online.ProviderSearchCallResult
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

interface ProviderSearchGateway {
    suspend fun searchPage(scope: SearchScope, request: ProviderSearchRequest): ProviderSearchCallResult
}

/** 仅协调 Provider 分页请求；状态仍直接写入现有 GlobalSearchUiState。 */
class ProviderSearchCoordinator(
    private val state: MutableStateFlow<GlobalSearchUiState>,
    private val gateway: ProviderSearchGateway
) {
    suspend fun startSearch(keyword: String, scope: SearchScope, category: ProviderSearchCategory) {
        state.update { current ->
            current.copy(
                queryText = keyword,
                scope = scope,
                selectedProviderCategory = category,
                providerCategoryStates = ProviderSearchCategory.entries.associateWith { ProviderSearchCategoryState() },
                searchGeneration = current.searchGeneration + 1
            )
        }
        loadInitial()
    }

    suspend fun selectCategory(category: ProviderSearchCategory) {
        state.update { it.copy(selectedProviderCategory = category) }
        loadInitial()
    }
    suspend fun loadInitial() {
        val current = state.value
        val category = current.selectedProviderCategory
        val categoryState = current.providerCategoryState(category)
        if (current.query.isBlank || current.scope == SearchScope.Local || categoryState.hasLoaded || categoryState.isInitialLoading) return
        load(category, null, current.searchGeneration)
    }

    suspend fun loadMore(category: ProviderSearchCategory = state.value.selectedProviderCategory) {
        val current = state.value
        val categoryState = current.providerCategoryState(category)
        val cursor = categoryState.nextCursor ?: return
        if (current.scope == SearchScope.Local || categoryState.isInitialLoading || categoryState.isLoadingMore) return
        load(category, cursor, current.searchGeneration)
    }

    private suspend fun load(category: ProviderSearchCategory, cursor: String?, generation: Long) {
        val before = state.value
        val keyword = before.query.text
        val scope = before.scope
        if (keyword.isBlank() || scope == SearchScope.Local) return
        val categoryState = before.providerCategoryState(category)
        if (cursor == null && (categoryState.hasLoaded || categoryState.isInitialLoading)) return
        if (cursor != null && (categoryState.isLoadingMore || categoryState.nextCursor != cursor)) return
        state.update { current ->
            if (!current.matches(generation, keyword, scope)) current
            else current.copy(providerCategoryStates = current.providerCategoryStates + (category to categoryState.startRequest(cursor)))
        }
        val result = gateway.searchPage(scope, ProviderSearchRequest(keyword, category, cursor))
        state.update { current ->
            if (!current.matches(generation, keyword, scope)) return@update current
            val previous = current.providerCategoryState(category)
            val next = when (result) {
                is ProviderSearchCallResult.Success -> previous.acceptPage(result.page, scope != SearchScope.All)
                is ProviderSearchCallResult.Failure -> previous.acceptFailure(result.exception.javaClass.simpleName)
            }
            current.copy(providerCategoryStates = current.providerCategoryStates + (category to next))
        }
    }
}

private fun GlobalSearchUiState.matches(generation: Long, keyword: String, scope: SearchScope): Boolean =
    searchGeneration == generation && query.text == keyword && this.scope == scope
