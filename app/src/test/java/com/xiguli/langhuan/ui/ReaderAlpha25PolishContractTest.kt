package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAlpha25PolishContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun tabHighlightStaysAtTopInsteadOfCrossingLabel() {
        val kit = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanComponentKitV4.kt")
        val tabs = kit.substringAfter("internal fun LanghuanTabsV4(").substringBefore("internal fun LanghuanActionTileV4(")
        assertTrue(tabs.contains(".background(bg)"))
        assertTrue(tabs.contains(".height(6.dp)"))
        assertTrue(tabs.contains("Color.Transparent"))
        assertFalse(tabs.contains(".background(v4DepthBrush(bg"))
    }

    @Test
    fun chapterChangesDoNotRearmLifecycleMountDelay() {
        val native = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt")
        assertTrue(native.contains("var resumeRequested by remember {"))
        assertTrue(native.contains("var readerMounted by remember {"))
        assertTrue(native.contains("LaunchedEffect(resumeRequested)"))
        assertFalse(native.contains("remember(chapterKey)"))
        assertFalse(native.contains("LaunchedEffect(resumeRequested, chapterKey)"))
        assertTrue(native.contains("key(chapterKey)"))
    }

    @Test
    fun readerUsesNaturalChineseParagraphLayout() {
        val hero = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        val pagination = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertFalse(hero.contains("textAlign = TextAlign.Justify"))
        assertTrue(hero.contains("textAlign = TextAlign.Start"))
        assertTrue(hero.contains("paragraph.trim(),"))
        assertTrue(hero.contains("恢复推荐排版"))
        assertTrue(hero.contains("onLine(1.65f); onParagraph(8f); onPadding(22f)"))
        assertTrue(pagination.contains("textAlign = TextAlign.Start"))
        assertTrue(pagination.contains(".map { it.trim() }"))
        assertTrue(pagination.contains(".filter { it.isNotBlank() }"))
    }
}

