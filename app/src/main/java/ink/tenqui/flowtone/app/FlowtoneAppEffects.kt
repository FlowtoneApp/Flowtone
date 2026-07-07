package ink.tenqui.flowtone.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.LikedSongsStore
import ink.tenqui.flowtone.permissions.hasAudioPermission
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

@Composable
internal fun FlowtoneAppEffects(
    selectedTopLevelPage: TopLevelPage,
    secondaryPage: SecondaryPage?,
    rootPage: FlowtoneRootPage,
    currentSong: Song?,
    artistRootReturnInProgress: Boolean,
    openExpandedPlayerRequest: Int,
    hasCurrentSong: Boolean,
    hasScanned: Boolean,
    songs: List<Song>,
    context: Context,
    likedSongsStore: LikedSongsStore,
    preloadSongMetadataCount: Int,
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
    LaunchedEffect(selectedTopLevelPage, secondaryPage, rootPage) {
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

    LaunchedEffect(context) {
        val granted = hasAudioPermission(context)
        musicViewModel.setPermissionStatus(granted)
        if (granted) {
            musicViewModel.scanSongs()
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

    LaunchedEffect(songRecordThresholdSeconds) {
        musicViewModel.setSongRecordThresholdSeconds(songRecordThresholdSeconds)
    }
}
