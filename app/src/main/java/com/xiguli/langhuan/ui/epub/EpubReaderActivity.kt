@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.xiguli.langhuan.ui.epub

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.xiguli.langhuan.data.epub.EpubOriginalStore
import com.xiguli.langhuan.data.epub.EpubPublicationSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.readium.r2.shared.publication.Layout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

/** Non-exported reader. Own restoration reconstructs the SDK only after its Publication exists. */
class EpubReaderActivity : FragmentActivity() {
    companion object {
        const val EXTRA_BOOK_ID = "epub_book_id"
        const val RESULT_TEXT_READER = 71
        private const val HOST_ID = 0x51ee010
        private const val NAVIGATOR_TAG = "epub_original_navigator"
        private const val PICK_ORIGINAL = 721
    }
    private lateinit var bookId: String
    private lateinit var store: EpubOriginalStore
    private lateinit var status: TextView
    private lateinit var heading: TextView
    private lateinit var originalButton: Button
    private var publication: Publication? = null
    private var prepared: EpubOriginalStore.Prepared? = null
    private var navigator: EpubNavigatorFragment? = null
    private var openJob: Job? = null
    private var locatorJob: Job? = null
    private var savedLocator: String? = null
    private var savedDigest: String? = null
    private var loaded = false
    private var restoreTarget: Locator? = null
    private var restoreJob: Job? = null
    private class AssociationCandidate(val staged: EpubOriginalStore.Staged, val opened: Publication) : java.io.Closeable {
        override fun close() {
            try { opened.close() } finally { staged.close() }
        }
    }
    private var associationCandidate: AssociationCandidate? = null
    private var associationDialog: AlertDialog? = null
    private val locatorLock = Any()
    private var locatorSequence = 0L
    private var progressSaveWarningShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The framework cannot instantiate an EPUB fragment until the asynchronously opened
        // Publication/FragmentFactory exists. Rebuild the view tree from our saved Locator instead.
        super.onCreate(null)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        if (bookId.isBlank() || bookId.length > 512) { finish(); return }
        savedLocator = savedInstanceState?.getString("epub_locator")
        savedDigest = savedInstanceState?.getString("epub_digest")
        store = EpubReaderEntry.store(this)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, view: View, state: Bundle?) {
                fun protect(current: View) {
                    if (current is WebView && current.webViewClient !is EpubSecureWebViewClient) {
                        current.webViewClient = EpubSecureWebViewClient(current.webViewClient, assets)
                    } else if (current is ViewGroup) {
                        for (index in 0 until current.childCount) protect(current.getChildAt(index))
                    }
                }
                protect(view)
            }
        }, true)
        buildChrome()
        if (store.hasOriginal(bookId)) openStored()
        else status.text = "这本旧书只保存了文字。重新关联原 EPUB 后可阅读插画和作者排版；现有文字和阅读进度会保留。"
    }

    private fun buildChrome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(250, 247, 239))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("返回") { finish() })
        heading = TextView(this).apply { text = "EPUB 原版"; textSize = 18f; maxLines = 1 }
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("目录") { showContents() })
        root.addView(header)
        val host = FrameLayout(this)
        host.addView(FrameLayout(this).apply { id = HOST_ID }, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            text = "正在打开 EPUB 原版…"; textSize = 17f; gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(250, 247, 239))
            isClickable = true; isFocusable = true
        }
        host.addView(status, FrameLayout.LayoutParams(-1, -1))
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(button("上一页") { if (loaded) navigator?.goBackward(animated = true) })
        controls.addView(button("下一页") { if (loaded) navigator?.goForward(animated = true) })
        controls.addView(button("文字版") {
            setResult(RESULT_TEXT_READER, Intent().putExtra(EXTRA_BOOK_ID, bookId)); finish()
        })
        originalButton = button("关联原文件") { pickOriginal() }
        controls.addView(originalButton)
        root.addView(controls)
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; minWidth = 0; minimumWidth = 0
        setPadding(10, 8, 10, 8); setOnClickListener { action() }
    }

    @Suppress("DEPRECATION")
    private fun pickOriginal() {
        // A stable request code survives our deliberate Activity/Fragment state reconstruction.
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/epub+zip", "application/octet-stream", "application/zip"))
        }, PICK_ORIGINAL)
    }

    @Deprecated("Uses a stable request code for process-recreated original-file association")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_ORIGINAL && resultCode == RESULT_OK) data?.data?.let(::prepareAssociation)
    }

    private fun openStored() {
        openJob?.cancel()
        openJob = lifecycleScope.launch {
            originalButton.isEnabled = false
            var pendingPublication: Publication? = null
            try {
                val pair = withContext(Dispatchers.IO) {
                    val scope = currentCoroutineContext()
                    val book = store.open(bookId) { scope.ensureActive() }
                    val opened = EpubPublicationSession.open(applicationContext, book)
                    pendingPublication = opened
                    book to opened
                }
                // Opening may finish after the screen was backgrounded or state was saved.
                // Keep the opened publication owned here until a resumed window can attach it.
                lifecycle.withResumed { attach(pair.first, pair.second) }
                pendingPublication = null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error.message ?: "无法打开 EPUB")
            } finally { pendingPublication?.close(); originalButton.isEnabled = true }
        }
    }

    private fun prepareAssociation(uri: Uri) {
        if (openJob?.isActive == true || associationCandidate != null) return
        openJob = lifecycleScope.launch {
            originalButton.isEnabled = false
            var staged: EpubOriginalStore.Staged? = null
            var pending: AssociationCandidate? = null
            try {
                val candidate = withContext(Dispatchers.IO) {
                    val scope = currentCoroutineContext()
                    val owned = contentResolver.openInputStream(uri)?.use { input ->
                        store.stage(input) { scope.ensureActive() }
                    } ?: error("无法读取所选文件")
                    staged = owned
                    val opened = EpubPublicationSession.open(applicationContext, owned.prepared)
                    AssociationCandidate(owned, opened).also { pending = it }
                }
                lifecycle.withResumed {
                    associationCandidate = candidate
                    associationDialog = AlertDialog.Builder(this@EpubReaderActivity)
                        .setTitle("关联《${candidate.staged.prepared.archive.title.ifBlank { "未命名 EPUB" }}》？")
                        .setMessage("请确认这是当前书籍的原文件。插画原版使用独立进度；已有文字、书签和文字版进度会保留。不同原文件的原版进度也分别保存。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("关联并阅读") { _, _ ->
                            // Transfer ownership before the dialog dismiss callback can clean it.
                            associationCandidate = null
                            adoptAssociation(candidate)
                        }
                        .setOnDismissListener {
                            closeAssociationCandidate()
                            associationDialog = null
                            if (openJob?.isActive != true) originalButton.isEnabled = true
                        }.show()
                    pending = null
                    staged = null
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(this@EpubReaderActivity, error.message ?: "无法关联 EPUB", Toast.LENGTH_LONG).show()
            } finally {
                if (pending != null) runCatching { pending?.close() } else runCatching { staged?.close() }
                originalButton.isEnabled = associationCandidate == null
            }
        }
    }

    private fun adoptAssociation(candidate: AssociationCandidate) {
        originalButton.isEnabled = false
        openJob = lifecycleScope.launch {
            var published = false
            var pendingPublication: Publication? = null
            try {
                val pair = withContext(Dispatchers.IO) {
                    val scope = currentCoroutineContext()
                    val canonical = store.associate(bookId, candidate.staged.prepared) { scope.ensureActive() }
                    published = true
                    // The reader must use canonical files, never files owned by the closed dialog.
                    val opened = EpubPublicationSession.open(applicationContext, canonical)
                    pendingPublication = opened
                    canonical to opened
                }
                lifecycle.withResumed {
                    savedLocator = null; savedDigest = null
                    attach(pair.first, pair.second)
                    pendingPublication = null
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(this@EpubReaderActivity,
                    if (published) "原文件已关联，重新打开书籍即可继续阅读" else "关联失败，原书未更改",
                    Toast.LENGTH_LONG).show()
            } finally {
                pendingPublication?.close()
                runCatching { candidate.close() }.onFailure { warnStagingCleanup() }
                originalButton.isEnabled = true
            }
        }
    }

    private fun closeAssociationCandidate() {
        val owned = associationCandidate ?: return
        associationCandidate = null
        runCatching { owned.close() }.onFailure { warnStagingCleanup() }
    }

    private fun warnStagingCleanup() {
        Toast.makeText(this, "暂存文件清理未完成，请检查存储空间", Toast.LENGTH_LONG).show()
    }

    private fun attach(book: EpubOriginalStore.Prepared, opened: Publication) {
        saveCurrentLocator()
        locatorJob?.cancel()
        restoreJob?.cancel(); restoreJob = null
        navigator?.let { supportFragmentManager.beginTransaction().remove(it).commitNow() }
        publication?.close()
        prepared = book; publication = opened; loaded = false
        val initialJson = savedLocator?.takeIf { savedDigest == book.sha256 } ?: store.loadLocator(bookId, book.sha256)
        val initial = runCatching { initialJson?.let { Locator.fromJSON(JSONObject(it)) } }.getOrNull()
            ?.takeIf { locator -> opened.readingOrder.any { it.url().removeFragment() == locator.href.removeFragment() } }
        restoreTarget = initial?.takeIf { opened.metadata.layout != Layout.FIXED }
        status.setOnClickListener(null); status.isClickable = true
        status.text = if (restoreTarget != null) "正在恢复原版阅读位置…" else "正在打开 EPUB 原版…"
        status.visibility = View.VISIBLE
        val factory = EpubNavigatorFactory(opened).createFragmentFactory(
            initialLocator = initial,
            initialPreferences = EpubPreferences(publisherStyles = true),
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    Toast.makeText(this@EpubReaderActivity, "原版阅读不会打开书内外部链接", Toast.LENGTH_SHORT).show()
                }
            },
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageLoaded() {
                    if (restoreTarget == null) { loaded = true; status.visibility = View.GONE }
                    else restorePendingPosition()
                }
            },
        )
        supportFragmentManager.fragmentFactory = factory
        val fragment = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
        navigator = fragment
        heading.text = opened.metadata.title
        supportFragmentManager.beginTransaction().replace(HOST_ID, fragment, NAVIGATOR_TAG).commitNow()
        locatorJob = lifecycleScope.launch {
            fragment.currentLocator.collect { locator ->
                if (loaded) {
                    val json = locator.toJSON().toString()
                    val sequence = synchronized(locatorLock) { ++locatorSequence }
                    val failed = withContext(Dispatchers.IO) {
                        synchronized(locatorLock) {
                            sequence == locatorSequence && runCatching { store.saveLocator(bookId, book.sha256, json) }.isFailure
                        }
                    }
                    if (failed) warnProgressSave()
                }
            }
        }
    }

    /** SDK page-loaded may precede the final inset/font layout. Do not persist that page's 0. */
    private fun restorePendingPosition() {
        val target = restoreTarget ?: return
        val fragment = navigator ?: return
        if (restoreJob?.isActive == true) return
        restoreJob = lifecycleScope.launch {
            try {
                val restored = withTimeoutOrNull(12_000) {
                    var previousGeometry: String? = null
                    var stableSamples = 0
                    while (true) {
                        delay(120)
                        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) continue
                        if (navigator !== fragment || restoreTarget !== target) return@withTimeoutOrNull false
                        val geometry = restorationGeometry(fragment, target) ?: continue
                        val expectedPath = "/" + target.href.removeFragment().toString().substringBefore('?').trimStart('/')
                        if (!geometry.optString("path").endsWith(expectedPath) || !geometry.optBoolean("ready")) continue
                        val signature = listOf("width", "height", "rangeWidth", "rangeHeight")
                            .joinToString(":") { geometry.optDouble(it).toString() }
                        stableSamples = if (signature == previousGeometry) stableSamples + 1 else 0
                        previousGeometry = signature
                        if (stableSamples < 2) continue
                        if (!fragment.go(target, animated = false)) continue
                        // Readium emits its geometry-derived Locator after a 100ms debounce.
                        delay(250)
                        val after = restorationGeometry(fragment, target) ?: continue
                        val afterSignature = listOf("width", "height", "rangeWidth", "rangeHeight")
                            .joinToString(":") { after.optDouble(it).toString() }
                        if (signature != afterSignature || !after.optBoolean("ready")) { stableSamples = 0; continue }
                        val width = after.optDouble("width")
                        val pages = (after.optDouble("rangeWidth") / width).coerceAtLeast(1.0)
                        val actual = fragment.currentLocator.value
                        val expectedProgression = target.locations.progression ?: 0.0
                        val actualProgression = actual.locations.progression ?: 0.0
                        if (width > 0 && pages.isFinite() && actual.href.removeFragment() == target.href.removeFragment() &&
                            kotlin.math.abs(actualProgression - expectedProgression) <= .5 / pages + .005) {
                            return@withTimeoutOrNull true
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") false
                } == true
                if (navigator !== fragment || restoreTarget !== target) return@launch
                if (restored) {
                    restoreTarget = null; loaded = true; status.visibility = View.GONE
                    saveCurrentLocator()
                } else {
                    showRestoreFailure("原版阅读位置尚未恢复，已保留上次进度。")
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (navigator === fragment) {
                    showRestoreFailure("原版阅读位置恢复失败，已保留上次进度。")
                }
            }
        }
    }

    private fun showRestoreFailure(message: String) {
        status.text = "$message\n可返回后重新打开，或点此从当前页继续阅读。"
        status.visibility = View.VISIBLE
        status.setOnClickListener {
            if (restoreTarget == null) return@setOnClickListener
            // Explicit user choice replaces the pending restoration intent. A late coroutine
            // cannot pull the reader back or resurrect the old target on the next resume.
            restoreTarget = null
            restoreJob?.cancel(); restoreJob = null
            loaded = true; status.visibility = View.GONE
            status.setOnClickListener(null)
            saveCurrentLocator()
        }
    }

    private suspend fun restorationGeometry(fragment: EpubNavigatorFragment, target: Locator): JSONObject? {
        val suffix = "/" + target.href.removeFragment().toString().substringBefore('?').trimStart('/')
        fun find(view: View?): WebView? {
            if (view is WebView && view.isShown && view.url?.substringBefore('#')?.substringBefore('?')?.endsWith(suffix) == true) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val webView = find(fragment.view) ?: return null
        // Application-owned read-only geometry; never execute book-authored code or enable networking.
        // A destroyed/backgrounded WebView need not return a callback, so the wait must be cancellable.
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript("""JSON.stringify({path:location.pathname,
                ready:document.readyState==='complete' && document.fonts.status==='loaded',
                width:innerWidth,height:innerHeight,rangeWidth:document.documentElement.scrollWidth,
                rangeHeight:document.documentElement.scrollHeight})""") { raw ->
                val result = runCatching {
                    val value = if (raw.startsWith('"')) org.json.JSONArray("[$raw]").getString(0) else raw
                    JSONObject(value)
                }.getOrNull()
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (restoreTarget != null && restoreJob?.isActive != true) restorePendingPosition()
    }

    private fun showContents() {
        if (!loaded) return
        val publication = publication ?: return
        val flattened = mutableListOf<Pair<String, Link>>()
        fun add(links: List<Link>, depth: Int = 0) {
            links.forEach { link ->
                flattened += ("  ".repeat(depth.coerceAtMost(5)) + (link.title ?: "章节")) to link
                add(link.children, depth + 1)
            }
        }
        add(publication.tableOfContents.ifEmpty { publication.readingOrder })
        AlertDialog.Builder(this).setTitle("目录")
            .setItems(flattened.map { it.first }.toTypedArray()) { _, index -> navigator?.go(flattened[index].second, animated = true) }
            .setNegativeButton("关闭", null).show()
    }

    private fun saveCurrentLocator() {
        val book = prepared ?: return
        if (!loaded) return
        navigator?.currentLocator?.value?.toJSON()?.toString()?.let { json ->
            // A small synchronous atomic flush on stop/state-save survives immediate process death.
            synchronized(locatorLock) {
                ++locatorSequence
                if (runCatching { store.saveLocator(bookId, book.sha256, json) }.isFailure) warnProgressSave()
            }
            savedLocator = json; savedDigest = book.sha256
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        saveCurrentLocator()
        super.onSaveInstanceState(outState)
        outState.putString("epub_locator", savedLocator)
        outState.putString("epub_digest", savedDigest)
    }
    override fun onStop() { saveCurrentLocator(); super.onStop() }
    override fun onDestroy() {
        locatorJob?.cancel(); restoreJob?.cancel()
        associationDialog?.dismiss(); associationDialog = null
        closeAssociationCandidate()
        super.onDestroy()
        publication?.close(); publication = null
    }
    private fun showError(message: String) { status.text = "$message\n可重新关联原文件，或继续阅读文字版"; status.visibility = View.VISIBLE }
    private fun warnProgressSave() {
        if (!progressSaveWarningShown) {
            progressSaveWarningShown = true
            Toast.makeText(this, "原版进度暂时保存失败，请检查可用空间", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        if (name.startsWith("org.readium.") && name.endsWith("WebView")) {
            // Activity's private inflater factory also runs for fragment XML. R2WebView is
            // Kotlin-internal but has a public XML constructor. Harden before onCreateView loads.
            return layoutInflater.createView(name, null, attrs).also { view ->
                check(view is WebView) { "Readium WebView construction failed" }
                harden(view)
            }
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    @Suppress("DEPRECATION")
    private fun harden(webView: WebView) {
        webView.settings.apply {
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            domStorageEnabled = false
            databaseEnabled = false
            setGeolocationEnabled(false)
        }
        webView.setDownloadListener { _, _, _, _, _ -> }
    }
}
