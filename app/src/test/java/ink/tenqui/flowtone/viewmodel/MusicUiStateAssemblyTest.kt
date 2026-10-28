package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.online.ProviderSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MusicUiStateAssemblyTest {
    @Test
    fun defaultSlicesPreserveExistingUiStateDefaults() {
        assertEquals(
            MusicUiState(),
            assembleMusicUiState(
                LocalLibraryState(),
                ProviderLibraryState(),
                PlaybackQueuePresentation(),
                MusicFeedbackState(),
                emptyList(),
                ListeningStatsSnapshot()
            )
        )
    }

    @Test
    fun catalogQueueAndFeedbackStayInTheirOwnFields() {
        val songs = listOf(ProviderSong(ExtensionTrackRef("a", "song"), "Song", "Artist"))
        val provider = ProviderLibraryState(
            providerSongs = mapOf("a" to songs),
            providerSongCatalogLoadStates = mapOf("a" to ProviderCatalogLoadState.Loaded),
            libraryProviderSourcesResolved = true
        )
        val local = LocalLibraryState(
            hasPermission = true,
            isLoading = true,
            hasScanned = true,
            errorMessage = "local error"
        )
        val queue = PlaybackQueuePresentation(
            currentQueueIndex = 4,
            trackPlaybackErrorMessage = "track error",
            trackPlaybackErrorEventId = 9L
        )
        val failure = ExtensionContentFailure(1L, "Failure", "Details")
        val feedback = MusicFeedbackState(
            extensionContentFailures = listOf(failure),
            libraryRefreshingSources = setOf("extension:a"),
            libraryRefreshFailure = "local" to 2L
        )
        val liked = listOf(PersistentTrack.Local("7"))
        val stats = ListeningStatsSnapshot()
        val ui = assembleMusicUiState(local, provider, queue, feedback, liked, stats)

        assertEquals(local.hasPermission, ui.hasPermission)
        assertEquals(local.isLoading, ui.isLoading)
        assertEquals(local.hasScanned, ui.hasScanned)
        assertEquals("local error", ui.errorMessage)
        assertSame(provider.providerSongs, ui.providerSongs)
        assertSame(provider.providerSongCatalogLoadStates, ui.providerSongCatalogLoadStates)
        assertEquals(true, ui.libraryProviderSourcesResolved)
        assertEquals(4, ui.currentQueueIndex)
        assertEquals("track error", ui.trackPlaybackErrorMessage)
        assertEquals(9L, ui.trackPlaybackErrorEventId)
        assertSame(feedback.extensionContentFailures, ui.extensionContentFailures)
        assertSame(feedback.libraryRefreshingSources, ui.libraryRefreshingSources)
        assertEquals("local" to 2L, ui.libraryRefreshFailure)
        assertSame(liked, ui.likedTracks)
        assertSame(stats, ui.listeningStats)
    }
}
