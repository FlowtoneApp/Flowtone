package ink.tenqui.flowtone.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Secondary destination for managing online extensions. */
@Composable
internal fun OnlineExtensionsScreen(
    pageScope: PageTransitionScope,
    onOpenExtensionInstall: (ExtensionInstallPreview) -> Unit,
    onOpenExtensionSettings: (InstalledExtension) -> Unit,
    onOpenCredentialSources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val scope = rememberCoroutineScope()
    var installedExtensions by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    fun refreshExtensions() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { extensionManager.installedExtensions() } }
                .onSuccess { current ->
                    if (current != installedExtensions) installedExtensions = current
                    loadError = null
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    loadError = "读取扩展列表失败，请重试。"
                }
            loading = false
            refreshing = false
        }
    }
    val extensionPackageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { extensionManager.inspect(uri) }
                .onSuccess(onOpenExtensionInstall)
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "扩展包检查失败",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    LaunchedEffect(Unit) { refreshExtensions() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshExtensions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnlineSettingsPage(
        installedExtensions = installedExtensions,
        loading = loading,
        loadError = loadError,
        onRetry = ::refreshExtensions,
        onInstall = { extensionPackageLauncher.launch(FlowtoneExtensionMimeTypes) },
        onOpenCredentialSources = onOpenCredentialSources,
        onOpenExtensionSettings = onOpenExtensionSettings,
        elementModifier = { index ->
            pageScope.elementModifier(index, maxOf(installedExtensions.size, 1) + 2)
        },
        modifier = modifier
    )
}

/** Keeps the detail page on the existing ExtensionManager uninstall path. */
internal suspend fun uninstallInstalledExtension(
    extensionManager: ExtensionManager,
    extensionId: String
): Result<Unit> = runCatching {
    check(extensionManager.uninstall(extensionId)) { "扩展删除失败" }
}
