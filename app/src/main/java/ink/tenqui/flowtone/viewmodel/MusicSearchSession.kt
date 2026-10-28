package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.data.search.GlobalSearchUiState
import ink.tenqui.flowtone.data.search.ProviderSearchCategoryState
import ink.tenqui.flowtone.data.search.ProviderSearchCoordinator
import ink.tenqui.flowtone.data.search.ExtensionManagerProviderSearchGateway
import ink.tenqui.flowtone.data.search.startRequest
import ink.tenqui.flowtone.data.search.SearchQuery
import ink.tenqui.flowtone.data.search.SearchRepository
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.data.online.ProviderSearchLandingState
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import ink.tenqui.flowtone.data.search.SearchProviderOption

/** 持有查询、任务和搜索结果；Provider 分页继续复用现有协调器。 */
internal class MusicSearchSession(
    private val scope: CoroutineScope,
    private val extensionManager: ExtensionManager,
    private val appPreferences: AppPreferences,
    private val onFailure: (String, String, Throwable) -> Unit
) {
    private val searchRepository = SearchRepository()
    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var providerSearchJob: Job? = null
    private var searchLandingJob: Job? = null
    private var searchLandingGeneration = 0L
    private val providerSearchCoordinator = ProviderSearchCoordinator(
        state = _state,
        gateway = ExtensionManagerProviderSearchGateway(extensionManager),
        onFailure = { scope, category, error ->
            val source = when (scope) {
                SearchScope.All -> "所有来源"
                SearchScope.Local -> "本地音乐"
                is SearchScope.Provider -> "扩展 ID：${scope.extensionId}"
            }
            reportExtensionContentFailure(
                title = "在线搜索失败",
                context = "搜索类别：$category\n搜索来源：$source",
                error = error
            )
        }
    )

    private fun reportExtensionContentFailure(title: String, context: String, error: Throwable) {
        onFailure(title, context, error)
    }

    fun updateProviders(providers: List<SearchProviderOption>) {
        val preferredScope = searchScopeFromPreference(appPreferences.getSearchScopePreference())
        _state.update { current ->
            val selected = when (val existing = if (current.providerOptions.isEmpty()) preferredScope else current.scope) {
                is SearchScope.Provider -> existing.takeIf { candidate -> providers.any { it.extensionId == candidate.extensionId } }
                    ?: SearchScope.All
                else -> existing
            }
            current.copy(scope = selected, providerOptions = providers)
        }
        if (_state.value.query.isBlank) loadSearchLandingForCurrentScope()
    }

    fun invalidateRuntime() {
        searchJob?.cancel()
        providerSearchJob?.cancel()
        searchLandingJob?.cancel()
        searchLandingGeneration += 1L
        _state.update { state ->
            state.copy(
                scope = if (state.scope is SearchScope.Provider) SearchScope.All else state.scope,
                providerOptions = emptyList(),
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                landingState = ProviderSearchLandingState.Idle,
                isSearching = false,
                searchGeneration = state.searchGeneration + 1L
            )
        }
    }

    fun updateSearchQuery(queryText: String) {
        updateSearchQueryForScope(queryText, _state.value.scope)
    }

    private fun updateSearchQueryForScope(queryText: String, scope: SearchScope) {
        val query = SearchQuery.from(queryText)
        searchJob?.cancel()
        providerSearchJob?.cancel()

        if (query.isBlank) {
            _state.update { current ->
                current.copy(
                    queryText = queryText,
                    scope = scope,
                    isSearching = false,
                    songResults = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    providerCategoryStates = emptyProviderSearchCategoryStates(),
                    searchGeneration = current.searchGeneration + 1
                )
            }
            loadSearchLandingForCurrentScope()
            return
        }

        _state.update { currentState ->
            currentState.copy(
                queryText = queryText,
                scope = scope,
                isSearching = scope == SearchScope.All || scope == SearchScope.Local,
                songResults = emptyList(),
                artistResults = emptyList(),
                albumResults = emptyList(),
                providerCategoryStates = providerSearchCategoryStatesForNewQuery(
                    scope = scope,
                    selectedCategory = currentState.selectedProviderCategory
                ),
                searchGeneration = currentState.searchGeneration + 1
            )
        }
        searchJob = this.scope.launch {
            delay(200)
            publishLocalSearchResults(query = query, visibleQueryText = queryText, scope = scope)
            loadInitialProviderSearchPage(acceptPreparedRequest = scope is SearchScope.Provider)
        }
    }

    fun clearSearchQuery() {
        searchJob?.cancel()
        providerSearchJob?.cancel()
        _state.update { current ->
            current.copy(
                queryText = "",
                isSearching = false,
                songResults = emptyList(),
                artistResults = emptyList(),
                albumResults = emptyList(),
                providerCategoryStates = emptyProviderSearchCategoryStates(),
                searchGeneration = current.searchGeneration + 1
            )
        }
        loadSearchLandingForCurrentScope()
    }

    fun selectSearchScope(scope: SearchScope) {
        val validScope = when (scope) {
            is SearchScope.Provider -> scope.takeIf { selected ->
                _state.value.providerOptions.any { it.extensionId == selected.extensionId }
            } ?: SearchScope.All
            else -> scope
        }
        appPreferences.setSearchScopePreference(searchScopePreferenceValue(validScope))
        val currentQuery = _state.value.queryText
        if (SearchQuery.from(currentQuery).isBlank) {
            _state.update {
                it.copy(
                    scope = validScope,
                    songResults = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    providerCategoryStates = emptyProviderSearchCategoryStates(),
                    isSearching = false,
                    searchGeneration = it.searchGeneration + 1
                )
            }
            loadSearchLandingForCurrentScope()
        } else {
            updateSearchQueryForScope(currentQuery, validScope)
        }
    }

    private suspend fun publishLocalSearchResults(
        query: SearchQuery,
        visibleQueryText: String,
        scope: SearchScope
    ) {
        val results = if (scope == SearchScope.All || scope == SearchScope.Local) {
            searchRepository.search(query)
        } else ink.tenqui.flowtone.data.search.SearchResults.Empty
        _state.update { currentState ->
            if (currentState.queryText != visibleQueryText || currentState.scope != scope) {
                currentState
            } else {
                currentState.copy(
                    isSearching = false,
                    songResults = results.songs,
                    artistResults = results.artists,
                    albumResults = results.albums
                )
            }
        }
    }

    fun selectProviderSearchCategory(category: ProviderSearchCategory) {
        _state.update { it.copy(selectedProviderCategory = category) }
        if (!_state.value.query.isBlank) loadInitialProviderSearchPage()
    }

    fun loadMoreProviderSearchResults(category: ProviderSearchCategory = _state.value.selectedProviderCategory) {
        this.scope.launch { providerSearchCoordinator.loadMore(category) }
    }

    private fun loadInitialProviderSearchPage(acceptPreparedRequest: Boolean = false) {
        providerSearchJob = this.scope.launch {
            providerSearchCoordinator.loadInitial(acceptPreparedRequest)
        }
    }

    fun updateLocalLibrary(songs: List<Song>, albums: List<LocalAlbum>): Job =
        this.scope.launch {
            searchRepository.updateLocalLibrary(songs, albums)
            val currentSearchState = _state.value
            val query = SearchQuery.from(currentSearchState.queryText)
            if (!query.isBlank) {
                searchJob?.cancel()
                _state.update { it.copy(isSearching = true) }
                publishLocalSearchResults(
                    query = query,
                    visibleQueryText = currentSearchState.queryText,
                    scope = currentSearchState.scope
                )
            }
        }

    private fun loadSearchLandingForCurrentScope() {
        searchLandingJob?.cancel()
        val generation = ++searchLandingGeneration
        val scope = _state.value.scope
        if (scope !is SearchScope.Provider) {
            _state.update { it.copy(landingState = ProviderSearchLandingState.Idle) }
            return
        }
        _state.update { it.copy(landingState = ProviderSearchLandingState.Loading) }
        searchLandingJob = this.scope.launch {
            try {
                val landing = extensionManager.getSearchLanding(scope.extensionId)
                _state.update { current ->
                    if (generation == searchLandingGeneration && current.scope == scope && current.query.isBlank) {
                        current.copy(landingState = ProviderSearchLandingState.Loaded(landing))
                    } else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == searchLandingGeneration &&
                    _state.value.scope == scope &&
                    _state.value.query.isBlank
                ) {
                    reportExtensionContentFailure(
                        title = "扩展推荐内容读取失败",
                        context = "扩展 ID：${scope.extensionId}",
                        error = error
                    )
                    _state.update { current ->
                        if (generation == searchLandingGeneration && current.scope == scope && current.query.isBlank) {
                            current.copy(landingState = ProviderSearchLandingState.Error(error.stackTraceToString()))
                        } else current
                    }
                }
            }
        }
    }

    fun close() {
        searchJob?.cancel()
        providerSearchJob?.cancel()
        searchLandingJob?.cancel()
    }
}

private fun searchScopeFromPreference(value: String): SearchScope = when {
    value == "local" -> SearchScope.Local
    value.startsWith("provider:") -> SearchScope.Provider(value.removePrefix("provider:").trim())
    else -> SearchScope.All
}

private fun searchScopePreferenceValue(scope: SearchScope): String = when (scope) {
    SearchScope.All -> "all"
    SearchScope.Local -> "local"
    is SearchScope.Provider -> "provider:${scope.extensionId}"
}

private fun emptyProviderSearchCategoryStates(): Map<ProviderSearchCategory, ProviderSearchCategoryState> =
    ProviderSearchCategory.entries.associateWith { ProviderSearchCategoryState() }

internal fun providerSearchCategoryStatesForNewQuery(
    scope: SearchScope,
    selectedCategory: ProviderSearchCategory
): Map<ProviderSearchCategory, ProviderSearchCategoryState> =
    ProviderSearchCategory.entries.associateWith { category ->
        if (scope is SearchScope.Provider && category == selectedCategory) {
            ProviderSearchCategoryState().startRequest(cursor = null)
        } else {
            ProviderSearchCategoryState()
        }
    }
