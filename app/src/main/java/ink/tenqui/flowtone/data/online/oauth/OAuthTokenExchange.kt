package ink.tenqui.flowtone.data.online.oauth

import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class OAuthTokenResult(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long?, val scopes: String?) {
    override fun toString() = "OAuthTokenResult([redacted])"
}

/** Public-client exchange; endpoints originate only from a trusted Host profile. */
internal class OAuthTokenExchange(private val allowInsecureTestEndpoint: Boolean = false) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    fun exchange(exchange: OAuthCodeExchange): OAuthTokenResult {
        exchange.consume()
        val profile = exchange.profile
        if (profile.requiresBroker || profile.clientId.isNullOrBlank() || profile.redirectUri.isNullOrBlank())
            throw OAuthException(OAuthFailure.PROVIDER_CONFIGURATION_REQUIRED)
        try {
            val endpoint = URI(profile.tokenEndpoint)
            require(endpoint.host != null && endpoint.userInfo == null && endpoint.fragment == null &&
                (endpoint.scheme == "https" || (allowInsecureTestEndpoint && endpoint.scheme == "http" && endpoint.host == "127.0.0.1")))
            val form = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", profile.clientId)
                .add("redirect_uri", profile.redirectUri).add("code", exchange.code).add("code_verifier", exchange.verifier).build()
            client.newCall(Request.Builder().url(profile.tokenEndpoint).post(form).build()).execute().use { response ->
                if (!response.isSuccessful) throw OAuthException(OAuthFailure.TOKEN_EXCHANGE_FAILED)
                val stream = response.body?.byteStream() ?: throw OAuthException(OAuthFailure.TOKEN_EXCHANGE_FAILED)
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 64 * 1024)
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                bytes.fill(0)
                val access = json.opt("access_token") as? String ?: error("invalid token")
                require(access.isNotEmpty() && access.length <= 16 * 1024)
                val refresh = if (json.has("refresh_token")) json.opt("refresh_token") as? String ?: error("invalid token") else null
                require(refresh == null || (refresh.isNotEmpty() && refresh.length <= 16 * 1024))
                require((json.opt("token_type") as? String)?.equals("Bearer", true) == true)
                val expires = if (json.has("expires_in")) (json.opt("expires_in") as? Number)?.toLong() ?: error("invalid expiry") else null
                require(expires == null || expires > 0)
                val scopes = if (json.has("scope")) json.opt("scope") as? String ?: error("invalid scope") else null
                return OAuthTokenResult(access, refresh, expires, scopes)
            }
        } catch (e: OAuthException) { throw e }
        catch (_: Exception) { throw OAuthException(OAuthFailure.TOKEN_EXCHANGE_FAILED) }
    }
}
