package ink.tenqui.flowtone.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun DeletePlaylistDialogContent(
    secondaryContentColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = "确定要删除这个歌单吗？",
        style = MaterialTheme.typography.bodyMedium,
        color = secondaryContentColor,
        modifier = modifier
    )
}
