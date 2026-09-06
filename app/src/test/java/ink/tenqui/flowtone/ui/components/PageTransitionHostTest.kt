package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTransitionHostTest {
    @Test fun reversingAtPartialEntryUsesOnlyCurrentRemainingProgress() {
        assertEquals(0.4f, pageTransitionRemainingFraction(0.4f, 0f), 0.0001f)
        assertEquals(0.6f, pageTransitionRemainingFraction(0.4f, 1f), 0.0001f)
    }

    @Test fun profileCardEntersFromNegativeYAndSettlesAtZero() {
        val start = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0f,
            signedOffsetYPx = -9.6f
        )
        val end = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 1f,
            signedOffsetYPx = -9.6f
        )

        assertTrue(start.translationY < 0f)
        assertEquals(0f, end.translationY, 0.0001f)
        assertEquals(0f, start.alpha, 0.0001f)
        assertEquals(1f, end.alpha, 0.0001f)
    }

    @Test fun artistBackButtonUsesTheSameNegativeYDirection() {
        val start = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0f,
            signedOffsetYPx = -8f
        )
        val reversing = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0.4f,
            signedOffsetYPx = -8f
        )

        assertEquals(-8f, start.translationY, 0.0001f)
        assertTrue(reversing.translationY in -8f..0f)
        assertEquals(0.4f, reversing.alpha, 0.0001f)
    }

    @Test fun backAtEverySampledProgressTargetsZeroImmediately() {
        listOf(0.2f, 0.5f, 0.7f, 0.8f, 0.9f, 0.95f, 0.99f).forEach { progress ->
            val action = pageTransitionRetargetAction(
                requestedTarget = "artist",
                outgoing = "artist",
                incoming = "album",
                samePage = String::equals
            )

            assertEquals(PageTransitionRetargetAction.ReverseToOutgoing, action)
            assertEquals(0f, pageTransitionTargetEndpoint(action), 0.0001f)
            assertEquals(
                (PageMotion.DurationMillis * progress).toInt(),
                pageTransitionDurationMillis(progress, 0f)
            )
        }
    }

    @Test fun cancelledForwardGenerationCannotCommitItsEndpoint() {
        val forwardGeneration = Any()
        val backGeneration = Any()

        assertFalse(
            pageTransitionEndpointCommitAllowed(
                animationGeneration = forwardGeneration,
                activeGeneration = backGeneration,
                requestedTargetIsStillActive = false,
                currentProgress = 1f,
                targetProgress = 1f
            )
        )
        assertTrue(
            pageTransitionEndpointCommitAllowed(
                animationGeneration = backGeneration,
                activeGeneration = backGeneration,
                requestedTargetIsStillActive = true,
                currentProgress = 0f,
                targetProgress = 0f
            )
        )
    }

    @Test fun anEndpointCannotCommitBeforeTheAnimatableActuallyReachesIt() {
        val generation = Any()

        assertFalse(
            pageTransitionEndpointCommitAllowed(
                animationGeneration = generation,
                activeGeneration = generation,
                requestedTargetIsStillActive = true,
                currentProgress = 0.99f,
                targetProgress = 1f
            )
        )
    }

    @Test fun forwardDuringReverseTargetsOneFromTheCurrentProgress() {
        val action = pageTransitionRetargetAction(
            requestedTarget = "album",
            outgoing = "artist",
            incoming = "album",
            samePage = String::equals
        )

        assertEquals(PageTransitionRetargetAction.KeepIncoming, action)
        assertEquals(1f, pageTransitionTargetEndpoint(action), 0.0001f)
        assertEquals(240, pageTransitionDurationMillis(0.4f, 1f))
    }

    @Test fun tinyRetargetDistanceStillHasANonZeroDuration() {
        assertEquals(1, pageTransitionDurationMillis(0.5f, 0.50001f))
    }

    @Test fun aGenuinelyNewTargetReplacesTheRetainedIncomingSlotImmediately() {
        assertEquals(
            PageTransitionRetargetAction.ReplaceIncoming,
            pageTransitionRetargetAction(
                requestedTarget = "artist-a-2",
                outgoing = "artist-a-1",
                incoming = "main",
                samePage = String::equals
            )
        )
    }

    @Test fun reversibleTargetsKeepUsingTheExistingSlotPair() {
        assertEquals(
            PageTransitionRetargetAction.ReverseToOutgoing,
            pageTransitionRetargetAction("artist", "artist", "album", String::equals)
        )
        assertEquals(
            PageTransitionRetargetAction.KeepIncoming,
            pageTransitionRetargetAction("album", "artist", "album", String::equals)
        )
    }
}
