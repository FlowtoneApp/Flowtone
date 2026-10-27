package ink.tenqui.flowtone.playback

import ink.tenqui.flowtone.data.online.runtime.isValidPlaybackResourceUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLinkFailureTest {
    @Test
    fun resolverClassifiesNullAndExceptionsAndKeepsSuccessfulResult() = runBlocking {
        val original = IllegalStateException("JavaScript failure")
        val thrown = runCatching { resolvePlaybackLink<String> { throw original } }.exceptionOrNull()
        val empty = runCatching { resolvePlaybackLink<String> { null } }.exceptionOrNull()

        assertTrue(thrown is PlaybackLinkResolutionFailure)
        assertSame(original, thrown?.cause)
        assertEquals("获取播放链接失败", playbackFailureDetails(thrown!!))
        assertTrue(empty is PlaybackLinkResolutionFailure)
        assertEquals("获取播放链接失败", playbackFailureDetails(empty!!))
        assertEquals("https://example.com/song.mp3", resolvePlaybackLink { "https://example.com/song.mp3" })
    }

    @Test
    fun resolverPropagatesCancellationWithoutReportingFailure() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val result = runCatching { resolvePlaybackLink<String> { throw cancellation } }
        assertSame(cancellation, result.exceptionOrNull())
    }

    @Test
    fun linkFailureKeepsDiagnosticCauseButOnlyPresentsSafeMessage() {
        val original = IllegalStateException("EvaluationFailedException: private JS stack")
        val failure = PlaybackLinkResolutionFailure(original)

        assertSame(original, failure.cause)
        assertEquals("获取播放链接失败", failure.message)
        assertEquals("获取播放链接失败", playbackFailureDetails(failure))
        assertFalse(playbackFailureDetails(failure).contains("EvaluationFailedException"))
    }

    @Test
    fun playerErrorStopsPlaybackWithoutChangingTheCurrentTarget() {
        val initial = PlaybackState(
            currentQueueId = "online-1",
            currentQueueIndex = 3,
            isPlaying = true,
            playWhenReady = true,
            isBuffering = true
        )
        val updated = initial.withPlayerError(
            message = PlaybackLinkFailureMessage,
            details = playbackFailureDetails(PlaybackLinkResolutionFailure(IllegalStateException("JS"))),
            eventId = 7
        )

        assertFalse(updated.isPlaying)
        assertFalse(updated.playWhenReady)
        assertFalse(updated.isBuffering)
        assertEquals("online-1", updated.currentQueueId)
        assertEquals(3, updated.currentQueueIndex)
        assertEquals("获取播放链接失败", updated.errorMessage)
        assertEquals("获取播放链接失败", updated.errorDetails)
        assertEquals(7L, updated.errorEventId)
    }

    @Test
    fun unrelatedPlaybackFailureKeepsItsOriginalDetails() {
        val error = IllegalStateException("stream network failed")
        val details = playbackFailureDetails(error)
        val state = PlaybackState().withPlayerError("stream network failed", details, 1)

        assertEquals("stream network failed", state.errorMessage)
        assertTrue(state.errorDetails.orEmpty().contains("stream network failed"))
        assertTrue(state.errorDetails.orEmpty().contains("IllegalStateException"))
    }

    @Test
    fun legacyHttpsPlaybackUrlsRemainValidAndMalformedUrlsAreRejected() {
        assertTrue(isValidPlaybackResourceUrl("https://example.com/song.mp3"))
        assertTrue(isValidPlaybackResourceUrl("https://cdn.example.com/live.m3u8?token=a%2Fb"))
        assertTrue(isValidPlaybackResourceUrl("https://example.com:8443/song"))
        assertFalse(isValidPlaybackResourceUrl(""))
        assertFalse(isValidPlaybackResourceUrl("http://example.com/song"))
        assertFalse(isValidPlaybackResourceUrl("https://"))
        assertFalse(isValidPlaybackResourceUrl("https://example.com:99999/song"))
        assertFalse(isValidPlaybackResourceUrl("https://example.com/a b"))
    }
}
