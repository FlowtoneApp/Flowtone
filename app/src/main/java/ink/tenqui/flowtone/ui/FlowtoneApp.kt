package ink.tenqui.flowtone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.permissions.currentAudioPermission
import ink.tenqui.flowtone.permissions.hasAudioPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowtoneApp() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(hasAudioPermission(context))
    }
    var permissionDenied by remember {
        mutableStateOf(false)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Flowtone")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when {
                    hasPermission -> "已获得音频权限"
                    permissionDenied -> "权限被拒绝"
                    else -> "需要音频权限才能扫描本地音乐"
                },
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasPermission) {
                    "下一步将扫描本地音乐"
                } else {
                    "下一步将请求音频权限"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!hasPermission) {
                Button(
                    modifier = Modifier.padding(top = 24.dp),
                    onClick = {
                        permissionLauncher.launch(currentAudioPermission())
                    }
                ) {
                    Text(text = "授予权限")
                }
            }
        }
    }
}
