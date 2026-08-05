package ink.tenqui.flowtone.ui.player.lyrics

import ink.tenqui.flowtone.lyrics.LyricLine
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

    @Test
    fun pureMusicNoticeAllowsSurroundingBlankLines() {
        val lines = listOf(
            LyricLine(timestampMs = 0L, text = ""),
            LyricLine(timestampMs = 10L, text = "  纯音乐，请欣赏  "),
            LyricLine(timestampMs = 20L, text = "")
        )

        assertTrue(isPureMusicNotice(lines))
    }

    @Test
    fun pureMusicNoticeMustBeTheOnlyActualContent() {
        val lines = listOf(
            LyricLine(timestampMs = 0L, text = "纯音乐，请欣赏"),
            LyricLine(timestampMs = 1_000L, text = "另一句歌词")
        )

        assertFalse(isPureMusicNotice(lines))
    }
}
