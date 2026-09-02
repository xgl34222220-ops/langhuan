package com.xiguli.langhuan.engine

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ProjectConversationOrigin { CREATION, PROJECT }

@Serializable
data class ProjectConversationMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val origin: ProjectConversationOrigin = ProjectConversationOrigin.PROJECT,
    val atMillis: Long = System.currentTimeMillis(),
)

@Serializable
private data class ProjectConversationLog(
    val schemaVersion: Int = 1,
    val novelId: String,
    val messages: List<ProjectConversationMessage> = emptyList(),
)

/**
 * Persistent, project-scoped author/AI conversation.
 *
 * This is operational authoring context only. It never enters Canon by itself and is kept
 * separate from StorySnapshot/RAG facts. A project may therefore keep the entire creative
 * discussion without silently turning brainstormed suggestions into novel truth.
 */
class ProjectConversationStore(context: Context) {
    private val root = File(context.filesDir, "project_conversations").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(novelId: String): List<ProjectConversationMessage> = runCatching {
        val file = AtomicFile(fileFor(novelId))
        if (!file.baseFile.exists()) return@runCatching emptyList()
        val bytes = file.openRead().use { it.readBytes() }
        if (bytes.isEmpty()) return@runCatching emptyList()
        json.decodeFromString<ProjectConversationLog>(bytes.toString(Charsets.UTF_8))
            .messages
            .map { it.copy(text = sanitizeProjectConversationText(it.text)) }
            .filter { it.text.isNotBlank() }
            .takeLast(MAX_PROJECT_CONVERSATION_MESSAGES)
    }.getOrElse { emptyList() }

    fun replace(novelId: String, messages: List<ProjectConversationMessage>) {
        write(
            novelId,
            messages
                .map { it.copy(text = sanitizeProjectConversationText(it.text)) }
                .filter { it.text.isNotBlank() }
                .takeLast(MAX_PROJECT_CONVERSATION_MESSAGES),
        )
    }

    fun append(novelId: String, message: ProjectConversationMessage) {
        val clean = message.copy(text = sanitizeProjectConversationText(message.text))
        if (clean.text.isBlank()) return
        write(novelId, (load(novelId) + clean).takeLast(MAX_PROJECT_CONVERSATION_MESSAGES))
    }

    fun handoffFromCreation(novelId: String, messages: List<Pair<String, String>>) {
        val handoff = projectConversationHandoff(messages)
        if (handoff.isNotEmpty()) replace(novelId, handoff)
    }

    private fun write(novelId: String, messages: List<ProjectConversationMessage>) {
        val atomic = AtomicFile(fileFor(novelId))
        val bytes = json.encodeToString(ProjectConversationLog(novelId = novelId, messages = messages))
            .toByteArray(Charsets.UTF_8)
        val output = runCatching { atomic.startWrite() }.getOrNull() ?: return
        try {
            output.write(bytes)
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (_: Throwable) {
            atomic.failWrite(output)
        }
    }

    private fun fileFor(novelId: String): File {
        val safe = novelId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "story" }
        return File(root, "$safe.json")
    }
}

internal fun projectConversationHandoff(messages: List<Pair<String, String>>): List<ProjectConversationMessage> =
    messages.mapNotNull { (role, text) ->
        val clean = sanitizeProjectConversationText(text)
        if (clean.isBlank()) null
        else ProjectConversationMessage(
            role = if (role == "assistant") "assistant" else "user",
            text = clean,
            origin = ProjectConversationOrigin.CREATION,
        )
    }.takeLast(MAX_PROJECT_CONVERSATION_MESSAGES)

internal fun sanitizeProjectConversationText(text: String): String = text
    .substringBefore("\n\n【琅嬛联网检索资料（隐藏上下文）】")
    .trim()

private const val MAX_PROJECT_CONVERSATION_MESSAGES = 120
