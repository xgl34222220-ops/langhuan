package com.xiguli.langhuan.engine

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class WritingSkillUpdateCandidate(
    val skillId: String,
    val currentVersion: String,
    val remoteVersion: String,
    val currentRevision: String,
    val remoteRevision: String,
    val sourceUrl: String,
    val updateUrl: String,
    val changes: List<String>,
    val rawManifest: String,
)

sealed interface WritingSkillUpdateCheck {
    data class UpToDate(val skillId: String) : WritingSkillUpdateCheck
    data class Update(val candidate: WritingSkillUpdateCandidate) : WritingSkillUpdateCheck
    data class Error(val skillId: String, val message: String) : WritingSkillUpdateCheck
}

/**
 * V8 declarative-only updater. It downloads JSON text, never code, scripts, archives or binaries.
 * Applying the result is a separate explicit user-confirmed Store operation.
 */
class WritingSkillUpdateClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(skill: WritingSkillDefinition): WritingSkillUpdateCheck = withContext(Dispatchers.IO) {
        val url = skill.updateUrl.trim()
        if (url.isBlank()) return@withContext WritingSkillUpdateCheck.Error(skill.id, "该 Skill 没有配置 updateUrl")
        if (!url.startsWith("https://")) return@withContext WritingSkillUpdateCheck.Error(skill.id, "只允许通过 HTTPS 检查 Skill 更新")

        runCatching {
            val raw = download(url)
            evaluate(skill, raw)
        }.getOrElse { error ->
            WritingSkillUpdateCheck.Error(skill.id, error.message ?: "检查更新失败")
        }
    }

    internal fun evaluate(skill: WritingSkillDefinition, raw: String): WritingSkillUpdateCheck {
        val manifest = runCatching { json.decodeFromString(UserWritingSkillManifest.serializer(), raw) }
            .getOrElse { return WritingSkillUpdateCheck.Error(skill.id, "远程返回的不是有效 Skill JSON") }
        if (manifest.id.trim() != skill.id) {
            return WritingSkillUpdateCheck.Error(skill.id, "远程 Skill id=${manifest.id} 与当前 ${skill.id} 不一致")
        }
        if (manifest.schemaVersion !in 1..2) {
            return WritingSkillUpdateCheck.Error(skill.id, "远程 schemaVersion=${manifest.schemaVersion} 暂不支持")
        }
        if (manifest.guidance.isBlank() && manifest.taskGuidance.values.all(String::isBlank)) {
            return WritingSkillUpdateCheck.Error(skill.id, "远程 Skill 没有任何 guidance")
        }
        if (manifest.supportedTasks.isEmpty()) {
            return WritingSkillUpdateCheck.Error(skill.id, "远程 Skill 没有 supportedTasks")
        }
        if (skill.builtin && manifest.supportedTasks.any { it !in skill.supportedTasks }) {
            return WritingSkillUpdateCheck.Error(skill.id, "远程内置 Skill 试图扩大可调用任务范围")
        }

        val remoteVersion = manifest.version.trim()
        val remoteRevision = manifest.sourceRevision.trim()
        val changed = remoteVersion != skill.version || remoteRevision != skill.sourceRevision
        if (!changed) return WritingSkillUpdateCheck.UpToDate(skill.id)

        val changes = buildList {
            if (remoteVersion != skill.version) add("版本：${skill.version} → $remoteVersion")
            if (remoteRevision != skill.sourceRevision) {
                val before = skill.sourceRevision.take(10).ifBlank { "未记录" }
                val after = remoteRevision.take(10).ifBlank { "未记录" }
                add("来源 revision：$before → $after")
            }
            if (manifest.description.trim() != skill.description.trim()) add("说明已更新")
            val remoteSupported = manifest.supportedTasks.toSet()
            if (remoteSupported != skill.supportedTasks) add("支持任务范围有调整")
            val remoteDefaults = manifest.defaultTasks.toSet().ifEmpty { remoteSupported }
            if (remoteDefaults != skill.defaultTasks) add("推荐任务绑定有调整（现有个人绑定不会自动重置）")
        }.ifEmpty { listOf("声明式写作规则已更新") }

        return WritingSkillUpdateCheck.Update(
            WritingSkillUpdateCandidate(
                skillId = skill.id,
                currentVersion = skill.version,
                remoteVersion = remoteVersion,
                currentRevision = skill.sourceRevision,
                remoteRevision = remoteRevision,
                sourceUrl = manifest.sourceUrl.trim().ifBlank { skill.sourceUrl },
                updateUrl = skill.updateUrl,
                changes = changes,
                rawManifest = raw,
            )
        )
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json,text/plain;q=0.9")
            setRequestProperty("User-Agent", "LangHuan-Skill-Updater/1")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "更新源返回 HTTP $code" }
            val length = connection.contentLengthLong
            require(length < 0 || length <= MAX_BYTES) { "远程 Skill 超过 ${MAX_BYTES / 1024}KB，已拒绝" }
            connection.inputStream.buffered().use { input ->
                val bytes = input.readNBytes(MAX_BYTES + 1)
                require(bytes.size <= MAX_BYTES) { "远程 Skill 超过 ${MAX_BYTES / 1024}KB，已拒绝" }
                return bytes.toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val MAX_BYTES = 256 * 1024
    }
}
