package ink.tenqui.flowtone.data.online.oauth

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class OAuthAcquisitionCoordinatorTest {
    private fun profile(token: String = "https://example.com/token") = OAuthProviderProfile("test", "example.com",
        "https://example.com/authorize", token, "public-client", "ink.example:/oauth/callback", false)
    private fun params(url: String): Map<String, String> = URI(url).rawQuery.split('&').associate {
        val parts = it.split('=', limit = 2); URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
    }
    private fun begin(c: OAuthAcquisitionCoordinator, p: OAuthProviderProfile = profile()) = c.begin(p, "extension", "instance", "account", "login", listOf("read"))

    @Test fun pkceExchangeWithLocalServerAndReplayRejection() {
        var challenge = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { request ->
            val body = request.requestBody.readBytes().toString(Charsets.UTF_8)
            val form = URI("https://example.test/?$body").rawQuery.split('&').associate {
                val parts = it.split('=', limit = 2); URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(form.getValue("code_verifier").toByteArray(Charsets.US_ASCII))
            val valid = Base64.getUrlEncoder().withoutPadding().encodeToString(digest) == challenge && form["code"] == "test-code"
            val response = if (valid) """{"access_token":"private-token","token_type":"Bearer","expires_in":3600}""" else "{}"
            val bytes = response.toByteArray()
            request.sendResponseHeaders(if (valid) 200 else 400, bytes.size.toLong())
            request.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val coordinator = OAuthAcquisitionCoordinator()
            val auth = begin(coordinator, profile("http://127.0.0.1:${server.address.port}/token"))
            val parameters = params(auth.authorizationUrl)
            challenge = parameters.getValue("code_challenge")
            val callback = "ink.example:/oauth/callback?state=${parameters.getValue("state")}&code=test-code"
            val exchange = coordinator.accept(auth.sessionId, callback)
            val result = OAuthTokenExchange(allowInsecureTestEndpoint = true).exchange(exchange)
            assertEquals("private-token", result.accessToken)
            assertFalse(result.toString().contains("private-token"))
            assertEquals(OAuthFailure.SESSION_INVALID, (runCatching { OAuthTokenExchange(true).exchange(exchange) }.exceptionOrNull() as OAuthException).code)
            assertEquals(OAuthFailure.SESSION_INVALID, (runCatching { coordinator.accept(auth.sessionId, callback) }.exceptionOrNull() as OAuthException).code)
            val bad = OAuthCodeExchange(exchange.profile, exchange.code, "incorrect", "extension", "instance", "account", "login")
            assertEquals(OAuthFailure.TOKEN_EXCHANGE_FAILED, (runCatching { OAuthTokenExchange(true).exchange(bad) }.exceptionOrNull() as OAuthException).code)
        } finally { server.stop(0) }
    }

    @Test fun oauthErrorsAndProcessSessionLossFailClosed() {
        val first = OAuthAcquisitionCoordinator()
        val request = begin(first)
        val state = params(request.authorizationUrl).getValue("state")
        fun failure(block: () -> Any) = (runCatching(block).exceptionOrNull() as OAuthException).code
        val callback = "ink.example:/oauth/callback?state=$state&error=access_denied"
        assertEquals(OAuthFailure.SESSION_INVALID, failure { OAuthAcquisitionCoordinator().accept(request.sessionId, callback) })
        assertEquals(OAuthFailure.CANCELLED, failure { first.accept(request.sessionId, callback) })
        assertEquals(OAuthFailure.SESSION_INVALID, failure { first.accept(request.sessionId, callback) })
        val second = begin(first)
        val secondState = params(second.authorizationUrl).getValue("state")
        assertEquals(OAuthFailure.AUTHORIZATION_FAILED, failure { first.accept(second.sessionId, "ink.example:/oauth/callback?state=$secondState&error=server_error") })
    }

    @Test fun rejectsInvalidCallbackCancellationExpiryAndUnconfiguredProvider() {
        var now = 100L
        val coordinator = OAuthAcquisitionCoordinator(now = { now }, lifetimeMs = 10)
        val auth = begin(coordinator)
        val state = params(auth.authorizationUrl).getValue("state")
        fun failure(block: () -> Any) = (runCatching(block).exceptionOrNull() as OAuthException).code
        assertEquals(OAuthFailure.CALLBACK_INVALID, failure { coordinator.accept(auth.sessionId, "ink.example:/oauth/callback?state=wrong&code=x") })
        assertEquals(OAuthFailure.CALLBACK_INVALID, failure { coordinator.accept(auth.sessionId, "ink.example:/other?state=$state&code=x") })
        coordinator.cancel(auth.sessionId)
        assertEquals(OAuthFailure.SESSION_INVALID, failure { coordinator.accept(auth.sessionId, "ink.example:/oauth/callback?state=$state&code=x") })
        val expired = begin(coordinator); now += 10
        assertEquals(OAuthFailure.EXPIRED, failure { coordinator.accept(expired.sessionId, "ink.example:/oauth/callback?state=x&code=x") })
        assertEquals(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED, failure { begin(coordinator, OAuthProviderProfiles.soundCloud) })
    }
}
