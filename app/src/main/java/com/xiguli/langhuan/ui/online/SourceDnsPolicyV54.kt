package com.xiguli.langhuan.ui

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

internal enum class SourceDnsFailureV54 { EMPTY, BENCHMARK_RANGE, NON_PUBLIC }

/** Address evidence only: 198.18/15 may be proxy Fake-IP, but does not prove a proxy is present. */
internal class SourceDnsBlockedV54(
    val reason: SourceDnsFailureV54,
    hostname: String? = null,
    route: List<String> = emptyList(),
) : UnknownHostException(
    (sourceDiagnosticHostV55(hostname)?.let { "解析 $it 时：" } ?: "") + when (reason) {
        SourceDnsFailureV54.EMPTY -> "DNS 没有返回可连接的地址，请检查当前网络后重试"
        SourceDnsFailureV54.BENCHMARK_RANGE ->
            "DNS 返回了 198.18.0.0/15 保留地址，可能是代理的 Fake-IP 映射；尚未连接网站。" +
            "请在代理中为此网站使用真实 IP 解析后重试，无需关闭代理"
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

internal fun sourceBenchmarkAddressV54(address: InetAddress): Boolean {
    val bytes=address.address
    return bytes.size==4 && (bytes[0].toInt() and 255)==198 && (bytes[1].toInt() and 255) in 18..19
}

/** No fallback resolver, private-address exception or second lookup after validation. */
internal fun checkedSourceDnsV54(delegate: Dns = Dns.SYSTEM): Dns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        // Freeze a delegate's mutable list so it cannot change between checking and connecting.
        val addresses=delegate.lookup(hostname).toList()
        if (addresses.isEmpty()) throw SourceDnsBlockedV54(SourceDnsFailureV54.EMPTY, hostname)
        val blocked=addresses.filterNot(::publicSourceAddressV36)
        if (blocked.isNotEmpty()) {
            val reason=if (blocked.all(::sourceBenchmarkAddressV54)) SourceDnsFailureV54.BENCHMARK_RANGE
                else SourceDnsFailureV54.NON_PUBLIC
            throw SourceDnsBlockedV54(reason, hostname)
        }
        // OkHttp connects to this exact checked answer; its TLS hostname verification remains on.
        return addresses
    }
}
