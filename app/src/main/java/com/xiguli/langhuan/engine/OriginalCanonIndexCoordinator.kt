package com.xiguli.langhuan.engine

import android.content.Context
import android.os.FileObserver
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.domain.StorySnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 把“从原著抽设定”生成的独立原著库同步成章节有界的 RAG 索引。
 *
 * 关键原则：原著库只通过带 chapterNumber 的 ORIGINAL_* chunk 进入检索，
 * 绝不把未来章节实体直接灌进普通 Bible / Character / Timeline。
 */
object OriginalCanonIndexCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Volatile private var started = false
    private var observer: FileObserver? = null

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            val app = context.applicationContext
            val dir = File(app.filesDir, "original_canon").apply { mkdirs() }
            scope.launch { syncAll(app, dir) }
            observer = object : FileObserver(
                dir.absolutePath,
                CLOSE_WRITE or MOVED_TO or CREATE or DELETE or MOVED_FROM,
            ) {
                override fun onEvent(event: Int, path: String?) {
                    val name = path ?: return
                    if (!name.endsWith(".json", true)) return
                    scope.launch {
                        val file = File(dir, name)
                        if (file.exists()) syncFile(app, file)
                        else LanghuanDatabase.get(app).memoryChunkDao()
                            .deleteOriginalCanonForNovel(name.removeSuffix(".json"))
                    }
                }
            }.also(FileObserver::startWatching)
        }
    }

    private suspend fun syncAll(context: Context, dir: File) {
        dir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            .orEmpty()
            .forEach { syncFile(context, it) }
    }

    private suspend fun syncFile(context: Context, file: File) {
        val archive = runCatching {
            json.decodeFromString(OriginalCanonIndexArchive.serializer(), file.readText())
        }.getOrNull() ?: return
        if (archive.novelId.isBlank()) return

        val db = LanghuanDatabase.get(context)
        val memoryDao = db.memoryChunkDao()
        memoryDao.deleteOriginalCanonForNovel(archive.novelId)
        // 清掉 PR #28 第一版“整本注入”产生的普通 BIBLE / CHARACTER / TIMELINE 记忆，
        // 否则这些无章节边界的旧 chunk 仍可能绕过 ORIGINAL_* 硬过滤。
        memoryDao.deleteLegacyOriginalCanonStructured(archive.novelId)

        if (archive.appliedAt <= 0L || archive.digests.isEmpty()) return

        val now = archive.updatedAt.takeIf { it > 0L } ?: file.lastModified().takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val chunks = buildList {
            archive.digests.forEach { digest ->
                val base = "${archive.novelId}:${digest.chapterNumber}:${digest.partIndex}"
                if (digest.summary.isNotBlank()) {
                    add(
                        MemoryChunkEntity(
                            id = "original:$base:summary",
                            novelId = archive.novelId,
                            sourceType = "ORIGINAL_SUMMARY",
                            sourceId = "$base:summary",
                            chapterNumber = digest.chapterNumber,
                            text = "第${digest.chapterNumber}章《${digest.chapterTitle}》片段${digest.partIndex}/${digest.partCount}摘要：${digest.summary.take(1200)}",
                            updatedAt = now,
                        )
                    )
                }
                digest.entities.forEachIndexed { index, item ->
                    add(
                        MemoryChunkEntity(
                            id = "original:$base:entity:$index",
                            novelId = archive.novelId,
                            sourceType = "ORIGINAL_ENTITY",
                            sourceId = "$base:entity:$index",
                            chapterNumber = digest.chapterNumber,
                            text = buildString {
                                append("类型：").append(item.type).append('\n')
                                append("实体：").append(item.name)
                                if (item.aliases.isNotEmpty()) append("（别名：").append(item.aliases.joinToString("、")).append('）')
                                append('\n').append("原著事实：").append(item.description.take(1000))
                                if (item.evidence.isNotBlank()) append('\n').append("证据：").append(item.evidence.take(300))
                            },
                            updatedAt = now,
                        )
                    )
                }
                digest.events.forEachIndexed { index, item ->
                    add(
                        MemoryChunkEntity(
                            id = "original:$base:event:$index",
                            novelId = archive.novelId,
                            sourceType = "ORIGINAL_EVENT",
                            sourceId = "$base:event:$index",
                            chapterNumber = digest.chapterNumber,
                            text = buildString {
                                append("事件：").append(item.summary.take(1000))
                                if (item.storyTime.isNotBlank()) append('\n').append("时间：").append(item.storyTime)
                                if (item.location.isNotBlank()) append('\n').append("地点：").append(item.location)
                                if (item.participants.isNotEmpty()) append('\n').append("参与者：").append(item.participants.joinToString("、"))
                                if (item.consequences.isNotEmpty()) append('\n').append("后果：").append(item.consequences.joinToString("；").take(700))
                                if (item.evidence.isNotBlank()) append('\n').append("证据：").append(item.evidence.take(300))
                            },
                            updatedAt = now,
                        )
                    )
                }
                digest.knowledge.forEachIndexed { index, item ->
                    add(
                        MemoryChunkEntity(
                            id = "original:$base:knowledge:$index",
                            novelId = archive.novelId,
                            sourceType = "ORIGINAL_KNOWLEDGE",
                            sourceId = "$base:knowledge:$index",
                            chapterNumber = digest.chapterNumber,
                            text = buildString {
                                append("角色：").append(item.character).append('\n')
                                append("事实：").append(item.fact.take(900))
                                if (item.evidence.isNotBlank()) append('\n').append("证据：").append(item.evidence.take(300))
                            },
                            updatedAt = now,
                        )
                    )
                }
                digest.relations.forEachIndexed { index, item ->
                    add(
                        MemoryChunkEntity(
                            id = "original:$base:relation:$index",
                            novelId = archive.novelId,
                            sourceType = "ORIGINAL_RELATION",
                            sourceId = "$base:relation:$index",
                            chapterNumber = digest.chapterNumber,
                            text = buildString {
                                append("起点：").append(item.from).append('\n')
                                append("终点：").append(item.to).append('\n')
                                append("关系：").append(item.label)
                                if (item.value.isNotBlank()) append(" = ").append(item.value)
                                if (item.evidence.isNotBlank()) append('\n').append("证据：").append(item.evidence.take(300))
                            },
                            updatedAt = now,
                        )
                    )
                }
            }
        }
        chunks.chunked(350).forEach(memoryDao::upsertAll)
        normalizeLegacySnapshot(context, archive.novelId)
    }

    /**
     * PR #28 第一版“写入 Canon”曾把全书抽取结果直接塞进 StorySnapshot。
     * 启用章节有界 RAG 后把这些自动注入项清掉，避免第 300 章看到第 800 章的设定。
     */
    private suspend fun normalizeLegacySnapshot(context: Context, novelId: String) {
        val db = LanghuanDatabase.get(context)
        val dao = db.storyStateDao()
        val row = dao.get(novelId) ?: return
        val snapshot = runCatching {
            json.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
        }.getOrNull() ?: return
        val cutoff = snapshot.novel.currentChapter.coerceAtLeast(1)
        val cleaned = snapshot.copy(
            bible = snapshot.bible.filterNot { it.id.startsWith(LEGACY_PREFIX) },
            characters = snapshot.characters.filterNot { it.id.startsWith(LEGACY_PREFIX) },
            recentTimeline = snapshot.recentTimeline.filterNot { it.id.startsWith(LEGACY_PREFIX) },
            recentSummaries = snapshot.recentSummaries.filter { chapterTag(it)?.let { chapter -> chapter <= cutoff } ?: true },
            longTermSummary = snapshot.longTermSummary.lineSequence()
                .filter { line -> chapterTag(line)?.let { chapter -> chapter <= cutoff } ?: true }
                .joinToString("\n")
                .trim(),
        )
        if (cleaned != snapshot) {
            dao.upsert(
                row.copy(
                    snapshotJson = json.encodeToString(StorySnapshot.serializer(), cleaned),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun chapterTag(value: String): Int? = Regex("第\\s*(\\d+)\\s*章")
        .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private const val LEGACY_PREFIX = "original-canon:"
}

@Serializable
private data class OriginalCanonIndexArchive(
    val novelId: String = "",
    val appliedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val digests: List<OriginalCanonIndexDigest> = emptyList(),
)

@Serializable
private data class OriginalCanonIndexDigest(
    val chapterNumber: Int = 0,
    val chapterTitle: String = "",
    val partIndex: Int = 1,
    val partCount: Int = 1,
    val summary: String = "",
    val entities: List<OriginalCanonIndexEntity> = emptyList(),
    val events: List<OriginalCanonIndexEvent> = emptyList(),
    val knowledge: List<OriginalCanonIndexKnowledge> = emptyList(),
    val relations: List<OriginalCanonIndexRelation> = emptyList(),
)

@Serializable
private data class OriginalCanonIndexEntity(
    val type: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val evidence: String = "",
)

@Serializable
private data class OriginalCanonIndexEvent(
    val storyTime: String = "",
    val location: String = "",
    val participants: List<String> = emptyList(),
    val summary: String = "",
    val consequences: List<String> = emptyList(),
    val evidence: String = "",
)

@Serializable
private data class OriginalCanonIndexKnowledge(
    val character: String = "",
    val fact: String = "",
    val evidence: String = "",
)

@Serializable
private data class OriginalCanonIndexRelation(
    val from: String = "",
    val to: String = "",
    val label: String = "",
    val value: String = "",
    val evidence: String = "",
)
