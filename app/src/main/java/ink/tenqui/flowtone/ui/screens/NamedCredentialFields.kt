package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.credential.*
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单字段操作保留在当前页面，输入不参与可保存状态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NamedCredentialFields(
    source: CredentialSource,
    installed: InstalledExtension? = null,
    request: CredentialRequestDefinition? = null,
    onChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(context) { CredentialSourceRepository.from(context) }
    val manager = remember(context) { ExtensionManager.get(context) }
    val scope = rememberCoroutineScope()
    var fields by remember(source.id) { mutableStateOf<List<NamedSecretStatus>>(emptyList()) }
    var revision by remember(source.id) { mutableIntStateOf(0) }
    var loadError by remember(source.id) { mutableStateOf(true) }
    var editing by remember(source.id) { mutableStateOf(false) }
    var chosen by remember(source.id) { mutableStateOf<NamedSecretField?>(null) }
    var deleting by remember(source.id) { mutableStateOf<NamedSecretField?>(null) }
    var error by remember(source.id) { mutableStateOf<String?>(null) }
    var busy by remember(source.id) { mutableStateOf(false) }
    LaunchedEffect(source.id, revision) {
        runCatching { withContext(Dispatchers.IO) { repository.namedSecretFields(source.id) } }
            .onSuccess { fields = it; loadError = false }
            .onFailure { loadError = true }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("具名敏感字段 · ${source.label}", style = MaterialTheme.typography.titleSmall)
        Text("由用户提供，账户身份未验证。", style = MaterialTheme.typography.bodySmall)
        if (loadError) Text("字段状态暂不可用，请重试。", color = MaterialTheme.colorScheme.error)
        val declared = request?.named?.fields?.sortedBy { it.id }
        if (declared == null) {
            if (fields.isEmpty() && !loadError) Text("尚无具名字段", style = MaterialTheme.typography.bodySmall)
            fields.forEach { entry ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(entry.field.id); Text("${entry.field.type} · ${namedStatusLabel(entry.state)}", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { chosen = entry.field; editing = true; error = null }, enabled = !busy) { Text("替换") }
                    TextButton(onClick = { deleting = entry.field }, enabled = !busy) { Text("删除") }
                }
            }
            TextButton(onClick = { chosen = null; editing = true; error = null }, enabled = !busy && !loadError) { Text("添加具名字段") }
        } else {
            declared.forEach { field ->
                val saved = fields.firstOrNull { it.field.id == field.id }
                val mismatch = saved != null && saved.field.type != field.type
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(field.id)
                        Text("${field.type} · ${if (field.required) "必需" else "可选"} · ${if (mismatch) "类型不匹配" else namedStatusLabel(saved?.state ?: CredentialSecretState.NotConfigured)}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { chosen = NamedSecretField(field.id, field.type); editing = true; error = null }, enabled = !busy && !loadError && !mismatch) {
                        Text(if (saved == null) "手动补充" else "替换")
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (editing) {
        val input = remember(source.id, chosen) { TextFieldState() }
        var id by remember(source.id, chosen) { mutableStateOf(chosen?.id.orEmpty()) }
        var type by remember(source.id, chosen) { mutableStateOf(chosen?.type ?: NamedSecretType.Cookie) }
        var confirmReplace by remember(source.id, chosen) { mutableStateOf(false) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, input) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) input.edit { replace(0, length, "") } }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); input.edit { replace(0, length, "") } }
        }
        val present = fields.any { it.field.id == id }
        AlertDialog(
            onDismissRequest = { if (!busy) editing = false },
            title = { Text(if (present) "替换具名字段" else "手动添加字段") },
            text = {
                Column {
                    Text("账户：${source.label} · ${source.realm.orEmpty()}")
                    Text("用户提供的凭据；账户身份未经验证。", style = MaterialTheme.typography.bodySmall)
                    request?.description?.let { Text("扩展用途：$it", style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(value = id, onValueChange = { id = it; confirmReplace = false }, enabled = chosen == null && !busy, label = { Text("字段 ID") }, singleLine = true)
                    if (chosen == null) Row { NamedSecretType.entries.forEach { candidate -> TextButton(onClick = { type = candidate }, enabled = !busy) { Text(if (type == candidate) "✓ ${candidate.name}" else candidate.name) } } }
                    else Text("类型：${type.name}")
                    OutlinedSecureTextField(state = input, label = { Text("字段值") }, textObfuscationMode = TextObfuscationMode.Hidden, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (present) Row { Checkbox(checked = confirmReplace, onCheckedChange = { confirmReplace = it }, enabled = !busy); Text("确认替换此账户中已有字段") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (!present || confirmReplace), onClick = {
                    val field = runCatching { NamedSecretField(id, type) }.getOrNull()
                    if (field == null || input.text.isEmpty()) { error = "字段 ID 或内容无效。"; return@TextButton }
                    val value = input.text.toString()
                    busy = true; error = null
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) {
                            if (request != null) {
                                val boundInstalled = requireNotNull(installed)
                                repository.importNamedSecretForRequest(boundInstalled.manifest.id, requireNotNull(boundInstalled.installationInstanceId), request.id, request, source.id, field, value, confirmReplace) {
                                    manager.installedExtensions().firstOrNull { it.manifest.id == boundInstalled.manifest.id }
                                }
                            } else repository.importNamedSecret(source.id, field, value, confirmReplace)
                        } }
                        busy = false
                        result.onSuccess { input.edit { replace(0, length, "") }; editing = false; revision++; manager.invalidateCredentialContext(); onChanged() }
                            .onFailure { error = "保存失败：账户、字段或扩展请求可能已变化，请检查后重试。" }
                    }
                }) { Text(if (busy) "保存中…" else "确认保存") }
            }, dismissButton = { TextButton(onClick = { editing = false }, enabled = !busy) { Text("取消") } }
        )
    }
    deleting?.let { field ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("删除具名字段？") },
            text = { Text("将删除账户 ${source.label} 的 ${field.id}；相关授权可能暂不可用。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { repository.deleteNamedSecret(source.id, field) } }
                    busy = false; deleting = null
                    result.onSuccess { revision++; manager.invalidateCredentialContext(); onChanged() }.onFailure { error = "删除失败，请重试。" }
                }
            }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text("取消") } })
    }
}

internal fun namedStatusLabel(state: CredentialSecretState): String = when (state) {
    CredentialSecretState.Configured -> "已配置"
    CredentialSecretState.NotConfigured -> "缺失"
    CredentialSecretState.Unavailable -> "不可用"
}
