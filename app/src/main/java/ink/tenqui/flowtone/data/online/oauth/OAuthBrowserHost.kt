package ink.tenqui.flowtone.data.online.oauth

import android.content.Context
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.credential.CredentialAcquisitionDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialSourceRepository
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.data.online.credential.OAuthCredentialBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class OAuthUiPhase { IDLE, PREPARING, WAITING, VERIFYING, EXCHANGING, SAVING, SAVED, FAILED, CANCELLED }
internal data class OAuthUiStatus(val extensionId: String = "", val accountId: String = "", val methodId: String = "", val phase: OAuthUiPhase = OAuthUiPhase.IDLE, val error: String? = null, val refreshNotSaved: Boolean = false)

/** Process-only Host state. The UI never receives callback data or tokens. */
internal object OAuthBrowserHost {
    private val coordinator = OAuthAcquisitionCoordinator(allowInsecureTestEndpoint = BuildConfig.DEBUG)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStatus = MutableStateFlow(OAuthUiStatus())
    val status = mutableStatus.asStateFlow()
    private data class Active(val sessionId: String, val extensionId: String, val instanceId: String,
        val accountId: String, val declaration: CredentialAcquisitionDefinition, val profile: OAuthProviderProfile,
        val replaceConfirmed: Boolean, val expiresAt: Long)
    private var active: Active? = null
    private var generation = 0L

    fun canStart(extensionId: String, method: CredentialAcquisitionDefinition): Boolean =
        (OAuthProviderProfiles.find(method.providerProfile)?.let { !it.requiresBroker && !it.clientId.isNullOrBlank() && !it.redirectUri.isNullOrBlank() && it.realm == method.realm } == true) ||
        (BuildConfig.DEBUG && extensionId == "flowtone.oauth.test" && method.providerProfile == "flowtone-debug-test" &&
            method.realm == "example.test" && method.scopes.isEmpty() && method.outputs.size == 1 &&
            method.outputs.single().fieldId == "oauth.access_token" && method.outputs.single().required)

    @Synchronized fun prepare(context: Context, extensionId: String, instanceId: String, accountId: String,
                              method: CredentialAcquisitionDefinition, replaceConfirmed: Boolean = false): String {
        if (active != null || mutableStatus.value.phase in setOf(OAuthUiPhase.EXCHANGING, OAuthUiPhase.SAVING)) throw OAuthException(OAuthFailure.SESSION_INVALID)
        mutableStatus.value = OAuthUiStatus(extensionId, accountId, method.methodId, OAuthUiPhase.PREPARING)
        try {
            val current = ExtensionManager.get(context).installedExtensions().firstOrNull { it.manifest.id == extensionId }
            if (current?.installationInstanceId != instanceId || method !in current.descriptor.credentialAcquisition) throw OAuthException(OAuthFailure.SESSION_INVALID)
            val profile = profileFor(method.providerProfile, extensionId) ?: throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
            if (profile.realm != method.realm) throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
            val source = CredentialSourceRepository.from(context).get(accountId)
            if (source?.credentialType != CredentialType.GenericAccount || source.realm != method.realm) throw OAuthException(OAuthFailure.SESSION_INVALID)
            if (method.outputs.size != 1 || method.outputs.single().fieldId != "oauth.access_token" || !method.outputs.single().required) throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
            if (profile.id == "flowtone-debug-test" && (method.scopes.isNotEmpty() || method.outputs.size != 1 || method.outputs.single().fieldId != "oauth.access_token")) throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
            val result = coordinator.begin(profile, extensionId, instanceId, accountId, method.methodId, method.scopes)
            active = Active(result.sessionId, extensionId, instanceId, accountId, method, profile, replaceConfirmed, android.os.SystemClock.elapsedRealtime() + 5 * 60_000L)
            val sessionGeneration = ++generation
            mutableStatus.value = OAuthUiStatus(extensionId, accountId, method.methodId, OAuthUiPhase.WAITING)
            scope.launch {
                delay(5 * 60_000L)
                synchronized(this@OAuthBrowserHost) {
                    if (generation == sessionGeneration && mutableStatus.value.phase in setOf(OAuthUiPhase.WAITING, OAuthUiPhase.EXCHANGING)) {
                        coordinator.cancel(result.sessionId); active = null; generation++
                        mutableStatus.value = OAuthUiStatus(extensionId, accountId, method.methodId, OAuthUiPhase.FAILED, OAuthFailure.EXPIRED.name)
                    }
                }
            }
            return result.authorizationUrl
        } catch (error: Exception) {
            mutableStatus.value = OAuthUiStatus(extensionId, accountId, method.methodId, OAuthUiPhase.FAILED,
                (error as? OAuthException)?.code?.name ?: OAuthFailure.SESSION_INVALID.name)
            throw OAuthException((error as? OAuthException)?.code ?: OAuthFailure.SESSION_INVALID)
        }
    }

