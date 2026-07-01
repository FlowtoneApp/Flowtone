package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneAboutCard
import ink.tenqui.flowtone.ui.components.StaggeredPageElement
import ink.tenqui.flowtone.ui.components.flowtoneAboutCardSharedBounds
import ink.tenqui.flowtone.ui.components.rememberFlowtoneVersionName

private val MineAboutPreviewCardHeight = 144.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MineScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    secondaryOpen: Boolean,
    sharedTransitionScope: SharedTransitionScope? = null,
    aboutSharedTransitionActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val versionName = rememberFlowtoneVersionName()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        StaggeredPageElement(
            visible = !secondaryOpen,
            animationIndex = 0,
            applyElementMotion = !aboutSharedTransitionActive
        ) {
            FlowtoneAboutCard(
                versionName = versionName,
                description = "声流已为您播放 xx 首歌",
                onClick = onOpenAbout,
                animatedVisibilityScope = this,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MineAboutPreviewCardHeight)
                    .flowtoneAboutCardSharedBounds(
                        sharedTransitionScope = if (aboutSharedTransitionActive) {
                            sharedTransitionScope
                        } else {
                            null
                        },
                        animatedVisibilityScope = this
                    )
            )
        }
        MineMenuItem(
            title = "\u8bbe\u7f6e",
            icon = Icons.Rounded.Settings,
            visible = !secondaryOpen,
            animationIndex = 1,
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 16.dp)
        )
        MineMenuItem(
            title = "\u5173\u4e8e",
            icon = Icons.Rounded.Info,
            visible = !secondaryOpen,
            animationIndex = 2,
            onClick = onOpenAbout,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun MineMenuItem(
    title: String,
    icon: ImageVector,
    visible: Boolean,
    animationIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StaggeredPageElement(
        visible = visible,
        animationIndex = animationIndex,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
