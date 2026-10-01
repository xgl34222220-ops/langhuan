package com.xiguli.langhuan.data

import com.xiguli.langhuan.domain.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ProjectRestorePlanTest {
    private fun draft(number: Int) = ChapterDraft("old-draft-$number", "old", number, "第${number}章", "", emptyList(),
        content = "正文$number", sourceUrl = "https://books.example/chapter/$number")
    private fun backup() = StoryProjectBackup(snapshot = StorySnapshot(
        Novel("old", "原书", "测试", "原创", "保留数据", 10000, currentChapter = 3,
            sourceId = "source-id", sourceBookUrl = "https://books.example/book/1"),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    ), chapters = listOf(draft(1), draft(3)))
    private fun outline(id: String, parent: String? = null) = OutlineNode(id, "old", parent, OutlineLevel.CHAPTER, 1, "章纲", "目标", "冲突", "转折")

    @Test fun sparseChaptersAndOnlineIdentityRemainIntactUnderANewBookId() {
        val original = backup()
        val restored = prepareProjectRestore(original)
        assertNotEquals("old", restored.snapshot.novel.id)
        assertEquals(listOf(1, 3), restored.chapters.map { it.chapterNumber })
        assertEquals(original.chapters.map { it.content }, restored.chapters.map { it.content })
        assertEquals(original.chapters.map { it.sourceUrl }, restored.chapters.map { it.sourceUrl })
        assertEquals("source-id", restored.snapshot.novel.sourceId)
        assertEquals("https://books.example/book/1", restored.snapshot.novel.sourceBookUrl)
        assertEquals(3, restored.currentDraft.chapterNumber)
        assertTrue(restored.chapters.all { it.novelId == restored.snapshot.novel.id })
        assertEquals("old", original.snapshot.novel.id)
    }

    @Test fun duplicateOrNonpositiveChaptersAreRejectedBeforeAPlanExists() {
        val original = backup()
        for (numbers in listOf(listOf(1, 1), listOf(0), listOf(-1))) {
            assertThrows(IllegalArgumentException::class.java) { prepareProjectRestore(original.copy(chapters = numbers.map(::draft))) }
        }
        assertEquals(listOf(1, 3), original.chapters.map { it.chapterNumber })
    }

    @Test fun unsupportedVersionEmptyInputAndCancellationFailBeforeWriting() {
        val original = backup()
        assertThrows(IllegalArgumentException::class.java) { prepareProjectRestore(original.copy(formatVersion = 99)) }
        assertThrows(IllegalArgumentException::class.java) { prepareProjectRestore(original.copy(chapters = emptyList())) }
        assertThrows(CancellationException::class.java) { prepareProjectRestore(original) { throw CancellationException("cancelled") } }
    }

    @Test fun outlineCyclesMissingParentsAndConflictingDuplicatesAreRejected() {
        val original = backup()
        for (nodes in listOf(listOf(outline("a", "b"), outline("b", "a")),
            listOf(outline("a", "missing")), listOf(outline("a"), outline("a").copy(title = "不同内容")))) {
            assertThrows(IllegalArgumentException::class.java) { prepareProjectRestore(original.copy(snapshot = original.snapshot.copy(outline = nodes))) }
        }
    }

    @Test fun identicalLegacyOutlineDuplicatesRemainCompatible() {
        val original = backup()
        val node = outline("legacy")
        val restored = prepareProjectRestore(original.copy(snapshot = original.snapshot.copy(activeOutline = listOf(node, node))))
        assertEquals(1, restored.snapshot.outline.size)
        assertNotEquals(node.id, restored.snapshot.outline.single().id)
    }

    @Test fun candidateFactsBelongToTheNewBookAfterRestore() {
        val original = backup()
        val fact = CandidateFact("candidate-old", "old", 1, CandidateFactKind.BIBLE_ENTRY, "设定", after = "原创证据")
        val restored = prepareProjectRestore(original.copy(snapshot = original.snapshot.copy(candidateFacts = listOf(fact))))
        assertEquals(restored.snapshot.novel.id, restored.snapshot.candidateFacts.single().novelId)
        assertNotEquals(fact.id, restored.snapshot.candidateFacts.single().id)
        assertEquals(fact.after, restored.snapshot.candidateFacts.single().after)
    }

    @Test fun foreshadowPlanKeepsItsReferenceToTheRemappedForeshadow() {
        val original = backup()
        val hint = Foreshadowing("hint-old", "old", "灯光", 1, "原创细节", "重逢", 3, 6, ForeshadowStatus.PLANTED)
        val state = LongFormState(autonomousPlan = AutonomousStoryPlan(foreshadowCadence = listOf(
            ForeshadowCadence(hint.id, hint.title, 3),
        )))
        val restored = prepareProjectRestore(original.copy(snapshot = original.snapshot.copy(relevantForeshadowing = listOf(hint), longForm = state)))
        assertEquals(restored.snapshot.relevantForeshadowing.single().id,
            restored.snapshot.longForm.autonomousPlan.foreshadowCadence.single().foreshadowId)
        assertEquals("hint-old", hint.id)
    }
    @Test fun repairedCatalogueKeepsStableKeysReadingOrderAndCurrentChapter() {
        val original = backup()
        val drafts = listOf(draft(1), draft(4).copy(readingOrder=2), draft(3).copy(readingOrder=3))
        val restored = prepareProjectRestore(original.copy(chapters=drafts))
        assertEquals(listOf(1,4,3), restored.chapters.map { it.chapterNumber })
        assertEquals(listOf(1,2,3), restored.chapters.map { it.readingOrder })
        assertEquals(3, restored.currentDraft.chapterNumber)
        assertEquals(drafts.map { it.sourceUrl }, restored.chapters.map { it.sourceUrl })
    }

    @Test fun missingCurrentChapterUsesFirstReadingPositionConsistently() {
        val original = backup()
        val restored = prepareProjectRestore(original.copy(
            snapshot=original.snapshot.copy(novel=original.snapshot.novel.copy(currentChapter=99)),
            chapters=listOf(draft(8).copy(readingOrder=1),draft(1).copy(readingOrder=2))))
        assertEquals(8,restored.currentDraft.chapterNumber)
        assertEquals(8,restored.snapshot.novel.currentChapter)
    }

    @Test fun duplicateOrNonpositiveReadingOrdersFailBeforeWriting() {
        val original = backup()
        for (drafts in listOf(listOf(draft(1),draft(3).copy(readingOrder=1)),listOf(draft(1).copy(readingOrder=0)))) {
            assertThrows(IllegalArgumentException::class.java) { prepareProjectRestore(original.copy(chapters=drafts)) }
        }
    }

}