    @Synchronized fun cancel() {
        if (mutableStatus.value.phase !in setOf(OAuthUiPhase.PREPARING, OAuthUiPhase.WAITING, OAuthUiPhase.VERIFYING, OAuthUiPhase.EXCHANGING)) return
        active?.let { coordinator.cancel(it.sessionId) }
        active = null; generation++
        mutableStatus.value = mutableStatus.value.copy(phase = OAuthUiPhase.CANCELLED, error = null)
    }

    @Synchronized fun browserFailed() {
        if (mutableStatus.value.phase != OAuthUiPhase.WAITING) return
        active?.let { coordinator.cancel(it.sessionId) }
        active = null; generation++
        mutableStatus.value = mutableStatus.value.copy(phase = OAuthUiPhase.FAILED, error = OAuthFailure.BROWSER_UNAVAILABLE.name)
    }

    fun accept(context: Context, uri: String) {
        val record: Active
        val exchange: OAuthCodeExchange
        val sessionGeneration: Long
        synchronized(this) {
            record = active ?: run {
                if (mutableStatus.value.phase == OAuthUiPhase.IDLE) mutableStatus.value = OAuthUiStatus(phase = OAuthUiPhase.FAILED, error = OAuthFailure.SESSION_INVALID.name)
                return
            }
            mutableStatus.value = mutableStatus.value.copy(phase = OAuthUiPhase.VERIFYING)
            try {
                if (uri.length > 8192 || !isCurrent(context, record)) throw OAuthException(OAuthFailure.SESSION_INVALID)
                exchange = coordinator.accept(record.sessionId, uri)
                active = null
                sessionGeneration = generation
                mutableStatus.value = mutableStatus.value.copy(phase = OAuthUiPhase.EXCHANGING)
            } catch (error: Exception) {
                coordinator.cancel(record.sessionId); active = null; generation++
                val code = (error as? OAuthException)?.code ?: OAuthFailure.CALLBACK_INVALID
                mutableStatus.value = mutableStatus.value.copy(phase = if (code == OAuthFailure.CANCELLED) OAuthUiPhase.CANCELLED else OAuthUiPhase.FAILED, error = code.name)
                return
            }
        }
        scope.launch {
            val result = runCatching {
                OAuthTokenExchange(allowInsecureTestEndpoint = BuildConfig.DEBUG && exchange.profile.id == "flowtone-debug-test").exchange(exchange)
            }
            synchronized(this@OAuthBrowserHost) {
                if (generation == sessionGeneration) {
                    val token = result.getOrNull()
                    val saved = if (token != null) runCatching {
                        if (android.os.SystemClock.elapsedRealtime() >= record.expiresAt) throw OAuthException(OAuthFailure.EXPIRED)
                        if (!isCurrent(context, record) || profileFor(record.declaration.providerProfile, record.extensionId) != record.profile) throw OAuthException(OAuthFailure.SESSION_INVALID)
                        if (token.scopes != null && !token.scopes.split(' ').filter { it.isNotEmpty() }.containsAll(record.declaration.scopes)) throw OAuthException(OAuthFailure.TOKEN_EXCHANGE_FAILED)
                        mutableStatus.value = mutableStatus.value.copy(phase = OAuthUiPhase.SAVING)
                        val binding = OAuthCredentialBinding(record.profile.id, requireNotNull(record.profile.clientId), record.declaration.realm, record.declaration.scopes.sorted())
                        val manager = ExtensionManager.get(context)
                        CredentialSourceRepository.from(context).saveOAuthAccessToken(record.extensionId, record.instanceId,
                            record.declaration, record.accountId, binding, token.accessToken, record.replaceConfirmed) {
                            manager.installedExtensions().firstOrNull { it.manifest.id == record.extensionId }
                        }
                        manager.invalidateCredentialContext()
                    } else result.map { Unit }
                    generation++
                    mutableStatus.value = mutableStatus.value.copy(phase = if (saved.isSuccess) OAuthUiPhase.SAVED else OAuthUiPhase.FAILED,
                        error = if (saved.isSuccess) null else (saved.exceptionOrNull() as? OAuthException)?.code?.name ?: "CREDENTIAL_SAVE_FAILED",
                        refreshNotSaved = saved.isSuccess && token?.refreshToken != null)
                }
            }
        }
    }

    private fun isCurrent(context: Context, record: Active): Boolean {
        val current = ExtensionManager.get(context).installedExtensions().firstOrNull { it.manifest.id == record.extensionId }
        return current?.installationInstanceId == record.instanceId && record.declaration in current.descriptor.credentialAcquisition
    }

    private fun profileFor(id: String, extensionId: String): OAuthProviderProfile? {
        if (BuildConfig.DEBUG && id == "flowtone-debug-test" && extensionId == "flowtone.oauth.test") {
            return runCatching {
                Class.forName("ink.tenqui.flowtone.data.online.oauth.OAuthDebugProvider")
                    .getMethod("start").invoke(null) as OAuthProviderProfile
            }.getOrNull()
        }
        return OAuthProviderProfiles.find(id)
    }
}
