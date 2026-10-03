package com.xiguli.langhuan.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderBookmarkV49DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private class Fixture {
        val token = java.util.UUID.randomUUID().toString()
        val a = ReaderBookUi("bookmark-A-$token", "书签隔离测试 A", "", "", "", "", 0, 1000, 1, 1)
        val b = a.copy(id = "bookmark-B-$token", title = "书签隔离测试 B")
        val current = mutableStateOf(a)
        val visible = mutableStateOf(true)
        fun chapters() = listOf(1,2).map { n -> ChapterDraft("${current.value.id}-$n", current.value.id, n, "第${n}章 测试", "", emptyList(), "合成的书签隔离测试正文。".repeat(15)) }
    }
    private fun withFixture(legacy: Set<String> = setOf("1"), corruptA: Boolean = false, check: (Fixture) -> Unit) {
        val f = Fixture()
        val prefs = rule.activity.getSharedPreferences("reader_qingmo_v9", 0)
        val previous = prefs.getStringSet("bookmarks", null)?.toSet()
        val existed = prefs.contains("bookmarks")
        val settingName = "bookmark-settings-${f.token}"
        lateinit var settings: ReaderSettingsV30
        rule.runOnUiThread {
            prefs.edit().putStringSet("bookmarks", legacy).commit()
            if (corruptA) prefs.edit().putString("bookmarks_v49_${f.a.id}", "fixture-corrupt-value").commit()
            settings = ReaderSettingsV30(rule.activity.getSharedPreferences(settingName, 0)).apply { turnMode = ReaderTurnModeV30.NONE; clickAnimation = false }
        }
        try {
            rule.setContent {
                PaperReaderThemeV44 {
                    if (f.visible.value) key(f.current.value.id) {
                        val chapters = f.chapters()
                        ReaderSessionV30(f.current.value, chapters, chapters.first().id, settings, true, true, {}, {}, {}, {}, {})
                    }
                }
            }
            rule.waitForIdle()
            check(f)
        } finally {
            rule.runOnIdle { f.visible.value = false }
            rule.waitForIdle()
            val edit = prefs.edit().remove("bookmarks_v49_${f.a.id}").remove("bookmarks_v49_${f.b.id}")
            if (existed) edit.putStringSet("bookmarks", previous) else edit.remove("bookmarks")
            edit.commit()
            for (name in listOf("reader_progress_v1", "reader_progress_v2")) {
                val progress = rule.activity.getSharedPreferences(name, 0)
                progress.edit().also { editor -> progress.all.keys.filter { it.endsWith("_${f.a.id}") || it.endsWith("_${f.b.id}") }.forEach(editor::remove) }.commit()
            }
            rule.activity.deleteSharedPreferences(settingName)
        }
    }

    @Test fun markingAndUnmarkingOneBookDoesNotChangeTheOtherBook() = withFixture { f ->
        rule.onNodeWithContentDescription("添加本章书签").assertExists().performClick()
        rule.onNodeWithContentDescription("取消本章书签").assertExists()
        rule.runOnIdle { f.current.value = f.b }
        rule.onNodeWithContentDescription("添加本章书签").assertExists().performClick()
        rule.runOnIdle { f.current.value = f.a }
        rule.onNodeWithContentDescription("取消本章书签").performClick()
        rule.onNodeWithContentDescription("添加本章书签").assertExists()
        rule.runOnIdle { f.current.value = f.b }
        rule.onNodeWithContentDescription("取消本章书签").assertExists()
        val prefs = rule.activity.getSharedPreferences("reader_qingmo_v9", 0)
        assertEquals(emptySet<String>(), ReaderBookmarkStoreV49.load(prefs, f.a.id).getOrThrow())
        assertEquals(setOf("1"), ReaderBookmarkStoreV49.load(prefs, f.b.id).getOrThrow())
        assertEquals(setOf("1"), prefs.getStringSet("bookmarks", null))
    }

    @Test fun legacyRecoveryRequiresAnExplicitChoiceAndCancelKeepsAllData() = withFixture(setOf("1", "99")) { f ->
        val prefs = rule.activity.getSharedPreferences("reader_qingmo_v9", 0)
        rule.onNodeWithText("目录").performClick()
        rule.onNodeWithText("书签 0").performClick()
        rule.onNodeWithText("旧版").performClick()
        rule.onNodeWithText("旧版暂存").performClick()
        rule.onNodeWithText("旧版书签暂存").assertExists()
        rule.onNodeWithText("关闭").performClick()
        assertTrue(ReaderBookmarkStoreV49.load(prefs, f.a.id).getOrThrow().isEmpty())
        assertEquals(setOf("1", "99"), prefs.getStringSet("bookmarks", null))
        rule.onNodeWithText("旧版暂存").performClick()
        rule.onNodeWithText("另有 1 条超出本书目录，仍保留在暂存中。").assertExists()
        rule.onNodeWithText("归入本书").performClick()
        rule.onNodeWithText("已归入").assertExists()
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        deviceWindowEvidenceV46("v49-legacy-bookmark-recovery")
        assertEquals("com.xiguli.langhuan", automation.rootInActiveWindow?.packageName?.toString())
        assertEquals(setOf("1"), ReaderBookmarkStoreV49.load(prefs, f.a.id).getOrThrow())
        assertTrue(ReaderBookmarkStoreV49.load(prefs, f.b.id).getOrThrow().isEmpty())
        assertEquals(setOf("1", "99"), prefs.getStringSet("bookmarks", null))
        rule.onNodeWithText("关闭").performClick()
        rule.onNodeWithText("书签 1").assertExists()
    }

    @Test fun corruptBookCannotCrashOrOverwriteItsBookmarksAndOtherBookStillWorks() = withFixture(corruptA = true) { f ->
        val prefs = rule.activity.getSharedPreferences("reader_qingmo_v9", 0)
        rule.onNodeWithContentDescription("添加本章书签").performClick()
        rule.onNodeWithText("目录").performClick()
        rule.onNodeWithText("书签 0").performClick()
        rule.onAllNodesWithText("本书书签无法完整读取，原始数据已保留，暂停修改").assertAny(hasText("本书书签无法完整读取，原始数据已保留，暂停修改"))
        assertEquals("fixture-corrupt-value", prefs.getString("bookmarks_v49_${f.a.id}", null))
        rule.runOnIdle { f.current.value = f.b }
        rule.onNodeWithContentDescription("添加本章书签").performClick()
        assertEquals(setOf("1"), ReaderBookmarkStoreV49.load(prefs, f.b.id).getOrThrow())
        assertEquals("fixture-corrupt-value", prefs.getString("bookmarks_v49_${f.a.id}", null))
    }
}
