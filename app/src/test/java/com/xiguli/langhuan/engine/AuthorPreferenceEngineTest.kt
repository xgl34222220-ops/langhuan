package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.AuthorLearningSource
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorPreferenceEngineTest {
    @Test
    fun `accepted rewrite instruction becomes active preference`() {
        val before = "周衍看着门。他觉得这里很危险，但还是决定进去。"
        val after = "周衍盯着门缝。里面没有声音。他把手搭上门把，停了两秒，还是压了下去。"
        val updated = AuthorPreferenceEngine.observeEdit(
            snapshot = snapshot(),
            chapterNumber = 4,
            before = before,
            after = after,
            source = AuthorLearningSource.AI_REWRITE_ACCEPTED,
            instruction = "对白更自然，减少网文腔，情绪不要直接解释",
            now = 100L,
        )
        val rules = updated.longForm.authorProfile.rules
        assertTrue(rules.any { it.id.startsWith("explicit:") && it.confidence >= 60 })
        assertTrue(AuthorPreferenceEngine.promptText(updated).contains("对白更自然"))
    }

    @Test
    fun `repeated removal of explanatory conclusions promotes stable rule`() {
        val before1 = "这说明他已经暴露了。这意味着门后的人早就知道他会来。显然，继续停留没有意义。" + "他站在楼道里。".repeat(30)
        val after1 = "门后没有动静。周衍收起手机，转身看向安全出口。" + "他站在楼道里。".repeat(30)
        val first = AuthorPreferenceEngine.observeEdit(snapshot(), 5, before1, after1, AuthorLearningSource.MANUAL_EDIT, now = 101L)
        val before2 = "这说明电话是假的。也就是说，对方一直在诱导他。由此可见，他不能再相信这个号码。" + "雨落在窗上。".repeat(30)
        val after2 = "号码又亮了一次。周衍没有接，把手机扣在桌面。" + "雨落在窗上。".repeat(30)
        val second = AuthorPreferenceEngine.observeEdit(first, 6, before2, after2, AuthorLearningSource.MANUAL_EDIT, now = 102L)
        val rule = second.longForm.authorProfile.rules.first { it.id == "less-explanation" }
        assertTrue(rule.evidenceCount >= 2)
        assertTrue(rule.confidence >= 60)
        assertTrue(AuthorPreferenceEngine.promptText(second).contains("减少解释性总结句"))
    }

    @Test
    fun `tiny manual typo does not pollute profile`() {
        val updated = AuthorPreferenceEngine.observeEdit(
            snapshot(), 2, "他走进房间。", "他走进房间里。", AuthorLearningSource.MANUAL_EDIT, now = 103L
        )
        assertTrue(updated.longForm.authorProfile.rules.isEmpty())
        assertTrue(updated.longForm.authorProfile.recentSignals.isEmpty())
    }

    private fun snapshot() = StorySnapshot(
        novel = Novel(
            id = "n1",
            title = "测试",
            genre = "悬疑",
            premise = "测试",
            theme = "选择",
            targetWords = 500_000,
            currentChapter = 1,
            status = NovelStatus.WRITING,
        ),
        activeOutline = emptyList(),
        bible = emptyList(),
        characters = emptyList(),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
    )
}
