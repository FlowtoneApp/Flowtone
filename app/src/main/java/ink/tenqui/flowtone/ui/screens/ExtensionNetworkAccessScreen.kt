package ink.tenqui.flowtone.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.permission.NetworkHostScope
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.ui.components.PageTransitionScope

@Composable
internal fun ExtensionNetworkAccessScreen(
    permissions: Set<NetworkOriginPermission>,
    pageScope: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val presentation = remember(permissions) { extensionNetworkAccessPresentation(permissions) }
    val httpsRules = remember(presentation.httpsOrigins) {
        presentation.httpsOrigins.map(::extensionNetworkRulePresentation)
    }
    val httpRules = remember(presentation.httpOrigins) {
        presentation.httpOrigins.map(::extensionNetworkRulePresentation)
    }
    val orderCount = 1 + httpsRules.size + httpRules.size +
        (if (httpsRules.isNotEmpty()) 1 else 0) + (if (httpRules.isNotEmpty()) 1 else 0)
    val httpHeaderOrder = 1 + (if (httpsRules.isNotEmpty()) 1 + httpsRules.size else 0)
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp, vertical = 16.dp
        )
    ) {
        item(key = "network-summary") {
            OnlineSectionHeading(
                title = "网络访问范围",
                subtitle = "${httpsRules.size} 条 HTTPS · ${httpRules.size} 条 HTTP。以下均为扩展声明的完整目标。",
                modifier = pageScope.elementModifier(0, orderCount).padding(bottom = 24.dp)
            )
        }
        if (httpsRules.isNotEmpty()) {
            item(key = "https-heading") {
                NetworkRuleHeading(
                    title = "HTTPS · 可按规则请求",
                    description = "请求仍须通过运行时权限校验。",
                    modifier = pageScope.elementModifier(1, orderCount)
                )
            }
            itemsIndexed(httpsRules, key = { _, rule -> "https:${rule.origin}" }) { index, rule ->
                NetworkRuleRow(
                    rule = rule,
                    onCopy = { copyNetworkRule(context, rule.origin) },
                    modifier = pageScope.elementModifier(index + 2, orderCount)
                )
            }
        }
        if (httpRules.isNotEmpty()) {
            item(key = "http-heading") {
                NetworkRuleHeading(
                    title = "HTTP · 当前不可用",
                    description = "这些地址虽被声明，Flowtone 当前运行时不允许扩展通过 HTTP 访问。",
                    modifier = pageScope.elementModifier(httpHeaderOrder, orderCount)
                        .padding(top = if (httpsRules.isEmpty()) 0.dp else 28.dp)
                )
            }
            itemsIndexed(httpRules, key = { _, rule -> "http:${rule.origin}" }) { index, rule ->
                NetworkRuleRow(
                    rule = rule,
                    onCopy = { copyNetworkRule(context, rule.origin) },
                    modifier = pageScope.elementModifier(httpHeaderOrder + index + 1, orderCount)
                )
            }
        }
        if (httpsRules.isEmpty() && httpRules.isEmpty()) {
            item(key = "network-empty") {
                Text(
                    "此扩展没有声明网络访问规则。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = pageScope.elementModifier(1, orderCount + 1)
                )
            }
        }
    }
}

internal data class ExtensionNetworkAccessPresentation(
    val httpsOrigins: List<NetworkOriginPermission>,
    val httpOrigins: List<NetworkOriginPermission>
)

internal data class ExtensionNetworkRulePresentation(
    val origin: String,
    val scope: String,
    val runtimeStatus: String,
    val runtimeSchemeSupported: Boolean
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

internal fun extensionNetworkRulePresentation(
    permission: NetworkOriginPermission
): ExtensionNetworkRulePresentation {
    val hostScope = when (permission.hostScope) {
        NetworkHostScope.Exact -> "仅匹配 ${permission.origin.host}"
        NetworkHostScope.SubdomainsOnly -> "仅匹配 ${permission.origin.host} 的子域名，不包含主域名"
    }
    val supported = permission.security == NetworkSecurity.Secure
    return ExtensionNetworkRulePresentation(
        origin = permission.toString(),
        scope = "$hostScope · 端口 ${permission.origin.effectivePort}",
        runtimeStatus = if (supported) "HTTPS 可按规则请求" else "当前运行时不支持 HTTP",
        runtimeSchemeSupported = supported
    )
}

@Composable
private fun NetworkRuleHeading(title: String, description: String, modifier: Modifier = Modifier) {
    OnlineSectionHeading(title, description, modifier)
}

@Composable
private fun NetworkRuleRow(
    rule: ExtensionNetworkRulePresentation,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 4.dp, top = 15.dp, end = 2.dp, bottom = 15.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    rule.origin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                Text(
                    rule.scope,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    rule.runtimeStatus,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rule.runtimeSchemeSupported) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制完整网络规则")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
    }
}

private fun copyNetworkRule(context: android.content.Context, rule: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("网络规则", rule))
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, "已复制网络规则", Toast.LENGTH_SHORT).show()
    }
}
