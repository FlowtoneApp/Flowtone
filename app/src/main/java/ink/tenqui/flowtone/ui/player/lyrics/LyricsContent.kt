package ink.tenqui.flowtone.ui.player.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LyricsPlaceholderContent(
    visibilityProgress: Float,
    clickEnabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = visibilityProgress.coerceIn(0f, 1f)
                translationY = 10.dp.toPx() * (1f - visibilityProgress)
            }
            .clickable(
                enabled = clickEnabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = Alignment.CenterVertically
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.Lyrics,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.88f)
        )
        Text(
            text = "\u6682\u65e0\u6b4c\u8bcd",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = "\u5355\u51fb\u8fd4\u56de\u5c01\u9762",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.70f)
        )
    }
}
