package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.ui.player.lyrics.FullscreenPlaybackContentMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerBackBehaviorTest {
    @Test
    fun `system back returns from lyrics to artwork`() {
        assertTrue(
            shouldReturnFromLyricsToArtwork(
                fullscreenContentMode = FullscreenContentMode.Playback,
                fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.Lyrics,
                fullscreenInteractionActive = true
            )
        )
    }

    @Test
    fun `system back is not consumed by artwork or another fullscreen content mode`() {
        assertFalse(
            shouldReturnFromLyricsToArtwork(
                fullscreenContentMode = FullscreenContentMode.Playback,
                fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.Artwork,
                fullscreenInteractionActive = true
            )
        )
        assertFalse(
            shouldReturnFromLyricsToArtwork(
                fullscreenContentMode = FullscreenContentMode.SongInfo,
                fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.Lyrics,
                fullscreenInteractionActive = true
            )
        )
    }

    @Test
    fun `system back waits until fullscreen interaction is active`() {
        assertFalse(
            shouldReturnFromLyricsToArtwork(
                fullscreenContentMode = FullscreenContentMode.Playback,
                fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.Lyrics,
                fullscreenInteractionActive = false
            )
        )
    }
}
