package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PageElementEnterScopeTest {
    @Test
    fun onlyUnseenKeysJoinTheNextEntryBatchInVisualOrder() {
        assertEquals(
            listOf("online:artist:1", "online:artist:2"),
            pageElementKeysAwaitingEntry(
                elementKeys = listOf(
                    "local:song:1",
                    "local:artist:1",
                    "online:artist:1",
                    "online:artist:2"
                ),
                enteredKeys = setOf("local:song:1", "local:artist:1"),
                activeKeys = emptySet()
            )
        )
    }

    @Test
    fun activeOrPreviouslyEnteredKeysDoNotReplay() {
        assertEquals(
            listOf("online:artist:2"),
            pageElementKeysAwaitingEntry(
                elementKeys = listOf("local:song:1", "online:artist:1", "online:artist:2"),
                enteredKeys = setOf("local:song:1"),
                activeKeys = setOf("online:artist:1")
            )
        )
    }

    @Test
    fun viewportKeysDetermineTheStaggerOrderWithoutDelayingLongLists() {
        assertEquals(
            listOf("local:artist:2", "local:artist:3"),
            pageElementStaggerKeys(
                newKeys = listOf(
                    "local:artist:1",
                    "local:artist:2",
                    "local:artist:3",
                    "local:artist:4"
                ),
                viewportKeys = listOf("local:artist:2", "local:artist:3")
            )
        )
    }

    @Test
    fun diffRetainsSharedKeysAndSeparatesEnterAndExitKeys() {
        val diff = pageElementKeyDiff(
            previousKeys = listOf("local:artist:Kou", "online:provider-a:Kou"),
            currentKeys = listOf("online:provider-a:Kou", "online:provider-b:Kou")
        )

        assertEquals(setOf("online:provider-a:Kou"), diff.retainedKeys)
        assertEquals(listOf("online:provider-b:Kou"), diff.enteringKeys)
        assertEquals(listOf("local:artist:Kou"), diff.exitingKeys)
    }

    @Test
    fun providerArrivalOnlyEntersTheNewProviderKey() {
        val diff = pageElementKeyDiff(
            previousKeys = listOf("local:song:1"),
            currentKeys = listOf("local:song:1", "online:provider-a:artist:1")
        )

        assertEquals(emptyList<Any>(), diff.exitingKeys)
        assertEquals(listOf("online:provider-a:artist:1"), diff.enteringKeys)
    }

    @Test
    fun removedProviderKeyOnlyExitsWhileLocalKeyStaysRetained() {
        val diff = pageElementKeyDiff(
            previousKeys = listOf("local:song:1", "online:provider-a:artist:1"),
            currentKeys = listOf("local:song:1")
        )

        assertEquals(setOf("local:song:1"), diff.retainedKeys)
        assertEquals(emptyList<Any>(), diff.enteringKeys)
        assertEquals(listOf("online:provider-a:artist:1"), diff.exitingKeys)
    }
}
