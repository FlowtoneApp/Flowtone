package ink.tenqui.flowtone.ui.screens

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal enum class CredentialAuthApiMode {
    BiometricAndDeviceCredential,
    BiometricWithKeyguardFallback
}

internal fun credentialAuthApiMode(apiLevel: Int): CredentialAuthApiMode =
    if (apiLevel >= 30) {
        CredentialAuthApiMode.BiometricAndDeviceCredential
    } else {
        CredentialAuthApiMode.BiometricWithKeyguardFallback
    }

internal fun shouldUseDirectKeyguardFallback(
    apiLevel: Int,
    biometricAvailable: Boolean,
    deviceSecure: Boolean
): Boolean = apiLevel in 28..29 && !biometricAvailable && deviceSecure

internal sealed interface CredentialSecretAuthenticationLaunch {
    data object PromptStarted : CredentialSecretAuthenticationLaunch
    data object UseDeviceCredential : CredentialSecretAuthenticationLaunch
    data object MissingDeviceSecurity : CredentialSecretAuthenticationLaunch
    data object Unavailable : CredentialSecretAuthenticationLaunch
}

/** Owns Android's authentication prompt; request identity and screen lifetime stay with the caller. */
internal class CredentialSecretAuthenticator(
    private val activity: FragmentActivity
) {
    private var generation = 0L
    private var prompt: BiometricPrompt? = null

    fun authenticate(
        onAuthenticated: () -> Unit,
        onUseDeviceCredential: () -> Unit,
        onFailure: () -> Unit
    ): CredentialSecretAuthenticationLaunch {
        cancel()
        val requestGeneration = generation
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            return CredentialSecretAuthenticationLaunch.MissingDeviceSecurity
        }

        val manager = BiometricManager.from(activity)
        val mode = credentialAuthApiMode(Build.VERSION.SDK_INT)
        val allowedAuthenticators: Int
        val legacyFallback: Boolean
        when (mode) {
            CredentialAuthApiMode.BiometricAndDeviceCredential -> {
                allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                legacyFallback = false
                if (manager.canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                    return CredentialSecretAuthenticationLaunch.Unavailable
                }
            }

            CredentialAuthApiMode.BiometricWithKeyguardFallback -> {
                allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                legacyFallback = true
                val biometricAvailable = manager.canAuthenticate(allowedAuthenticators) ==
                    BiometricManager.BIOMETRIC_SUCCESS
                if (shouldUseDirectKeyguardFallback(Build.VERSION.SDK_INT, biometricAvailable, true)) {
                    return CredentialSecretAuthenticationLaunch.UseDeviceCredential
                }
            }
        }

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (generation != requestGeneration) return
                    prompt = null
                    onAuthenticated()
                }

                override fun onAuthenticationFailed() {
                    // The prompt remains open; a failed sample never authorizes a Secret read.
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (generation != requestGeneration) return
                    prompt = null
                    if (legacyFallback && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onUseDeviceCredential()
                    } else {
                        onFailure()
                    }
                }
            }
        )
        prompt = biometricPrompt
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份")
            .setSubtitle("查看已保存的凭证")
            .setAllowedAuthenticators(allowedAuthenticators)
            .apply {
                if (legacyFallback) setNegativeButtonText("使用设备密码")
            }
            .build()
        return try {
            biometricPrompt.authenticate(promptInfo)
            CredentialSecretAuthenticationLaunch.PromptStarted
        } catch (_: Exception) {
            prompt = null
            CredentialSecretAuthenticationLaunch.Unavailable
        }
    }

    fun cancel() {
        generation++
        prompt?.cancelAuthentication()
        prompt = null
    }
}
