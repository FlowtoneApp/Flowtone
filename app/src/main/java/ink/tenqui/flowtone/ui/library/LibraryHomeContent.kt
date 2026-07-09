package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.ui.components.FlowtonePageHeader
import ink.tenqui.flowtone.ui.components.StaggeredPageElement

@Composable
internal fun LibraryHomeContent(
    songCount: Int,
    visible: Boolean,
    likedPlaylist: LibraryPlaylistCard,
    playlistRows: List<List<LibraryPlaylistCard>>,
    playlistCardHeight: Dp,
    libraryCardsProgress: Float,
    playlistRowItemOffsetYPx: Float,
    listState: LazyListState,
    activePlaylistActionId: String?,
    newlyCreatedPlaylistId: String?,
    onClearPlaylistActions: () -> Unit,
    onOpenLocalLibrary: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onShowPlaylistActions: (String) -> Unit,
    onRenamePlaylist: (LibraryPlaylistCard) -> Unit,
    onDeletePlaylist: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = onClearPlaylistActions
            ),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 48.dp,
            end = 20.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "library-header") {
            StaggeredPageElement(
                visible = visible,
                animationIndex = 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    FlowtonePageHeader(
                        title = "曲库",
                        subtitle = "收集自己喜欢的声音",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
        item(key = "library-actions") {
            StaggeredPageElement(
                visible = visible,
                animationIndex = 4,
                modifier = Modifier.fillMaxWidth()
            ) {
                LibraryHomeEntryCards(
                    songCount = songCount,
                    onOpenLocalLibrary = onOpenLocalLibrary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        libraryPlaylistRows(
            likedPlaylist = likedPlaylist,
            playlistRows = playlistRows,
            playlistCardHeight = playlistCardHeight,
            libraryCardsProgress = libraryCardsProgress,
            playlistRowItemOffsetYPx = playlistRowItemOffsetYPx,
            activePlaylistActionId = activePlaylistActionId,
            newlyCreatedPlaylistId = newlyCreatedPlaylistId,
            onCreateAnimationFinished = onCreateAnimationFinished,
            onCreatePlaylist = onCreatePlaylist,
            onOpenPlaylist = onOpenPlaylist,
            onShowPlaylistActions = onShowPlaylistActions,
            onRenamePlaylist = onRenamePlaylist,
            onDeletePlaylist = onDeletePlaylist
        )
    }
}
