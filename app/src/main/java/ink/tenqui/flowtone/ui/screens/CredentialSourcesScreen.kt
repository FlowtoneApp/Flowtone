package ink.tenqui.flowtone.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle.State
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { CredentialSourceRepository.from(context) }
    var sources by remember { mutableStateOf(emptyList<CredentialSource>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableStateOf(0) }
    var hasBeenCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(refreshGeneration) {
        val result = runCatching { withContext(Dispatchers.IO) { repository.list() } }
        result.onSuccess { sources = it; error = false }.onFailure { error = true }
        loading = false
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !loading) refreshGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pageScope.phase) {
        if (pageScope.phase == ink.tenqui.flowtone.ui.components.PageTransitionPhase.Current) {
            if (hasBeenCurrent && !loading) refreshGeneration++
            hasBeenCurrent = true
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "your-credentials") {
            OnlineSectionHeading(
                title = "你的凭据",
                subtitle = "独立保存，由你决定授权给哪个扩展",
                modifier = pageScope.elementModifier(0, sources.size + 3)
            )
        }
        item(key = "create") {
            OnlinePrimaryAction(
                title = "创建凭据",
                subtitle = "为在线服务保存账户信息",
                icon = Icons.Rounded.Key,
                onClick = { onOpenSource(null) },
                modifier = pageScope.elementModifier(1, sources.size + 3)
            )
        }
        if (loading && sources.isEmpty()) {
            item(key = "loading") { Text("正在读取凭据…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (error && sources.isEmpty()) {
            item(key = "error") {
                Column {
                    Text("凭据暂时无法读取", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refreshGeneration++ }) { Text("重试") }
                }
            }
        } else if (sources.isEmpty()) {
            item {
                Column(pageScope.elementModifier(2).padding(vertical = 16.dp)) {
                    Text("还没有凭据", style = MaterialTheme.typography.titleSmall)
                    Text("创建后可在扩展的凭据授权页选择。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            item(key = "heading") {
                OnlineSectionHeading("已保存的凭据", "${sources.size} 份凭据", pageScope.elementModifier(2, sources.size + 3).padding(top = 18.dp))
            }
            if (error) item(key = "refresh_error") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("刷新失败，列表可能不是最新状态", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { refreshGeneration++ }) { Text("重试") }
                }
            }
            itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                CredentialSourceLine(source, { onOpenSource(source.id) }, pageScope.elementModifier(3 + index, sources.size + 3))
                if (index != sources.lastIndex) androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
                )
            }
        }
    }
}

