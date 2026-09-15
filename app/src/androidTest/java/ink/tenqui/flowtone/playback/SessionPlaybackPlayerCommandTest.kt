package ink.tenqui.flowtone.playback

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPlaybackPlayerCommandTest {
    @Test
    fun resolvingLogicalCurrentItemExposesCurrentItemSeekWhenRawEngineIsEmpty() {
        val commands = sessionAvailableCommands(
            baseCommands = Player.Commands.EMPTY,
            logicalCurrentItemIsSeekable = true
        )

        assertTrue(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun noLogicalCurrentItemDoesNotExposeCurrentItemSeek() {
        val commands = sessionAvailableCommands(
            baseCommands = Player.Commands.EMPTY,
            logicalCurrentItemIsSeekable = false
        )

        assertFalse(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }
}
