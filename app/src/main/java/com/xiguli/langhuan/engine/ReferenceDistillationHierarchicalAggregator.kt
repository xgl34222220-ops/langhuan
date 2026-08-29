package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.StateChange

/**
 * Bounded hierarchical reducer for long reference-novel distillation.
 *
 * V2 still creates compact group/final summaries for a readable report, but it no longer treats those
 * summaries as the only source of truth. Every structured batch DNA line is retained and attached to
 * the returned dossier so ReferenceDistillationReportStore can build a searchable long-form DNA index.
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
        val final = aggregateFinal(
            gateway = gateway,
            manuscript = manuscript,
            metrics = metrics,
            groupSummaries = summaries,
        )
        val retained = parseRetainedObservations(observations)
        return final.copy(
            stateChanges = (final.stateChanges + retained)
                .distinctBy { change ->
                    listOf(
                        normalizeSubject(change.subject),
                        change.field.trim().uppercase(),
                        normalizeValue(change.after.ifBlank { change.before }),
                    ).joinToString("|")
                }
                .take(MAX_RETAINED_STATE_CHANGES),
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
                    2. 同一人物、规则、副本或阶段出现冲突时，保留“阶段变化/样本不一致”，禁止擅自选边。
                    3. 副本相关 INSTANCE_* 条目要保留副本归属，不要把多个副本的规则、NPC、通关条件混成一套。
                    4. 这里只生成可阅读摘要；批次级 DNA 会由 App 单独长期保留，所以不要为了压缩而杜撰概括。
                    5. 不输出原文句子，不仿写。

                    输出 GeneratedChapter JSON：
                    - title="DISTILL_GROUP"；
                    - content=220-450字 Story 层阶段摘要；
                    - summary=120-260字 Style 层稳定规律；
                    - stateChanges=10-24项，subject 只允许 STYLE / STORY / KEEP / TRANSFORM / AVOID；
                    - evidence 只保留短证据标签；
                    - touchedForeshadowingIds=[]。
                """.trimIndent(),
                user = """
                    作品：$title
                    聚合组：$index/$total

                    【本组已蒸馏观察】
                    ${observations.joinToString("\n\n---\n\n").take(11_000)}

                    只做去重、冲突保留和摘要，不重新猜测原作。
                """.trimIndent(),
            )
        )
        return compact(output).take(4_200)
    }

    private suspend fun aggregateFinal(
        gateway: AiGateway,
        manuscript: ImportedManuscript,
        metrics: String,
        groupSummaries: List<String>,
    ): GeneratedChapter = gateway.generate(
        PromptBundle(
            system = """
                你是琅嬛的“作品双层 DNA 最终聚合器”。输入已经经过分层摘要；原始批次 DNA 由 App 另行保留，因此这里的职责是形成稳定总览，而不是把所有细节再次压进几十条里。

                必须同时输出：
                1. Story DNA（content，650-1400字）：主角身份/动机/困境、重要配角与关系、世界观、硬规则、能力/成长体系、主要势力与地点、核心谜团/冲突、剧情阶段演化、主题；如果存在副本/试炼/关卡/规则场域，还要单独总结主要副本及其类型、进入条件、核心规则、阶段目标、关键 NPC/怪物、威胁与失败条件、线索链、通关条件、奖励/代价和与主线关系。只写摘要支持的内容；冲突必须标阶段变化或样本不一致。
                2. Style DNA（summary，300-600字）：视角、叙事距离、句段节奏、对白、信息释放、悬念、章末钩子、人物塑造、规则呈现、场景切换、情绪与结构模式。
                3. stateChanges 28-72项，尽量覆盖不同维度而不是同义重复：
                   - STYLE：稳定高层技法；
                   - STORY：结构化事实或阶段变化；副本使用 INSTANCE / INSTANCE_RULE / INSTANCE_OBJECTIVE / INSTANCE_NPC / INSTANCE_THREAT / INSTANCE_CLUE / INSTANCE_CLEAR / INSTANCE_REWARD / INSTANCE_MAINLINE；
                   - KEEP：可迁移的通用机制；
                   - TRANSFORM：值得参考但必须原创重构的机制；
                   - AVOID：原作专名、人物组合、具体能力规则、独特谜底、副本专名与独特通关解法、剧情骨架、标志性表达等禁止照搬内容。
                4. 同一副本的各项信息必须能看出归属于哪个副本；不要把多个副本揉成“典型副本机制”。
                5. evidence 只能使用“分层聚合1/跨段共同出现/本地统计”等短标签，不写原句。
                6. touchedForeshadowingIds=[]。

                这是研究档案，不是仿写模板。可以准确描述原作帮助理解，但新书引用时必须原创转换。
            """.trimIndent(),
            user = """
                作品：${manuscript.title}
                总章节：${manuscript.chapters.size}

                【全书本地结构统计】
                ${metrics.take(3_200)}

                【已完成的分层聚合摘要】
                ${groupSummaries.joinToString("\n\n===\n\n").take(20_000)}

                生成最终 Story DNA + Style DNA 总览。若存在副本体系，必须给出可检索的副本结构总览；不要逐章复述，也不要丢掉 KEEP / TRANSFORM / AVOID 的方法论边界。
            """.trimIndent(),
        )
    )

    private fun compact(output: GeneratedChapter): String = buildString {
        if (output.content.isNotBlank()) appendLine("STORY_GROUP: ${output.content.take(800)}")
        if (output.summary.isNotBlank()) appendLine("STYLE_GROUP: ${output.summary.take(560)}")
        output.stateChanges.take(28).forEach { change ->
            val subject = normalizeSubject(change.subject)
            if (subject !in ALLOWED_SUBJECTS) return@forEach
            val value = change.after.ifBlank { change.before }.trim().take(420)
            if (value.isBlank()) return@forEach
            val evidence = change.evidence.trim().take(100)
            appendLine("$subject/${change.field.trim().ifBlank { "UNKNOWN" }}: $value${if (evidence.isNotBlank()) " [$evidence]" else ""}")
        }
    }

    private fun parseRetainedObservations(observations: List<String>): List<StateChange> = observations
        .flatMap { observation ->
            observation.lineSequence().mapNotNull { raw ->
                val line = raw.trim()
                val slash = line.indexOf('/')
                val colon = line.indexOf(':')
                if (slash <= 0 || colon <= slash + 1) return@mapNotNull null
                val subject = normalizeSubject(line.substring(0, slash))
                if (subject !in ALLOWED_SUBJECTS) return@mapNotNull null
                val field = line.substring(slash + 1, colon).trim().ifBlank { "UNKNOWN" }
                var value = line.substring(colon + 1).trim()
                if (value.isBlank()) return@mapNotNull null
                var evidence = ""
                val evidenceStart = value.lastIndexOf(" [")
                if (evidenceStart >= 0 && value.endsWith(']')) {
                    evidence = value.substring(evidenceStart + 2, value.length - 1).trim()
                    value = value.substring(0, evidenceStart).trim()
                }
                StateChange(
                    subject = subject,
                    field = field,
                    after = value.take(560),
                    evidence = evidence.take(120),
                )
            }.toList()
        }

    private fun normalizeSubject(value: String): String = when (value.trim().uppercase()) {
        "DNA" -> "STYLE"
        else -> value.trim().uppercase()
    }

    private fun normalizeValue(value: String): String = value.lowercase()
        .replace(Regex("[\\s，。！？、,.!?;；:：()（）《》“”\\\"'·._—-]"), "")
        .take(220)

    private companion object {
        const val GROUP_SIZE = 3
        const val AGGREGATION_VERSION = 2
        const val MAX_RETAINED_STATE_CHANGES = 960
        val ALLOWED_SUBJECTS = setOf("STYLE", "STORY", "KEEP", "TRANSFORM", "AVOID")
    }
}
