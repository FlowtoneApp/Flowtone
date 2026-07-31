package ink.tenqui.flowtone.ui.player.lyrics

import ink.tenqui.flowtone.ui.player.FullscreenContentMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsModelsTest {
    @Test
    fun lyricsContentIsActiveOnlyInsideFullscreenPlayback() {
        assertTrue(
            isLyricsPlaybackContentActive(
                playbackContentMode = FullscreenPlaybackContentMode.Lyrics,
                fullscreenContentMode = FullscreenContentMode.Playback,
                fullscreen = true,
                expanded = true,
                hasCurrentSong = true
            )
        )

        assertFalse(
            isLyricsPlaybackContentActive(
                playbackContentMode = FullscreenPlaybackContentMode.Lyrics,
                fullscreenContentMode = FullscreenContentMode.AddToPlaylist,
                fullscreen = true,
                expanded = true,
                hasCurrentSong = true
            )
        )
        assertFalse(
            isLyricsPlaybackContentActive(
                playbackContentMode = FullscreenPlaybackContentMode.Lyrics,
                fullscreenContentMode = FullscreenContentMode.Playback,
                fullscreen = false,
                expanded = true,
                hasCurrentSong = true
            )
        )
    }
}
