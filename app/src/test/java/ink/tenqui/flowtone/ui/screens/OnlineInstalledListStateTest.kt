package ink.tenqui.flowtone.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineInstalledListStateTest {
    @Test
    fun initialLoadDoesNotFlashEmptyOrError() {
        assertEquals(OnlineInstalledListState.Loading, onlineInstalledListState(0, true, null))
        assertEquals(OnlineInstalledListState.Loading, onlineInstalledListState(0, true, "previous error"))
    }

    @Test
    fun existingListRemainsVisibleDuringRefreshFailure() {
        assertEquals(OnlineInstalledListState.Content, onlineInstalledListState(2, true, null))
        assertEquals(OnlineInstalledListState.Content, onlineInstalledListState(2, false, "refresh failed"))
    }

    @Test
    fun emptyAndErrorAreDistinctAfterLoading() {
        assertEquals(OnlineInstalledListState.Empty, onlineInstalledListState(0, false, null))
        assertEquals(OnlineInstalledListState.Error, onlineInstalledListState(0, false, "read failed"))
    }
}
