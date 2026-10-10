package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.ui.design.TabReselectBusV95
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V95 brand refresh on the real MainActivity: the launch screen hands over to the shelf, the new
 * tab bar selects/reselects (reselect publishes a scroll-to-top signal), the large titles render,
 * and 「空白新书」 opens as a bottom sheet that 取消 closes without creating anything.
 * Screenshots feed the PR contact sheet.
 */
class BrandRefreshV95DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(700)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${File(dir, "$name.png").absolutePath} /sdcard/Download/reader-qa/$name.png"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun tab(key: String) {
        rule.waitUntil(20000) { rule.onAllNodesWithTag("bottom-tab-$key").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("bottom-tab-$key").performClick()
        rule.waitForIdle()
    }

    @Test fun tabBarReselectLargeTitlesAndBlankBookSheet() {
        rule.waitUntil(20000) { rule.onAllNodesWithTag("langhuan-bottom-bar").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("正在检查琅嬛数据…").fetchSemanticsNodes().isEmpty())

        tab("MINE")
        rule.onNodeWithTag("bottom-tab-MINE").assertIsSelected()
        rule.onNodeWithText("我的").assertIsDisplayed()
        screenshot("v95-tab-mine")
        val before = TabReselectBusV95.current.token
        tab("MINE")
        assertEquals("MINE", TabReselectBusV95.current.key)
        assertEquals(before + 1, TabReselectBusV95.current.token)
        rule.onNodeWithTag("bottom-tab-MINE").assertIsSelected()

        tab("ONLINE")
        rule.onNodeWithText("书城").assertIsDisplayed()
        screenshot("v95-tab-online")

        tab("CREATE_HUB")
        screenshot("v95-tab-create")
        rule.onNodeWithText("空白新书").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("创建并开写").fetchSemanticsNodes().isNotEmpty() }
        screenshot("v95-create-blank-sheet")
        rule.onNodeWithText("取消").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("创建并开写").fetchSemanticsNodes().isEmpty() }

        tab("SHELF")
        rule.onNodeWithTag("bottom-tab-SHELF").assertIsSelected()
        screenshot("v95-tab-shelf")
    }
}
