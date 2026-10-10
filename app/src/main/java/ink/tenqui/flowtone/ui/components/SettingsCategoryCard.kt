package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.025f),
                spotColor = Color.Black.copy(alpha = 0.025f)
            ),
        shape = cardShape,
        color = SettingsCategoryCardSurfaceColor,
        contentColor = SettingsCategoryCardTitleColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SettingsCategoryCardTitleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsCategoryCardSubtitleColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = SettingsCategoryCardSubtitleColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private val SettingsCategoryCardTitleColor = Color(0xFF1C1B1F)
private val SettingsCategoryCardSubtitleColor = Color(0xFF49454F)
private val SettingsCategoryCardSurfaceColor = Color(0xFFF5F8FF)
