package ink.tenqui.flowtone.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.FlowtoneContentSectionTitle
import ink.tenqui.flowtone.ui.components.FlowtonePageHeader
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderBodyGap
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.library.FastInteractionTrace
import ink.tenqui.flowtone.ui.library.LibraryTemporaryPlaylistListItem
import ink.tenqui.flowtone.ui.library.observeFastInteractionPointerEvents
import ink.tenqui.flowtone.ui.player.DefaultFlowCloudSpeed

@Composable
internal fun FastLibraryInteractionContent(
    selection: FastInteractionTestSelection,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onOpenTestPlaylist: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .observeFastInteractionPointerEvents("library-home")
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.Top
    ) {
        FlowtonePageHeader(
            title = stringResource(R.string.top_level_library_title),
            subtitle = stringResource(R.string.top_level_library_subtitle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = FlowtonePageHeaderExpandedStartPadding,
                    top = FlowtonePageHeaderExpandedTopPadding,
                    end = FlowtonePageHeaderExpandedEndPadding
                )
                .alpha(0f)
                .clearAndSetSemantics { }
        )
        Spacer(Modifier.height(FlowtonePageHeaderBodyGap))
        FlowtoneContentSectionTitle(
            title = "交互对照实验",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        val testSong = selection.comparisonSong
        if (testSong == null) {
            Text(
                text = "当前本地曲库没有可用于测试的歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            Text(
                text = "测试用随机歌曲",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            SongListItem(
                song = testSong,
                isCurrentSong = currentSong?.let { it.id == testSong.id && it.uri == testSong.uri }
                    ?: false,
                onClick = { clickedSong ->
                    FastInteractionTrace.record("ON_CLICK_BEGIN", "library-single", clickedSong)
                    onSongClick(clickedSong)
                },
                onPressInteraction = { pressedSong ->
                    FastInteractionTrace.record(
                        stage = "PRESS_INTERACTION_COLLECTED",
                        page = "library-single",
                        song = pressedSong,
                        details = "source=MutableInteractionSource collector; visual frame not measured"
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
            LibraryTemporaryPlaylistListItem(
                title = "测试歌单",
                subtitle = "${selection.playlistSongs.size} 首本地歌曲 · 临时对照",
                itemHeight = 72.dp,
                flowCloudSpeed = DefaultFlowCloudSpeed,
                isFlowCloudPlaying = false,
                onClick = {
                    FastInteractionTrace.record(
                        stage = "PLAYLIST_OPEN",
                        page = "library-home",
                        details = "songs=${selection.playlistSongs.size}"
                    )
                    onOpenTestPlaylist(selection.playlistSongs)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
