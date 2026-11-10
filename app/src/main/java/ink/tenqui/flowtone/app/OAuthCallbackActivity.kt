package ink.tenqui.flowtone.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ink.tenqui.flowtone.data.online.oauth.OAuthBrowserHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The Host coordinator validates the entire URI and its one-shot state. */
class OAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            intent?.dataString?.let { uri ->
                CoroutineScope(Dispatchers.IO).launch { OAuthBrowserHost.accept(applicationContext, uri) }
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        finish()
    }
}
