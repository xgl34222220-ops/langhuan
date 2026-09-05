package com.xiguli.langhuan.ui.shell

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryExchange
import com.xiguli.langhuan.data.StoryProjectManager
import kotlinx.coroutines.launch

fun StudioViewModel.exportProjectBackup(uri: Uri) {
    val app = getApplication<Application>()
    val current = state.value
    viewModelScope.launch {
        runCatching {
            val drafts = StoryProjectManager(app).chapterDrafts(current.snapshot.novel.id)
            val artifact = StoryExchange.exportProject(current.snapshot, drafts)
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(artifact.bytes) }
                ?: error("无法写入目标文件")
        }.onSuccess {
            Toast.makeText(app, "项目备份完成（不含 API Key）", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(app, error.message ?: "项目备份失败", Toast.LENGTH_LONG).show()
        }
    }
}
