package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonChangeProposalTest {
    @Test
    fun `normalization binds before value to current canon`() {
        val snapshot = fixtureSnapshot()
        val proposed = listOf(
            CanonChangePatch(
                targetType = CanonPatchTargetType.BIBLE,
                targetId = "rule-1",
                field = "content",
                before = "AI 猜错的旧值",
                after = "异常只能在午夜触发。",
            )
        )

        val normalized = CanonPatchEngine.normalize(snapshot, proposed)

        assertEquals(1, normalized.size)
        assertEquals("异常在任何时段都可能触发。", normalized.single().before)
        assertEquals(CanonChangeRisk.HIGH, normalized.single().risk)
    }

    @Test
    fun `chapter outline direct patch is rejected`() {
        val snapshot = fixtureSnapshot()
        val proposed = listOf(
            CanonChangePatch(
                targetType = CanonPatchTargetType.OUTLINE,
                targetId = "chapter-1",
                field = "objective",
                after = "偷偷改章纲",
            )
        )

        assertTrue(CanonPatchEngine.normalize(snapshot, proposed).isEmpty())
    }

    @Test
    fun `confirmed patch updates canon and records provenance`() {
        val snapshot = fixtureSnapshot()
        val patch = CanonPatchEngine.normalize(
            snapshot,
            listOf(
                CanonChangePatch(
                    targetType = CanonPatchTargetType.CHARACTER,
                    targetId = "char-1",
                    field = "goal",
                    after = "找到失踪的妹妹并隐瞒真相",
                )
            )
        ).single()

        val updated = CanonPatchEngine.apply(snapshot, listOf(patch), "把周衍的目标改成找到妹妹并隐瞒真相")

        assertEquals("找到失踪的妹妹并隐瞒真相", updated.characters.single().goal)
        assertEquals(1, updated.factHistory.size)
        assertTrue(updated.factHistory.single().kind.startsWith("AUTHOR_CHANGE"))
        assertEquals("调查旧楼失踪案", updated.factHistory.single().before)
    }

    @Test
    fun `stale preview is rejected before apply`() {
        val snapshot = fixtureSnapshot()
        val patch = CanonPatchEngine.normalize(
            snapshot,
            listOf(
                CanonChangePatch(
                    targetType = CanonPatchTargetType.NOVEL,
                    targetId = snapshot.novel.id,
                    field = "theme",
                    after = "记忆如何塑造身份",
                )
            )
        ).single()
        val changedElsewhere = snapshot.copy(novel = snapshot.novel.copy(theme = "已经被别处修改"))

        val conflicts = CanonPatchEngine.conflicts(changedElsewhere, listOf(patch))

        assertFalse(conflicts.isEmpty())
    }

    @Test
    fun `impact analysis finds chapter and outline references`() {
        val snapshot = fixtureSnapshot()
        val patch = CanonPatchEngine.normalize(
            snapshot,
            listOf(
                CanonChangePatch(
                    targetType = CanonPatchTargetType.CHARACTER,
                    targetId = "char-1",
                    field = "goal",
                    after = "离开旧楼",
                )
            )
        ).single()
        val drafts = listOf(
            ChapterDraft(
                id = "draft-1",
                novelId = snapshot.novel.id,
                chapterNumber = 1,
                title = "旧楼",
                objective = "周衍进入旧楼调查",
                scenePlan = listOf(
                    ScenePlan(
                        order = 1,
                        viewpoint = "周衍",
                        location = "旧楼",
                        purpose = "调查",
                        conflict = "门被锁死",
                        outcome = "发现妹妹留下的纸条",
                    )
                ),
                content = "周衍在旧楼里继续寻找妹妹。",
            )
        )

        val impacts = CanonImpactAnalyzer.analyze(snapshot, drafts, listOf(patch))

        assertTrue(impacts.any { it.scope == "章节" && it.chapterNumber == 1 })
        assertTrue(impacts.any { it.scope == "蓝图" })
    }

    private fun fixtureSnapshot(): StorySnapshot {
        val novel = Novel(
            id = "novel-1",
            title = "无人生还的梦",
            genre = "悬疑",
            premise = "周衍调查一栋不断重置的旧楼。",
            theme = "真相与代价",
            targetWords = 300_000,
            currentChapter = 1,
        )
        val master = OutlineNode(
            id = "master-1",
            novelId = novel.id,
            level = OutlineLevel.MASTER,
            order = 1,
            title = "总纲",
            objective = "周衍逐步查清旧楼与失踪妹妹的关系",
            conflict = "真相会伤害他最想保护的人",
            turningPoint = "周衍发现妹妹主动进入旧楼",
        )
        val chapter = OutlineNode(
            id = "chapter-1",
            novelId = novel.id,
            parentId = master.id,
            level = OutlineLevel.CHAPTER,
            order = 1,
            title = "旧楼",
            objective = "周衍第一次进入旧楼",
            conflict = "旧楼封闭",
            turningPoint = "发现妹妹留下的纸条",
        )
        return StorySnapshot(
            novel = novel,
            activeOutline = listOf(master, chapter),
            bible = listOf(
                BibleEntry(
                    id = "rule-1",
                    novelId = novel.id,
                    category = BibleCategory.RULE,
                    name = "异常触发",
                    content = "异常在任何时段都可能触发。",
                )
            ),
            characters = listOf(
                CharacterState(
                    id = "char-1",
                    novelId = novel.id,
                    name = "周衍",
                    personality = listOf("谨慎"),
                    location = "旧楼外",
                    physicalState = "正常",
                    emotionalState = "警惕",
                    goal = "调查旧楼失踪案",
                    lastUpdatedChapter = 1,
                )
            ),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
            outline = listOf(master, chapter),
        )
    }
}
