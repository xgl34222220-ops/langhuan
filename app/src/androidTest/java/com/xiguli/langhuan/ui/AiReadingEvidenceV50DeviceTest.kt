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
import org.junit.Rule
import org.junit.Test

/** An explicitly synthetic result rendered in an actual ModalBottomSheet window. No AI/network call. */
@OptIn(ExperimentalMaterial3Api::class)
class AiReadingEvidenceV50DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun resultNamesItsSampleAndUnknownsWithoutClaimingAllBooksWereVerified() {
        val source = BookSourceV36("fixture", "离线测试站", "https://books.example")
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
        rule.onNodeWithText("「离线测试站」规则抽样完成").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("未找到完整目录证据，当前 36 条不能视为全书总章数").assertExists()
        saveFrame("v50-ai-reading-evidence-test-data")
        rule.onNodeWithText("小说分类 · 本页解析 25 本 · 抽查 1 本").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("正文抽样 · 第一章 开场").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("https://books.example/read/1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(sourceSampleScopeV50(chapter)).assertExists()
        saveFrame("v50-ai-chapter-provenance-test-data")
        rule.onNodeWithText("保存书源").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, saved) }
        rule.onNodeWithText("AI 生成书源").performScrollTo().assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        rule.waitUntil(10_000) { !open }
        rule.onNodeWithText("AI 抽样结果组件测试").assertIsDisplayed()
    }

    private fun saveFrame(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val foreground = automation.rootInActiveWindow?.packageName?.toString()
        if (foreground != rule.activity.packageName) deviceWindowEvidenceV46("v50-ai-foreground-failure")
        assertEquals(rule.activity.packageName, foreground)
        val bitmap = automation.takeScreenshot() ?: error("No compositor screenshot")
        val file = File(rule.activity.getExternalFilesDir(null), "reader-qa/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        automation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}
