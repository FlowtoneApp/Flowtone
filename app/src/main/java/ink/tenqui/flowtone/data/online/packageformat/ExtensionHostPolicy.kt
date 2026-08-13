package ink.tenqui.flowtone.data.online.packageformat

import java.net.InetAddress
import java.net.URI

class ExtensionHostPolicy(private val allowedHosts: List<String>) {
    fun requireAllowed(url: String) {
        val uri = runCatching { URI(url) }.getOrElse { throw SecurityException("URL 非法") }
        if (!uri.scheme.equals("https", ignoreCase = true)) throw SecurityException("扩展网络仅允许 HTTPS")
        val host = uri.host?.trimEnd('.')?.lowercase() ?: throw SecurityException("URL 缺少 host")
        if (isLocalOrPrivate(host)) throw SecurityException("禁止访问本机或私有网络")
        if (allowedHosts.none { matches(it, host) }) throw SecurityException("host 未获扩展授权：$host")
    }

    internal fun matches(rule: String, host: String): Boolean {
        val normalizedRule = rule.trimEnd('.').lowercase()
        val normalizedHost = host.trimEnd('.').lowercase()
        return if (normalizedRule.startsWith("*.")) {
            val suffix = normalizedRule.removePrefix("*.")
            normalizedHost != suffix && normalizedHost.endsWith(".$suffix")
        } else {
            normalizedHost == normalizedRule
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
