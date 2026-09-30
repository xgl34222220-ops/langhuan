package com.xiguli.langhuan.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test

/** Runs real UI, ViewModel, public HTTPS, parsing and Room persistence against our original fixture. */
class SourceDiscoveryV43DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun source(base: String) = BookSourceV36(
        id = "discovery-ui-v43", name = "测试书站", baseUrl = base,
        searchUrl = "search.html?key={{key}}", searchList = ".book", searchName = "h2@text", searchBookUrl = "a@href",
        infoName = "h1@text", infoTocUrl = ".catalogue@href", infoIntro = ".intro@text",
        tocList = "#chapters a", tocName = "@text", tocUrl = "@href", contentText = ".content@html",
        exploreUrl = "分类::category-1.html&&月榜::rank-1.html", exploreList = ".book", exploreName = "h2@text",
        exploreAuthor = ".author@text", exploreBookUrl = "a@href",
    )

    @Test fun categoryRankPagingAndDownloadWorkThroughTheVisibleControls() = runBlocking {
        val base = InstrumentationRegistry.getArguments().getString("sourceFixtureBase")
        assumeNotNull(base)
        val context = rule.activity.applicationContext
        val priorSources = BookSourceStoreV36.load(context)
        val createdId = AtomicReference<String?>(null)
        lateinit var vm: OnlineBooksViewModelV36
        try {
            BookSourceStoreV36.save(context, listOf(source(base!!)))
            rule.runOnUiThread { vm = OnlineBooksViewModelV36(rule.activity.application as Application) }
            rule.setContent {
                LanghuanStableTheme {
                    OnlineBooksPageV36(vm, {}, { createdId.set(it) }, embedded = true)
                }
            }
            rule.onNodeWithText("测试书站 · 分类").performClick()
            rule.waitUntil(30000) { !vm.state.value.searching && vm.state.value.discoveryPage == 1 }
            rule.onNodeWithText("测试航行记").assertIsDisplayed()
            saveFrame("v43-category-test-data")
            rule.onNodeWithText("测试书站 · 月榜").performScrollTo().performClick()
            rule.waitUntil(30000) { !vm.state.value.searching && vm.state.value.discoveryLabel?.endsWith("月榜") == true && vm.state.value.discoveryPage == 1 }
            rule.onNodeWithText("加载更多").performScrollTo().performClick()
            rule.waitUntil(30000) { !vm.state.value.searching && vm.state.value.discoveryPage == 2 }
            assertEquals(listOf("测试航行记", "测试归港记"), vm.state.value.results.map { it.name })
            assertFalse(vm.state.value.discoveryHasMore)
            rule.onNodeWithText("测试归港记").assertIsDisplayed()
            rule.onNodeWithText("已加载 2 本 · 没有更多书籍").assertIsDisplayed()
            saveFrame("v43-rank-page2-test-data")
            rule.onNodeWithText("测试归港记").performClick()
            rule.waitUntil(30000) { !vm.state.value.detailLoading && vm.state.value.detail?.chapters?.size == 1 }
            rule.onNodeWithText("加入书架").performClick()
            rule.waitUntil(30000) { vm.state.value.detail?.shelfStoryId != null && !vm.state.value.addingToShelf }
            val savedId = vm.state.value.detail!!.shelfStoryId!!
            createdId.set(savedId)
            val before = StoryProjectManager(context).chapterDrafts(savedId)
            assertTrue("Collecting must not download any chapter body", before.all { it.content.isBlank() && it.sourceUrl.isNotBlank() })
            assertNull(vm.state.value.download)
            saveFrame("v46-collected-without-download")
            rule.onNodeWithText("离线下载").performScrollTo().performClick()
            rule.waitUntil(30000) { vm.state.value.download == null && vm.state.value.message?.contains("已离线缓存") == true }
            val chapters = StoryProjectManager(context).chapterDrafts(savedId)
            assertEquals(1, chapters.size)
            assertTrue(chapters.single().content.contains("归航的船停在港口"))
            assertTrue(chapters.single().content.contains("沿着岸边的小路走向家门"))
        } finally {
            BookSourceStoreV36.save(context, priorSources)
            createdId.get()?.let { id ->
                val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
                listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                    sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
                }
                context.getSharedPreferences("book_sources_v36", 0).edit().remove("link_$id").commit()
            }
        }
    }

    @Test fun sameAddressPostCategoriesHaveDistinctUiKeys() {
        val context = rule.activity.applicationContext
        val priorSources = BookSourceStoreV36.load(context)
        val fixture = source("https://books.example").copy(exploreUrl =
            "分类甲::/list,{\"method\":\"POST\",\"body\":\"category=a&page={{page}}\"}&&" +
                "分类乙::/list,{\"method\":\"POST\",\"body\":\"category=b&page={{page}}\"}")
        try {
            BookSourceStoreV36.save(context, listOf(fixture))
            lateinit var vm: OnlineBooksViewModelV36
            rule.runOnUiThread { vm = OnlineBooksViewModelV36(rule.activity.application as Application) }
            rule.setContent { LanghuanStableTheme { OnlineBooksPageV36(vm, {}, {}, embedded = true) } }
            rule.onNodeWithText("测试书站 · 分类甲").assertExists()
            rule.onNodeWithText("测试书站 · 分类乙").performScrollTo().assertIsDisplayed()
        } finally {
            BookSourceStoreV36.save(context, priorSources)
        }
    }

    private fun saveFrame(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val bitmap = if (name.startsWith("v46-")) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            assertEquals(rule.activity.packageName, automation.rootInActiveWindow?.packageName?.toString())
            requireNotNull(automation.takeScreenshot())
        } else rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
}
