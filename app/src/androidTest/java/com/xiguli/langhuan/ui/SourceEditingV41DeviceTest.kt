package com.xiguli.langhuan.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Interruption regression through the real launcher, not a screenshot-only mock. */
class SourceEditingV41DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun sourceDraftSurvivesActivityRecreationAndCancelDoesNotWrite() {
        lateinit var vm: OnlineBooksViewModelV36
        val id = "qa-source-editor-v41"
        rule.runOnUiThread {
            vm = ViewModelProvider(rule.activity)[OnlineBooksViewModelV36::class.java]
            vm.importSources("""{"id":"$id","name":"编辑测试书源","baseUrl":"https://example.com","searchUrl":"/search?q={{key}}","searchList":".book","searchName":"a@text","searchBookUrl":"a@href"}""")
        }
        rule.waitUntil(20000) { vm.state.value.sources.any { it.id == id } }
        rule.waitUntil(20000) { rule.onAllNodesWithContentDescription("在线书城").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("在线书城").onLast().performClick()
        rule.onNodeWithText("书源管理").performClick()
        rule.onNodeWithText("编辑测试书源").performClick()
        // The visible header action and bottom operation both edit the same source.
        rule.onAllNodesWithText("编辑规则").onFirst().assertIsDisplayed().performClick()
        rule.onNodeWithText("书源 JSON").performTextReplacement("{\"unfinished\":")
        assertEquals("{\"unfinished\":", vm.state.value.sourceEditDraft)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(20000) { rule.onAllNodesWithText("书源 JSON").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("书源 JSON").assertTextContains("{\"unfinished\":")
        rule.onNodeWithText("取消", useUnmergedTree = true).performClick()
        rule.runOnIdle {
            assertEquals(null, vm.state.value.sourceEditId)
            assertNotNull(vm.state.value.sources.firstOrNull { it.id == id && it.name == "编辑测试书源" })
            vm.deleteSource(id)
        }
    }
}
