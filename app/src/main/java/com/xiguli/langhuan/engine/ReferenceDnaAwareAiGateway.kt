package com.xiguli.langhuan.engine

import android.content.Context
import com.xiguli.langhuan.domain.GeneratedChapter

/** Proof emitted only when Reference DNA was actually found and injected into a model prompt. */
data class ReferenceDnaExecutionEvidence(
    val task: AiTaskType,
    val purpose: ReferenceDnaPurpose,
    val injectedChars: Int,
)

/**
 * Injects only creative-transfer DNA into prompts for an already-created novel.
 * STORY facts from the source work are intentionally excluded here; they are available only in
 * reference-fact conversations. This keeps source names/powers/mysteries out of the user's Canon.
 */
class ReferenceDnaAwareAiGateway(
    context: Context,
    private val novelId: String,
    private val delegate: AiGateway,
    private val onDnaInjected: (ReferenceDnaExecutionEvidence) -> Unit = {},
) : AiGateway, AiTaskAttributionSource, AiTaskQualityFeedback {
    private val bindings = ReferenceDnaBindingStore(context.applicationContext)

    override suspend fun generate(prompt: PromptBundle): GeneratedChapter = delegate.generate(enrich(prompt))

    override suspend fun generateText(prompt: PromptBundle): String = delegate.generateText(enrich(prompt))

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter =
        delegate.generateStreaming(enrich(prompt), onDelta)

    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String =
        delegate.generateTextStreaming(enrich(prompt), onDelta)

    override fun modelAttributions() = (delegate as? AiTaskAttributionSource)?.modelAttributions().orEmpty()

    override fun recordQualitySignal(task: AiTaskType, signal: AiQualitySignal) {
        (delegate as? AiTaskQualityFeedback)?.recordQualitySignal(task, signal)
    }

    private fun enrich(prompt: PromptBundle): PromptBundle {
        if (novelId.isBlank()) return prompt
        val task = AiPromptTaskClassifier.classify(prompt) ?: return prompt
        val purpose = when (task) {
            AiTaskType.SCENE_DIRECTOR, AiTaskType.AUTONOMOUS_PLANNER -> ReferenceDnaPurpose.SCENE
            AiTaskType.PROSE_AUTHOR, AiTaskType.NOVELIZATION, AiTaskType.EDITOR_REWRITE -> ReferenceDnaPurpose.PROSE
            AiTaskType.EDITOR_REVIEW, AiTaskType.FULL_BOOK_EDITOR, AiTaskType.EXECUTION_AUDIT -> ReferenceDnaPurpose.EDITOR
            else -> return prompt
        }
        val query = buildString {
            append(prompt.user.take(3_800))
            append(' ')
            append(prompt.system.take(1_200))
        }
        val dna = bindings.search(novelId, query, purpose)
        if (dna.isBlank()) return prompt
        val policy = when (purpose) {
            ReferenceDnaPurpose.SCENE -> "只迁移场景组织、节奏、悬念、信息释放和结构方法。不能改变本书章纲、Canon、人物状态或时间线。"
            ReferenceDnaPurpose.PROSE -> "只迁移文风、叙事距离、节奏、对白、场景化和信息释放方法。不能复制原作专名、具体能力规则、谜底或剧情骨架。"
            ReferenceDnaPurpose.EDITOR -> "把它当作风格/结构目标检查，不是 Canon。偏离参考 DNA 本身绝不能判为硬冲突或 BLOCKING。"
            ReferenceDnaPurpose.BLUEPRINT -> "只做原创迁移。"
        }
        onDnaInjected(
            ReferenceDnaExecutionEvidence(
                task = task,
                purpose = purpose,
                injectedChars = dna.length,
            )
        )
        return prompt.copy(
            system = prompt.system + "\n\n【作品长期绑定的 Reference DNA｜低于章节合同/Canon】\n$policy\n$dna",
        )
    }
}
