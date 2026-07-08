package ink.tenqui.flowtone.data.search

interface SearchSource {
    suspend fun search(query: SearchQuery): List<SearchResult>
}
