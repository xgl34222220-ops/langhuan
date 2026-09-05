package com.xiguli.langhuan.ui.reference

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.engine.ReferenceDistillationJobs
import com.xiguli.langhuan.engine.ReferenceDistillationSourceStore
import com.xiguli.langhuan.ui.shell.StudioViewModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Copy a user-selected reference novel into app-private storage before enqueueing WorkManager.
 * Persisting the bytes first is important: a SAF Uri permission or the UI process may disappear while
 * the long distillation job is still running.
 */
fun StudioViewModel.enqueueReferenceDistillation(uri: Uri) {
    val app = getApplication<Application>()
    viewModelScope.launch {
        val outcome = runCatching {
            withContext(Dispatchers.IO) {
                val resolver = app.contentResolver
                val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "参考小说.txt" }
                val lower = displayName.lowercase()
                require(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".epub")) {
                    "参考小说蒸馏目前支持 TXT / Markdown / EPUB"
                }
                val inbox = File(app.filesDir, "distillation/inbox").apply { mkdirs() }
                val digest = MessageDigest.getInstance("SHA-256")
                val target = File(inbox, "${System.currentTimeMillis()}-${safeName(displayName)}")
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("无法读取选择的参考小说")
                require(target.length() > 0L) { "参考小说文件为空" }
                val prefix = digest.digest().take(6).joinToString("") { "%02x".format(it) }
                val stable = File(inbox, "$prefix-${safeName(displayName)}")
                if (stable.exists()) stable.delete()
                if (!target.renameTo(stable)) {
                    target.copyTo(stable, overwrite = true)
                    target.delete()
                }
                val taskId = ReferenceDistillationJobs.enqueue(app, stable, displayName)
                ReferenceDistillationSourceStore(app).save(taskId, stable, displayName)
                displayName
            }
        }
        outcome.onSuccess { name ->
            Toast.makeText(app, "《${name.substringBeforeLast('.')}》已加入后台蒸馏，可离开琅嬛继续执行", Toast.LENGTH_LONG).show()
        }.onFailure { error ->
            Toast.makeText(app, error.message ?: "无法开始参考小说蒸馏", Toast.LENGTH_LONG).show()
        }
    }
}

private fun safeName(value: String): String = value
    .replace(Regex("[\\/:*?\"<>|]"), "_")
    .take(120)
    .ifBlank { "reference.txt" }
