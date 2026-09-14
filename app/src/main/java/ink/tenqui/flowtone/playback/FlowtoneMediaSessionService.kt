package ink.tenqui.flowtone.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.app.MainActivity
import ink.tenqui.flowtone.data.listening.ListeningStatsRepositoryProvider
import ink.tenqui.flowtone.data.local.LikedTracksRepository
import ink.tenqui.flowtone.data.online.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FlowtoneMediaSessionService : MediaSessionService() {
    private val appPreferences by lazy { AppPreferences(applicationContext) }
    private val likedTracksRepository by lazy { LikedTracksRepository.get(applicationContext) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: SessionPlaybackPlayer? = null
    private var mediaSession: MediaSession? = null
    private var listeningStatsTracker: ListeningStatsTracker? = null

    private val togglePlaybackOrderCommand = SessionCommand(
        ACTION_TOGGLE_PLAYBACK_ORDER,
        Bundle.EMPTY
    )
    private val setPlaybackOrderCommand = SessionCommand(
        ACTION_SET_PLAYBACK_ORDER,
        Bundle.EMPTY
    )
    private val toggleLikedCommand = SessionCommand(ACTION_TOGGLE_LIKED, Bundle.EMPTY)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = logPlayerState("onIsPlayingChanged")

        override fun onPlaybackStateChanged(playbackState: Int) =
            logPlayerState("onPlaybackStateChanged")

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (
                playbackSuppressionReason ==
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS &&
                player?.playWhenReady == true &&
                !appPreferences.shouldResumePlaybackAfterCall()
            ) {
                player?.pause()
            }
            logPlayerState("onPlaybackSuppressionReasonChanged")
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
            logPlayerState("onMediaMetadataChanged")

        override fun onRepeatModeChanged(repeatMode: Int) = updateMediaButtons()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateMediaButtons()
    }

    private val sessionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Log.d(MEDIA_SESSION_LOG_TAG, "onConnect: packageName=${controller.packageName}")
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands
                .buildUpon()
                .add(togglePlaybackOrderCommand)
                .add(setPlaybackOrderCommand)
                .add(toggleLikedCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(connectionResult.availablePlayerCommands)
                .setMediaButtonPreferences(buildMediaButtonPreferences())
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<androidx.media3.common.MediaItem>
        ): ListenableFuture<List<androidx.media3.common.MediaItem>> {
            if (PlaybackQueueMediaItemCodec.isLogicalQueue(mediaItems)) {
                Log.d(
                    MEDIA_SESSION_LOG_TAG,
                    "onAddMediaItems logicalCount=${mediaItems.size} " +
                        "target=${mediaItems.firstOrNull()?.mediaId} package=${controller.packageName}"
                )
                return Futures.immediateFuture(mediaItems)
            }
            return super.onAddMediaItems(mediaSession, controller, mediaItems)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_PLAYBACK_ORDER -> {
                    player?.let { sessionPlayer ->
                        sessionPlayer.setPlaybackOrderMode(
                            nextPlaybackOrderMode(sessionPlayer.playbackOrderMode)
                        )
                    }
                    updateMediaButtons()
                    return successResult()
                }

                ACTION_SET_PLAYBACK_ORDER -> {
                    player?.setPlaybackOrderMode(parsePlaybackOrderMode(args))
                    updateMediaButtons()
                    return successResult()
                }

                ACTION_TOGGLE_LIKED -> {
                    val track = likeCommandTarget(player?.currentLogicalItem)
                        ?: return Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                        )
                    likedTracksRepository.toggle(track)
                    updateMediaButtons()
                    return successResult()
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_media_notification)
        }
        setMediaNotificationProvider(notificationProvider)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val extensionManager = ExtensionManager.get(applicationContext)
        val engine = ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(extensionManager.extensionMediaSourceFactory(applicationContext))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        val sessionPlayer = SessionPlaybackPlayer(
            engine = engine,
            scope = serviceScope,
            extensionManager = extensionManager,
            appPreferences = appPreferences,
            artworkLoader = SessionArtworkLoader(applicationContext, extensionManager),
            onLogicalTargetChanged = {
                updateMediaButtons()
                logPlayerState("logicalTargetChanged")
            },
            onPlaybackOrderChanged = { updateMediaButtons() },
            onResolveError = { item, message ->
                Log.w(MEDIA_SESSION_LOG_TAG, "resolve failed target=${item.stableIdentity}: $message")
            }
        )
        player = sessionPlayer
        sessionPlayer.addListener(playerListener)
        listeningStatsTracker = ListeningStatsTracker(
            repository = ListeningStatsRepositoryProvider.get(applicationContext),
            scope = serviceScope,
            thresholdMsProvider = {
                appPreferences.getSongRecordThresholdSeconds().toLong() * 1_000L
            },
            elapsedRealtimeMs = SystemClock::elapsedRealtime,
            currentTimeMillis = System::currentTimeMillis
        ).also { it.attach(sessionPlayer) }

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId("flowtone_service_session")
            .setCallback(sessionCallback)
            .setSessionActivity(buildOpenExpandedPlayerPendingIntent())
            .setMediaButtonPreferences(buildMediaButtonPreferences())
            .build()

        serviceScope.launch {
            runCatching { extensionManager.initialize() }
                .onFailure { Log.w(MEDIA_SESSION_LOG_TAG, "extension initialization failed", it) }
        }
        serviceScope.launch {
            likedTracksRepository.tracks.collectLatest { updateMediaButtons() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(
            MEDIA_SESSION_LOG_TAG,
            "onGetSession: packageName=${controllerInfo.packageName}, hasSession=${mediaSession != null}"
        )
        return mediaSession
    }

    override fun onDestroy() {
        listeningStatsTracker?.release()
        listeningStatsTracker = null
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        serviceScope.cancel()
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun updateMediaButtons() {
        mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences())
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaButtonPreferences(): List<CommandButton> = listOf(
        buildLikedCommandButton(),
        buildPlaybackOrderCommandButton(currentPlaybackOrderMode())
    )

    @OptIn(UnstableApi::class)
    private fun buildLikedCommandButton(): CommandButton {
        val track = likeCommandTarget(player?.currentLogicalItem)
        val liked = likedTracksRepository.isLiked(track)
        return CommandButton.Builder(
            if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (liked) "取消收藏" else "收藏")
            .setSessionCommand(toggleLikedCommand)
            .setEnabled(track != null)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
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
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun buildOpenExpandedPlayerPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_EXPANDED_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EXPAND_MINI_PLAYER, true)
        }
        return PendingIntent.getActivity(
            this,
            OPEN_EXPANDED_PLAYER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun currentPlaybackOrderMode(): PlaybackOrderMode =
        player?.playbackOrderMode ?: PlaybackOrderMode.Sequence

    private fun nextPlaybackOrderMode(mode: PlaybackOrderMode): PlaybackOrderMode = when (mode) {
        PlaybackOrderMode.Sequence -> PlaybackOrderMode.RepeatOne
        PlaybackOrderMode.RepeatOne -> PlaybackOrderMode.Shuffle
        PlaybackOrderMode.Shuffle -> PlaybackOrderMode.Sequence
    }

    private fun parsePlaybackOrderMode(args: Bundle): PlaybackOrderMode =
        args.getString(EXTRA_PLAYBACK_ORDER_MODE)
            ?.let { runCatching { PlaybackOrderMode.valueOf(it) }.getOrNull() }
            ?: currentPlaybackOrderMode()

    private fun logPlayerState(event: String) {
        val sessionPlayer = player
        val metadata = sessionPlayer?.mediaMetadata
        Log.d(
            MEDIA_SESSION_LOG_TAG,
            "$event: target=${sessionPlayer?.currentLogicalItem?.stableIdentity}, " +
                "isPlaying=${sessionPlayer?.isPlaying}, " +
                "playWhenReady=${sessionPlayer?.playWhenReady}, " +
                "playbackState=${sessionPlayer?.playbackState}, " +
                "title=${metadata?.title}, artist=${metadata?.artist}"
        )
    }

    private fun successResult(): ListenableFuture<SessionResult> =
        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))

    private companion object {
        const val MEDIA_SESSION_LOG_TAG = "FlowtoneMediaSession"
        const val OPEN_EXPANDED_PLAYER_REQUEST_CODE = 1001
    }
}
