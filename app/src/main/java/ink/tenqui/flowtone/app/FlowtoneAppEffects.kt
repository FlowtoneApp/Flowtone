package ink.tenqui.flowtone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

@Composable
internal fun FlowtoneAppEffects(
    selectedTopLevelPage: TopLevelPage,
    secondaryPage: SecondaryPage?,
    currentSong: Song?,
    openExpandedPlayerRequest: Int,
    hasCurrentSong: Boolean,
    hasScanned: Boolean,
    songs: List<Song>,
    preloadSongMetadataCount: Int,
    preloadLyricsCount: Int,
    songRecordThresholdSeconds: Int,
    musicViewModel: MusicViewModel,
    onContentScrollOffsetChange: (Float) -> Unit,
    onClearMiniPlayerState: () -> Unit,
    onDismissSearchInputForSystemPlayerOpen: () -> Unit,
    onOpenExpandedMiniPlayer: () -> Unit,
    onOpenExpandedPlayerRequestConsumed: () -> Unit,
    onHideSwipeHint: () -> Unit
) {
    LaunchedEffect(selectedTopLevelPage, secondaryPage) {
        onContentScrollOffsetChange(0f)
    }

    LaunchedEffect(currentSong) {
        if (currentSong == null) {
            onClearMiniPlayerState()
        }
    }

    LaunchedEffect(openExpandedPlayerRequest, hasCurrentSong, hasScanned, songs) {
        if (openExpandedPlayerRequest == 0) {
            return@LaunchedEffect
        }

        if (hasCurrentSong) {
            onDismissSearchInputForSystemPlayerOpen()
            onOpenExpandedMiniPlayer()
            onOpenExpandedPlayerRequestConsumed()
        } else if (hasScanned && songs.isEmpty()) {
            onOpenExpandedPlayerRequestConsumed()
        }
    }

    LaunchedEffect(Unit) {
        delay(2_000)
        onHideSwipeHint()
    }

    LaunchedEffect(preloadSongMetadataCount) {
        musicViewModel.setPreloadSongMetadataCount(preloadSongMetadataCount)
    }

    LaunchedEffect(preloadLyricsCount) {
        musicViewModel.setPreloadLyricsCount(preloadLyricsCount)
    }

    LaunchedEffect(songRecordThresholdSeconds) {
        musicViewModel.setSongRecordThresholdSeconds(songRecordThresholdSeconds)
    }
}

internal data class SearchInputContext(
    val inputFocused: Boolean,
    val keyboardVisible: Boolean,
    val focusRequest: Int,
    val keyboardDismissRequest: Int
)

/** Keeps the search result context while ending only the transient text-input context. */
internal fun dismissSearchInputSession(
    context: SearchInputContext
): SearchInputContext {
    if (!context.inputFocused && !context.keyboardVisible && context.focusRequest == 0) {
        return context
    }
    return context.copy(
        inputFocused = false,
        focusRequest = 0,
        keyboardDismissRequest = context.keyboardDismissRequest + 1
    )
}
