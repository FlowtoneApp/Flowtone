package ink.tenqui.flowtone.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSongPresentationTest {
    private val requestA = PlayerArtworkRequestIdentity("content://artwork/a", 768)
    private val requestB = PlayerArtworkRequestIdentity("content://artwork/b", 768)
    private val oldPainter = ColorPainter(Color.Red)
    private val newPainter = ColorPainter(Color.Blue)

    @Test
    fun loadingArtworkKeepsThePlaceholderHiddenWhileARealPainterIsRetained() {
        val presentation = presentation(
            state = PlayerArtworkLoadState.Loading,
            painter = oldPainter,
            identity = requestB,
            painterIdentity = requestA
        )

        assertFalse(shouldShowMissingArtworkPlaceholder(presentation))
    }

    @Test
    fun loadingArtworkWithoutAnyPainterCanShowThePlaceholder() {
        assertTrue(
            shouldShowMissingArtworkPlaceholder(
                presentation(
                    state = PlayerArtworkLoadState.Loading,
                    painter = null
                )
            )
        )
    }

    @Test
    fun missingAndFailedArtworkCanShowThePlaceholder() {
        assertTrue(
            shouldShowMissingArtworkPlaceholder(
                presentation(state = PlayerArtworkLoadState.Missing, painter = null)
            )
        )
        assertTrue(
            shouldShowMissingArtworkPlaceholder(
                presentation(state = PlayerArtworkLoadState.Failed, painter = null)
            )
        )
    }

    @Test
    fun readyArtworkNeverShowsThePlaceholder() {
        assertFalse(
            shouldShowMissingArtworkPlaceholder(
                presentation(state = PlayerArtworkLoadState.Ready, painter = newPainter)
            )
        )
    }

    @Test
    fun retainedArtworkFollowsThePainterVisibleAtTheCrossfadeHandoff() {
        val presentation = presentation(
            state = PlayerArtworkLoadState.Ready,
            painter = newPainter,
            identity = requestB,
            previousPainter = oldPainter,
            previousIdentity = requestA
        )

        assertSame(oldPainter, retainedPlayerArtworkForNextTarget(presentation, 0.49f)?.painter)
        assertSame(newPainter, retainedPlayerArtworkForNextTarget(presentation, 0.5f)?.painter)
    }

    @Test
    fun retainedOutgoingArtworkDoesNotReceiveTheTrackSwitchAlphaTwice() {
        val outgoing = presentation(
            state = PlayerArtworkLoadState.Ready,
            painter = oldPainter,
            identity = requestA,
            key = "song-a"
        )
        val loading = presentation(
            state = PlayerArtworkLoadState.Loading,
            painter = oldPainter,
            identity = requestB,
            painterIdentity = requestA,
            key = "song-b"
        )
        val transition = PlayerSongPresentationTransition(
            previous = outgoing,
            current = loading,
            progress = 0.4f,
            artworkCrossfadeProgress = 1f
        )

        assertEquals(1f, playerArtworkSwitchAlpha(transition, loading, 0.4f), 0f)

        val ready = loading.copy(
            artworkPainter = newPainter,
            artworkPainterIdentity = requestB,
            artworkLoadState = PlayerArtworkLoadState.Ready
        )
        assertEquals(0.4f, playerArtworkSwitchAlpha(transition.copy(current = ready), ready, 0.4f), 0f)
    }

    @Test
    fun staleArtworkResultIsRejectedWhenSongOrRequestChanged() {
        val current = presentation(
            state = PlayerArtworkLoadState.Loading,
            painter = oldPainter,
            identity = requestB,
            key = "song-b"
        )

        assertTrue(
            isCurrentPlayerArtworkRequest(
                current = current,
                requestedSongKey = "song-b",
                requestedIdentity = requestB,
                latestSongKey = "song-b",
                latestRequestIdentity = requestB
            )
        )
        assertFalse(
            isCurrentPlayerArtworkRequest(
                current = current,
                requestedSongKey = "song-a",
                requestedIdentity = requestA,
                latestSongKey = "song-b",
                latestRequestIdentity = requestB
            )
        )
        assertFalse(
            isCurrentPlayerArtworkRequest(
                current = current,
                requestedSongKey = "song-b",
                requestedIdentity = requestA,
                latestSongKey = "song-b",
                latestRequestIdentity = requestB
            )
        )
    }

    private fun presentation(
        state: PlayerArtworkLoadState,
        painter: androidx.compose.ui.graphics.painter.Painter?,
        identity: PlayerArtworkRequestIdentity? = if (painter != null) requestA else null,
        painterIdentity: PlayerArtworkRequestIdentity? = identity,
        previousPainter: androidx.compose.ui.graphics.painter.Painter? = null,
        previousIdentity: PlayerArtworkRequestIdentity? = null,
        key: String? = "song-a"
    ) = PlayerSongPresentation(
        key = key,
        lyricsSongId = 1L,
        title = "Title",
        artist = "Artist",
        imageRequest = null,
        artworkRequestIdentity = identity,
        artworkPainter = painter,
        artworkPainterIdentity = painterIdentity,
        artworkLoadState = state,
        previousArtworkPainter = previousPainter,
        previousArtworkPainterIdentity = previousIdentity
    )
}
