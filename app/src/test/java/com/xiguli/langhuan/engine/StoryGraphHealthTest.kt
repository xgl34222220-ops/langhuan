package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.FactProvenance
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.Foreshadowing
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ReaderKnowledgeState
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGraphHealthTest {
    @Test
    fun `clean structured project starts healthy`() {
        val (snapshot, chapters) = fixture(currentChapter = 2)
        val report = StoryGraphHealthEngine.analyze(snapshot, chapters)
        assertEquals(100, report.score)
        assertEquals(0, report.highCount)
        assertTrue(report.nodes.any { it.id == "chapter:1" })
        assertTrue(report.edges.any { it.type == StoryGraphEdgeType.PARENT_OF })
    }

    @Test
    fun `non flashback story day regression is high risk`() {
        val (base, chapters) = fixture(currentChapter = 2)
        val snapshot = base.copy(
            recentTimeline = listOf(
                TimelineEvent("t1", "novel-1", 1, "夜里", "旧楼", emptyList(), "进入旧楼", storyDay = 3, orderInChapter = 1),
                TimelineEvent("t2", "novel-1", 2, "清晨", "医院", emptyList(), "醒来", storyDay = 2, orderInChapter = 1),
            )
        )
        val report = StoryGraphHealthEngine.analyze(snapshot, chapters)
        assertTrue(report.issues.any { it.category == StoryHealthCategory.TIMELINE && it.severity == StoryHealthSeverity.HIGH && it.title.contains("倒退") })
    }

    @Test
    fun `overdue foreshadowing is high risk`() {
        val (base, chapters) = fixture(currentChapter = 6)
        val snapshot = base.copy(
            relevantForeshadowing = listOf(
                Foreshadowing(
                    id = "f1",
                    novelId = "novel-1",
                    title = "旧楼钥匙",
                    plantedChapter = 1,
                    detail = "主角拿到无法解释的钥匙",
                    expectedPayoff = "打开地下室",
                    expectedChapterStart = 3,
                    expectedChapterEnd = 4,
                    status = ForeshadowStatus.DEVELOPING,
                )
            )
        )
        val report = StoryGraphHealthEngine.analyze(snapshot, chapters)
        assertTrue(report.issues.any { it.category == StoryHealthCategory.FORESHADOW && it.severity == StoryHealthSeverity.HIGH && it.title.contains("超期") })
        assertTrue(report.score < 100)
    }

    @Test
    fun `fact provenance of same subject becomes cross chapter chain`() {
        val (base, chapters) = fixture(currentChapter = 2)
        val snapshot = base.copy(
            factHistory = listOf(
                FactProvenance("p1", "novel-1", 1, "CHARACTER_GOAL", "周衍目标", after = "寻找妹妹"),
                FactProvenance("p2", "novel-1", 2, "CHARACTER_GOAL", "周衍目标", before = "寻找妹妹", after = "隐瞒旧楼真相"),
            )
        )
        val report = StoryGraphHealthEngine.analyze(snapshot, chapters)
        assertTrue(report.edges.any { it.from == "fact:p1" && it.to == "fact:p2" && it.type == StoryGraphEdgeType.FACT_CHAIN })
        assertTrue(report.hotspots.any { it.node.id == "fact:p1" || it.node.id == "fact:p2" })
    }

    @Test
    fun `pending high risk migration is visible in health report`() {
        val (snapshot, chapters) = fixture(currentChapter = 2)
        val task = CanonMigrationTask(
            id = "m1",
            sourceProposalId = "proposal-1",
            sourceRequest = "修改异常规则",
            scope = "章节",
            label = "第2章",
            detail = "正文仍使用旧规则",
            chapterNumber = 2,
            action = CanonMigrationAction.REWRITE_CHAPTER,
            priority = CanonChangeRisk.HIGH,
            repairInstruction = "修复第2章",
        )
        val queue = CanonMigrationQueue("novel-1", listOf(task))
        val report = StoryGraphHealthEngine.analyze(snapshot, chapters, queue)
        assertTrue(report.issues.any { it.category == StoryHealthCategory.WORKFLOW && it.severity == StoryHealthSeverity.HIGH })
        assertTrue(report.edges.any { it.from == "migration:m1" && it.to == "chapter:2" && it.type == StoryGraphEdgeType.REPAIR_TARGET })
    }

    @Test
    fun `full reveal before earliest chapter is high risk`() {
        val (base, chapters) = fixture(currentChapter = 2)
        val snapshot = base.copy(
            knowledgeLedger = listOf(
                KnowledgeBoundary(
                    id = "k1",
                    title = "妹妹失踪真相",
                    truth = "妹妹主动进入旧楼",
                    readerState = ReaderKnowledgeState.UNKNOWN,
                    revealPolicy = KnowledgeRevealPolicy.FULL,
                    earliestFullRevealChapter = 8,
                )
            )
        )
        val report = StoryGraphHealthEngine.analyze(snapshot, chapters)
        assertTrue(report.issues.any { it.category == StoryHealthCategory.KNOWLEDGE && it.severity == StoryHealthSeverity.HIGH && it.title.contains("过早") })
    }

    private fun fixture(currentChapter: Int): Pair<StorySnapshot, List<ChapterDraft>> {
        val novel = Novel(
            id = "novel-1",
            title = "测试小说",
            genre = "悬疑",
            premise = "寻找失踪者",
            theme = "真相与代价",
            targetWords = 100_000,
            currentChapter = currentChapter,
            status = NovelStatus.WRITING,
        )
        val master = OutlineNode("master", "novel-1", level = OutlineLevel.MASTER, order = 1, title = "总纲", objective = "查明真相", conflict = "真相有代价", turningPoint = "进入旧楼")
        val volume = OutlineNode("volume", "novel-1", parentId = "master", level = OutlineLevel.VOLUME, order = 1, title = "第一卷", objective = "进入主线", conflict = "规则阻碍", turningPoint = "发现异常")
        val chapter1 = OutlineNode("c1", "novel-1", parentId = "volume", level = OutlineLevel.CHAPTER, order = 1, title = "第一章", objective = "进入旧楼", conflict = "门被锁住", turningPoint = "拿到钥匙")
        val chapter2 = OutlineNode("c2", "novel-1", parentId = "volume", level = OutlineLevel.CHAPTER, order = 2, title = "第二章", objective = "调查异常", conflict = "规则升级", turningPoint = "发现地下室")
        val outline = listOf(master, volume, chapter1, chapter2)
        val snapshot = StorySnapshot(
            novel = novel,
            activeOutline = listOf(master, volume, if (currentChapter <= 1) chapter1 else chapter2),
            bible = emptyList(),
            characters = emptyList(),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
            outline = outline,
        )
        val scene = ScenePlan(1, "周衍", "旧楼", "调查", "门锁", "进入")
        val drafts = listOf(
            ChapterDraft("d1", "novel-1", 1, "第一章", "进入旧楼", listOf(scene)),
            ChapterDraft("d2", "novel-1", 2, "第二章", "调查异常", listOf(scene.copy(order = 1, location = "地下室"))),
        )
        return snapshot to drafts
    }
}
