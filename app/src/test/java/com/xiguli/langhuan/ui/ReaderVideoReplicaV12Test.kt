package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderVideoReplicaV12Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun shelfMatchesRecordedReaderInformationArchitecture() {
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV8.kt").readText()
        listOf("正在阅读", "书架列表", "查看详情", "移动书架", "删除图书", "新建书架", "个人中心")
            .forEach { label -> assertTrue("missing $label", source.contains(label)) }
        assertTrue(source.contains("GridCells.Fixed(3)"))
        assertTrue(source.contains("book_shelf_"))
    }

    @Test
    fun readerMatchesRecordedControlAndThemeStructure() {
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderFirstBookV11.kt").readText()
        listOf("详情", "目录", "更多", "日间主题", "夜间主题", "全文搜索", "仿真翻页", "时间电量", "进入故事")
            .forEach { label -> assertTrue("missing $label", source.contains(label)) }
        assertTrue(source.contains("GridCells.Fixed(4)"))
        assertTrue(source.contains("GridCells.Fixed(2)"))
    }
}
