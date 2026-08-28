package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.domain.GeneratedChapter

/**
 * Bounded hierarchical reducer for long reference-novel distillation.
 *
 * Batch observations are already compact and contain no imported prose. Instead of putting every
 * observation into one giant final request, this reducer first merges 2-3 neighboring observations,
 * checkpoints every group, then builds the final Story + Style DNA from those bounded summaries.
 * A timeout late in aggregation therefore never forces completed batch analysis to run again.
 */
class ReferenceDistillationHierarchicalAggregator(
    private val checkpointStore: ReferenceDistillationCheckpointStore,
) {
    suspend fun aggregate(
        gateway: AiGateway,
        manuscript: ImportedManuscript,
        metrics: String,
        observations: List<String>,
        fingerprint: String,
        onProgress: suspend (stage: String, progress: Int, group: Int, groups: Int) -> Unit,
    ): GeneratedChapter {
        require(observations.isNotEmpty()) { "没有可聚合的蒸馏观察" }

        val groups = observations.chunked(GROUP_SIZE)
        var checkpoint = checkpointStore.load(fingerprint)
            ?: error("蒸馏断点不存在，无法继续聚合")

        val compatibleAggregation = checkpoint.aggregationVersion == AGGREGATION_VERSION &&
            checkpoint.totalAggregateGroups == groups.size &&
            checkpoint.aggregateSummaries.size >= checkpoint.completedAggregateGroups

        var completed = if (compatibleAggregation) {
            checkpoint.completedAggregateGroups.coerceIn(0, groups.size)
        } else {
            0
        }
        val summaries = if (compatibleAggregation) {
            checkpoint.aggregateSummaries.take(completed).toMutableList()
        } else {
            mutableListOf()
        }

        if (!compatibleAggregation) {
            checkpoint = checkpoint.copy(
                completedAggregateGroups = 0,
                totalAggregateGroups = groups.size,
                aggregateSummaries = emptyList(),
                aggregationVersion = AGGREGATION_VERSION,
            )
            checkpointStore.save(checkpoint)
        }

        for (index in completed until groups.size) {
            val progress = 84 + ((index + 1) * 10 / groups.size.coerceAtLeast(1))
            onProgress("aggregate_group", progress.coerceAtMost(94), index + 1, groups.size)

            val summary = aggregateGroup(
                gateway = gateway,
                title = manuscript.title,
                observations = groups[index],
                index = index + 1,
                total = groups.size,
            )
            summaries += summary
            completed = index + 1

            checkpoint = (checkpointStore.load(fingerprint) ?: checkpoint).copy(
                completedAggregateGroups = completed,
                totalAggregateGroups = groups.size,
                aggregateSummaries = summaries.toList(),
                aggregationVersion = AGGREGATION_VERSION,
            )
            checkpointStore.save(checkpoint)
        }

        onProgress("aggregate_final", 96, groups.size, groups.size)
        return aggregateFinal(
            gateway = gateway,
            manuscript = manuscript,
            metrics = metrics,
            groupSummaries = summaries,
        )
    }

    private suspend fun aggregateGroup(
        gateway: AiGateway,
        title: String,
        observations: List<String>,
        index: Int,
        total: Int,
    ): String {
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是琅嬛的“DNA 分层归并器”。这不是最终报告，只负责把相邻的若干批次观察压缩成一个可靠的小组摘要。

                    原则：
                    1. 合并重复事实和重复写法，不要为了完整而扩写。
                    2. 同一人物、规则或阶段出现冲突时，保留“阶段变化/样本不一致”，禁止擅自选边。
                    3. 只保留后续最终聚合真正需要的证据；不得补原作中没有被观察支持的细节。
                    4. 不输出原文句子，不仿写。

                    输出 GeneratedChapter JSON：
                    - title="DISTILL_GROUP"；
                    - content=220-450字 Story 层阶段摘要；
                    - summary=120-260字 Style 层稳定规律；
                    - stateChanges=10-22项，subject 只允许 STYLE / STORY / KEEP / TRANSFORM / AVOID；
                    - evidence 只保留短证据标签；
                    - touchedForeshadowingIds=[]。
                """.trimIndent(),
                user = """
                    作品：$title
                    聚合组：$index/$total

                    【本组已蒸馏观察】
                    ${observations.joinToString("\n\n---\n\n").take(9_500)}

                    只做去重、冲突保留和压缩，不重新猜测原作。
                """.trimIndent(),
            )
        )
        return compact(output).take(3_400)
    }

    private suspend fun aggregateFinal(
        gateway: AiGateway,
        manuscript: ImportedManuscript,
        metrics: String,
        groupSummaries: List<String>,
    ): GeneratedChapter = gateway.generate(
        PromptBundle(
            system = """
                你是琅嬛的“作品双层 DNA 最终聚合器”。输入已经经过分层压缩，不需要再逐批复盘。

                必须同时输出：
                1. Story DNA（content，500-900字）：主角身份/动机/困境、重要配角与关系、世界观、硬规则、能力/成长体系、主要势力与地点、核心谜团/冲突、剧情阶段演化、主题。只写摘要支持的内容；冲突必须标阶段变化或样本不一致。
                2. Style DNA（summary，220-420字）：视角、叙事距离、句段节奏、对白、信息释放、悬念、章末钩子、人物塑造、规则呈现、场景切换、情绪与结构模式。
                3. stateChanges 18-36项：
                   - STYLE：稳定高层技法；
                   - STORY：结构化事实或阶段变化；
                   - KEEP：可迁移的通用机制；
                   - TRANSFORM：值得参考但必须原创重构的机制；
                   - AVOID：原作专名、人物组合、具体能力规则、独特谜底、剧情骨架、标志性表达等禁止照搬内容。
                4. evidence 只能使用“分层聚合1/跨段共同出现/本地统计”等短标签，不写原句。
                5. touchedForeshadowingIds=[]。

                这是研究档案，不是仿写模板。可以准确描述原作以帮助用户理解，但新书引用时必须原创转换。
            """.trimIndent(),
            user = """
                作品：${manuscript.title}
                总章节：${manuscript.chapters.size}

                【全书本地结构统计】
                ${metrics.take(2_800)}

                【已完成的分层聚合摘要】
                ${groupSummaries.joinToString("\n\n===\n\n").take(14_000)}

                直接生成最终 Story DNA + Style DNA。不要重新扩写成逐章复述。
            """.trimIndent(),
        )
    )

    private fun compact(output: GeneratedChapter): String = buildString {
        if (output.content.isNotBlank()) appendLine("STORY_GROUP: ${output.content.take(650)}")
        if (output.summary.isNotBlank()) appendLine("STYLE_GROUP: ${output.summary.take(420)}")
        output.stateChanges.take(22).forEach { change ->
            val subject = change.subject.trim().uppercase()
            if (subject !in ALLOWED_SUBJECTS) return@forEach
            val value = change.after.ifBlank { change.before }.trim().take(300)
            if (value.isBlank()) return@forEach
            val evidence = change.evidence.trim().take(80)
            appendLine("$subject/${change.field.trim().ifBlank { "UNKNOWN" }}: $value${if (evidence.isNotBlank()) " [$evidence]" else ""}")
        }
    }

    private companion object {
        const val GROUP_SIZE = 3
        const val AGGREGATION_VERSION = 1
        val ALLOWED_SUBJECTS = setOf("STYLE", "STORY", "KEEP", "TRANSFORM", "AVOID")
    }
}
