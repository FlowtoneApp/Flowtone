package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.pageElementKeysAwaitingEntry
import ink.tenqui.flowtone.viewmodel.ProviderCatalogLoadState
import ink.tenqui.flowtone.viewmodel.ProviderPlaylistDetailState
import ink.tenqui.flowtone.viewmodel.ProviderLikedSongsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLibraryPresentationTest {
    @Test
    fun providerLikedSongsDistinguishEmptyFailureAndRefreshWithoutDroppingContent() {
        val song = ProviderSong(ExtensionTrackRef("example.music", "track"), "Track", "Artist")
        val cached = ProviderLikedSongsState(songs = listOf(song))
        val empty = ProviderLikedSongsState(songs = emptyList())

        assertEquals(ProviderLibraryPresentation.Loading, providerLikedSongsPresentation(null))
        assertEquals(ProviderLibraryPresentation.Ready, providerLikedSongsPresentation(cached))
        assertEquals(ProviderLibraryPresentation.Refreshing, providerLikedSongsPresentation(cached.copy(isLoading = true)))
        assertEquals(ProviderLibraryPresentation.Ready, providerLikedSongsPresentation(cached.copy(loadFailed = true)))
        assertEquals(ProviderLibraryPresentation.Empty, providerLikedSongsPresentation(empty))
        assertEquals(ProviderLibraryPresentation.Empty, providerLikedSongsPresentation(empty.copy(isLoading = true)))
        assertEquals(ProviderLibraryPresentation.Error, providerLikedSongsPresentation(ProviderLikedSongsState(loadFailed = true)))
    }

    @Test
    fun initialRequestsShowLoadingBeforeAndAfterTheRequestStateIsPublished() {
        assertPresentations(
            ProviderLibraryPresentation.Loading,
            catalog = null,
            detail = null
        )
        assertPresentations(
            ProviderLibraryPresentation.Loading,
            catalog = ProviderCatalogLoadState.Loading,
            detail = ProviderPlaylistDetailState(isLoading = true)
        )
    }

    @Test
    fun completedRequestsWithContentReplaceLoadingRows() {
        assertPresentations(
            ProviderLibraryPresentation.Ready,
            catalog = ProviderCatalogLoadState.Loaded,
            detail = ProviderPlaylistDetailState(),
            hasContent = true
        )
    }

    @Test
    fun successfulEmptyResultsAndFailuresUseDifferentMessages() {
        assertPresentations(
            ProviderLibraryPresentation.Empty,
            catalog = ProviderCatalogLoadState.Loaded,
            detail = ProviderPlaylistDetailState()
        )
        assertPresentations(
            ProviderLibraryPresentation.Error,
            catalog = ProviderCatalogLoadState.Failed,
            detail = ProviderPlaylistDetailState(loadFailed = true)
        )
        assertEquals(
            ProviderLibraryPresentation.Error,
            providerCatalogPresentation(false, ProviderCatalogLoadState.Unavailable, false)
        )
    }

    @Test
    fun existingContentRemainsVisibleDuringRefreshAndAfterFailure() {
        assertPresentations(
            ProviderLibraryPresentation.Refreshing,
            catalog = ProviderCatalogLoadState.Loaded,
            detail = ProviderPlaylistDetailState(isLoading = true),
            hasContent = true,
            isRefreshing = true
        )
        assertPresentations(
            ProviderLibraryPresentation.Ready,
            catalog = ProviderCatalogLoadState.Failed,
            detail = ProviderPlaylistDetailState(loadFailed = true),
            hasContent = true
        )
    }

    @Test
    fun catalogRetryLoadsWhenNoSuccessfulResultExists() {
        assertEquals(
            ProviderLibraryPresentation.Loading,
            providerCatalogPresentation(false, ProviderCatalogLoadState.Failed, true)
        )
        assertEquals(
            ProviderLibraryPresentation.Empty,
            providerCatalogPresentation(false, ProviderCatalogLoadState.Loaded, true)
        )
    }

    @Test
    fun loadingKeysAreStableAndSeparateFromAllRealRowKeyFormats() {
        val identity = ProviderEntityIdentity("test-provider", providerDetailSkeletonKey(0))
        val realKeys = providerPlaylistSelectionKeys(listOf(identity, identity)) +
            listOf(identity.stableKey, "provider-playlist:${identity.stableKey}")
        val songSkeletons = List(ProviderDetailSkeletonCount, ::providerDetailSkeletonKey)
        val playlistSkeletons = List(ProviderPlaylistSkeletonCount) { index ->
            providerPlaylistSkeletonKey(identity.providerId, index)
        }
        val loadingKeys = songSkeletons + playlistSkeletons

        assertEquals(loadingKeys.size, loadingKeys.toSet().size)
        assertEquals(songSkeletons, List(ProviderDetailSkeletonCount, ::providerDetailSkeletonKey))
        assertTrue(loadingKeys.intersect(realKeys.toSet()).isEmpty())
    }

    @Test
    fun rowsLoadedDuringPageEntranceStartTheirOwnBatchAndKeepItWhenPageSettles() {
        val loadedRows = listOf("cached", "newly-loaded")
        val enteredKeys = setOf("cached", providerDetailSkeletonKey(0))
        val incomingKeys = providerRowEnterKeys(loadedRows, PageTransitionPhase.Incoming)
        val incomingBatch = pageElementKeysAwaitingEntry(incomingKeys, enteredKeys, emptySet())
        assertEquals(listOf("newly-loaded"), incomingBatch)

        val currentKeys = providerRowEnterKeys(loadedRows, PageTransitionPhase.Current)
        assertEquals(emptyList<Any>(), pageElementKeysAwaitingEntry(currentKeys, enteredKeys, incomingBatch.toSet()))
        assertEquals(emptyList<String>(), providerRowEnterKeys(loadedRows, PageTransitionPhase.Outgoing))
    }

    private fun assertPresentations(
        expected: ProviderLibraryPresentation,
        catalog: ProviderCatalogLoadState?,
        detail: ProviderPlaylistDetailState?,
        hasContent: Boolean = false,
        isRefreshing: Boolean = false
    ) {
        assertEquals(expected, providerCatalogPresentation(hasContent, catalog, isRefreshing))
        assertEquals(expected, providerPlaylistDetailPresentation(hasContent, detail))
    }
}
