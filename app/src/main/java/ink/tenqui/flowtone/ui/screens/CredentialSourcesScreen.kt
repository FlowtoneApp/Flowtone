package ink.tenqui.flowtone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ink.tenqui.flowtone.app.ExtensionDiscardChangesConfirmation
import ink.tenqui.flowtone.data.online.credential.CredentialFieldDefinitions
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialSource
import ink.tenqui.flowtone.data.online.credential.CredentialSourceContracts
import ink.tenqui.flowtone.data.online.credential.CredentialSecretDraft
import ink.tenqui.flowtone.data.online.credential.CredentialSecretMutation
import ink.tenqui.flowtone.data.online.credential.CredentialSecretReadResult
import ink.tenqui.flowtone.data.online.credential.CredentialSecretState
import ink.tenqui.flowtone.data.online.credential.CredentialSourceInput
import ink.tenqui.flowtone.data.online.credential.CredentialSourceRepository
import ink.tenqui.flowtone.data.online.credential.CredentialSourceSaveException
import ink.tenqui.flowtone.data.online.credential.CredentialSourceValidator
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CredentialSourcesScreen(
    pageScope: PageTransitionScope,
    onOpenSource: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { CredentialSourceRepository.from(context) }
    var sources by remember { mutableStateOf(emptyList<CredentialSource>()) }
    LaunchedEffect(Unit) { sources = repository.list() }

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { CredentialSourceRepository.from(context) }
    val revealedSecret = remember(sourceId) { CredentialSecretRevealState() }
    val passwordInput = remember(sourceId) { TextFieldState() }
    var existing by remember(sourceId) { mutableStateOf<CredentialSource?>(null) }
    var type by remember(sourceId) { mutableStateOf<CredentialType?>(null) }
    var label by remember(sourceId) { mutableStateOf("") }
    var realm by remember(sourceId) { mutableStateOf("") }
    var publicFields by remember(sourceId) { mutableStateOf<Map<CredentialFieldId, String>>(emptyMap()) }
    var passwordClearPending by remember(sourceId) { mutableStateOf(false) }
    var cookieDraft by remember(sourceId) { mutableStateOf(CredentialSecretDraft()) }
    var passwordInputVisible by remember(sourceId) { mutableStateOf(false) }
    var passwordInputFocused by remember(sourceId) { mutableStateOf(false) }
    var cookieInputVisible by remember(sourceId) { mutableStateOf(false) }
    var revealJob by remember(sourceId) { mutableStateOf<Job?>(null) }
    var errors by remember(sourceId) { mutableStateOf(emptyMap<String, String>()) }
    var operationError by remember(sourceId) { mutableStateOf<String?>(null) }
    fun hideSavedSecret() {
        revealJob?.cancel()
        revealJob = null
        revealedSecret.hide()
    }
    fun hideSecretDisplays() {
        hideSavedSecret()
        passwordInputVisible = false
        passwordInputFocused = false
        cookieInputVisible = false
    }
    fun leavePage() {
        hideSecretDisplays()
        onBack()
    }
    DisposableEffect(lifecycleOwner, revealedSecret) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) hideSecretDisplays()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            revealJob?.cancel()
            revealedSecret.close()
        }
    }
    LaunchedEffect(sourceId) {
        existing = sourceId?.let(repository::get)
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
    val hasSecretChanges = credentialSecretDraftHasChanges(passwordInput.text, passwordClearPending, cookieDraft)
    val dirty = draft != null && (
        baseline == null || draft != baseline ||
            hasSecretChanges
        )
    fun requestBack() {
        if (!dirty) leavePage() else onDiscardChangesConfirmationChange(
            ExtensionDiscardChangesConfirmation(
                title = "放弃未保存的更改？",
                message = "未保存的凭据信息和 Secret 更改将会丢失。",
                onKeepEditing = { onDiscardChangesConfirmationChange(null) },
                onDiscard = { onDiscardChangesConfirmationChange(null); leavePage() }
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
                    OutlinedTextField(value = label, onValueChange = { label = it; errors = errors - "label" }, label = { Text("凭证名称") }, isError = "label" in errors, supportingText = { errors["label"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
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
                        val secrets = CredentialSourceContracts.secretFields(currentType)
                            .sortedBy { CredentialFieldDefinitions.get(it).order }
                        secrets.forEach { field ->
                            val definition = CredentialFieldDefinitions.get(field)
                            val storedState = existing?.secretFieldStates?.get(field)
                                ?: CredentialSecretState.NotConfigured
                            val isPassword = field == CredentialFieldId.Password
                            val replacing = if (isPassword) passwordInput.text.isNotEmpty()
                                else cookieDraft.mutation is CredentialSecretMutation.Replace
                            val deleting = if (isPassword) !replacing && passwordClearPending
                                else cookieDraft.mutation == CredentialSecretMutation.Delete
                            val status = when {
                                replacing -> "保存后替换"
                                deleting -> "保存后清除"
                                storedState == CredentialSecretState.NotConfigured -> "未设置"
                                storedState == CredentialSecretState.Configured -> "已保存"
                                else -> "已保存，但无法解密；请重新设置"
                            }
                            if (!isPassword) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(definition.label, style = MaterialTheme.typography.titleSmall)
                                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (storedState == CredentialSecretState.Configured && existing != null) {
                                    val shown = revealedSecret.field == field
                                    IconButton(onClick = {
                                        if (shown) hideSavedSecret() else {
                                            hideSavedSecret()
                                            val id = existing?.id ?: return@IconButton
                                            revealJob = scope.launch {
                                                revealedSecret.reveal(field) { requested ->
                                                    withContext(Dispatchers.IO) { repository.readSecret(id, requested) }
                                                }
                                            }
                                        }
                                    }) {
                                        Icon(
                                            if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = "${if (shown) "隐藏" else "显示"}已保存的${definition.label}"
                                        )
                                    }
                                }
                            }
                            if (!isPassword && revealedSecret.field == field) {
                                when (val result = revealedSecret.result) {
                                    is CredentialSecretReadResult.Available -> OutlinedTextField(
                                        value = result.plaintext,
                                        onValueChange = {},
                                        label = { Text("已保存的${definition.label}（只读）") },
                                        readOnly = true,
                                        singleLine = isPassword,
                                        minLines = if (isPassword) 1 else 3,
                                        maxLines = if (isPassword) 1 else 6,
                                        modifier = Modifier.fillMaxWidth().then(if (isPassword) Modifier else Modifier.heightIn(max = 220.dp))
                                    )
                                    is CredentialSecretReadResult.Unavailable -> Text("已保存的${definition.label}不可用，请输入新值重新设置。", color = MaterialTheme.colorScheme.error)
                                    CredentialSecretReadResult.NotConfigured -> Text("已保存的${definition.label}已不存在，请输入新值重新设置。", color = MaterialTheme.colorScheme.error)
                                    null -> Text("正在读取已保存的${definition.label}…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (isPassword) {
                                val savedPasswordShown = revealedSecret.field == field
                                val savedPassword = revealedSecret.result as? CredentialSecretReadResult.Available
                                val revealPending = savedPasswordShown && revealedSecret.result == null
                                val revealFailure = revealedSecret.result is CredentialSecretReadResult.Unavailable ||
                                    revealedSecret.result == CredentialSecretReadResult.NotConfigured
                                val canRevealSaved = storedState == CredentialSecretState.Configured && existing != null
                                val hasInput = passwordInput.text.isNotEmpty()
                                val showSavedValue = savedPasswordShown && savedPassword != null && !hasInput
                                val showSavedPlaceholder = canRevealSaved && !hasInput &&
                                    !passwordInputFocused && !passwordClearPending &&
                                    revealedSecret.field == null

                                LaunchedEffect(hasInput, savedPasswordShown) {
                                    if (hasInput && savedPasswordShown) hideSavedSecret()
                                }
                                Text("密码", style = MaterialTheme.typography.titleSmall)
                                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    OutlinedSecureTextField(
                                        state = passwordInput,
                                        label = null,
                                        textObfuscationMode = credentialPasswordObfuscationMode(passwordInputVisible),
                                        trailingIcon = {
                                            IconButton(
                                                enabled = hasInput || canRevealSaved || showSavedValue,
                                                onClick = {
                                                    when {
                                                        showSavedValue -> {
                                                            hideSavedSecret()
                                                            passwordInputFocused = false
                                                        }
                                                        hasInput -> passwordInputVisible = !passwordInputVisible
                                                        canRevealSaved -> {
                                                            val id = existing?.id ?: return@IconButton
                                                            passwordInputVisible = false
                                                            passwordInputFocused = false
                                                            revealJob?.cancel()
                                                            revealJob = scope.launch {
                                                                revealedSecret.reveal(field) { requested ->
                                                                    withContext(Dispatchers.IO) { repository.readSecret(id, requested) }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    if (passwordInputVisible || showSavedValue) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = if (passwordInputVisible || showSavedValue) "隐藏密码" else "显示密码"
                                                )
                                            }
                                        },
                                        isError = "secrets.${field.value}" in errors || revealFailure,
                                        modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                                            passwordInputFocused = focus.isFocused
                                            if (focus.isFocused && showSavedValue) hideSavedSecret()
                                        }
                                    )
                                    AnimatedContent(
                                        showSavedValue,
                                        transitionSpec = {
                                            if (targetState) {
                                                fadeIn(tween(FlowtoneMotion.ShortDurationMillis, easing = FlowtoneMotion.Easing)) togetherWith ExitTransition.None
                                            } else {
                                                EnterTransition.None togetherWith ExitTransition.None
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 64.dp),
                                        label = "credentialSavedPasswordContent"
                                    ) { savedValueVisible ->
                                        when {
                                            savedValueVisible && showSavedValue -> Text(
                                                savedPassword?.plaintext.orEmpty(),
                                                maxLines = 1,
                                                softWrap = false,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            showSavedPlaceholder -> Text(
                                                "•••",
                                                maxLines = 1,
                                                softWrap = false,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            revealPending -> Text(
                                                "…",
                                                maxLines = 1,
                                                softWrap = false,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                val passwordSupportingText = when {
                                    revealPending -> "正在读取已保存的密码…"
                                    revealFailure -> "已保存的密码不可用，请输入新密码重新设置。"
                                    "secrets.${field.value}" in errors -> errors.getValue("secrets.${field.value}")
                                    else -> status
                                }
                                Text(
                                    passwordSupportingText,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (revealFailure || "secrets.${field.value}" in errors) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                OutlinedTextField(
                                    value = cookieDraft.input,
                                    onValueChange = { value ->
                                        cookieDraft = cookieDraft.edit(value)
                                        errors = errors - "secrets.${field.value}"
                                        operationError = null
                                    },
                                    label = { Text("新 Cookie") },
                                    visualTransformation = credentialCookieVisualTransformation(cookieInputVisible),
                                    trailingIcon = {
                                        IconButton(onClick = { cookieInputVisible = !cookieInputVisible }) {
                                            Icon(
                                                if (cookieInputVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (cookieInputVisible) "隐藏新 Cookie" else "显示新 Cookie"
                                            )
                                        }
                                    },
                                    isError = "secrets.${field.value}" in errors,
                                    supportingText = { errors["secrets.${field.value}"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(max = 220.dp),
                                    singleLine = false,
                                    minLines = 4,
                                    maxLines = 6
                                )
                            }
                            if (deleting) {
                                TextButton(onClick = {
                                    if (isPassword) passwordClearPending = false else cookieDraft = CredentialSecretDraft()
                                }) { Text("撤销清除") }
                            } else if (storedState != CredentialSecretState.NotConfigured || replacing) {
                                TextButton(onClick = {
                                    if (isPassword) {
                                        passwordInput.edit { replace(0, length, "") }
                                        passwordClearPending = true
                                    } else cookieDraft = cookieDraft.clear()
                                    hideSavedSecret()
                                    errors = errors - "secrets.${field.value}"
                                    operationError = null
                                }) { Text("清除${definition.label}", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = {
                    val input = draft ?: return@Button
                    val secretMutations = CredentialSourceContracts.secretFields(input.credentialType).associateWith { field ->
                        if (field == CredentialFieldId.Password) credentialSecretInputMutation(passwordInput.text, passwordClearPending)
                        else cookieDraft.mutation
                    }
                    val validation = CredentialSourceValidator.validateInput(input) +
                        CredentialSourceRepository.validateSecretMutations(type!!, secretMutations)
                    errors = validation.associate { it.field to it.reason }
                    operationError = null
                    if (validation.isEmpty()) {
                        try {
                            val saved = repository.save(existing?.id ?: sourceId, input, secretMutations)
                            existing = saved
                            leavePage()
                        } catch (failure: CredentialSourceSaveException) {
                            existing = repository.get(failure.persistedMetadata.id) ?: failure.persistedMetadata
                            operationError = "凭证信息已保存，但 Secret 保存失败。请重试。"
                        } catch (_: Exception) {
                            operationError = "凭证保存失败，请检查信息或安全存储状态后重试。"
                        }
                    }
                }, enabled = dirty, modifier = Modifier.fillMaxWidth()) { Text("保存") }
                operationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (existing != null) {
                    TextButton(onClick = {
                        onDiscardChangesConfirmationChange(
                            ExtensionDiscardChangesConfirmation(
                                title = "删除凭据？",
                                message = "此操作将删除凭证元数据及其已保存的 Secret。",
                                keepLabel = "取消",
                                discardLabel = "删除",
                                onKeepEditing = { onDiscardChangesConfirmationChange(null) },
                                onDiscard = {
                                    val id = existing!!.id
                                    onDiscardChangesConfirmationChange(null)
                                    try {
                                        if (repository.delete(id)) leavePage()
                                        else operationError = "凭证不存在，未完成删除。"
                                    } catch (_: Exception) {
                                        operationError = "无法完整删除凭证及其 Secret，请重试。"
                                    }
                                }
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

internal fun credentialSecretInputMutation(input: CharSequence, deleteRequested: Boolean): CredentialSecretMutation = when {
    input.isNotEmpty() -> CredentialSecretMutation.Replace(input.toString())
    deleteRequested -> CredentialSecretMutation.Delete
    else -> CredentialSecretMutation.Keep
}

internal fun credentialSecretDraftHasChanges(
    passwordInput: CharSequence,
    passwordDeleteRequested: Boolean,
    cookieDraft: CredentialSecretDraft
): Boolean = passwordInput.isNotEmpty() || passwordDeleteRequested ||
    cookieDraft.mutation != CredentialSecretMutation.Keep

internal fun credentialPasswordObfuscationMode(visible: Boolean): TextObfuscationMode =
    if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped

internal fun credentialCookieVisualTransformation(visible: Boolean): VisualTransformation =
    if (visible) VisualTransformation.None else CookieMaskVisualTransformation

internal object CookieMaskVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        AnnotatedString(buildString(text.length) {
            text.text.forEach { append(if (it == '\n' || it == '\r') it else '•') }
        }),
        OffsetMapping.Identity
    )
}

@Composable
private fun CredentialSourceGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
}

internal fun credentialSourceSummary(source: CredentialSource): String = when (source.credentialType) {
    CredentialType.WebDav -> listOfNotNull(source.publicFields[CredentialFieldId.Endpoint], source.publicFields[CredentialFieldId.Username]).joinToString(" · ").ifBlank { "密码未设置" }
    CredentialType.AccountPassword -> listOfNotNull(source.publicFields[CredentialFieldId.Username], source.publicFields[CredentialFieldId.Email], source.publicFields[CredentialFieldId.Phone], source.publicFields[CredentialFieldId.UserId]).firstOrNull() ?: "未填写身份标识"
    CredentialType.GenericAccount -> listOfNotNull(source.realm, source.publicFields[CredentialFieldId.Account]).joinToString(" · ")
}
