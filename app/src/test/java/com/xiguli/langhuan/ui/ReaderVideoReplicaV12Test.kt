package com.xiguli.langhuan.ui

import java.io.File
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
    fun matureReaderKeepsEssentialControlsAndContinuousReading() {
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt").readText()
        listOf(
            "目录",
            "搜索",
            "排版",
            "书签",
            "批注",
            "进入故事",
            "首行缩进",
            "段距",
            "继续向下滑动",
            "自动进入下一章",
        ).forEach { label -> assertTrue("missing $label", source.contains(label)) }
        assertTrue(source.contains("progressRestored"))
        assertTrue(source.contains("textOffset"))
        assertTrue(source.contains("positionFraction"))
        assertTrue(source.contains("page >= pages.size"))
        assertTrue(source.contains("scrollState.isScrollInProgress"))
        assertTrue(source.contains("readerBuiltInPresetsV12()"))
        assertTrue(source.contains("ReaderReadingStoreV11"))
        assertTrue(source.contains("EpubOriginalTocV1"))
    }
}
