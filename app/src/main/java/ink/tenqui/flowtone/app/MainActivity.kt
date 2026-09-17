package ink.tenqui.flowtone.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ink.tenqui.flowtone.ui.theme.FlowtoneTheme
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.ui.debug.WindowJankSampler
import ink.tenqui.flowtone.data.online.ExtensionManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var expandMiniPlayerRequest by mutableStateOf(0)
    private val appPreferences by lazy {
        AppPreferences(applicationContext)
    }
    private var themeMode by mutableStateOf(AppThemeMode.FollowSystem)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { ExtensionManager.get(applicationContext).initialize() }
        themeMode = appPreferences.getThemeMode()
        enableEdgeToEdge()
        handleOpenPlayerIntent(intent)
        setContent {
            FlowtoneTheme(themeMode = themeMode) {
                FlowtoneApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        appPreferences.setThemeMode(mode)
                    },
                    openExpandedPlayerRequest = expandMiniPlayerRequest,
                    onOpenExpandedPlayerRequestConsumed = {
                        expandMiniPlayerRequest = 0
                    }
                )
            }
        }
        WindowJankSampler.install(this)
    }

    override fun onResume() {
        super.onResume()
        Log.d(SEARCH_FOCUS_DEBUG_TAG, "ACTIVITY ON_RESUME")
        WindowJankSampler.setTrackingEnabled(true)
    }

    override fun onStart() {
        super.onStart()
        Log.d(SEARCH_FOCUS_DEBUG_TAG, "ACTIVITY ON_START")
    }

    override fun onPause() {
        Log.d(SEARCH_FOCUS_DEBUG_TAG, "ACTIVITY ON_PAUSE")
        WindowJankSampler.setTrackingEnabled(false)
        super.onPause()
    }

    override fun onStop() {
        Log.d(SEARCH_FOCUS_DEBUG_TAG, "ACTIVITY ON_STOP")
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(SEARCH_FOCUS_DEBUG_TAG, "WINDOW_FOCUS hasFocus=$hasFocus")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(SEARCH_FOCUS_DEBUG_TAG, "ACTIVITY ON_NEW_INTENT action=${intent.action}")
        setIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    private fun handleOpenPlayerIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_EXPANDED_PLAYER) {
            return
        }

        if (intent.getBooleanExtra(EXTRA_EXPAND_MINI_PLAYER, false)) {
            Log.d(SEARCH_FOCUS_DEBUG_TAG, "OPEN_PLAYER_REQUEST source=media_session")
            expandMiniPlayerRequest += 1
        }

        intent.action = null
        intent.removeExtra(EXTRA_EXPAND_MINI_PLAYER)
    }

    companion object {
        const val SEARCH_FOCUS_DEBUG_TAG = "SearchFocusDebug"
        const val ACTION_OPEN_EXPANDED_PLAYER = "ink.tenqui.flowtone.action.OPEN_EXPANDED_PLAYER"
        const val EXTRA_EXPAND_MINI_PLAYER = "ink.tenqui.flowtone.extra.EXPAND_MINI_PLAYER"
    }
}
