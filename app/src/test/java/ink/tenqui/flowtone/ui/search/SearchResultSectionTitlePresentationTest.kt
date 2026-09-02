package ink.tenqui.flowtone.ui.search

import ink.tenqui.flowtone.ui.components.pageElementKeyDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultSectionTitlePresentationTest {
    @Test
    fun localSectionFirstAppearanceMakesItsTitleEnter() {
        val localTitle = titleKey("session-a", SearchResultSourceSection.Local)

        val diff = pageElementKeyDiff(emptyList(), listOf(localTitle))

        assertEquals(listOf(localTitle), diff.enteringKeys)
    }

    @Test
    fun onlineArrivalEntersOnlyOnlineTitleAndRetainsLocalTitle() {
        val localTitle = titleKey("session-a", SearchResultSourceSection.Local)
        val onlineTitle = titleKey("session-a", SearchResultSourceSection.Online)

        val diff = pageElementKeyDiff(
            previousKeys = listOf(localTitle),
            currentKeys = listOf(localTitle, onlineTitle)
        )

        assertEquals(setOf(localTitle), diff.retainedKeys)
        assertEquals(listOf(onlineTitle), diff.enteringKeys)
        assertTrue(diff.exitingKeys.isEmpty())
    }

    @Test
    fun onlineRemovalMakesItsTitleExit() {
        val localTitle = titleKey("session-a", SearchResultSourceSection.Local)
        val onlineTitle = titleKey("session-a", SearchResultSourceSection.Online)

        val diff = pageElementKeyDiff(
            previousKeys = listOf(localTitle, onlineTitle),
            currentKeys = listOf(localTitle)
        )

        assertEquals(listOf(onlineTitle), diff.exitingKeys)
    }

    @Test
    fun queryChangeMakesBothExistingTitlesExit() {
        val oldLocalTitle = titleKey("query-a", SearchResultSourceSection.Local)
        val oldOnlineTitle = titleKey("query-a", SearchResultSourceSection.Online)

        val diff = pageElementKeyDiff(
            previousKeys = listOf(oldLocalTitle, oldOnlineTitle),
            currentKeys = emptyList()
        )

        assertEquals(listOf(oldLocalTitle, oldOnlineTitle), diff.exitingKeys)
    }

    @Test
    fun titleKeyIsStableWhenResultCountChanges() {
        assertEquals(
            titleKey("session-a", SearchResultSourceSection.Local),
            titleKey("session-a", SearchResultSourceSection.Local)
        )
    }

    @Test
    fun onlineArrivalDoesNotReplayEnteredLocalTitle() {
        val localTitle = titleKey("session-a", SearchResultSourceSection.Local)
        val onlineTitle = titleKey("session-a", SearchResultSourceSection.Online)

        val diff = pageElementKeyDiff(
            previousKeys = listOf(localTitle),
            currentKeys = listOf(localTitle, onlineTitle)
        )

        assertTrue(localTitle !in diff.enteringKeys)
    }

    private fun titleKey(
        sessionIdentity: String,
        section: SearchResultSourceSection
    ): String = searchResultSectionTitleKey(sessionIdentity, section)
}
