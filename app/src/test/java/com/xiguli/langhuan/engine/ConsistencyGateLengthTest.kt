package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyGateLengthTest {
    @Test
    fun `severely short chapter is blocked`() {
        val issues = ConsistencyGate().inspect(request(2_000), GeneratedChapter(content = "周衍推开门，看见空走廊。", summary = "门外无人。"))

        assertTrue(issues.any { it.code == "CHAPTER_SEVERELY_UNDERSIZED" })
    }

    @Test
    fun `small utility generations are exempt from chapter length gate`() {
        val issues = ConsistencyGate().inspect(request(100), GeneratedChapter(content = "周衍推开门，看见空走廊。", summary = "门外无人。"))

        assertFalse(issues.any { it.code == "CHAPTER_SEVERELY_UNDERSIZED" || it.code == "CHAPTER_UNDERSIZED" })
    }

    private fun request(targetWords: Int): GenerationRequest {
        val novelId = "length-test"
        val snapshot = StorySnapshot(
            novel = Novel(novelId, "长度测试", "悬疑", "门外的人消失了", "选择", 200_000),
            activeOutline = emptyList(),
            bible = emptyList(),
            characters = emptyList(),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
        )
        val chapter = ChapterDraft(
            id = "chapter-1",
            novelId = novelId,
            chapterNumber = 1,
            title = "空走廊",
            objective = "确认门外的人消失",
            scenePlan = emptyList(),
        )
        return GenerationRequest(snapshot, chapter, targetWords)
    }
}
