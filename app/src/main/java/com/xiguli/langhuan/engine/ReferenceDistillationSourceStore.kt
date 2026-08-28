package com.xiguli.langhuan.engine

import android.content.Context
import java.io.File

/**
 * Keeps only the app-private retry source path/name for a WorkManager task. The novel bytes remain in
 * the private inbox file; no prose is copied into preferences or WorkInfo progress/output data.
 *
 * WorkManager keeps terminal FAILED/SUCCEEDED records for a while. We therefore also persist a small
 * dismissed-id set so users can remove stale task cards immediately without waiting for WorkManager
 * to prune its database.
 */
class ReferenceDistillationSourceStore(context: Context) {
    private val preferences = context.getSharedPreferences("reference_distillation_sources", Context.MODE_PRIVATE)

    fun save(taskId: String, source: File, displayName: String) {
        if (taskId.isBlank()) return
        preferences.edit()
            .putString("$taskId:path", source.absolutePath)
            .putString("$taskId:name", displayName)
            .apply()
    }

    fun load(taskId: String): ReferenceDistillationSource? {
        val path = preferences.getString("$taskId:path", null).orEmpty()
        if (path.isBlank()) return null
        return ReferenceDistillationSource(
            path = path,
            displayName = preferences.getString("$taskId:name", null).orEmpty(),
        )
    }

    fun remove(taskId: String) {
        preferences.edit()
            .remove("$taskId:path")
            .remove("$taskId:name")
            .apply()
    }

    fun dismiss(taskId: String) {
        if (taskId.isBlank()) return
        val next = dismissedIds().toMutableSet().apply { add(taskId) }
        preferences.edit().putStringSet(KEY_DISMISSED, next).apply()
    }

    fun isDismissed(taskId: String): Boolean = taskId.isNotBlank() && taskId in dismissedIds()

    /**
     * Permanently remove a failed task's private retry source and hide its WorkManager history card.
     * The checkpoint itself is cleared by the caller because it is keyed by content fingerprint.
     */
    fun deleteFailedTaskSource(taskId: String) {
        load(taskId)?.path?.takeIf(String::isNotBlank)?.let { path ->
            runCatching { File(path).delete() }
        }
        remove(taskId)
        dismiss(taskId)
    }

    private fun dismissedIds(): Set<String> = preferences.getStringSet(KEY_DISMISSED, emptySet())?.toSet().orEmpty()

    private companion object {
        const val KEY_DISMISSED = "dismissed_task_ids"
    }
}

data class ReferenceDistillationSource(
    val path: String,
    val displayName: String,
)
