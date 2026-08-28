package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

data class PromptBundle(
    val system: String,
    val user: String,
    val attachments: List<PromptAttachment> = emptyList(),
)

data class PromptAttachment(
    val fileName: String,
    val mimeType: String,
    val base64Data: String,
)

class PromptAssembler(
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
    private val longFormEngine: LongFormContinuityEngine = LongFormContinuityEngine(),
) {
    fun build(request: GenerationRequest): PromptBundle {
        val snapshot = request.snapshot
        val currentChapter = request.chapter.chapterNumber.coerceAtLeast(1)
        val styleRules = snapshot.bible
            .filter { it.category == BibleCategory.STYLE }
            .joinToString("\n") { "- ${it.name}: ${it.content}${if (it.locked) "（必须遵守）" else "（偏好）"}" }
            .ifBlank { "- 暂无专门文风模板；保持自然、稳定、符合当前作品类型。" }

        val hardRules = snapshot.bible
            .filter { it.category != BibleCategory.STYLE && (it.locked || it.category == BibleCategory.FORBIDDEN) }
            .joinToString("\n") { "- [${it.category}] ${it.name}: ${it.content}" }

        val outline = snapshot.activeOutline
            .sortedWith(compareBy({ it.level.ordinal }, { it.order }))
            .joinToString("\n") { node ->
                val level = when (node.level) {
                    OutlineLevel.MASTER -> "总纲"
                    OutlineLevel.VOLUME -> "卷纲"
                    OutlineLevel.CHAPTER -> "章纲"
                }
                buildString {
                    append("- [$level] ${node.title}｜目标:${node.objective}｜冲突:${node.conflict}｜转折:${node.turningPoint}")
                    if (node.mustInclude.isNotEmpty()) append("｜必须包含:${node.mustInclude.joinToString("、")}")
                    if (node.forbidden.isNotEmpty()) append("｜本层禁止:${node.forbidden.joinToString("、")}")
                }
            }

        val characters = snapshot.characters.joinToString("\n") {
            "- ${it.name}: 地点=${it.location}; 身体=${it.physicalState}; 情绪=${it.emotionalState}; " +
                "目标=${it.goal}; 性格=${it.personality.joinToString("、")}; 关系=${it.relationshipNotes.entries.joinToString("；") { e -> "${e.key}=${e.value}" }}; 已知秘密=${it.knownSecrets.joinToString("、")}"
        }

        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(40)
            .joinToString("\n") {
                val structured = if (it.storyDay > 0) {
                    "故事第${it.storyDay}天·${it.timeOfDay.ifBlank { it.storyTime }}｜距上次=${it.elapsedFromPrevious.ifBlank { "未记录" }}${if (it.isFlashback) "｜闪回" else ""}"
                } else it.storyTime.ifBlank { "旧时间记录未结构化" }
                "- 第${it.chapter}章/$structured/${it.location}: ${it.summary}; 后果=${it.consequences.joinToString("、")}"
            }

        val foreshadowing = snapshot.relevantForeshadowing.joinToString("\n") { item ->
            val urgency = when {
                item.status in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED) -> ""
                item.expectedChapterEnd > 0 && currentChapter > item.expectedChapterEnd -> "；回收提醒=已超过计划窗口"
                item.expectedChapterStart > 0 && currentChapter >= item.expectedChapterStart -> "；回收提醒=已进入计划窗口"
                else -> ""
            }
            "- id=${item.id}; ${item.title}; 状态=${item.status}; 计划窗口=${item.expectedChapterStart}-${item.expectedChapterEnd}$urgency; 细节=${item.detail}; 预期回收=${item.expectedPayoff}"
        }

        val scenes = request.chapter.scenePlan.sortedBy { it.order }.joinToString("\n") {
            val clock = if (it.storyDay > 0 || it.timeOfDay.isNotBlank()) {
                "故事第${it.storyDay.takeIf { day -> day > 0 } ?: 0}天·${it.timeOfDay.ifBlank { "待锁定" }}; 距上一场=${it.elapsedFromPrevious.ifBlank { "连续" }}; ${if (it.isFlashback) "闪回" else "主时间线"}; "
            } else "时间沿用时间轴锁; "
            "- 场景${it.order}: $clock 视角=${it.viewpoint}; 地点=${it.location}; 目的=${it.purpose}; 冲突=${it.conflict}; 结果=${it.outcome}"
        }

        val recentMemory = snapshot.recentSummaries.joinToString("\n") { "- $it" }
        val longTerm = snapshot.longTermSummary.ifBlank { "暂无；以锁定设定与最近事件为准。" }
        val chronology = chronologyGuard.promptText(snapshot, request.chapter.scenePlan)
        val longFormNavigation = longFormEngine.promptText(snapshot)

        return PromptBundle(
            system = """
                你是长篇小说写作引擎。你的首要任务是保持故事一致，而不是自由发挥。

                不可违反的规则：
                1. 被锁定的设定是事实，不得修改、否定或绕过。
                2. 本章必须完成章纲目标，并服务于卷纲和总纲主题。
                3. 不得让角色拥有其尚未知晓的信息，不得瞬移，不得无因改变性格、关系或能力。
                4. 新增重要人物、规则、能力、地点或道具时，必须在 stateChanges 中明确声明。
                5. 不得提前回收未到时机的伏笔；进入计划回收窗口后可自然触及或回收，超过计划窗口的旧伏笔应优先寻找自然处理机会，但禁止为了清提醒而机械硬塞。
                6. 若用户临时要求与锁定设定冲突，以锁定设定为准。
                7. 长期摘要、RAG 检索片段只作为历史证据；若与锁定圣经冲突，以锁定圣经为最高优先级。
                8. 【文风模板】决定叙述语气、句式、节奏、视角距离和修辞偏好；它可以改变“怎么写”，但不能改变“发生什么”。
                9. 大纲中的“必须包含”属于硬性验收条件；“本层禁止”与 FORBIDDEN 圣经同级，不得以任何方式绕过或变相出现。
                10. 每个场景必须产生信息、代价、关系变化、目标进展或新的选择之一；禁止连续大段原地解释和无状态变化的过场。
                11. 【时间轴锁】与锁定设定同级。没有场景计划或章纲授权，禁止擅自跨天、跨月、跨年、切闪回或改变事件先后顺序。
                12. 场景之间必须体现合理耗时。人物换地点、睡眠、等待、调查和交通不能出现“空间到了但时间没走”的瞬移。
                13. 任何过去叙事都要区分“人物短暂回忆”与“真正闪回场景”；只有后者才能改变叙事时间层。
                14. 【超长篇导航】是滚动规划与压缩记忆，不高于锁定圣经；但本章应优先推进当前剧情弧，避免在旧弧未收束时无理由再开一条同等级主线。
                15. 角色成长必须由选择、代价和章级事件推动。不得因为“已经写了很多章”就自动性格突变，也不得让成长曲线长期完全静止。
                16. 输出必须是可解析 JSON，禁止在 JSON 前后添加解释或 Markdown。
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

                【超长篇导航】
                $longFormNavigation

                【时间轴锁】
                $chronology

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
                summary 必须是 120-260 字的高信息密度事实摘要，包含关键事件、人物状态变化、地点、获得/失去的信息、未解决问题，并在末尾明确写“本章结束时=故事第N天·时段”。
                stateChanges 每项包含 subject、field、before、after、evidence。人物关系变化请使用 field=relationship，after 写“目标人物=关系变化”；新出现但值得长期追踪的重要人物可使用 field=newCharacter，after 写“地点||情绪||目标||性格标签”。
            """.trimIndent(),
        )
    }
}
