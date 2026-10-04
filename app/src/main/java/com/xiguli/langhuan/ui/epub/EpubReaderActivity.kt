@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.xiguli.langhuan.ui.epub

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.xiguli.langhuan.data.epub.EpubOriginalStore
import com.xiguli.langhuan.data.epub.EpubPublicationSession
import com.xiguli.langhuan.ui.EpubReaderScreenV50
import com.xiguli.langhuan.ui.EpubReaderUiStateV50
import com.xiguli.langhuan.ui.LibraryExperienceViewModel
import com.xiguli.langhuan.ui.ReaderSelectionV30
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl


/**
 * EPUB 原版阅读宿主。
 *
 * 真实 EPUB 能力仍由 Readium 提供：
 * Publication / EpubNavigatorFragment / Locator / readingOrder /
 * tableOfContents / publisherStyles。Compose 只负责 V50 外层阅读 UI。
 *
 * 原版 Locator 与文字版 ReaderProgressStore 使用各自原有存储，
 * 切换「原版 / 文字版」不会相互覆盖阅读位置。
 */
class EpubReaderActivity : FragmentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "epub_book_id"
        const val RESULT_TEXT_READER = 71
        private const val HOST_ID = 0x51ee010
        private const val NAVIGATOR_TAG = "epub_original_navigator"
        private const val PICK_ORIGINAL = 721
        private const val READER_PREFS = "reader_qingmo_v9"
        private const val ORIGINAL_BOOKMARK_PREFIX = "epub_original_bookmarks_v50_"
    }

    /* ------------------------------ Identity ------------------------------ */
    private lateinit var bookId: String
    private lateinit var store: EpubOriginalStore
    private lateinit var readerPrefs: android.content.SharedPreferences
    private lateinit var libraryViewModel: LibraryExperienceViewModel

    /* ------------------------------- Readium ------------------------------- */
    private var publication: Publication? = null
    private var prepared: EpubOriginalStore.Prepared? = null
    private var navigator: EpubNavigatorFragment? = null
    private var readerHostReady = false
    private var navigatorAttached = false
    private var navigatorGeneration = 0L
    private var rendererExited = false

    /* -------------------------------- Jobs -------------------------------- */
    private var openJob: Job? = null
    private var locatorJob: Job? = null
    private var restoreJob: Job? = null
    private var selectionJob: Job? = null

    /* ----------------------------- Restoration ----------------------------- */
    private var savedLocator: String? = null
    private var savedDigest: String? = null
    private var restoreTarget: Locator? = null

    /* ---------------------------- Compose State ---------------------------- */
    private var readerUiState by mutableStateOf(EpubReaderUiStateV50())
    private var tocState by mutableStateOf<List<Link>>(emptyList())
    private var currentTocIndex by mutableIntStateOf(-1)
    private var selectionState by mutableStateOf<ReaderSelectionV30?>(null)

    /* -------------------------------- State -------------------------------- */
    private var loaded = false
        set(value) {
            field = value
            readerUiState = readerUiState.copy(loaded = value)
        }

    private val locatorLock = Any()
    private var locatorSequence = 0L
    private var progressSaveWarningShown = false

    /* --------------------------- Association State --------------------------- */
    private class AssociationCandidate(
        val staged: EpubOriginalStore.Staged,
        val opened: Publication,
    ) : Closeable {
        override fun close() {
            try {
                opened.close()
            } finally {
                staged.close()
            }
        }
    }

    private var associationCandidate: AssociationCandidate? = null
    private var associationDialog: AlertDialog? = null

    /* -------------------------------- Create -------------------------------- */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium Fragment 的 FragmentFactory 依赖异步打开的 Publication，
        // 继续禁止 Android 自动恢复旧 Fragment，从持久化 Locator 主动重建。
        super.onCreate(null)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        if (bookId.isBlank() || bookId.length > 512) {
            finish()
            return
        }
        savedLocator = savedInstanceState?.getString("epub_locator")
        savedDigest = savedInstanceState?.getString("epub_digest")
        rendererExited = savedInstanceState?.getBoolean("epub_renderer_exited") == true
        store = EpubReaderEntry.store(this)
        readerPrefs = getSharedPreferences(READER_PREFS, Context.MODE_PRIVATE)
        libraryViewModel = ViewModelProvider(this)[LibraryExperienceViewModel::class.java]

        // Readium 内部可能创建多个私有 WebView，每一个都套用已有安全策略。
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    state: Bundle?,
                ) {
                    protectReadiumViewTree(view)
                }
            },
            true,
        )

        enableEdgeToEdge()
        buildComposeContent()

        if (store.hasOriginal(bookId)) {
            if (rendererExited) showRendererFailure() else openStored()
        } else {
            showError(
                "这本旧书只保存了文字。重新关联原 EPUB 后可阅读插画和作者排版；" +
                    "现有文字、笔记和文字版阅读进度都会保留。",
            )
        }
    }

    /* ----------------------------- Compose Host ----------------------------- */
    private fun buildComposeContent() {
        setContent {
            LanghuanStableTheme {
                val libraryState by libraryViewModel.state
                    .collectAsStateWithLifecycle()
                val book = libraryState.stories.firstOrNull { it.id == bookId }
                if (book == null) {
                    EpubBookMetadataLoadingV50(
                        libraryLoaded = libraryState.libraryLoaded,
                    )
                    if (libraryState.libraryLoaded) {
                        androidx.compose.runtime.LaunchedEffect(bookId) {
                            Toast.makeText(
                                this@EpubReaderActivity,
                                "找不到这本 EPUB 的书架记录",
                                Toast.LENGTH_LONG,
                            ).show()
                            finish()
                        }
                    }
                } else {
                    EpubReaderScreenV50(
                        book = book,
                        prefs = readerPrefs,
                        state = readerUiState,
                        tableOfContents = tocState,
                        currentTocIndex = currentTocIndex,
                        readerHostId = HOST_ID,
                        selection = selectionState,
                        onReaderHostReady = { onReaderHostReady(it) },
                        onBack = {
                            saveCurrentLocator()
                            finish()
                        },
                        onOpenTextVersion = { openTextVersion() },
                        onOpenOriginalVersion = { clearSelection() },
                        onPreviousPage = {
                            if (loaded) {
                                clearSelection()
                                navigator?.goBackward(animated = true)
                            }
                        },
                        onNextPage = {
                            if (loaded) {
                                clearSelection()
                                navigator?.goForward(animated = true)
                            }
                        },
                        onNavigateTo = { link ->
                            if (loaded) {
                                clearSelection()
                                navigator?.go(link, animated = true)
                            }
                        },
                        onToggleBookmark = { toggleOriginalBookmark() },
                        onRelinkOriginal = { pickOriginal() },
                        onContinueAfterRestoreFailure = { continueAfterRestoreFailure() },
                        onRestartAfterRendererExit = { restartAfterRendererExit() },
                        onClearSelection = { clearSelection() },
                        onNoteSaved = {
                            Toast.makeText(
                                this@EpubReaderActivity,
                                "段落笔记已保存",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onHighlightChanged = {
                            Toast.makeText(
                                this@EpubReaderActivity,
                                "段落划线已更新",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun EpubBookMetadataLoadingV50(libraryLoaded: Boolean) {
        val t = LocalLanghuanUiTokens.current
        Box(
            modifier = Modifier.fillMaxSize().background(t.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!libraryLoaded) {
                    CircularProgressIndicator(color = t.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.height(t.space4))
                }
                Text(
                    text = if (libraryLoaded) "未找到书籍资料" else "正在读取书籍资料…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.secondaryForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    /* ------------------------------ Host Ready ------------------------------ */
    private fun onReaderHostReady(host: FrameLayout) {
        if (host.id != HOST_ID) host.id = HOST_ID
        // AndroidView.factory runs before the host belongs to the Activity view tree.
        // View.post on an unattached host waits for attachment; FragmentManager then sees it.
        host.post {
            if (isDestroyed || isFinishing || window.decorView.findViewById<View>(HOST_ID) !== host) return@post
            readerHostReady = true
            attachNavigatorToHostIfReady()
        }
    }

    private fun attachNavigatorToHostIfReady() {
        if (!readerHostReady || navigatorAttached || window.decorView.findViewById<View>(HOST_ID) == null) return
        val fragment = navigator ?: return
        if (supportFragmentManager.isStateSaved) {
            lifecycleScope.launch {
                lifecycle.withResumed { attachNavigatorToHostIfReady() }
            }
            return
        }
        supportFragmentManager
            .beginTransaction()
            .replace(HOST_ID, fragment, NAVIGATOR_TAG)
            .commitNow()
        navigatorAttached = true
    }

    /* ----------------------------- Text Version ----------------------------- */
    private fun openTextVersion() {
        // 不修改 ReaderProgressStoreV11。主页面收到 RESULT_TEXT_READER 后，
        // 会沿现有 requestBook() -> ReaderProgressStoreV11.load() 路径恢复
        // 文字版自己的阅读位置。
        saveCurrentLocator()
        setResult(
            RESULT_TEXT_READER,
            Intent().putExtra(EXTRA_BOOK_ID, bookId),
        )
        finish()
    }

    /* -------------------------- Original File Picker -------------------------- */
    @Suppress("DEPRECATION")
    private fun pickOriginal() {
        if (openJob?.isActive == true || associationCandidate != null) return
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/epub+zip",
                        "application/octet-stream",
                        "application/zip",
                    ),
                )
            },
            PICK_ORIGINAL,
        )
    }

    @Deprecated(
        "Uses a stable request code for process-recreated original-file association",
    )
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_ORIGINAL && resultCode == RESULT_OK) {
            data?.data?.let(::prepareAssociation)
        }
    }

    /* ------------------------------ Open Stored ------------------------------ */
    private fun openStored() {
        openJob?.cancel()
        showStatus("正在打开 EPUB 原版…")
        openJob = lifecycleScope.launch {
            var pendingPublication: Publication? = null
            try {
                val pair = withContext(Dispatchers.IO) {
                    val scope = currentCoroutineContext()
                    val preparedBook = store.open(bookId) { scope.ensureActive() }
                    val opened = EpubPublicationSession.open(applicationContext, preparedBook)
                    pendingPublication = opened
                    preparedBook to opened
                }
                lifecycle.withResumed {
                    attach(pair.first, pair.second)
                    pendingPublication = null
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (rendererExited) {
                    showRendererFailure("重新打开原版失败。已保留上次确认进度，可重试或阅读文字版。")
                } else {
                    showError(error.message ?: "无法打开 EPUB")
                }
            } finally {
                pendingPublication?.close()
            }
        }
    }

    /* --------------------------- Prepare Association --------------------------- */
    private fun prepareAssociation(uri: Uri) {
        if (openJob?.isActive == true || associationCandidate != null) return
        showStatus("正在检查 EPUB 原文件…")
        openJob = lifecycleScope.launch {
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
                        .setTitle(
                            "关联《${
                                candidate.staged.prepared.archive.title.ifBlank {
                                    "未命名 EPUB"
                                }
                            }》？",
                        )
                        .setMessage(
                            "请确认这是当前书籍的原文件。" +
                                "插画原版使用独立 Locator 进度；" +
                                "已有文字、书签、段落笔记和文字版进度都会保留。",
                        )
                        .setNegativeButton("取消", null)
                        .setPositiveButton("关联并阅读") { _, _ ->
                            associationCandidate = null
                            adoptAssociation(candidate)
                        }
                        .setOnDismissListener {
                            closeAssociationCandidate()
                            associationDialog = null
                        }
                        .show()
                    pending = null
                    staged = null
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError(error.message ?: "无法关联 EPUB")
                Toast.makeText(
                    this@EpubReaderActivity,
                    error.message ?: "无法关联 EPUB",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                if (pending != null) {
                    runCatching { pending?.close() }
                } else {
                    runCatching { staged?.close() }
                }
            }
        }
    }
    /* ---------------------------- Adopt Association ---------------------------- */
    private fun adoptAssociation(candidate: AssociationCandidate) {
        showStatus("正在关联 EPUB 原文件…")
        openJob = lifecycleScope.launch {
            var published = false
            var pendingPublication: Publication? = null
            try {
                val pair = withContext(Dispatchers.IO) {
                    val scope = currentCoroutineContext()
                    val canonical = store.associate(
                        bookId,
                        candidate.staged.prepared,
                    ) { scope.ensureActive() }
                    published = true
                    val opened = EpubPublicationSession.open(applicationContext, canonical)
                    pendingPublication = opened
                    canonical to opened
                }
                lifecycle.withResumed {
                    savedLocator = null
                    savedDigest = null
                    attach(pair.first, pair.second)
                    pendingPublication = null
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = if (published) {
                    "原文件已关联，重新打开书籍即可继续阅读"
                } else {
                    "关联失败，原书未更改"
                }
                showError(message)
                Toast.makeText(this@EpubReaderActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                pendingPublication?.close()
                runCatching { candidate.close() }
                    .onFailure { warnStagingCleanup() }
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

    /* --------------------------------- Attach --------------------------------- */
    private fun attach(
        book: EpubOriginalStore.Prepared,
        opened: Publication,
    ) {
        saveCurrentLocator()
        val generation = ++navigatorGeneration
        rendererExited = false
        locatorJob?.cancel()
        selectionJob?.cancel()
        restoreJob?.cancel()
        restoreJob = null
        selectionState = null
        navigator?.takeIf { it.isAdded }?.let {
            if (!supportFragmentManager.isStateSaved) {
                supportFragmentManager
                    .beginTransaction()
                    .remove(it)
                    .commitNow()
            }
        }
        navigator = null
        navigatorAttached = false
        publication?.close()
        prepared = book
        publication = opened
        loaded = false
        tocState = opened.tableOfContents.ifEmpty { opened.readingOrder }
        readerUiState = readerUiState.copy(
            publicationTitle = opened.metadata.title.orEmpty(),
            chapterIndex = 0,
            chapterTitle = firstReadableTitle(opened),
            pageIndex = 0,
            pageCount = 0,
            bookProgress = 0f,
            canGoBack = false,
            canGoForward = opened.readingOrder.size > 1,
            bookmarked = false,
        )

        val initialJson = savedLocator?.takeIf { savedDigest == book.sha256 }
            ?: store.loadLocator(bookId, book.sha256)
        val initial = runCatching {
            initialJson?.let { Locator.fromJSON(JSONObject(it)) }
        }.getOrNull()?.takeIf { locator ->
            opened.readingOrder.any {
                it.url().removeFragment() == locator.href.removeFragment()
            }
        }
        restoreTarget = initial?.takeIf {
            opened.metadata.layout != Layout.FIXED
        }

        showStatus(
            if (restoreTarget != null) "正在恢复原版阅读位置…" else "正在打开 EPUB 原版…",
        )

        val factory = EpubNavigatorFactory(opened)
            .createFragmentFactory(
                initialLocator = initial,
                initialPreferences = EpubPreferences(publisherStyles = true),
                listener = object : EpubNavigatorFragment.Listener {
                    override fun onExternalLinkActivated(url: AbsoluteUrl) {
                        Toast.makeText(
                            this@EpubReaderActivity,
                            "原版阅读不会打开书内外部链接",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageLoaded() {
                        if (navigatorGeneration != generation || rendererExited || navigator == null) return
                        if (restoreTarget == null) {
                            loaded = true
                            hideStatus()
                            scheduleReaderUiRefresh()
                        } else {
                            restorePendingPosition()
                        }
                    }
                },
            )

        supportFragmentManager.fragmentFactory = factory
        val fragment = factory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment
        navigator = fragment
        attachNavigatorToHostIfReady()

        locatorJob = lifecycleScope.launch {
            fragment.currentLocator.collect { locator ->
                if (navigator !== fragment) return@collect
                if (loaded) {
                    persistLocatorAsync(book = book, locator = locator)
                    refreshReaderUi(fragment = fragment, locator = locator)
                    clearSelectionStateOnly()
                }
            }
        }
    }

    /* ---------------------------- Locator Persistence ---------------------------- */
    private fun persistLocatorAsync(
        book: EpubOriginalStore.Prepared,
        locator: Locator,
    ) {
        val json = locator.toJSON().toString()
        val sequence = synchronized(locatorLock) { ++locatorSequence }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                synchronized(locatorLock) {
                    if (sequence == locatorSequence) {
                        runCatching { store.saveLocator(bookId, book.sha256, json) }
                    } else null
                }
            }
            val current = synchronized(locatorLock) { sequence == locatorSequence }
            if (!current || !loaded || prepared !== book || rendererExited || result == null) return@launch
            if (result.isFailure) {
                warnProgressSave()
            } else {
                savedLocator = json
                savedDigest = book.sha256
            }
        }
    }

    private fun saveCurrentLocator() {
        val book = prepared ?: return
        if (!loaded) return
        val locator = navigator?.currentLocator?.value ?: return
        val json = locator.toJSON().toString()
        synchronized(locatorLock) {
            ++locatorSequence
            if (
                runCatching {
                    store.saveLocator(bookId, book.sha256, json)
                }.isFailure
            ) {
                warnProgressSave()
            }
        }
        savedLocator = json
        savedDigest = book.sha256
    }

    /* ----------------------------- Reader UI State ----------------------------- */
    private fun scheduleReaderUiRefresh() {
        val fragment = navigator ?: return
        lifecycleScope.launch {
            delay(140)
            if (navigator === fragment && loaded) {
                refreshReaderUi(
                    fragment = fragment,
                    locator = fragment.currentLocator.value,
                )
            }
        }
    }

    private suspend fun refreshReaderUi(
        fragment: EpubNavigatorFragment,
        locator: Locator,
    ) {
        val opened = publication ?: return
        val readingOrder = opened.readingOrder
        if (readingOrder.isEmpty()) return

        val resourceIndex = readingOrder
            .indexOfFirst {
                it.url().removeFragment() == locator.href.removeFragment()
            }
            .takeIf { it >= 0 } ?: 0
        val resourceProgression = locator.locations.progression?.coerceIn(0.0, 1.0) ?: 0.0
        val geometry = runCatching {
            currentGeometry(fragment, locator)
        }.getOrNull()
        // Geometry awaits a WebView response. It must not clear a renderer fault or
        // publish the retired Navigator's page state after that suspension.
        if (navigator !== fragment || !loaded || rendererExited) return

        val pageCount = when {
            opened.metadata.layout == Layout.FIXED -> 1
            geometry == null -> 1
            geometry.optDouble("width", 0.0) <= 0.0 -> 1
            else -> {
                val width = geometry.optDouble("width", 0.0)
                val rangeWidth = geometry.optDouble("rangeWidth", width)
                ceil((rangeWidth / width).coerceAtLeast(1.0))
                    .toInt().coerceAtLeast(1)
            }
        }
        val pageIndex = if (pageCount <= 1) {
            0
        } else {
            floor(resourceProgression * pageCount.toDouble())
                .toInt().coerceIn(0, pageCount - 1)
        }
        val chapterTitle = titleForLocator(
            publication = opened,
            locator = locator,
            resourceIndex = resourceIndex,
        )
        val overallProgress = (
            (resourceIndex.toDouble() + resourceProgression)
                .div(readingOrder.size.toDouble())
                .coerceIn(0.0, 1.0)
            ).toFloat()

        currentTocIndex = currentTocIndexForLocator(locator)
        val canGoBack = resourceIndex > 0 || pageIndex > 0
        val canGoForward = resourceIndex < readingOrder.lastIndex ||
            pageIndex < pageCount - 1

        readerUiState = readerUiState.copy(
            publicationTitle = opened.metadata.title.orEmpty(),
            chapterIndex = resourceIndex,
            chapterTitle = chapterTitle,
            pageIndex = pageIndex,
            pageCount = pageCount,
            bookProgress = overallProgress,
            loaded = loaded,
            statusMessage = "",
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            bookmarked = isOriginalBookmarked(locator = locator, pageCount = pageCount),
        )
    }

    private fun titleForLocator(
        publication: Publication,
        locator: Locator,
        resourceIndex: Int,
    ): String {
        val flatToc = flattenToc(tocState)
        val tocTitle = flatToc
            .firstOrNull {
                it.url().removeFragment() == locator.href.removeFragment()
            }
            ?.title?.takeIf { it.isNotBlank() }
        if (tocTitle != null) return tocTitle
        return publication.readingOrder
            .getOrNull(resourceIndex)
            ?.title?.takeIf { it.isNotBlank() }
            ?: "正文"
    }

    private fun firstReadableTitle(publication: Publication): String {
        return publication.tableOfContents.firstOrNull()
            ?.title?.takeIf { it.isNotBlank() }
            ?: publication.readingOrder.firstOrNull()
                ?.title?.takeIf { it.isNotBlank() }
            ?: "正文"
    }

    private fun currentTocIndexForLocator(locator: Locator): Int {
        return flattenToc(tocState)
            .indexOfFirst {
                it.url().removeFragment() == locator.href.removeFragment()
            }
    }

    private fun flattenToc(links: List<Link>): List<Link> {
        val result = ArrayList<Link>()
        fun add(source: List<Link>) {
            source.forEach { link ->
                result += link
                if (link.children.isNotEmpty()) add(link.children)
            }
        }
        add(links)
        return result
    }

    /* -------------------------- Original EPUB Bookmark -------------------------- */
    private fun originalBookmarkKey(): String = ORIGINAL_BOOKMARK_PREFIX + bookId

    private fun loadOriginalBookmarks(): Set<String> {
        return readerPrefs.getStringSet(originalBookmarkKey(), emptySet()).orEmpty().toSet()
    }

    private fun isOriginalBookmarked(locator: Locator, pageCount: Int): Boolean {
        return loadOriginalBookmarks().any { raw ->
            val saved = parseStoredLocator(raw) ?: return@any false
            sameBookmarkPage(
                first = saved,
                second = locator,
                pageCount = pageCount,
            )
        }
    }

    private fun toggleOriginalBookmark() {
        val locator = navigator?.currentLocator?.value ?: return
        val currentPageCount = readerUiState.pageCount.coerceAtLeast(1)
        val old = loadOriginalBookmarks()
        val matching = old.firstOrNull { raw ->
            val saved = parseStoredLocator(raw) ?: return@firstOrNull false
            sameBookmarkPage(
                first = saved,
                second = locator,
                pageCount = currentPageCount,
            )
        }
        val next = old.toMutableSet()
        val adding = matching == null
        if (matching != null) {
            next.remove(matching)
        } else {
            next.add(locator.toJSON().toString())
        }
        val committed = readerPrefs.edit()
            .putStringSet(originalBookmarkKey(), next)
            .commit()
        if (!committed) {
            Toast.makeText(this, "原版书签保存失败，请检查存储空间", Toast.LENGTH_LONG).show()
            return
        }
        readerUiState = readerUiState.copy(bookmarked = adding)
        Toast.makeText(
            this,
            if (adding) "已加入原版书签" else "已取消原版书签",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun parseStoredLocator(raw: String): Locator? {
        return runCatching { Locator.fromJSON(JSONObject(raw)) }.getOrNull()
    }

    private fun sameBookmarkPage(
        first: Locator,
        second: Locator,
        pageCount: Int,
    ): Boolean {
        if (
            first.href.removeFragment() != second.href.removeFragment()
        ) {
            return false
        }
        val a = first.locations.progression ?: 0.0
        val b = second.locations.progression ?: 0.0
        // Locator progression 可能因为字体加载或 inset 有极小变化；
        // 半页以内视为同一原版页。
        val tolerance = maxOf(0.004, 0.5 / pageCount.coerceAtLeast(1).toDouble())
        return abs(a - b) <= tolerance
    }
    /* ------------------------------ Restoration ------------------------------ */
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
                        if (
                            !lifecycle.currentState.isAtLeast(
                                androidx.lifecycle.Lifecycle.State.RESUMED,
                            )
                        ) {
                            continue
                        }
                        if (navigator !== fragment || restoreTarget !== target) {
                            return@withTimeoutOrNull false
                        }
                        val geometry = currentGeometry(fragment, target) ?: continue
                        val expectedPath = "/" +
                            target.href.removeFragment().toString()
                                .substringBefore('?').trimStart('/')
                        if (
                            !geometry.optString("path").endsWith(expectedPath) ||
                            !geometry.optBoolean("ready")
                        ) {
                            continue
                        }
                        val signature = listOf(
                            "width", "height", "rangeWidth", "rangeHeight",
                        ).joinToString(":") { geometry.optDouble(it).toString() }
                        stableSamples = if (signature == previousGeometry) {
                            stableSamples + 1
                        } else {
                            0
                        }
                        previousGeometry = signature
                        if (stableSamples < 2) continue
                        if (!fragment.go(target, animated = false)) continue
                        delay(250)
                        val after = currentGeometry(fragment, target) ?: continue
                        val afterSignature = listOf(
                            "width", "height", "rangeWidth", "rangeHeight",
                        ).joinToString(":") { after.optDouble(it).toString() }
                        if (signature != afterSignature || !after.optBoolean("ready")) {
                            stableSamples = 0
                            continue
                        }
                        val width = after.optDouble("width")
                        val pages = (after.optDouble("rangeWidth") / width)
                            .coerceAtLeast(1.0)
                        val actual = fragment.currentLocator.value
                        val expectedProgression = target.locations.progression ?: 0.0
                        val actualProgression = actual.locations.progression ?: 0.0
                        if (
                            width > 0 && pages.isFinite() &&
                            actual.href.removeFragment() == target.href.removeFragment() &&
                            abs(actualProgression - expectedProgression) <= 0.5 / pages + 0.005
                        ) {
                            return@withTimeoutOrNull true
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                } == true

                if (navigator !== fragment || restoreTarget !== target) return@launch

                if (restored) {
                    restoreTarget = null
                    loaded = true
                    hideStatus()
                    saveCurrentLocator()
                    refreshReaderUi(
                        fragment = fragment,
                        locator = fragment.currentLocator.value,
                    )
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
        showStatus("$message\n可返回后重新打开，或选择从当前页继续。")
        readerUiState = readerUiState.copy(canContinueAfterRestoreFailure = restoreTarget != null && navigator != null)
    }

    private fun continueAfterRestoreFailure() {
        if (!readerUiState.canContinueAfterRestoreFailure) return
        val fragment = navigator ?: return
        // Explicit user choice cancels the pending target before accepting new navigation.
        restoreJob?.cancel()
        restoreJob = null
        restoreTarget = null
        loaded = true
        readerUiState = readerUiState.copy(canContinueAfterRestoreFailure = false)
        hideStatus()
        lifecycleScope.launch { refreshReaderUi(fragment, fragment.currentLocator.value) }
        saveCurrentLocator()
    }

    /* -------------------------- WebView Geometry -------------------------- */
    private suspend fun currentGeometry(
        fragment: EpubNavigatorFragment,
        locator: Locator,
    ): JSONObject? {
        val webView = findWebViewForLocator(fragment.view, locator) ?: return null
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        return JSON.stringify({
                            path: location.pathname,
                            ready:
                                document.readyState === 'complete' &&
                                (!document.fonts || document.fonts.status === 'loaded'),
                            width: innerWidth,
                            height: innerHeight,
                            rangeWidth:
                                document.documentElement.scrollWidth,
                            rangeHeight:
                                document.documentElement.scrollHeight
                        });
                    } catch (e) {
                        return null;
                    }
                })();
                """.trimIndent(),
            ) { raw ->
                val result = decodeJavascriptJson(raw)?.let {
                    runCatching { JSONObject(it) }.getOrNull()
                }
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun findWebViewForLocator(root: View?, locator: Locator): WebView? {
        val suffix = "/" +
            locator.href.removeFragment().toString()
                .substringBefore('?').trimStart('/')
        fun find(current: View?): WebView? {
            when (current) {
                is WebView -> {
                    val path = current.url?.substringBefore('#')?.substringBefore('?')
                    if (current.isShown && path?.endsWith(suffix) == true) {
                        return current
                    }
                }
                is ViewGroup -> {
                    for (index in 0 until current.childCount) {
                        val hit = find(current.getChildAt(index))
                        if (hit != null) return hit
                    }
                }
            }
            return null
        }
        return find(root)
    }

    /* -------------------------- WebView Selection -------------------------- */
    private fun scheduleSelectionCapture(webView: WebView) {
        selectionJob?.cancel()
        selectionJob = lifecycleScope.launch {
            delay(260)
            captureSelection(webView)
        }
    }

    private suspend fun captureSelection(webView: WebView) {
        if (!loaded || !webView.isShown) return
        val payload = evaluateSelection(webView) ?: return
        val text = payload.optString("text").trim()
        if (text.isBlank()) return
        val start = payload.optInt("start", -1)
        val end = payload.optInt("end", -1)
        if (start < 0 || end <= start) return
        selectionState = ReaderSelectionV30(
            chapterIndex = readerUiState.chapterIndex,
            pageIndex = readerUiState.pageIndex,
            start = start,
            end = end,
            text = text,
            top = payload.optDouble("top", 0.0).toFloat(),
            bottom = payload.optDouble("bottom", 0.0).toFloat(),
        )
    }

    private suspend fun evaluateSelection(webView: WebView): JSONObject? {
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        const selection = window.getSelection();

                        if (
                            !selection ||
                            selection.rangeCount === 0 ||
                            selection.isCollapsed
                        ) {
                            return null;
                        }

                        const range = selection.getRangeAt(0);
                        const text = range.toString();

                        if (!text || !text.trim()) {
                            return null;
                        }

                        const before = document.createRange();
                        before.selectNodeContents(document.body);
                        before.setEnd(
                            range.startContainer,
                            range.startOffset
                        );

                        const start = before.toString().length;
                        const end = start + text.length;

                        const rect = range.getBoundingClientRect();

                        return JSON.stringify({
                            text: text,
                            start: start,
                            end: end,
                            top: rect.top,
                            bottom: rect.bottom
                        });
                    } catch (e) {
                        return null;
                    }
                })();
                """.trimIndent(),
            ) { raw ->
                val result = decodeJavascriptJson(raw)?.let {
                    runCatching { JSONObject(it) }.getOrNull()
                }
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun clearSelection() {
        clearSelectionStateOnly()
        val fragment = navigator ?: return
        val locator = fragment.currentLocator.value
        val webView = findWebViewForLocator(fragment.view, locator) ?: return
        webView.evaluateJavascript(
            """
            (function() {
                try {
                    const selection = window.getSelection();
                    if (selection) {
                        selection.removeAllRanges();
                    }
                } catch (e) {}
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun clearSelectionStateOnly() {
        selectionState = null
    }

    /* ---------------------------- WebView Security ---------------------------- */
    private fun protectReadiumViewTree(current: View) {
        if (current is WebView) {
            if (current.webViewClient !is EpubSecureWebViewClient) {
                val generation = navigatorGeneration
                current.webViewClient =
                    EpubSecureWebViewClient(current.webViewClient, assets) { view ->
                        retireNavigatorAfterRendererExit(view, generation)
                    }
            }
            harden(current)
        } else if (current is ViewGroup) {
            for (index in 0 until current.childCount) {
                protectReadiumViewTree(current.getChildAt(index))
            }
        }
    }

    private fun retireNavigatorAfterRendererExit(view: WebView, generation: Long): Boolean {
        val retired = navigator ?: return false
        if (generation != navigatorGeneration || isDestroyed) return false
        var ancestor: View? = view
        while (ancestor != null && ancestor !== retired.view) ancestor = ancestor.parent as? View
        if (ancestor == null || !retired.isAdded) return false
        // Stop accepting navigation/save callbacks BEFORE disposing any SDK views.
        // Never ask a failed WebView for a new Locator, and never accept page zero.
        loaded = false
        rendererExited = true
        ++navigatorGeneration
        locatorJob?.cancel()
        restoreJob?.cancel()
        selectionJob?.cancel()
        openJob?.cancel()
        restoreJob = null
        restoreTarget = null
        selectionState = null
        synchronized(locatorLock) {
            ++locatorSequence
            prepared?.let { book ->
                savedLocator = store.loadLocator(bookId, book.sha256)
                savedDigest = book.sha256
            }
        }
        navigator = null
        navigatorAttached = false
        showRendererFailure()
        // This is disposal of an unusable transient Navigator, not a data transaction.
        // Automatic Fragment restoration is already disabled in onCreate. Removal
        // must also work after onSaveInstanceState; Readium onDetach destroys its views.
        return runCatching {
            supportFragmentManager.beginTransaction().remove(retired).commitNowAllowingStateLoss()
            true
        }.getOrElse {
            android.util.Log.w("EpubReader", "Deferring retired Navigator disposal", it)
            window.decorView.post {
                if (!supportFragmentManager.isDestroyed && retired.isAdded) {
                    supportFragmentManager.beginTransaction().remove(retired).commitNowAllowingStateLoss()
                }
            }
            false
        }
    }

    private fun showRendererFailure(
        message: String = "原版页面渲染已中断。已保留上次确认进度，重新打开可继续阅读。",
    ) {
        loaded = false
        readerUiState = readerUiState.copy(
            loaded = false,
            statusMessage = message,
            canContinueAfterRestoreFailure = false,
            canRestartAfterRendererExit = true,
            canGoBack = false,
            canGoForward = false,
        )
    }

    private fun restartAfterRendererExit() {
        if (!rendererExited || !readerUiState.canRestartAfterRendererExit || openJob?.isActive == true) return
        openStored()
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? {
        if (name.startsWith("org.readium.") && name.endsWith("WebView")) {
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
        webView.setDownloadListener { _, _, _, _, _ -> Unit }
        // 返回 false：不拦截 Readium / WebView 自己的文本选择行为。
        // 只在系统完成长按选区后读取 Range 信息，再映射为现有 ReaderSelectionV30。
        webView.setOnLongClickListener {
            scheduleSelectionCapture(webView)
            false
        }
    }

    /* ---------------------------- JS Result Decode ---------------------------- */
    private fun decodeJavascriptJson(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null" || raw == "\"null\"") return null
        return runCatching {
            if (raw.startsWith("\"")) {
                JSONArray("[$raw]").getString(0)
            } else {
                raw
            }
        }.getOrNull()
    }

    /* -------------------------------- Status -------------------------------- */
    private fun showStatus(message: String) {
        loaded = false
        readerUiState = readerUiState.copy(
            loaded = false, statusMessage = message,
            canContinueAfterRestoreFailure = false, canRestartAfterRendererExit = false,
        )
    }

    private fun hideStatus() {
        readerUiState = readerUiState.copy(statusMessage = "")
    }

    private fun showError(message: String) {
        loaded = false
        readerUiState = readerUiState.copy(
            loaded = false,
            statusMessage = "$message\n可重新关联原文件，或继续阅读文字版。",
        )
    }

    private fun warnProgressSave() {
        if (progressSaveWarningShown) return
        progressSaveWarningShown = true
        Toast.makeText(this, "原版进度暂时保存失败，请检查可用空间", Toast.LENGTH_LONG).show()
    }

    /* ------------------------------- Lifecycle ------------------------------- */
    override fun onResume() {
        super.onResume()
        if (restoreTarget != null && restoreJob?.isActive != true) {
            restorePendingPosition()
        } else if (loaded) {
            scheduleReaderUiRefresh()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveCurrentLocator()
        super.onSaveInstanceState(outState)
        outState.putString("epub_locator", savedLocator)
        outState.putString("epub_digest", savedDigest)
        outState.putBoolean("epub_renderer_exited", rendererExited)
    }

    override fun onStop() {
        saveCurrentLocator()
        super.onStop()
    }

    override fun onDestroy() {
        ++navigatorGeneration
        locatorJob?.cancel()
        restoreJob?.cancel()
        selectionJob?.cancel()
        openJob?.cancel()
        associationDialog?.dismiss()
        associationDialog = null
        closeAssociationCandidate()
        navigator = null
        publication?.close()
        publication = null
        super.onDestroy()
    }
}
