package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWorkspaceSeparationContractTest {
    @Test
    fun activeReaderStaysReadingOnlyAndWorkspaceOwnsCreation() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val reader = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt",
        ).readText()
        val workspace = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/workspace/LanghuanBookWorkspaceV1.kt",
        ).readText()

        assertTrue(reader.contains("ReaderQingmoHeroV13("))
        assertTrue(reader.contains("\"详情\", \"目录\", \"设置\""))
        assertTrue(reader.contains("全文搜索"))
        assertTrue(reader.contains("排版预设"))
        assertTrue(reader.contains("返回作品"))
        assertFalse(reader.contains("LanghuanRowV4(\"编辑本章\""))
        assertFalse(reader.contains("LanghuanRowV4(\"AI 创作\""))
        assertFalse(reader.contains("LanghuanRowV4(\"进入故事\""))

        assertTrue(workspace.contains("BookEditPageV5("))
        assertTrue(workspace.contains("\"写作\""))
        assertTrue(workspace.contains("\"大纲与章纲\""))
        assertTrue(workspace.contains("\"故事\""))
        assertTrue(workspace.contains("\"世界与规则\""))
        assertTrue(workspace.contains("\"角色与关系\""))
        assertTrue(workspace.contains("\"时间线与伏笔\""))
        assertTrue(workspace.contains("\"项目记忆\""))
        assertTrue(workspace.contains("\"AI 助手\""))
    }
}
