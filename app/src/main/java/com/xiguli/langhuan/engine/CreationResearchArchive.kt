package com.xiguli.langhuan.engine

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ArchivedResearchSource(
    val title: String,
    val url: String,
    val snippet: String,
    val detail: String = "",
)

@Serializable
data class ResearchArchiveEntry(
    val target: String,
    val kind: String,
    val queries: List<String> = emptyList(),
    val sources: List<ArchivedResearchSource> = emptyList(),
    val searchCount: Int = 1,
    val updatedAt: Long = 0L,
)

@Serializable
data class CreationResearchArchive(
    val lastAuthorTarget: String? = null,
    val lastWorkTargets: List<String> = emptyList(),
    val entries: List<ResearchArchiveEntry> = emptyList(),
    val updatedAt: Long = 0L,
)

/**
 * Persistent research memory for creation conversations.
 *
 * Public evidence is kept separately from model answers. A temporary search failure therefore cannot
 * erase previously collected author/work evidence, and a restarted process can still resolve follow-up
 * references such as “他”“这本”“前面那几本”.
 */
class CreationResearchArchiveStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "research/creation_research_archive.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun load(): CreationResearchArchive = runCatching {
        if (!file.baseFile.exists()) return@runCatching CreationResearchArchive()
        file.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
            json.decodeFromString<CreationResearchArchive>(reader.readText())
        }
    }.getOrDefault(CreationResearchArchive())

    fun merge(
        bundle: CreationResearchBundle?,
        detectedTargets: List<String> = emptyList(),
    ): CreationResearchArchive {
        val before = load()
        val now = System.currentTimeMillis()
        val entries = before.entries.associateBy { key(it.target) }.toMutableMap()

        var authorTarget: String? = null
        val explicitWorks = linkedSetOf<String>()

        bundle?.groups.orEmpty().forEach { group ->
            val authorGroup = group.target.trim().startsWith("作者：") || group.target.trim().startsWith("作者:")
            val target = canonicalTarget(group.target)
            if (target.isBlank()) return@forEach
            if (authorGroup) authorTarget = target else explicitWorks += target

            mergeEntry(
                entries = entries,
                target = target,
                kind = if (authorGroup) "author" else "work",
                query = group.result.query,
                sources = group.result.sources,
                now = now,
            )

            // Author-portfolio evidence is prefixed with [书名]. Promote it into standalone dossiers.
            group.result.sources.groupBy { workFromSourceTitle(it.title) }.forEach { (work, sources) ->
                if (work.isNullOrBlank()) return@forEach
                explicitWorks += work
                mergeEntry(
                    entries = entries,
                    target = work,
                    kind = "work",
                    query = "由作者作品研究自动归档：$target",
                    sources = sources,
                    now = now,
                )
            }
        }

        val detected = detectedTargets.map(::canonicalTarget).filter(String::isNotBlank)
        val lastWorks = when {
            explicitWorks.isNotEmpty() -> explicitWorks.take(8)
            detected.isNotEmpty() && authorTarget == null -> detected.take(8)
            else -> before.lastWorkTargets
        }
        val nextAuthor = when {
            authorTarget != null -> authorTarget
            explicitWorks.isNotEmpty() && bundle?.groups.orEmpty().any { it.target.trim().startsWith("作者") } -> before.lastAuthorTarget
            detected.isNotEmpty() && bundle?.groups.orEmpty().none { it.target.trim().startsWith("作者") } -> null
            else -> before.lastAuthorTarget
        }

        val next = CreationResearchArchive(
            lastAuthorTarget = nextAuthor,
            lastWorkTargets = lastWorks,
            entries = entries.values.sortedByDescending { it.updatedAt }.take(MAX_ARCHIVE_ENTRIES),
            updatedAt = now,
        )
        save(next)
        return next
    }

    /** Keep dossiers, but detach pronouns from the previous new-book session. */
    fun clearSessionContext(): CreationResearchArchive {
        val current = load()
        val next = current.copy(
            lastAuthorTarget = null,
            lastWorkTargets = emptyList(),
            updatedAt = System.currentTimeMillis(),
        )
        save(next)
        return next
    }

    fun seed(engine: WebResearchEngine, state: CreationResearchArchive = load()) {
        state.lastAuthorTarget?.takeIf(String::isNotBlank)?.let { engine.referenceTargets("作者：$it") }
        if (state.lastWorkTargets.isNotEmpty()) {
            engine.referenceTargets(state.lastWorkTargets.joinToString(" ") { "《$it》" })
        }
    }

    fun contextFor(
        requestedTargets: List<String>,
        state: CreationResearchArchive = load(),
        maxChars: Int = 7_000,
    ): String {
        if (state.entries.isEmpty()) return ""
        val wanted = linkedSetOf<String>()
        requestedTargets.map(::canonicalTarget).filter(String::isNotBlank).forEach { wanted += key(it) }
        if (wanted.isEmpty()) {
            state.lastAuthorTarget?.let { wanted += key(it) }
            state.lastWorkTargets.forEach { wanted += key(it) }
        } else if (state.lastAuthorTarget != null && wanted.contains(key(state.lastAuthorTarget))) {
            state.lastWorkTargets.forEach { wanted += key(it) }
        }
        if (wanted.isEmpty()) return ""

        val selected = state.entries
            .filter { wanted.contains(key(it.target)) }
            .sortedWith(compareBy<ResearchArchiveEntry> { if (it.kind == "author") 0 else 1 }.thenByDescending { it.updatedAt })
            .take(8)
        if (selected.isEmpty()) return ""

        return buildString {
            appendLine("【琅嬛长期研究档案】")
            appendLine("这是此前已经检索并保存在本机的公开证据。本轮搜索失败不能清空这些档案；新旧证据冲突时只降低对应事实置信度。")
            selected.forEach { entry ->
                appendLine("【${if (entry.kind == "author") "作者" else "作品"}：${entry.target}｜累计检索 ${entry.searchCount} 次】")
                entry.sources.take(5).forEachIndexed { index, source ->
                    appendLine("- ${source.title}")
                    if (source.snippet.isNotBlank()) appendLine("  摘要：${source.snippet.take(260)}")
                    if (index < 2 && source.detail.isNotBlank()) appendLine("  深读：${source.detail.take(500)}")
                }
            }
        }.take(maxChars.coerceIn(1_000, 10_000))
    }

    private fun mergeEntry(
        entries: MutableMap<String, ResearchArchiveEntry>,
        target: String,
        kind: String,
        query: String,
        sources: List<WebResearchSource>,
        now: Long,
    ) {
        val entryKey = key(target)
        val old = entries[entryKey]
        val archived = sources.map { source ->
            ArchivedResearchSource(
                title = source.title.take(MAX_TITLE_CHARS),
                url = source.url.take(MAX_URL_CHARS),
                snippet = source.snippet.take(MAX_SNIPPET_CHARS),
                detail = source.detail.take(MAX_DETAIL_CHARS),
            )
        }
        val mergedSources = (archived + old?.sources.orEmpty())
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
            .take(MAX_SOURCES_PER_ENTRY)
        val queries = (listOf(query) + old?.queries.orEmpty())
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_QUERIES_PER_ENTRY)
        entries[entryKey] = ResearchArchiveEntry(
            target = target,
            kind = if (old?.kind == "author" || kind == "author") "author" else "work",
            queries = queries,
            sources = mergedSources,
            searchCount = (old?.searchCount ?: 0) + 1,
            updatedAt = now,
        )
    }

    private fun save(state: CreationResearchArchive) {
        file.baseFile.parentFile?.mkdirs()
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json.encodeToString(state).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (_: Throwable) {
            stream?.let(file::failWrite)
        }
    }

    private fun canonicalTarget(raw: String): String = raw
        .trim()
        .replace(Regex("^作者[:：]\\s*"), "")
        .substringBefore("（")
        .substringBefore("(")
        .trim('《', '》', '“', '”', '"', '\'', ' ', '：', ':')
        .take(40)

    private fun workFromSourceTitle(title: String): String? = Regex("^\\[([^]]{2,40})]\\s*")
        .find(title.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun key(value: String): String = value
        .lowercase()
        .replace(Regex("[《》“”\\\"'\\s·._—-]"), "")

    private companion object {
        const val MAX_ARCHIVE_ENTRIES = 32
        const val MAX_SOURCES_PER_ENTRY = 18
        const val MAX_QUERIES_PER_ENTRY = 8
        const val MAX_TITLE_CHARS = 240
        const val MAX_URL_CHARS = 900
        const val MAX_SNIPPET_CHARS = 650
        const val MAX_DETAIL_CHARS = 1_600
    }
}
