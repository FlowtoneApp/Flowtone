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
            require(normalizedHost.split('.').all { label ->
                label.isNotEmpty() && !label.startsWith('-') && !label.endsWith('-') &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }) { "Origin host 非法" }
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

object NetworkOriginPermissionParser {
    private val OriginPattern = Regex(
        pattern = "^(https?)://(\\*\\.)?([a-zA-Z0-9.-]+)(?::([0-9]{1,5}))?$",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(value: String): NetworkOriginPermission {
        val match = requireNotNull(OriginPattern.matchEntire(value.trim())) {
            "网络权限必须是纯 http/https Origin，不能包含 userinfo、路径、查询或 fragment"
        }
        val scheme = requireNotNull(NetworkScheme.fromValue(match.groupValues[1]))
        val wildcard = match.groupValues[2].isNotEmpty()
        val host = match.groupValues[3]
        val port = match.groupValues[4].takeIf(String::isNotEmpty)?.toInt()
        return NetworkOriginPermission(
            origin = NetworkOrigin.of(scheme, host, port),
            hostScope = if (wildcard) NetworkHostScope.SubdomainsOnly else NetworkHostScope.Exact
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
