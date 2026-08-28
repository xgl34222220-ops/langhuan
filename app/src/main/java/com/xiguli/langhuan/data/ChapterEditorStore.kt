package com.xiguli.langhuan.data

import android.content.Context
import androidx.room.withTransaction
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.serialization.json.Json

private val EditorStoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * 正文编辑器专用持久化层。
 *
 * autosave 只更新当前稿，不创建历史版本；checkpoint/restore 才写 chapter_versions，
 * 避免输入过程每隔一秒制造一个永久版本。
 */
class ChapterEditorStore(context: Context) {
    private val db = LanghuanDatabase.get(context)
    private val storyDao = db.storyStateDao()
    private val chapterStateDao = db.chapterStateDao()
    private val versionDao = db.chapterVersionDao()
    private val memoryDao = db.memoryChunkDao()
    private val projects = StoryProjectManager(context)

    suspend fun load(novelId: String, chapterNumber: Int? = null): PersistedStory {
        val loaded = projects.loadStory(novelId) ?: error("找不到这本小说")
        val number = chapterNumber ?: loaded.snapshot.novel.currentChapter
        return projects.selectChapter(novelId, number) ?: loaded
    }

    suspend fun chapters(novelId: String): List<ChapterDraft> = projects.chapterDrafts(novelId)

    suspend fun versions(novelId: String, chapterNumber: Int): List<StoredChapterVersion> =
        versionDao.forChapter(novelId, chapterNumber).map {
            StoredChapterVersion(
                id = it.id,
                chapterNumber = it.chapterNumber,
                version = it.version,
                title = it.title,
                content = it.content,
                summary = it.summary,
                createdAt = it.createdAt,
            )
        }

    suspend fun autosave(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
        val old = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraft() ?: draft
        val delta = draft.content.length - old.content.length
        val updated = snapshot.copy(
            novel = snapshot.novel.copy(
                currentWords = (snapshot.novel.currentWords + delta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            )
        )
        persistCurrent(updated, draft, System.currentTimeMillis())
        return PersistedStory(updated, draft)
    }

    suspend fun checkpoint(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
        val old = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraft() ?: draft
        val latest = versionDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version
        val versioned = draft.copy(version = maxOf(latest, draft.version) + 1)
        val delta = versioned.content.length - old.content.length
        val updated = snapshot.copy(
            novel = snapshot.novel.copy(
                currentWords = (snapshot.novel.currentWords + delta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            )
        )
        val now = System.currentTimeMillis()
        db.withTransaction {
            persistCurrent(updated, versioned, now)
            versionDao.upsert(
                ChapterVersionEntity(
                    id = "${versioned.id}:v${versioned.version}",
                    novelId = versioned.novelId,
                    chapterNumber = versioned.chapterNumber,
                    version = versioned.version,
                    title = versioned.title,
                    content = versioned.content,
                    summary = versioned.summary,
                    createdAt = now,
                )
            )
        }
        return PersistedStory(updated, versioned)
    }

    /** 恢复旧版本时生成一个新的版本号，历史记录永不原地覆盖。 */
    suspend fun restore(snapshot: StorySnapshot, draft: ChapterDraft, source: StoredChapterVersion): PersistedStory {
        require(source.chapterNumber == draft.chapterNumber) { "版本不属于当前章节" }
        val latest = versionDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version
        val restored = draft.copy(
            title = source.title,
            content = source.content,
            summary = source.summary,
            version = maxOf(latest, draft.version) + 1,
        )
        val old = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraft() ?: draft
        val delta = restored.content.length - old.content.length
        val updated = snapshot.copy(
            novel = snapshot.novel.copy(
                currentWords = (snapshot.novel.currentWords + delta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            )
        )
        val now = System.currentTimeMillis()
        db.withTransaction {
            persistCurrent(updated, restored, now)
            versionDao.upsert(
                ChapterVersionEntity(
                    id = "${restored.id}:v${restored.version}",
                    novelId = restored.novelId,
                    chapterNumber = restored.chapterNumber,
                    version = restored.version,
                    title = restored.title,
                    content = restored.content,
                    summary = restored.summary,
                    createdAt = now,
                )
            )
        }
        return PersistedStory(updated, restored)
    }

    private suspend fun persistCurrent(snapshot: StorySnapshot, draft: ChapterDraft, now: Long) {
        storyDao.upsert(
            StoryStateEntity(
                novelId = snapshot.novel.id,
                snapshotJson = EditorStoreJson.encodeToString(StorySnapshot.serializer(), snapshot),
                draftJson = EditorStoreJson.encodeToString(ChapterDraft.serializer(), draft),
                updatedAt = now,
            )
        )
        chapterStateDao.upsert(
            ChapterStateEntity(
                id = draft.id,
                novelId = draft.novelId,
                chapterNumber = draft.chapterNumber,
                draftJson = EditorStoreJson.encodeToString(ChapterDraft.serializer(), draft),
                updatedAt = now,
            )
        )

        // RAG 只保留这一章当前正文，防止旧版本正文同时参与检索造成剧情污染。
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM memory_chunks WHERE novelId = ? AND sourceType = 'CHAPTER' AND sourceId = ?",
            arrayOf(draft.novelId, draft.id),
        )
        memoryDao.upsert(
            MemoryChunkEntity(
                id = "chapter:${draft.id}:live",
                novelId = draft.novelId,
                sourceType = "CHAPTER",
                sourceId = draft.id,
                chapterNumber = draft.chapterNumber,
                text = buildString {
                    append("第${draft.chapterNumber}章 ${draft.title}。")
                    if (draft.summary.isNotBlank()) append(draft.summary)
                    if (draft.content.isNotBlank()) append('\n').append(draft.content.take(4_000))
                },
                updatedAt = now,
            )
        )
    }

    private fun ChapterStateEntity.decodeDraft(): ChapterDraft? = runCatching {
        EditorStoreJson.decodeFromString(ChapterDraft.serializer(), draftJson)
    }.getOrNull()
}
