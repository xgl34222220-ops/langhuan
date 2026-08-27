package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot

data class ChapterPlanSuggestion(
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
    val scenes: List<ScenePlan>,
)

class WorkspaceAiEngine(
    private val gateway: AiGateway,
) {
    suspend fun planNextChapter(snapshot: StorySnapshot, current: ChapterDraft): ChapterPlanSuggestion {
        val recent = snapshot.recentSummaries.takeLast(6).joinToString("\n")
        val characters = snapshot.characters.joinToString("\n") {
            "${it.name}｜地点=${it.location}｜目标=${it.goal}｜情绪=${it.emotionalState}"
        }
        val activeForeshadowing = snapshot.relevantForeshadowing
            .filter { it.status.name != "RESOLVED" && it.status.name != "ABANDONED" }
            .joinToString("\n") { "${it.title}：${it.detail}，预计${it.expectedChapterStart}-${it.expectedChapterEnd}章回收" }
        val outline = snapshot.activeOutline.joinToString("\n") {
            "${it.level}:${it.title}｜目标=${it.objective}｜冲突=${it.conflict}｜转折=${it.turningPoint}"
        }
        val prompt = PromptBundle(
            system = """
                你是长篇小说的章节策划引擎。你必须沿用现有总纲、卷纲、人物状态、伏笔和最近剧情，规划紧接当前章节的下一章，不得擅自换主线。
                输出必须符合 GeneratedChapter JSON：title、content、summary、stateChanges、touchedForeshadowingIds。
                这里字段有专门含义：
                - title = 下一章标题。
                - content = 下一章唯一主目标，必须具体可验证。
                - summary = “主要冲突 || 章末转折”，必须用两个竖线分隔。
                - stateChanges = 场景计划。每项 subject=视角人物，field=地点，before=场景目的，after=场景冲突，evidence=场景结果。
                - touchedForeshadowingIds = 建议本章触及的伏笔 id。
                至少规划 2 个、最多 6 个场景。不要返回小说正文。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                核心命题：${snapshot.novel.premise}
                主题：${snapshot.novel.theme}
                当前章节：第${current.chapterNumber}章 ${current.title}
                当前章节目标：${current.objective}
                当前章节摘要：${current.summary.ifBlank { "尚未形成摘要" }}

                【当前大纲链】
                $outline

                【人物状态】
                $characters

                【活跃伏笔】
                $activeForeshadowing

                【最近剧情】
                $recent

                请规划第${current.chapterNumber + 1}章。
            """.trimIndent(),
        )
        val output = gateway.generate(prompt)
        val summaryParts = output.summary.split("||", limit = 2).map { it.trim() }
        val scenes = output.stateChanges.take(6).mapIndexed { index, item ->
            ScenePlan(
                order = index + 1,
                viewpoint = item.subject.ifBlank { "主角" },
                location = item.field.ifBlank { "待定地点" },
                purpose = item.before.ifBlank { "推动本章目标" },
                conflict = item.after.ifBlank { "目标受到阻碍" },
                outcome = item.evidence.ifBlank { "形成新的信息或选择" },
            )
        }.ifEmpty {
            listOf(
                ScenePlan(1, "主角", "承接上一章的场景", "承接上一章结果", "新问题立即出现", "主角被迫做出下一步选择"),
                ScenePlan(2, "主角", "核心场景", "推进本章唯一目标", "关键阻碍升级", "章末形成新的代价或转折"),
            )
        }
        return ChapterPlanSuggestion(
            title = output.title.ifBlank { "第${current.chapterNumber + 1}章" },
            objective = output.content.trim().ifBlank { "承接上一章结果并推进当前卷主线。" },
            conflict = summaryParts.getOrNull(0).orEmpty().ifBlank { "人物目标遭遇更具体的阻碍。" },
            turningPoint = summaryParts.getOrNull(1).orEmpty().ifBlank { "章末出现新的信息、代价或选择。" },
            scenes = scenes,
        )
    }

    suspend fun rewriteSelection(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        selectedText: String,
        instruction: String,
    ): String {
        require(selectedText.isNotBlank()) { "请先选择需要重写的正文" }
        val contextBefore = chapter.content.substringBefore(selectedText, "").takeLast(1_200)
        val contextAfter = chapter.content.substringAfter(selectedText, "").take(1_200)
        val prompt = PromptBundle(
            system = """
                你是长篇小说局部改写引擎。只重写用户选中的片段，不改变片段前后的既定事实、人物状态和剧情方向。
                输出必须符合 GeneratedChapter JSON。title 固定为 rewrite；content 只放替换后的正文，不要包含原文、解释、Markdown 或引号；summary 简述改写策略；stateChanges 和 touchedForeshadowingIds 均返回空数组。
                改写后的长度原则上保持原片段的 60%-160%，除非用户明确要求扩写或缩写。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                本章：第${chapter.chapterNumber}章 ${chapter.title}
                本章目标：${chapter.objective}
                用户要求：${instruction.ifBlank { "润色表达，增强画面感与节奏，不改变事实。" }}

                【片段之前】
                $contextBefore

                【要重写的片段】
                $selectedText

                【片段之后】
                $contextAfter
            """.trimIndent(),
        )
        return gateway.generate(prompt).content.trim().ifBlank { error("AI 没有返回改写内容") }
    }
}
