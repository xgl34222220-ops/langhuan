package com.xiguli.langhuan.ui

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val BROWSER_FETCH = 1
private const val BROWSER_CANCEL = 2
private const val BROWSER_RESULT = 3
private const val BROWSER_SHOW = 4
private const val BROWSER_FIXTURE_RENDERER_EXIT = 5
private const val BROWSER_TIMEOUT_MS = 120_000L
private const val BROWSER_CACHE = "book-source-browser-v56"

/** Small IPC messages only. HTML stays in a bounded, app-private file, never in a Binder parcel. */
internal class SourceBrowserTransportV56(private val context: Context) {
    private class Pending {
        val done = CountDownLatch(1)
        @Volatile var result: Bundle? = null
    }
    private val handler = Handler(Looper.getMainLooper())
    private val gate = Semaphore(1, true)
    private val pending = ConcurrentHashMap<String, Pending>()
    @Volatile private var remote: Messenger? = null
    private var connection: ServiceConnection? = null
    private val idle = Runnable {
        if (pending.isEmpty()) {
            connection?.let { runCatching { context.unbindService(it) } }
            connection = null
            remote = null
        }
    }
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        val id = data.getString("id").orEmpty()
        val waiting = pending[id]
        if (waiting != null) when (message.what) {
            BROWSER_RESULT -> { waiting.result = data; waiting.done.countDown() }
            BROWSER_SHOW -> runCatching { context.startActivity(
                Intent(context, BookSourceBrowserActivityV56::class.java)
                    .putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) }.onFailure {
                waiting.result = Bundle().apply { putString("error", "网站需要网页验证，请回到琅嬛后重试") }
                waiting.done.countDown()
            }
        }
        true
    })

    private fun connected(): Messenger {
        remote?.let { return it }
        val ready = CountDownLatch(1)
        var failure: Exception? = null
        handler.post {
            handler.removeCallbacks(idle)
            remote?.let { ready.countDown(); return@post }
            val next = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    remote = Messenger(binder)
                    ready.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    remote = null
                    pending.forEach { (id, waiting) ->
                        waiting.result = Bundle().apply { putString("id", id); putString("error", "浏览器进程已结束，请重试") }
                        waiting.done.countDown()
                    }
                }
                override fun onNullBinding(name: ComponentName) {
                    failure = IOException("浏览器服务未能启动")
                    ready.countDown()
                }
            }
            // A cancelled bind can still finish later; retire it before starting another bind.
            connection?.let { runCatching { context.unbindService(it) } }
            connection = next
            try {
                if (!context.bindService(Intent(context, BookSourceBrowserServiceV56::class.java), next, Context.BIND_AUTO_CREATE)) {
                    failure = IOException("浏览器服务未能启动")
                    ready.countDown()
                }
            } catch (error: Exception) { failure = error; ready.countDown() }
        }
        if (!ready.await(15, TimeUnit.SECONDS)) throw IOException("浏览器启动超时")
        failure?.let { throw it }
        return remote ?: throw IOException("浏览器服务连接已断开")
    }

    fun document(source: BookSourceV36, request: SourceRequestV36): Document = read(source, request, null)

    /** Real renderer termination is confined to an active debug reserved-domain fixture. */
    internal fun terminateFixtureRendererV57() {
        check((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        val id = pending.keys.single()
        checkNotNull(remote).send(Message.obtain(null, BROWSER_FIXTURE_RENDERER_EXIT).apply {
            data = Bundle().apply { putString("id", id) }
        })
    }

    /** Controlled, inline device fixture; the exported UI can never select it. */
    internal fun fixtureDocument(source: BookSourceV36, request: SourceRequestV36, html: String): Document {
        check((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && publicSourceUrlV36(request.url).host == "browser-fixture.example")
        require(html.toByteArray().size <= 64 * 1024)
        return read(source, request, html)
    }

    private fun read(source: BookSourceV36, request: SourceRequestV36, fixture: String?): Document {
        check(Looper.myLooper() != Looper.getMainLooper()) { "网页读取必须在后台执行" }
        publicSourceUrlV36(request.url)
        SourceCooldownV46.check(publicSourceUrlV36(request.url))
        require(request.method.uppercase() in setOf("GET", "POST")) { "浏览器模式只支持 GET、POST" }
        require(request.body.orEmpty().length <= 64 * 1024) { "搜索请求过大" }
        require(source.headers.keys.all { it.lowercase() in setOf("cookie", "user-agent", "accept-language") }) {
            "这个书源含自定义凭据请求头，请使用普通模式读取"
        }
        val id = UUID.randomUUID().toString()
        val file = File(File(context.cacheDir, BROWSER_CACHE), "$id.html")
        var acquired = false
        var service: Messenger? = null
        try {
            gate.acquire()
            acquired = true
            val waiting = Pending()
            pending[id] = waiting
            handler.removeCallbacks(idle)
            service = connected()
            val data = Bundle().apply {
                putString("id", id); putString("url", request.url)
                putString("method", request.method.uppercase()); putString("body", request.body)
                putString("charset", request.charset); putString("base", source.baseUrl)
                val headers = sourceHeadersForUrlV36(source, publicSourceUrlV36(request.url))
                require(headers.values.sumOf(String::length) <= 12 * 1024) { "浏览器请求头过大" }
                putString("cookie", headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value)
                putString("ua", headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value)
                if (fixture != null) putString("fixture", fixture)
            }
            service.send(Message.obtain(null, BROWSER_FETCH).apply { this.data = data; replyTo = reply })
            if (!waiting.done.await(BROWSER_TIMEOUT_MS + 10_000, TimeUnit.MILLISECONDS)) throw IOException("网页验证或加载超时，请重试")
            val result = waiting.result ?: throw IOException("浏览器没有返回页面")
            result.getString("error")?.let { message ->
                if (result.getBoolean("cancelled")) throw CancellationException(message)
                val code = result.getInt("status")
                if (code > 0) {
                    val error = SourceHttpStatusExceptionV44(code, result.getString("origin").orEmpty(), message, result.getLong("retryAfter").takeIf { it > 0 })
                    SourceCooldownV46.record(error)
                    throw error
                }
                throw IOException(message)
            }
            val url = publicSourceUrlV36(result.getString("url") ?: throw IOException("浏览器没有返回最终网址")).toString()
            val bytes = file.inputStream().use { readSourceBytesV36(it, MAX_SOURCE_BYTES_V36) }
            return parseSourceDocumentV44(bytes, url, "UTF-8")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("网页读取已取消").apply { initCause(error) }
        } finally {
            pending.remove(id)
            runCatching { service?.send(Message.obtain(null, BROWSER_CANCEL).apply { data = Bundle().apply { putString("id", id) } }) }
            file.delete()
            if (acquired) gate.release()
            handler.postDelayed(idle, 15_000)
        }
    }
}

/** Pure readiness check used by the renderer and deterministic tests. */
internal fun browserDocumentReadyV56(html: String): Boolean {
    if (browserChallengePendingV38(html)) return false
    val doc = Jsoup.parse(html)
    doc.select("script, style, template, [hidden], [aria-hidden=true]").remove()
    val text = doc.body().text().trim()
    val loading = Regex("(?:loading|加载中|載入中|正在加载)[.!…\\s]*", RegexOption.IGNORE_CASE)
    // A site header can already be stable while its result/body container is still loading.
    // Match standalone visible indicators; prose quoting these words remains readable.
    if (doc.selectFirst("[aria-busy=true], [role=progressbar], progress:not([value])") != null ||
        doc.body().allElements.any { loading.matches(it.ownText().trim()) }) return false
    return text.isNotEmpty() || doc.selectFirst("form input:not([type=hidden]), a[href], img[src]") != null
}

internal fun browserHttpsUpgradeV56(status: Int, method: String, url: String, html: String): String? {
    if (status != 400 || method != "GET" || publicSourceUrlV36(url).isHttps) return null
    val text = Jsoup.parse(html.take(MAX_SOURCE_ERROR_BYTES_V44)).text().lowercase()
    if (listOf("plain http request was sent to https port", "this combination of host and port requires tls", "the http request was sent to https port").none(text::contains)) return null
    return httpsFallbackUrlV36(url)
}

/** A dedicated profile keeps book-site cookies and JavaScript away from EPUBs and app credentials.
 * There is no JavaScript bridge, source-rule evaluator, file picker, download, or native permission.
 */
class BookSourceBrowserServiceV56 : Service() {
    private class Task(val id: String, val reply: Messenger, val request: SourceRequestV36, val fixture: String? = null) {
        var navigation = 0
        var status = 200
        var retryAfter: Long? = null
        var html: String? = null
        var stable = 0
        var shown = false
        var upgraded = false
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val checkedHosts = ConcurrentHashMap<String, Long>()
    private val writes = ConcurrentHashMap<String, AtomicBoolean>()
    @Volatile private var task: Task? = null
    @Volatile internal var webView: WebView? = null
        private set
    private val timeout = Runnable { finishError("网页尚未完成验证或加载，请完成验证后重试") }
    private val poll = Runnable { capture() }
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            BROWSER_FETCH -> begin(message)
            BROWSER_CANCEL -> {
                val id = message.data.getString("id").orEmpty()
                if (task?.id == id) cancel()
                writes[id]?.set(true)
                if (id.matches(Regex("[a-f0-9-]{36}"))) File(File(cacheDir, BROWSER_CACHE), "$id.html").delete()
            }
            BROWSER_FIXTURE_RENDERER_EXIT -> {
                val currentTask = task
                if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                    currentTask != null && currentTask.id == message.data.getString("id") && currentTask.fixture != null &&
                    publicSourceUrlV36(currentTask.request.url).host == "browser-fixture.example") {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || webView?.webViewRenderProcess?.terminate() != true)
                        finishError("合成样例的渲染器未能终止，测试未完成")
                }
            }
        }
        true
    })

    override fun onCreate() {
        super.onCreate()
        current = this
        File(cacheDir, BROWSER_CACHE).apply { mkdirs() }.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 300_000 }?.forEach { it.delete() }
    }
    override fun onBind(intent: Intent?): IBinder = incoming.binder
    internal fun active(id: String): Boolean = task?.id == id
    internal fun cancel() = finishError("已取消网页验证", cancelled = true)

    private fun begin(message: Message) {
        val data = message.data
        val id = data.getString("id").orEmpty()
        val reply = message.replyTo ?: return
        try {
            require(id.matches(Regex("[a-f0-9-]{36}"))) { "浏览器请求无效" }
            check(task == null) { "浏览器正在读取其他页面" }
            val url = publicSourceUrlV36(data.getString("url").orEmpty()).toString()
            val request = SourceRequestV36(url, data.getString("method") ?: "GET", data.getString("body"), data.getString("charset"))
            require(request.method in setOf("GET", "POST") && request.body.orEmpty().length <= 64 * 1024)
            request.charset?.let { Charset.forName(it) }
            if (request.method == "POST") require(sameSourceOriginV36(publicSourceUrlV36(data.getString("base").orEmpty()), publicSourceUrlV36(url))) { "不能跨站提交搜索表单" }
            val fixture = data.getString("fixture")
            if (fixture != null) check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && publicSourceUrlV36(url).host == "browser-fixture.example" && fixture.toByteArray().size <= 64 * 1024)
            task = Task(id, reply, request, fixture)
            val view = webView ?: createView().also { webView = it }
            view.onResume()
            view.resumeTimers()
            view.settings.userAgentString = data.getString("ua")?.takeIf { it.length <= 1024 } ?: WebSettings.getDefaultUserAgent(this)
            mainHandler.postDelayed(timeout, BROWSER_TIMEOUT_MS)
            fun load() {
                if (!active(id)) return
                if (request.method == "POST") view.postUrl(url, request.body.orEmpty().toByteArray(Charset.forName(request.charset ?: "UTF-8")))
                else view.loadUrl(url)
            }
            val cookies = data.getString("cookie").orEmpty().split(';').map(String::trim).filter { it.contains('=') }
            if (cookies.isEmpty()) load() else {
                require(cookies.size <= 100 && cookies.sumOf(String::length) <= 8192) { "书源 Cookie 过大" }
                var remaining = cookies.size
                cookies.forEach { CookieManager.getInstance().setCookie(url, "$it; Path=/") { if (--remaining == 0) load() } }
            }
        } catch (error: Exception) {
            if (active(id)) finishError(error.message ?: "浏览器启动失败")
            else send(reply, BROWSER_RESULT, Bundle().apply { putString("id", id); putString("error", error.message?.take(500) ?: "浏览器请求失败") })
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setGeolocationEnabled(false)
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.safeBrowsingEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.allowFileAccess = false
            serviceWorkerWebSettings.allowContentAccess = false
            serviceWorkerWebSettings.blockNetworkLoads = true
            setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = blocked()
            })
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
            override fun onCreateWindow(view: WebView, dialog: Boolean, gesture: Boolean, result: Message): Boolean = false
        }
        setDownloadListener { _, _, _, _, _ -> finishError("这个链接返回下载文件，未取得小说网页") }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = try {
                if (webView !== view) true else {
                    val next = publicSourceUrlV36(request.url.toString())
                    val previous = view.url?.let { runCatching { publicSourceUrlV36(it) }.getOrNull() }
                    require(previous == null || !previous.isHttps || next.isHttps) { "不能降级到 HTTP" }
                    task?.request?.let { initial ->
                        require(initial.method != "POST" || request.method != "POST" || sameSourceOriginV36(publicSourceUrlV36(initial.url), next)) { "不能跨站转发搜索表单" }
                    }
                    false
                }
            } catch (_: Exception) { if (request.isForMainFrame) finishError("网页跳转到了不支持的地址"); true }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (webView !== view) return blocked()
                val currentTask = task ?: return blocked()
                val id = currentTask.id
                // The debug-only reserved-domain fixture follows normal loadUrl/postUrl and
                // supplies just its main document. No fixture resource reaches the network.
                currentTask.fixture?.let { html ->
                    return if (request.isForMainFrame && request.url.toString() == currentTask.request.url)
                        WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
                    else blocked()
                }
                return try {
                    val url = publicSourceUrlV36(request.url.toString())
                    val now = System.currentTimeMillis()
                    if ((checkedHosts[url.host] ?: 0) < now) {
                        check(checkedHosts.size < 128 || checkedHosts.containsKey(url.host)) { "网页附属域名过多，未继续加载" }
                        checkedSourceDnsV54().lookup(url.host)
                        checkedHosts[url.host] = now + 30_000
                    }
                    null
                } catch (error: Exception) {
                    if (request.isForMainFrame) mainHandler.post { if (active(id)) finishError(error.message?.take(500) ?: "网页地址无法读取") }
                    blocked()
                }
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (webView !== view) return
                task?.let { it.navigation++; it.status = 200; it.retryAfter = null; it.html = null; it.stable = 0 }
                mainHandler.removeCallbacks(poll)
            }
            override fun onPageFinished(view: WebView, url: String) { if (webView === view && task != null) { mainHandler.removeCallbacks(poll); mainHandler.postDelayed(poll, 750) } }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (webView === view && request.isForMainFrame) finishError("网页加载失败，请检查网络（${error.errorCode}）")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (webView === view && request.isForMainFrame) task?.let {
                    it.status = response.statusCode
                    it.retryAfter = sourceRetryAfterMillisV46(response.responseHeaders?.entries?.firstOrNull { header -> header.key.equals("Retry-After", true) }?.value)
                    if (it.status == 429) finishError("网站限制了请求频率（HTTP 429），请稍后重试", status = 429)
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (webView === view) finishError("网站证书验证失败，未继续读取")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // A dead WebView must never receive stopLoading/onPause or be reused. Retire
                // only this instance before releasing the waiter and closing verification UI.
                val wasCurrent = webView === view
                if (wasCurrent) webView = null
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                if (wasCurrent) finishError("网页渲染进程已结束，请重试；已保存的书源和阅读进度保留")
                return true
            }
        }
        // Detached requests still get a phone-sized viewport for responsive layouts.
        layout(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        onResume()
    }

    private fun capture() {
        val currentTask = task ?: return
        val view = webView ?: return
        val navigation = currentTask.navigation
        view.evaluateJavascript("(function(){var h=document.documentElement?document.documentElement.outerHTML:'';return h.length<=4194304?h:null;})()") { value ->
            if (task !== currentTask || webView !== view || navigation != currentTask.navigation) return@evaluateJavascript
            try {
                val primitive = BookSourceJsonV36.parseToJsonElement(value) as? JsonPrimitive
                val html = primitive?.takeIf { it.isString }?.content ?: throw IOException("网页内容超过大小限制或尚未可读")
                val challenge = browserChallengePendingV38(html)
                if (challenge && !currentTask.shown) {
                    currentTask.shown = true
                    send(currentTask.reply, BROWSER_SHOW, Bundle().apply { putString("id", currentTask.id) })
                }
                if (currentTask.status >= 400 && !challenge) {
                    val url = publicSourceUrlV36(view.url ?: currentTask.request.url).toString()
                    val upgrade = if (currentTask.upgraded) null else browserHttpsUpgradeV56(currentTask.status, currentTask.request.method, url, html)
                    if (upgrade != null) {
                        currentTask.upgraded = true
                        view.loadUrl(upgrade)
                        return@evaluateJavascript
                    }
                    val failure = sourceHttpFailureV44(currentTask.status, publicSourceUrlV36(url), html.take(MAX_SOURCE_ERROR_BYTES_V44).toByteArray())
                    finishError(failure.detail, status = currentTask.status)
                    return@evaluateJavascript
                }
                currentTask.stable = if (currentTask.html == html && browserDocumentReadyV56(html)) currentTask.stable + 1 else 0
                currentTask.html = html
                if (currentTask.stable >= 2) {
                    val url = publicSourceUrlV36(view.url ?: currentTask.request.url).toString()
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    require(bytes.size <= MAX_SOURCE_BYTES_V36) { "网页内容超过大小限制" }
                    CookieManager.getInstance().flush()
                    val cancelledWrite = AtomicBoolean(false)
                    writes[currentTask.id] = cancelledWrite
                    detachTask()
                    writer.execute {
                        val file = File(File(cacheDir, BROWSER_CACHE), "${currentTask.id}.html")
                        try {
                            if (cancelledWrite.get()) return@execute
                            file.outputStream().use { it.write(bytes) }
                            if (cancelledWrite.get()) file.delete()
                            else send(currentTask.reply, BROWSER_RESULT, Bundle().apply { putString("id", currentTask.id); putString("url", url) })
                        } catch (_: Exception) {
                            file.delete()
                            send(currentTask.reply, BROWSER_RESULT, Bundle().apply { putString("id", currentTask.id); putString("error", "浏览器页面暂存失败") })
                        } finally { writes.remove(currentTask.id) }
                    }
                } else mainHandler.postDelayed(poll, 750)
            } catch (error: Exception) { finishError(error.message?.take(500) ?: "浏览器页面读取失败") }
        }
    }

    private fun detachTask() {
        task = null
        mainHandler.removeCallbacks(timeout); mainHandler.removeCallbacks(poll)
        webView?.stopLoading()
        webView?.onPause()
        webView?.pauseTimers()
        BookSourceBrowserActivityV56.current?.finish()
    }
    private fun finishError(message: String, cancelled: Boolean = false, status: Int = 0) {
        val currentTask = task ?: return
        detachTask()
        send(currentTask.reply, BROWSER_RESULT, Bundle().apply {
            putString("id", currentTask.id); putString("error", message.take(500)); putBoolean("cancelled", cancelled)
            val url = runCatching { publicSourceUrlV36(webView?.url ?: currentTask.request.url) }.getOrElse { publicSourceUrlV36(currentTask.request.url) }
            putInt("status", status); putString("origin", sourceOriginV46(url))
            currentTask.retryAfter?.let { putLong("retryAfter", it) }
        })
    }
    override fun onDestroy() {
        finishError("浏览器进程已结束，请重试")
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        webView = null
        if (current === this) current = null
        writer.shutdown()
        super.onDestroy()
    }
    companion object {
        @Volatile private var currentReference: WeakReference<BookSourceBrowserServiceV56>? = null
        internal var current: BookSourceBrowserServiceV56?
            get() = currentReference?.get()
            set(value) { currentReference = value?.let(::WeakReference) }
        private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        private fun send(reply: Messenger, what: Int, data: Bundle) { runCatching { reply.send(Message.obtain(null, what).apply { this.data = data }) } }
    }
}

