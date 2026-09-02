package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonMigrationOrchestratorTest {
    @Test
    fun `structure repairs run before chapter and memory`() {
        val structure = task("structure", CanonMigrationAction.REPLAN_OUTLINE, CanonChangeRisk.HIGH)
        val chapter = task("chapter", CanonMigrationAction.REWRITE_CHAPTER, CanonChangeRisk.HIGH, chapter = 3)
        val memory = task("memory", CanonMigrationAction.REFRESH_MEMORY, CanonChangeRisk.MEDIUM)

        val plan = CanonMigrationOrchestrator.build(CanonMigrationQueue("novel-1", listOf(memory, chapter, structure)))

        assertEquals("structure", plan.next?.task?.id)
        val chapterItem = plan.items.first { it.task.id == "chapter" }
        val memoryItem = plan.items.first { it.task.id == "memory" }
        assertEquals(listOf("structure"), chapterItem.blockedByTaskIds)
        assertTrue(memoryItem.blockedByTaskIds.containsAll(listOf("structure", "chapter")))
    }

    @Test
    fun `chapter becomes next after structure is resolved`() {
        val structure = task("structure", CanonMigrationAction.REPAIR_TIMELINE, CanonChangeRisk.HIGH, status = CanonMigrationTaskStatus.DONE)
        val chapter = task("chapter", CanonMigrationAction.REWRITE_CHAPTER, CanonChangeRisk.HIGH, chapter = 4)
        val memory = task("memory", CanonMigrationAction.REFRESH_MEMORY, CanonChangeRisk.MEDIUM)

        val plan = CanonMigrationOrchestrator.build(CanonMigrationQueue("novel-1", listOf(memory, chapter, structure)))

        assertEquals("chapter", plan.next?.task?.id)
        assertEquals(CanonMigrationExecutionMode.CHAPTER_RUNTIME, plan.next?.mode)
        assertFalse(plan.items.first { it.task.id == "chapter" }.blockedByTaskIds.isNotEmpty())
    }

    @Test
    fun `memory is safe local only after earlier phases are resolved`() {
        val structure = task("structure", CanonMigrationAction.RECONCILE_CHARACTER, CanonChangeRisk.HIGH, status = CanonMigrationTaskStatus.DONE)
        val chapter = task("chapter", CanonMigrationAction.REWRITE_CHAPTER, CanonChangeRisk.HIGH, chapter = 2, status = CanonMigrationTaskStatus.SKIPPED)
        val memory = task("memory", CanonMigrationAction.REFRESH_MEMORY, CanonChangeRisk.MEDIUM)

        val plan = CanonMigrationOrchestrator.build(CanonMigrationQueue("novel-1", listOf(memory, chapter, structure)))

        assertEquals("memory", plan.next?.task?.id)
        assertEquals(CanonMigrationExecutionMode.SAFE_LOCAL, plan.next?.mode)
        assertEquals(listOf("memory"), plan.readySafeItems.map { it.task.id })
    }

    @Test
    fun `awaiting confirmation remains active and blocks automatic advance`() {
        val awaiting = task(
            "proposal",
            CanonMigrationAction.REWORK_FORESHADOW,
            CanonChangeRisk.HIGH,
            status = CanonMigrationTaskStatus.AWAITING_CONFIRMATION,
        )
        val other = task("other", CanonMigrationAction.RECONCILE_KNOWLEDGE, CanonChangeRisk.HIGH)

        val plan = CanonMigrationOrchestrator.build(CanonMigrationQueue("novel-1", listOf(other, awaiting)))

        assertEquals("proposal", plan.active?.task?.id)
        assertEquals("proposal", plan.next?.task?.id)
        assertTrue(plan.summary().contains("等待确认"))
    }

    @Test
    fun `failed proposal is retryable without being treated as resolved`() {
        val failed = task(
            "failed",
            CanonMigrationAction.REVIEW_STRUCTURE,
            CanonChangeRisk.MEDIUM,
            status = CanonMigrationTaskStatus.FAILED,
        )
        val queue = CanonMigrationQueue("novel-1", listOf(failed))
        val plan = CanonMigrationOrchestrator.build(queue)

        assertEquals(1, queue.pending.size)
        assertEquals("failed", plan.next?.task?.id)
        assertTrue(plan.next?.ready == true)
        assertEquals(CanonMigrationExecutionMode.PROPOSAL_GATE, plan.next?.mode)
    }

    @Test
    fun `resolved queue has no next work`() {
        val done = task("done", CanonMigrationAction.REPLAN_OUTLINE, CanonChangeRisk.HIGH, status = CanonMigrationTaskStatus.DONE)
        val skipped = task("skip", CanonMigrationAction.REWRITE_CHAPTER, CanonChangeRisk.HIGH, chapter = 1, status = CanonMigrationTaskStatus.SKIPPED)

        val plan = CanonMigrationOrchestrator.build(CanonMigrationQueue("novel-1", listOf(done, skipped)))

        assertEquals(0, plan.unresolvedCount)
        assertNull(plan.next)
        assertEquals("修复迁移已完成", plan.summary())
    }

    private fun task(
        id: String,
        action: CanonMigrationAction,
        risk: CanonChangeRisk,
        chapter: Int? = null,
        status: CanonMigrationTaskStatus = CanonMigrationTaskStatus.PENDING,
    ) = CanonMigrationTask(
        id = id,
        sourceProposalId = "proposal-1",
        sourceRequest = "修改核心设定",
        scope = when (action) {
            CanonMigrationAction.REWRITE_CHAPTER -> "章节"
            CanonMigrationAction.REFRESH_MEMORY -> "长期记忆"
            else -> "结构"
        },
        label = id,
        detail = "detail-$id",
        chapterNumber = chapter,
        action = action,
        priority = risk,
        repairInstruction = "repair-$id",
        status = status,
        createdAt = id.hashCode().toLong(),
    )
}
