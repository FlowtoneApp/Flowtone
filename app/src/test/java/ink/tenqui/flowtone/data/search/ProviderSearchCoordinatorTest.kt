package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSearchCallResult
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchPage
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import ink.tenqui.flowtone.data.online.ProviderSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSearchCoordinatorTest {
    @Test fun switchingToUnloadedAlbumRequestsItsFirstPage() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val initial = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield(); fake.complete(0, page("a")); initial.await()
        val album = async { coordinator.selectCategory(ProviderSearchCategory.Album) }; yield()
        assertEquals(ProviderSearchCategory.Album, fake.calls[1].request.category); assertEquals(null, fake.calls[1].request.cursor)
        fake.complete(1, page("album")); album.await()
    }

    @Test fun loadMoreDoesNothingWithoutNextCursor() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val initial = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield(); fake.complete(0, page("a")); initial.await()
        coordinator.loadMore(); coordinator.loadMore(); coordinator.loadMore()
        assertEquals(1, fake.calls.size); assertFalse(state.value.providerCategoryState(ProviderSearchCategory.Single).isLoadingMore)
    }

    @Test fun delayedOldKeywordFailureCannotPolluteNewKeyword() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val old = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield()
        val newer = async { coordinator.startSearch("miku expo", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield()
        fake.complete(1, page("new")); newer.await(); fake.fail(0); old.await()
        val result = state.value.providerCategoryState(ProviderSearchCategory.Single)
        assertEquals("miku expo", state.value.queryText); assertEquals(listOf("new"), result.items.map { it.id }); assertEquals(null, result.error); assertFalse(result.isInitialLoading)
    }

    @Test fun delayedOldScopeSuccessAndFailureCannotPolluteNewScope() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val old = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield()
        val newer = async { coordinator.startSearch("miku", SearchScope.Provider("b"), ProviderSearchCategory.Single) }; yield()
        fake.complete(1, page("b")); newer.await(); fake.complete(0, page("a")); old.await()
        assertEquals(SearchScope.Provider("b"), state.value.scope); assertEquals(listOf("b"), state.value.providerCategoryState(ProviderSearchCategory.Single).items.map { it.id })
    }

    @Test fun delayedOldScopeFailureCannotPolluteNewScope() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val old = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield()
        val newer = async { coordinator.startSearch("miku", SearchScope.Provider("b"), ProviderSearchCategory.Single) }; yield()
        fake.complete(1, page("b")); newer.await(); fake.fail(0); old.await()
        val result = state.value.providerCategoryState(ProviderSearchCategory.Single); assertEquals(listOf("b"), result.items.map { it.id }); assertEquals(null, result.error)
    }

    @Test fun playlistCompletionAfterAlbumSelectionUpdatesOnlyPlaylistCache() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val playlist = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Playlist) }; yield()
        val album = async { coordinator.selectCategory(ProviderSearchCategory.Album) }; yield(); fake.complete(1, page("album")); album.await(); fake.complete(0, page("playlist")); playlist.await()
        assertEquals(ProviderSearchCategory.Album, state.value.selectedProviderCategory)
        assertEquals(listOf("album"), state.value.providerCategoryState(ProviderSearchCategory.Album).items.map { it.id })
        assertEquals(listOf("playlist"), state.value.providerCategoryState(ProviderSearchCategory.Playlist).items.map { it.id })
    }

    @Test fun singleLoadMoreCompletionAfterPlaylistSelectionUpdatesOnlySingleCache() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState()); val coordinator = ProviderSearchCoordinator(state, fake)
        val first = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }; yield(); fake.complete(0, page("a", cursor = "single-2")); first.await()
        val more = async { coordinator.loadMore() }; yield(); val playlist = async { coordinator.selectCategory(ProviderSearchCategory.Playlist) }; yield(); fake.complete(2, page("p")); playlist.await(); fake.complete(1, page("b")); more.await()
        assertEquals(ProviderSearchCategory.Playlist, state.value.selectedProviderCategory)
        assertEquals(listOf("a", "b"), state.value.providerCategoryState(ProviderSearchCategory.Single).items.map { it.id })
        assertEquals(listOf("p"), state.value.providerCategoryState(ProviderSearchCategory.Playlist).items.map { it.id })
    }
    @Test fun initialSearchRequestsOnlySelectedCategoryAndSwitchingIsLazy() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState())
        val coordinator = ProviderSearchCoordinator(state, fake)
        val single = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }
        yield(); assertEquals(listOf(ProviderSearchCategory.Single), fake.calls.map { it.request.category })
        fake.complete(0, page("a")); single.await()
        val playlist = async { coordinator.selectCategory(ProviderSearchCategory.Playlist) }
        yield(); assertEquals(ProviderSearchCategory.Playlist, fake.calls[1].request.category)
        fake.complete(1, page("p")); playlist.await()
        coordinator.selectCategory(ProviderSearchCategory.Single)
        assertEquals(2, fake.calls.size)
    }

    @Test fun loadMoreUsesOpaqueCursorDeduplicatesAndRetriesAfterFailure() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState())
        val coordinator = ProviderSearchCoordinator(state, fake)
        val initial = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Single) }
        yield(); fake.complete(0, page("a", "b", cursor = "opaque-2")); initial.await()
        val more = async { coordinator.loadMore() }; yield(); coordinator.loadMore(); coordinator.loadMore()
        assertEquals(2, fake.calls.size); assertEquals("opaque-2", fake.calls[1].request.cursor)
        fake.fail(1); more.await()
        assertEquals("opaque-2", state.value.providerCategoryState(ProviderSearchCategory.Single).nextCursor)
        val retry = async { coordinator.loadMore() }; yield(); assertEquals(3, fake.calls.size)
        fake.complete(2, page("b", "c")); retry.await()
        assertEquals(listOf("a", "b", "c"), state.value.providerCategoryState(ProviderSearchCategory.Single).items.map { it.id })
    }

    @Test fun keywordScopeAndCategoryRacesOnlyUpdateTheirOwnValidState() = runBlocking {
        val fake = FakeGateway(); val state = kotlinx.coroutines.flow.MutableStateFlow(GlobalSearchUiState())
        val coordinator = ProviderSearchCoordinator(state, fake)
        val old = async { coordinator.startSearch("miku", SearchScope.Provider("a"), ProviderSearchCategory.Playlist) }; yield()
        val newer = async { coordinator.startSearch("miku expo", SearchScope.Provider("b"), ProviderSearchCategory.Album) }; yield()
        fake.complete(1, page("new")); newer.await(); fake.complete(0, page("old")); old.await()
        assertEquals("miku expo", state.value.queryText)
        assertEquals(listOf("new"), state.value.providerCategoryState(ProviderSearchCategory.Album).items.map { it.id })
        assertTrue(state.value.providerCategoryState(ProviderSearchCategory.Playlist).items.isEmpty())
    }

    private fun page(vararg ids: String, cursor: String? = null) = ProviderSearchPage(ids.map(::song), cursor)
    private fun song(id: String) = ProviderSong(ExtensionTrackRef("test", id), id, "artist", searchCategory = ProviderSearchCategory.Single)

    private class FakeGateway : ProviderSearchGateway {
        data class Call(val scope: SearchScope, val request: ProviderSearchRequest, val deferred: CompletableDeferred<ProviderSearchCallResult>)
        val calls = mutableListOf<Call>()
        override suspend fun searchPage(scope: SearchScope, request: ProviderSearchRequest): ProviderSearchCallResult {
            val call = Call(scope, request, CompletableDeferred()); calls += call; return call.deferred.await()
        }
        fun complete(index: Int, page: ProviderSearchPage) { calls[index].deferred.complete(ProviderSearchCallResult.Success(page)) }
        fun fail(index: Int) { calls[index].deferred.complete(ProviderSearchCallResult.Failure(IllegalStateException("failed"))) }
    }
}
