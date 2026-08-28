package com.xiguli.langhuan.data

import android.content.Context
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StorySnapshot
import java.util.UUID
import kotlinx.serialization.json.Json

private val BackupStoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

class ProjectBackupManager(context: Context) {
    private val projects = StoryProjectManager(context)
    private val db = LanghuanDatabase.get(context)
    private val chapterStateDao = db.chapterStateDao()
    private val chapterVersionDao = db.chapterVersionDao()
    private val memoryDao = db.memoryChunkDao()

    suspend fun restore(backup: StoryProjectBackup): PersistedStory {
        require(backup.formatVersion in 1..StoryProjectBackup.CURRENT_VERSION) { "备份版本过新，当前琅嬛暂不支持" }
        require(backup.chapters.isNotEmpty()) { "项目备份中没有章节" }

        val old = backup.snapshot
        val newNovelId = UUID.randomUUID().toString()
        val sourceOutline = (if (old.outline.isEmpty()) old.activeOutline else old.outline).distinctBy { it.id }
        val outlineIdMap = sourceOutline.associate { it.id to UUID.randomUUID().toString() }
        val newOutline = sourceOutline.map { node ->
            node.copy(
                id = outlineIdMap.getValue(node.id),
                novelId = newNovelId,
                parentId = node.parentId?.let(outlineIdMap::get),
            )
        }

        val characterIdMap = old.characters.associate { it.id to UUID.randomUUID().toString() }
        val foreshadowIdMap = old.relevantForeshadowing.associate { it.id to UUID.randomUUID().toString() }
        val chapterNumber = old.novel.currentChapter.coerceAtLeast(1)
        val remappedSnapshot = old.copy(
            novel = old.novel.copy(
                id = newNovelId,
                currentChapter = chapterNumber,
                currentWords = backup.chapters.sumOf { it.content.length },
            ),
            activeOutline = activeChain(newOutline, chapterNumber),
            outline = newOutline,
            bible = old.bible.map { it.copy(id = UUID.randomUUID().toString(), novelId = newNovelId) },
            characters = old.characters.map { character ->
                character.copy(id = characterIdMap.getValue(character.id), novelId = newNovelId)
            },
            recentTimeline = old.recentTimeline.map { it.copy(id = UUID.randomUUID().toString(), novelId = newNovelId) },
            relevantForeshadowing = old.relevantForeshadowing.map { item ->
                item.copy(id = foreshadowIdMap.getValue(item.id), novelId = newNovelId)
            },
            factHistory = old.factHistory.map { fact ->
                fact.copy(id = UUID.randomUUID().toString(), novelId = newNovelId)
            },
            longForm = old.longForm.copy(
                arcs = old.longForm.arcs.map { arc ->
                    arc.copy(id = "$newNovelId:arc:${arc.startChapter}")
                },
                characterGrowth = old.longForm.characterGrowth.mapNotNull { growth ->
                    val mapped = characterIdMap[growth.characterId]
                        ?: old.characters.firstOrNull { it.name == growth.name }?.id?.let(characterIdMap::get)
                    mapped?.let { growth.copy(characterId = it) }
                },
            ),
        )

        val restoredDrafts = backup.chapters.sortedBy { it.chapterNumber }.map { draft ->
            draft.copy(
                id = "draft-$newNovelId-${draft.chapterNumber}-${UUID.randomUUID()}",
                novelId = newNovelId,
                version = draft.version.coerceAtLeast(1),
            )
        }
        val currentDraft = restoredDrafts.firstOrNull { it.chapterNumber == chapterNumber } ?: restoredDrafts.first()
        val now = System.currentTimeMillis()

        restoredDrafts.forEach { draft ->
            chapterStateDao.upsert(
                ChapterStateEntity(
                    id = draft.id,
                    novelId = newNovelId,
                    chapterNumber = draft.chapterNumber,
                    draftJson = BackupStoreJson.encodeToString(ChapterDraft.serializer(), draft),
                    updatedAt = now,
                )
            )
            chapterVersionDao.upsert(
                ChapterVersionEntity(
                    id = "${draft.id}:v${draft.version}",
                    novelId = newNovelId,
                    chapterNumber = draft.chapterNumber,
                    version = draft.version,
                    title = draft.title,
                    content = draft.content,
                    summary = draft.summary,
                    createdAt = now,
                )
            )
        }

        val persisted = projects.saveStructure(remappedSnapshot, currentDraft)
        restoredDrafts.forEach { draft ->
            memoryDao.upsert(
                MemoryChunkEntity(
                    id = "chapter:${draft.id}:v${draft.version}",
                    novelId = newNovelId,
                    sourceType = "CHAPTER",
                    sourceId = draft.id,
                    chapterNumber = draft.chapterNumber,
                    text = buildString {
                        append("第${draft.chapterNumber}章 ${draft.title}。")
                        if (draft.summary.isNotBlank()) append(draft.summary)
                        if (draft.content.isNotBlank()) append("\n").append(draft.content.take(4_000))
                    },
                    updatedAt = now,
                )
            )
        }
        projects.setActiveStoryId(newNovelId)
        return persisted
    }

    private fun activeChain(nodes: List<OutlineNode>, chapterNumber: Int): List<OutlineNode> {
        val chapter = nodes.firstOrNull { it.level == OutlineLevel.CHAPTER && it.order == chapterNumber }
            ?: nodes.filter { it.level == OutlineLevel.CHAPTER }.minByOrNull { it.order }
            ?: return emptyList()
        val volume = nodes.firstOrNull { it.id == chapter.parentId }
        val master = volume?.parentId?.let { id -> nodes.firstOrNull { it.id == id } }
            ?: nodes.firstOrNull { it.level == OutlineLevel.MASTER }
        return listOfNotNull(master, volume, chapter)
    }
}
