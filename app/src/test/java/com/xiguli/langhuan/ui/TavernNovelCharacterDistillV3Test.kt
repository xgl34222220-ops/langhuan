package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.ChapterDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernNovelCharacterDistillV3Test {
    @Test
    fun parsesStructuredNovelCharacterAndEvidence() {
        val content = """
            <CHARACTER>
            name=杨间
            aliases=腿哥|杨无敌
            gender=男
            ageStage=青年
            appearance=神情冷淡
            personality=警惕、现实，面对危险时倾向先验证规则
            identity=驭鬼者
            occupationBehavior=处理灵异事件时优先确认规律和代价
            abilities=鬼眼|鬼影
            faction=总部
            relationships=王小明：合作但保持警惕|张伟：朋友
            history=经历敲门鬼事件
            likes=
            dislikes=无谓冒险
            boundaries=不轻信未经验证的情报
            speechStyle=直接、克制，少说废话
            catchphrases=
            worldFacts=知道厉鬼存在且多数无法被常规方式杀死
            currentMemory=记得敲门鬼事件
            dialogueExamples=这东西不是人。|先别动。
            characterArc=从普通学生逐步成为处理灵异事件的核心人物
            currentStatus=存活
            evidence=12~性格~杨间没有立刻靠近，而是先观察四周。§15~能力~他的额头裂开，一只猩红的眼睛睁开。
            </CHARACTER>
        """.trimIndent()

        val result = parseNovelCharacterBlocksV3(
            content = content,
            sourceTitle = "测试小说",
            mode = NovelCharacterDistillModeV3.DEEP,
            scannedThroughChapter = 15,
        )

        assertEquals(1, result.size)
        val profile = result.single()
        assertEquals("杨间", profile.name)
        assertTrue("腿哥" in profile.aliases)
        assertTrue("鬼眼" in profile.abilities)
        assertEquals(2, profile.evidences.size)
        assertEquals(12, profile.evidences.first().chapter)
        assertEquals(15, profile.scannedThroughChapter)
    }

    @Test
    fun mergesSamePersonThroughAliasesWithoutDuplicatingCard() {
        val old = NovelCharacterProfileV3(
            id = "keep-id",
            name = "杨间",
            aliases = listOf("腿哥"),
            personality = "冷静",
            abilities = listOf("鬼眼"),
            evidences = listOf(NovelCharacterEvidenceV3(2, "身份", "杨间站在教室门口。")),
        )
        val update = NovelCharacterProfileV3(
            name = "腿哥",
            aliases = listOf("杨间"),
            personality = "冷静、现实、警惕",
            abilities = listOf("鬼影"),
            distillMode = NovelCharacterDistillModeV3.DEEP,
            scannedThroughChapter = 30,
            evidences = listOf(NovelCharacterEvidenceV3(30, "能力", "黑色影子贴在他的脚下。")),
        )

        val merged = mergeNovelCharacterProfilesV3(listOf(old), listOf(update))

        assertEquals(1, merged.size)
        assertEquals("keep-id", merged.single().id)
        assertTrue("鬼眼" in merged.single().abilities)
        assertTrue("鬼影" in merged.single().abilities)
        assertEquals(NovelCharacterDistillModeV3.DEEP, merged.single().distillMode)
        assertEquals(30, merged.single().scannedThroughChapter)
        assertEquals(2, merged.single().evidences.size)
    }

    @Test
    fun quickSamplesAtMostTwelveChaptersWhileDeepCoversAll() {
        val chapters = (1..30).map { number ->
            ChapterDraft(
                id = "chapter-$number",
                novelId = "novel",
                chapterNumber = number,
                title = "第${number}章",
                objective = "",
                scenePlan = emptyList(),
                content = "第 $number 章正文。人物在这里行动和对话。",
            )
        }

        val quick = buildNovelCharacterBatchesV3(chapters, NovelCharacterDistillModeV3.QUICK)
        val deep = buildNovelCharacterBatchesV3(chapters, NovelCharacterDistillModeV3.DEEP)
        val quickNumbers = quick.flatMap { it.chapterNumbers }.distinct().sorted()
        val deepNumbers = deep.flatMap { it.chapterNumbers }.distinct().sorted()

        assertTrue(quickNumbers.size <= 12)
        assertEquals(1, quickNumbers.first())
        assertEquals(30, quickNumbers.last())
        assertEquals((1..30).toList(), deepNumbers)
    }
}
