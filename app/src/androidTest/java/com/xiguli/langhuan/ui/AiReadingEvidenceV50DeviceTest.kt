package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic result views; the first case uses an actual ModalBottomSheet. No AI/network call. */
@OptIn(ExperimentalMaterial3Api::class)
class AiReadingEvidenceV50DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun resultNamesItsSampleAndUnknownsWithoutClaimingAllBooksWereVerified() {
        val source = BookSourceV36("fixture", "离线测试站", "https://books.example", enabledExplore = true)
        val catalogue = SourceCatalogueProofV50("https://books.example/book/1", 1,
            warnings = listOf("未找到完整目录证据，当前 36 条不能视为全书总章数"))
        val chapter = SourceChapterProofV50("https://books.example/read/1", 1,
            SourceIdentityStateV50.UNKNOWN, SourceIdentityStateV50.UNKNOWN, emptyList(), emptyList())
        val report = AiSourceReportV37(source, 1, "原创演艺故事", 36, "「JTBC新劇官宣！」小說人物在讀新聞，隨即把手機放回桌上。",
            discoveryLabels = listOf("小说分类"),
            discoveryWarnings = listOf("空间：HTTP 429，本轮已停止访问；其余入口未验证"),
            discoveryEvidence = listOf(AiDiscoveryEvidenceV37("小说分类", "https://books.example/category", 25, "原创演艺故事", 36,
                sampleChapter = "第一章 开场", sampleChapterUrl = "https://books.example/read/1", catalogueProof = catalogue, chapterProof = chapter)),
            sampleBookUrl = "https://books.example/book/1", sampleChapter = "第一章 开场", sampleChapterUrl = "https://books.example/read/1",
            catalogueProof = catalogue, chapterProof = chapter, readingWarnings = catalogue.warnings)
        var open by mutableStateOf(true)
        var saved = 0
        rule.setContent {
            LanghuanStableTheme { PaperReaderThemeV44 {
                Surface(Modifier.fillMaxSize()) { Text("AI 抽样结果组件测试") }
                if (open) ModalBottomSheet(onDismissRequest = { open = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    OnlineAiSheetV36(OnlineBooksStateV36(aiReport = report), { _, _ -> }, {}, { saved++ }, {})
                }
            } }
        }
        rule.onAllNodesWithText("搜索与阅读测试通过", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("目录/正文已验证", substring = true).assertCountEquals(0)
        rule.onNodeWithText("「离线测试站」书源草稿").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("未找到完整目录证据，当前 36 条不能视为全书总章数").assertExists()
        saveFrame("v50-ai-reading-evidence-test-data", "书源草稿")
        rule.onNodeWithText("小说分类 · 本页解析 25 本 · 抽查 1 本").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("正文抽样 · 第一章 开场").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("https://books.example/read/1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(sourceSampleScopeV50(chapter)).assertExists()
        saveFrame("v50-ai-chapter-provenance-test-data", "https://books.example/read/1")
        rule.onNodeWithText("保存书源").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, saved) }
        rule.onNodeWithText("AI 生成书源").performScrollTo().assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        rule.waitUntil(10_000) { !open }
        rule.onNodeWithText("AI 抽样结果组件测试").assertIsDisplayed()
    }

    @Test fun optionalDiscoveryFailureShowsOneSummaryAndDeduplicatedExpandableDetails() {
        val limit = "分类较多，本次最多验证 12 个入口；其余可稍后编辑添加"
        val invalid = "发现规则格式未通过，已保留搜索与阅读规则"
        val noBooks = "玄幻：未取到书目，未添加此入口"
        val warnings = listOf(limit, limit, limit, invalid, noBooks)
        val state = OnlineBooksStateV36(
            aiSteps = listOf(AiSourceStepV37("识别并验证发现分类与排行榜", false,
                "发现入口未通过，搜索与阅读草稿已保留", true, warnings)),
            aiReport = AiSourceReportV37(BookSourceV36("fixture", "离线测试站", "https://books.example", enabledExplore = false),
                1, "原创故事", 36, "原创正文抽样。", discoveryWarnings = warnings),
        )
        rule.setContent { LanghuanStableTheme { PaperReaderThemeV44 {
            Surface(Modifier.fillMaxSize()) { OnlineAiSheetV36(state, { _, _ -> }, {}, {}, {}) }
        } } }
        rule.onAllNodesWithText(limit).assertCountEquals(0)
        rule.onAllNodesWithText("已停止，未保存书源", substring = true).assertCountEquals(0)
        rule.onNodeWithText("查看发现详情（3项）").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("查看第1步详情", substring = true).assertCountEquals(0)
        rule.onNodeWithText("查看发现详情（3项）").performClick()
        rule.onAllNodesWithText(limit).assertCountEquals(1)
        rule.onAllNodesWithText(invalid).assertCountEquals(1)
        rule.onAllNodesWithText(noBooks).assertCountEquals(1)
        rule.onNodeWithText("收起发现详情").performScrollTo().performClick()
        rule.onAllNodesWithText(limit).assertCountEquals(0)
        rule.onNodeWithText("保存搜索书源").performScrollTo().assertIsDisplayed()
        saveFrame("v51-ai-partial-discovery-summary-test-data", "保存搜索书源")
    }

