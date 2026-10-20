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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.permission.NetworkHostScope
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.settingsOptionPalette

@Composable
internal fun ExtensionNetworkAccessScreen(
    permissions: Set<NetworkOriginPermission>,
    iconAccent: Color?,
    pageScope: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val presentation = remember(permissions) { extensionNetworkAccessPresentation(permissions) }
    val optionPalette = settingsOptionPalette()
    val extensionCardSurfaceColor = optionPalette.surfaceColorWithAccent(
        iconAccent,
        ExtensionCardTintBlend
    )
    val httpsRules = remember(presentation.httpsOrigins) {
        presentation.httpsOrigins.map(::extensionNetworkRulePresentation)
    }
    val httpRules = remember(presentation.httpOrigins) {
        presentation.httpOrigins.map(::extensionNetworkRulePresentation)
    }
    val orderCount = 1 +
        (if (httpsRules.isNotEmpty()) 1 else 0) +
        (if (httpRules.isNotEmpty()) 1 else 0)
    val httpSectionOrder = 1 + (if (httpsRules.isNotEmpty()) 1 else 0)
    val hasRules = httpsRules.isNotEmpty() || httpRules.isNotEmpty()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp, vertical = 16.dp
        )
    ) {
        item(key = "network-summary") {
            OptionGroup(
                title = "网络访问范围",
                surfaceColor = extensionCardSurfaceColor,
                modifier = pageScope.elementModifier(0, orderCount).padding(bottom = 16.dp)
            ) {
                Text(
                    text = if (hasRules) {
                        "HTTPS 请求 ${httpsRules.size} 项 · HTTP 请求 ${httpRules.size} 项"
                    } else {
                        "此扩展没有声明网络访问规则。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (httpsRules.isNotEmpty()) {
            item(key = "https-requests") {
                OptionGroup(
                    title = "HTTPS 请求",
                    surfaceColor = extensionCardSurfaceColor,
                    modifier = pageScope.elementModifier(1, orderCount).padding(bottom = 16.dp)
                ) {
                    httpsRules.forEachIndexed { index, rule ->
                        key(rule.origin) {
                            NetworkRuleRow(
                                rule = rule,
                                onCopy = { copyNetworkRule(context, rule.origin) },
                                showDivider = index < httpsRules.lastIndex
                            )
                        }
                    }
                }
            }
        }
        if (httpRules.isNotEmpty()) {
            item(key = "http-requests") {
                OptionGroup(
                    title = "HTTP 请求",
                    surfaceColor = extensionCardSurfaceColor,
                    modifier = pageScope.elementModifier(httpSectionOrder, orderCount)
                ) {
                    Text(
                        text = "当前运行时不支持 HTTP 请求。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    httpRules.forEachIndexed { index, rule ->
                        key(rule.origin) {
                            NetworkRuleRow(
                                rule = rule,
                                onCopy = { copyNetworkRule(context, rule.origin) },
                                showDivider = index < httpRules.lastIndex
                            )
                        }
                    }
                }
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
private fun NetworkRuleRow(
    rule: ExtensionNetworkRulePresentation,
    onCopy: () -> Unit,
    showDivider: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
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
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制完整网络规则")
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
        }
    }
}

private fun copyNetworkRule(context: android.content.Context, rule: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("网络规则", rule))
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, "已复制网络规则", Toast.LENGTH_SHORT).show()
    }
}
