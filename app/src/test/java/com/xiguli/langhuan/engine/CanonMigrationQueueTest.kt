package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonMigrationQueueTest {
    @Test
    fun `confirmed impacts become typed repair queue`() {
        val proposal = proposal(
            impacts = listOf(
                CanonChangeImpact("人物", "周衍", "人物目标仍引用旧规则"),
                CanonChangeImpact("时间线", "午夜事件", "事件描述依赖旧规则", 3),
                CanonChangeImpact("伏笔", "旧楼钥匙", "回收逻辑需要重排", 8),
                CanonChangeImpact("信息边界", "妹妹真相", "真相内容变化", 5),
                CanonChangeImpact("长期记忆", "全书摘要", "摘要仍含旧规则"),
            )
        )

        val queue = CanonMigrationPlanner.build("novel-1", proposal)

        assertEquals(5, queue.pending.size)
        assertTrue(queue.tasks.any { it.action == CanonMigrationAction.RECONCILE_CHARACTER })
        assertTrue(queue.tasks.any { it.action == CanonMigrationAction.REPAIR_TIMELINE })
        assertTrue(queue.tasks.any { it.action == CanonMigrationAction.REWORK_FORESHADOW })
        assertTrue(queue.tasks.any { it.action == CanonMigrationAction.RECONCILE_KNOWLEDGE })
        assertTrue(queue.tasks.any { it.action == CanonMigrationAction.REFRESH_MEMORY })
    }

    @Test
    fun `chapter outline impact returns to chapter runtime instead of direct Canon patch`() {
        val proposal = proposal(
            impacts = listOf(CanonChangeImpact("蓝图", "第12章《雨夜》", "章目标包含旧设定", 12))
        )

        val task = CanonMigrationPlanner.build("novel-1", proposal).tasks.single()

        assertEquals(CanonMigrationAction.REWRITE_CHAPTER, task.action)
        assertEquals(12, task.chapterNumber)
        assertEquals(CanonChangeRisk.HIGH, task.priority)
        assertTrue(task.repairInstruction.contains("章纲、场景或正文"))
    }

    @Test
    fun `repair instructions stay anchored to confirmed author change`() {
        val proposal = proposal(
            request = "把异常规则改成只有午夜后才能主动杀人",
            summary = "修改异常主动杀人时间规则",
            impacts = listOf(CanonChangeImpact("章节", "第4章", "正文写成白天主动袭击", 4)),
        )

        val task = CanonMigrationPlanner.build("novel-1", proposal).tasks.single()

        assertTrue(task.repairInstruction.contains(proposal.summary))
        assertTrue(task.repairInstruction.contains(proposal.request))
        assertTrue(task.repairInstruction.contains("不借机全面改写"))
        assertTrue(task.repairInstruction.contains("连续性检查"))
    }

    @Test
    fun `high risk work is ordered before memory refresh`() {
        val proposal = proposal(
            impacts = listOf(
                CanonChangeImpact("长期记忆", "全书摘要", "旧摘要"),
                CanonChangeImpact("章节", "第2章", "旧正文", 2),
            )
        )

        val queue = CanonMigrationPlanner.build("novel-1", proposal)

        assertEquals(CanonMigrationAction.REWRITE_CHAPTER, queue.tasks.first().action)
        assertEquals(CanonChangeRisk.HIGH, queue.tasks.first().priority)
    }

    private fun proposal(
        request: String = "修改核心设定",
        summary: String = "核心设定已修改",
        impacts: List<CanonChangeImpact>,
    ) = CanonChangeProposal(
        id = "proposal-1",
        request = request,
        summary = summary,
        patches = listOf(
            CanonChangePatch(
                targetType = CanonPatchTargetType.NOVEL,
                targetId = "novel-1",
                targetLabel = "小说",
                field = "theme",
                before = "旧设定",
                after = "新设定",
                risk = CanonChangeRisk.HIGH,
            )
        ),
        impacts = impacts,
        warnings = emptyList(),
    )
}
