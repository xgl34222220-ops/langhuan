package com.xiguli.langhuan.ui

import android.content.Context
import org.jsoup.Jsoup

/** Browser rendering is explicitly selected and confined to its own process and WebView profile. */
internal object BookSourceBrowserV38 {
    @Volatile private var transport: SourceBrowserTransportV56? = null
    fun install(context: Context) {
        if (transport == null) synchronized(this) {
            if (transport == null) transport = SourceBrowserTransportV56(context.applicationContext)
        }
    }
    fun document(source: BookSourceV36, request: SourceRequestV36): org.jsoup.nodes.Document =
        (transport ?: error("浏览器会话尚未初始化")).document(source, request)
}

internal fun browserChallengePendingV38(html: String, mitigationHeader: String? = null): Boolean {
    // Cloudflare documents this header as the signal for an actual Challenge Page. Its normal
    // HTML responses can also contain /cdn-cgi/challenge-platform/ JavaScript Detections scripts;
    // those scripts, loading copy, and words quoted in a novel are not evidence of a blocked page.
    if (mitigationHeader?.trim().equals("challenge", ignoreCase = true)) return true
    val doc = Jsoup.parse(html.take(200_000))
    val title = doc.title().trim().lowercase()
    val challengeStructure = doc.selectFirst(
        "form#challenge-form, #challenge-stage, #cf-challenge-running, #cf-chl-widget, #challenge-running, #cf-error-details",
    ) != null
    val challengeScript = doc.select("script[src]").any {
        val path = it.attr("src").lowercase()
        path.contains("/cdn-cgi/challenge-platform/") && path.contains("/orchestrate/")
    }
    doc.select("script, style, template, [hidden], [aria-hidden=true]").remove()
    val text = doc.body().text().trim().lowercase()
    val verificationPhrases = listOf(
        "checking your browser", "verify you are human", "verifying you are human",
        "正在检查您的浏览器", "正在验证您的浏览器", "正在檢查您的瀏覽器", "正在驗證您的瀏覽器",
        "enable javascript and cookies to continue",
    )
    val verificationHeading = doc.select("h1, h2, [role=heading]").any { heading ->
        verificationPhrases.any { heading.text().trim().lowercase().startsWith(it) }
    }
    val verificationPage = text.length <= 3000 && verificationPhrases.any(text::startsWith)
    val challengeTitle = title.matches(Regex("just a moment[.!…\\s]*")) ||
        title.startsWith("checking your browser") ||
        (title.startsWith("attention required") && title.contains("cloudflare"))
    return verificationHeading || verificationPage ||
        (challengeTitle && (challengeStructure || challengeScript || verificationPhrases.any(text::contains)))
}
