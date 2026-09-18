package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import ink.tenqui.flowtone.data.online.capability.SummaryCapability
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityId
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityStatus
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.ui.components.rememberArtworkBackgroundColor
import coil3.compose.AsyncImage
import coil3.imageLoader

@Composable
internal fun ExtensionInstallScreen(
    preview: ExtensionInstallPreview,
    installing: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val extractedIconColor = rememberArtworkBackgroundColor(
        artworkData = preview.icon?.bytes,
        imageLoader = context.imageLoader,
        fallbackColor = colorScheme.primaryContainer,
        isDarkTheme = colorScheme.background.luminance() <= 0.5f
    ) ?: colorScheme.primaryContainer
    val iconColor = preview.incomingManifest.color
        ?.let(::extensionThemeColor)
        ?: extractedIconColor
    val providerType = preview.incomingManifest.musicSources
        .firstOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "未指定类型"
    val cardShape = RoundedCornerShape(28.dp)

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(180.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            ) {
                ExtensionInstallHeaderCloud(
                    color = iconColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(660.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = iconColor.copy(alpha = 0.82f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            preview.icon?.let { icon ->
                                AsyncImage(
                                    model = icon.bytes,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            } ?: Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = preview.incomingManifest.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = providerType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = preview.incomingManifest.version +
                                " · " + preview.incomingManifest.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                preview.incomingManifest.description
                    .takeIf(String::isNotBlank)
                    ?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 24.dp, top = 122.dp, end = 24.dp)
                        )
                    }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            ExtensionCapabilitiesCard(preview.summaryCapabilities)
            if (preview.networkPermissions.isNotEmpty()) {
                ExtensionNetworkSummaryCard(
                    preview = preview,
                    modifier = Modifier.padding(top = 28.dp)
                )
            }
        }

        Column(modifier = Modifier.navigationBarsPadding()) {
            ExtensionInstallEnvironmentNotice(
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 4.dp)
            )
            val actionLabel = if (preview.isUpdate) "更新 " else "安装 "
            Button(
                onClick = onConfirm,
                enabled = !installing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = iconColor,
                    contentColor = if (iconColor.luminance() > 0.5f) Color.Black else Color.White
                )
            ) {
                if (installing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = if (iconColor.luminance() > 0.5f) Color.Black else Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = if (installing) "处理中" else actionLabel + preview.incomingManifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExtensionCapabilitiesCard(capabilities: List<SummaryCapability>) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = "功能支持",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colorScheme.onSurface
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) {
            capabilities.forEachIndexed { index, capability ->
                ExtensionCapabilityRow(capability)
                if (index != capabilities.lastIndex) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.48f))
                }
            }
        }
    }
}

@Composable
private fun ExtensionCapabilityRow(capability: SummaryCapability) {
    val colorScheme = MaterialTheme.colorScheme
    val (statusText, statusColor, statusBackground) = when (capability.status) {
        SummaryCapabilityStatus.Supported -> Triple(
            "支持",
            Color(0xFF237A3B),
            Color(0xFF237A3B).copy(alpha = 0.14f)
        )
        SummaryCapabilityStatus.Partial -> Triple(
            "部分支持",
            Color(0xFF9A6700),
            Color(0xFFB77900).copy(alpha = 0.16f)
        )
        SummaryCapabilityStatus.Unsupported -> Triple(
            "不支持",
            colorScheme.error,
            colorScheme.error.copy(alpha = 0.12f)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.68f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (capability.id) {
                        SummaryCapabilityId.SearchAndDiscovery -> Icons.Rounded.Search
                        SummaryCapabilityId.Playback -> Icons.Rounded.PlayCircle
                        SummaryCapabilityId.Artist -> Icons.Rounded.Person
                        SummaryCapabilityId.Album -> Icons.Rounded.Album
                        SummaryCapabilityId.Playlist -> Icons.Rounded.QueueMusic
                    },
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = capability.label,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = statusBackground
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ExtensionNetworkSummaryCard(
    preview: ExtensionInstallPreview,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = "网络访问",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colorScheme.onSurface,
        modifier = modifier
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.68f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = "可访问 ${preview.incomingManifest.name} 网络服务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${preview.networkPermissions.size} 条网络访问规则",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "查看详情",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    if (preview.networkPermissions.any { it.security == NetworkSecurity.Insecure }) {
        Text(
            text = "包含不安全 HTTP 访问；当前 Flowtone 不支持扩展通过 HTTP 访问网络。",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.error,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun ExtensionInstallEnvironmentNotice(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "扩展将在 Flowtone 扩展环境中运行，并只能访问声明的网络地址。",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun ExtensionInstallHeaderCloud(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = 260.dp, y = 240.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.26f),
                        color.copy(alpha = 0.12f),
                        color.copy(alpha = 0.04f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}

private fun extensionThemeColor(value: String): Color? {
    val rgb = value.removePrefix("#").toLongOrNull(16) ?: return null
    if (rgb !in 0x000000L..0xFFFFFFL) return null
    return Color((0xFF000000L or rgb).toInt())
}
