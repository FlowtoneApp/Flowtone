package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialSecretAccessGateTest {
    @Test
    fun decryptCanStartOnlyAfterTheBoundRequestIsAuthenticated() {
        val gate = CredentialSecretAccessGate()
        val request = gate.begin("source-a", CredentialFieldId.Password)!!
        var reads = 0

        assertEquals(0, reads)
        assertTrue(gate.canCompleteAuthentication(request, "source-a"))
        assertFalse(gate.canCompleteAuthentication(request, "source-b"))
        assertFalse(gate.authorizeRead(request, "source-b"))
        assertEquals(0, reads)

        assertTrue(gate.authorizeRead(request, "source-a"))
        reads++
        assertEquals(1, reads)
        assertFalse(gate.canCompleteAuthentication(request, "source-a"))
        gate.finishRead(request)
        assertFalse(gate.isBusy)
    }

    @Test
    fun duplicateClicksAndOtherFieldsCannotReuseOneAuthentication() {
        val gate = CredentialSecretAccessGate()
        val passwordRequest = gate.begin("source-a", CredentialFieldId.Password)!!

        assertNull(gate.begin("source-a", CredentialFieldId.Password))
        assertNull(gate.begin("source-a", CredentialFieldId.Cookie))
        assertTrue(gate.authorizeRead(passwordRequest, "source-a"))
        gate.finishRead(passwordRequest)

        val cookieRequest = gate.begin("source-a", CredentialFieldId.Cookie)!!
        assertFalse(gate.canCompleteAuthentication(passwordRequest, "source-a"))
        assertTrue(gate.canCompleteAuthentication(cookieRequest, "source-a"))
        assertEquals(CredentialFieldId.Cookie, cookieRequest.fieldId)
    }

    @Test
    fun canceledOrDisposedPageRejectsLateAuthenticationResult() {
        val canceledGate = CredentialSecretAccessGate()
        val canceled = canceledGate.begin("source-a", CredentialFieldId.Cookie)!!
        canceledGate.cancel(canceled)
        assertFalse(canceledGate.authorizeRead(canceled, "source-a"))

        val disposedGate = CredentialSecretAccessGate()
        val disposed = disposedGate.begin("source-a", CredentialFieldId.Password)!!
        disposedGate.close()
        assertFalse(disposedGate.canCompleteAuthentication(disposed, "source-a"))
        assertFalse(disposedGate.authorizeRead(disposed, "source-a"))
    }

    @Test
    fun hidingDuringReadReleasesThatRequestWithoutKeepingAnUnlock() {
        val gate = CredentialSecretAccessGate()
        val request = gate.begin("source-a", CredentialFieldId.Password)!!
        assertTrue(gate.authorizeRead(request, "source-a"))

        gate.cancelRead(request)
        assertFalse(gate.isBusy)
        assertFalse(gate.canCompleteAuthentication(request, "source-a"))
        val nextRequest = gate.begin("source-a", CredentialFieldId.Cookie)!!
        assertTrue(nextRequest.token != request.token)
        assertTrue(gate.canCompleteAuthentication(nextRequest, "source-a"))
    }

    @Test
    fun api28And29UseBiometricWithKeyguardFallbackAndApi30UsesCombinedPrompt() {
        assertEquals(CredentialAuthApiMode.BiometricWithKeyguardFallback, credentialAuthApiMode(28))
        assertEquals(CredentialAuthApiMode.BiometricWithKeyguardFallback, credentialAuthApiMode(29))
        assertEquals(CredentialAuthApiMode.BiometricAndDeviceCredential, credentialAuthApiMode(30))
        assertEquals(CredentialAuthApiMode.BiometricAndDeviceCredential, credentialAuthApiMode(36))
    }

    @Test
    fun legacyDirectKeyguardFallbackRequiresSecureDeviceCredentialAndUnavailableBiometric() {
        assertTrue(shouldUseDirectKeyguardFallback(28, biometricAvailable = false, deviceSecure = true))
        assertTrue(shouldUseDirectKeyguardFallback(29, biometricAvailable = false, deviceSecure = true))
        assertFalse(shouldUseDirectKeyguardFallback(28, biometricAvailable = true, deviceSecure = true))
        assertFalse(shouldUseDirectKeyguardFallback(29, biometricAvailable = false, deviceSecure = false))
        assertFalse(shouldUseDirectKeyguardFallback(30, biometricAvailable = false, deviceSecure = true))
    }
}
