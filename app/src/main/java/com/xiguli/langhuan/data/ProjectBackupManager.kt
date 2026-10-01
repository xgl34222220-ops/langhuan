package com.xiguli.langhuan.data

import android.content.Context
import androidx.room.withTransaction
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.domain.ChapterDraft
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

private val BackupStoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

internal enum class ProjectRestoreStage {
    PREPARED, CHAPTER_STATE_WRITTEN, CHAPTER_VERSION_WRITTEN,
    STRUCTURE_SAVED, CHAPTER_MEMORY_WRITTEN, BEFORE_COMMIT,
}

internal data class ProjectRestoreCheckpoint(val stage: ProjectRestoreStage, val chapterNumber: Int? = null)

class ProjectBackupManager internal constructor(
    context: Context,
    private val db: LanghuanDatabase,
    private val checkpoint: suspend (ProjectRestoreCheckpoint) -> Unit = {},
) {
    constructor(context: Context) : this(context, LanghuanDatabase.get(context))

    private val projects = StoryProjectManager(context, db)
    private val chapterStateDao = db.chapterStateDao()
    private val chapterVersionDao = db.chapterVersionDao()
    private val memoryDao = db.memoryChunkDao()

    /** A committed restore adds a complete project. The caller owns any later active selection. */
    suspend fun restore(backup: StoryProjectBackup): PersistedStory {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val plan = prepareProjectRestore(backup) { caller.ensureActive() }
        reach(ProjectRestoreStage.PREPARED)
        return db.withTransaction {
            val now = System.currentTimeMillis()
            plan.chapters.forEach { draft ->
                chapterStateDao.upsert(
                    ChapterStateEntity(
                        id = draft.id,
                        novelId = plan.snapshot.novel.id,
                        chapterNumber = draft.chapterNumber,
                        draftJson = BackupStoreJson.encodeToString(ChapterDraft.serializer(), draft),
                        updatedAt = now,
                    )
                )
                reach(ProjectRestoreStage.CHAPTER_STATE_WRITTEN, draft.chapterNumber)
                chapterVersionDao.upsert(
                    ChapterVersionEntity(
                        id = "${draft.id}:v${draft.version}",
                        novelId = plan.snapshot.novel.id,
                        chapterNumber = draft.chapterNumber,
                        version = draft.version,
                        title = draft.title,
                        content = draft.content,
                        summary = draft.summary,
                        createdAt = now,
                    )
                )
                reach(ProjectRestoreStage.CHAPTER_VERSION_WRITTEN, draft.chapterNumber)
            }
    
            val persisted = projects.saveStructure(plan.snapshot, plan.currentDraft)
            reach(ProjectRestoreStage.STRUCTURE_SAVED)
            plan.chapters.forEach { draft ->
                memoryDao.upsert(
                    MemoryChunkEntity(
                        id = "chapter:${draft.id}:v${draft.version}",
                        novelId = plan.snapshot.novel.id,
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
                reach(ProjectRestoreStage.CHAPTER_MEMORY_WRITTEN, draft.chapterNumber)
            }
            reach(ProjectRestoreStage.BEFORE_COMMIT)
            persisted
        }
    }

    private suspend fun reach(stage: ProjectRestoreStage, chapterNumber: Int? = null) {
        currentCoroutineContext().ensureActive()
        checkpoint(ProjectRestoreCheckpoint(stage, chapterNumber))
        currentCoroutineContext().ensureActive()
    }
}
