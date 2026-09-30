package com.xiguli.langhuan.ui

import android.content.Context

/** Kept for application startup compatibility. Source fetches never create a WebView.
 * Dynamic sites require an explicit, isolated browser design before they can be supported safely.
 */
internal object BookSourceBrowserV38 {
    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context) = Unit
}

internal fun browserChallengePendingV38(html: String): Boolean {
    val sample = html.take(200_000).lowercase()
    return listOf(
        "just a moment",
        "checking your browser",
        "cf-chl-",
        "challenge-platform",
        "正在检查您的浏览器",
        "请稍候",
    ).any(sample::contains)
}
