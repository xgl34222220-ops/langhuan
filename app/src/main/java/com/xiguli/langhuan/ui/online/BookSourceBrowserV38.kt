package com.xiguli.langhuan.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.view.ContextThemeWrapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.json.Json

/**
 * Browser-backed fetcher for sites that reject HttpURLConnection (403/WAF/browser checks).
 *
 * The normal source engine stays on HttpURLConnection for speed. This path is entered only after
 * direct requests fail. WebView executes redirects/JavaScript and keeps the platform WebView cookie
 * jar, so later search/detail/chapter pages share the same browser session.
 */
internal object BookSourceBrowserV38 {
    @Volatile private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val lock = ReentrantLock()
    private val json = Json { ignoreUnknownKeys = true }

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun fetchDocument(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 35_000L,
    ): org.jsoup.nodes.Document = lock.withLock {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "浏览器书源抓取不能阻塞主线程"
        }
        val context = appContext ?: error("浏览器书源会话尚未初始化")
        val latch = CountDownLatch(1)
        var result: BrowserPageV38? = null
        var failure: Throwable? = null
        var webViewRef: WebView? = null

        handler.post {
            val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
            val webView = WebView(themed)
            webViewRef = webView
            val startedAt = System.currentTimeMillis()
            var captureGeneration = 0
            var lastHttpError: String? = null

            fun finish(page: BrowserPageV38?, error: Throwable?) {
                if (latch.count == 0L) return
                result = page
                failure = error
                latch.countDown()
                handler.post { runCatching { webView.stopLoading(); webView.loadUrl("about:blank"); webView.destroy() } }
            }

            fun captureWhenSettled(delayMs: Long = 900L) {
                val generation = ++captureGeneration
                handler.postDelayed({
                    if (generation != captureGeneration || latch.count == 0L) return@postDelayed
                    captureHtmlV38(webView) { html ->
                        if (latch.count == 0L) return@captureHtmlV38
                        val finalUrl = webView.url.orEmpty().takeIf { it.startsWith("http") } ?: url
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (browserChallengePendingV38(html) && elapsed < 15_000L) {
                            captureWhenSettled(1_200L)
                            return@captureHtmlV38
                        }
                        if (html.isBlank()) {
                            finish(null, IllegalStateException(lastHttpError ?: "浏览器没有返回页面内容"))
                            return@captureHtmlV38
                        }
                        val cookie = CookieManager.getInstance().getCookie(finalUrl).orEmpty()
                        val ua = webView.settings.userAgentString.orEmpty()
                        finish(BrowserPageV38(finalUrl, html, cookie, ua), null)
                    }
                }, delayMs)
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    super.onPageFinished(view, finishedUrl)
                    captureWhenSettled()
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame) {
                        lastHttpError = "浏览器返回 ${errorResponse.statusCode}"
                        // Do not fail immediately: challenge pages can respond before JS redirects.
                        captureWhenSettled(1_400L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    lastHttpError = "浏览器加载失败：$description"
                    captureWhenSettled(900L)
                }
            }

            val browserHeaders = buildMap {
                put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                headers.forEach { (k, v) ->
                    if (!k.equals("Cookie", true) && !k.equals("User-Agent", true)) put(k, v)
                }
            }
            runCatching { webView.loadUrl(url, browserHeaders) }
                .onFailure { finish(null, it) }
        }

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            handler.post { runCatching { webViewRef?.stopLoading(); webViewRef?.destroy() } }
            error("浏览器模式加载超时")
        }
        failure?.let { throw it }
        val page = result ?: error("浏览器没有返回页面")
        org.jsoup.Jsoup.parse(page.html, page.finalUrl)
    }

    private fun captureHtmlV38(webView: WebView, done: (String) -> Unit) {
        webView.evaluateJavascript("(document.documentElement && document.documentElement.outerHTML ? document.documentElement.outerHTML.length : 0)") { rawLength ->
            val length = rawLength.trim().trim('"').toIntOrNull() ?: 0
            if (length <= 0) {
                done("")
                return@evaluateJavascript
            }
            val out = StringBuilder(length.coerceAtMost(2_000_000))
            fun read(start: Int) {
                if (start >= length) {
                    done(out.toString())
                    return
                }
                val end = (start + 48_000).coerceAtMost(length)
                webView.evaluateJavascript(
                    "(document.documentElement.outerHTML).slice($start,$end)",
                ) { raw ->
                    val chunk = runCatching { json.decodeFromString<String>(raw) }.getOrDefault("")
                    out.append(chunk)
                    read(end)
                }
            }
            read(0)
        }
    }
}

internal data class BrowserPageV38(
    val finalUrl: String,
    val html: String,
    val cookie: String,
    val userAgent: String,
)

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
