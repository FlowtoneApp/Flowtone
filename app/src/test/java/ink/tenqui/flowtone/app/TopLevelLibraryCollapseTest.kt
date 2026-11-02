package ink.tenqui.flowtone.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelLibraryCollapseTest {
    @Test fun progressDoesNotJumpWhenHeaderLeavesViewport() {
        assertEquals(1f, libraryHeaderCollapseProgress(0, 145f, 146f, 18f, 96f), 0.001f)
        assertEquals(1f, libraryHeaderCollapseProgress(1, 0f, null, 18f, 96f), 0.001f)
        assertEquals(1f, libraryHeaderCollapseProgress(0, 79f, 80f, 18f, 96f), 0.02f)
        assertEquals(0f, libraryHeaderCollapseProgress(0, 0f, 146f, 18f, 96f), 0.001f)
    }
}
