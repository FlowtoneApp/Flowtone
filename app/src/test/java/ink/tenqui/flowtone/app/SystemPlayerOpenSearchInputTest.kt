package ink.tenqui.flowtone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class SearchInputSessionTest {
    @Test
    fun `system player open dismisses focused search input and cancels pending focus`() {
        val dismissed = dismissSearchInputSession(
            SearchInputContext(
                inputFocused = true,
                keyboardVisible = true,
                focusRequest = 3,
                keyboardDismissRequest = 7
            )
        )

        assertFalse(dismissed.inputFocused)
        assertEquals(0, dismissed.focusRequest)
        assertEquals(8, dismissed.keyboardDismissRequest)
    }

    @Test
    fun `system player open has no search keyboard side effect without input context`() {
        val context = SearchInputContext(
            inputFocused = false,
            keyboardVisible = false,
            focusRequest = 0,
            keyboardDismissRequest = 7
        )

        assertSame(context, dismissSearchInputSession(context))
    }

    @Test
    fun `keyboard-visible search is dismissed even if focus callback has not arrived`() {
        val dismissed = dismissSearchInputSession(
            SearchInputContext(
                inputFocused = false,
                keyboardVisible = true,
                focusRequest = 0,
                keyboardDismissRequest = 0
            )
        )

        assertFalse(dismissed.inputFocused)
        assertEquals(0, dismissed.focusRequest)
        assertEquals(1, dismissed.keyboardDismissRequest)
    }
}
