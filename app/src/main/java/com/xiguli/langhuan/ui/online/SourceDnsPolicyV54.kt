package com.xiguli.langhuan.ui

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

internal enum class SourceDnsFailureV54 { EMPTY, NON_PUBLIC }

internal class SourceDnsBlockedV54(
    val reason: SourceDnsFailureV54,
    hostname: String? = null,
    route: List<String> = emptyList(),
) : UnknownHostException(
    (sourceDiagnosticHostV55(hostname)?.let { "解析 $it 时：" } ?: "") + when (reason) {
        SourceDnsFailureV54.EMPTY -> "DNS 没有返回可连接的地址，请检查当前网络后重试"
        SourceDnsFailureV54.NON_PUBLIC ->
            "DNS 返回了本机、内网或其他非公网地址，已停止连接；请检查网站地址及当前 DNS/代理的域名解析设置"
    }
) {
    val blockedHost = sourceDiagnosticHostV55(hostname)
    val routeHosts = (route + listOfNotNull(blockedHost)).mapNotNull(::sourceDiagnosticHostV55)
        .fold(emptyList<String>()) { hosts, host -> if (hosts.lastOrNull() == host) hosts else hosts + host }.take(8)
}

private fun sourceDiagnosticHostV55(value: String?): String? =
    value?.takeIf { it.matches(Regex("[A-Za-z0-9.-]{1,253}")) }

internal fun sourceDnsFailureV55(error: Throwable): SourceDnsBlockedV54? =
    generateSequence(error) { it.cause }.take(12).filterIsInstance<SourceDnsBlockedV54>().firstOrNull()

/** The route contains only hosts that this request actually attempted, never URLs or headers. */
internal fun sourceDnsWithRouteV55(error: Exception, route: List<String>): Exception {
    if (error is java.util.concurrent.CancellationException) return error
    val dns = sourceDnsFailureV55(error) ?: return error
    return SourceDnsBlockedV54(dns.reason, dns.blockedHost, route).apply { initCause(error) }
}

/** RFC 2544 benchmark range. Proxy apps in Fake-IP/TUN mode (Clash, mihomo, sing-box) answer DNS with it and route the connection themselves. */
internal fun sourceBenchmarkAddressV54(address: InetAddress): Boolean {
    val bytes=address.address
    return bytes.size==4 && (bytes[0].toInt() and 255)==198 && (bytes[1].toInt() and 255) in 18..19
}

/**
 * Addresses a source request may connect to: public addresses plus 198.18.0.0/15 Fake-IP answers.
 * That range is never LAN, loopback or link-local, so SSRF protection for real private ranges is unchanged.
 */
internal fun connectableSourceAddressV54(address: InetAddress): Boolean =
    publicSourceAddressV36(address) || sourceBenchmarkAddressV54(address)

/** No fallback resolver or second lookup after validation; only the Fake-IP range is exempt from the public check. */
internal fun checkedSourceDnsV54(delegate: Dns = Dns.SYSTEM): Dns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        // Freeze a delegate's mutable list so it cannot change between checking and connecting.
        val addresses=delegate.lookup(hostname).toList()
        if (addresses.isEmpty()) throw SourceDnsBlockedV54(SourceDnsFailureV54.EMPTY, hostname)
        if (!addresses.all(::connectableSourceAddressV54)) throw SourceDnsBlockedV54(SourceDnsFailureV54.NON_PUBLIC, hostname)
        // OkHttp connects to this exact checked answer; its TLS hostname verification remains on.
        return addresses
    }
}
