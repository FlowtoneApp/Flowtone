package ink.tenqui.flowtone.ui.components

import coil3.decode.DataSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongArtworkPlaceholderPolicyTest {
    @Test
    fun noArtworkUsesPlaceholder() {
        assertTrue(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = false,
                loadState = SongArtworkLoadState.Loading,
                hasKnownCachedArtwork = false
            )
        )
    }

    @Test
    fun uncachedLoadingUsesPlaceholderButKnownCacheDoesNot() {
        assertTrue(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Loading,
                hasKnownCachedArtwork = false
            )
        )
        assertFalse(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Loading,
                hasKnownCachedArtwork = true
            )
        )
    }

    @Test
    fun successfulArtworkStaysVisibleAndFailureReturnsToPlaceholder() {
        assertFalse(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                hasKnownCachedArtwork = false
            )
        )
        assertTrue(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Failure,
                hasKnownCachedArtwork = true
            )
        )
    }

    @Test
    fun newArtworkIdentityStartsAsUncachedLoadingInsteadOfRetainingPriorArtwork() {
        assertTrue(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Loading,
                hasKnownCachedArtwork = false
            )
        )
    }

    @Test
    fun placeholderRemainsBelowArtworkUntilNetworkRevealCompletes() {
        assertTrue(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                hasKnownCachedArtwork = false,
                revealInProgress = true
            )
        )
        assertFalse(
            shouldShowSongArtworkPlaceholder(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                hasKnownCachedArtwork = false,
                revealInProgress = false
            )
        )
    }

    @Test
    fun onlyFirstNetworkSuccessRevealsArtwork() {
        assertTrue(
            shouldRevealSongArtwork(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                dataSource = DataSource.NETWORK,
                hasSuccessfulPresentation = false
            )
        )
        assertFalse(
            shouldRevealSongArtwork(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                dataSource = DataSource.NETWORK,
                hasSuccessfulPresentation = true
            )
        )
    }

    @Test
    fun cachedAndFailedArtworkDoNotStartReveal() {
        assertFalse(
            shouldRevealSongArtwork(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                dataSource = DataSource.MEMORY_CACHE,
                hasSuccessfulPresentation = false
            )
        )
        assertFalse(
            shouldRevealSongArtwork(
                hasArtworkSource = true,
                loadState = SongArtworkLoadState.Success,
                dataSource = DataSource.DISK,
                hasSuccessfulPresentation = false
            )
        )
        assertFalse(
            shouldRevealSongArtwork(
                hasArtworkSource = false,
                loadState = SongArtworkLoadState.Failure,
                dataSource = DataSource.NETWORK,
                hasSuccessfulPresentation = false
            )
        )
    }
}
