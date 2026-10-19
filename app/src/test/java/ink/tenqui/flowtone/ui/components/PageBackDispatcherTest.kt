package ink.tenqui.flowtone.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBackDispatcherTest {
    @Test fun retainedOutgoingHandlerCannotConsumeBackForAnIncomingPageWithoutAHandler() {
        val events = mutableListOf<String>()
        val parent = OnBackPressedDispatcher()
        parent.addCallback(callback { events += "navigate-back" })
        val outgoing = scope(parent)
        outgoing.dispatcher.addCallback(callback { events += "old-page" })
        outgoing.setActive(false)
        scope(parent).setActive(true)

        parent.onBackPressed()

        assertEquals(listOf("navigate-back"), events)
        assertTrue(outgoing.dispatcher.hasEnabledCallbacks())
        assertFalse(outgoing.parentCallback.isEnabled)
    }

    @Test fun latestTargetWinsThroughRapidReversalRegardlessOfSlotRegistrationOrder() {
        val events = mutableListOf<String>()
        val parent = OnBackPressedDispatcher()
        val first = scope(parent)
        val second = scope(parent)
        first.dispatcher.addCallback(callback { events += "A" })
        second.dispatcher.addCallback(callback { events += "B" })

        listOf("B", "A", "B", "A").forEach { target ->
            first.setActive(target == "A")
            second.setActive(target == "B")
            parent.onBackPressed()
        }

        assertEquals(listOf("B", "A", "B", "A"), events)
    }

    @Test fun localEnableChangesAndRemovalImmediatelyRestoreTheParentFallback() {
        val events = mutableListOf<String>()
        val parent = OnBackPressedDispatcher(Runnable { events += "fallback" })
        val page = scope(parent)
        page.setActive(true)
        val local = callback { events += "page" }.apply { isEnabled = false }
        page.dispatcher.addCallback(local)

        parent.onBackPressed()
        local.isEnabled = true
        assertTrue(page.parentCallback.isEnabled)
        parent.onBackPressed()
        local.isEnabled = false
        assertFalse(page.parentCallback.isEnabled)
        parent.onBackPressed()
        local.isEnabled = true
        local.remove()
        parent.onBackPressed()

        assertEquals(listOf("fallback", "page", "fallback", "fallback"), events)
    }

    @Test fun nestedHostsOnlyDispatchAlongTheActiveTargetLineage() {
        val events = mutableListOf<String>()
        val root = OnBackPressedDispatcher(Runnable { events += "root" })
        val page = scope(root)
        val nested = scope(page.dispatcher)
        nested.dispatcher.addCallback(callback { events += "nested" })
        nested.setActive(true)
        page.setActive(false)

        root.onBackPressed()
        page.setActive(true)
        root.onBackPressed()
        nested.setActive(false)
        root.onBackPressed()

        assertEquals(listOf("root", "nested", "root"), events)
    }

    @Test fun disposingAnOldSlotDoesNotClearTheActiveSlotsBackOwnership() {
        val events = mutableListOf<String>()
        val parent = OnBackPressedDispatcher()
        val old = scope(parent)
        old.dispatcher.addCallback(callback { events += "old" })
        old.setActive(false)
        val current = scope(parent)
        current.dispatcher.addCallback(callback { events += "current" })
        current.setActive(true)

        old.parentCallback.remove()
        parent.onBackPressed()

        assertEquals(listOf("current"), events)
        assertTrue(current.parentCallback.isEnabled)
    }

    @Test fun modalCallbacksKeepPriorityWithinThePageAndAboveThePage() {
        val events = mutableListOf<String>()
        val parent = OnBackPressedDispatcher()
        val page = scope(parent)
        page.dispatcher.addCallback(callback { events += "page" })
        page.setActive(true)
        val localModal = callback { events += "page-modal" }
        page.dispatcher.addCallback(localModal)
        val windowModal = callback { events += "window-modal" }
        parent.addCallback(windowModal)

        parent.onBackPressed()
        windowModal.remove()
        parent.onBackPressed()
        localModal.remove()
        parent.onBackPressed()

        assertEquals(listOf("window-modal", "page-modal", "page"), events)
    }

    @Test fun systemGestureStartProgressCancelAndCommitReachTheLocalDispatcher() {
        val events = mutableListOf<String>()
        val parent = OnBackPressedDispatcher()
        val page = scope(parent)
        page.dispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) { events += "start" }
            override fun handleOnBackProgressed(backEvent: BackEventCompat) { events += "progress" }
            override fun handleOnBackCancelled() { events += "cancel" }
            override fun handleOnBackPressed() { events += "back" }
        })
        page.setActive(true)
        val event = BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT)

        parent.dispatchOnBackStarted(event)
        parent.dispatchOnBackProgressed(event)
        parent.dispatchOnBackCancelled()
        parent.onBackPressed()

        assertEquals(listOf("start", "progress", "cancel", "back"), events)
    }

    private fun scope(parent: OnBackPressedDispatcher) = PageBackDispatcher(parent).also {
        parent.addCallback(it.parentCallback)
    }

    private fun callback(onBack: () -> Unit) = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBack()
    }
}
