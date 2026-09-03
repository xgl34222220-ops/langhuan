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
    fun readerMatchesCurrentControlAndThemeStructure() {
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderFirstBookV11.kt").readText()
        listOf(
            "详情",
            "目录",
            "阅读设置",
            "书签",
            "批注",
            "全文搜索",
            "阅读方案",
            "翻页方式",
            "进入故事",
            "纸张",
            "暖黄",
            "护眼",
            "夜间",
        ).forEach { label -> assertTrue("missing $label", source.contains(label)) }
        assertTrue(source.contains("GridCells.Fixed(3)"))
        assertTrue(source.contains("GridCells.Fixed(2)"))
        assertTrue(source.contains("ReaderPageModeV10.entries"))
        assertTrue(source.contains("ReaderReadingStoreV11"))
        assertTrue(source.contains("EpubOriginalTocV1"))
    }
}
