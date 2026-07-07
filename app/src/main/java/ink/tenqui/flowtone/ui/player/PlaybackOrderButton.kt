package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import ink.tenqui.flowtone.playback.PlaybackOrderMode

@Composable
internal fun PlaybackOrderButton(
    mode: PlaybackOrderMode,
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    val icon = when (mode) {
        PlaybackOrderMode.Sequence -> Icons.Rounded.Repeat
        PlaybackOrderMode.RepeatOne -> Icons.Rounded.RepeatOne
        PlaybackOrderMode.Shuffle -> Icons.Rounded.Shuffle
    }
    val description = when (mode) {
        PlaybackOrderMode.Sequence -> "\u987a\u5e8f\u64ad\u653e"
        PlaybackOrderMode.RepeatOne -> "\u5355\u66f2\u5faa\u73af"
        PlaybackOrderMode.Shuffle -> "\u968f\u673a\u64ad\u653e"
    }
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = iconColor,
            modifier = Modifier.size(PlayerSideButtonIconSize)
        )
    }
}
