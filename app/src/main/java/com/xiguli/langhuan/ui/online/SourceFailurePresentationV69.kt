package com.xiguli.langhuan.ui

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

internal const val SOURCE_TIMEOUT_MESSAGE_V69 = "书源请求超时，网站响应较慢；请稍后重试"
internal const val SOURCE_TLS_MESSAGE_V70 = "网站安全连接验证失败；请检查网址和系统时间，确认无误后再重试"
internal const val SOURCE_DNS_LOOKUP_MESSAGE_V71 = "无法解析网站地址；请检查网址、网络或私人 DNS 设置后重试"

/** Converts transport failures into bounded, actionable copy before they enter persistent UI state. */
internal fun sourceFailureMessageV69(
    error: Throwable,
    fallback: String = "书源请求失败，请检查网络或规则",
): String {
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    if (causes.any { it is SocketTimeoutException }) return SOURCE_TIMEOUT_MESSAGE_V69
    if (causes.any { it is SSLException || it is CertificateException }) return SOURCE_TLS_MESSAGE_V70
    causes.filterIsInstance<SourceDnsBlockedV54>().firstOrNull()?.message?.let { return it }
    if (causes.any { it is UnknownHostException }) return SOURCE_DNS_LOOKUP_MESSAGE_V71
    return error.message
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.take(240)
        ?.takeIf(String::isNotBlank)
        ?: fallback
}
