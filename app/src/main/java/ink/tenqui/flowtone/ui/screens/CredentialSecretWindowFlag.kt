package ink.tenqui.flowtone.ui.screens

import android.view.Window
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Adds screenshot protection only while this page displays plaintext Secret content. */
internal class CredentialSecretWindowFlag {
    private var window: Window? = null
    private var addedByThisPage = false
    private var foregroundObserver: LifecycleEventObserver? = null

    fun show(window: Window) {
        if (this.window === window && addedByThisPage) return
        hide()
        this.window = window
        val wasSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (!wasSecure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            addedByThisPage = true
        }
    }

    fun hide() {
        if (foregroundObserver != null) return
        val ownedWindow = window
        if (ownedWindow != null && addedByThisPage) {
            ownedWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        window = null
        addedByThisPage = false
    }

    fun retainUntilForeground(lifecycle: Lifecycle) {
        if (!addedByThisPage || foregroundObserver != null) return
        val observer = LifecycleEventObserver { owner, event ->
            if (event == Lifecycle.Event.ON_START) {
                owner.lifecycle.removeObserver(foregroundObserver ?: return@LifecycleEventObserver)
                foregroundObserver = null
                hide()
            }
        }
        foregroundObserver = observer
        lifecycle.addObserver(observer)
    }
}
