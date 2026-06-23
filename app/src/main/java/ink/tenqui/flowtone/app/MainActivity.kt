package ink.tenqui.flowtone.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ink.tenqui.flowtone.ui.theme.FlowtoneTheme

class MainActivity : ComponentActivity() {
    private var expandMiniPlayerRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenPlayerIntent(intent)
        setContent {
            FlowtoneTheme {
                FlowtoneApp(
                    openExpandedPlayerRequest = expandMiniPlayerRequest,
                    onOpenExpandedPlayerRequestConsumed = {
                        expandMiniPlayerRequest = 0
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    private fun handleOpenPlayerIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_EXPANDED_PLAYER) {
            return
        }

        if (intent.getBooleanExtra(EXTRA_EXPAND_MINI_PLAYER, false)) {
            expandMiniPlayerRequest += 1
        }

        intent.action = null
        intent.removeExtra(EXTRA_EXPAND_MINI_PLAYER)
    }

    companion object {
        const val ACTION_OPEN_EXPANDED_PLAYER = "ink.tenqui.flowtone.action.OPEN_EXPANDED_PLAYER"
        const val EXTRA_EXPAND_MINI_PLAYER = "ink.tenqui.flowtone.extra.EXPAND_MINI_PLAYER"
    }
}
