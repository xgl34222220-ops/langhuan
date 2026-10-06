package com.xiguli.langhuan.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real ViewModel/default engine/WebView and visible V50 controls, with legal fixture faults. */
class SourceSearchRecoveryV57DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val base = "https://browser-fixture.example"
    private val keyword = "原创"

    private fun source(path: String, name: String) = BookSourceV36(
        "search-v57-$path", name, base,
        searchUrl = "/v57/$path?key={{key}}", searchList = ".book", searchName = "h2@text", searchBookUrl = "a@href",
        infoName = "h1@text", tocList = "#chapters a", tocName = "@text", tocUrl = "@href", contentText = ".content@html",
        useBrowser = true,
    )

    private fun book(path: String, title: String) = "<li class='book'><a href='/v57/book/$path'><h2>$title</h2></a></li>"

    private fun withScreen(sources: List<BookSourceV36>, pages: (SourceRequestV36) -> String, checks: (OnlineBooksViewModelV36) -> Unit) {
        val app = rule.activity.application as Application
        val previous = BookSourceStoreV36.load(app)
        var vm: OnlineBooksViewModelV36? = null
        try {
            BookSourceStoreV36.save(app, sources)
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
                    assertEquals("Search/cancel/retry must not rewrite saved sources", stored, BookSourceStoreV36.raw(app))
                } finally {
                    rule.runOnUiThread { vm?.viewModelScope?.cancel() }
                    rule.waitUntil(15_000) { vm?.viewModelScope?.coroutineContext?.get(Job)?.isCompleted != false }
                }
            }
        } finally { BookSourceStoreV36.save(app, previous) }
    }

    private fun retry() {
        rule.onNodeWithText("重试未完成书源").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }

    @Test fun aFailedSourceIsNamedAndRetryKeepsSuccessWithoutRefetchingIt() {
        val failing = AtomicBoolean(true)
        val goodCalls = AtomicInteger()
        val badCalls = AtomicInteger()
        val stable = source("stable", "稳定样例")
        val unstable = source("unstable", "异常样例")
        withScreen(listOf(stable, unstable), { request ->
            when {
                request.url.contains("/v57/stable?") -> { goodCalls.incrementAndGet(); book("stable", "原创稳定结果") }
                request.url.contains("/v57/unstable?") -> {
                    badCalls.incrementAndGet()
                    if (failing.get()) throw SourceHttpStatusExceptionV44(503, base, "合成服务暂不可用")
                    book("unstable", "原创恢复结果")
                }
                else -> error("Unexpected reserved-domain fixture: ${request.url}")
            }
        }) { vm ->
            rule.runOnUiThread { vm.search(keyword) }
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.searchedSources == 2 }
            assertEquals(1, vm.state.value.failedSources)
            assertEquals(listOf(stable.id), vm.state.value.results.map { it.sourceId })
            rule.onNodeWithText("书源搜索未完成").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("异常样例：", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("HTTP 503", substring = true).assertIsDisplayed()
            deviceWindowEvidenceV46("v57-search-partial-failure")
            failing.set(false)
            retry()
            assertEquals("Completed rows must remain while retry is running", listOf(stable.id), vm.state.value.results.map { it.sourceId })
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.results.size == 2 }
            assertEquals(setOf(stable.id, unstable.id), vm.state.value.results.map { it.sourceId }.toSet())
            assertEquals(1, goodCalls.get())
            assertEquals(2, badCalls.get())
            assertEquals(2, vm.state.value.searchedSources)
            assertEquals(0, vm.state.value.failedSources)
            assertTrue(vm.state.value.searchFailures.isEmpty())
            rule.onNodeWithText("书源搜索未完成").assertDoesNotExist()
        }
    }

    @Test fun slowSourceTimeoutIsExplainedAndRetryRecovers() {
        val timingOut = AtomicBoolean(true)
        val calls = AtomicInteger()
        val slow = source("timeout", "慢速样例")
        withScreen(listOf(slow), { request ->
            check(request.url.contains("/v57/timeout?")) { "Unexpected reserved-domain fixture: ${request.url}" }
            calls.incrementAndGet()
            if (timingOut.get()) throw SocketTimeoutException("Read timed out: browser-fixture.example")
            book("timeout", "原创慢源恢复结果")
        }) { vm ->
            rule.runOnUiThread { vm.search(keyword) }
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.failedSources == 1 }
            assertEquals(SOURCE_TIMEOUT_MESSAGE_V69, vm.state.value.searchFailures.single().reason)
            rule.onNodeWithText("慢速样例：", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(SOURCE_TIMEOUT_MESSAGE_V69, substring = true).assertIsDisplayed()
            rule.onNodeWithText("Read timed out", substring = true).assertDoesNotExist()
            deviceWindowEvidenceV46("v69-source-timeout-diagnostic")

            timingOut.set(false)
            retry()
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.results.size == 1 }
            assertEquals(2, calls.get())
            assertEquals("原创慢源恢复结果", vm.state.value.results.single().name)
            assertTrue(vm.state.value.searchFailures.isEmpty())
            rule.onNodeWithText("书源搜索未完成").assertDoesNotExist()
        }
    }

    @Test fun tlsCertificateFailureIsExplainedWithoutBypassAndRetryRecovers() {
        val failingTls = AtomicBoolean(true)
        val calls = AtomicInteger()
        val secure = source("tls", "安全连接样例")
        withScreen(listOf(secure), { request ->
            check(request.url.contains("/v57/tls?")) { "Unexpected reserved-domain fixture: ${request.url}" }
            calls.incrementAndGet()
            if (failingTls.get()) {
                throw SSLHandshakeException("Chain validation failed: CN=private.browser-fixture.example").apply {
                    initCause(CertificateException("Trust anchor for certification path not found"))
                }
            }
            book("tls", "原创安全连接恢复结果")
        }) { vm ->
            rule.runOnUiThread { vm.search(keyword) }
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.failedSources == 1 }
            assertEquals(SOURCE_TLS_MESSAGE_V70, vm.state.value.searchFailures.single().reason)
            rule.onNodeWithText("安全连接样例：", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(SOURCE_TLS_MESSAGE_V70, substring = true).assertIsDisplayed()
            rule.onNodeWithText("private.browser-fixture.example", substring = true).assertDoesNotExist()
            rule.onNodeWithText("Trust anchor", substring = true).assertDoesNotExist()
            rule.onNodeWithText("继续访问", substring = true).assertDoesNotExist()
            deviceWindowEvidenceV46("v70-source-tls-diagnostic")

            failingTls.set(false)
            retry()
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.results.size == 1 }
            assertEquals(2, calls.get())
            assertEquals("原创安全连接恢复结果", vm.state.value.results.single().name)
            assertTrue(vm.state.value.searchFailures.isEmpty())
            rule.onNodeWithText("书源搜索未完成").assertDoesNotExist()
        }
    }

    @Test fun dnsLookupFailureIsExplainedWithoutHostnameAndRetryRecovers() {
        val failingDns = AtomicBoolean(true)
        val calls = AtomicInteger()
        val unresolved = source("dns", "域名解析样例")
        withScreen(listOf(unresolved), { request ->
            check(request.url.contains("/v57/dns?")) { "Unexpected reserved-domain fixture: ${request.url}" }
            calls.incrementAndGet()
            if (failingDns.get()) {
                throw UnknownHostException(
                    "Unable to resolve host private.browser-fixture.example: No address associated with hostname",
                )
            }
            book("dns", "原创域名恢复结果")
        }) { vm ->
            rule.runOnUiThread { vm.search(keyword) }
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.failedSources == 1 }
            assertEquals(SOURCE_DNS_LOOKUP_MESSAGE_V71, vm.state.value.searchFailures.single().reason)
            rule.onNodeWithText("域名解析样例：", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(SOURCE_DNS_LOOKUP_MESSAGE_V71, substring = true).assertIsDisplayed()
            rule.onNodeWithText("private.browser-fixture.example", substring = true).assertDoesNotExist()
            rule.onNodeWithText("No address associated", substring = true).assertDoesNotExist()
            deviceWindowEvidenceV46("v71-source-dns-diagnostic")

            failingDns.set(false)
            retry()
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.results.size == 1 }
            assertEquals(2, calls.get())
            assertEquals("原创域名恢复结果", vm.state.value.results.single().name)
            assertTrue(vm.state.value.searchFailures.isEmpty())
            rule.onNodeWithText("书源搜索未完成").assertDoesNotExist()
        }
    }

    @Test fun refusedConnectionIsExplainedWithoutEndpointAndRetryRecovers() {
        val refusing = AtomicBoolean(true)
        val calls = AtomicInteger()
        val unavailable = source("refused", "连接拒绝样例")
        withScreen(listOf(unavailable), { request ->
            check(request.url.contains("/v57/refused?")) { "Unexpected reserved-domain fixture: ${request.url}" }
            calls.incrementAndGet()
            if (refusing.get()) {
                throw ConnectException(
                    "Failed to connect to private.browser-fixture.example/203.0.113.7:65535",
                )
            }
            book("refused", "原创连接恢复结果")
        }) { vm ->
            rule.runOnUiThread { vm.search(keyword) }
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.failedSources == 1 }
            assertEquals(SOURCE_CONNECTION_MESSAGE_V72, vm.state.value.searchFailures.single().reason)
            rule.onNodeWithText("连接拒绝样例：", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(SOURCE_CONNECTION_MESSAGE_V72, substring = true).assertIsDisplayed()
            rule.onNodeWithText("private.browser-fixture.example", substring = true).assertDoesNotExist()
            rule.onNodeWithText("203.0.113.7", substring = true).assertDoesNotExist()
            rule.onNodeWithText("65535", substring = true).assertDoesNotExist()
            deviceWindowEvidenceV46("v72-source-connection-diagnostic")

            refusing.set(false)
            retry()
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.results.size == 1 }
            assertEquals(2, calls.get())
            assertEquals("原创连接恢复结果", vm.state.value.results.single().name)
            assertTrue(vm.state.value.searchFailures.isEmpty())
            rule.onNodeWithText("书源搜索未完成").assertDoesNotExist()
        }
    }

    @Test fun stoppingASlowSourceKeepsCompletedRowsAndResumesOnlyUnfinishedWork() {
        val slowEntered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val goodCalls = AtomicInteger()
        val slowCalls = AtomicInteger()
        val stable = source("quick", "快速样例")
        val slow = source("slow", "慢网样例")
        try {
            withScreen(listOf(stable, slow), { request ->
                when {
                    request.url.contains("/v57/quick?") -> { goodCalls.incrementAndGet(); book("quick", "原创已完成结果") }
                    request.url.contains("/v57/slow?") -> {
                        if (slowCalls.incrementAndGet() == 1) {
                            slowEntered.countDown()
                            check(release.await(30, TimeUnit.SECONDS)) { "Controlled slow fixture was not cancelled" }
                        }
                        book("slow", "原创慢网恢复结果")
                    }
                    else -> error("Unexpected reserved-domain fixture: ${request.url}")
                }
            }) { vm ->
                rule.runOnUiThread { vm.search(keyword) }
                rule.waitUntil(20_000) { slowEntered.count == 0L && vm.state.value.results.size == 1 && vm.state.value.searching }
                rule.onNodeWithContentDescription("停止").performScrollTo().assertIsDisplayed().performClick()
                rule.waitUntil(5_000) { !vm.state.value.searching && vm.state.value.searchStopped }
                assertEquals(listOf(stable.id), vm.state.value.results.map { it.sourceId })
                assertEquals(setOf(slow.id), vm.state.value.pendingSearchSourceIds)
                assertEquals("Cancellation is not a failed source", 0, vm.state.value.failedSources)
                rule.onNodeWithText("搜索已停止").performScrollTo().assertIsDisplayed()
                deviceWindowEvidenceV46("v57-search-stopped-with-results")
                retry()
                rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.results.size == 2 }
                assertEquals(1, goodCalls.get())
                assertEquals(2, slowCalls.get())
                assertEquals(2, vm.state.value.searchedSources)
                assertFalse(vm.state.value.searchStopped)
                assertTrue(vm.state.value.pendingSearchSourceIds.isEmpty())
                assertTrue(vm.state.value.searchFailures.isEmpty())
                rule.onNodeWithText("搜索已停止").assertDoesNotExist()
            }
        } finally { release.countDown() }
    }

    @Test fun allSourceErrorsAndASuccessfulEmptySearchHaveDifferentVisibleOutcomes() {
        val failing = AtomicBoolean(true)
        withScreen(listOf(source("empty", "异常空结果样例")), {
            if (failing.get()) throw SourceHttpStatusExceptionV44(503, base, "合成服务暂不可用")
            "<h1>本次没有匹配的书籍</h1>"
        }) { vm ->
            rule.runOnUiThread { vm.search(keyword) }
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.failedSources == 1 }
            assertTrue(vm.state.value.results.isEmpty())
            rule.onNodeWithText("书源搜索未完成").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("没有找到「$keyword」").assertDoesNotExist()
            failing.set(false)
            retry()
            rule.waitUntil(20_000) { !vm.state.value.searching && vm.state.value.searchedSources == 1 && vm.state.value.failedSources == 0 }
            rule.onNodeWithText("没有找到「$keyword」").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("书源搜索未完成").assertDoesNotExist()
            assertTrue(vm.state.value.searchFailures.isEmpty())
            assertTrue(vm.state.value.pendingSearchSourceIds.isEmpty())
        }
    }
}
