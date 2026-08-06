package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.lyrics.SongLyricsState
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import ink.tenqui.flowtone.ui.player.lyrics.LyricsContent
import ink.tenqui.flowtone.ui.player.lyrics.LyricsTrackSwitchPhase
import ink.tenqui.flowtone.ui.player.lyrics.isPureMusicNotice
import kotlinx.coroutines.flow.StateFlow

private data class LyricsPageState(
    val songId: Long,
    val state: LyricsState
)

private enum class LyricsPageContentKind {
    TimedLyrics,
    PureMusicNotice,
    NoEffectiveLyrics
}

@Composable
internal fun MiniPlayerLyricsHost(
    currentSong: Song?,
    presentedSongId: Long?,
    songLyricsState: SongLyricsState,
    confirmedPlaybackPosition: StateFlow<PlaybackPositionSnapshot>,
    activeLineTargetY: Dp,
    visibilityProgress: Float,
    switchDirection: Int,
    onLyricPress: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onChooseLyricsDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null || visibilityProgress <= 0.001f) {
        return
    }
    val playbackPosition by confirmedPlaybackPosition.collectAsState()
    val playbackPositionMs = playbackPositionForSong(
        songId = currentSong.id,
        playbackPosition = playbackPosition
    )
    val resolvedLyricsState = if (songLyricsState.songId == currentSong.id) {
        songLyricsState.state
    } else {
        LyricsState.Loading
    }
    val pageState = LyricsPageState(
        songId = currentSong.id,
        state = if (resolvedLyricsState == LyricsState.Idle) {
            LyricsState.Loading
        } else {
            resolvedLyricsState
        }
    )
    var presentedPageState by remember { mutableStateOf(pageState) }
    val targetPageState = if (pageState.songId == presentedSongId) {
        pageState
    } else {
        presentedPageState
    }
    LaunchedEffect(targetPageState, presentedSongId) {
        if (targetPageState.songId == presentedSongId) {
            presentedPageState = targetPageState
        }
    }
    val normalizedSwitchDirection = if (switchDirection < 0) -1 else 1
    var observedSongId by remember {
        mutableLongStateOf(targetPageState.songId)
    }
    var activeStaggeredEntranceSongId by remember {
        mutableStateOf<Long?>(null)
    }
    var trackEnterReadySongId by remember {
        mutableStateOf<Long?>(null)
    }
    val songChangedThisFrame = observedSongId != targetPageState.songId
    val staggeredEntranceActive =
        (songChangedThisFrame && targetPageState.state.hasLyricsLines()) ||
            activeStaggeredEntranceSongId == targetPageState.songId
    LaunchedEffect(targetPageState.songId) {
        if (observedSongId != targetPageState.songId) {
            observedSongId = targetPageState.songId
            activeStaggeredEntranceSongId = targetPageState.songId.takeIf {
                targetPageState.state.hasLyricsLines()
            }
            trackEnterReadySongId = null
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        AnimatedContent(
            targetState = targetPageState,
            transitionSpec = {
                val enteringLyricsLines = targetState.state.hasLyricsLines()
                val exitingLyricsLines = initialState.state.hasLyricsLines()
                val enter = if (enteringLyricsLines) {
                    fadeIn(tween(durationMillis = 1))
                } else {
                    fadeIn(
                        tween(
                            durationMillis = LyricsStateEnterDurationMillis,
                            easing = TrackSwitchProgressEasing
                        )
                    )
                }
                val exit = if (exitingLyricsLines) {
                    fadeOut(
                        tween(
                            durationMillis = 1,
                            delayMillis = LyricsStaggeredExitLifetimeMillis
                        )
                    )
                } else {
                    fadeOut(
                        tween(
                            durationMillis = LyricsStateExitDurationMillis,
                            easing = TrackSwitchProgressEasing
                        )
                    )
                }
                enter togetherWith exit
            },
            contentKey = { state ->
                lyricsTrackSwitchContentKey(
                    songId = state.songId,
                    state = state.state
                )
            },
            label = "LyricsTrackSwitch",
            modifier = Modifier.fillMaxSize()
        ) { displayedPage ->
            val isCurrentPage =
                displayedPage == targetPageState &&
                    displayedPage.songId == currentSong.id
            val trackSwitchPhase = when {
                !displayedPage.state.hasLyricsLines() -> LyricsTrackSwitchPhase.Static
                displayedPage.songId != targetPageState.songId ->
                    LyricsTrackSwitchPhase.Exiting
                staggeredEntranceActive &&
                    trackEnterReadySongId == displayedPage.songId ->
                    LyricsTrackSwitchPhase.Entering
                staggeredEntranceActive -> LyricsTrackSwitchPhase.WaitingToEnter
                else -> LyricsTrackSwitchPhase.Static
            }
            LyricsContent(
                state = displayedPage.state,
                confirmedPlaybackPositionMs = playbackPositionMs.takeIf {
                    displayedPage.songId == currentSong.id
                },
                activeLineTargetY = activeLineTargetY,
                visibilityProgress = visibilityProgress,
                trackSwitchPhase = trackSwitchPhase,
                trackSwitchDirection = normalizedSwitchDirection,
                onTrackEnterReady = if (isCurrentPage) {
                    { trackEnterReadySongId = displayedPage.songId }
                } else {
                    { }
                },
                onTrackEnterFinished = if (isCurrentPage) {
                    {
                        if (activeStaggeredEntranceSongId == displayedPage.songId) {
                            activeStaggeredEntranceSongId = null
                        }
                    }
                } else {
                    { }
                },
                onLyricPress = if (isCurrentPage) onLyricPress else ({ }),
                onLyricClick = if (isCurrentPage) onSeekTo else ({ _ -> }),
                onChooseLyricsDirectory = if (isCurrentPage) {
                    onChooseLyricsDirectory
                } else {
                    { }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal fun playbackPositionForSong(
    songId: Long,
    playbackPosition: PlaybackPositionSnapshot
): Long? = playbackPosition.positionMs.takeIf {
    playbackPosition.mediaId == songId.toString()
}

internal fun lyricsTrackSwitchContentKey(
    songId: Long,
    state: LyricsState
): Any = when (state.contentKind()) {
    LyricsPageContentKind.TimedLyrics -> songId to LyricsPageContentKind.TimedLyrics
    LyricsPageContentKind.PureMusicNotice -> LyricsPageContentKind.PureMusicNotice
    LyricsPageContentKind.NoEffectiveLyrics -> LyricsPageContentKind.NoEffectiveLyrics
}

private const val LyricsStateEnterDurationMillis = 220
private const val LyricsStateExitDurationMillis = 160
private const val LyricsStaggeredExitLifetimeMillis =
    PlayerSongSwitchDurationMillis

private fun LyricsState.hasLyricsLines(): Boolean =
    this is LyricsState.Available && lines.isNotEmpty() && !isPureMusicNotice(lines)

private fun LyricsState.contentKind(): LyricsPageContentKind = when {
    this is LyricsState.Available && isPureMusicNotice(lines) ->
        LyricsPageContentKind.PureMusicNotice
    hasLyricsLines() -> LyricsPageContentKind.TimedLyrics
    else -> LyricsPageContentKind.NoEffectiveLyrics
}
