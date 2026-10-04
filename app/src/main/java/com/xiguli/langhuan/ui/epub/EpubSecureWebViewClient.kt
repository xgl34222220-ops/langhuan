package com.xiguli.langhuan.ui.epub

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xiguli.langhuan.data.epub.EpubArchivePolicy
import com.xiguli.langhuan.data.epub.EpubWebContentPolicy
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Wraps the SDK's public Android client, after Readium's own HTML injection has completed. */
internal class EpubSecureWebViewClient(
    private val sdkClient: WebViewClient,
    private val assets: AssetManager,
    private val onRendererExit: (WebView) -> Boolean,
) : WebViewClient() {
    private val protectedDocumentServed = AtomicBoolean(false)
    private val rendererExited = AtomicBoolean(false)
    private var initialReloadRequested = false

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        if (rendererExited.get()) return denied()
        val url = request.url.toString()
        if (request.method != "GET") return denied()
        val path = EpubWebContentPolicy.packagePath(url) ?: return denied()
        if (path.startsWith(EpubWebContentPolicy.SDK_ALIAS_PATH)) {
            if (request.isForMainFrame) return denied()
            val asset = EpubWebContentPolicy.aliasedAsset(url) ?: return denied()
            return runCatching {
                val bytes = assets.open(asset).use { EpubArchivePolicy.readBounded(it, EpubArchivePolicy.MAX_ENTRY) }
                require(EpubWebContentPolicy.assetHashMatches(asset, bytes)) { "Readium 3.4 script checksum mismatch" }
                val type = when {
                    asset in EpubWebContentPolicy.scriptHashes -> "application/javascript"
                    asset in EpubWebContentPolicy.stylesheetPaths -> "text/css"
                    asset.endsWith(".ttf") -> "font/ttf"
                    else -> "font/otf"
                }
                response(type, bytes)
            }.getOrElse { denied() }
        }
        val original = sdkClient.shouldInterceptRequest(view, request) ?: return denied()
        val mime = original.mimeType.orEmpty().substringBefore(';').lowercase()
        if (mime != "application/xhtml+xml" && mime != "text/html") return original
        return runCatching {
            val bytes = original.data.use { EpubArchivePolicy.readBounded(it, 24L * 1024 * 1024) }
            val secured = EpubWebContentPolicy.secureFinalHtml(bytes, xhtml = mime == "application/xhtml+xml")
            // The HTTP-equivalent header protects even SDK styles prepended before a meta CSP.
            response(mime, secured).also {
                if (request.isForMainFrame) protectedDocumentServed.set(true)
            }
        }.getOrElse { denied() }
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (rendererExited.get()) return
        if (!protectedDocumentServed.get()) {
            // Readium calls loadUrl inside onCreateView, before our fragment callback can install
            // this wrapper. That initial document has the fail-closed CSP and cannot run SDK/book
            // scripts. Do not forward its finish event or let it emit a new Locator. Reload once
            // through this client, then forward only a successfully protected document.
            if (!initialReloadRequested && EpubWebContentPolicy.packagePath(url) != null) {
                initialReloadRequested = true
                view.loadUrl(url)
            }
            return
        }
        sdkClient.onPageFinished(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        if (rendererExited.get() || EpubWebContentPolicy.packagePath(request.url.toString()) == null) true
        else sdkClient.shouldOverrideUrlLoading(view, request)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (!rendererExited.get()) sdkClient.onPageStarted(view, url, favicon)
    }
    override fun onPageCommitVisible(view: WebView, url: String) {
        if (!rendererExited.get()) sdkClient.onPageCommitVisible(view, url)
    }
    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean = sdkClient.shouldOverrideKeyEvent(view, event)
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) = sdkClient.onReceivedError(view, request, error)
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) = sdkClient.onReceivedHttpError(view, request, errorResponse)
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        rendererExited.set(true)
        protectedDocumentServed.set(false)
        // The host retires the entire Navigator as one publication session. Readium's
        // onDetach owns destruction of its page WebViews. A late/stale callback still
        // disposes only its own affected view, without touching a replacement session.
        if (!onRendererExit(view)) {
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        return true
    }

    private fun response(mime: String, bytes: ByteArray) = WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf(
        "Content-Security-Policy" to EpubWebContentPolicy.CSP,
        "X-Content-Type-Options" to "nosniff",
        "Cache-Control" to "no-store",
    ), ByteArrayInputStream(bytes))

    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", mapOf(
        "Content-Security-Policy" to "default-src 'none'", "Cache-Control" to "no-store",
    ), ByteArrayInputStream(ByteArray(0)))
}
