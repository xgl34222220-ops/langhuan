package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class NovelCharacterDistillModeV3 {
    QUICK,
    DEEP,
}

@Serializable
data class NovelCharacterEvidenceV3(
    val chapter: Int,
    val field: String,
    val excerpt: String,
)

@Serializable
data class NovelCharacterProfileV3(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val gender: String = "",
    val ageStage: String = "",
    val appearance: String = "",
    val personality: String = "",
    val identity: String = "",
    val occupationBehavior: String = "",
    val abilities: List<String> = emptyList(),
    val faction: String = "",
    val relationships: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val speechStyle: String = "",
    val catchphrases: List<String> = emptyList(),
    val worldFacts: List<String> = emptyList(),
    val currentMemory: List<String> = emptyList(),
    val dialogueExamples: List<String> = emptyList(),
    val characterArc: String = "",
    val currentStatus: String = "",
    val sourceTitle: String = "",
    val distillMode: NovelCharacterDistillModeV3 = NovelCharacterDistillModeV3.QUICK,
    val scannedThroughChapter: Int = 0,
    val evidences: List<NovelCharacterEvidenceV3> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NovelCharacterChatMessageV3(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class NovelCharacterArchiveV3(
    val novelId: String,
    val profiles: List<NovelCharacterProfileV3> = emptyList(),
    val chats: Map<String, List<NovelCharacterChatMessageV3>> = emptyMap(),
)

data class NovelCharacterDistillUiStateV3(
    val novelId: String = "",
    val profiles: List<NovelCharacterProfileV3> = emptyList(),
    val preview: List<NovelCharacterProfileV3> = emptyList(),
    val chats: Map<String, List<NovelCharacterChatMessageV3>> = emptyMap(),
    val distilling: Boolean = false,
    val chatting: Boolean = false,
    val progressText: String = "",
    val notice: String? = null,
    val error: String? = null,
)

private data class NovelCharacterBatchV3(
    val chapterNumbers: List<Int>,
    val text: String,
)

class TavernNovelCharacterViewModelV3(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(NovelCharacterDistillUiStateV3())
    val state: StateFlow<NovelCharacterDistillUiStateV3> = _state.asStateFlow()

    fun open(novelId: String) {
        if (novelId.isBlank()) return
        if (_state.value.novelId == novelId) return
        val archive = loadArchive(novelId)
        _state.value = NovelCharacterDistillUiStateV3(
            novelId = novelId,
            profiles = archive.profiles,
            chats = archive.chats,
        )
    }

    fun distill(
        book: ReaderBookUi,
        chapters: List<ChapterDraft>,
        mode: NovelCharacterDistillModeV3,
        aiReady: Boolean,
    ) {
        if (_state.value.distilling) return
        if (!aiReady) {
            _state.update { it.copy(error = "小说人物蒸馏需要先配置 AI") }
            return
        }
        val usable = chapters.filter { it.content.isNotBlank() }.sortedBy { it.chapterNumber }
        if (usable.isEmpty()) {
            _state.update { it.copy(error = "这本书还没有可蒸馏的章节正文") }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    distilling = true,
                    preview = emptyList(),
                    progressText = if (mode == NovelCharacterDistillModeV3.DEEP) "正在准备深度蒸馏…" else "正在准备快速蒸馏…",
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                val gateway = activeGateway()
                val batches = buildNovelCharacterBatchesV3(usable, mode)
                var merged = emptyList<NovelCharacterProfileV3>()
                batches.forEachIndexed { index, batch ->
                    _state.update {
                        it.copy(
                            progressText = "正在分析 ${index + 1}/${batches.size} · 第 ${batch.chapterNumbers.first()}-${batch.chapterNumbers.last()} 章",
                        )
                    }
                    val output = gateway.generate(
                        PromptBundle(
                            system = novelCharacterDistillSystemPromptV3(),
                            user = """
                                来源作品：《${book.title}》
                                蒸馏模式：${if (mode == NovelCharacterDistillModeV3.DEEP) "深度蒸馏" else "快速蒸馏"}
                                本批章节：${batch.chapterNumbers.joinToString("、")}

                                【小说原文】
                                ${batch.text}
                            """.trimIndent(),
                        )
                    )
                    val parsed = parseNovelCharacterBlocksV3(
                        content = output.content,
                        sourceTitle = book.title,
                        mode = mode,
                        scannedThroughChapter = batch.chapterNumbers.maxOrNull() ?: 0,
                    )
                    merged = mergeNovelCharacterProfilesV3(merged, parsed)
                }
                merged
                    .filter { it.name.isNotBlank() }
                    .sortedWith(
                        compareByDescending<NovelCharacterProfileV3> { it.evidences.size }
                            .thenByDescending { it.dialogueExamples.size }
                            .thenBy { it.name }
                    )
                    .take(MAX_NOVEL_CHARACTERS_V3)
            }.onSuccess { profiles ->
                _state.update {
                    it.copy(
                        distilling = false,
                        progressText = "",
                        preview = profiles,
                        notice = if (profiles.isEmpty()) "没有提取到可用人物" else "已蒸馏 ${profiles.size} 个人物，确认后保存",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        distilling = false,
                        progressText = "",
                        error = error.message ?: "小说人物蒸馏失败",
                    )
                }
            }
        }
    }

    fun savePreview(ids: Set<String>) {
        val current = _state.value
        val selected = current.preview.filter { it.id in ids }
        if (selected.isEmpty()) return
        val merged = mergeNovelCharacterProfilesV3(current.profiles, selected)
        saveArchive(NovelCharacterArchiveV3(current.novelId, merged, current.chats))
        _state.update {
            it.copy(
                profiles = merged,
                preview = emptyList(),
                notice = "已保存 ${selected.size} 个人物",
                error = null,
            )
        }
    }

    fun discardPreview() = _state.update { it.copy(preview = emptyList()) }

    fun deleteProfile(id: String) {
        val current = _state.value
        val profiles = current.profiles.filterNot { it.id == id }
        val chats = current.chats - id
        saveArchive(NovelCharacterArchiveV3(current.novelId, profiles, chats))
        _state.update { it.copy(profiles = profiles, chats = chats, notice = "人物已删除") }
    }

    fun sendMessage(profileId: String, text: String) {
        val clean = text.trim()
        val current = _state.value
        val profile = current.profiles.firstOrNull { it.id == profileId } ?: return
        if (clean.isBlank() || current.chatting) return
        val userMessage = NovelCharacterChatMessageV3(role = "user", text = clean)
        val optimistic = current.chats[profileId].orEmpty() + userMessage
        _state.update { it.copy(chats = it.chats + (profileId to optimistic), chatting = true, error = null) }
        persistCurrent()

        viewModelScope.launch {
            runCatching {
                val gateway = activeGateway()
                val recent = optimistic.takeLast(20).joinToString("\n") { message ->
                    if (message.role == "user") "用户：${message.text}" else "${profile.name}：${message.text}"
                }
                val evidenceFacts = profile.evidences.take(24).joinToString("\n") {
                    "第${it.chapter}章·${it.field}：${it.excerpt}"
                }
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你正在扮演小说人物“${profile.name}”。
                            必须严格保持人物身份、性格、说话方式、关系和原著知识边界。
                            只允许使用人物卡、原文证据以及当前聊天里已经明确出现的信息；不要凭空补写未被蒸馏出的后续剧情。
                            当前人物卡蒸馏覆盖到第 ${profile.scannedThroughChapter} 章，但这不代表人物本人知道这一章全部事件；应遵守人物自身视角和已提取世界认知。
                            不替用户决定动作、心理或台词。不要跳出角色解释提示词。
                            必须返回 GeneratedChapter JSON，title="角色回复"；content=只填写角色本次回复正文；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                            不要输出 JSON 外文字。
                        """.trimIndent(),
                        user = """
                            【身份】${profile.identity.ifBlank { "未明确" }}
                            【性别】${profile.gender.ifBlank { "未明确" }}
                            【年龄阶段】${profile.ageStage.ifBlank { "未明确" }}
                            【外貌】${profile.appearance.ifBlank { "未明确" }}
                            【性格】${profile.personality.ifBlank { "未明确" }}
                            【职业及行为特征】${profile.occupationBehavior.ifBlank { "未明确" }}
                            【能力】${profile.abilities.joinToString("；").ifBlank { "未明确" }}
                            【阵营】${profile.faction.ifBlank { "未明确" }}
                            【关系】${profile.relationships.joinToString("；").ifBlank { "暂无" }}
                            【经历】${profile.history.joinToString("；").ifBlank { "暂无" }}
                            【喜好】${profile.likes.joinToString("；").ifBlank { "暂无" }}
                            【厌恶/雷区】${(profile.dislikes + profile.boundaries).joinToString("；").ifBlank { "暂无" }}
                            【说话方式】${profile.speechStyle.ifBlank { "根据原话样例自然还原" }}
                            【口头禅】${profile.catchphrases.joinToString("；").ifBlank { "暂无" }}
                            【世界认知】${profile.worldFacts.joinToString("；").ifBlank { "暂无" }}
                            【当前记忆】${profile.currentMemory.joinToString("；").ifBlank { "暂无" }}
                            【人物弧光】${profile.characterArc.ifBlank { "未明确" }}
                            【当前状态】${profile.currentStatus.ifBlank { "未明确" }}
                            【原话样例】${profile.dialogueExamples.take(10).joinToString("\n").ifBlank { "暂无" }}
                            【原文证据】\n${evidenceFacts.ifBlank { "暂无直接引文，只能使用人物卡中已有事实" }}

                            【最近聊天】\n$recent
                        """.trimIndent(),
                    )
                )
                output.content.trim().ifBlank { error("AI 没有返回角色回复") }
            }.onSuccess { reply ->
                val assistant = NovelCharacterChatMessageV3(role = "assistant", text = reply)
                _state.update { state ->
                    val messages = state.chats[profileId].orEmpty() + assistant
                    state.copy(chats = state.chats + (profileId to messages), chatting = false)
                }
                persistCurrent()
            }.onFailure { error ->
                _state.update { it.copy(chatting = false, error = error.message ?: "角色回复失败") }
            }
        }
    }

    fun clearChat(profileId: String) {
        _state.update { it.copy(chats = it.chats + (profileId to emptyList()), notice = "聊天记录已清空") }
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

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "tavern_novel_character_v3")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): NovelCharacterArchiveV3 {
        val file = archiveFile(novelId)
        if (!file.isFile) return NovelCharacterArchiveV3(novelId)
        return runCatching { json.decodeFromString(NovelCharacterArchiveV3.serializer(), file.readText()) }
            .getOrElse { NovelCharacterArchiveV3(novelId) }
    }

    private fun saveArchive(archive: NovelCharacterArchiveV3) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(NovelCharacterArchiveV3.serializer(), archive)) }
    }

    private fun persistCurrent() {
        val current = _state.value
        if (current.novelId.isBlank()) return
        saveArchive(NovelCharacterArchiveV3(current.novelId, current.profiles, current.chats))
    }
}

internal fun buildNovelCharacterBatchesV3(
    chapters: List<ChapterDraft>,
    mode: NovelCharacterDistillModeV3,
): List<NovelCharacterBatchV3> {
    val ordered = chapters.filter { it.content.isNotBlank() }.sortedBy { it.chapterNumber }
    if (ordered.isEmpty()) return emptyList()
    val selected = if (mode == NovelCharacterDistillModeV3.DEEP || ordered.size <= QUICK_CHAPTER_COUNT_V3) {
        ordered
    } else {
        val indexes = (0 until QUICK_CHAPTER_COUNT_V3)
            .map { slot -> ((ordered.lastIndex.toDouble() * slot) / (QUICK_CHAPTER_COUNT_V3 - 1)).toInt() }
            .distinct()
        indexes.map { ordered[it] }
    }

    val segments = selected.flatMap { chapter ->
        val content = if (mode == NovelCharacterDistillModeV3.QUICK && chapter.content.length > QUICK_CHAPTER_CHAR_LIMIT_V3) {
            val half = QUICK_CHAPTER_CHAR_LIMIT_V3 / 2
            chapter.content.take(half) + "\n……本章中段已省略……\n" + chapter.content.takeLast(half)
        } else {
            chapter.content
        }
        content.chunked(CHAPTER_SEGMENT_CHARS_V3).mapIndexed { segmentIndex, segment ->
            val title = chapter.title.ifBlank { "第${chapter.chapterNumber}章" }
            chapter.chapterNumber to "【第${chapter.chapterNumber}章 $title · 片段${segmentIndex + 1}】\n$segment"
        }
    }

    val batches = mutableListOf<NovelCharacterBatchV3>()
    var currentNumbers = mutableListOf<Int>()
    var currentText = StringBuilder()
    fun flush() {
        if (currentText.isNotEmpty()) {
            batches += NovelCharacterBatchV3(currentNumbers.distinct(), currentText.toString())
            currentNumbers = mutableListOf()
            currentText = StringBuilder()
        }
    }
    segments.forEach { (chapterNumber, segment) ->
        val extra = segment.length + 2
        if (currentText.isNotEmpty() && currentText.length + extra > BATCH_SOURCE_CHARS_V3) flush()
        currentNumbers += chapterNumber
        currentText.append(segment).append("\n\n")
    }
    flush()
    return batches
}

private fun novelCharacterDistillSystemPromptV3(): String = """
    你是“琅嬛小说人物蒸馏器”。任务是从小说原文中建立可直接用于角色扮演和长期记忆的人物卡。

    原则：
    1. 只提取本批原文能证明的事实，未知字段留空，严禁为了完整而编造。
    2. 同一人物的本名、昵称、称号、假名放入 aliases，尽量不要重复建卡。
    3. 区分“读者知道”与“人物本人知道”。worldFacts/currentMemory 只写这个人物能够知道或亲历的信息。
    4. dialogueExamples 必须是这个人物在输入原文中真正说过的话，每条不超过 140 字。
    5. evidence 必须引用本批原文，标明章节与字段；摘录不超过 120 字。没有直接证据就不要写 evidence。
    6. personality 要写稳定人格与行为倾向，不要用空泛的“善良、勇敢”凑字段。
    7. relationships 写“对象：关系/态度/变化”；history 写关键经历；characterArc 写人物阶段性变化。
    8. 最多输出 20 个人物，优先保留有稳定身份、对白、行为或剧情作用的人物。

    必须返回 GeneratedChapter JSON，title="小说人物蒸馏"；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
    content 字段只能包含以下纯文本块，可重复多个，不要 Markdown 代码块：
    <CHARACTER>
    name=角色名
    aliases=别名1|别名2
    gender=性别
    ageStage=年龄或年龄阶段
    appearance=外貌
    personality=性格特点
    identity=身份
    occupationBehavior=职业及行为特征
    abilities=能力1|能力2
    faction=阵营或势力
    relationships=人物A：关系|人物B：关系
    history=经历1|经历2
    likes=喜好1|喜好2
    dislikes=厌恶1|厌恶2
    boundaries=禁忌或底线1|禁忌或底线2
    speechStyle=说话方式
    catchphrases=口头禅1|口头禅2
    worldFacts=本人知道的世界事实1|本人知道的世界事实2
    currentMemory=本人重要记忆1|本人重要记忆2
    dialogueExamples=原话1|原话2|原话3
    characterArc=人物弧光或阶段变化
    currentStatus=当前状态
    evidence=章节号~字段~原文摘录§章节号~字段~原文摘录
    </CHARACTER>
    所有字段都必须单行；字段内容不能使用换行。没有证据的字段留空。
    不要输出 JSON 外文字。
""".trimIndent()

internal fun parseNovelCharacterBlocksV3(
    content: String,
    sourceTitle: String,
    mode: NovelCharacterDistillModeV3,
    scannedThroughChapter: Int,
): List<NovelCharacterProfileV3> {
    if (content.isBlank()) return emptyList()
    val blockPattern = Regex("(?s)<CHARACTER>\\s*(.*?)\\s*</CHARACTER>")
    fun split(value: String): List<String> = value.split('|').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    fun evidence(value: String): List<NovelCharacterEvidenceV3> = value.split('§').mapNotNull { raw ->
        val parts = raw.trim().split('~', limit = 3)
        val chapter = parts.getOrNull(0)?.filter(Char::isDigit)?.toIntOrNull() ?: return@mapNotNull null
        val field = parts.getOrNull(1).orEmpty().trim()
        val excerpt = parts.getOrNull(2).orEmpty().trim().take(120)
        if (excerpt.isBlank()) null else NovelCharacterEvidenceV3(chapter, field, excerpt)
    }
    return blockPattern.findAll(content).mapNotNull { match ->
        val fields = linkedMapOf<String, String>()
        match.groupValues[1].lineSequence().forEach { line ->
            val index = line.indexOf('=')
            if (index > 0) fields[line.substring(0, index).trim()] = line.substring(index + 1).trim()
        }
        val name = fields["name"].orEmpty().trim()
        if (name.isBlank()) return@mapNotNull null
        NovelCharacterProfileV3(
            name = name,
            aliases = split(fields["aliases"].orEmpty()),
            gender = fields["gender"].orEmpty(),
            ageStage = fields["ageStage"].orEmpty(),
            appearance = fields["appearance"].orEmpty(),
            personality = fields["personality"].orEmpty(),
            identity = fields["identity"].orEmpty(),
            occupationBehavior = fields["occupationBehavior"].orEmpty(),
            abilities = split(fields["abilities"].orEmpty()),
            faction = fields["faction"].orEmpty(),
            relationships = split(fields["relationships"].orEmpty()),
            history = split(fields["history"].orEmpty()),
            likes = split(fields["likes"].orEmpty()),
            dislikes = split(fields["dislikes"].orEmpty()),
            boundaries = split(fields["boundaries"].orEmpty()),
            speechStyle = fields["speechStyle"].orEmpty(),
            catchphrases = split(fields["catchphrases"].orEmpty()),
            worldFacts = split(fields["worldFacts"].orEmpty()),
            currentMemory = split(fields["currentMemory"].orEmpty()),
            dialogueExamples = split(fields["dialogueExamples"].orEmpty()).take(12),
            characterArc = fields["characterArc"].orEmpty(),
            currentStatus = fields["currentStatus"].orEmpty(),
            sourceTitle = sourceTitle,
            distillMode = mode,
            scannedThroughChapter = scannedThroughChapter,
            evidences = evidence(fields["evidence"].orEmpty()),
        )
    }.toList()
}

internal fun mergeNovelCharacterProfilesV3(
    existing: List<NovelCharacterProfileV3>,
    incoming: List<NovelCharacterProfileV3>,
): List<NovelCharacterProfileV3> {
    val result = existing.toMutableList()
    incoming.forEach { profile ->
        val incomingNames = (listOf(profile.name) + profile.aliases)
            .map(::normalizeCharacterNameV2)
            .filter(String::isNotBlank)
            .toSet()
        val index = result.indexOfFirst { old ->
            (listOf(old.name) + old.aliases)
                .map(::normalizeCharacterNameV2)
                .any { it in incomingNames }
        }
        if (index < 0) {
            result += profile
        } else {
            val old = result[index]
            fun richer(a: String, b: String): String = if (b.length > a.length) b else a
            fun mergedList(a: List<String>, b: List<String>, limit: Int = 40): List<String> =
                (a + b).map(String::trim).filter(String::isNotBlank).distinct().takeLast(limit)
            val aliases = mergedList(
                old.aliases,
                profile.aliases + listOf(profile.name).filter { normalizeCharacterNameV2(it) != normalizeCharacterNameV2(old.name) },
                20,
            )
            result[index] = old.copy(
                aliases = aliases,
                gender = richer(old.gender, profile.gender),
                ageStage = richer(old.ageStage, profile.ageStage),
                appearance = richer(old.appearance, profile.appearance),
                personality = richer(old.personality, profile.personality),
                identity = richer(old.identity, profile.identity),
                occupationBehavior = richer(old.occupationBehavior, profile.occupationBehavior),
                abilities = mergedList(old.abilities, profile.abilities),
                faction = richer(old.faction, profile.faction),
                relationships = mergedList(old.relationships, profile.relationships),
                history = mergedList(old.history, profile.history),
                likes = mergedList(old.likes, profile.likes),
                dislikes = mergedList(old.dislikes, profile.dislikes),
                boundaries = mergedList(old.boundaries, profile.boundaries),
                speechStyle = richer(old.speechStyle, profile.speechStyle),
                catchphrases = mergedList(old.catchphrases, profile.catchphrases, 20),
                worldFacts = mergedList(old.worldFacts, profile.worldFacts),
                currentMemory = mergedList(old.currentMemory, profile.currentMemory),
                dialogueExamples = mergedList(old.dialogueExamples, profile.dialogueExamples, 20),
                characterArc = richer(old.characterArc, profile.characterArc),
                currentStatus = richer(old.currentStatus, profile.currentStatus),
                distillMode = if (old.distillMode == NovelCharacterDistillModeV3.DEEP || profile.distillMode == NovelCharacterDistillModeV3.DEEP) NovelCharacterDistillModeV3.DEEP else NovelCharacterDistillModeV3.QUICK,
                scannedThroughChapter = maxOf(old.scannedThroughChapter, profile.scannedThroughChapter),
                evidences = (old.evidences + profile.evidences)
                    .distinctBy { Triple(it.chapter, it.field, it.excerpt) }
                    .sortedBy { it.chapter }
                    .takeLast(100),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }
    return result.sortedByDescending { it.updatedAt }
}

private enum class NovelCharacterScreenV3 {
    LIBRARY,
    DETAIL,
    CHAT,
    CHAT_IMPORT,
    STORY,
}

@Composable
fun TavernNovelCharacterExperienceV3(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val vm: TavernNovelCharacterViewModelV3 = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var screen by rememberSaveable(book.id) { mutableStateOf(NovelCharacterScreenV3.LIBRARY) }
    var selectedId by rememberSaveable(book.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(book.id) { vm.open(book.id) }
    LaunchedEffect(state.notice, state.error) {
        val message = state.error ?: state.notice
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            vm.clearFeedback()
        }
    }

    val selected = state.profiles.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId, state.profiles) {
        if (selectedId != null && selected == null) {
            selectedId = null
            screen = NovelCharacterScreenV3.LIBRARY
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            NovelCharacterScreenV3.LIBRARY -> NovelCharacterLibraryV3(
                book = book,
                chapterCount = libraryState.chapters.count { it.content.isNotBlank() },
                state = state,
                onQuick = {
                    if (aiReady) vm.distill(book, libraryState.chapters, NovelCharacterDistillModeV3.QUICK, true) else onAiSetup()
                },
                onDeep = {
                    if (aiReady) vm.distill(book, libraryState.chapters, NovelCharacterDistillModeV3.DEEP, true) else onAiSetup()
                },
                onOpen = { profile -> selectedId = profile.id; screen = NovelCharacterScreenV3.DETAIL },
                onChatImport = { screen = NovelCharacterScreenV3.CHAT_IMPORT },
                onStory = { screen = NovelCharacterScreenV3.STORY },
            )
            NovelCharacterScreenV3.DETAIL -> if (selected != null) {
                NovelCharacterDetailV3(
                    profile = selected,
                    messageCount = state.chats[selected.id].orEmpty().size,
                    onBack = { screen = NovelCharacterScreenV3.LIBRARY },
                    onChat = { if (aiReady) screen = NovelCharacterScreenV3.CHAT else onAiSetup() },
                    onDelete = { vm.deleteProfile(selected.id); selectedId = null; screen = NovelCharacterScreenV3.LIBRARY },
                )
            }
            NovelCharacterScreenV3.CHAT -> if (selected != null) {
                NovelCharacterChatV3(
                    profile = selected,
                    messages = state.chats[selected.id].orEmpty(),
                    busy = state.chatting,
                    onBack = { screen = NovelCharacterScreenV3.DETAIL },
                    onSend = { vm.sendMessage(selected.id, it) },
                    onClear = { vm.clearChat(selected.id) },
                )
            }
            NovelCharacterScreenV3.CHAT_IMPORT -> Box(Modifier.fillMaxSize()) {
                TavernCharacterHubV2(book, libraryState, aiReady, onAiSetup)
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 10.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                ) {
                    IconButton(onClick = { screen = NovelCharacterScreenV3.LIBRARY }) {
                        Icon(Icons.Rounded.ArrowBack, "返回小说人物")
                    }
                }
            }
            NovelCharacterScreenV3.STORY -> Box(Modifier.fillMaxSize()) {
                StoryCoreExperience(book, libraryState, aiReady, onAiSetup)
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 10.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                ) {
                    IconButton(onClick = { screen = NovelCharacterScreenV3.LIBRARY }) {
                        Icon(Icons.Rounded.ArrowBack, "返回小说人物")
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 42.dp, vertical = 8.dp),
        )
    }

    if (state.preview.isNotEmpty()) {
        NovelCharacterPreviewDialogV3(
            profiles = state.preview,
            onDismiss = vm::discardPreview,
            onSave = vm::savePreview,
        )
    }
}

@Composable
private fun NovelCharacterLibraryV3(
    book: ReaderBookUi,
    chapterCount: Int,
    state: NovelCharacterDistillUiStateV3,
    onQuick: () -> Unit,
    onDeep: () -> Unit,
    onOpen: (NovelCharacterProfileV3) -> Unit,
    onChatImport: () -> Unit,
    onStory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("人物蒸馏", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(
                "从《${book.title}》正文提取可聊天的原著人物卡",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(12.dp).size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("当前小说 · $chapterCount 章正文", fontWeight = FontWeight.Bold)
                            Text("识别别名、性格、能力、关系、经历、对白、世界认知和原文证据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onQuick,
                            enabled = !state.distilling && chapterCount > 0,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.Speed, null)
                            Spacer(Modifier.width(6.dp))
                            Text("快速蒸馏")
                        }
                        FilledTonalButton(
                            onClick = onDeep,
                            enabled = !state.distilling && chapterCount > 0,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.TravelExplore, null)
                            Spacer(Modifier.width(6.dp))
                            Text("深度蒸馏")
                        }
                    }
                    if (state.distilling) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(state.progressText, Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChatImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text("聊天导入")
                }
                OutlinedButton(onClick = onStory, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.AutoStories, null)
                    Spacer(Modifier.width(6.dp))
                    Text("故事分支")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("已保存人物", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (state.profiles.isEmpty()) "还没有小说人物，先从当前小说蒸馏" else "点击人物查看完整角色卡、原文证据并开始聊天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.profiles.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Groups, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("从小说正文建立人物库", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        Text("快速蒸馏适合先看主要人物；深度蒸馏会扫描全部正文片段。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(profile) },
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 1.dp,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(profile.name.take(1).ifBlank { "人" }, fontSize = 23.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(profile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                profile.identity.ifBlank { profile.personality.ifBlank { "原著人物" } },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (profile.distillMode == NovelCharacterDistillModeV3.DEEP) "深度" else "快速", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("证据 ${profile.evidences.size}", style = MaterialTheme.typography.labelSmall)
                                if (profile.scannedThroughChapter > 0) Text("至 ${profile.scannedThroughChapter} 章", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelCharacterDetailV3(
    profile: NovelCharacterProfileV3,
    messageCount: Int,
    onBack: () -> Unit,
    onChat: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(top = 58.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
            Text("角色卡", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text(profile.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    if (profile.aliases.isNotEmpty()) Text(profile.aliases.joinToString(" · "), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f), modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(if (profile.distillMode == NovelCharacterDistillModeV3.DEEP) "深度蒸馏" else "快速蒸馏") })
                        if (profile.scannedThroughChapter > 0) AssistChip(onClick = {}, label = { Text("覆盖至 ${profile.scannedThroughChapter} 章") })
                    }
                }
            }
            NovelFieldV3("性别", profile.gender)
            NovelFieldV3("年龄 / 年龄阶段", profile.ageStage)
            NovelFieldV3("外貌", profile.appearance)
            NovelFieldV3("性格特点", profile.personality)
            NovelFieldV3("身份", profile.identity)
            NovelFieldV3("职业及行为特征", profile.occupationBehavior)
            NovelListV3("能力 / 技能 / 装备", profile.abilities)
            NovelFieldV3("阵营 / 势力", profile.faction)
            NovelListV3("人物关系", profile.relationships)
            NovelListV3("重要经历", profile.history)
            NovelListV3("特殊喜好", profile.likes)
            NovelListV3("厌恶", profile.dislikes)
            NovelListV3("禁忌 / 底线", profile.boundaries)
            NovelFieldV3("说话方式", profile.speechStyle)
            NovelListV3("口头禅", profile.catchphrases)
            NovelListV3("对话示例", profile.dialogueExamples)
            NovelListV3("世界认知", profile.worldFacts)
            NovelListV3("当前记忆", profile.currentMemory)
            NovelFieldV3("人物弧光", profile.characterArc)
            NovelFieldV3("当前状态", profile.currentStatus)
            if (profile.evidences.isNotEmpty()) {
                Text("原文证据", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
                profile.evidences.take(40).forEach { item ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.padding(11.dp)) {
                            Text("第 ${item.chapter} 章 · ${item.field.ifBlank { "人物事实" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(item.excerpt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Surface(shadowElevation = 5.dp) {
            Button(onClick = onChat, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Chat, null)
                Spacer(Modifier.width(8.dp))
                Text(if (messageCount > 0) "继续聊天" else "开始聊天", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${profile.name}？") },
            text = { Text("人物卡、原文证据和这个人物的聊天记录都会删除。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NovelFieldV3(title: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(value, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
    }
}

@Composable
private fun NovelListV3(title: String, values: List<String>) {
    val clean = values.map(String::trim).filter(String::isNotBlank).distinct()
    if (clean.isEmpty()) return
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        clean.forEach { item ->
            Row(Modifier.padding(top = 6.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Text(item, Modifier.padding(start = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun NovelCharacterChatV3(
    profile: NovelCharacterProfileV3,
    messages: List<NovelCharacterChatMessageV3>,
    busy: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by rememberSaveable(profile.id) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(top = 58.dp).imePadding()) {
        Surface(tonalElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回人物") }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1), fontWeight = FontWeight.Black) }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    Text("原著角色 · 覆盖至第 ${profile.scannedThroughChapter} 章", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "清空聊天") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MenuBook, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("和 ${profile.name} 说点什么", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("人物卡、原文证据、世界认知和聊天记忆会一起约束回复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val mine = message.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        modifier = Modifier.widthIn(max = 310.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, lineHeight = 21.sp)
                    }
                }
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("${profile.name} 正在回复…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Surface(shadowElevation = 5.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    placeholder = { Text("和 ${profile.name} 聊天…") },
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotBlank()) {
                            onSend(text)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Rounded.Send, "发送") }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空聊天？") },
            text = { Text("人物卡和原文证据会保留，只删除聊天记录。") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NovelCharacterPreviewDialogV3(
    profiles: List<NovelCharacterProfileV3>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(profiles) { mutableStateOf(profiles.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("蒸馏到 ${profiles.size} 个人物") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    val checked = profile.id in selected
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (checked) selected - profile.id else selected + profile.id
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1), fontWeight = FontWeight.Black) }
                            }
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text(profile.name, fontWeight = FontWeight.Bold)
                                Text(profile.identity.ifBlank { profile.personality.ifBlank { "原著人物" } }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("原文证据 ${profile.evidences.size} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected) }, enabled = selected.isNotEmpty()) {
                Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("保存 ${selected.size} 个")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private const val QUICK_CHAPTER_COUNT_V3 = 12
private const val QUICK_CHAPTER_CHAR_LIMIT_V3 = 12_000
private const val CHAPTER_SEGMENT_CHARS_V3 = 10_000
private const val BATCH_SOURCE_CHARS_V3 = 30_000
private const val MAX_NOVEL_CHARACTERS_V3 = 60
