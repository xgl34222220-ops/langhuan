package com.xiguli.langhuan.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the actual catalogue engine, WebView, ViewModel and visible recovery controls. */
class SourceCatalogueRecoveryV58DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val base = "https://browser-fixture.example"
    private val source = BookSourceV36(
        "catalogue-v58", "原创目录样例", base,
        searchUrl = "/v58/search?key={{key}}", searchList = ".book", searchName = "h2@text", searchBookUrl = "a@href",
        infoName = "h1@text", tocList = "#chapters a", tocName = "@text", tocUrl = "@href", contentText = ".content@html",
        useBrowser = true,
    )

    private fun book(path: String) = OnlineBookV36(source.id, source.name, "原创 $path", "", "", "", "", "$base/v58/$path/book.html")

    private fun catalogue(path: String, count: Int = 3, declared: Int = count) =
        "<h1>原创 $path</h1><section id='chapters'><h2>全部章节（共${declared}章）</h2>" +
            (1..count).joinToString("") { "<a href='/v58/$path/chapter-$it.html'>第${it}章 原创章节</a>" } + "</section>"

    private fun withScreen(pages: (SourceRequestV36) -> String, checks: (OnlineBooksViewModelV36) -> Unit) {
        val app = rule.activity.application as Application
        val previous = BookSourceStoreV36.load(app)
        val projects = StoryProjectManager(app)
        val shelfBefore = runBlocking { projects.observeStories().first() }
        val activeBefore = projects.activeStoryId()
        var vm: OnlineBooksViewModelV36? = null
        try {
            BookSourceStoreV36.save(app, listOf(source))
            val stored = BookSourceStoreV36.raw(app)
            BookSourceBrowserV38.withFixtureSiteV56(pages) {
                try {
                    rule.runOnUiThread { vm = OnlineBooksViewModelV36(app, SavedStateHandle()) }
                    val model = requireNotNull(vm)
                    rule.setContent {
                        val state by model.state.collectAsState()
                        LanghuanStableTheme {
                            OnlineBooksScreenV50(
                                state, state.query, emptyList(), embedded = true,
                                onBack = {}, onManageSources = {}, onQueryChange = {}, onSearch = model::search,
                                onStopSearch = model::stopSearch, onRetrySearch = model::retrySearch,
                                onRecentSearch = model::search, onClearRecentSearches = {},
                                onDiscover = model::discover, onLoadMore = model::loadMoreDiscovery,
                                onOpenBook = model::openDetail, onCloseDetail = model::closeDetail, onViewSource = {},
                                onRetryDetail = model::retryDetail, onStopDetail = model::stopDetail,
                                onAddToShelf = model::addToShelf, onRead = model::readAddedBook,
                                onDownload = model::downloadDetail, onCancelDownload = model::cancelDownload, onChapterClick = {},
                            )
                        }
                    }
                    checks(model)
                    assertEquals("Catalogue recovery must not rewrite sources", stored, BookSourceStoreV36.raw(app))
                    assertEquals("Loading or cancellation must not change the shelf", shelfBefore, runBlocking { projects.observeStories().first() })
                    assertEquals("Loading or cancellation must not change the active story", activeBefore, projects.activeStoryId())
                } finally {
                    rule.runOnUiThread { vm?.viewModelScope?.cancel() }
                    rule.waitUntil(15_000) { vm?.viewModelScope?.coroutineContext?.get(Job)?.isCompleted != false }
                }
            }
        } finally { BookSourceStoreV36.save(app, previous) }
    }

    @Test fun anIncompleteCatalogueExplainsFailureAndRetryNeverSavesThePartialList() {
        val incomplete = AtomicBoolean(true)
        val calls = AtomicInteger()
        val selected = book("incomplete")
        withScreen({ request ->
            assertEquals(selected.bookUrl, request.url)
            calls.incrementAndGet()
            catalogue("incomplete", count = if (incomplete.get()) 2 else 3, declared = 3)
        }) { vm ->
            rule.runOnUiThread { vm.openDetail(selected) }
            rule.waitUntil(20_000) { !vm.state.value.detailLoading && vm.state.value.detailError != null }
            assertTrue(vm.state.value.detail!!.chapters.isEmpty())
            assertNull(vm.state.value.detail!!.shelfStoryId)
            assertFalse(vm.state.value.detailStopped)
            rule.onNodeWithText("目录读取失败").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("目录不完整", substring = true).assertIsDisplayed()
            rule.onNodeWithText("暂无目录").assertDoesNotExist()
            rule.onNodeWithText("加入书架").assertDoesNotExist()
            rule.runOnUiThread { vm.addToShelf() }
            assertNull(vm.state.value.detail!!.shelfStoryId)
            deviceWindowEvidenceV46("v58-catalogue-incomplete-retry")
            incomplete.set(false)
            rule.onNodeWithText("重试目录").performScrollTo().assertIsDisplayed().performClick()
            rule.waitUntil(20_000) { !vm.state.value.detailLoading && vm.state.value.detail!!.chapters.size == 3 }
            assertEquals(2, calls.get())
            assertNull(vm.state.value.detailError)
            assertFalse(vm.state.value.detailStopped)
            assertEquals(3, vm.state.value.detail!!.catalogueProof!!.declaredTotal)
            assertTrue(vm.state.value.detail!!.catalogueProof!!.hasCompletenessEvidence)
            assertNull(vm.state.value.detail!!.shelfStoryId)
            rule.onNodeWithText("加入书架").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("目录读取失败").assertDoesNotExist()
        }
    }

    @Test fun stoppingASlowCatalogueKeepsTheBookAndOffersVisibleContinuation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val selected = book("slow")
        try {
            withScreen({ request ->
                assertEquals(selected.bookUrl, request.url)
                if (calls.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(30, TimeUnit.SECONDS)) { "Slow catalogue fixture was not cancelled" }
                }
                catalogue("slow")
            }) { vm ->
                rule.runOnUiThread { vm.openDetail(selected) }
                rule.waitUntil(10_000) { entered.count == 0L && vm.state.value.detailLoading }
                rule.onNodeWithText("停止加载").assertIsDisplayed().performClick()
                rule.waitUntil(5_000) { !vm.state.value.detailLoading && vm.state.value.detailStopped }
                assertEquals(selected, vm.state.value.detail!!.book)
                assertTrue(vm.state.value.detail!!.chapters.isEmpty())
                assertNull(vm.state.value.detailError)
                rule.onNodeWithText("目录加载已停止").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("暂无目录").assertDoesNotExist()
                deviceWindowEvidenceV46("v58-catalogue-stopped")
                rule.onNodeWithText("继续加载目录").performScrollTo().assertIsDisplayed().performClick()
                rule.waitUntil(20_000) { !vm.state.value.detailLoading && vm.state.value.detail!!.chapters.size == 3 }
                assertEquals(2, calls.get())
                assertEquals(selected.bookUrl, vm.state.value.detail!!.book.bookUrl)
                assertFalse(vm.state.value.detailStopped)
                assertNull(vm.state.value.detailError)
                rule.onNodeWithText("目录加载已停止").assertDoesNotExist()
            }
        } finally { release.countDown() }
    }

    @Test fun aLateOldRequestCannotReopenOrOverwriteTheNextBook() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val old = book("old")
        val next = book("next")
        try {
            withScreen({ request ->
                when (request.url) {
                    old.bookUrl -> {
                        entered.countDown()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                        while (release.count != 0L) {
                            check(System.nanoTime() < deadline) { "Late request fixture was not released" }
                            try { release.await(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) {
                                // Model a transport that returns late despite cancellation, confined to this fixture.
                            }
                        }
                        returned.countDown()
                        throw SourceHttpStatusExceptionV44(503, base, "迟到的旧目录失败")
                    }
                    next.bookUrl -> catalogue("next")
                    else -> error("Unexpected reserved-domain fixture: ${request.url}")
                }
            }) { vm ->
                try {
                    rule.runOnUiThread { vm.openDetail(old) }
                    rule.waitUntil(10_000) { entered.count == 0L && vm.state.value.detailLoading }
                    rule.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
                    rule.waitUntil(5_000) { vm.state.value.detail == null }
                    rule.runOnUiThread { vm.openDetail(next) }
                    rule.waitUntil(20_000) { !vm.state.value.detailLoading && vm.state.value.detail!!.chapters.size == 3 }
                    release.countDown()
                    rule.waitUntil(5_000) { returned.count == 0L }
                    // Let all cancelled detail children finish before checking for an old error or reopen.
                    rule.runOnUiThread { vm.viewModelScope.cancel() }
                    rule.waitUntil(15_000) { vm.viewModelScope.coroutineContext[Job]!!.isCompleted }
                    assertEquals(next.bookUrl, vm.state.value.detail!!.book.bookUrl)
                    assertEquals(3, vm.state.value.detail!!.chapters.size)
                    assertNull(vm.state.value.detailError)
                    assertNull(vm.state.value.error)
                    assertFalse(vm.state.value.detailStopped)
                    rule.onNodeWithText("目录读取失败").assertDoesNotExist()
                } finally { release.countDown() }
            }
        } finally { release.countDown() }
    }

    @Test fun readingAndCachingRequireAnExplicitShelfAdditionAndThenBecomeEnabled() {
        val added = AtomicInteger()
        val read = AtomicInteger()
        val cached = AtomicInteger()
        val displayed = mutableStateOf(OnlineBooksStateV36(
            detail = OnlineDetailV36(book("read"), (1..3).map { OnlineChapterV36("第${it}章", "$base/v58/read/chapter-$it.html") }),
        ))
        rule.setContent {
            LanghuanStableTheme {
                val state = displayed.value
                OnlineBooksScreenV50(
                    state, "", emptyList(), embedded = true,
                    onBack = {}, onManageSources = {}, onQueryChange = {}, onSearch = {},
                    onStopSearch = {}, onRetrySearch = {}, onRecentSearch = {}, onClearRecentSearches = {},
                    onDiscover = {}, onLoadMore = {}, onOpenBook = {}, onCloseDetail = {}, onViewSource = {},
                    onRetryDetail = {}, onStopDetail = {},
                    onAddToShelf = { added.incrementAndGet(); displayed.value = displayed.value.copy(addingToShelf = true) },
                    onRead = { read.incrementAndGet() }, onDownload = { cached.incrementAndGet() },
                    onCancelDownload = {}, onChapterClick = {},
                )
            }
        }
        rule.onNodeWithText("先加入书架", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("开始阅读").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click(center) }
        rule.onNodeWithText("离线下载").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click(center) }
        assertEquals(0, read.get())
        assertEquals(0, cached.get())
        assertEquals(0, added.get())
        deviceWindowEvidenceV46("v59-catalogue-needs-shelf")
        rule.onNodeWithText("加入书架").assertIsEnabled().performClick()
        assertEquals(1, added.get())
        rule.onNodeWithText("正在加入书架", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("开始阅读").assertIsNotEnabled()
        rule.onNodeWithText("离线下载").assertIsNotEnabled()
        assertEquals(0, read.get())
        assertEquals(0, cached.get())
        rule.runOnUiThread {
            displayed.value = displayed.value.copy(addingToShelf = false,
                detail = displayed.value.detail!!.copy(shelfStoryId = "fixture-shelf-v59"))
        }
        rule.onNodeWithText("先加入书架", substring = true).assertDoesNotExist()
        rule.onNodeWithText("已在书架").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click(center) }
        assertEquals(1, added.get())
        rule.onNodeWithText("开始阅读").assertIsEnabled().performClick()
        rule.onNodeWithText("离线下载").assertIsEnabled().performClick()
        assertEquals(1, read.get())
        assertEquals(1, cached.get())
        deviceWindowEvidenceV46("v59-catalogue-ready-to-read")
    }

    @Test fun cacheCancellationRemainsReachableWhileCatalogueIsLoadingOrFailed() {
        val cancelled = AtomicInteger()
        val displayed = mutableStateOf(OnlineBooksStateV36(
            detail = OnlineDetailV36(book("cache"), emptyList()), detailLoading = true,
            download = OnlineDownloadV36(1, 3, 0),
        ))
        rule.setContent {
            LanghuanStableTheme {
                val state = displayed.value
                OnlineBooksScreenV50(
                    state, "", emptyList(), embedded = true,
                    onBack = {}, onManageSources = {}, onQueryChange = {}, onSearch = {},
                    onStopSearch = {}, onRetrySearch = {}, onRecentSearch = {}, onClearRecentSearches = {},
                    onDiscover = {}, onLoadMore = {}, onOpenBook = {}, onCloseDetail = {}, onViewSource = {},
                    onRetryDetail = {}, onStopDetail = {}, onAddToShelf = {}, onRead = {}, onDownload = {},
                    onCancelDownload = { cancelled.incrementAndGet(); displayed.value = displayed.value.copy(download = null) },
                    onChapterClick = {},
                )
            }
        }
        rule.onNodeWithText("加入书架").assertDoesNotExist()
        rule.onNodeWithContentDescription("取消下载").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, cancelled.get())
        rule.onNodeWithContentDescription("取消下载").assertDoesNotExist()
        rule.runOnUiThread {
            displayed.value = displayed.value.copy(detailLoading = false, detailError = "合成目录错误",
                error = "合成目录错误", download = OnlineDownloadV36(2, 3, 0))
        }
        rule.onNodeWithText("目录读取失败").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("取消下载").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(2, cancelled.get())
        assertEquals(book("cache"), displayed.value.detail!!.book)
        rule.onNodeWithContentDescription("取消下载").assertDoesNotExist()
    }
}
