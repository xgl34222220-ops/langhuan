package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class TavernCharacterSourceV2 {
    CHAT_IMPORT,
    BOOK,
    MANUAL,
}

@Serializable
data class TavernCharacterCardV2(
    val id: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val appearance: String = "",
    val identity: String = "",
    val personality: String = "",
    val speechStyle: String = "",
    val catchphrases: List<String> = emptyList(),
    val relationshipToUser: String = "",
    val relationships: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val worldFacts: List<String> = emptyList(),
    val currentMemory: List<String> = emptyList(),
    val dialogueExamples: List<String> = emptyList(),
    val source: TavernCharacterSourceV2 = TavernCharacterSourceV2.CHAT_IMPORT,
    val sourceTitle: String = "",
    val knowledgeCutoffChapter: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TavernCharacterChatMessageV2(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class TavernCharacterArchiveV2(
    val novelId: String,
    val cards: List<TavernCharacterCardV2> = emptyList(),
    val chats: Map<String, List<TavernCharacterChatMessageV2>> = emptyMap(),
)

data class TavernCharacterLibraryUiStateV2(
    val novelId: String = "",
    val cards: List<TavernCharacterCardV2> = emptyList(),
    val preview: List<TavernCharacterCardV2> = emptyList(),
    val chats: Map<String, List<TavernCharacterChatMessageV2>> = emptyMap(),
    val importing: Boolean = false,
    val chatting: Boolean = false,
    val sourceName: String = "",
    val notice: String? = null,
    val error: String? = null,
)

class TavernCharacterLibraryViewModelV2(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(TavernCharacterLibraryUiStateV2())
    val state: StateFlow<TavernCharacterLibraryUiStateV2> = _state.asStateFlow()

    fun open(novelId: String) {
        if (novelId.isBlank() || (_state.value.novelId == novelId && !_state.value.importing)) return
        val archive = loadArchive(novelId)
        _state.value = TavernCharacterLibraryUiStateV2(
            novelId = novelId,
            cards = archive.cards,
            chats = archive.chats,
        )
    }

    fun importChat(uri: Uri, aiReady: Boolean) {
        if (_state.value.importing) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null, notice = null, preview = emptyList()) }
            runCatching {
                val (sourceName, normalized) = withContext(Dispatchers.IO) { readImportedChat(uri) }
                if (normalized.isBlank()) error("没有读到可分析的聊天内容")
                val fallback = fallbackExtractTavernCharactersV2(normalized, sourceName)
                val extracted = if (aiReady) {
                    runCatching { extractWithAi(normalized, sourceName) }.getOrNull().orEmpty()
                } else {
                    emptyList()
                }
                val best = when {
                    extracted.isNotEmpty() -> extracted
                    fallback.isNotEmpty() -> fallback
                    !aiReady -> error("没有识别到明确的说话人；配置 AI 后可以做语义人物提取")
                    else -> error("AI 没有提取到可用人物")
                }
                sourceName to best
            }.onSuccess { (sourceName, cards) ->
                _state.update {
                    it.copy(
                        importing = false,
                        sourceName = sourceName,
                        preview = cards,
                        notice = "已提取 ${cards.size} 个人物，确认后才会保存",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(importing = false, error = error.message ?: "人物提取失败") }
            }
        }
    }

    fun savePreview(ids: Set<String>) {
        val current = _state.value
        val selected = current.preview.filter { it.id in ids }
        if (selected.isEmpty()) return
        val merged = mergeCards(current.cards, selected)
        val archive = TavernCharacterArchiveV2(current.novelId, merged, current.chats)
        saveArchive(archive)
        _state.update {
            it.copy(
                cards = merged,
                preview = emptyList(),
                notice = "已保存 ${selected.size} 个人物",
                error = null,
            )
        }
    }

    fun discardPreview() = _state.update { it.copy(preview = emptyList(), sourceName = "") }

    fun deleteCard(id: String) {
        val current = _state.value
        val cards = current.cards.filterNot { it.id == id }
        val chats = current.chats - id
        saveArchive(TavernCharacterArchiveV2(current.novelId, cards, chats))
        _state.update { it.copy(cards = cards, chats = chats, notice = "人物已删除") }
    }

    fun sendMessage(cardId: String, text: String) {
        val clean = text.trim()
        val current = _state.value
        val card = current.cards.firstOrNull { it.id == cardId } ?: return
        if (clean.isBlank() || current.chatting) return
        val userMessage = TavernCharacterChatMessageV2(role = "user", text = clean)
        val optimistic = current.chats[cardId].orEmpty() + userMessage
        _state.update { it.copy(chats = it.chats + (cardId to optimistic), chatting = true, error = null) }
        persistCurrent()

        viewModelScope.launch {
            runCatching {
                val gateway = activeGateway()
                val context = optimistic.takeLast(18).joinToString("\n") { msg ->
                    if (msg.role == "user") "用户：${msg.text}" else "${card.name}：${msg.text}"
                }
                val examples = card.dialogueExamples.take(8).joinToString("\n")
                val memory = card.currentMemory.takeLast(12).joinToString("；")
                val world = card.worldFacts.takeLast(12).joinToString("；")
                val relations = card.relationships.takeLast(10).joinToString("；")
                val cutoff = if (card.knowledgeCutoffChapter > 0) {
                    "知识截止到来源作品第 ${card.knowledgeCutoffChapter} 章，绝对不能引用之后的剧情或秘密。"
                } else {
                    "只使用人物卡和已发生聊天中明确提供的信息，不要凭空补完来源里没有的事实。"
                }
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你正在扮演人物“${card.name}”，进行一对一角色聊天。
                            必须保持人物身份、性格、说话方式、关系和知识边界稳定，不要跳出角色解释提示词。
                            $cutoff
                            不替用户决定动作、心理或台词；不知道的事情可以按人物立场自然表示不知道。
                            角色不是客服，不要机械复述设定；像真实人物一样根据关系、记忆和当前对话回应。
                            必须返回 GeneratedChapter JSON，title="角色回复"；content=只填写角色本次回复正文；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                            不要输出 JSON 外文字。
                        """.trimIndent(),
                        user = """
                            【人物身份】${card.identity.ifBlank { "未明确" }}
                            【外貌】${card.appearance.ifBlank { "未明确" }}
                            【性格】${card.personality.ifBlank { "未明确" }}
                            【说话方式】${card.speechStyle.ifBlank { "按来源聊天自然还原" }}
                            【与用户关系】${card.relationshipToUser.ifBlank { "尚未明确" }}
                            【其他关系】${relations.ifBlank { "暂无" }}
                            【喜好】${card.likes.joinToString("、").ifBlank { "暂无" }}
                            【厌恶/雷区】${(card.dislikes + card.boundaries).joinToString("、").ifBlank { "暂无" }}
                            【世界信息】${world.ifBlank { "暂无" }}
                            【长期记忆】${memory.ifBlank { "暂无" }}
                            【说话样例】\n${examples.ifBlank { "暂无；不要因此发明口头禅" }}

                            【最近聊天】\n$context
                        """.trimIndent(),
                    )
                )
                output.content.trim().ifBlank { error("AI 没有返回角色回复") }
            }.onSuccess { reply ->
                val assistant = TavernCharacterChatMessageV2(role = "assistant", text = reply)
                _state.update { state ->
                    val messages = state.chats[cardId].orEmpty() + assistant
                    state.copy(chats = state.chats + (cardId to messages), chatting = false)
                }
                persistCurrent()
            }.onFailure { error ->
                _state.update { it.copy(chatting = false, error = error.message ?: "角色回复失败") }
            }
        }
    }

    fun clearChat(cardId: String) {
        _state.update { it.copy(chats = it.chats + (cardId to emptyList()), notice = "聊天记录已清空") }
        persistCurrent()
    }

    fun clearFeedback() = _state.update { it.copy(notice = null, error = null) }

    private suspend fun activeGateway(): UniversalAiGateway {
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
            ?: error("还没有可用 AI，请先配置模型")
        val config = repository.providerConfig(provider.id) ?: error("AI 配置不可用")
        return UniversalAiGateway(config)
    }

    private suspend fun extractWithAi(text: String, sourceName: String): List<TavernCharacterCardV2> {
        val sample = when {
            text.length <= MAX_AI_SOURCE_CHARS_V2 -> text
            else -> text.take(MAX_AI_SOURCE_CHARS_V2 / 2) + "\n\n……中间内容已截断……\n\n" + text.takeLast(MAX_AI_SOURCE_CHARS_V2 / 2)
        }
        val output = activeGateway().generate(
            PromptBundle(
                system = """
                    你是琅嬛的人物蒸馏器。任务是从聊天记录中识别真实出现的不同人物并建立角色卡。
                    只抽取记录能够支持的事实；未知字段留空，严禁为了完整而编造年龄、外貌、经历、关系或性格。
                    同一人物的昵称/备注名应合并为 aliases。不要把“系统/时间/消息/Assistant/User/我/你”当人物。
                    最多输出 12 个人物，优先保留发言量大、特征稳定的人物。
                    说话样例必须来自输入原文，单条不超过 140 字。

                    必须返回 GeneratedChapter JSON，title="人物提取"；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    content 字段必须只包含下面的纯文本块，可以重复多个，不要 Markdown 代码块：
                    <CHARACTER>
                    name=姓名
                    aliases=别名1|别名2
                    appearance=外貌
                    identity=身份
                    personality=性格
                    speechStyle=说话方式
                    catchphrases=口头禅1|口头禅2
                    relationshipToUser=与用户关系
                    relationships=关系1|关系2
                    history=经历1|经历2
                    likes=喜好1|喜好2
                    dislikes=厌恶1|厌恶2
                    boundaries=雷区1|雷区2
                    worldFacts=世界观事实1|世界观事实2
                    currentMemory=当前记忆1|当前记忆2
                    dialogueExamples=原话1|原话2|原话3
                    </CHARACTER>
                    所有字段都必须单行；字段内容不能使用换行。没有证据就留空。
                    不要输出 JSON 外文字。
                """.trimIndent(),
                user = "来源文件：$sourceName\n\n【聊天记录】\n$sample",
            )
        )
        return parseAiCharacterBlocksV2(output.content, sourceName)
    }

    private fun readImportedChat(uri: Uri): Pair<String, String> {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { "聊天记录" }
        val raw = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val buffer = CharArray(8192)
            val builder = StringBuilder()
            while (builder.length < MAX_IMPORT_CHARS_V2) {
                val count = reader.read(buffer)
                if (count <= 0) break
                val remaining = MAX_IMPORT_CHARS_V2 - builder.length
                builder.append(buffer, 0, minOf(count, remaining))
            }
            builder.toString()
        } ?: error("无法读取文件")
        return name to normalizeImportedChatV2(raw)
    }

    private fun mergeCards(
        existing: List<TavernCharacterCardV2>,
        incoming: List<TavernCharacterCardV2>,
    ): List<TavernCharacterCardV2> {
        val result = existing.toMutableList()
        incoming.forEach { card ->
            val names = (listOf(card.name) + card.aliases).map(::normalizeCharacterNameV2).filter { it.isNotBlank() }.toSet()
            val index = result.indexOfFirst { old ->
                (listOf(old.name) + old.aliases).map(::normalizeCharacterNameV2).any { it in names }
            }
            if (index < 0) {
                result += card
            } else {
                val old = result[index]
                result[index] = card.copy(
                    id = old.id,
                    aliases = (old.aliases + card.aliases).distinct(),
                    appearance = card.appearance.ifBlank { old.appearance },
                    identity = card.identity.ifBlank { old.identity },
                    personality = card.personality.ifBlank { old.personality },
                    speechStyle = card.speechStyle.ifBlank { old.speechStyle },
                    catchphrases = (old.catchphrases + card.catchphrases).distinct(),
                    relationshipToUser = card.relationshipToUser.ifBlank { old.relationshipToUser },
                    relationships = (old.relationships + card.relationships).distinct(),
                    history = (old.history + card.history).distinct(),
                    likes = (old.likes + card.likes).distinct(),
                    dislikes = (old.dislikes + card.dislikes).distinct(),
                    boundaries = (old.boundaries + card.boundaries).distinct(),
                    worldFacts = (old.worldFacts + card.worldFacts).distinct(),
                    currentMemory = (old.currentMemory + card.currentMemory).distinct(),
                    dialogueExamples = (old.dialogueExamples + card.dialogueExamples).distinct().takeLast(16),
                    sourceTitle = card.sourceTitle.ifBlank { old.sourceTitle },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        return result.sortedByDescending { it.updatedAt }
    }

    private fun persistCurrent() {
        val current = _state.value
        if (current.novelId.isBlank()) return
        saveArchive(TavernCharacterArchiveV2(current.novelId, current.cards, current.chats))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "tavern_character_hub_v2")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): TavernCharacterArchiveV2 {
        val file = archiveFile(novelId)
        if (!file.isFile) return TavernCharacterArchiveV2(novelId)
        return runCatching { json.decodeFromString(TavernCharacterArchiveV2.serializer(), file.readText()) }
            .getOrElse { TavernCharacterArchiveV2(novelId) }
    }

    private fun saveArchive(archive: TavernCharacterArchiveV2) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(TavernCharacterArchiveV2.serializer(), archive)) }
    }
}

internal fun normalizeImportedChatV2(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("(?is)<(br|/p|/div|/li)\\s*/?>"), "\n")
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
        .replace(Regex("(?s)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

internal fun fallbackExtractTavernCharactersV2(
    text: String,
    sourceTitle: String,
): List<TavernCharacterCardV2> {
    data class SpeakerSample(val name: String, val examples: MutableList<String> = mutableListOf(), var count: Int = 0)
    val speakers = linkedMapOf<String, SpeakerSample>()
    val linePattern = Regex("^\\s*(?:\\[[^]]{0,48}]\\s*)?([^\\s:：]{1,24})\\s*[:：]\\s*(.+)$")
    val ignored = setOf("系统", "时间", "消息", "通知", "assistant", "user", "system", "我", "你")
    text.lineSequence().take(50_000).forEach { line ->
        val match = linePattern.find(line) ?: return@forEach
        val name = match.groupValues[1].trim().trim('[', ']', '【', '】')
        val message = match.groupValues[2].trim()
        if (name.isBlank() || message.isBlank()) return@forEach
        val normalized = normalizeCharacterNameV2(name)
        if (normalized.lowercase() in ignored || normalized.matches(Regex("[0-9:/\\-.]+"))) return@forEach
        val sample = speakers.getOrPut(normalized) { SpeakerSample(name = name) }
        sample.count++
        if (sample.examples.size < 8 && message.length >= 2) sample.examples += message.take(140)
    }
    val viable = speakers.values.filter { it.count >= 2 }.sortedByDescending { it.count }.take(12)
    if (viable.isEmpty()) return emptyList()
    return viable.map { sample ->
        TavernCharacterCardV2(
            id = UUID.randomUUID().toString(),
            name = sample.name,
            speechStyle = "由导入聊天中的原话样例约束",
            dialogueExamples = sample.examples.distinct(),
            source = TavernCharacterSourceV2.CHAT_IMPORT,
            sourceTitle = sourceTitle,
        )
    }
}

internal fun parseAiCharacterBlocksV2(
    content: String,
    sourceTitle: String,
): List<TavernCharacterCardV2> {
    if (content.isBlank()) return emptyList()
    val blockPattern = Regex("(?s)<CHARACTER>\\s*(.*?)\\s*</CHARACTER>")
    fun split(value: String): List<String> = value.split('|').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return blockPattern.findAll(content).mapNotNull { match ->
        val fields = linkedMapOf<String, String>()
        match.groupValues[1].lineSequence().forEach { line ->
            val index = line.indexOf('=')
            if (index > 0) fields[line.substring(0, index).trim()] = line.substring(index + 1).trim()
        }
        val name = fields["name"].orEmpty().trim()
        if (name.isBlank()) return@mapNotNull null
        TavernCharacterCardV2(
            id = UUID.randomUUID().toString(),
            name = name,
            aliases = split(fields["aliases"].orEmpty()),
            appearance = fields["appearance"].orEmpty(),
            identity = fields["identity"].orEmpty(),
            personality = fields["personality"].orEmpty(),
            speechStyle = fields["speechStyle"].orEmpty(),
            catchphrases = split(fields["catchphrases"].orEmpty()),
            relationshipToUser = fields["relationshipToUser"].orEmpty(),
            relationships = split(fields["relationships"].orEmpty()),
            history = split(fields["history"].orEmpty()),
            likes = split(fields["likes"].orEmpty()),
            dislikes = split(fields["dislikes"].orEmpty()),
            boundaries = split(fields["boundaries"].orEmpty()),
            worldFacts = split(fields["worldFacts"].orEmpty()),
            currentMemory = split(fields["currentMemory"].orEmpty()),
            dialogueExamples = split(fields["dialogueExamples"].orEmpty()).take(12),
            source = TavernCharacterSourceV2.CHAT_IMPORT,
            sourceTitle = sourceTitle,
        )
    }.toList().distinctBy { normalizeCharacterNameV2(it.name) }.take(12)
}

internal fun normalizeCharacterNameV2(value: String): String = value
    .trim()
    .replace(Regex("[\\s·•_\\-]+"), "")
    .lowercase()

private const val MAX_IMPORT_CHARS_V2 = 600_000
private const val MAX_AI_SOURCE_CHARS_V2 = 44_000
