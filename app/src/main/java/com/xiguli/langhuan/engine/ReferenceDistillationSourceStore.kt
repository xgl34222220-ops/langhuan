package com.xiguli.langhuan.engine

import android.content.Context
import java.io.File

/**
 * Keeps only the app-private retry source path/name for a WorkManager task. The novel bytes remain in
 * the private inbox file; no prose is copied into preferences or WorkInfo progress/output data.
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
}

data class ReferenceDistillationSource(
    val path: String,
    val displayName: String,
)
