package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.core.model.Song

@Composable
internal fun BoxScope.MiniPlayerArtistHost(
    fullscreenContentMode: FullscreenContentMode,
    artistPlaceholderArtists: List<String>,
    artistPlaceholderLocalSongs: List<Song>,
    artistPlaceholderActive: Boolean,
    artistPlaceholderProgress: Float,
    currentSong: Song?,
    fullscreenSwipeThresholdPx: Float,
    visualPanelHeight: Dp,
    songInfoProgress: Float,
    songInfoTopPadding: Dp,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit
) {
    if (
        shouldShowArtistPlaceholderOverlay(
            artistPlaceholderArtists = artistPlaceholderArtists,
            artistPlaceholderActive = artistPlaceholderActive,
            artistPlaceholderProgress = artistPlaceholderProgress
        )
    ) {
        ArtistPlaceholderOverlay(
            artists = artistPlaceholderArtists,
            artistSongs = artistPlaceholderLocalSongs,
            currentSong = currentSong,
            progress = artistPlaceholderProgress,
            backGestureThresholdPx = fullscreenSwipeThresholdPx,
            backGestureEnabled = isArtistPlaceholderBackGestureEnabled(
                artistPlaceholderActive = artistPlaceholderActive,
                artistPlaceholderProgress = artistPlaceholderProgress
            ),
            onBack = onBack,
            onArtistClick = onArtistClick,
            onSongClick = onSongClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(visualPanelHeight)
                .zIndex(6f)
        )
    }
    if (
        shouldShowSongInfoOverlay(
            fullscreenContentMode = fullscreenContentMode,
            songInfoProgress = songInfoProgress
        )
    ) {
        FullscreenSongInfoOverlay(
            song = currentSong,
            progress = songInfoProgress,
            topPadding = songInfoTopPadding,
            backGestureThresholdPx = fullscreenSwipeThresholdPx,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(visualPanelHeight)
                .zIndex(6f)
        )
    }
}
