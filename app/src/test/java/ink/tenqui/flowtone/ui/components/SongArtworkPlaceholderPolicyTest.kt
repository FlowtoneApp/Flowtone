package ink.tenqui.flowtone.ui.components

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
}
