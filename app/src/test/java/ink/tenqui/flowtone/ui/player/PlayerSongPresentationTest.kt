package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.coroutines.runBlocking
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
    fun loadingTargetKeepsThePreviousCoverFullyAvailable() {
        val loading = preparePlayerArtworkTarget(
            current = artwork(request = requestA, painter = oldPainter),
            imageRequest = null,
            requestIdentity = requestB,
            largeArtworkRequestIdentity = null,
            crossfadeProgress = 1f
        )

        assertEquals(PlayerArtworkLoadState.Loading, loading.loadState)
        assertSame(oldPainter, loading.basePainter)
        assertEquals(requestA, loading.baseIdentity)
        assertFalse(shouldShowMissingArtworkPlaceholder(loading))
    }

    @Test
    fun noCoverIsRepresentedByThePlaceholder() {
        val missing = artwork(request = null, painter = null)
        val failed = missing.copy(loadState = PlayerArtworkLoadState.Failed)

        assertTrue(shouldShowMissingArtworkPlaceholder(missing))
        assertTrue(shouldShowMissingArtworkPlaceholder(failed))
        assertEquals(1f, playerArtworkPlaceholderAlpha(missing), 0f)
    }

    @Test
    fun readyCoverDoesNotShowThePlaceholder() {
        assertFalse(shouldShowMissingArtworkPlaceholder(artwork(requestA, oldPainter)))
    }

    @Test
    fun loadedTargetStartsAtZeroProgressRegardlessOfCacheTiming() {
        val loading = preparePlayerArtworkTarget(
            current = artwork(request = requestA, painter = oldPainter),
            imageRequest = null,
            requestIdentity = requestB,
            largeArtworkRequestIdentity = null,
            crossfadeProgress = 1f
        )

        val ready = requireNotNull(
            playerArtworkReadyForTarget(loading, requestB, newPainter)
        )

        assertTrue(ready.isTransitioning)
        assertEquals(0f, ready.progress, 0f)
        assertSame(oldPainter, ready.basePainter)
        assertSame(newPainter, ready.targetPainter)
        assertEquals(0f, playerArtworkPlaceholderAlpha(ready), 0f)
    }

    @Test
    fun newTargetIsNeverPublishedWithThePreviousCompletedProgress() = runBlocking {
        val progress = Animatable(1f)
        val loading = preparePlayerArtworkTarget(
            current = artwork(request = requestA, painter = oldPainter),
            imageRequest = null,
            requestIdentity = requestB,
            largeArtworkRequestIdentity = null,
            crossfadeProgress = progress.value
        )
        val ready = requireNotNull(
            playerArtworkReadyForTarget(loading, requestB, newPainter)
        )
        var published: PlayerArtworkPresentation? = null

        publishPlayerArtworkPresentation(ready, progress) { presentation ->
            published = presentation
            assertSame(newPainter, presentation.targetPainter)
            assertSame(oldPainter, presentation.basePainter)
            assertEquals(0f, progress.value, 0f)
        }

        assertSame(ready, published)
    }

    @Test
    fun sameArtworkIdentityIsReusedWithoutATransition() {
        val current = artwork(request = requestA, painter = oldPainter)
        val reused = preparePlayerArtworkTarget(
            current = current,
            imageRequest = null,
            requestIdentity = requestA,
            largeArtworkRequestIdentity = null,
            crossfadeProgress = 1f
        )

        assertFalse(reused.isTransitioning)
        assertEquals(PlayerArtworkLoadState.Ready, reused.loadState)
        assertSame(oldPainter, reused.basePainter)
        assertEquals(requestA, reused.baseIdentity)
    }

    @Test
    fun rapidRetargetingKeepsTheMoreVisibleCoverAsTheNextBase() {
        val transition = artwork(
            request = requestB,
            painter = oldPainter,
            baseIdentity = requestA
        ).copy(
            targetPainter = newPainter,
            targetIdentity = requestB,
            progress = 0.49f,
            isTransitioning = true
        )

        assertSame(
            oldPainter,
            retainedPlayerArtworkForNextTarget(transition, 0.49f)?.painter
        )
        assertSame(
            newPainter,
            retainedPlayerArtworkForNextTarget(transition, 0.5f)?.painter
        )
    }

    @Test
    fun failureTransitionsFromThePreviousCoverToThePlaceholder() {
        val loading = preparePlayerArtworkTarget(
            current = artwork(request = requestA, painter = oldPainter),
            imageRequest = null,
            requestIdentity = requestB,
            largeArtworkRequestIdentity = null,
            crossfadeProgress = 1f
        )
        val failed = requireNotNull(playerArtworkFailedForTarget(loading, requestB))

        assertTrue(failed.targetIsPlaceholder)
        assertSame(oldPainter, failed.basePainter)
        assertEquals(0f, playerArtworkPlaceholderAlpha(failed), 0f)

        val completed = completePlayerArtworkTransition(failed)
        assertEquals(PlayerArtworkLoadState.Failed, completed.loadState)
        assertTrue(shouldShowMissingArtworkPlaceholder(completed))
        assertEquals(1f, playerArtworkPlaceholderAlpha(completed), 0f)
    }

    @Test
    fun staleArtworkResultIsRejectedAfterTheTargetChanges() {
        val current = preparePlayerArtworkTarget(
            current = artwork(request = requestA, painter = oldPainter),
            imageRequest = null,
            requestIdentity = requestB,
            largeArtworkRequestIdentity = null,
            crossfadeProgress = 1f
        )

        assertTrue(isCurrentPlayerArtworkRequest(current, requestB, requestB))
        assertFalse(isCurrentPlayerArtworkRequest(current, requestA, requestB))
        assertFalse(
            isCurrentPlayerArtworkRequest(
                current.copy(requestIdentity = requestA),
                requestA,
                requestB
            )
        )
    }

    private fun artwork(
        request: PlayerArtworkRequestIdentity?,
        painter: androidx.compose.ui.graphics.painter.Painter?,
        baseIdentity: PlayerArtworkRequestIdentity? = request
    ) = PlayerArtworkPresentation(
        imageRequest = null,
        requestIdentity = request,
        basePainter = painter,
        baseIdentity = baseIdentity,
        loadState = if (painter == null) {
            PlayerArtworkLoadState.Missing
        } else {
            PlayerArtworkLoadState.Ready
        }
    )
}
