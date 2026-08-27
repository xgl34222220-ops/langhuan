package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

data class PromptBundle(
    val system: String,
    val user: String,
)

class PromptAssembler {
    fun build(request: GenerationRequest): PromptBundle {
        val snapshot = request.snapshot
        val styleRules = snapshot.bible
            .filter { it.category == BibleCategory.STYLE }
            .joinToString("\n") { "- ${it.name}: ${it.content}${if (it.locked) "（必须遵守）" else "（偏好）"}" }
            .ifBlank { "- 暂无专门文风模板；保持自然、稳定、符合当前作品类型。" }

        val hardRules = snapshot.bible
            .filter { it.category != BibleCategory.STYLE && (it.locked || it.category == BibleCategory.FORBIDDEN) }
            .joinToString("\n") { "- [${it.category}] ${it.name}: ${it.content}" }

        val outline = snapshot.activeOutline
            .sortedWith(compareBy({ it.level.ordinal }, { it.order }))
            .joinToString("\n") {
                val level = when (it.level) {
                    OutlineLevel.MASTER -> "总纲"
                    OutlineLevel.VOLUME -> "卷纲"
                    OutlineLevel.CHAPTER -> "章纲"
                }
                "- [$level] ${it.title}｜目标:${it.objective}｜冲突:${it.conflict}｜转折:${it.turningPoint}"
            }

        val characters = snapshot.characters.joinToString("\n") {
            "- ${it.name}: 地点=${it.location}; 身体=${it.physicalState}; 情绪=${it.emotionalState}; " +
                "目标=${it.goal}; 性格=${it.personality.joinToString("、")}; 关系=${it.relationshipNotes.entries.joinToString("；") { e -> "${e.key}=${e.value}" }}; 已知秘密=${it.knownSecrets.joinToString("、")}"
        }

        val timeline = snapshot.recentTimeline.joinToString("\n") {
            "- 第${it.chapter}章/${it.storyTime}/${it.location}: ${it.summary}; 后果=${it.consequences.joinToString("、")}"
        }

        val foreshadowing = snapshot.relevantForeshadowing.joinToString("\n") {
            "- id=${it.id}; ${it.title}; 状态=${it.status}; 细节=${it.detail}; 预期回收=${it.expectedPayoff}"
        }

        val scenes = request.chapter.scenePlan.sortedBy { it.order }.joinToString("\n") {
            "- 场景${it.order}: 视角=${it.viewpoint}; 地点=${it.location}; 目的=${it.purpose}; " +
                "冲突=${it.conflict}; 结果=${it.outcome}"
        }

        val recentMemory = snapshot.recentSummaries.joinToString("\n") { "- $it" }
        val longTerm = snapshot.longTermSummary.ifBlank { "暂无；以锁定设定与最近事件为准。" }

        return PromptBundle(
            system = """
                你是长篇小说写作引擎。你的首要任务是保持故事一致，而不是自由发挥。

                不可违反的规则：
                1. 被锁定的设定是事实，不得修改、否定或绕过。
                2. 本章必须完成章纲目标，并服务于卷纲和总纲主题。
                3. 不得让角色拥有其尚未知晓的信息，不得瞬移，不得无因改变性格、关系或能力。
                4. 新增重要人物、规则、能力、地点或道具时，必须在 stateChanges 中明确声明。
                5. 不得提前回收未到时机的伏笔；不得遗忘本章要求触及的伏笔。
                6. 若用户临时要求与锁定设定冲突，以锁定设定为准。
                7. 长期摘要、RAG 检索片段只作为历史证据；若与锁定圣经冲突，以锁定圣经为最高优先级。
                8. 【文风模板】决定叙述语气、句式、节奏、视角距离和修辞偏好；它可以改变“怎么写”，但不能改变“发生什么”。
                9. 输出必须是可解析 JSON，禁止在 JSON 前后添加解释或 Markdown。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                核心命题：${snapshot.novel.premise}
                主题：${snapshot.novel.theme}

                【文风模板】
                $styleRules

                【锁定设定】
                $hardRules

                【当前大纲链】
                $outline

                【长期故事摘要】
                $longTerm

                【RAG 检索与最近章节记忆】
                $recentMemory

                【人物当前状态】
                $characters

                【最近时间线】
                $timeline

                【相关伏笔】
                $foreshadowing

                【本章任务】
                章节：第${request.chapter.chapterNumber}章 ${request.chapter.title}
                唯一主目标：${request.chapter.objective}
                场景计划：
                $scenes
                目标字数：约${request.targetWords}字
                补充要求：${request.extraInstruction.ifBlank { "无" }}

                返回字段：title、content、summary、stateChanges、touchedForeshadowingIds。
                summary 必须是 120-260 字的高信息密度事实摘要，包含关键事件、人物状态变化、地点、获得/失去的信息和未解决问题，供后续长期记忆使用。
                stateChanges 每项包含 subject、field、before、after、evidence。人物关系变化请使用 field=relationship，after 写“目标人物=关系变化”；新出现但值得长期追踪的重要人物可使用 field=newCharacter，after 写“地点||情绪||目标||性格标签”。
            """.trimIndent(),
        )
    }
}
