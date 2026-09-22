package ink.tenqui.flowtone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.ExtensionDiscardChangesConfirmation
import ink.tenqui.flowtone.data.online.credential.CredentialFieldDefinitions
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialSource
import ink.tenqui.flowtone.data.online.credential.CredentialSourceContracts
import ink.tenqui.flowtone.data.online.credential.CredentialSourceInput
import ink.tenqui.flowtone.data.online.credential.CredentialSourceStore
import ink.tenqui.flowtone.data.online.credential.CredentialSourceValidator
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.ui.components.PageTransitionScope

@Composable
internal fun CredentialSourcesScreen(
    pageScope: PageTransitionScope,
    onOpenSource: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { CredentialSourceStore.from(context) }
    var sources by remember { mutableStateOf(emptyList<CredentialSource>()) }
    LaunchedEffect(Unit) { sources = store.list() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { onOpenSource(null) },
                modifier = pageScope.elementModifier(0).fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("添加凭据")
            }
        }
        if (sources.isEmpty()) {
            item {
                CredentialSourceGroup(pageScope.elementModifier(1)) {
                    Text("尚未添加凭据", style = MaterialTheme.typography.bodyMedium)
                    Text("凭据是 Flowtone 用户级资源，可供未来兼容的在线扩展使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            items(sources, key = CredentialSource::id) { source ->
                CredentialSourceGroup(
                    modifier = pageScope.elementModifier(1).clickable { onOpenSource(source.id) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(source.credentialType.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                            Text(credentialSourceSummary(source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CredentialSourceEditScreen(
    sourceId: String?,
    pageScope: PageTransitionScope,
    onBack: () -> Unit,
    onBackActionChange: ((() -> Unit)?) -> Unit,
    onDiscardChangesConfirmationChange: (ExtensionDiscardChangesConfirmation?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { CredentialSourceStore.from(context) }
    var existing by remember(sourceId) { mutableStateOf<CredentialSource?>(null) }
    var type by remember(sourceId) { mutableStateOf<CredentialType?>(null) }
    var label by remember(sourceId) { mutableStateOf("") }
    var realm by remember(sourceId) { mutableStateOf("") }
    var publicFields by remember(sourceId) { mutableStateOf<Map<CredentialFieldId, String>>(emptyMap()) }
    var errors by remember(sourceId) { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(sourceId) {
        existing = sourceId?.let(store::get)
        existing?.let { source ->
            type = source.credentialType
            label = source.label
            realm = source.realm.orEmpty()
            publicFields = source.publicFields
        }
    }
    val draft = type?.let {
        CredentialSourceInput(it, label, realm.takeIf(String::isNotBlank), publicFields)
    }
    val baseline = existing?.let {
        CredentialSourceInput(it.credentialType, it.label, it.realm, it.publicFields)
    }
    val dirty = draft != null && (baseline == null || draft != baseline)
    fun requestBack() {
        if (!dirty) onBack() else onDiscardChangesConfirmationChange(
            ExtensionDiscardChangesConfirmation(
                title = "放弃未保存的更改？",
                message = "未保存的凭据元数据更改将会丢失。",
                onKeepEditing = { onDiscardChangesConfirmationChange(null) },
                onDiscard = { onDiscardChangesConfirmationChange(null); onBack() }
            )
        )
    }
    val currentBackActionChange by rememberUpdatedState(onBackActionChange)
    val currentConfirmationChange by rememberUpdatedState(onDiscardChangesConfirmationChange)
    DisposableEffect(dirty, draft, baseline) {
        currentBackActionChange(::requestBack)
        onDispose { currentBackActionChange(null); currentConfirmationChange(null) }
    }
    BackHandler(onBack = ::requestBack)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (type == null) {
            item {
                Text("选择凭据类型", style = MaterialTheme.typography.titleMedium, modifier = pageScope.elementModifier(0))
                CredentialType.entries.forEach { candidate ->
                    OutlinedButton(onClick = { type = candidate }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(candidate.label)
                    }
                }
            }
        } else {
            item {
                CredentialSourceGroup(pageScope.elementModifier(0)) {
                    Text(type!!.label, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = label, onValueChange = { label = it; errors = errors - "label" }, label = { Text("名称") }, isError = "label" in errors, supportingText = { errors["label"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
                    if (type == CredentialType.GenericAccount) {
                        OutlinedTextField(value = realm, onValueChange = { if (existing == null) { realm = it; errors = errors - "realm" } }, label = { Text("服务 realm") }, enabled = existing == null, isError = "realm" in errors, supportingText = { Text(errors["realm"] ?: if (existing == null) "例如 music.163.com" else "创建后不可修改") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
                    }
                    CredentialSourcePublicFields(type!!, publicFields, errors) { field, value ->
                        publicFields = publicFields.toMutableMap().apply { if (value.isBlank()) remove(field) else put(field, value) }
                        errors = errors - credentialSourcePublicFieldErrorKey(field)
                    }
                }
            }
            item {
                CredentialSourceGroup(pageScope.elementModifier(1)) {
                    type!!.let { currentType ->
                        val secrets = currentType.let { ink.tenqui.flowtone.data.online.credential.CredentialSourceContracts.secretFields(it) }
                        secrets.forEach { field ->
                            Text(CredentialFieldDefinitions.get(field).label, style = MaterialTheme.typography.bodyMedium)
                            Text("需要安全存储支持，当前版本暂不可配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
                        }
                    }
                }
            }
            item {
                Button(onClick = {
                    val input = draft ?: return@Button
                    val validation = CredentialSourceValidator.validateInput(input)
                    errors = validation.associate { it.field to it.reason }
                    if (validation.isEmpty()) {
                        if (sourceId == null) store.create(input) else store.update(sourceId, input)
                        onBack()
                    }
                }, enabled = dirty, modifier = Modifier.fillMaxWidth()) { Text("保存") }
                if (existing != null) {
                    TextButton(onClick = {
                        onDiscardChangesConfirmationChange(
                            ExtensionDiscardChangesConfirmation(
                                title = "删除凭据？",
                                message = "此操作将删除该凭据的非 Secret 元数据。",
                                keepLabel = "取消",
                                discardLabel = "删除",
                                onKeepEditing = { onDiscardChangesConfirmationChange(null) },
                                onDiscard = { store.delete(existing!!.id); onDiscardChangesConfirmationChange(null); onBack() }
                            )
                        )
                    }, modifier = Modifier.fillMaxWidth()) { Text("删除凭据", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun CredentialSourcePublicFields(
    type: CredentialType,
    values: Map<CredentialFieldId, String>,
    errors: Map<String, String>,
    onChange: (CredentialFieldId, String) -> Unit
) {
    CredentialSourceContracts.publicFields(type).sortedBy { CredentialFieldDefinitions.get(it).order }.forEach { field ->
        val definition = CredentialFieldDefinitions.get(field)
        val error = errors[credentialSourcePublicFieldErrorKey(field)]
        OutlinedTextField(
            value = values[field].orEmpty(),
            onValueChange = { onChange(field, it) },
            label = { Text(definition.label) },
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true
        )
    }
}

internal fun credentialSourcePublicFieldErrorKey(field: CredentialFieldId): String =
    "publicFields.${field.value}"

@Composable
private fun CredentialSourceGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
}

internal fun credentialSourceSummary(source: CredentialSource): String = when (source.credentialType) {
    CredentialType.WebDav -> listOfNotNull(source.publicFields[CredentialFieldId.Endpoint], source.publicFields[CredentialFieldId.Username]).joinToString(" · ").ifBlank { "密码未设置" }
    CredentialType.AccountPassword -> listOfNotNull(source.publicFields[CredentialFieldId.Username], source.publicFields[CredentialFieldId.Email], source.publicFields[CredentialFieldId.Phone], source.publicFields[CredentialFieldId.UserId]).firstOrNull() ?: "密码未设置"
    CredentialType.GenericAccount -> listOfNotNull(source.realm, source.publicFields[CredentialFieldId.Account]).joinToString(" · ")
}
