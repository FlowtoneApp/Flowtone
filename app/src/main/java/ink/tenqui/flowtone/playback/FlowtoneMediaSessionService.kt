package ink.tenqui.flowtone.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import ink.tenqui.flowtone.app.MainActivity
import ink.tenqui.flowtone.R

class FlowtoneMediaSessionService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val togglePlaybackOrderCommand = SessionCommand(
        ACTION_TOGGLE_PLAYBACK_ORDER,
        Bundle.EMPTY
    )
    private val playerListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            updatePlaybackOrderButton()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updatePlaybackOrderButton()
        }
    }
    private val sessionCallback = object : MediaSession.Callback {
        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            if (playerCommand.isUserSkipCommand()) {
                player?.playWhenReady = true
            }
            return SessionResult.RESULT_SUCCESS
        }

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands
                .buildUpon()
                .add(togglePlaybackOrderCommand)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(connectionResult.availablePlayerCommands)
                .setMediaButtonPreferences(
                    listOf(buildPlaybackOrderCommandButton(currentPlaybackOrderMode()))
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_PLAYBACK_ORDER) {
                val servicePlayer = player
                if (servicePlayer != null) {
                    applyPlaybackOrderMode(
                        servicePlayer,
                        nextPlaybackOrderMode(currentPlaybackOrderMode(servicePlayer))
                    )
                    updatePlaybackOrderButton()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

        return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val servicePlayer = ExoPlayer.Builder(applicationContext)
            .setHandleAudioBecomingNoisy(true)
            .build()
        servicePlayer.addListener(playerListener)
        player = servicePlayer
        mediaSession = MediaSession.Builder(this, servicePlayer)
            .setId("flowtone_service_session")
            .setCallback(sessionCallback)
            .setSessionActivity(buildOpenExpandedPlayerPendingIntent())
            .setMediaButtonPreferences(
                listOf(buildPlaybackOrderCommandButton(currentPlaybackOrderMode(servicePlayer)))
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null

        player?.removeListener(playerListener)
        player?.release()
        player = null

        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun updatePlaybackOrderButton() {
        mediaSession?.setMediaButtonPreferences(
            listOf(buildPlaybackOrderCommandButton(currentPlaybackOrderMode()))
        )
    }

    private fun buildOpenExpandedPlayerPendingIntent(): PendingIntent {
        val openPlayerIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_EXPANDED_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EXPAND_MINI_PLAYER, true)
        }

        return PendingIntent.getActivity(
            this,
            OPEN_EXPANDED_PLAYER_REQUEST_CODE,
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @OptIn(UnstableApi::class)
    private fun buildPlaybackOrderCommandButton(mode: PlaybackOrderMode): CommandButton {
        val (displayName, iconResId) = when (mode) {
            PlaybackOrderMode.Sequence -> "顺序播放" to R.drawable.ic_repeat_24
            PlaybackOrderMode.RepeatOne -> "单曲循环" to R.drawable.ic_repeat_one_24
            PlaybackOrderMode.Shuffle -> "随机播放" to R.drawable.ic_shuffle_24
        }

        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(displayName)
            .setCustomIconResId(iconResId)
            .setSessionCommand(togglePlaybackOrderCommand)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun currentPlaybackOrderMode(servicePlayer: Player? = player): PlaybackOrderMode {
        servicePlayer ?: return PlaybackOrderMode.Sequence
        return when {
            servicePlayer.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackOrderMode.RepeatOne
            servicePlayer.shuffleModeEnabled -> PlaybackOrderMode.Shuffle
            else -> PlaybackOrderMode.Sequence
        }
    }

    private fun nextPlaybackOrderMode(mode: PlaybackOrderMode): PlaybackOrderMode {
        return when (mode) {
            PlaybackOrderMode.Sequence -> PlaybackOrderMode.RepeatOne
            PlaybackOrderMode.RepeatOne -> PlaybackOrderMode.Shuffle
            PlaybackOrderMode.Shuffle -> PlaybackOrderMode.Sequence
        }
    }

    private fun applyPlaybackOrderMode(servicePlayer: Player, mode: PlaybackOrderMode) {
        when (mode) {
            PlaybackOrderMode.Sequence -> {
                servicePlayer.shuffleModeEnabled = false
                servicePlayer.repeatMode = Player.REPEAT_MODE_OFF
            }

            PlaybackOrderMode.RepeatOne -> {
                servicePlayer.repeatMode = Player.REPEAT_MODE_ONE
                servicePlayer.shuffleModeEnabled = false
            }

            PlaybackOrderMode.Shuffle -> {
                servicePlayer.shuffleModeEnabled = true
                servicePlayer.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun Int.isUserSkipCommand(): Boolean {
        return this == Player.COMMAND_SEEK_TO_NEXT ||
            this == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            this == Player.COMMAND_SEEK_TO_PREVIOUS ||
            this == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    }

    private companion object {
        const val OPEN_EXPANDED_PLAYER_REQUEST_CODE = 1001
    }
}
