package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot

data class ScenePlanSuggestion(
    val note: String,
    val scenes: List<ScenePlan>,
)

class WritingFlowEngine(
    private val gateway: AiGateway,
) {
    suspend fun planCurrentChapter(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        currentScenes: List<ScenePlan>,
        conversation: List<Pair<String, String>> = emptyList(),
        instruction: String = "",
    ): ScenePlanSuggestion {
        val outline = snapshot.activeOutline.joinToString("\n") { node ->
            buildString {
                append("${node.level}:${node.title}｜目标=${node.objective}｜冲突=${node.conflict}｜转折=${node.turningPoint}")
                if (node.mustInclude.isNotEmpty()) append("｜必须=${node.mustInclude.joinToString("、")}")
                if (node.forbidden.isNotEmpty()) append("｜禁止=${node.forbidden.joinToString("、")}")
            }
        }
        val characters = snapshot.characters.joinToString("\n") {
            "${it.name}｜地点=${it.location}｜身体=${it.physicalState}｜情绪=${it.emotionalState}｜目标=${it.goal}"
        }
        val foreshadowing = snapshot.relevantForeshadowing
            .filter { it.status.name != "RESOLVED" && it.status.name != "ABANDONED" }
            .joinToString("\n") {
                "id=${it.id}｜${it.title}｜${it.detail}｜预计${it.expectedChapterStart}-${it.expectedChapterEnd}章回收"
            }
        val recent = snapshot.recentSummaries.takeLast(8).joinToString("\n")
        val current = currentScenes.sortedBy { it.order }.joinToString("\n") {
            "${it.order}. 视角=${it.viewpoint}｜地点=${it.location}｜目的=${it.purpose}｜冲突=${it.conflict}｜结果=${it.outcome}"
        }
        val chat = conversation.takeLast(10).joinToString("\n") { (role, text) ->
            if (role == "user") "用户：$text" else "琅嬛：$text"
        }

        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的章节场景导演。你的任务是把已经锁定的章纲拆成 2-6 个可直接写正文的场景，而不是改写总纲或另起主线。
                    输出必须严格符合 GeneratedChapter JSON：title、content、summary、stateChanges、touchedForeshadowingIds，不要 Markdown。

                    字段约定：
                    - title 固定为 scene-plan。
                    - content = 80-220 字说明本次场景编排为什么这样安排、节奏如何推进。
                    - summary = 一句话说明本章最终应完成的情绪/剧情落点。
                    - stateChanges = 场景计划；每项 subject=视角人物，field=地点，before=场景目的，after=场景冲突，evidence=场景结果。
                    - touchedForeshadowingIds = 本章建议明确触及的既有伏笔 id。

                    硬规则：
                    1. 本章唯一目标不能被替换；场景必须共同完成章纲目标。
                    2. 总纲/卷纲/章纲中的 mustInclude 必须落实，forbidden 绝不能出现。
                    3. 不得让人物知道其尚未知晓的信息，不得无因改变位置、能力、关系和性格。
                    4. 每个场景都必须产生新的信息、代价、关系变化或选择，禁止纯过场。
                    5. 最后一个场景必须形成章末转折或强钩子，但不能为了钩子提前泄露后续核心答案。
                    6. 用户只是要求“更紧张/少一个场景/换地点”等时，只改场景层，不擅自改章纲事实。
                """.trimIndent(),
                user = """
                    小说：${snapshot.novel.title}
                    核心命题：${snapshot.novel.premise}
                    主题：${snapshot.novel.theme}

                    【当前章】
                    第${chapter.chapterNumber}章 ${chapter.title}
                    唯一目标：${chapter.objective}

                    【总纲→卷纲→章纲】
                    $outline

                    【人物当前状态】
                    $characters

                    【活跃伏笔】
                    $foreshadowing

                    【最近剧情记忆】
                    $recent

                    【当前场景计划】
                    ${current.ifBlank { "暂无，请从章纲开始拆分。" }}

                    【本轮场景讨论】
                    ${chat.ifBlank { "暂无。" }}

                    【用户最新要求】
                    ${instruction.ifBlank { "请重新检查本章场景节奏，给出可直接进入正文写作的版本。" }}
                """.trimIndent(),
            )
        )

        val scenes = output.stateChanges.take(6).mapIndexed { index, change ->
            ScenePlan(
                order = index + 1,
                viewpoint = change.subject.trim().ifBlank { snapshot.characters.firstOrNull()?.name ?: "主角" },
                location = change.field.trim().ifBlank { "待定地点" },
                purpose = change.before.trim().ifBlank { "推进本章目标" },
                conflict = change.after.trim().ifBlank { "目标受到具体阻碍" },
                outcome = change.evidence.trim().ifBlank { "形成新的信息、代价或选择" },
            )
        }.ifEmpty {
            currentScenes.ifEmpty {
                listOf(
                    ScenePlan(1, snapshot.characters.firstOrNull()?.name ?: "主角", "承接上一章的场景", "迅速承接上一章结果并明确本章目标", "目标立即受到阻碍", "主角被迫做出具体选择"),
                    ScenePlan(2, snapshot.characters.firstOrNull()?.name ?: "主角", "本章核心场景", chapter.objective, "主要冲突升级", "章末形成新的信息、代价或转折"),
                )
            }
        }
        return ScenePlanSuggestion(
            note = output.content.trim().ifBlank { output.summary.trim().ifBlank { "场景计划已按当前章纲重新整理。" } },
            scenes = scenes,
        )
    }
}