@Composable
private fun CredentialSourceLine(source: CredentialSource, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(source.label, style = MaterialTheme.typography.bodyLarge)
            Text("${source.credentialType.label} · ${credentialSourceSummary(source)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(credentialSourceStatus(source), style = MaterialTheme.typography.labelMedium,
                color = if (source.secretFieldStates.values.any { it == CredentialSecretState.Unavailable })
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp))
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
    val accessGate = remember(sourceId) { CredentialSecretAccessGate() }
    val hostActivity = remember(context) { context.findFragmentActivity() }
    val systemAuthenticator = remember(hostActivity) { hostActivity?.let(::CredentialSecretAuthenticator) }
    val windowFlag = remember(hostActivity) { CredentialSecretWindowFlag() }
    val passwordInput = remember(sourceId) { TextFieldState() }
    var existing by remember(sourceId) { mutableStateOf<CredentialSource?>(null) }
    var sourceLoading by remember(sourceId) { mutableStateOf(sourceId != null) }
    var sourceLoadError by remember(sourceId) { mutableStateOf(false) }
    var type by remember(sourceId) { mutableStateOf<CredentialType?>(null) }
    var label by remember(sourceId) { mutableStateOf("") }
    var realm by remember(sourceId) { mutableStateOf("") }
    var publicFields by remember(sourceId) { mutableStateOf<Map<CredentialFieldId, String>>(emptyMap()) }
    var passwordClearPending by remember(sourceId) { mutableStateOf(false) }
    var cookieDraft by remember(sourceId) { mutableStateOf(CredentialSecretDraft()) }
    var passwordInputVisible by remember(sourceId) { mutableStateOf(false) }
    var cookieInputVisible by remember(sourceId) { mutableStateOf(false) }
    var revealJob by remember(sourceId) { mutableStateOf<Job?>(null) }
    var pendingKeyguardRequest by remember(sourceId) { mutableStateOf<CredentialSecretAccessRequest?>(null) }
    var deferredAuthentication by remember(sourceId) { mutableStateOf<CredentialSecretAccessRequest?>(null) }
    var errors by remember(sourceId) { mutableStateOf(emptyMap<String, String>()) }
    var operationError by remember(sourceId) { mutableStateOf<String?>(null) }
    var authenticationError by remember(sourceId) { mutableStateOf<String?>(null) }
    var authenticationErrorField by remember(sourceId) { mutableStateOf<CredentialFieldId?>(null) }
    var saving by remember(sourceId) { mutableStateOf(false) }
    var deletingSource by remember(sourceId) { mutableStateOf(false) }

    fun hideSavedSecret() {
        accessGate.activeRequest?.let(accessGate::cancelRead)
        revealJob?.cancel()
        revealJob = null
        revealedSecret.hide()
    }
    fun hideSecretDisplays() {
        hideSavedSecret()
        passwordInputVisible = false
        cookieInputVisible = false
    }
    fun cancelAuthentication() {
        pendingKeyguardRequest?.let(accessGate::cancel)
        pendingKeyguardRequest = null
        deferredAuthentication = null
        accessGate.cancelAll()
        systemAuthenticator?.cancel()
    }
    fun leavePage() {
        hideSecretDisplays()
        cancelAuthentication()
        accessGate.close()
        onBack()
    }

    fun startReadAfterAuthentication(request: CredentialSecretAccessRequest) {
        if (!accessGate.authorizeRead(request, sourceId.orEmpty())) return
        deferredAuthentication = null
        authenticationError = null
        authenticationErrorField = null
        revealJob?.cancel()
        revealJob = scope.launch {
            try {
                revealedSecret.reveal(request.fieldId) { field ->
                    withContext(Dispatchers.IO) {
                        repository.readSecret(request.sourceId, field)
                    }
                }
            } finally {
                accessGate.finishRead(request)
            }
        }
    }

    fun receiveAuthenticationSuccess(request: CredentialSecretAccessRequest, allowDeferred: Boolean = false) {
        if (!accessGate.canCompleteAuthentication(request, sourceId.orEmpty())) return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(State.STARTED)) {
            // Only the system Keyguard return may be deferred across an Activity stop.
            if (allowDeferred) deferredAuthentication = request else accessGate.cancel(request)
            return
        }
        startReadAfterAuthentication(request)
    }

    fun failAuthentication(request: CredentialSecretAccessRequest, message: String) {
        if (!accessGate.canCompleteAuthentication(request, sourceId.orEmpty())) return
        accessGate.cancel(request)
        if (pendingKeyguardRequest == request) pendingKeyguardRequest = null
        deferredAuthentication = null
        authenticationErrorField = request.fieldId
        authenticationError = message
    }

    val keyguardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pendingKeyguardRequest
        pendingKeyguardRequest = null
        if (request != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                receiveAuthenticationSuccess(request, allowDeferred = true)
            } else {
                failAuthentication(request, "身份认证未完成，请重试。")
            }
        }
    }

    fun startKeyguardConfirmation(request: CredentialSecretAccessRequest) {
        val activity = hostActivity
        val keyguard = activity?.getSystemService(KeyguardManager::class.java)
        if (activity == null || keyguard?.isDeviceSecure != true) {
            failAuthentication(request, "请先设置设备锁屏保护，再查看已保存的凭证。")
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "验证身份",
            "查看已保存的凭证"
        )
        if (intent == null) {
            failAuthentication(request, "系统设备认证暂不可用，请确认设备已设置安全锁屏。")
            return
        }
        pendingKeyguardRequest = request
        try {
            keyguardLauncher.launch(intent)
        } catch (_: Exception) {
            pendingKeyguardRequest = null
            failAuthentication(request, "无法启动系统设备认证，请重试。")
        }
    }

    fun requestSavedSecret(field: CredentialFieldId) {
        val id = sourceId ?: return
        if (existing == null || accessGate.isBusy) return
        hideSavedSecret()
        authenticationError = null
        authenticationErrorField = field
        val request = accessGate.begin(id, field) ?: return
        when (
            val result = systemAuthenticator?.authenticate(
                onAuthenticated = { receiveAuthenticationSuccess(request) },
                onUseDeviceCredential = { startKeyguardConfirmation(request) },
                onFailure = { failAuthentication(request, "身份认证未完成，请重试。") }
            ) ?: CredentialSecretAuthenticationLaunch.Unavailable
        ) {
            CredentialSecretAuthenticationLaunch.PromptStarted -> Unit
            CredentialSecretAuthenticationLaunch.UseDeviceCredential -> startKeyguardConfirmation(request)
            CredentialSecretAuthenticationLaunch.MissingDeviceSecurity ->
                failAuthentication(request, "请先设置设备锁屏保护，再查看已保存的凭证。")
            CredentialSecretAuthenticationLaunch.Unavailable ->
                failAuthentication(request, "系统身份认证暂不可用，请检查设备锁屏设置后重试。")
            }
    }

    val showsSavedSecret = revealedSecret.result is CredentialSecretReadResult.Available
    val showsPlaintext = showsSavedSecret ||
        passwordInput.text.isNotEmpty() ||
        (cookieInputVisible && cookieDraft.input.isNotEmpty())
    val currentShowsPlaintext by rememberUpdatedState(showsPlaintext)
    DisposableEffect(lifecycleOwner, revealedSecret, accessGate, sourceId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (currentShowsPlaintext) windowFlag.retainUntilForeground(lifecycleOwner.lifecycle)
                    hideSecretDisplays()
                    if (pendingKeyguardRequest == null && deferredAuthentication == null) cancelAuthentication()
                }
                Lifecycle.Event.ON_START -> deferredAuthentication?.let { receiveAuthenticationSuccess(it) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            revealJob?.cancel()
            revealedSecret.close()
            accessGate.close()
            systemAuthenticator?.cancel()
            pendingKeyguardRequest = null
            deferredAuthentication = null
            windowFlag.hide()
        }
    }

    DisposableEffect(hostActivity?.window, showsPlaintext, windowFlag) {
        val window = hostActivity?.window
        if (window != null && showsPlaintext) windowFlag.show(window) else windowFlag.hide()
        onDispose { windowFlag.hide() }
    }
    LaunchedEffect(sourceId) {
        val result = runCatching { withContext(Dispatchers.IO) { sourceId?.let(repository::get) } }
        existing = result.getOrNull()
        sourceLoadError = sourceId != null && existing == null
        sourceLoading = false
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
    LaunchedEffect(passwordInput.text.length) {
        if (passwordInput.text.isNotEmpty() && revealedSecret.field == CredentialFieldId.Password) hideSavedSecret()
    }
    LaunchedEffect(revealedSecret.result) {
        if (revealedSecret.result is CredentialSecretReadResult.Available) {
            kotlinx.coroutines.delay(30_000)
            hideSavedSecret()
        }
    }
    val dirty = draft != null && (
        baseline == null || draft != baseline ||
            hasSecretChanges
        )
    fun requestBack() {
        if (saving || deletingSource) return
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
    DisposableEffect(dirty, draft, baseline, saving, deletingSource) {
        currentBackActionChange(::requestBack)
        onDispose { currentBackActionChange(null); currentConfirmationChange(null) }
    }
    BackHandler(onBack = ::requestBack)

    fun chooseType(candidate: CredentialType) {
        if (type == candidate || saving || deletingSource) return
        val resetDraft = {
            type = candidate
            label = ""
            realm = ""
            publicFields = emptyMap()
            passwordInput.edit { replace(0, length, "") }
            cookieDraft = CredentialSecretDraft()
            passwordClearPending = false
            hideSecretDisplays()
            cancelAuthentication()
            errors = emptyMap()
            operationError = null
        }
        if (type != null && (label.isNotBlank() || realm.isNotBlank() || publicFields.isNotEmpty() || hasSecretChanges)) {
            onDiscardChangesConfirmationChange(
                ExtensionDiscardChangesConfirmation(
                    title = "切换凭据类型？",
                    message = "切换后将清空当前填写但尚未保存的信息。",
                    onKeepEditing = { onDiscardChangesConfirmationChange(null) },
                    onDiscard = { onDiscardChangesConfirmationChange(null); resetDraft() }
                )
            )
        } else resetDraft()
    }

    fun saveSource() {
        if (saving || deletingSource) return
        val input = draft ?: return
        val secretMutations = CredentialSourceContracts.secretFields(input.credentialType).associateWith { field ->
            if (field == CredentialFieldId.Password) credentialSecretInputMutation(passwordInput.text, passwordClearPending)
            else cookieDraft.mutation
        }
        val validation = CredentialSourceValidator.validateInput(input) +
            CredentialSourceRepository.validateSecretMutations(input.credentialType, secretMutations)
        errors = validation.associate { it.field to it.reason }
        operationError = null
        if (validation.isNotEmpty()) return
        saving = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.save(existing?.id ?: sourceId, input, secretMutations) }
            }
            saving = false
            result.onSuccess { saved ->
                existing = saved
                leavePage()
            }.onFailure { failure ->
                if (failure is CredentialSourceSaveException) {
                    existing = withContext(Dispatchers.IO) {
                        repository.get(failure.persistedMetadata.id)
                    } ?: failure.persistedMetadata
                    operationError = "凭据信息已保存，但敏感字段保存失败。草稿已保留，请重试。"
                } else {
                    operationError = "凭据保存失败，草稿已保留，请重试。"
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (sourceLoading) {
            item { Text("正在读取凭据…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (sourceLoadError) {
            item { Text("凭据已不存在或暂时无法读取。", color = MaterialTheme.colorScheme.error) }
        } else {
        if (existing == null) {
            item {
                Text("凭据类型", style = MaterialTheme.typography.titleMedium, modifier = pageScope.elementModifier(0))
                CredentialType.entries.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { chooseType(candidate) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(candidate.label, style = MaterialTheme.typography.bodyLarge)
                            Text(credentialTypeDescription(candidate), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = type == candidate, onClick = { chooseType(candidate) })
                    }
                }
            }
        }
        if (type != null) {
            item {
                CredentialSourceGroup(pageScope.elementModifier(1)) {
                    Text("基本信息", style = MaterialTheme.typography.titleMedium)
                    Text(type!!.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = label, onValueChange = { label = it; errors = errors - "label" }, label = { Text("凭证名称") }, isError = "label" in errors, supportingText = { errors["label"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
                    if (type == CredentialType.GenericAccount) {
                        OutlinedTextField(value = realm, onValueChange = { if (existing == null) { realm = it; errors = errors - "realm" } }, label = { Text("服务标识（realm）") }, enabled = existing == null, isError = "realm" in errors, supportingText = { Text(if ("realm" in errors) "请输入有效的服务域名，例如 music.example.com" else if (existing == null) "用于限定凭据所属服务，例如 music.example.com" else "创建后不可修改") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
                    }
                    Text("账户与服务", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
                    CredentialSourcePublicFields(type!!, publicFields, errors) { field, value ->
                        publicFields = publicFields.toMutableMap().apply { if (value.isBlank()) remove(field) else put(field, value) }
                        errors = errors - credentialSourcePublicFieldErrorKey(field)
                    }
                }
            }
            item {
                CredentialSourceGroup(pageScope.elementModifier(1)) {
                    Text("敏感信息", style = MaterialTheme.typography.titleMedium)
                    Text("可稍后补齐。查看已保存内容需要通过系统身份认证。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            val fieldAuthenticationPending = accessGate.activeRequest?.fieldId == field
                            CredentialSavedSecretRow(
                                label = definition.label,
                                state = storedState,
                                status = if (fieldAuthenticationPending) "等待系统身份认证…" else status,
                                result = if (revealedSecret.field == field) revealedSecret.result else null,
                                revealing = revealedSecret.field == field,
                                authenticationError = if (authenticationErrorField == field) authenticationError else null,
                                onReveal = {
                                    if (revealedSecret.field == field) hideSavedSecret()
                                    else requestSavedSecret(field)
                                },
                                enabled = !accessGate.isBusy && !saving && !deletingSource,
                                isPassword = isPassword
                            )
                            if (isPassword) {
                                OutlinedSecureTextField(
                                    state = passwordInput,
                                    label = { Text("输入新密码") },
                                    textObfuscationMode = credentialPasswordObfuscationMode(passwordInputVisible),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordInputVisible = !passwordInputVisible }) {
                                            Icon(
                                                if (passwordInputVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (passwordInputVisible) "隐藏新密码" else "显示新密码"
                                            )
                                        }
                                    },
                                    isError = "secrets.${field.value}" in errors,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        .onFocusChanged { focus ->
                                            if (focus.isFocused && accessGate.isBusy) cancelAuthentication()
                                        }
                                )
                                errors["secrets.${field.value}"]?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                OutlinedTextField(
                                    value = cookieDraft.input,
                                    onValueChange = { value ->
                                        cookieDraft = cookieDraft.edit(value)
                                        if (value.isNotEmpty() && revealedSecret.field == field) hideSavedSecret()
                                        errors = errors - "secrets.${field.value}"
                                        operationError = null
                                    },
                                    label = { Text("输入新 Cookie") },
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
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(max = 220.dp)
                                        .onFocusChanged { focus ->
                                            if (focus.isFocused && accessGate.isBusy) cancelAuthentication()
                                        },
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
                                    cancelAuthentication()
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
                if (existing != null) {
                    TextButton(
                        onClick = {
                            if (saving || deletingSource) return@TextButton
                            onDiscardChangesConfirmationChange(
                                ExtensionDiscardChangesConfirmation(
                                    title = "删除凭据？",
                                    message = "将删除凭据信息、已保存的敏感字段及相关授权。",
                                    keepLabel = "取消",
                                    discardLabel = "删除",
                                    onKeepEditing = { onDiscardChangesConfirmationChange(null) },
                                    onDiscard = {
                                        val id = existing!!.id
                                        onDiscardChangesConfirmationChange(null)
                                        deletingSource = true
                                        scope.launch {
                                            val result = runCatching { withContext(Dispatchers.IO) { repository.delete(id) } }
                                            deletingSource = false
                                            result.onSuccess { deleted ->
                                                if (deleted) leavePage() else operationError = "凭据不存在，未完成删除。"
                                            }.onFailure { operationError = "无法完整删除凭据及敏感信息，请重试。" }
                                        }
                                    }
                                )
                            )
                        },
                        enabled = !saving && !deletingSource,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (deletingSource) "正在删除…" else "删除凭据", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        }
    }
    if (!sourceLoading && !sourceLoadError && type != null) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)) {
                operationError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                Button(
                    onClick = ::saveSource,
                    enabled = dirty && !saving && !deletingSource,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(if (saving) "正在保存…" else "保存凭据") }
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
            supportingText = {
                when {
                    error != null -> Text(error)
                    type == CredentialType.WebDav && field == CredentialFieldId.Username -> Text("授权 WebDAV 扩展时需要")
                    type == CredentialType.AccountPassword -> Text("至少填写一种扩展请求接受的账户标识")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true
        )
    }
}

@Composable
private fun CredentialSavedSecretRow(
    label: String,
    state: CredentialSecretState,
    status: String,
    result: CredentialSecretReadResult?,
    revealing: Boolean,
    authenticationError: String?,
    onReveal: () -> Unit,
    enabled: Boolean,
    isPassword: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state == CredentialSecretState.Configured) {
                IconButton(onClick = onReveal, enabled = enabled) {
                    Icon(
                        if (revealing) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealing) "隐藏已保存的$label" else "认证后查看已保存的$label"
                    )
                }
            }
        }
        if (revealing) when (result) {
            is CredentialSecretReadResult.Available -> OutlinedTextField(
                value = result.plaintext,
                onValueChange = {},
                readOnly = true,
                label = { Text("已保存的$label（只读）") },
                singleLine = isPassword,
                minLines = if (isPassword) 1 else 3,
                maxLines = if (isPassword) 1 else 6,
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
            )
            is CredentialSecretReadResult.Unavailable -> Text("已保存的${label}不可用，请输入新值重新设置。", color = MaterialTheme.colorScheme.error)
            CredentialSecretReadResult.NotConfigured -> Text("已保存的${label}已不存在，请输入新值重新设置。", color = MaterialTheme.colorScheme.error)
            null -> Text("正在读取已保存的$label…", style = MaterialTheme.typography.bodySmall)
        }
        authenticationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun credentialSourceStatus(source: CredentialSource): String {
    val requiredPublic = when (source.credentialType) {
        CredentialType.WebDav -> listOf(CredentialFieldId.Endpoint, CredentialFieldId.Username)
        CredentialType.AccountPassword -> emptyList()
        CredentialType.GenericAccount -> emptyList()
    }
    val missingPublic = requiredPublic.any { source.publicFields[it].isNullOrBlank() } ||
        (source.credentialType == CredentialType.AccountPassword && listOf(
            CredentialFieldId.Username, CredentialFieldId.UserId, CredentialFieldId.Email, CredentialFieldId.Phone
        ).all { source.publicFields[it].isNullOrBlank() })
    val states = CredentialSourceContracts.secretFields(source.credentialType).map {
        source.secretFieldStates[it] ?: CredentialSecretState.NotConfigured
    }
    return when {
        missingPublic -> "需要补齐公开信息"
        CredentialSecretState.Unavailable in states -> "敏感信息不可用，需重新设置"
        states.all { it == CredentialSecretState.NotConfigured } -> "敏感信息尚未设置"
        CredentialSecretState.NotConfigured in states -> "已保存部分敏感信息"
        else -> "敏感信息已保存"
    }
}

internal fun credentialTypeDescription(type: CredentialType): String = when (type) {
    CredentialType.WebDav -> "用于 WebDAV 服务，填写服务器地址与用户名。"
    CredentialType.AccountPassword -> "用于以用户名、邮箱、手机号或用户 ID 识别的账户。"
    CredentialType.GenericAccount -> "用于指定服务的账户，可按扩展请求保存密码或 Cookie。"
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
    Column(modifier = modifier.fillMaxWidth(), content = { content() })
}

internal fun credentialSourceSummary(source: CredentialSource): String = when (source.credentialType) {
    CredentialType.WebDav -> listOfNotNull(source.publicFields[CredentialFieldId.Endpoint], source.publicFields[CredentialFieldId.Username]).joinToString(" · ").ifBlank { "未填写服务器和用户名" }
    CredentialType.AccountPassword -> listOfNotNull(source.publicFields[CredentialFieldId.Username], source.publicFields[CredentialFieldId.Email], source.publicFields[CredentialFieldId.Phone], source.publicFields[CredentialFieldId.UserId]).firstOrNull() ?: "未填写身份标识"
    CredentialType.GenericAccount -> listOfNotNull(source.realm, source.publicFields[CredentialFieldId.Account]).joinToString(" · ")
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
