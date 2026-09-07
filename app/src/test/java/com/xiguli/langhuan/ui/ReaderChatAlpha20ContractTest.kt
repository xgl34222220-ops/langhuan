package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChatAlpha20ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun creationReplyDetectsAndStitchesCleanCutoff() {
        assertTrue(shouldAutoContinueCreationReplyV20("这是一段已经足够长的创作分析。".repeat(20) + "附件设计了"))
        assertFalse(shouldAutoContinueCreationReplyV20("这是一段已经正常完成的回答。".repeat(20)))
        assertEquals("前文重复片段继续", stitchCreationContinuationV20("前文重复片段", "重复片段继续"))
    }

    @Test
    fun creationComposerKeepsSpatialBackgroundVisible() {
        val creation = source("src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
        assertTrue(creation.contains("color = Color.Transparent"))
        assertTrue(creation.contains("creationChatDisplayTextV20(text)"))
        val glass = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanSpatialMotion.kt")
        assertTrue(glass.contains("t.card.copy(alpha = .74f)"))
    }

    @Test
    fun readerUsesStablePagedBaselineAndSpatialPreset() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("琅嬛星图"))
        assertTrue(reader.contains("LanghuanConstellationField"))
        assertTrue(reader.contains("val pagedParagraphSpacing = if (pageMode == ReaderPageModeV10.SCROLL) paragraphSpacing else 0f"))
        assertTrue(reader.contains("snapPositionalThreshold = 0.32f"))
        assertTrue(reader.contains("labels = listOf(\"详情\", \"目录\", \"设置\")"))
        val actionBlock = reader.substringAfter("val actions = listOf(").substringBefore("LazyVerticalGrid")
        assertFalse(actionBlock.contains("仿真翻页"))
        assertFalse(actionBlock.contains("全屏下一页"))
        assertFalse(actionBlock.contains("背景图遮罩"))
        assertFalse(actionBlock.contains("背景跟随"))
        assertFalse(actionBlock.contains("下拉书签"))
        assertTrue(actionBlock.contains("滚动阅读"))
    }

    @Test
    fun plainChatGetsMoreOutputHeadroom() {
        val gateway = source("src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt")
        assertTrue(gateway.contains("else -> if (prompt.jsonMode) 4_096 else 6_144"))
    }
}
