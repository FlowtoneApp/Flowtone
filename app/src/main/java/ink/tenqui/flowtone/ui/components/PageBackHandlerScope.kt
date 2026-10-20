package ink.tenqui.flowtone.ui.components

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 保留旧页的 Composition 不等于保留其系统返回所有权。
 * 每个 slot 的回调独立注册，只有最新目标页能向上声明它有可处理的返回事件。
 */
@SuppressLint("VisibleForTests")
internal class PageBackDispatcher(parent: OnBackPressedDispatcher) {
    private var active = false

    val parentCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            dispatcher.dispatchOnBackStarted(backEvent)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            dispatcher.dispatchOnBackProgressed(backEvent)
        }

        override fun handleOnBackCancelled() {
            dispatcher.dispatchOnBackCancelled()
        }

        override fun handleOnBackPressed() {
            dispatcher.onBackPressed()
        }
    }

    val dispatcher = OnBackPressedDispatcher(
        Runnable { parent.onBackPressed() },
        Consumer { updateEnabled() }
    )

    fun setActive(value: Boolean) {
        active = value
        updateEnabled()
    }

    private fun updateEnabled() {
        val enabled = active && dispatcher.hasEnabledCallbacks()
        if (parentCallback.isEnabled != enabled) parentCallback.isEnabled = enabled
    }
}

@Composable
internal fun PageBackHandlerScope(
    active: Boolean,
    content: @Composable () -> Unit
) {
    val parentOwner = LocalOnBackPressedDispatcherOwner.current
    if (parentOwner == null) {
        content()
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val parentDispatcher = parentOwner.onBackPressedDispatcher
    val scope = remember(parentDispatcher) { PageBackDispatcher(parentDispatcher) }
    val owner = remember(scope, lifecycleOwner) {
        object : OnBackPressedDispatcherOwner {
            override val lifecycle = lifecycleOwner.lifecycle
            override val onBackPressedDispatcher = scope.dispatcher
        }
    }
    DisposableEffect(scope, parentDispatcher, lifecycleOwner) {
        parentDispatcher.addCallback(lifecycleOwner, scope.parentCallback)
        onDispose { scope.parentCallback.remove() }
    }
    SideEffect { scope.setActive(active) }
    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner, content = content)
}
