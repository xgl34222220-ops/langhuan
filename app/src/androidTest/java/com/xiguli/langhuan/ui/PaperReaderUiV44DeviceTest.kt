package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Screenshots are deterministic UI fixtures, never a claim that an AI service was called. */
class PaperReaderUiV44DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun aiFormShowsActualConfigurationAndUsesEnteredInputs() {
        val settings = AtomicInteger()
        val starts = mutableListOf<Pair<String, String>>()
        rule.setContent {
            LanghuanStableTheme { PaperReaderThemeV44 {
                Surface(Modifier.fillMaxSize()) {
                    OnlineAiSheetV36(OnlineBooksStateV36(), { site, keyword -> starts += site to keyword }, {}, {}, { settings.incrementAndGet() })
                }
            } }
        }
        rule.onNodeWithText("尚未配置服务").assertExists()
        rule.onNodeWithText("网站链接").performTextInput("https://example.com/")
        rule.onNodeWithText("该站能搜到的一本书名（用于测试）").performTextInput("测试航行记")
        hideKeyboard()
        rule.onNodeWithText("设置").performScrollTo().performClick()
        assertEquals(1, settings.get())
        rule.onNodeWithText("开始生成").performScrollTo().performClick()
        assertEquals(listOf("https://example.com/" to "测试航行记"), starts)
        rule.onNodeWithText("网站地址").performScrollTo()
        saveFrame("v44-ai-form-test-data")
    }

    @Test fun failedHomepageHasOneCompleteDiagnosisAndUsableRetry() {
        val error = "打不开这个网址：HTTP 400 · http://example.com。服务器拒绝了请求，请确认网址；若网站提供 HTTPS，可修改后重试。"
        val starts = AtomicInteger()
        val state = OnlineBooksStateV36(aiSteps = listOf(AiSourceStepV37("读取网站首页", false, error, true)), aiError = error)
        rule.setContent {
            LanghuanStableTheme { PaperReaderThemeV44 {
                Surface(Modifier.fillMaxSize()) { OnlineAiSheetV36(state, { _, _ -> starts.incrementAndGet() }, {}, {}, {}) }
            } }
        }
        rule.onNodeWithText("网站链接").performTextInput("http://example.com/")
        rule.onNodeWithText("该站能搜到的一本书名（用于测试）").performTextInput("测试航行记")
        hideKeyboard()
        rule.onAllNodesWithText(error).assertCountEquals(1)
        rule.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("重试").performScrollTo().assertIsDisplayed()
        saveFrame("v44-ai-http-error-test-data")
        rule.onNodeWithText("重试").performClick()
        assertEquals(1, starts.get())
        rule.onNodeWithText("AI 生成书源").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("网站链接").assertIsDisplayed()
        rule.onNodeWithText("网站链接").assertTextContains("http://example.com/")
    }

    @Test fun leavingNightReaderRestoresTheCurrentPaperRouteBars() {
        val prefs = rule.activity.getSharedPreferences("reader_qingmo_v9", 0)
        val prior = prefs.getString("theme", null)
        var reader by mutableStateOf(false)
        val controller = androidx.core.view.WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
        try {
            prefs.edit().putString("theme", "night").commit()
            rule.setContent {
                com.xiguli.langhuan.ui.design.PaperReaderSystemBarsV44(lightBackground = true, readerActive = reader)
                if (reader) ReaderWindowSessionV27(resumed = true)
            }
            rule.runOnIdle { assertEquals(true, controller.isAppearanceLightStatusBars); reader = true }
            rule.waitForIdle()
            rule.runOnIdle { assertEquals(false, controller.isAppearanceLightStatusBars); reader = false }
            rule.waitForIdle()
            rule.runOnIdle {
                assertEquals(true, controller.isAppearanceLightStatusBars)
                assertEquals(true, controller.isAppearanceLightNavigationBars)
            }
        } finally {
            prefs.edit().apply { if (prior == null) remove("theme") else putString("theme", prior) }.commit()
        }
    }

    private fun hideKeyboard() {
        rule.runOnUiThread {
            rule.activity.currentFocus?.clearFocus()
            val manager = rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            manager.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0)
        }
        rule.waitForIdle()
    }

    private fun saveFrame(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(rule.activity.getExternalFilesDir(null), "reader-qa/$name.png").apply { parentFile!!.mkdirs() }
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
