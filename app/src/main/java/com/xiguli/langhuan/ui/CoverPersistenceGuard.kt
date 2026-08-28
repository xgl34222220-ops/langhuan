package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.domain.StorySnapshot
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val CoverGuardJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * 封面文件始终使用 files/covers/<novelId>.png 的稳定路径。
 * 如果其它旧 ViewModel 用过期 snapshot 覆盖了 coverPath，这里会从真实封面文件自动修复元数据，
 * 避免“刚生成能看到，退出再进又变默认封面”。
 */
class CoverPersistenceGuardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LanghuanDatabase.get(application)
    private val storyDao = db.storyStateDao()
    private val coversDir = File(application.filesDir, "covers")

    init {
        viewModelScope.launch {
            storyDao.observeAll().collectLatest { rows ->
                rows.forEach { row ->
                    val snapshot = runCatching {
                        CoverGuardJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
                    }.getOrNull() ?: return@forEach
                    val file = File(coversDir, "${snapshot.novel.id}.png")
                    if (!file.isFile || file.length() <= 0L) return@forEach
                    if (snapshot.novel.coverPath == file.absolutePath) return@forEach

                    val repaired = snapshot.copy(
                        novel = snapshot.novel.copy(coverPath = file.absolutePath),
                    )
                    storyDao.upsert(
                        row.copy(
                            snapshotJson = CoverGuardJson.encodeToString(StorySnapshot.serializer(), repaired),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
    }
}
