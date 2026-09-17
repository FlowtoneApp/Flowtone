package ink.tenqui.flowtone.data.online.permission

import java.util.Locale

enum class NetworkSecurity {
    Secure,
    Insecure
}

enum class NetworkScheme(
    val value: String,
    val defaultPort: Int,
    val security: NetworkSecurity
) {
    Https("https", 443, NetworkSecurity.Secure),
    Http("http", 80, NetworkSecurity.Insecure);

    companion object {
        fun fromValue(value: String): NetworkScheme? = entries.firstOrNull {
            it.value == value.trim().lowercase(Locale.ROOT)
        }
    }
}

class NetworkOrigin private constructor(
    val scheme: NetworkScheme,
    val host: String,
    /** null 表示使用该 scheme 的默认端口。 */
    val port: Int?
) {
    val security: NetworkSecurity get() = scheme.security
    val effectivePort: Int get() = port ?: scheme.defaultPort

    override fun equals(other: Any?): Boolean = other is NetworkOrigin &&
        scheme == other.scheme && host == other.host && port == other.port

    override fun hashCode(): Int = 31 * (31 * scheme.hashCode() + host.hashCode()) + (port ?: 0)

    override fun toString(): String = buildString {
        append(scheme.value)
        append("://")
        append(host)
        port?.let { append(':').append(it) }
    }

    companion object {
        fun of(scheme: String, host: String, port: Int? = null): NetworkOrigin {
            val normalizedScheme = requireNotNull(NetworkScheme.fromValue(scheme)) {
                "仅支持 http 和 https Origin"
            }
            return of(normalizedScheme, host, port)
        }

        fun of(scheme: NetworkScheme, host: String, port: Int? = null): NetworkOrigin {
            val normalizedHost = host.trim().trimEnd('.').lowercase(Locale.ROOT)
            require(normalizedHost.isNotEmpty()) { "Origin host 不能为空" }
            require('*' !in normalizedHost && '/' !in normalizedHost && ':' !in normalizedHost) {
                "Origin host 必须是具体 host，不能包含 wildcard、路径或端口"
            }
            require(normalizedHost.none(Char::isWhitespace)) { "Origin host 不能包含空白字符" }
            if (port != null) require(port in 1..65535) { "Origin port 超出有效范围" }
            val canonicalPort = port?.takeUnless { it == scheme.defaultPort }
            return NetworkOrigin(scheme, normalizedHost, canonicalPort)
        }
    }
}

enum class NetworkHostScope {
    Exact,
    SubdomainsOnly
}

data class NetworkOriginPermission(
    val origin: NetworkOrigin,
    val hostScope: NetworkHostScope = NetworkHostScope.Exact
) {
    val security: NetworkSecurity get() = origin.security

    override fun toString(): String = if (hostScope == NetworkHostScope.Exact) {
        origin.toString()
    } else {
        buildString {
            append(origin.scheme.value)
            append("://*.")
            append(origin.host)
            origin.port?.let { append(':').append(it) }
        }
    }
}

class LegacyNetworkHostRule private constructor(
    val value: String,
    val host: String,
    val hostScope: NetworkHostScope
) {
    companion object {
        fun parse(value: String): LegacyNetworkHostRule {
            val normalized = value.trim().trimEnd('.').lowercase(Locale.ROOT)
            val wildcard = normalized.startsWith("*.")
            val host = normalized.removePrefix("*.")
            require(host.isNotEmpty() && '*' !in host && '/' !in host && ':' !in host) {
                "Legacy network host 规则非法"
            }
            require(host.none(Char::isWhitespace)) { "Legacy network host 规则包含空白字符" }
            return LegacyNetworkHostRule(
                value = if (wildcard) "*.$host" else host,
                host = host,
                hostScope = if (wildcard) NetworkHostScope.SubdomainsOnly else NetworkHostScope.Exact
            )
        }
    }
}

object LegacyNetworkPermissionCanonicalizer {
    /**
     * v1 manifest 的 host 规则本身没有 scheme。这里映射为 HTTPS，是因为当前
     * ExtensionHostPolicy 的实际放行行为只允许 HTTPS，而不是把旧声明解释成通用 HTTPS 授权。
     */
    fun canonicalize(hostRules: Collection<String>): Set<NetworkOriginPermission> = hostRules
        .map(LegacyNetworkHostRule::parse)
        .mapTo(linkedSetOf()) { rule ->
            NetworkOriginPermission(
                origin = NetworkOrigin.of(NetworkScheme.Https, rule.host),
                hostScope = rule.hostScope
            )
        }
}

data class NetworkSecurityDowngrade(
    val previous: NetworkOriginPermission,
    val incoming: NetworkOriginPermission
)

internal fun NetworkOriginPermission.hasSameAuthoritySemanticsAs(
    other: NetworkOriginPermission
): Boolean = origin.host == other.origin.host &&
    hostScope == other.hostScope &&
    when {
        origin.port == null && other.origin.port == null -> true
        else -> origin.port == other.origin.port
    }
