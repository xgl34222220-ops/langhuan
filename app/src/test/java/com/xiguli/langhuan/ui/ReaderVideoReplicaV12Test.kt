package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderVideoReplicaV12Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun shelfMatchesCurrentReaderInformationArchitecture() {
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV9.kt").readText()
        listOf("图书", "书架", "我的", "AI 新建小说", "移动到其他书架", "进入故事")
            .forEach { label -> assertTrue("missing $label", source.contains(label)) }
        assertTrue(source.contains("GridCells.Fixed(3)"))
    }

    @Test
    fun matureReaderProtectsResumeInsetsCrossChapterAndTypographyState() {
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt").readText()
        val assets = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderAssetsV10.kt").readText()

        listOf(
            "目录", "搜索", "排版", "书签", "批注", "进入故事",
            "首行缩进", "段距", "继续向下滑动", "细调当前排版", "已应用", "已微调",
        ).forEach { label -> assertTrue("missing $label", reader.contains(label)) }

        // Resume must be gated until the saved chapter is resolved; temporary chapter-one rendering caused progress loss.
        assertTrue(reader.contains("ReaderResumeGate"))
        assertTrue(reader.contains("val chapter = state.readingChapter"))
        assertFalse(reader.contains("state.readingChapter ?: state.chapters.firstOrNull()"))

        // System bars / rounded corners and both horizontal chapter boundaries are first-class reader concerns.
        assertTrue(reader.contains("WindowInsets.safeDrawing"))
        assertTrue(reader.contains("leadingPageCount"))
        assertTrue(reader.contains("trailingPageCount"))
        assertTrue(reader.contains("pagerState.settledPage"))
        assertTrue(reader.contains("atEnd = true"))

        // Stable text anchors survive repagination; sliders only apply after release to avoid continuous page jitter.
        assertTrue(reader.contains("progressRestored"))
        assertTrue(reader.contains("textOffset"))
        assertTrue(reader.contains("positionFraction"))
        assertTrue(reader.contains("onValueChangeFinished"))
        assertTrue(reader.contains("readerBuiltInPresetsV12()"))
        assertTrue(reader.contains("ReaderReadingStoreV11"))
        assertTrue(reader.contains("EpubOriginalTocV1"))

        // Regression guard for the old phone-overflow paginator and oversized paragraph spacing.
        assertFalse(assets.contains("920f / densityPenalty"))
        assertFalse(assets.contains("coerceIn(420, 1500)"))
        assertTrue(assets.contains("paragraphSpacing: Float = 8f"))
    }
}
