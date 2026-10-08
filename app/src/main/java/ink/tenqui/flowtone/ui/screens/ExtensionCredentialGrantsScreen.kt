package ink.tenqui.flowtone.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialFieldDefinitions
import ink.tenqui.flowtone.data.online.credential.CredentialGrantAuthorizationBinding
import ink.tenqui.flowtone.data.online.credential.CredentialGrantAuthorizationGate
import ink.tenqui.flowtone.data.online.credential.CredentialGrantAuthorizationToken
import ink.tenqui.flowtone.data.online.credential.CredentialGrantEvaluation
import ink.tenqui.flowtone.data.online.credential.CredentialGrantInvalidReason
import ink.tenqui.flowtone.data.online.credential.CredentialGrantRepository
import ink.tenqui.flowtone.data.online.credential.CredentialRequestContractSnapshot
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialSource
import ink.tenqui.flowtone.data.online.credential.CredentialSourceMatch
import ink.tenqui.flowtone.data.online.credential.CredentialSourceMatcher
import ink.tenqui.flowtone.data.online.credential.CredentialSourceRepository
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneModalPanel
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CredentialGrantRow(
    val request: CredentialRequestDefinition,
    val evaluation: CredentialGrantEvaluation
)

@Composable
internal fun ExtensionCredentialGrantsScreen(
    installed: InstalledExtension,
    pageScope: PageTransitionScope,
    onChooseSource: (InstalledExtension, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val repository = remember(context) { CredentialGrantRepository.from(context) }
    var currentInstalled by remember(installed.manifest.id) { mutableStateOf<InstalledExtension?>(null) }
    var rows by remember(installed.manifest.id) { mutableStateOf<List<CredentialGrantRow>>(emptyList()) }
    var loading by remember(installed.manifest.id) { mutableStateOf(true) }
    var error by remember(installed.manifest.id) { mutableStateOf<String?>(null) }
    var revokeRequest by remember(installed.manifest.id) { mutableStateOf<CredentialRequestDefinition?>(null) }
    var revoking by remember(installed.manifest.id) { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val current = extensionManager.installedExtensions()
                        .firstOrNull { it.manifest.id == installed.manifest.id }
                    val results = current?.descriptor?.credentialRequests.orEmpty().map { request ->
                        CredentialGrantRow(request, repository.evaluate(current, request.id))
                    }
                    current to results
                }
            }.onSuccess { (current, results) ->
                currentInstalled = current
                rows = results
            }.onFailure {
                error = "凭据授权状态暂不可用，请稍后重试。"
            }
            loading = false
        }
    }
    LaunchedEffect(installed.manifest.id) { refresh() }
    val currentRefresh by androidx.compose.runtime.rememberUpdatedState(::refresh)
    DisposableEffect(currentRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "为扩展 ${installed.manifest.name} 管理凭据授权。每条请求单独授权，扩展更新后如果请求范围改变，需要重新确认。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = pageScope.elementModifier(0)
            )
        }
        if (loading) item { GrantInfoCard("正在读取授权状态…", Modifier) }
        error?.let { message ->
            item {
                Column {
                    GrantInfoCard(message, Modifier)
                    TextButton(onClick = ::refresh) { Text("重试") }
                }
            }
        }
        if (!loading && error == null && rows.isEmpty()) {
            item { GrantInfoCard("此扩展没有声明凭据请求。", pageScope.elementModifier(1)) }
        }
        items(rows, key = { it.request.id }) { row ->
            CredentialGrantRequestCard(
                row = row,
                modifier = pageScope.elementModifier(1),
                onChoose = {
                    currentInstalled?.let { onChooseSource(it, row.request.id) }
                },
                onRevoke = { revokeRequest = row.request }
            )
        }
    }

    revokeRequest?.let { request ->
        GrantConfirmationOverlay(
            title = "撤销凭据授权？",
            description = "扩展将不再拥有此请求对应的授权。",
            confirmLabel = "撤销授权",
            destructive = true,
            onDismiss = { if (!revoking) revokeRequest = null },
            onConfirm = {
                if (!revoking) {
                    scope.launch {
                        revoking = true
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                repository.revoke(installed.manifest.id, request.id)
                            }
                        }
                        revoking = false
                        revokeRequest = null
                        if (result.isSuccess) refresh() else error = "撤销授权失败，请重试。"
                    }
                }
            }
        )
    }
}

