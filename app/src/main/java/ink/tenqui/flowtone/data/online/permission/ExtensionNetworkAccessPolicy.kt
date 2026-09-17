package ink.tenqui.flowtone.data.online.permission

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/** Runtime 对扩展所有网络路径执行的 canonical Origin 权限检查。 */
class ExtensionNetworkAccessPolicy(
    private val permissions: Set<NetworkOriginPermission>
) {
    fun requireAllowed(url: String) {
        val uri = runCatching { URI(url) }.getOrElse { throw SecurityException("URL 非法") }
        val scheme = NetworkScheme.fromValue(uri.scheme.orEmpty())
            ?: throw SecurityException("扩展网络仅支持 HTTP 或 HTTPS")
        val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT)
            ?: throw SecurityException("URL 缺少 host")
        if (isLocalOrPrivate(host)) throw SecurityException("禁止访问本机或私有网络")

        val requestedOrigin = runCatching {
            NetworkOrigin.of(scheme, host, uri.port.takeIf { it >= 0 })
        }.getOrElse { throw SecurityException("URL Origin 非法") }
        val allowed = permissions.any { permission -> permission.matches(requestedOrigin) }
        if (!allowed) throw SecurityException("Origin 未获扩展授权：$requestedOrigin")
        if (scheme == NetworkScheme.Http) {
            throw SecurityException("HTTP cleartext 当前不受 Flowtone Android runtime 支持")
        }
    }

    private fun NetworkOriginPermission.matches(requested: NetworkOrigin): Boolean {
        if (origin.scheme != requested.scheme || origin.effectivePort != requested.effectivePort) {
            return false
        }
        return when (hostScope) {
            NetworkHostScope.Exact -> requested.host == origin.host
            NetworkHostScope.SubdomainsOnly ->
                requested.host != origin.host && requested.host.endsWith(".${origin.host}")
        }
    }

    private fun isLocalOrPrivate(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true
        val isIpLiteral = host.all { it.isDigit() || it == '.' } || ':' in host
        if (!isIpLiteral) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return true
        return address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
    }
}
