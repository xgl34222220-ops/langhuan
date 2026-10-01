package com.xiguli.langhuan.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceStorageUiV48DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun corruptSourcesShowProtectedStateAndKeepRawExportAvailable() {
        val app = rule.activity.application as Application
        val prefs = app.getSharedPreferences("book_sources_v36", 0)
        val previous = prefs.getString("sources", null)
        val corrupt = "[{\"id\":\"synthetic-incomplete-entry\"}]"
        var vm: OnlineBooksViewModelV36? = null
        try {
            prefs.edit().putString("sources", corrupt).commit()
            rule.runOnUiThread { vm = OnlineBooksViewModelV36(app) }
            rule.setContent { OnlineBooksPageV36(checkNotNull(vm), {}, {}, startWithSources = true) }
            rule.onNodeWithText("读取异常 · 原始配置已保留").assertExists()
            rule.onNodeWithText("连接你的阅读世界").assertDoesNotExist()
            rule.onNodeWithText("导出原始数据").assertIsEnabled().performClick()
            rule.onNodeWithText("复制原始书源数据").assertExists()
            rule.onNodeWithText("取消").performClick()
            assertEquals(corrupt, vm!!.exportSources())
            assertEquals(corrupt, prefs.getString("sources", null))
        } finally {
            rule.runOnUiThread { vm?.viewModelScope?.cancel() }
            val editor = prefs.edit()
            if (previous == null) editor.remove("sources") else editor.putString("sources", previous)
            editor.commit()
        }
    }
}
