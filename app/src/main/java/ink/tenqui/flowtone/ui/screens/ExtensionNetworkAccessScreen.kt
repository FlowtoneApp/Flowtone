package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.ui.components.PageTransitionScope

@Composable
internal fun ExtensionNetworkAccessScreen(
    permissions: Set<NetworkOriginPermission>,
    pageScope: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val presentation = remember(permissions) {
        extensionNetworkAccessPresentation(permissions)
    }

    val sectionCount = listOf(
        presentation.httpsOrigins,
        presentation.httpOrigins
    ).count { it.isNotEmpty() }
    var sectionIndex = 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        presentation.httpsOrigins.takeIf { it.isNotEmpty() }?.let { origins ->
            NetworkOriginSection(
                title = "HTTPS 请求",
                description = "这些地址使用加密的 HTTPS 连接。",
                origins = origins,
                insecure = false,
                modifier = pageScope.elementModifier(sectionIndex++, sectionCount)
            )
        }
        presentation.httpOrigins.takeIf { it.isNotEmpty() }?.let { origins ->
            NetworkOriginSection(
                title = "HTTP 请求（不安全）",
                description = "通过 HTTP 传输的数据可能不会被加密。\n当前 Flowtone 版本暂不支持扩展通过 HTTP 进行网络访问。",
                origins = origins,
                insecure = true,
                modifier = pageScope.elementModifier(sectionIndex, sectionCount)
            )
        }
    }
}

internal data class ExtensionNetworkAccessPresentation(
    val httpsOrigins: List<NetworkOriginPermission>,
    val httpOrigins: List<NetworkOriginPermission>
)

internal fun extensionNetworkAccessPresentation(
    permissions: Set<NetworkOriginPermission>
): ExtensionNetworkAccessPresentation = ExtensionNetworkAccessPresentation(
    httpsOrigins = permissions
        .filter { it.security == NetworkSecurity.Secure }
        .sortedBy(Any::toString),
    httpOrigins = permissions
        .filter { it.security == NetworkSecurity.Insecure }
        .sortedBy(Any::toString)
)

@Composable
private fun NetworkOriginSection(
    title: String,
    description: String,
    origins: List<NetworkOriginPermission>,
    insecure: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (insecure) colors.error else colors.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = if (insecure) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            origins.forEach { origin ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (insecure) {
                        colors.errorContainer.copy(alpha = 0.42f)
                    } else {
                        colors.surfaceContainer
                    }
                ) {
                    NetworkOriginRow(origin = origin, insecure = insecure)
                }
            }
        }
    }
}

@Composable
private fun NetworkOriginRow(origin: NetworkOriginPermission, insecure: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = if (insecure) {
                colors.error.copy(alpha = 0.12f)
            } else {
                colors.surfaceContainerHighest
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (insecure) Icons.Rounded.Language else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (insecure) colors.error else colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = origin.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        if (insecure) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = colors.error.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "不安全",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