    @Test fun shortDnsErrorStillExpandsWhenLargeTextOverflowsThreeLines() {
        val detail = "首页读取失败：" + SourceDnsBlockedV54(SourceDnsFailureV54.BENCHMARK_RANGE).message.orEmpty()
        assertTrue("Regression fixture must stay below the old 140-character threshold", detail.length < 140)
        val state = OnlineBooksStateV36(aiError = detail, aiSteps = listOf(
            AiSourceStepV37("读取网站首页", false, detail, true,
                listOf("已观察到的域名跳转：old.example → new.example"))))
        rule.setContent {
            val native = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(native.density, fontScale = 1.5f)) {
                LanghuanStableTheme { PaperReaderThemeV44 {
                    Surface(Modifier.fillMaxSize()) { OnlineAiSheetV36(state, { _, _ -> }, {}, {}, {}) }
                } }
            }
        }
        rule.onNodeWithText("展开完整提示").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText(detail).assertCountEquals(1)
        fun overflows(): Boolean {
            var overflow = false
            rule.onNodeWithText(detail).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(results))
                overflow = results.single().hasVisualOverflow
            }
            return overflow
        }
        assertTrue("The actual rendered message must overflow before expansion", overflows())
        rule.onNodeWithText("展开完整提示").performClick()
        rule.onNodeWithText("收起提示").performScrollTo().assertIsDisplayed()
        assertTrue("Expanded text must include its complete final sentence", !overflows())
        rule.onAllNodesWithText(detail).assertCountEquals(1)
        saveFrame("v55-ai-complete-dns-error-test-data", "无需关闭代理")
        rule.onNodeWithText("查看第1步详情（1项）").performScrollTo().performClick()
        rule.onNodeWithText("已观察到的域名跳转：old.example → new.example").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("重试").performScrollTo().assertIsDisplayed()
    }

    private fun saveFrame(name: String, visibleText: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // Compose semantics can be ready before the Dialog's native window and surface.
        // Wait for the actual accessibility window, not a guessed delay or the Activity alone.
        try {
            rule.waitUntil(10_000) {
                val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
                roots.any { root -> root.packageName?.toString() == rule.activity.packageName &&
                    containsVisibleText(root, visibleText) }
            }
        } catch (error: Throwable) {
            deviceWindowEvidenceV46("$name-native-window-not-ready")
            val dump = StringBuilder("expected=$visibleText\n")
            fun record(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0) {
                if (node == null || depth > 20 || dump.length > 60000) return
                dump.append(" ".repeat(depth)).append("visible=${node.isVisibleToUser} package=${node.packageName} text=${node.text} description=${node.contentDescription}\n")
                for (i in 0 until node.childCount) record(node.getChild(i), depth + 1)
            }
            record(automation.rootInActiveWindow)
            automation.windows.forEach { record(it.root) }
            val file = File(rule.activity.getExternalFilesDir(null), "reader-qa/$name-accessibility.txt")
            file.writeText(dump.toString())
            automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
            throw error
        }
        instrumentation.waitForIdleSync()
        val foreground = automation.rootInActiveWindow?.packageName?.toString()
        if (foreground != rule.activity.packageName) deviceWindowEvidenceV46("v50-ai-foreground-failure")
        assertEquals(rule.activity.packageName, foreground)
        val bitmap = automation.takeScreenshot() ?: error("No compositor screenshot")
        var bodyInk = 0
        for (y in bitmap.height / 3 until bitmap.height * 5 / 6 step 3) {
            for (x in bitmap.width / 20 until bitmap.width * 19 / 20 step 3) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) < 190 && android.graphics.Color.green(pixel) < 190 && android.graphics.Color.blue(pixel) < 190) bodyInk++
            }
        }
        if (bodyInk <= 80) deviceWindowEvidenceV46("v51-ai-visible-surface-missing")
        assertTrue("Native screenshot must contain the rendered form/result, not the empty Activity", bodyInk > 80)
        val file = File(rule.activity.getExternalFilesDir(null), "reader-qa/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        automation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun containsVisibleText(node: android.view.accessibility.AccessibilityNodeInfo, text: String, depth: Int = 0): Boolean {
        if (depth > 20) return false
        if (node.isVisibleToUser && (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true)) return true
        return (0 until node.childCount).any { index -> node.getChild(index)?.let { containsVisibleText(it, text, depth + 1) } == true }
    }
}