/** Only appears when the current site actually asks for a human browser verification. */
class BookSourceBrowserActivityV56 : ComponentActivity() {
    private var id = ""
    private var attachedView: WebView? = null
    private var container: ViewGroup? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra("id").orEmpty()
        val service = BookSourceBrowserServiceV56.current
        val view = service?.webView
        if (service == null || view == null || !service.active(id)) { finish(); return }
        current = this
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (service.active(id)) service.cancel(); finish() }
        })
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xfff7f5f0.toInt())
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            root.setPadding(pad, pad + insets.systemWindowInsetTop, pad, pad + insets.systemWindowInsetBottom)
            insets
        }
        root.addView(TextView(this).apply {
            text = "完成网站验证后，琅嬛会自动继续读取。"
            textSize = 16f
            setTextColor(0xff22221f.toInt())
            setPadding(0, pad, 0, pad)
        })
        root.addView(Button(this).apply { text = "取消验证"; setOnClickListener { if (service.active(this@BookSourceBrowserActivityV56.id)) service.cancel(); finish() } })
        (view.parent as? ViewGroup)?.removeView(view)
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        attachedView = view
        container = root
        setContentView(root)
    }
    override fun onDestroy() {
        attachedView?.let { if (it.parent === container) container?.removeView(it) }
        attachedView = null
        container = null
        if (current === this) current = null
        super.onDestroy()
    }
    companion object {
        @Volatile private var currentReference: WeakReference<BookSourceBrowserActivityV56>? = null
        internal var current: BookSourceBrowserActivityV56?
            get() = currentReference?.get()
            set(value) { currentReference = value?.let(::WeakReference) }
    }
}
