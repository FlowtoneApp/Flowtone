package ink.tenqui.flowtone.data.online.oauth

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Only Host code constructs profiles. Manifest data never supplies endpoints or client secrets. */
internal data class OAuthProviderProfile(
    val id: String,
    val realm: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String?,
    val redirectUri: String?,
    val requiresBroker: Boolean
)

internal object OAuthProviderProfiles {
    val soundCloud = OAuthProviderProfile("soundcloud", "soundcloud.com", "https://secure.soundcloud.com/authorize",
        "https://secure.soundcloud.com/oauth/token", null, null, true)
    fun find(id: String): OAuthProviderProfile? = if (id == soundCloud.id) soundCloud else null
}

internal enum class OAuthFailure { PROVIDER_CONFIGURATION_REQUIRED, SESSION_INVALID, CALLBACK_INVALID, EXPIRED, CANCELLED, AUTHORIZATION_FAILED, TOKEN_EXCHANGE_FAILED, BROWSER_UNAVAILABLE }
internal class OAuthException(val code: OAuthFailure) : Exception(code.name)

internal class OAuthAuthorizationRequest internal constructor(val authorizationUrl: String, internal val sessionId: String) {
    override fun toString() = "OAuthAuthorizationRequest([redacted])"
}

internal class OAuthCodeExchange internal constructor(
    val profile: OAuthProviderProfile, val code: String, val verifier: String,
    val extensionId: String, val instanceId: String, val accountId: String, val methodId: String
) {
    private var consumed = false
    @Synchronized internal fun consume() {
        if (consumed) throw OAuthException(OAuthFailure.SESSION_INVALID)
        consumed = true
    }
    override fun toString() = "OAuthCodeExchange([redacted])"
}

/** Process-local, single-use sessions; verifier and state are never persisted. */
internal class OAuthAcquisitionCoordinator(
    private val random: SecureRandom = SecureRandom(),
    private val now: () -> Long = System::currentTimeMillis,
    private val lifetimeMs: Long = 5 * 60_000L,
    private val allowInsecureTestEndpoint: Boolean = false
) {
    private data class Session(val profile: OAuthProviderProfile, val state: String, val verifier: String,
        val extensionId: String, val instanceId: String, val accountId: String, val methodId: String, val expiresAt: Long)
    private val sessions = mutableMapOf<String, Session>()

    @Synchronized fun begin(profile: OAuthProviderProfile, extensionId: String, instanceId: String, accountId: String,
                            methodId: String, scopes: List<String>): OAuthAuthorizationRequest {
        if (profile.requiresBroker || profile.clientId.isNullOrBlank() || profile.redirectUri.isNullOrBlank())
            throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
        val endpoint = runCatching { URI(profile.authorizationEndpoint) }.getOrNull()
        if ((endpoint?.scheme != "https" && !(allowInsecureTestEndpoint && endpoint?.scheme == "http" && endpoint.host == "127.0.0.1")) || endpoint.host == null || endpoint.userInfo != null || endpoint.fragment != null || endpoint.query != null)
            throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
        if (sessions.size >= 8) throw OAuthException(OAuthFailure.SESSION_INVALID)
        val state = nonce(); val verifier = nonce(); val sessionId = nonce()
        val challenge = base64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val query = listOf("response_type" to "code", "client_id" to profile.clientId, "redirect_uri" to profile.redirectUri,
            "state" to state, "code_challenge" to challenge, "code_challenge_method" to "S256") +
            if (scopes.isEmpty()) emptyList() else listOf("scope" to scopes.joinToString(" "))
        val url = profile.authorizationEndpoint + "?" + query.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        sessions[sessionId] = Session(profile, state, verifier, extensionId, instanceId, accountId, methodId, now() + lifetimeMs)
        return OAuthAuthorizationRequest(url, sessionId)
    }

    @Synchronized fun cancel(sessionId: String) { sessions.remove(sessionId) }

    @Synchronized fun accept(sessionId: String, rawUri: String): OAuthCodeExchange {
        val session = sessions[sessionId] ?: throw OAuthException(OAuthFailure.SESSION_INVALID)
        if (now() >= session.expiresAt) { sessions.remove(sessionId); throw OAuthException(OAuthFailure.EXPIRED) }
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        val redirect = runCatching { URI(session.profile.redirectUri!!) }.getOrNull() ?: throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        if (uri.scheme != redirect.scheme || uri.rawAuthority != redirect.rawAuthority || uri.rawPath != redirect.rawPath ||
            uri.rawFragment != null || redirect.rawQuery != null || uri.rawQuery == null) throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        val pairs = runCatching { uri.rawQuery.split('&').map { part ->
            val parts = part.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        } }.getOrElse { throw OAuthException(OAuthFailure.CALLBACK_INVALID) }
        if (pairs.groupingBy { it.first }.eachCount().any { it.value != 1 }) throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        val params = pairs.toMap()
        val state = params["state"] ?: throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        if (!MessageDigest.isEqual(state.toByteArray(StandardCharsets.UTF_8), session.state.toByteArray(StandardCharsets.UTF_8)))
            throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        sessions.remove(sessionId)
        if (params.containsKey("error")) throw OAuthException(if (params["error"] == "access_denied") OAuthFailure.CANCELLED else OAuthFailure.AUTHORIZATION_FAILED)
        val code = params["code"]?.takeIf { it.isNotBlank() && it.length <= 4096 }
            ?: throw OAuthException(OAuthFailure.CALLBACK_INVALID)
        return OAuthCodeExchange(session.profile, code, session.verifier, session.extensionId, session.instanceId, session.accountId, session.methodId)
    }

    private fun nonce(): String = ByteArray(32).also(random::nextBytes).let(::base64)
    private fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
