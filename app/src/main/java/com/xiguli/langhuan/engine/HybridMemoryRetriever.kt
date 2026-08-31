package com.xiguli.langhuan.engine

import kotlin.math.sqrt

/**
 * 手机端轻量混合检索器。
 *
 * 不引入本地大模型：把中英文词元、中文 2/3-gram 哈希到固定维度向量，
 * 用余弦相似度 + 精确词元 + 来源权重 + 章节距离进行排序。
 *
 * 章节边界是硬过滤，不参与打分：
 * - 普通记忆只允许 currentChapter 及之前；
 * - ORIGINAL_* 原著证据严格只允许 currentChapter 之前，防止“写第301章时偷看第301章原文”。
 * Story Runtime 若要让“从第300章进入故事”看到第300章事实，可用 currentChapter=301 调用。
 */
data class MemoryCandidate(
    val text: String,
    val sourceType: String,
    val sourceId: String,
    val chapterNumber: Int?,
    val updatedAt: Long,
)

data class RetrievedMemory(
    val candidate: MemoryCandidate,
    val score: Double,
    /** 给 Context Builder / UI 的可解释召回信号，不参与后续生成事实。 */
    val reasons: List<String> = emptyList(),
)

class HybridMemoryRetriever(
    private val dimensions: Int = 384,
) {
    fun rank(
        query: String,
        candidates: List<MemoryCandidate>,
        currentChapter: Int,
        limit: Int,
    ): List<RetrievedMemory> {
        if (query.isBlank() || candidates.isEmpty() || limit <= 0) return emptyList()
        val queryVector = vectorize(query)
        val queryTerms = terms(query)
        val now = System.currentTimeMillis()

        return candidates.asSequence()
            .filter { candidate ->
                val chapter = candidate.chapterNumber ?: return@filter true
                if (candidate.sourceType.startsWith("ORIGINAL_")) chapter < currentChapter
                else chapter <= currentChapter
            }
            .map { candidate ->
                val vectorScore = cosine(queryVector, vectorize(candidate.text))
                val candidateTerms = terms(candidate.text)
                val exactScore = if (queryTerms.isEmpty()) 0.0 else {
                    queryTerms.count { it in candidateTerms }.toDouble() / queryTerms.size
                }
                val sourceBoost = when (candidate.sourceType) {
                    "ORIGINAL_KNOWLEDGE" -> 1.0
                    "ORIGINAL_RELATION" -> 0.99
                    "ORIGINAL_ENTITY" -> 0.98
                    "ORIGINAL_EVENT" -> 0.97
                    "ORIGINAL_SUMMARY" -> 0.95
                    "BIBLE" -> 1.0
                    "CHARACTER" -> 0.94
                    "GROWTH" -> 0.93
                    "ARC" -> 0.91
                    "FORESHADOW" -> 0.90
                    "MEDIUM" -> 0.86
                    "TIMELINE" -> 0.84
                    "LONG_SUMMARY" -> 0.79
                    "SUMMARY" -> 0.75
                    "CHAPTER" -> 0.68
                    else -> 0.55
                }
                val chapterBoost = candidate.chapterNumber?.let {
                    1.0 / (1.0 + kotlin.math.abs(currentChapter - it) / 10.0)
                } ?: 0.45
                val ageDays = ((now - candidate.updatedAt).coerceAtLeast(0L) / 86_400_000.0)
                val freshness = 1.0 / (1.0 + ageDays / 60.0)
                val score = vectorScore * 0.59 + exactScore * 0.22 +
                    sourceBoost * 0.10 + chapterBoost * 0.06 + freshness * 0.03
                val reasons = buildList {
                    if (candidate.sourceType.startsWith("ORIGINAL_")) add("原著章节边界内证据")
                    if (vectorScore >= 0.28) add("语义相关")
                    if (exactScore >= 0.12) add("关键词命中")
                    if (sourceBoost >= 0.90) add("高优先级结构化记忆")
                    if (chapterBoost >= 0.72) add("临近当前章节")
                    if (freshness >= 0.82) add("近期更新")
                    if (isEmpty()) add("综合相关性")
                }
                RetrievedMemory(candidate, score, reasons)
            }
            .filter { it.score >= 0.12 }
            .sortedByDescending { it.score }
            .distinctBy { it.candidate.text }
            .take(limit)
            .toList()
    }

    private fun vectorize(value: String): FloatArray {
        val vector = FloatArray(dimensions)
        val features = features(value)
        if (features.isEmpty()) return vector
        features.forEach { feature ->
            val hash = feature.hashCode()
            val index = (hash and Int.MAX_VALUE) % dimensions
            val sign = if ((hash ushr 30) and 1 == 0) 1f else -1f
            vector[index] += sign
        }
        var norm = 0.0
        vector.forEach { norm += it * it }
        if (norm > 0.0) {
            val scale = sqrt(norm).toFloat()
            for (i in vector.indices) vector[i] /= scale
        }
        return vector
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1.0, 1.0).coerceAtLeast(0.0)
    }

    private fun features(value: String): List<String> {
        val normalized = value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        if (normalized.isBlank()) return emptyList()

        val result = ArrayList<String>()
        normalized.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
            result += "w:$token"
            if (token.length >= 2) token.windowed(2).forEach { result += "b:$it" }
            if (token.length >= 3) token.windowed(3).forEach { result += "t:$it" }
        }

        val compact = normalized.filterNot(Char::isWhitespace)
        if (compact.length >= 2) compact.windowed(2).forEach { result += "c2:$it" }
        if (compact.length >= 3) compact.windowed(3).forEach { result += "c3:$it" }
        return result
    }

    private fun terms(value: String): Set<String> = features(value)
        .asSequence()
        .filter { it.startsWith("w:") || it.startsWith("b:") || it.startsWith("c2:") }
        .map { it.substringAfter(':') }
        .filter { it.length >= 2 }
        .toSet()
}
