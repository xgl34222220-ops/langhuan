package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFirstDesignContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun readerChromeContainsOnlyReadingActions() {
        val reader = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt",
        ).readText()
        val chrome = reader.substringAfter("private fun MobileReaderChrome(")
            .substringBefore("private fun ReaderChromeIcon(")

        assertTrue(chrome.contains("\"目录\""))
        assertTrue(chrome.contains("\"A−\""))
        assertTrue(chrome.contains("\"背景\""))
        assertTrue(chrome.contains("\"A+\""))
        assertTrue(chrome.contains("\"排版\""))
        assertFalse(chrome.contains("\"故事\""))
        assertFalse(chrome.contains("\"编辑\""))
        assertFalse(chrome.contains("\"创作\""))
        assertFalse(chrome.contains("onStory"))
        assertFalse(chrome.contains("onEdit"))
        assertFalse(chrome.contains("onWriting"))
    }

    @Test
    fun settingsUseProgressiveDisclosureAndSoftSelection() {
        val reader = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt",
        ).readText()

        assertTrue(reader.contains("advancedOpen"))
        assertTrue(reader.contains("\"高级排版\""))
        assertTrue(reader.contains("color = if (selected) t.accent else t.card"))
        assertFalse(reader.contains("color = if (selected) t.foreground else t.card"))
    }

    @Test
    fun chineseParagraphsIndentEveryNaturalParagraph() {
        val reader = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt",
        ).readText()

        assertTrue(reader.contains("18.5f"))
        assertTrue(reader.contains("1.78f"))
        assertTrue(reader.contains("24f"))
        assertTrue(reader.contains("6f"))
        assertTrue(reader.contains("index > 0 || indentFirstParagraph"))
        assertTrue(reader.contains("reader_mobile_settings_v2"))
    }

    @Test
    fun designContractRejectsDashboardReadingUi() {
        val design = File(root.parentFile, "design-systems/langhuan/DESIGN.md")
        assertTrue(design.exists())
        val text = design.readText()
        assertTrue(text.contains("Content first, chrome recedes"))
        assertTrue(text.contains("禁止") && text.contains("AI / 编辑 / 故事"))
        assertTrue(text.contains("用“CI 通过”代替 UI/交互验收"))
    }
}