@Composable
private fun CredentialGrantRequestCard(
    row: CredentialGrantRow,
    modifier: Modifier,
    onChoose: () -> Unit,
    onRevoke: () -> Unit
) {
    val request = row.request
    val evaluation = row.evaluation
    val status = when {
        evaluation.grant == null -> "未授权"
        !evaluation.authorizationValid -> when (evaluation.invalidReason) {
            CredentialGrantInvalidReason.RequestContractChanged,
            CredentialGrantInvalidReason.RequestRemoved,
            CredentialGrantInvalidReason.ExtensionInstallationChanged -> "请求范围已变化，需重新授权"
            else -> "授权当前无效"
        }
        !evaluation.sourceReady -> "已授权，凭据当前未就绪"
        else -> "已授权"
    }
    GrantCard(modifier) {
        Text(request.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(request.credentialType.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        request.realm?.let {
            Text("服务：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "所需字段：${request.contract.fields.joinToString("、") { it.label }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(status, style = MaterialTheme.typography.labelLarge, color = if (evaluation.grant != null && evaluation.authorizationValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        evaluation.grant?.let {
            Text(
                "绑定凭证：${evaluation.sourceLabel ?: "来源不可用"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (evaluation.grant != null) {
                TextButton(onClick = onRevoke) { Text("撤销") }
            }
            TextButton(onClick = onChoose) {
                Text(if (evaluation.grant == null) "选择凭据" else "重新选择")
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class CredentialCandidate(
    val source: CredentialSource,
    val match: CredentialSourceMatch
)

@Composable
internal fun ExtensionCredentialSourcePickerScreen(
    installed: InstalledExtension,
    requestId: String,
    pageScope: PageTransitionScope,
    onOpenCredentialSources: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val sourceRepository = remember(context) { CredentialSourceRepository.from(context) }
    val grantRepository = remember(context) { CredentialGrantRepository.from(context) }
    val gate = remember(installed.manifest.id, requestId) { CredentialGrantAuthorizationGate() }
    var liveInstalled by remember(installed.manifest.id) { mutableStateOf<InstalledExtension?>(null) }
    var request by remember(installed.manifest.id, requestId) { mutableStateOf<CredentialRequestDefinition?>(null) }
    var candidates by remember(installed.manifest.id, requestId) { mutableStateOf<List<CredentialCandidate>>(emptyList()) }
    var loading by remember(installed.manifest.id, requestId) { mutableStateOf(true) }
    var loadError by remember(installed.manifest.id, requestId) { mutableStateOf<String?>(null) }
    var selectedSource by remember(installed.manifest.id, requestId) { mutableStateOf<CredentialSource?>(null) }
    var authToken by remember(installed.manifest.id, requestId) { mutableStateOf<CredentialGrantAuthorizationToken?>(null) }
    var keyguardToken by remember(installed.manifest.id, requestId) { mutableStateOf<CredentialGrantAuthorizationToken?>(null) }
    var authenticating by remember(installed.manifest.id, requestId) { mutableStateOf(false) }
    var authError by remember(installed.manifest.id, requestId) { mutableStateOf<String?>(null) }
    val hostActivity = remember(context) { context.findGrantFragmentActivity() }
    val authenticator = remember(hostActivity) { hostActivity?.let(::CredentialSecretAuthenticator) }

    fun refresh() {
        scope.launch {
            loading = true
            loadError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val current = extensionManager.installedExtensions()
                        .firstOrNull { it.manifest.id == installed.manifest.id }
                    val currentRequest = current?.descriptor?.credentialRequests?.firstOrNull { it.id == requestId }
                    val sources = sourceRepository.list()
                    Triple(current, currentRequest, sources)
                }
            }.onSuccess { (current, currentRequest, sources) ->
                liveInstalled = current
                request = currentRequest
                candidates = if (currentRequest == null) emptyList() else sources.map {
                    CredentialCandidate(it, CredentialSourceMatcher.match(it, currentRequest))
                }
            }.onFailure { loadError = "凭据来源暂不可用，请重试。" }
            loading = false
        }
    }
    LaunchedEffect(installed.manifest.id, requestId) { refresh() }
    val currentRefresh by androidx.compose.runtime.rememberUpdatedState(::refresh)
    DisposableEffect(lifecycleOwner, gate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gate.close()
            authenticator?.cancel()
            authToken = null
            keyguardToken = null
        }
    }

    fun cancelAuthentication() {
        gate.cancel(authToken)
        authToken = null
        keyguardToken = null
        authenticating = false
        authenticator?.cancel()
    }

    fun backAndCancel() {
        cancelAuthentication()
        selectedSource = null
        onBack()
    }

    LaunchedEffect(pageScope.phase) {
        if (pageScope.phase == ink.tenqui.flowtone.ui.components.PageTransitionPhase.Outgoing) {
            cancelAuthentication()
            selectedSource = null
        }
    }

    fun finishAuthentication(token: CredentialGrantAuthorizationToken) {
        if (!gate.isCurrent(token) || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        authenticating = false
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    gate.commitIfCurrent(token) {
                        grantRepository.createOrReplace(
                            installed = installed,
                            requestId = token.binding.requestId,
                            sourceId = token.binding.sourceId,
                            confirmedContract = token.binding.requestContract,
                            confirmedInstallInstanceId = token.binding.extensionInstanceId,
                            currentInstalled = {
                                extensionManager.installedExtensions()
                                    .firstOrNull { it.manifest.id == token.binding.extensionId }
                            }
                        )
                    } ?: throw IllegalStateException("Authorization request expired")
                }
            }
            authToken = null
            selectedSource = null
            if (result.isSuccess) {
                onBack()
            } else {
                authError = "授权未完成。请检查扩展请求和凭据状态后重试。"
            }
        }
    }

    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val token = keyguardToken
        keyguardToken = null
        token?.let(gate::finishKeyguardConfirmation)
        if (token != null && result.resultCode == Activity.RESULT_OK) {
            finishAuthentication(token)
        } else if (token != null) {
            gate.cancel(token)
            authToken = null
            authenticating = false
            authError = "系统身份认证已取消。"
        }
    }

    fun startKeyguardConfirmation(token: CredentialGrantAuthorizationToken) {
        val activity = hostActivity
        val keyguard = activity?.getSystemService(KeyguardManager::class.java)
        val intent = if (keyguard?.isDeviceSecure == true) {
            keyguard.createConfirmDeviceCredentialIntent("验证身份", "确认向此扩展授权使用所选凭据")
        } else null
        if (activity == null || intent == null) {
            gate.cancel(token)
            authToken = null
            authenticating = false
            authError = "请先设置设备锁屏保护，再授权扩展使用凭据。"
            return
        }
        keyguardToken = token
        gate.markKeyguardConfirmation(token)
        runCatching { keyguardLauncher.launch(intent) }.onFailure {
            keyguardToken = null
            gate.cancel(token)
            authToken = null
            authenticating = false
            authError = "系统身份认证暂不可用，请重试。"
        }
    }

    fun requestAuthorization(source: CredentialSource) {
        val currentInstalled = liveInstalled ?: return
        val currentRequest = request ?: return
        val contract = CredentialRequestContractSnapshot.from(currentRequest) ?: run {
            authError = "扩展凭据请求无效，无法授权。"
            return
        }
        val instanceId = currentInstalled.installationInstanceId ?: run {
            authError = "扩展安装状态不可用，请重新打开此页面。"
            return
        }
        val binding = CredentialGrantAuthorizationBinding(
            extensionId = currentInstalled.manifest.id,
            extensionInstanceId = instanceId,
            requestId = currentRequest.id,
            sourceId = source.id,
            requestContract = contract
        )
        val token = gate.begin(binding) ?: return
        authToken = token
        authenticating = true
        authError = null
        val launch = authenticator?.authenticate(
            onAuthenticated = { finishAuthentication(token) },
            onUseDeviceCredential = { startKeyguardConfirmation(token) },
            onFailure = {
                gate.cancel(token)
                authToken = null
                authenticating = false
                authError = "系统身份认证未完成，未创建授权。"
            },
            promptSubtitle = "确认向此扩展授权使用所选凭据"
        ) ?: CredentialSecretAuthenticationLaunch.Unavailable
        when (launch) {
            CredentialSecretAuthenticationLaunch.PromptStarted -> Unit
            CredentialSecretAuthenticationLaunch.UseDeviceCredential -> startKeyguardConfirmation(token)
            CredentialSecretAuthenticationLaunch.MissingDeviceSecurity -> {
                gate.cancel(token)
                authToken = null
                authenticating = false
                authError = "请先设置设备锁屏保护，再授权扩展使用凭据。"
            }
            CredentialSecretAuthenticationLaunch.Unavailable -> {
                gate.cancel(token)
                authToken = null
                authenticating = false
                authError = "系统身份认证暂不可用，未创建授权。"
            }
        }
    }

    val currentAuthToken by androidx.compose.runtime.rememberUpdatedState(authToken)
    val currentCancel by androidx.compose.runtime.rememberUpdatedState(::cancelAuthentication)
    DisposableEffect(lifecycleOwner, gate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && currentAuthToken != null &&
                !gate.isKeyguardConfirmationPending()
            ) {
                currentCancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = authToken != null) { backAndCancel() }

    LazyColumn(
        modifier = modifier.fillMaxSize().rightSwipeBackGesture(::backAndCancel),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val currentRequest = request
        item {
            if (currentRequest == null) {
                GrantInfoCard("此请求已从扩展清单中移除，不能继续授权。", pageScope.elementModifier(0))
            } else {
                GrantCard(pageScope.elementModifier(0)) {
                    Text("${liveInstalled?.manifest?.name ?: installed.manifest.name} · ${currentRequest.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(currentRequest.credentialType.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    currentRequest.realm?.let { Text("服务：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                    Text("所需字段：${currentRequest.contract.fields.joinToString("、") { it.label }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Text("只有你明确确认并通过系统身份认证后，才会保存这条授权。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (loading) item { GrantInfoCard("正在检查凭据兼容性…", Modifier) }
        loadError?.let { message ->
            item {
                Column {
                    GrantInfoCard(message, Modifier)
                    TextButton(onClick = ::refresh) { Text("重试") }
                }
            }
        }
        if (!loading && loadError == null) {
            if (request != null && candidates.none { it.match.fullyReady }) {
                item {
                    GrantCard(pageScope.elementModifier(1)) {
                        Text("没有兼容且就绪的凭据。", style = MaterialTheme.typography.titleSmall)
                        Text("你可以前往凭据管理创建凭据或补齐缺少的字段与 Secret。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                        Button(
                            onClick = {
                                cancelAuthentication()
                                selectedSource = null
                                onOpenCredentialSources()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text("前往凭据管理") }
                    }
                }
            }
            items(candidates, key = { it.source.id }) { candidate ->
                CredentialCandidateCard(
                    candidate = candidate,
                    modifier = pageScope.elementModifier(1),
                    onClick = { if (candidate.match.fullyReady && !authenticating) selectedSource = candidate.source }
                )
            }
        }
        authError?.let { message -> item { GrantInfoCard(message, Modifier) } }
    }

    selectedSource?.let { source ->
        GrantConsentOverlay(
            installed = liveInstalled ?: installed,
            request = request,
            source = source,
            authenticating = authenticating,
            onDismiss = {
                if (!authenticating) {
                    selectedSource = null
                    authError = null
                }
            },
            onConfirm = { requestAuthorization(source) }
        )
    }
}

@Composable
private fun CredentialCandidateCard(
    candidate: CredentialCandidate,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val ready = candidate.match.fullyReady
    GrantCard(modifier.then(if (ready) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.source.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(candidate.source.credentialType.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                candidate.source.realm?.let { Text("服务：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }
                Text(
                    credentialSourceSummary(candidate.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(candidateReason(candidate.match), style = MaterialTheme.typography.bodySmall, color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
            }
            if (ready) androidx.compose.material3.Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun candidateReason(match: CredentialSourceMatch): String = when {
    match.fullyReady -> "兼容且就绪 · 可选择"
    !match.contractCompatible -> "不兼容：凭据类型、服务 realm 或请求字段不匹配"
    !match.metadataCompatible -> "兼容，但缺少请求所需的普通字段"
    else -> "兼容，但请求所需的 Secret 未配置或不可用"
}

@Composable
private fun GrantConsentOverlay(
    installed: InstalledExtension,
    request: CredentialRequestDefinition?,
    source: CredentialSource,
    authenticating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    FlowtoneModalOverlayShell(
        visible = true,
        scrimAlpha = .42f,
        panelProgress = 1f,
        panelScale = 1f,
        shadowSafePadding = 12.dp,
        onDismissRequest = onDismiss
    ) {
        FlowtoneModalPanel {
            Text("确认凭据授权", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("扩展：${installed.manifest.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            Text("请求：${request?.label ?: "请求不可用"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
            Text("凭证：${source.label}（${source.credentialType.label}）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
            Text("来源信息：${credentialSourceSummary(source)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            request?.realm?.let { Text("服务：$it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp)) }
            Text("字段：${request?.contract?.fields?.joinToString("、") { it.label }.orEmpty()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
            Text("通过系统身份认证后，Flowtone 会保存这条授权。此操作不会向扩展显示凭据明文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !authenticating) { Text("取消") }
                Button(onClick = onConfirm, enabled = !authenticating && request != null) { Text(if (authenticating) "等待系统认证…" else "确认并认证") }
            }
        }
    }
}

@Composable
private fun GrantConfirmationOverlay(
    title: String,
    description: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    FlowtoneModalOverlayShell(
        visible = true,
        scrimAlpha = .42f,
        panelProgress = 1f,
        panelScale = 1f,
        shadowSafePadding = 12.dp,
        onDismissRequest = onDismiss
    ) {
        FlowtoneModalPanel {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onConfirm) { Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun GrantInfoCard(message: String, modifier: Modifier) {
    GrantCard(modifier) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GrantCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
}

private tailrec fun Context.findGrantFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findGrantFragmentActivity()
    else -> null
}
