package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionGroupPositionTest {

    @Test
    fun unselectedItemNeverJoinsAGroup() {
        assertEquals(
            SelectionGroupPosition.None,
            selectionGroupPosition(
                isSelected = false,
                isPreviousSelected = true,
                isNextSelected = true
            )
        )
    }

    @Test
    fun selectedItemUsesItsDisplayedNeighbors() {
        assertEquals(
            SelectionGroupPosition.Single,
            selectionGroupPosition(true, false, false)
        )
        assertEquals(
            SelectionGroupPosition.Top,
            selectionGroupPosition(true, false, true)
        )
        assertEquals(
            SelectionGroupPosition.Middle,
            selectionGroupPosition(true, true, true)
        )
        assertEquals(
            SelectionGroupPosition.Bottom,
            selectionGroupPosition(true, true, false)
        )
    }

    @Test
    fun onlyMiddleAndBottomConnectUpward() {
        assertEquals(false, SelectionGroupPosition.None.connectsTop)
        assertEquals(false, SelectionGroupPosition.Single.connectsTop)
        assertEquals(false, SelectionGroupPosition.Top.connectsTop)
        assertEquals(true, SelectionGroupPosition.Middle.connectsTop)
        assertEquals(true, SelectionGroupPosition.Bottom.connectsTop)
    }

    @Test
    fun onlyTopAndMiddleConnectDownward() {
        assertEquals(false, SelectionGroupPosition.None.connectsBottom)
        assertEquals(false, SelectionGroupPosition.Single.connectsBottom)
        assertEquals(true, SelectionGroupPosition.Top.connectsBottom)
        assertEquals(true, SelectionGroupPosition.Middle.connectsBottom)
        assertEquals(false, SelectionGroupPosition.Bottom.connectsBottom)
    }
}
