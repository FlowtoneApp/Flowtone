package ink.tenqui.flowtone.data.online.oauth

import java.net.ServerSocket
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Debug-only loopback fixture. It never accepts provider configuration from extensions. */
internal object OAuthDebugProvider {
    private val codes = ConcurrentHashMap<String, String>()
    private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    private val endpoint = "http://127.0.0.1:${socket.localPort}"
    init {
        thread(name = "oauth-debug-fixture", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) {
                    client.use { connection ->
                        try {
                            connection.soTimeout = 5000
                            val input = connection.getInputStream().bufferedReader(Charsets.UTF_8)
                            val first = input.readLine() ?: return@use
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val line = input.readLine() ?: return@use
                                if (line.isEmpty()) break
                                val i = line.indexOf(':'); if (i > 0) headers[line.substring(0, i).lowercase()] = line.substring(i + 1).trim()
                            }
                            val length = headers["content-length"]?.toIntOrNull() ?: 0
                            require(length in 0..8192)
                            val body = CharArray(length); var used = 0
                            while (used < length) { val n = input.read(body, used, length - used); if (n < 0) break; used += n }
                            val parts = first.split(' ')
                            val path = parts.getOrElse(1) { "" }
                            val parameters = parse(if (parts.firstOrNull() == "POST") String(body, 0, used) else path.substringAfter('?', ""))
                            val reply = when {
                                path.startsWith("/authorize?") && parameters["client_id"] == "flowtone-debug-public" && parameters["code_challenge_method"] == "S256" -> {
                                    val code = UUID.randomUUID().toString()
                                    codes[code] = parameters["code_challenge"].orEmpty()
                                    val state = URLEncoder.encode(parameters["state"].orEmpty(), "UTF-8")
                                    val link = "ink.tenqui.flowtone.oauth://callback/complete?code=$code&amp;state=$state"
                                    "<html><body><p>Flowtone Debug OAuth 测试，不使用真实账户。</p><a href=\"$link\">确认并返回 Flowtone</a></body></html>"
                                }
                                path == "/token" && parameters["client_id"] == "flowtone-debug-public" -> {
                                    val expected = codes.remove(parameters["code"].orEmpty())
                                    val digest = MessageDigest.getInstance("SHA-256").digest(parameters["code_verifier"].orEmpty().toByteArray(Charsets.US_ASCII))
                                    val actual = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
                                    if (expected != null && MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray()))
                                        """{"access_token":"debug-fixture-token","token_type":"Bearer","expires_in":60}"""
                                    else "{}"
                                }
                                else -> "{}"
                            }
                            val bytes = reply.toByteArray(Charsets.UTF_8)
                            val output = connection.getOutputStream()
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: ${if (reply.startsWith("<")) "text/html; charset=utf-8" else "application/json"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(bytes); output.flush()
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }
    @JvmStatic fun start(): OAuthProviderProfile = OAuthProviderProfile("flowtone-debug-test", "example.test", "$endpoint/authorize", "$endpoint/token", "flowtone-debug-public", "ink.tenqui.flowtone.oauth://callback/complete", false)
    private fun parse(value: String): Map<String, String> = value.split('&').filter { it.isNotEmpty() }.associate { part ->
        val split = part.split('=', limit = 2)
        URLDecoder.decode(split[0], "UTF-8") to URLDecoder.decode(split.getOrElse(1) { "" }, "UTF-8")
    }
}
