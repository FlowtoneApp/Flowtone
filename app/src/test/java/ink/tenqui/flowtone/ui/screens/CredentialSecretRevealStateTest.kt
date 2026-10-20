package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialSecretDraft
import ink.tenqui.flowtone.data.online.credential.CredentialSecretFailure
import ink.tenqui.flowtone.data.online.credential.CredentialSecretMutation
import ink.tenqui.flowtone.data.online.credential.CredentialSecretReadResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialSecretRevealStateTest {
    @Test
    fun savedSecretIsReadOnlyAfterExplicitRevealAndHideDropsPlaintext() = runBlocking {
        val state = CredentialSecretRevealState()
        val draft = CredentialSecretDraft()
        var reads = 0
        assertNull(state.result)
        assertEquals(0, reads)

        state.reveal(CredentialFieldId.Password) {
            reads++
            CredentialSecretReadResult.Available("fixture secret")
        }
        assertEquals(1, reads)
        assertTrue(state.result is CredentialSecretReadResult.Available)
        state.hide()
        assertNull(state.field)
        assertNull(state.result)
        assertFalse(state.loading)
        assertEquals(CredentialSecretMutation.Keep, draft.mutation)
        assertFalse(credentialSecretDraftHasChanges("", false, draft))
    }

    @Test
    fun lateReadCannotShowAfterHideOrPageClose() = runBlocking {
        val state = CredentialSecretRevealState()
        val firstResult = CompletableDeferred<CredentialSecretReadResult>()
        val firstRead = async(start = CoroutineStart.UNDISPATCHED) {
            state.reveal(CredentialFieldId.Cookie) { firstResult.await() }
        }
        assertTrue(state.loading)
        state.hide()
        firstResult.complete(CredentialSecretReadResult.Available("fixture cookie"))
        firstRead.await()
        assertNull(state.result)

        val secondResult = CompletableDeferred<CredentialSecretReadResult>()
        val secondRead = async(start = CoroutineStart.UNDISPATCHED) {
            state.reveal(CredentialFieldId.Password) { secondResult.await() }
        }
        state.close()
        secondResult.complete(CredentialSecretReadResult.Available("fixture secret"))
        secondRead.await()
        assertNull(state.result)
        assertNull(state.field)
    }

    @Test
    fun unavailableReadIsVisibleAsFailureAndCanBeHidden() = runBlocking {
        val state = CredentialSecretRevealState()
        state.reveal(CredentialFieldId.Password) {
            CredentialSecretReadResult.Unavailable(CredentialSecretFailure.KeyUnavailable)
        }
        assertTrue(state.result is CredentialSecretReadResult.Unavailable)
        state.hide()
        assertNull(state.result)
    }

    @Test
    fun emptyInputKeepsUnlessClearWasExplicitAndNewInputReplaces() {
        assertEquals(CredentialSecretMutation.Keep, credentialSecretInputMutation("", false))
        assertEquals(CredentialSecretMutation.Delete, credentialSecretInputMutation("", true))
        val replacement = credentialSecretInputMutation("new fixture", false)
        assertTrue(replacement is CredentialSecretMutation.Replace)
        assertTrue(credentialSecretInputMutation("new fixture", true) is CredentialSecretMutation.Replace)
        assertFalse(credentialSecretDraftHasChanges("", false, CredentialSecretDraft()))
        assertTrue(credentialSecretDraftHasChanges("new fixture", false, CredentialSecretDraft()))
        assertTrue(credentialSecretDraftHasChanges("", true, CredentialSecretDraft()))
        assertTrue(credentialSecretDraftHasChanges("", false, CredentialSecretDraft().clear()))
    }

    @Test
    fun newPasswordStartsWithLastTypedRevealAndEyeTogglesFullVisibility() {
        assertEquals(TextObfuscationMode.RevealLastTyped, credentialPasswordObfuscationMode(false))
        assertEquals(TextObfuscationMode.Visible, credentialPasswordObfuscationMode(true))
        assertEquals(TextObfuscationMode.RevealLastTyped, credentialPasswordObfuscationMode(false))
    }

    @Test
    fun cookieMaskPreservesMultilineShapeAndEyeRevealsFullText() {
        val input = AnnotatedString("a=b\nc=d")
        val masked = credentialCookieVisualTransformation(false).filter(input).text.text
        assertEquals("•••\n•••", masked)
        assertEquals(input.text, credentialCookieVisualTransformation(true).filter(input).text.text)
        assertTrue(credentialCookieVisualTransformation(true) === VisualTransformation.None)
    }
}
