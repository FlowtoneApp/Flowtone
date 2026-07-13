package ink.tenqui.flowtone.ui.library

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderPlaceholder
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
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    listState: LazyListState,
    editingPlaylistId: String?,
    newlyCreatedPlaylistId: String?,
    onOpenLocalLibrary: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onStartPlaylistEditing: (LibraryPlaylistCard) -> Unit,
    onEditingPlaylistBoundsChanged: (String, Rect) -> Unit,
    onEditingPlaylistBoundsRemoved: (String) -> Unit,
    onLibraryViewportBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        userScrollEnabled = editingPlaylistId == null,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionInRoot()
                onLibraryViewportBoundsChanged(
                    Rect(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = topLeft.x + coordinates.size.width,
                        bottom = topLeft.y + coordinates.size.height
                    )
                )
            },
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
                    FlowtonePageHeaderPlaceholder(
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
            flowCloudSpeed = flowCloudSpeed,
            isFlowCloudPlaying = isFlowCloudPlaying,
            editingPlaylistId = editingPlaylistId,
            newlyCreatedPlaylistId = newlyCreatedPlaylistId,
            onCreateAnimationFinished = onCreateAnimationFinished,
            onCreatePlaylist = onCreatePlaylist,
            onOpenPlaylist = onOpenPlaylist,
            onStartPlaylistEditing = onStartPlaylistEditing,
            onEditingPlaylistBoundsChanged = onEditingPlaylistBoundsChanged,
            onEditingPlaylistBoundsRemoved = onEditingPlaylistBoundsRemoved
        )
    }
}
