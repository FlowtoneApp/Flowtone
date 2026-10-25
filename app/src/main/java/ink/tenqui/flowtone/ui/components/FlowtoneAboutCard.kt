package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val FlowtoneAboutCardShape = RoundedCornerShape(24.dp)

@Composable
internal fun rememberFlowtoneVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
            .ifBlank { "x.x.x" }
    }
}

@Composable
internal fun FlowtoneAboutCard(
    versionName: String,
    description: String,
    modifier: Modifier = Modifier,
    iconBackgroundColor: Color? = null,
    iconContent: (@Composable BoxScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(FlowtoneAboutCardShape)
            .background(Color(0xFF2E344A))
            .then(clickableModifier)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(iconBackgroundColor ?: MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
                content = iconContent ?: {}
            )
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "声流 / Flowtone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0D7F4)
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE0D7F4)
                    )
                }
            }
        }

        if (bottomContent != null) {
            Spacer(modifier = Modifier.weight(1f))
            Box {
                bottomContent()
            }
        }
    }
}
