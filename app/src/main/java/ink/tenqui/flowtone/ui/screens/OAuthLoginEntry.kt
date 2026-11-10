package ink.tenqui.flowtone.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.credential.CredentialAcquisitionDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialSource
import ink.tenqui.flowtone.data.online.credential.CredentialSourceRepository
import ink.tenqui.flowtone.data.online.oauth.OAuthBrowserHost
import ink.tenqui.flowtone.data.online.oauth.OAuthUiPhase
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OAuthLoginEntry(installed: InstalledExtension, source: CredentialSource, method: CredentialAcquisitionDefinition, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by OAuthBrowserHost.status.collectAsState()
    var confirm by remember(source.id, method.methodId) { mutableStateOf(false) }
    var fieldExists by remember(source.id, method.methodId) { mutableStateOf<Boolean?>(null) }
    var replaceConfirmed by remember(source.id, method.methodId) { mutableStateOf(false) }
    val own = status.extensionId == installed.manifest.id && status.accountId == source.id && status.methodId == method.methodId
    val inProgress = status.phase in setOf(OAuthUiPhase.PREPARING, OAuthUiPhase.WAITING, OAuthUiPhase.VERIFYING, OAuthUiPhase.EXCHANGING, OAuthUiPhase.SAVING)
    val available = OAuthBrowserHost.canStart(installed.manifest.id, method) && installed.installationInstanceId != null
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(method.label, style = MaterialTheme.typography.titleSmall)
        Text(method.description, style = MaterialTheme.typography.bodySmall)
        if (!available) Text("不可用：需要受信任的 Provider 配置或 Token Broker。", style = MaterialTheme.typography.bodySmall)
        if (own) {
            val label = when (status.phase) {
                OAuthUiPhase.PREPARING -> "准备授权…"
                OAuthUiPhase.WAITING -> "等待浏览器授权"
                OAuthUiPhase.VERIFYING -> "正在验证回调…"
                OAuthUiPhase.EXCHANGING -> "正在交换 Token…"
                OAuthUiPhase.SAVING -> "正在保存凭据…"
                OAuthUiPhase.SAVED -> "字段已配置，远程账户身份未验证；仅兼容的 OAuth client 可申请授权。${if (status.refreshNotSaved) "刷新令牌未保存。" else ""}"
                OAuthUiPhase.FAILED -> "授权失败：${status.error.orEmpty()}"
                OAuthUiPhase.CANCELLED -> "授权已取消"
                else -> ""
            }
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall)
        }
        if (own && inProgress) {
            TextButton(onClick = { OAuthBrowserHost.cancel() }, enabled = status.phase != OAuthUiPhase.SAVING) { Text("取消授权") }
        } else {
            TextButton(enabled = available && !inProgress, onClick = { confirm = true }) { Text("使用浏览器授权") }
        }
    }
    LaunchedEffect(own, status.phase) { if (own && status.phase == OAuthUiPhase.SAVED) onSaved() }
    LaunchedEffect(confirm) {
        if (confirm) {
            fieldExists = null; replaceConfirmed = false
            fieldExists = runCatching { withContext(Dispatchers.IO) {
                CredentialSourceRepository.from(context).namedSecretFields(source.id).any { it.field.id == "oauth.access_token" }
            } }.getOrNull()
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("确认浏览器授权") },
        text = { Column {
            Text("扩展：${installed.manifest.name}\n服务：${method.realm}\n账户：${source.label}\n用途：${method.description}\n仅保存 access_token；身份未经验证，过期后可能需要重新登录。")
            if (fieldExists == null) Text("正在检查字段状态；若检查失败请取消并重试。")
            if (fieldExists == true) {
                Text("此账户已有 oauth.access_token。仅允许替换相同 OAuth client 的字段；不兼容的字段会被拒绝。")
                androidx.compose.foundation.layout.Row { Checkbox(checked = replaceConfirmed, onCheckedChange = { replaceConfirmed = it }); Text("确认替换此账户的字段") }
            }
        } },
        confirmButton = { TextButton(enabled = fieldExists != null && (fieldExists == false || replaceConfirmed), onClick = {
            confirm = false
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) {
                    OAuthBrowserHost.prepare(context.applicationContext, installed.manifest.id, requireNotNull(installed.installationInstanceId), source.id, method, replaceConfirmed)
                } }
                result.onSuccess { url ->
                    val current = OAuthBrowserHost.status.value
                    if (current.phase != OAuthUiPhase.WAITING || current.extensionId != installed.manifest.id ||
                        current.accountId != source.id || current.methodId != method.methodId) return@onSuccess
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        .onFailure { OAuthBrowserHost.browserFailed() }
                }
            }
        }) { Text("继续") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } }
    )
}
