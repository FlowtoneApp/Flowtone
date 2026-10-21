package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderPlaceholder
import ink.tenqui.flowtone.ui.components.PageTransitionElement
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SettingsCategoryCard
import ink.tenqui.flowtone.ui.components.SettingsOptionSurface
import ink.tenqui.flowtone.ui.components.settingsOptionPalette

private const val MinePageTransitionElementCount = 4
private const val MineCardCloudTintBlend = 0.2f
private const val MineCardIconCloudTintBlend = 0.28f

@Composable
internal fun MineScreen(
    cloudAccent: Color,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    pageScope: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val optionPalette = settingsOptionPalette()
    val settingsCardSurfaceColor = optionPalette.surfaceColorWithAccent(
        cloudAccent,
        MineCardCloudTintBlend
    )
    val accountCardSurfaceColor = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        cloudAccent,
        MineCardCloudTintBlend
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 16.dp)
    ) {
        PageTransitionElement(
            scope = pageScope,
            order = 0,
            orderCount = MinePageTransitionElementCount
        ) {
            FlowtonePageHeaderPlaceholder()
        }
        Spacer(modifier = Modifier.height(30.dp))
        MineAccountCard(
            pageScope = pageScope,
            surfaceColor = accountCardSurfaceColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        MineMenuItem(
            title = "设置",
            subtitle = "应用偏好与管理",
            icon = Icons.Rounded.Settings,
            scope = pageScope,
            order = 2,
            surfaceColor = settingsCardSurfaceColor,
            cloudAccent = cloudAccent,
            onClick = onOpenSettings,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        MineMenuItem(
            title = "关于",
            subtitle = "版本信息与开源许可",
            icon = Icons.Rounded.Info,
            scope = pageScope,
            order = 3,
            surfaceColor = settingsCardSurfaceColor,
            cloudAccent = cloudAccent,
            onClick = onOpenAbout
        )
    }
}

@Composable
private fun MineAccountCard(
    pageScope: PageTransitionScope,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    PageTransitionElement(
        scope = pageScope,
        order = 1,
        orderCount = MinePageTransitionElementCount,
        modifier = modifier
    ) {
        val palette = settingsOptionPalette()
        val cornerRadius = 24.dp
        val outlineWidth = 1.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val strokeWidth = outlineWidth.toPx()
                    drawRoundRect(
                        color = palette.outlineColor,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = CornerRadius(cornerRadius.toPx()),
                        style = Stroke(width = strokeWidth)
                    )
                }
        ) {
            SettingsOptionSurface(
                modifier = Modifier.padding(outlineWidth),
                shape = RoundedCornerShape(cornerRadius - outlineWidth),
                surfaceColor = surfaceColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "账号头像占位",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "账号",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "账号功能暂未开放",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MineMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    scope: PageTransitionScope,
    order: Int,
    surfaceColor: Color,
    cloudAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PageTransitionElement(
        scope = scope,
        order = order,
        orderCount = MinePageTransitionElementCount,
        modifier = modifier
    ) {
        val colorScheme = MaterialTheme.colorScheme
        SettingsCategoryCard(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconContainerColor = lerp(
                colorScheme.primaryContainer,
                cloudAccent,
                MineCardIconCloudTintBlend
            ),
            iconContentColor = colorScheme.onPrimaryContainer,
            surfaceColor = surfaceColor,
            onClick = onClick
        )
    }
}
