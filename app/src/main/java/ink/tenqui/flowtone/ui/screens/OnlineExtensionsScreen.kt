package ink.tenqui.flowtone.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import kotlinx.coroutines.launch

/** Secondary destination for managing online extensions. */
@Composable
internal fun OnlineExtensionsScreen(
    pageScope: PageTransitionScope,
    onOpenExtensionInstall: (ExtensionInstallPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val scope = rememberCoroutineScope()
    var installedExtensions by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    fun refreshExtensions() {
        installedExtensions = extensionManager.installedExtensions()
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

    OnlineSettingsPage(
        installedExtensions = installedExtensions,
        onInstall = { extensionPackageLauncher.launch(FlowtoneExtensionMimeTypes) },
        onUninstall = { id ->
            scope.launch {
                val result = runCatching {
                    check(extensionManager.uninstall(id)) { "扩展删除失败" }
                }
                refreshExtensions()
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { "扩展已删除" },
                        onFailure = { it.message ?: "扩展删除失败" }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        elementModifier = { index ->
            pageScope.elementModifier(index, installedExtensions.size + 2)
        },
        modifier = modifier
    )
}
