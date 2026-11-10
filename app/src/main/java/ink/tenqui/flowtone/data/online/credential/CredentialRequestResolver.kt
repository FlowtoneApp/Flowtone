package ink.tenqui.flowtone.data.online.credential

import ink.tenqui.flowtone.data.online.network.GlobalExtensionNetworkLimiter
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.permission.ExtensionNetworkAccessPolicy
import java.net.URI

internal enum class CredentialResolutionError {
    CREDENTIAL_NOT_AUTHORIZED, CREDENTIAL_MISSING, CREDENTIAL_UNAVAILABLE,
    CREDENTIAL_ORIGIN_DENIED, CREDENTIAL_SCHEME_UNSUPPORTED
}

internal class CredentialResolutionException(val code: CredentialResolutionError) : Exception(code.name)

/** 只供一次 Host 传输使用；调用方必须在传输结束后关闭，不得记录或序列化。 */
internal class CommittedCredentialHeaders internal constructor(private val values: MutableMap<String, String>) : AutoCloseable {
    internal fun forTransport(): Map<String, String> = values
    override fun close() { values.clear() }
    override fun toString(): String = "CommittedCredentialHeaders([redacted])"
}

/** 必须在取得活动网络配额后调用；返回即为发送提交点，调用者独占 admission 直至传输完成。 */
internal class CredentialRequestResolver(
    private val grants: CredentialGrantStore,
    private val sources: CredentialSourceRepository,
    private val currentInstalled: (String) -> InstalledExtension?
) {
    fun commit(
        admission: GlobalExtensionNetworkLimiter.Admission,
        extensionId: String,
        installationInstanceId: String,
        requestId: String,
        targetUrl: String
    ): CommittedCredentialHeaders {
        admission.requireActive()
        try {
            val initial = grants.find(extensionId, requestId) ?: fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
            return CredentialGrantLifecycleLocks.withGrantBinding(extensionId, initial.credentialSourceId) {
                admission.requireActive()
                val grant = grants.find(extensionId, requestId) ?: fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                if (grant != initial) fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                val installed = currentInstalled(extensionId) ?: fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                if (installed.manifest.id != extensionId || installed.installationInstanceId != installationInstanceId ||
                    grant.extensionInstanceId != installationInstanceId) fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                val request = installed.descriptor.credentialRequests.firstOrNull { it.id == requestId }
                    ?: fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                val snapshot = CredentialRequestContractSnapshot.from(request)
                if (snapshot == null || snapshot != grant.requestContract || snapshot.named == null) {
                    fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                }
                val uri = runCatching { URI(targetUrl) }.getOrNull()
                    ?: fail(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED)
                if (!uri.scheme.equals("https", true) || uri.host == null || uri.rawUserInfo != null || uri.rawFragment != null) {
                    fail(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED)
                }
                val origin = runCatching { CredentialTargetOrigin.normalize(uri.scheme + "://" + uri.rawAuthority) }.getOrNull()
                if (origin != snapshot.named.origin) fail(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED)
                try { ExtensionNetworkAccessPolicy(installed.descriptor.networkPermissions).requireAllowed(targetUrl) }
                catch (_: Exception) { fail(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED) }
                val source = sources.get(grant.credentialSourceId) ?: fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                if (source.credentialType != CredentialType.GenericAccount || source.realm != snapshot.realm) {
                    fail(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                }
                val available = sources.namedSecretFields(source.id).associateBy { it.field.id }
                val cookies = mutableListOf<String>()
                var bearer: String? = null
                for (field in snapshot.named.fields.sortedBy { it.id }) {
                    val stored = available[field.id]
                    if (stored != null && stored.field.type != field.type) fail(CredentialResolutionError.CREDENTIAL_SCHEME_UNSUPPORTED)
                    val secret = when (val result = sources.readNamedSecret(source.id, NamedSecretField(field.id, field.type))) {
                        is CredentialSecretReadResult.Available -> result.plaintext
                        CredentialSecretReadResult.NotConfigured -> {
                            if (field.required) fail(CredentialResolutionError.CREDENTIAL_MISSING)
                            continue
                        }
                        is CredentialSecretReadResult.Unavailable -> fail(CredentialResolutionError.CREDENTIAL_UNAVAILABLE)
                    }
                    when (field.injection) {
                        CredentialInjection.Cookie -> {
                            val name = field.cookieName ?: fail(CredentialResolutionError.CREDENTIAL_SCHEME_UNSUPPORTED)
                            if (field.type != NamedSecretType.Cookie || !CookieValue.matches(secret)) fail(CredentialResolutionError.CREDENTIAL_SCHEME_UNSUPPORTED)
                            cookies += "$name=$secret"
                        }
                        CredentialInjection.Bearer -> {
                            if (field.type != NamedSecretType.Token || bearer != null || !BearerValue.matches(secret)) fail(CredentialResolutionError.CREDENTIAL_SCHEME_UNSUPPORTED)
                            bearer = secret
                        }
                    }
                }
                val headers = mutableMapOf<String, String>()
                if (cookies.isNotEmpty()) headers["Cookie"] = cookies.joinToString("; ")
                bearer?.let { headers["Authorization"] = "Bearer $it" }
                CommittedCredentialHeaders(headers)
            }
        } catch (e: CredentialResolutionException) {
            throw e
        } catch (_: Exception) {
            throw CredentialResolutionException(CredentialResolutionError.CREDENTIAL_UNAVAILABLE)
        }
    }

    private fun fail(code: CredentialResolutionError): Nothing = throw CredentialResolutionException(code)
    private companion object {
        val CookieValue = Regex("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]*")
        val BearerValue = Regex("[A-Za-z0-9._~+/-]+=*")
    }
}
