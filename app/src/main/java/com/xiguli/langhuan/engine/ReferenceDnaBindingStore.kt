package com.xiguli.langhuan.engine

import android.content.Context

enum class ReferenceDnaPurpose {
    BLUEPRINT,
    SCENE,
    PROSE,
    EDITOR,
}

data class ReferenceDnaBindingSummary(
    val reportIds: List<String>,
    val titles: List<String>,
    val availableItems: Int,
) {
    val count: Int get() = reportIds.size
    val label: String get() = if (count == 0) "未绑定参考 DNA" else "已绑定 $count 本 · 可检索 $availableItems 条 DNA"
}

/**
 * A selected distillation report is a persistent project reference, not a one-shot creation-chat hint.
 * Report data itself remains in ReferenceDistillationReportStore; this store only remembers which reports
 * a novel intentionally selected, so source-specific STORY facts never need to be copied into Canon.
 */
class ReferenceDnaBindingStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val reports = ReferenceDistillationReportStore(app)

    fun bind(novelId: String, reportIds: List<String>) {
        if (novelId.isBlank()) return
        val valid = reportIds.distinct().filter { reports.load(it) != null }
        prefs.edit().putString(key(novelId), valid.joinToString(SEPARATOR)).apply()
    }

    fun ids(novelId: String): List<String> = prefs.getString(key(novelId), "")
        .orEmpty()
        .split(SEPARATOR)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .filter { reports.load(it) != null }

    fun clear(novelId: String) {
        prefs.edit().remove(key(novelId)).apply()
    }

    fun summary(novelId: String): ReferenceDnaBindingSummary {
        val ids = ids(novelId)
        val selected = ids.mapNotNull(reports::load)
        return ReferenceDnaBindingSummary(
            reportIds = ids,
            titles = selected.map { it.title },
            availableItems = selected.sumOf(reports::retainedItemCount),
        )
    }

    fun search(
        novelId: String,
        query: String,
        purpose: ReferenceDnaPurpose,
        maxChars: Int = 7_200,
    ): String {
        val selected = ids(novelId)
        if (selected.isEmpty()) return ""
        val purposeQuery = when (purpose) {
            ReferenceDnaPurpose.BLUEPRINT -> "原创迁移 结构 节奏 悬念 人物塑造 世界规则 $query"
            ReferenceDnaPurpose.SCENE -> "场景 结构 节奏 信息释放 悬念 章末钩子 人物关系 $query"
            ReferenceDnaPurpose.PROSE -> "正文 文风 叙事距离 句段节奏 对白 信息释放 人物塑造 氛围 $query"
            ReferenceDnaPurpose.EDITOR -> "主编 审稿 文风 节奏 结构 悬念 信息释放 禁止照搬 $query"
        }
        return reports.searchContext(
            selectedTaskIds = selected,
            query = purposeQuery,
            maxChars = maxChars,
            maxItemsPerReport = when (purpose) {
                ReferenceDnaPurpose.BLUEPRINT -> 18
                ReferenceDnaPurpose.SCENE -> 14
                ReferenceDnaPurpose.PROSE -> 16
                ReferenceDnaPurpose.EDITOR -> 12
            },
            allowedKinds = CREATIVE_KINDS,
        )
    }

    fun usage(novelId: String, query: String, purpose: ReferenceDnaPurpose): ReferenceDnaUsage {
        val selected = ids(novelId)
        if (selected.isEmpty()) return ReferenceDnaUsage(0, 0, 0, emptyList())
        return reports.usage(
            selectedTaskIds = selected,
            query = when (purpose) {
                ReferenceDnaPurpose.BLUEPRINT -> "原创迁移 结构 节奏 $query"
                ReferenceDnaPurpose.SCENE -> "场景 结构 悬念 节奏 $query"
                ReferenceDnaPurpose.PROSE -> "正文 文风 对白 节奏 $query"
                ReferenceDnaPurpose.EDITOR -> "主编 审稿 结构 文风 $query"
            },
            allowedKinds = CREATIVE_KINDS,
        )
    }

    private fun key(novelId: String) = "novel:$novelId"

    private companion object {
        const val PREFS = "reference_dna_bindings_v2"
        const val SEPARATOR = "\u001f"
        val CREATIVE_KINDS = setOf("STYLE", "KEEP", "TRANSFORM", "AVOID")
    }
}
