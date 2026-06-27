package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.LibraryCollectionCard
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.viewmodel.MusicUiState

private val LibraryInfoCardHeight = 112.dp

@Composable
fun LibraryScreen(
    songCount: Int,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        LibraryCollectionCard(
            title = "\u672c\u5730\u66f2\u5e93",
            subtitle = "$songCount \u9996\u6b4c\u66f2",
            onClick = onOpenLocalLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .height(LibraryInfoCardHeight)
        )
    }
}

@Composable
fun LocalLibraryScreen(
    uiState: MusicUiState,
    currentSong: Song?,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        !uiState.hasPermission -> PermissionContent(
            permissionDenied = permissionDenied,
            onRequestPermission = onRequestPermission,
            modifier = modifier
        )

        uiState.isLoading -> CenterMessage(
            title = "\u6b63\u5728\u626b\u63cf\u672c\u5730\u97f3\u4e50",
            subtitle = "\u6211\u4eec\u6b63\u5728\u67e5\u627e\u8bbe\u5907\u4e2d\u7684\u97f3\u4e50\u6587\u4ef6",
            modifier = modifier,
            showProgress = true
        )

        uiState.errorMessage != null -> CenterMessage(
            title = uiState.errorMessage,
            modifier = modifier
        )

        !uiState.hasScanned -> CenterMessage(
            title = "\u51c6\u5907\u626b\u63cf\u672c\u5730\u97f3\u4e50",
            subtitle = "\u6388\u6743\u540e\u5c06\u81ea\u52a8\u663e\u793a\u53ef\u64ad\u653e\u7684\u6b4c\u66f2",
            modifier = modifier
        )

        uiState.songs.isEmpty() -> CenterMessage(
            title = "\u6ca1\u6709\u627e\u5230\u672c\u5730\u97f3\u4e50",
            subtitle = "\u8bf7\u786e\u8ba4\u8bbe\u5907\u4e2d\u5df2\u4fdd\u5b58\u97f3\u4e50\u6587\u4ef6",
            modifier = modifier
        )

        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = uiState.songs,
                key = { it.id }
            ) { song ->
                SongListItem(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id,
                    onClick = onSongClick
                )
            }
        }
    }
}

@Composable
private fun PermissionContent(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (permissionDenied) {
                "\u65e0\u6cd5\u8bbf\u95ee\u672c\u5730\u97f3\u4e50"
            } else {
                "\u9700\u8981\u97f3\u9891\u6743\u9650"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (permissionDenied) {
                "\u6743\u9650\u88ab\u62d2\u7edd\uff0c\u53ef\u4ee5\u518d\u6b21\u6388\u6743\u540e\u7ee7\u7eed\u626b\u63cf"
            } else {
                "\u6388\u6743\u540e\uff0cFlowtone \u624d\u80fd\u626b\u63cf\u5e76\u64ad\u653e\u672c\u5730\u97f3\u4e50"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = onRequestPermission
        ) {
            Text(text = "\u6388\u4e88\u6743\u9650")
        }
    }
}

@Composable
private fun CenterMessage(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
