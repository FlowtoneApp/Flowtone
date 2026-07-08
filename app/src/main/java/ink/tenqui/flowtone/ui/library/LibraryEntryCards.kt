package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.ui.components.LibraryCollectionCard

@Composable
internal fun LibraryHomeEntryCards(
    songCount: Int,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibraryCollectionCard(
        title = "\u672c\u5730\u66f2\u5e93",
        subtitle = "$songCount \u9996\u6b4c\u66f2",
        onClick = onOpenLocalLibrary,
        modifier = modifier
            .fillMaxWidth()
            .height(LibraryInfoCardHeight)
    )
}
