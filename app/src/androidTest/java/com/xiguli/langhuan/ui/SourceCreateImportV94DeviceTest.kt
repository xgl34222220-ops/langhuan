package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.MainActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V94 书源制作与导入，全程走真实的 MainActivity：底栏 → 书城 → 书源管理 → 新建（表单）/ 导入（粘贴
 * 阅读格式 JSON，含单引号请求选项）→ 结果卡片。也顺带截取底栏四个标签页供视觉检查。
 */
class SourceCreateImportV94DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val createdId = "https://create-v94.example"
    private val importedIds = listOf("https://import-a-v94.example", "https://import-b-v94.example")

    private fun screenshot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(600)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "mkdir -p /sdcard/Download/reader-qa"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${File(dir, "$name.png").absolutePath} /sdcard/Download/reader-qa/$name.png"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun tab(key: String) {
        rule.waitUntil(20000) { rule.onAllNodesWithTag("bottom-tab-$key").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("bottom-tab-$key").performClick()
        rule.waitForIdle()
    }

    @Test fun bottomBarTabsThenCreateAndImportSources() {
        lateinit var vm: OnlineBooksViewModelV36
        rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[OnlineBooksViewModelV36::class.java] }
        rule.runOnIdle { (listOf(createdId) + importedIds).forEach { id -> if (vm.state.value.sources.any { it.id == id }) vm.deleteSource(id) } }
        try {
            // Bottom bar: every tab root shows the bar; each tab keeps its own page.
            tab("SHELF")
            rule.onNodeWithTag("langhuan-bottom-bar").assertIsDisplayed()
            screenshot("v94-tab-shelf")
            tab("CREATE_HUB")
            rule.onNodeWithText("空白新书").assertIsDisplayed()
            screenshot("v94-tab-create")
            tab("MINE")
            rule.onNodeWithText("书源管理").assertIsDisplayed()
            screenshot("v94-tab-mine")
            tab("ONLINE")
            rule.onNodeWithText("书源管理").assertIsDisplayed()
            screenshot("v94-tab-online")

            // 新建书源 through the form.
            rule.onNodeWithText("书源管理").performClick()
            rule.onNodeWithText("新建").performClick()
            rule.onNodeWithText("新建书源").assertIsDisplayed()
            // Sub-pages hide the bottom bar.
            assertTrue(rule.onAllNodesWithTag("langhuan-bottom-bar").fetchSemanticsNodes().isEmpty())
            // Saving an empty skeleton is refused with a reason and keeps the editor open.
            rule.onNodeWithText("保存规则").performClick()
            rule.waitUntil(10000) { vm.state.value.sourceEditError != null }
            assertEquals(SOURCE_NEW_ID_V94, vm.state.value.sourceEditId)
            rule.onNodeWithTag("source-field-name").performTextInput("手工测试书源")
            rule.onNodeWithTag("source-field-baseUrl").performTextReplacement(createdId)
            rule.onNodeWithTag("source-field-searchList").performScrollTo().performTextInput("class.item")
            rule.onNodeWithTag("source-field-searchName").performScrollTo().performTextInput("tag.a@text")
            rule.onNodeWithTag("source-field-searchBookUrl").performScrollTo().performTextInput("tag.a@href")
            screenshot("v94-source-create")
            rule.onNodeWithText("保存规则").performClick()
            rule.waitUntil(15000) { vm.state.value.sources.any { it.id == createdId } }
            rule.waitUntil(10000) { vm.state.value.sourceEditId == null }
            val created = vm.state.value.sources.first { it.id == createdId }
            assertEquals("手工测试书源", created.name)
            assertEquals("class.item", created.searchList)
            rule.waitUntil(10000) { rule.onAllNodesWithText("手工测试书源").fetchSemanticsNodes().isNotEmpty() }
            screenshot("v94-source-list")

            // 导入：paste a Legado array with single-quoted request options and one script-only source.
            rule.onNodeWithText("导入").performClick()
            rule.onNodeWithText("导入书源").assertIsDisplayed()
            val legado = """[
              {"bookSourceName":"导入甲V94","bookSourceUrl":"${importedIds[0]}","bookSourceGroup":"测试",
               "searchUrl":"/modules/article/search.php,{'charset':'gbk','method':'POST','body':'searchkey={{key}}'}",
               "ruleSearch":{"bookList":"class.item","name":"tag.a@text","bookUrl":"tag.a@href","coverUrl":"tag.img@src@js:result"}},
              {"bookSourceName":"导入乙V94","bookSourceUrl":"${importedIds[1]}","searchUrl":"/s?q={{key}}",
               "ruleSearch":{"bookList":"li","name":"a@text","bookUrl":"a@href"}},
              {"bookSourceName":"脚本源V94","bookSourceUrl":"https://script-v94.example","searchUrl":"@js:java.ajax('x')",
               "ruleSearch":{"bookList":"$.data","name":"$.name"}}
            ]"""
            rule.onNodeWithTag("source-import-text").performTextInput(legado)
            screenshot("v94-source-import")
            rule.onNodeWithText("导入粘贴的书源").performClick()
            rule.waitUntil(15000) { importedIds.all { id -> vm.state.value.sources.any { it.id == id } } }
            rule.waitUntil(10000) { vm.state.value.sourceImportReport != null }
            val report = vm.state.value.sourceImportReport
            assertNotNull(report)
            assertEquals(2, report!!.added.size)
            assertTrue(report.skipped.single(), report.skipped.single().startsWith("脚本源V94"))
            rule.waitUntil(10000) { rule.onAllNodesWithText("已导入 2 个书源").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("跳过 1 个：").assertIsDisplayed()
            screenshot("v94-source-import-report")
            val imported = vm.state.value.sources.first { it.id == importedIds[0] }
            assertEquals("POST", buildSearchRequestV36(imported, "长夜").method)
            assertEquals("", imported.searchCover)
            // The dynamic group chip comes from the imported source.
            rule.onAllNodesWithText("测试").fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        } finally {
            rule.runOnIdle {
                vm.cancelSourceEdit()
                vm.dismissSourceImportReport()
                (listOf(createdId) + importedIds).forEach { id -> if (vm.state.value.sources.any { it.id == id }) vm.deleteSource(id) }
            }
        }
    }
}
