package com.xiguli.langhuan.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMemoryRetrieverBoundaryTest {
    private val retriever = HybridMemoryRetriever()

    @Test
    fun writingTargetChapterNeverSeesSameOrFutureOriginalChapter() {
        val candidates = listOf(
            original("c300", 300, "林舟在旧车站拿到黑色钥匙，决定寻找地下档案室"),
            original("c301", 301, "林舟在地下档案室发现幕后身份真相"),
            original("c800", 800, "林舟最终知道所有事件都是镜像实验"),
            normal("draft301", 301, "第301章写作合同：林舟准备进入地下档案室"),
        )

        val hits = retriever.rank(
            query = "林舟 地下档案室 黑色钥匙",
            candidates = candidates,
            currentChapter = 301,
            limit = 10,
        )
        val ids = hits.map { it.candidate.sourceId }.toSet()

        assertTrue("目标章之前的原著事实必须可用", "c300" in ids)
        assertFalse("写第301章不能偷看原著第301章", "c301" in ids)
        assertFalse("未来原著章节必须硬过滤", "c800" in ids)
        assertTrue("当前章的非原著写作合同仍可使用", "draft301" in ids)
    }

    @Test
    fun storyAnchorCanSeeFactsThroughAnchorButNotNextChapter() {
        val candidates = listOf(
            original("c299", 299, "沈雾知道北门守卫每天午夜换班"),
            original("c300", 300, "沈雾在北门亲眼见到守卫交接暗号"),
            original("c301", 301, "沈雾后来才知道暗号其实是陷阱"),
        )

        // Story Runtime 锚定第300章时使用 anchorChapter + 1 作为检索 currentChapter。
        val hits = retriever.rank(
            query = "沈雾 北门 守卫 暗号",
            candidates = candidates,
            currentChapter = 301,
            limit = 10,
        )
        val ids = hits.map { it.candidate.sourceId }.toSet()

        assertTrue("进入第300章故事应看到第299章事实", "c299" in ids)
        assertTrue("进入第300章故事应看到第300章已经发生的事实", "c300" in ids)
        assertFalse("第301章才揭露的事实不能提前进入故事", "c301" in ids)
    }

    private fun original(id: String, chapter: Int, text: String) = MemoryCandidate(
        text = text,
        sourceType = "ORIGINAL_KNOWLEDGE",
        sourceId = id,
        chapterNumber = chapter,
        updatedAt = System.currentTimeMillis(),
    )

    private fun normal(id: String, chapter: Int, text: String) = MemoryCandidate(
        text = text,
        sourceType = "BIBLE",
        sourceId = id,
        chapterNumber = chapter,
        updatedAt = System.currentTimeMillis(),
    )
}
