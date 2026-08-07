package ink.tenqui.flowtone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.LikedSongsStore
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

@Composable
internal fun FlowtoneAppEffects(
    selectedTopLevelPage: TopLevelPage,
    secondaryPage: SecondaryPage?,
    currentSong: Song?,
    artistRootReturnInProgress: Boolean,
    openExpandedPlayerRequest: Int,
    hasCurrentSong: Boolean,
    hasScanned: Boolean,
    songs: List<Song>,
    likedSongsStore: LikedSongsStore,
    preloadSongMetadataCount: Int,
    preloadLyricsCount: Int,
    songRecordThresholdSeconds: Int,
    musicViewModel: MusicViewModel,
    onContentScrollOffsetChange: (Float) -> Unit,
    onClearMiniPlayerState: () -> Unit,
    onArtistRootReturnCompleted: () -> Unit,
    onOpenExpandedMiniPlayer: () -> Unit,
    onOpenExpandedPlayerRequestConsumed: () -> Unit,
    onLikedSongKeysLoaded: (List<String>) -> Unit,
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

    LaunchedEffect(artistRootReturnInProgress) {
        if (artistRootReturnInProgress) {
            delay(MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS.toLong())
            onArtistRootReturnCompleted()
        }
    }

    LaunchedEffect(openExpandedPlayerRequest, hasCurrentSong, hasScanned, songs) {
        if (openExpandedPlayerRequest == 0) {
            return@LaunchedEffect
        }

        if (hasCurrentSong) {
            onOpenExpandedMiniPlayer()
            onOpenExpandedPlayerRequestConsumed()
        } else if (hasScanned && songs.isEmpty()) {
            onOpenExpandedPlayerRequestConsumed()
        }
    }

    LaunchedEffect(likedSongsStore) {
        onLikedSongKeysLoaded(likedSongsStore.loadLikedSongKeys())
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
