package ink.tenqui.flowtone.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LocalPlaylistCreatorName
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork

private val PlaylistMetadataArtworkSize = 140.dp

internal data class PlaylistDetailMetadata(
    val title: String,
    val creatorName: String? = null,
    val description: String? = null,
    val customArtworkUri: Uri? = null
)

@Composable
internal fun PlaylistMetadataHeader(
    metadata: PlaylistDetailMetadata,
    artworkUri: Uri?,
    modifier: Modifier = Modifier
) {
    val creator = metadata.creatorName?.trim()?.ifBlank { null } ?: LocalPlaylistCreatorName
    val summary = metadata.description?.trim()?.ifBlank { null }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = creator,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            summary?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        FlowtoneArtwork(
            artworkUri = artworkUri,
            modifier = Modifier.size(PlaylistMetadataArtworkSize)
        )
    }
}
