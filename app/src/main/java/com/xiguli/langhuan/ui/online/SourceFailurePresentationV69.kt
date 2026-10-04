package com.xiguli.langhuan.ui

import java.net.SocketTimeoutException

internal const val SOURCE_TIMEOUT_MESSAGE_V69 = "书源请求超时，网站响应较慢；请稍后重试"

/** Converts transport failures into bounded, actionable copy before they enter persistent UI state. */
internal fun sourceFailureMessageV69(
    error: Throwable,
    fallback: String = "书源请求失败，请检查网络或规则",
): String {
    val causes = generateSequence(error) { it.cause }.take(12)
    if (causes.any { it is SocketTimeoutException }) return SOURCE_TIMEOUT_MESSAGE_V69
    return error.message
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.take(240)
        ?.takeIf(String::isNotBlank)
        ?: fallback
}
