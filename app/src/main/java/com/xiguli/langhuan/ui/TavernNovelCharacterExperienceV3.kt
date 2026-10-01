package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.itemsIndexed
import com.xiguli.langhuan.ui.design.LanghuanMotionStatus
import com.xiguli.langhuan.ui.design.LanghuanEnterRegistryV31
import com.xiguli.langhuan.ui.design.LanghuanTypingDotsV31
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.enterOnceV31
import com.xiguli.langhuan.ui.design.rememberEnterRegistryV31
import com.xiguli.langhuan.ui.design.springClickV31
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.TaskModelRouter
import com.xiguli.langhuan.engine.hasConfiguredDefaultAi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class NovelCharacterDistillModeV3 { QUICK, DEEP }

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

internal data class NovelCharacterBatchV3(
    val chapterNumbers: List<Int>,
    val text: String,
)

class TavernNovelCharacterViewModelV3(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(NovelCharacterDistillUiStateV3())
    val state: StateFlow<NovelCharacterDistillUiStateV3> = _state.asStateFlow()
    val aiReady: StateFlow<Boolean> = repository.observeProviders()
        .map(::hasConfiguredDefaultAi)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val requests = StoryRequestScope()
    private var previewOwner: StoryRequestScope.Ticket? = null

    fun open(novelId: String) {
        if (novelId.isBlank() || _state.value.novelId == novelId) return
        requests.open(novelId)
        previewOwner = null
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
        if (_state.value.novelId != book.id || _state.value.distilling) return
        if (!aiReady) {
            _state.update { it.copy(error = "小说人物蒸馏需要先配置 AI") }
            return
        }
        val usable = chapters.filter { it.novelId == book.id && it.content.isNotBlank() }.sortedBy { it.readingOrder }
        if (usable.isEmpty()) {
            _state.update { it.copy(error = "这本书还没有可蒸馏的章节正文") }
            return
        }

        val request = requests.begin("distill")
        previewOwner = null
        updateRequest(request) {
            it.copy(
                distilling = true,
                preview = emptyList(),
                progressText = if (mode == NovelCharacterDistillModeV3.DEEP) "正在准备深度蒸馏…" else "正在准备快速蒸馏…",
                notice = null,
                error = null,
            )
        }
        requests.launch(viewModelScope, request) {
            runCatching {
                val gateway = activeGateway(AiTaskType.CHARACTER_EXTRACTION)
                requests.ensureCurrent(request)
                val batches = buildNovelCharacterBatchesV3(usable, mode)
                var merged = emptyList<NovelCharacterProfileV3>()
                batches.forEachIndexed { index, batch ->
                    requests.ensureCurrent(request)
                    val first = batch.chapterNumbers.firstOrNull() ?: 0
                    val last = batch.chapterNumbers.lastOrNull() ?: first
                    updateRequest(request) {
                        it.copy(progressText = "正在分析 ${index + 1}/${batches.size} · 第 $first-$last 章")
                    }
                    val result = gateway.generate(
                        PromptBundle(
                            task = AiTaskType.CHARACTER_EXTRACTION,
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
                    requests.ensureCurrent(request)
                    merged = mergeNovelCharacterProfilesV3(
                        merged,
                        parseNovelCharacterBlocksV3(
                            content = result.content,
                            sourceTitle = book.title,
                            mode = mode,
                            scannedThroughChapter = batch.chapterNumbers.maxOrNull() ?: 0,
                        ),
                    )
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
                requests.ensureCurrent(request)
                previewOwner = request
                updateRequest(request) {
                    it.copy(
                        distilling = false,
                        progressText = "",
                        preview = profiles,
                        notice = if (profiles.isEmpty()) "没有提取到可用人物" else "已蒸馏 ${profiles.size} 个人物，确认后保存",
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                updateRequest(request) {
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
        val owner = previewOwner ?: return
        if (!requests.isCurrent(owner) || current.novelId != owner.novelId) return
        val selected = current.preview.filter { it.id in ids }
        if (selected.isEmpty()) return
        val merged = mergeNovelCharacterProfilesV3(current.profiles, selected)
        saveArchive(NovelCharacterArchiveV3(owner.novelId, merged, current.chats))
        previewOwner = null
        updateRequest(owner) {
            it.copy(profiles = merged, preview = emptyList(), notice = "已保存 ${selected.size} 个人物", error = null)
        }
    }

    fun discardPreview() {
        previewOwner = null
        requests.cancel("distill")
        _state.update { it.copy(preview = emptyList(), distilling = false, progressText = "") }
    }

    fun deleteProfile(id: String) {
        val current = _state.value
        if (current.novelId.isBlank() || current.profiles.none { it.id == id }) return
        cancelChatFor(id)
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

        val request = requests.begin("chat", profileId)
        val userMessage = NovelCharacterChatMessageV3(role = "user", text = clean)
        val optimistic = current.chats[profileId].orEmpty() + userMessage
        updateRequest(request) { it.copy(chats = it.chats + (profileId to optimistic), chatting = true, error = null) }
        persistCurrent(request)

        requests.launch(viewModelScope, request) {
            runCatching {
                val gateway = activeGateway(AiTaskType.ROLEPLAY)
                requests.ensureCurrent(request)
                val recent = optimistic.takeLast(20).joinToString("\n") { message ->
                    if (message.role == "user") "用户：${message.text}" else "${profile.name}：${message.text}"
                }
                val evidence = profile.evidences.take(24).joinToString("\n") {
                    "第${it.chapter}章·${it.field}：${it.excerpt}"
                }
                gateway.generate(
                    PromptBundle(
                        system = """
                            你正在扮演小说人物“${profile.name}”。
                            必须严格保持人物身份、性格、说话方式、关系和原著知识边界。
                            只允许使用人物卡、人物本人可知的世界认知、原文证据和当前聊天里已经明确出现的信息。
                            人物卡覆盖到第 ${profile.scannedThroughChapter} 章，不代表人物本人知道这一章的全部事件。
                            不替用户决定动作、心理或台词，不跳出角色解释提示词。
                            必须返回 GeneratedChapter JSON，title="角色回复"；content=只填写角色本次回复正文；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                            不要输出 JSON 外文字。
                        """.trimIndent(),
                        user = buildCharacterChatContextV3(profile, evidence, recent),
                        task = AiTaskType.ROLEPLAY,
                    )
                ).content.trim().ifBlank { error("AI 没有返回角色回复") }
            }.onSuccess { reply ->
                requests.ensureCurrent(request)
                val assistant = NovelCharacterChatMessageV3(role = "assistant", text = reply)
                updateRequest(request) { state ->
                    val messages = state.chats[profileId].orEmpty() + assistant
                    state.copy(chats = state.chats + (profileId to messages), chatting = false)
                }
                persistCurrent(request)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                updateRequest(request) { it.copy(chatting = false, error = error.message ?: "角色回复失败") }
            }
        }
    }

    fun clearChat(profileId: String) {
        val current = _state.value
        if (current.novelId.isBlank() || current.profiles.none { it.id == profileId }) return
        cancelChatFor(profileId)
        _state.update { it.copy(chats = it.chats + (profileId to emptyList()), notice = "聊天记录已清空") }
        persistCurrent(current.novelId)
    }

    private fun cancelChatFor(profileId: String) {
        if (requests.current("chat")?.profileId == profileId) {
            requests.cancel("chat")
            _state.update { it.copy(chatting = false) }
        }
    }

    fun clearFeedback() = _state.update { it.copy(notice = null, error = null) }

    private suspend fun activeGateway(task: AiTaskType): AiGateway =
        TaskModelRouter(getApplication<Application>()).snapshot().selection(task).gateway

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "tavern_novel_character_v3")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): NovelCharacterArchiveV3 {
        val file = archiveFile(novelId)
        if (!file.isFile) return NovelCharacterArchiveV3(novelId)
        return runCatching {
            json.decodeFromString(NovelCharacterArchiveV3.serializer(), file.readText())
                .takeIf { it.novelId == novelId } ?: NovelCharacterArchiveV3(novelId)
        }
            .getOrElse { NovelCharacterArchiveV3(novelId) }
    }

    private fun saveArchive(archive: NovelCharacterArchiveV3) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(NovelCharacterArchiveV3.serializer(), archive)) }
    }

    private inline fun updateRequest(request: StoryRequestScope.Ticket, transform: (NovelCharacterDistillUiStateV3) -> NovelCharacterDistillUiStateV3) {
        _state.update { state ->
            if (requests.isCurrent(request) && state.novelId == request.novelId &&
                (request.profileId == null || state.profiles.any { it.id == request.profileId })
            ) transform(state) else state
        }
    }

    private fun persistCurrent(request: StoryRequestScope.Ticket) {
        if (!requests.isCurrent(request)) return
        if (request.profileId != null && _state.value.profiles.none { it.id == request.profileId }) return
        persistCurrent(request.novelId)
    }

    private fun persistCurrent(novelId: String) {
        val current = _state.value
        if (novelId.isBlank() || current.novelId != novelId) return
        saveArchive(NovelCharacterArchiveV3(novelId, current.profiles, current.chats))
    }
}

private fun buildCharacterChatContextV3(
    profile: NovelCharacterProfileV3,
    evidence: String,
    recent: String,
): String = """
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
    【原话样例】
    ${profile.dialogueExamples.take(10).joinToString("\n").ifBlank { "暂无" }}
    【原文证据】
    ${evidence.ifBlank { "暂无直接引文，只能使用人物卡中已有事实" }}
    【最近聊天】
    $recent
""".trimIndent()

internal fun buildNovelCharacterBatchesV3(
    chapters: List<ChapterDraft>,
    mode: NovelCharacterDistillModeV3,
): List<NovelCharacterBatchV3> {
    val ordered = chapters.filter { it.content.isNotBlank() }.sortedBy { it.readingOrder }
    if (ordered.isEmpty()) return emptyList()

    val selected = if (mode == NovelCharacterDistillModeV3.DEEP || ordered.size <= QUICK_CHAPTER_COUNT_V3) {
        ordered
    } else {
        (0 until QUICK_CHAPTER_COUNT_V3)
            .map { slot -> ((ordered.lastIndex.toDouble() * slot) / (QUICK_CHAPTER_COUNT_V3 - 1)).toInt() }
            .distinct()
            .map { ordered[it] }
    }

    val segments = selected.flatMap { chapter ->
        val source = if (mode == NovelCharacterDistillModeV3.QUICK && chapter.content.length > QUICK_CHAPTER_CHAR_LIMIT_V3) {
            val half = QUICK_CHAPTER_CHAR_LIMIT_V3 / 2
            chapter.content.take(half) + "\n……本章中段已省略……\n" + chapter.content.takeLast(half)
        } else {
            chapter.content
        }
        source.chunked(CHAPTER_SEGMENT_CHARS_V3).mapIndexed { index, segment ->
            val title = chapter.title.ifBlank { "第${chapter.chapterNumber}章" }
            chapter.chapterNumber to "【第${chapter.chapterNumber}章 $title · 片段${index + 1}】\n$segment"
        }
    }

    val result = mutableListOf<NovelCharacterBatchV3>()
    var numbers = mutableListOf<Int>()
    var text = StringBuilder()
    fun flush() {
        if (text.isEmpty()) return
        result += NovelCharacterBatchV3(numbers.distinct(), text.toString())
        numbers = mutableListOf()
        text = StringBuilder()
    }
    segments.forEach { (chapterNumber, segment) ->
        if (text.isNotEmpty() && text.length + segment.length + 2 > BATCH_SOURCE_CHARS_V3) flush()
        numbers += chapterNumber
        text.append(segment).append("\n\n")
    }
    flush()
    return result
}

private fun novelCharacterDistillSystemPromptV3(): String = """
    你是“琅嬛小说人物蒸馏器”。从小说原文建立可用于角色扮演、人物库和长期记忆的人物卡。
    只提取当前原文能够证明的事实，未知字段必须留空，禁止为了完整而编造。
    同一人物的本名、昵称、称号、假名合并到 aliases，尽量避免重复建卡。
    必须区分读者知道和人物本人知道：worldFacts/currentMemory 只写人物本人能知道或亲历的信息。
    dialogueExamples 必须是人物在输入原文中真正说过的话，每条不超过 140 字。
    evidence 必须引用输入原文，格式为“章节号~字段~摘录”，摘录不超过 120 字。
    personality 写稳定人格和行为倾向；relationships 写“对象：关系/态度/变化”；history 写关键经历；characterArc 写阶段变化。
    每批最多输出 20 个人物，优先保留有身份、对白、行为或剧情作用的人物。

    必须返回 GeneratedChapter JSON，title="小说人物蒸馏"；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
    content 只能包含以下纯文本块，可重复多个：
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
    所有字段单行；没有证据就留空；不要输出 JSON 外文字。
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
    fun parseEvidence(value: String): List<NovelCharacterEvidenceV3> = value.split('§').mapNotNull { raw ->
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
            evidences = parseEvidence(fields["evidence"].orEmpty()),
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
            (listOf(old.name) + old.aliases).map(::normalizeCharacterNameV2).any { it in incomingNames }
        }
        if (index < 0) {
            result += profile
        } else {
            val old = result[index]
            fun richer(a: String, b: String): String = if (b.length > a.length) b else a
            fun mergeList(a: List<String>, b: List<String>, limit: Int = 40): List<String> =
                (a + b).map(String::trim).filter(String::isNotBlank).distinct().takeLast(limit)
            result[index] = old.copy(
                aliases = mergeList(old.aliases, profile.aliases + listOf(profile.name).filter { normalizeCharacterNameV2(it) != normalizeCharacterNameV2(old.name) }, 20),
                gender = richer(old.gender, profile.gender),
                ageStage = richer(old.ageStage, profile.ageStage),
                appearance = richer(old.appearance, profile.appearance),
                personality = richer(old.personality, profile.personality),
                identity = richer(old.identity, profile.identity),
                occupationBehavior = richer(old.occupationBehavior, profile.occupationBehavior),
                abilities = mergeList(old.abilities, profile.abilities),
                faction = richer(old.faction, profile.faction),
                relationships = mergeList(old.relationships, profile.relationships),
                history = mergeList(old.history, profile.history),
                likes = mergeList(old.likes, profile.likes),
                dislikes = mergeList(old.dislikes, profile.dislikes),
                boundaries = mergeList(old.boundaries, profile.boundaries),
                speechStyle = richer(old.speechStyle, profile.speechStyle),
                catchphrases = mergeList(old.catchphrases, profile.catchphrases, 20),
                worldFacts = mergeList(old.worldFacts, profile.worldFacts),
                currentMemory = mergeList(old.currentMemory, profile.currentMemory),
                dialogueExamples = mergeList(old.dialogueExamples, profile.dialogueExamples, 20),
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

private enum class NovelCharacterScreenV3 { LIBRARY, DETAIL, CHAT, CHAT_IMPORT, STORY }

@Composable
fun TavernNovelCharacterExperienceV3(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onBack: (() -> Unit)? = null,
    onLoadCurrentChapter: (() -> Unit)? = null,
) {
    val vm: TavernNovelCharacterViewModelV3 = viewModel()
    val observedState by vm.state.collectAsStateWithLifecycle()
    val configuredAi by vm.aiReady.collectAsStateWithLifecycle()
    val state = observedState.takeIf { it.novelId == book.id } ?: NovelCharacterDistillUiStateV3(novelId = book.id)
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
    val currentChapter = libraryState.readingChapter?.takeIf { it.novelId == book.id }
    val sourceChapters = libraryState.chapters.filter { it.novelId == book.id }.let { chapters ->
        if (currentChapter?.content?.isNotBlank() == true) {
            chapters.filterNot { it.id == currentChapter.id } + currentChapter
        } else chapters
    }
    // One back path for the whole area: sub-pages step back inside, the library leaves.
    BackHandler(enabled = screen != NovelCharacterScreenV3.LIBRARY || onBack != null) {
        when (screen) {
            NovelCharacterScreenV3.LIBRARY -> onBack?.invoke()
            NovelCharacterScreenV3.CHAT -> screen = NovelCharacterScreenV3.DETAIL
            else -> screen = NovelCharacterScreenV3.LIBRARY
        }
    }
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
                chapterCount = sourceChapters.count { it.content.isNotBlank() },
                state = state,
                onQuick = { if (configuredAi) vm.distill(book, sourceChapters, NovelCharacterDistillModeV3.QUICK, true) else onAiSetup() },
                onDeep = { if (configuredAi) vm.distill(book, sourceChapters, NovelCharacterDistillModeV3.DEEP, true) else onAiSetup() },
                onOpen = { profile -> selectedId = profile.id; screen = NovelCharacterScreenV3.DETAIL },
                onChatImport = { screen = NovelCharacterScreenV3.CHAT_IMPORT },
                onStory = { screen = NovelCharacterScreenV3.STORY },
                onBack = onBack,
                onLoadCurrentChapter = onLoadCurrentChapter,
                currentChapterNeedsLoading = currentChapter?.content.isNullOrBlank(),
                loadingChapterNumber = libraryState.loadingChapterNumber,
                readerLoadError = libraryState.readerLoadError,
            )
            NovelCharacterScreenV3.DETAIL -> if (selected != null) {
                NovelCharacterDetailV3(
                    profile = selected,
                    messageCount = state.chats[selected.id].orEmpty().size,
                    onBack = { screen = NovelCharacterScreenV3.LIBRARY },
                    onChat = { if (configuredAi) screen = NovelCharacterScreenV3.CHAT else onAiSetup() },
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
            NovelCharacterScreenV3.CHAT_IMPORT -> SecondaryTavernRouteV3("导入聊天角色", onBack = { screen = NovelCharacterScreenV3.LIBRARY }) {
                TavernCharacterHubV2(book, libraryState, configuredAi, onAiSetup)
            }
            NovelCharacterScreenV3.STORY -> SecondaryTavernRouteV3("故事分支", onBack = { screen = NovelCharacterScreenV3.LIBRARY }) {
                StoryCoreExperience(book, libraryState, configuredAi, onAiSetup)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 42.dp, vertical = 8.dp))
    }

    if (state.preview.isNotEmpty()) {
        NovelCharacterPreviewDialogV3(state.preview, vm::discardPreview, vm::savePreview)
    }
}

@Composable
private fun SecondaryTavernRouteV3(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    // A real top bar instead of a floating button that covered the child's own header actions.
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回故事入口") }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        // The child already pads for the status bar; our bar has taken that space.
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) { content() }
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
    onBack: (() -> Unit)? = null,
    onLoadCurrentChapter: (() -> Unit)? = null,
    currentChapterNeedsLoading: Boolean = false,
    loadingChapterNumber: Int? = null,
    readerLoadError: String? = null,
) {
    val profileEnter = rememberEnterRegistryV31()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (onBack != null) 4.dp else 24.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.Rounded.ArrowBack, "返回") }
            }
            Text("进入故事", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("从《${book.title}》当前章开始互动，或提取人物后聊天", color = LocalLanghuanUiTokens.current.mutedForeground, modifier = Modifier.padding(top = 3.dp))
            Button(onClick = onStory, enabled = !currentChapterNeedsLoading, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.AutoStories, null)
                Spacer(Modifier.width(8.dp))
                Text("从当前章开始互动故事")
            }
            Text(
                if (currentChapterNeedsLoading) "当前章尚无可用正文，先加载或编辑正文后开始互动。" else "进入后可选择分支、扮演身份并推进情节。",
                style = MaterialTheme.typography.bodySmall,
                color = LocalLanghuanUiTokens.current.mutedForeground,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("提取人物并聊天", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(26.dp), color = LocalLanghuanUiTokens.current.card) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = LocalLanghuanUiTokens.current.accent) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(12.dp).size(26.dp), tint = LocalLanghuanUiTokens.current.accentForeground)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("当前小说 · $chapterCount 章正文", fontWeight = FontWeight.Bold)
                            Text("识别别名、性格、能力、关系、经历、对白、世界认知和原文证据", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanUiTokens.current.mutedForeground)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (currentChapterNeedsLoading && (book.sourceId.isNotBlank() || book.sourceBookUrl.isNotBlank())) {
                        Text(
                            if (chapterCount == 0) "尚无可分析正文，先加载当前章；更多章节可在书架离线缓存"
                            else "加载当前章后可开始互动故事；人物提取可使用已加载的正文",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLanghuanUiTokens.current.mutedForeground,
                        )
                        if (!readerLoadError.isNullOrBlank()) {
                            Text(readerLoadError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { onLoadCurrentChapter?.invoke() },
                            enabled = onLoadCurrentChapter != null && currentChapterNeedsLoading && loadingChapterNumber == null,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        ) {
                            Text(when {
                                loadingChapterNumber != null -> "正在加载第 $loadingChapterNumber 章…"
                                !readerLoadError.isNullOrBlank() -> "重试加载当前章"
                                else -> "加载当前章正文"
                            })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onQuick, enabled = !state.distilling && chapterCount > 0, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.Speed, null); Spacer(Modifier.width(6.dp)); Text("快速蒸馏")
                        }
                        FilledTonalButton(onClick = onDeep, enabled = !state.distilling && chapterCount > 0, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.TravelExplore, null); Spacer(Modifier.width(6.dp)); Text("深度蒸馏")
                        }
                    }
                    AnimatedVisibility(
                        state.distilling,
                        enter = expandVertically(LanghuanMotionV31.settle()) + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        LanghuanMotionStatus(state.progressText.ifBlank { "正在蒸馏人物" }, Modifier.padding(top = 14.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChatImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("聊天导入")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("已保存人物", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (state.profiles.isEmpty()) "还没有小说人物，先从当前小说蒸馏" else "点击人物查看完整角色卡、原文证据并开始聊天",
                style = MaterialTheme.typography.bodySmall,
                color = LocalLanghuanUiTokens.current.mutedForeground,
            )
        }
        if (state.profiles.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = LocalLanghuanUiTokens.current.card) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Groups, null, Modifier.size(38.dp), tint = LocalLanghuanUiTokens.current.primary)
                        Text("从小说正文建立人物库", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        Text("快速蒸馏先看主要人物；深度蒸馏逐段覆盖已加载的正文。", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanUiTokens.current.mutedForeground, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        } else {
            itemsIndexed(state.profiles, key = { _, it -> it.id }) { index, profile ->
                Box(Modifier.animateItem().enterOnceV31(profileEnter, profile.id, index)) { NovelCharacterListCardV3(profile, onOpen) }
            }
        }
    }
}

@Composable
private fun NovelCharacterListCardV3(profile: NovelCharacterProfileV3, onOpen: (NovelCharacterProfileV3) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().springClickV31(pressedScale = .97f) { onOpen(profile) },
        shape = RoundedCornerShape(LocalLanghuanUiTokens.current.radiusLg),
        color = LocalLanghuanUiTokens.current.card,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = LocalLanghuanUiTokens.current.accent, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1).ifBlank { "人" }, fontSize = 23.sp, fontWeight = FontWeight.Black) }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(profile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(profile.identity.ifBlank { profile.personality.ifBlank { "原著人物" } }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanUiTokens.current.mutedForeground)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (profile.distillMode == NovelCharacterDistillModeV3.DEEP) "深度" else "快速", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanUiTokens.current.primary)
                    Text("证据 ${profile.evidences.size}", style = MaterialTheme.typography.labelSmall)
                    if (profile.scannedThroughChapter > 0) Text("至 ${profile.scannedThroughChapter} 章", style = MaterialTheme.typography.labelSmall)
                }
            }
            Icon(Icons.Rounded.Description, null, tint = LocalLanghuanUiTokens.current.mutedForeground)
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
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
            Text("角色卡", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = LocalLanghuanUiTokens.current.accent, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text(profile.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    if (profile.aliases.isNotEmpty()) Text(profile.aliases.joinToString(" · "), color = LocalLanghuanUiTokens.current.accentForeground.copy(alpha = .72f), modifier = Modifier.padding(top = 4.dp))
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
                    Surface(shape = RoundedCornerShape(16.dp), color = LocalLanghuanUiTokens.current.card, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.padding(11.dp)) {
                            Text("第 ${item.chapter} 章 · ${item.field.ifBlank { "人物事实" }}", style = MaterialTheme.typography.labelMedium, color = LocalLanghuanUiTokens.current.primary)
                            Text(item.excerpt, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanUiTokens.current.mutedForeground, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Surface(shadowElevation = 5.dp) {
            Button(onClick = onChat, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Chat, null); Spacer(Modifier.width(8.dp)); Text(if (messageCount > 0) "继续聊天" else "开始聊天", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${profile.name}？") },
            text = { Text("人物卡、原文证据和这个人物的聊天记录都会删除。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除", color = LocalLanghuanUiTokens.current.destructive) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NovelFieldV3(title: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(value, modifier = Modifier.padding(top = 6.dp), color = LocalLanghuanUiTokens.current.mutedForeground, lineHeight = 22.sp)
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
                Text("•", color = LocalLanghuanUiTokens.current.primary)
                Text(item, Modifier.padding(start = 7.dp), color = LocalLanghuanUiTokens.current.mutedForeground, lineHeight = 21.sp)
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
    // History already on screen when the chat opens appears at once; only new turns animate.
    val chatEnter = remember(profile.id) { LanghuanEnterRegistryV31().also { r -> messages.forEach { r.markSeenV31(it.id) } } }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Surface(tonalElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回人物") }
                Surface(shape = CircleShape, color = LocalLanghuanUiTokens.current.accent, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1), fontWeight = FontWeight.Black) }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    Text("原著角色 · 覆盖至第 ${profile.scannedThroughChapter} 章", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanUiTokens.current.mutedForeground)
                }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "清空聊天") }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MenuBook, null, Modifier.size(38.dp), tint = LocalLanghuanUiTokens.current.primary)
                        Text("和 ${profile.name} 说点什么", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("人物卡、原文证据、世界认知和聊天记忆会一起约束回复", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanUiTokens.current.mutedForeground, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val mine = message.role == "user"
                Row(
                    Modifier.fillMaxWidth().animateItem().enterOnceV31(chatEnter, message.id, 0, rise = if (mine) 10.dp else 16.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(modifier = Modifier.widthIn(max = 310.dp), shape = RoundedCornerShape(20.dp), color = if (mine) LocalLanghuanUiTokens.current.primary else LocalLanghuanUiTokens.current.muted) {
                        Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = if (mine) LocalLanghuanUiTokens.current.primaryForeground else LocalLanghuanUiTokens.current.foreground, lineHeight = 21.sp)
                    }
                }
            }
            if (busy) item(key = "typing") {
                // A typing bubble in the character's own colour instead of a spinner.
                Surface(Modifier.animateItem(), shape = RoundedCornerShape(20.dp), color = LocalLanghuanUiTokens.current.muted) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        LanghuanTypingDotsV31(LocalLanghuanUiTokens.current.mutedForeground, dot = 6.dp)
                    }
                }
            }
        }
        Surface(shadowElevation = 5.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), placeholder = { Text("和 ${profile.name} 聊天…") }, maxLines = 5)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { val text = input.trim(); if (text.isNotBlank()) { onSend(text); input = "" } }, enabled = input.isNotBlank() && !busy, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Send, "发送")
                }
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
                    Surface(modifier = Modifier.fillMaxWidth().clickable { selected = if (checked) selected - profile.id else selected + profile.id }, shape = RoundedCornerShape(18.dp), color = LocalLanghuanUiTokens.current.card) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Surface(shape = CircleShape, color = LocalLanghuanUiTokens.current.accent, modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1), fontWeight = FontWeight.Black) }
                            }
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text(profile.name, fontWeight = FontWeight.Bold)
                                Text(profile.identity.ifBlank { profile.personality.ifBlank { "原著人物" } }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanUiTokens.current.mutedForeground)
                                Text("原文证据 ${profile.evidences.size} 条", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanUiTokens.current.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected) }, enabled = selected.isNotEmpty()) {
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("保存 ${selected.size} 个")
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
