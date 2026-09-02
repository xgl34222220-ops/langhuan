package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryExchange
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.data.StoryFoundationApplier
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.NovelRouteDecision
import com.xiguli.langhuan.engine.NovelRouteInput
import com.xiguli.langhuan.engine.NovelRouteStatus
import com.xiguli.langhuan.engine.NovelSkillRouter
import com.xiguli.langhuan.engine.PromptAttachment
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.PromptMessage
import com.xiguli.langhuan.engine.ReferenceDnaBindingStore
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStage
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.engine.UniversalAiGateway
import com.xiguli.langhuan.engine.blueprintRunStage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CHAT_SENTINEL = "__CHAT__"
private const val NEW_BOOK_DRAFT_FILE = "new_book_conversation_draft.json"
private const val RESEARCH_CONTEXT_MARKER = "\n\n【琅嬛联网检索资料（隐藏上下文）】"
private val GENRE_PLACEHOLDERS = setOf("小说类型", "类型", "题材", "genre")
private val THEME_PLACEHOLDERS = setOf("主题命题", "主题", "核心主题", "theme")
private const val DEFAULT_THEME = "人在真相、执念与代价之间如何选择"
private val CREATIVE_REFERENCE_KINDS = setOf("STYLE", "KEEP", "TRANSFORM", "AVOID")
private val FACT_REFERENCE_KINDS = setOf("STORY", "STYLE", "KEEP", "TRANSFORM", "AVOID")

private fun isQuestionLike(text: String): Boolean {
    val value = text.trim()
    if (value.isBlank()) return false
    if ('?' in value || '？' in value) return true
    return listOf("什么", "谁", "怎么", "怎样", "为何", "为什么", "多少", "叫啥", "叫什么", "是不是", "有没有", "知道吗", "知道不", "哪一个", "哪个")
        .any { value.contains(it, ignoreCase = true) }
}

private fun isReferenceFactQuestion(text: String): Boolean {
    val value = text.trim()
    if (!isQuestionLike(value)) return false
    val referenceCue = listOf(
        "模板", "参考", "原作", "蒸馏", "这本", "那本", "这部", "那部", "Story DNA", "DNA",
        "他们", "它们", "这几本", "这两本", "这些作品", "那些作品", "作品",
    ).any { value.contains(it, ignoreCase = true) }
    val factCue = listOf("主角", "配角", "人物", "名字", "姓名", "能力", "世界观", "世界", "规则", "设定", "关系", "势力", "地点", "冲突", "谜团", "主题", "剧情", "结局")
        .any { value.contains(it, ignoreCase = true) }
    return referenceCue && factCue
}

@Serializable
data class CreationChatMessage(
    val role: String,
    val text: String,
    val attachments: List<CreationChatAttachment> = emptyList(),
)

@Serializable
data class CreationChatAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val extractedText: String = "",
    val base64Data: String = "",
)

@Serializable
data class NewBookProposal(
    val title: String,
    val genre: String,
    val premise: String,
    val theme: String,
    val targetWords: Int,
    val coreHook: String,
    val coverBrief: String,
    val rationale: String,
    val decisionLedger: String = "",
)

data class NewBookConversationState(
    val messages: List<CreationChatMessage> = listOf(
        CreationChatMessage(
            role = "assistant",
            text = "先别填表。直接告诉我你想写什么：题材、一个画面、一个主角，甚至只说你喜欢哪本书的某种感觉都可以。我会先跟你聊清楚，再一起把书名、简介、核心冲突和整体气质定下来。",
        )
    ),
    val proposal: NewBookProposal? = null,
    val foundation: StoryFoundation? = null,
    val foundationStage: Int = 0,
    val blueprintDirty: Boolean = false,
    val pendingAttachments: List<CreationChatAttachment> = emptyList(),
    val isLoadingAttachments: Boolean = false,
    val isBusy: Boolean = false,
    val busyLabel: String = "",
    val streamingReply: String = "",
    val runEvents: List<RunEvent> = emptyList(),
    val createdStoryId: String? = null,
    val error: String? = null,
    val selectedReferenceTemplateIds: List<String> = emptyList(),
    /** V2：向用户显示上一轮真正检索并送进模型的参考 DNA 数量。 */
    val lastReferenceUsage: String = "",
    /** Novel Skill OS：仅记录本轮路由与执行状态，不属于项目事实，也不持久化进蓝图。 */
    val lastRouteDecision: NovelRouteDecision? = null,
)

@Serializable
private data class NewBookConversationDraft(
    val schemaVersion: Int = 10,
    val messages: List<CreationChatMessage>,
    val proposal: NewBookProposal? = null,
    val foundation: StoryFoundation? = null,
    val foundationStage: Int = 0,
    val blueprintDirty: Boolean = false,
    val pendingAttachments: List<CreationChatAttachment> = emptyList(),
    val selectedReferenceTemplateIds: List<String> = emptyList(),
)

private class NewBookConversationDraftStore(application: Application) {
    private val atomicFile = AtomicFile(File(application.filesDir, NEW_BOOK_DRAFT_FILE))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun restore(): NewBookConversationState? = runCatching {
        val bytes = atomicFile.openRead().use { it.readBytes() }
        if (bytes.isEmpty()) return@runCatching null
        val draft = json.decodeFromString<NewBookConversationDraft>(bytes.toString(Charsets.UTF_8))
        if (draft.messages.isEmpty()) return@runCatching null
        val cleanFoundation = draft.foundation?.sanitizeFoundationPlaceholders()
        NewBookConversationState(
            messages = draft.messages.map(::compactStoredMessage),
            proposal = draft.proposal?.sanitizePlaceholders(),
            foundation = cleanFoundation,
            foundationStage = draft.foundationStage.takeIf { it in 1..3 } ?: inferFoundationStage(cleanFoundation),
            blueprintDirty = draft.blueprintDirty,
            pendingAttachments = draft.pendingAttachments,
            selectedReferenceTemplateIds = draft.selectedReferenceTemplateIds.distinct(),
        )
    }.getOrElse { clear(); null }

    fun persist(state: NewBookConversationState) {
        if (state.messages.size <= 1 && state.proposal == null && state.foundation == null && state.selectedReferenceTemplateIds.isEmpty() && state.pendingAttachments.isEmpty()) {
            clear(); return
        }
        val bytes = json.encodeToString(
            NewBookConversationDraft(
                messages = state.messages.map(::compactStoredMessage),
                proposal = state.proposal?.sanitizePlaceholders(),
                foundation = state.foundation?.sanitizeFoundationPlaceholders(),
                foundationStage = state.foundationStage.coerceIn(0, 3),
                blueprintDirty = state.blueprintDirty,
                pendingAttachments = state.pendingAttachments,
                selectedReferenceTemplateIds = state.selectedReferenceTemplateIds.distinct(),
            )
        ).toByteArray(Charsets.UTF_8)
        val output = runCatching { atomicFile.startWrite() }.getOrNull() ?: return
        try { output.write(bytes); output.fd.sync(); atomicFile.finishWrite(output) }
        catch (_: Throwable) { atomicFile.failWrite(output) }
    }

    fun clear() = runCatching { atomicFile.delete() }.let { Unit }
    private fun compactStoredMessage(message: CreationChatMessage): CreationChatMessage =
        if (message.role == "user") message.copy(text = message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()) else message
}

class NewBookConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val foundationApplier = StoryFoundationApplier(application)
    private val draftStore = NewBookConversationDraftStore(application)
    private val referenceReportStore = ReferenceDistillationReportStore(application)
    private val referenceBindings = ReferenceDnaBindingStore(application)
    private val _state = MutableStateFlow(draftStore.restore() ?: NewBookConversationState())
    val state: StateFlow<NewBookConversationState> = _state.asStateFlow()
    private var activeProviderId: String? = null
    private var foundationJob: kotlinx.coroutines.Job? = null
    @Volatile private var suppressDraftPersistence = false

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                activeProviderId = providers.firstOrNull { it.isDefault }?.id ?: providers.firstOrNull()?.id
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.collect { current ->
                if (suppressDraftPersistence) draftStore.clear()
                else if (current.streamingReply.isBlank()) draftStore.persist(current)
            }
        }
    }

    fun send(text: String) {
        val clean = text.trim()
        val before = _state.value
        if ((clean.isBlank() && before.pendingAttachments.isEmpty()) || before.isBusy || before.isLoadingAttachments) return
        val userText = clean.ifBlank { defaultAttachmentInstruction(before.pendingAttachments) }
        val history = before.messages + CreationChatMessage("user", userText, before.pendingAttachments)
        val plainInstruction = userText.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        val referenceQuestion = isReferenceFactQuestion(plainInstruction) && before.selectedReferenceTemplateIds.isNotEmpty()
        val routeDecision = NovelSkillRouter.route(
            NovelRouteInput(
                message = plainInstruction,
                attachmentPurposes = before.pendingAttachments.map(::attachmentPurpose),
                hasConversationHistory = before.messages.any { it.role == "user" },
                hasFoundation = before.foundation != null,
                hasSelectedReferences = before.selectedReferenceTemplateIds.isNotEmpty(),
                referenceFactQuestion = referenceQuestion,
            )
        )
        val allowedKinds = if (referenceQuestion) FACT_REFERENCE_KINDS else CREATIVE_REFERENCE_KINDS
        val referenceContext = referenceReportStore.searchContext(
            selectedTaskIds = before.selectedReferenceTemplateIds,
            query = plainInstruction,
            maxChars = 8_500,
            maxItemsPerReport = 18,
            allowedKinds = allowedKinds,
        )
        val usage = referenceReportStore.usage(before.selectedReferenceTemplateIds, plainInstruction, 18, allowedKinds)
        val runDetail = listOf(routeDecision.compactSummary, usage.label).filter(String::isNotBlank).joinToString(" · ")
        _state.update {
            it.copy(
                messages = history,
                pendingAttachments = emptyList(),
                isBusy = true,
                busyLabel = when {
                    before.selectedReferenceTemplateIds.isNotEmpty() -> "${routeDecision.intent.label} · 正在从所选参考 DNA 检索本轮相关内容……"
                    routeDecision.capabilities.isNotEmpty() -> "${routeDecision.intent.label} · 已路由 ${routeDecision.capabilities.size} 个能力……"
                    else -> "AI 正在继续和你聊这本书……"
                },
                streamingReply = "",
                lastReferenceUsage = usage.label,
                lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.RUNNING),
                runEvents = listOf(RunEvent(RunStage.CREATION_CHAT, RunStatus.RUNNING, runDetail.ifBlank { "模型正在流式回复" })),
                error = null,
            )
        }

        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "未配置 AI 服务")
                _state.update {
                    it.copy(
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),
                        error = "请先在设置里添加并启用一个 AI 服务",
                    )
                }
                return@launch
            }
            runCatching {
                NewBookConversationEngine(gateway).reply(
                    messages = history,
                    currentProposal = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders(),
                    referenceContext = referenceContext,
                    routeDecision = routeDecision,
                    onDelta = { partial -> _state.update { it.copy(streamingReply = partial) } },
                )
            }.onSuccess { turn ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.SUCCESS, runDetail.ifBlank { "回复完成" })
                _state.update {
                    it.copy(
                        messages = it.messages + CreationChatMessage("assistant", turn.reply),
                        proposal = if (referenceQuestion) before.proposal else turn.proposal?.sanitizePlaceholders() ?: it.proposal,
                        foundation = if (referenceQuestion) before.foundation else it.foundation,
                        foundationStage = if (referenceQuestion) before.foundationStage else it.foundationStage,
                        blueprintDirty = if (referenceQuestion) it.blueprintDirty else it.blueprintDirty || (before.foundation != null && !isQuestionLike(plainInstruction)),
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                        lastReferenceUsage = usage.label,
                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.SUCCESS),
                    )
                }
            }.onFailure { error ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "${routeDecision.intent.label} · ${error.message.orEmpty()}")
                _state.update {
                    it.copy(
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                        lastRouteDecision = routeDecision.copy(status = NovelRouteStatus.FAILED),
                        error = friendlyAiError(error, if (referenceQuestion) "模板事实读取失败" else "AI 构思失败"),
                    )
                }
            }
        }
    }

    fun addConversationAttachments(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isBusy || _state.value.isLoadingAttachments) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingAttachments = true, error = null) }
            val results = withContext(Dispatchers.IO) { uris.map { runCatching { readCreationAttachment(it) } } }
            val imported = results.mapNotNull { it.getOrNull() }
            val failures = results.mapNotNull { it.exceptionOrNull()?.message }.distinct()
            _state.update {
                it.copy(
                    pendingAttachments = (it.pendingAttachments + imported).distinctBy { item -> item.fileName to (item.extractedText.ifBlank { item.base64Data }.hashCode()) },
                    isLoadingAttachments = false,
                    error = failures.takeIf { messages -> messages.isNotEmpty() }?.joinToString("\n"),
                )
            }
        }
    }

    fun removePendingAttachment(id: String) {
        if (_state.value.isBusy || _state.value.isLoadingAttachments) return
        _state.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { item -> item.id == id }) }
    }

    private fun readCreationAttachment(uri: Uri): CreationChatAttachment {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        var displayName = "附件"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) { displayName = cursor.getString(0).orEmpty().ifBlank { "附件" }; declaredSize = cursor.getLong(1) }
        }
        require(declaredSize <= MAX_CHAT_ATTACHMENT_BYTES || declaredSize < 0) { "$displayName 超过 12 MB。长篇小说请使用“参考蒸馏”，普通聊天附件需控制在 12 MB 内。" }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取 $displayName")
        require(bytes.isNotEmpty()) { "$displayName 是空文件" }
        require(bytes.size <= MAX_CHAT_ATTACHMENT_BYTES) { "$displayName 超过 12 MB。长篇小说请使用“参考蒸馏”，普通聊天附件需控制在 12 MB 内。" }
        val lower = displayName.lowercase()
        val mime = canonicalAttachmentMime(lower, resolver.getType(uri).orEmpty())
        val extracted = when {
            lower.endsWith(".epub") -> StoryExchange.`import`(displayName, bytes).chapters.joinToString("\n\n") { "${it.title}\n${it.content}" }
            lower.endsWith(".docx") -> extractDocxText(bytes)
            mime.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".json") || lower.endsWith(".csv") -> bytes.toString(Charsets.UTF_8)
            mime == "application/pdf" || mime.startsWith("image/") -> ""
            else -> error("暂不支持 $displayName；可上传 TXT、Markdown、JSON、CSV、EPUB、DOCX、PDF 或图片。")
        }.trim()
        require(extracted.isNotBlank() || mime == "application/pdf" || mime.startsWith("image/")) { "$displayName 没有解析到可读内容" }
        return CreationChatAttachment(UUID.randomUUID().toString(), displayName, mime, extracted, if (extracted.isBlank()) Base64.getEncoder().encodeToString(bytes) else "")
    }

    fun syncConversationProposal() {
        val before = _state.value
        if (before.isBusy || before.isLoadingAttachments || before.messages.none { it.role == "user" }) return
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) { _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }; return@launch }
            val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders() ?: defaultProposal()
            _state.update { it.copy(isBusy = true, busyLabel = "正在把当前会谈整理为建书方案……", runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "合并用户最新决定，不自动生成蓝图")), error = null) }
            runCatching { ProposalConsolidator(gateway).consolidate(baseline, before.messages) }
                .onSuccess { proposal ->
                    emitRun(RunStage.PROPOSAL_SYNC, RunStatus.SUCCESS, "当前会谈已整理成方案缓存")
                    _state.update { it.copy(proposal = proposal.sanitizePlaceholders(), blueprintDirty = before.foundation != null, isBusy = false, busyLabel = "", error = null) }
                }
                .onFailure { error ->
                    emitRun(RunStage.PROPOSAL_SYNC, RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "整理当前方案失败")) }
                }
        }
    }

    fun generateFoundation(regenerate: Boolean = false) {
        val before = _state.value
        val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders()
            ?: if (before.messages.any { it.role == "user" }) defaultProposal() else return
        if (before.isBusy) return
        val resumeStage = if (regenerate || before.blueprintDirty) 0 else before.foundationStage.coerceIn(0, 2)
        val resumeFoundation = if (regenerate) null else before.foundation?.sanitizeFoundationPlaceholders()
        foundationJob = viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) { _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }; return@launch }
            _state.update { it.copy(proposal = baseline, isBusy = true, blueprintDirty = before.blueprintDirty, busyLabel = "正在把整段会谈的最新决定合并为最终方案……", runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "先把会谈最新决定锁成蓝图输入")), error = null) }
            val instruction = if (regenerate) {
                "以会谈和用户上传作品设定为硬约束，重新整理并补全蓝图。只重做 AI 补充部分；附件明确的人物、规则、势力、卷数、卷序、主线节点和终局不得改动。"
            } else {
                "以会谈最新决定和用户上传作品设定为唯一准绳，把当前方案扩展成可写作蓝图。附件原文是硬约束；AI 只能补缺口，禁止删卷、并卷、把类别当人物或偷换既定规则。"
            }
            runCatching {
                val refreshed = runCatching { ProposalConsolidator(gateway).consolidate(baseline, before.messages) }.getOrElse { baseline }
                emitRun(RunStage.PROPOSAL_SYNC, RunStatus.SUCCESS, "方案合并完成")
                _state.update { it.copy(proposal = refreshed, busyLabel = if (resumeStage > 0) "最新方案已合并，检查蓝图断点 $resumeStage/3……" else "最新方案已合并，开始分阶段生成建书蓝图……") }
                val blueprintQuery = listOf(refreshed.genre, refreshed.premise, refreshed.theme, refreshed.coreHook, conversationTranscript(before.messages.takeLast(8), false)).joinToString(" ")
                val referenceContext = referenceReportStore.searchContext(
                    before.selectedReferenceTemplateIds,
                    blueprintQuery,
                    maxChars = 10_000,
                    maxItemsPerReport = 22,
                    allowedKinds = CREATIVE_REFERENCE_KINDS,
                )
                ProgressiveFoundationEngine(gateway).build(
                    proposal = refreshed,
                    messages = before.messages,
                    current = resumeFoundation,
                    instruction = instruction,
                    referenceContext = referenceContext,
                    resumeStage = resumeStage,
                    onStage = { label ->
                        val parsedStage = when { label.contains("3/3") -> 3; label.contains("2/3") -> 2; label.contains("1/3") -> 1; else -> (_state.value.foundationStage + 1).coerceIn(1, 3) }
                        emitRun(blueprintRunStage(parsedStage), RunStatus.RUNNING, label)
                        _state.update { it.copy(busyLabel = label) }
                    },
                    onCheckpoint = { stage, checkpoint ->
                        emitRun(blueprintRunStage(stage), RunStatus.SUCCESS, "第 $stage/3 阶段已保存检查点，可断点续跑")
                        val cleanCheckpoint = checkpoint.sanitizeFoundationPlaceholders()
                        _state.update { it.copy(foundation = cleanCheckpoint, proposal = cleanCheckpoint.toProposal(), foundationStage = stage, blueprintDirty = before.blueprintDirty) }
                    },
                )
            }.onSuccess { foundation ->
                val cleanFoundation = foundation.sanitizeFoundationPlaceholders()
                _state.update { it.copy(foundation = cleanFoundation, proposal = cleanFoundation.toProposal(), foundationStage = inferFoundationStage(cleanFoundation).coerceAtLeast(1), blueprintDirty = false, messages = it.messages + CreationChatMessage("assistant", "当前有效蓝图已经保存。核心蓝图完成后即可正式建书；章纲或伏笔没补完也不会再把整本书锁死。"), isBusy = false, busyLabel = "") }
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    emitRun(blueprintRunStage((_state.value.foundationStage + 1).coerceIn(1, 3)), RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "建书蓝图生成失败")) }
                }
            }
            foundationJob = null
        }
    }

    fun createCurrentFoundation() {
        var snapshot = _state.value
        val runningFoundation = foundationJob?.takeIf { it.isActive }
        if (snapshot.isBusy) {
            if (runningFoundation == null) { _state.update { it.copy(error = "AI 还在处理当前聊天，请等这一轮回复结束后再正式建书。") }; return }
            runningFoundation.cancel(); foundationJob = null
            _state.update { it.copy(isBusy = false, busyLabel = "", error = null) }
            snapshot = _state.value
        }
        val foundation = snapshot.foundation?.sanitizeFoundationPlaceholders() ?: return
        if (snapshot.blueprintDirty) { _state.update { it.copy(error = "你在聊天里又改了要求，当前蓝图还没同步最新决定。请先同步当前聊天，再正式建书。") }; return }
        val stage = maxOf(snapshot.foundationStage, inferFoundationStage(foundation))
        if (stage < 1) { _state.update { it.copy(error = "核心蓝图还没有形成。至少完成世界规则、核心人物和分卷后才能正式建书。") }; return }
        val selectedReferences = snapshot.selectedReferenceTemplateIds

        viewModelScope.launch {
            _state.update { it.copy(foundation = foundation, proposal = foundation.toProposal(), foundationStage = stage, blueprintDirty = false, isBusy = true, runEvents = listOf(RunEvent(RunStage.CREATE_BOOK, RunStatus.RUNNING, "把已确认核心蓝图写入正式项目结构")), busyLabel = if (stage < 3) "正在用当前有效核心蓝图建书；未完成的章纲/伏笔可稍后补齐……" else "正在把蓝图写入小说圣经、三级大纲和长期记忆……", error = null) }
            runCatching { foundationApplier.create(foundation) }
                .onSuccess { created ->
                    referenceBindings.bind(created.snapshot.novel.id, selectedReferences)
                    val bindingSummary = referenceBindings.summary(created.snapshot.novel.id)
                    emitRun(RunStage.CREATE_BOOK, RunStatus.SUCCESS, "《${created.snapshot.novel.title}》项目已创建 · ${bindingSummary.label}")
                    suppressDraftPersistence = true
                    _state.update {
                        it.copy(
                            isBusy = false,
                            busyLabel = "",
                            createdStoryId = created.snapshot.novel.id,
                            messages = it.messages + CreationChatMessage(
                                "assistant",
                                (if (stage >= 3) "《${created.snapshot.novel.title}》已经正式建好。完整蓝图已经进入项目结构与长期记忆。" else "《${created.snapshot.novel.title}》已经正式建好。核心世界、人物和分卷已经写入项目；未完成的详细章纲/伏笔不会阻止开书，可以在项目里继续补齐。") +
                                    if (bindingSummary.count > 0) " 你选择的参考 DNA 已绑定到这本作品，后续场景、正文与主编会按任务持续检索。" else "",
                            ),
                        )
                    }
                    draftStore.clear()
                }
                .onFailure { error ->
                    emitRun(RunStage.CREATE_BOOK, RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = error.message ?: "正式建书失败") }
                }
        }
    }

    fun reset() { suppressDraftPersistence = false; draftStore.clear(); _state.value = NewBookConversationState() }
    fun consumeCreatedStory() { _state.update { it.copy(createdStoryId = null) } }

    fun setReferenceTemplateIds(ids: List<String>) {
        val valid = referenceReportStore.listReports().map { it.taskId }.toSet()
        val next = ids.filter(valid::contains).distinct()
        _state.update {
            val changed = next != it.selectedReferenceTemplateIds
            it.copy(selectedReferenceTemplateIds = next, foundationStage = if (changed && it.foundation != null) 0 else it.foundationStage, blueprintDirty = it.blueprintDirty || (changed && it.foundation != null), lastReferenceUsage = if (changed) "" else it.lastReferenceUsage)
        }
    }

    private fun emitRun(stage: RunStage, status: RunStatus, detail: String = "") { _state.update { state -> state.copy(runEvents = (state.runEvents + RunEvent(stage, status, detail)).takeLast(72)) } }
    private suspend fun activeGateway(): AiGateway? { val id = activeProviderId ?: return null; return repository.providerConfig(id)?.let(::UniversalAiGateway) }

    private fun defaultProposal() = NewBookProposal("未命名", "未分类", "尚未整理", DEFAULT_THEME, 500_000, "待整理", "", "")
}

private data class ConversationTurn(val reply: String, val proposal: NewBookProposal? = null)

private class NewBookConversationEngine(private val gateway: AiGateway) {
    suspend fun reply(
        messages: List<CreationChatMessage>,
        currentProposal: NewBookProposal? = null,
        referenceContext: String = "",
        routeDecision: NovelRouteDecision,
        onDelta: (String) -> Unit = {},
    ): ConversationTurn {
        val latest = messages.lastOrNull { it.role == "user" }?.text?.substringBefore(RESEARCH_CONTEXT_MARKER)?.trim().orEmpty()
        val hiddenContext = buildString {
            appendLine(routeDecision.systemGuidance())
            appendLine()
            currentProposal?.let { appendLine(proposalContext(it)); appendLine() }
            if (referenceContext.isNotBlank()) { appendLine(referenceContext); appendLine() }
        }
        val response = gateway.generateTextStreaming(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书创作搭档。第一职责是像正常可靠的 AI 助手一样理解用户当前这句话并自然回应，而不是把每轮聊天强行变成表格、JSON、方案卡或自动工作流。

                    对话原则：
                    1. 优先回答用户真正问的内容。简单问题简洁回答；复杂设定、长文件分析、剧情推演可以充分展开，不机械限字。
                    2. 承接完整多轮上下文。后出现的明确决定覆盖旧决定；“他/他们/这本/前面那几本”等按最近上下文理解，不把代词当新实体。
                    3. 用户上传的作品设定、世界观、大纲和人物文件属于项目资料。先读文件，再结合实际名称、规则、人物和分卷回答。原文事实不得擅改；新增想法标成建议或待确认。
                    4. 不要因为用户提到“小说、作品、资料、参考、融合”就自行联网。只有页面联网工具明确附带网页研究上下文时才作为辅助证据。
                    5. 普通聊天不自动生成/修改建书方案、蓝图、简介，不输出内部状态字段，也不要要求用户填表。用户满意时会主动整理方案/生成蓝图/正式建书。
                    6. 可以主动指出设定漏洞、人物动机、规则闭环、节奏和更好的方案，但必须区分“原文事实”和“建议”。
                    7. 当上下文出现【本轮主动检索的参考 DNA】时，必须先利用真正相关的命中条目再回答，不能把参考 DNA 当成可有可无的背景。用户问原作事实时可直接依据 STORY；讨论用户自己的新书时只能迁移 STYLE / KEEP / TRANSFORM 并遵守 AVOID，禁止照搬原作专名、具体能力规则、独特谜底和剧情骨架。
                    8. 多本参考同时选中时，要综合它们的共同机制与差异，不要默认只看第一本；用户使用“他们/这几本”时按已选参考和对话上下文解析。
                    9. 不要用“如果你愿意我可以……”空泛收尾。该分析就分析，该给方案就直接给方案。

                    $hiddenContext
                """.trimIndent(),
                user = latest,
                messages = conversationPromptMessages(messages),
                attachments = messagesPromptAttachments(messages.takeLast(1)),
                jsonMode = false,
            ),
            onDelta = onDelta,
        ).trim()
        return ConversationTurn(response.ifBlank { "我在。继续按你刚才的设定往下聊。" })
    }

    private fun proposalContext(proposal: NewBookProposal): String = buildString {
        appendLine("【当前方案缓存：若与后续用户决定冲突，必须覆盖】")
        appendLine("书名：${proposal.title}"); appendLine("类型：${proposal.genre}"); appendLine("简介：${proposal.premise}"); appendLine("主题：${proposal.theme}")
        appendLine("目标字数：${proposal.targetWords}"); appendLine("核心钩子：${proposal.coreHook}"); appendLine("内部策划：${proposal.rationale}")
        if (proposal.decisionLedger.isNotBlank()) { appendLine("确认事实账本："); appendLine(proposal.decisionLedger) }
    }
}

internal fun blueprintDirtyAfterConversation(alreadyDirty: Boolean, hasFoundation: Boolean, proposalUpdated: Boolean): Boolean = alreadyDirty || (hasFoundation && proposalUpdated)

private fun StoryFoundation.toProposal() = NewBookProposal(title, sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"), premise, sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME), targetWords, coreHook, coverBrief, storyPromise, creationBrief)
private fun NewBookProposal.sanitizePlaceholders(): NewBookProposal = copy(genre = sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"), theme = sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME))
private fun StoryFoundation.sanitizeFoundationPlaceholders(): StoryFoundation = copy(genre = sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"), theme = sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME))
private fun inferFoundationStage(foundation: StoryFoundation?): Int {
    foundation ?: return 0
    if (foundation.foreshadowing.isNotEmpty()) return 3
    if ((foundation.volumes.firstOrNull { it.order == 1 }?.chapters?.size ?: 0) >= 6) return 2
    if (foundation.bible.isNotEmpty() || foundation.characters.isNotEmpty() || foundation.volumes.isNotEmpty()) return 1
    return 0
}
private fun sanitizeMetaValue(value: String, placeholders: Set<String>, fallback: String): String { val clean = value.trim(); return if (clean.isBlank() || placeholders.any { clean.equals(it, true) }) fallback else clean }

private fun conversationPromptMessages(messages: List<CreationChatMessage>): List<PromptMessage> {
    val firstUser = messages.indexOfFirst { it.role == "user" }; if (firstUser < 0) return emptyList()
    val relevant = messages.drop(firstUser); val lastUser = relevant.indexOfLast { it.role == "user" }
    return relevant.mapIndexedNotNull { index, message ->
        var text = message.text
        if (message.role == "user" && index != lastUser) text = text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()
        val attachments = attachmentContext(message.attachments); val content = if (attachments.isBlank()) text else "$text\n$attachments"
        content.trim().takeIf(String::isNotBlank)?.let { PromptMessage(if (message.role == "assistant") "assistant" else "user", it) }
    }
}

private fun conversationTranscript(messages: List<CreationChatMessage>, keepLatestResearch: Boolean): String {
    val lastUser = messages.indexOfLast { it.role == "user" }
    return messages.mapIndexed { index, message ->
        var text = message.text
        if (message.role == "user" && (!keepLatestResearch || index != lastUser)) text = text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()
        text = if (message.role == "user" && keepLatestResearch && index == lastUser) {
            val plain = text.substringBefore(RESEARCH_CONTEXT_MARKER); val research = text.substringAfter(RESEARCH_CONTEXT_MARKER, "")
            if (research.isBlank()) plain else "$plain$RESEARCH_CONTEXT_MARKER\n$research"
        } else text
        val attachmentContext = attachmentContext(message.attachments); val combined = if (attachmentContext.isBlank()) text else "$text\n$attachmentContext"
        if (message.role == "user") "用户：$combined" else "琅嬛：$combined"
    }.joinToString("\n")
}

internal fun attachmentContext(attachments: List<CreationChatAttachment>): String = buildString {
    attachments.forEach { attachment ->
        appendLine("【用户附件：${attachment.fileName}｜${attachment.mimeType}｜识别用途=${attachmentPurpose(attachment)}】")
        if (attachment.extractedText.isNotBlank()) appendLine(attachment.extractedText) else appendLine("附件以原生文件形式随请求发送，请直接读取其内容。")
        appendLine("【附件结束】")
    }
}.trim()

internal fun defaultAttachmentInstruction(attachments: List<CreationChatAttachment>): String {
    val purposes = attachments.map(::attachmentPurpose).distinct()
    return if ("作品设定" in purposes) "请自动识别并整理我上传的作品设定：明确写出的内容同步进当前方案；检查规则闭环、人物动机、能力代价、主线推进、谜底释放和分卷升级，给出具体优化完善方案。你新增或改动的设定先标为待确认，不要擅自覆盖原文。"
    else "请先识别我上传的文件属于设定、人物、大纲、正文还是参考资料，再读取其中内容，结合当前会谈给出具体分析和可执行的优化建议。"
}

internal fun attachmentPurpose(attachment: CreationChatAttachment): String {
    val name = attachment.fileName.lowercase(); val text = attachment.extractedText
    return when {
        listOf("作品设定", "世界观", "设定集", "故事圣经", "小说圣经").any(name::contains) || listOf("世界规则", "故事梗概", "核心设定", "势力", "分卷大纲").count(text::contains) >= 2 -> "作品设定"
        listOf("大纲", "章纲", "卷纲").any(name::contains) -> "故事大纲"
        listOf("人物", "角色", "人设").any(name::contains) -> "人物设定"
        listOf("正文", "章节", "第1章", "第一章").any(name::contains) -> "小说正文"
        attachment.mimeType.startsWith("image/") -> "图片资料"
        attachment.mimeType == "application/pdf" -> "PDF资料"
        else -> "参考资料"
    }
}

internal fun messagesPromptAttachments(messages: List<CreationChatMessage>): List<PromptAttachment> = messages.flatMap { message ->
    message.attachments.mapNotNull { attachment -> attachment.base64Data.takeIf(String::isNotBlank)?.let { PromptAttachment(attachment.fileName, attachment.mimeType, it) } }
}

internal fun extractDocxText(bytes: ByteArray): String {
    var xml = ""
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; if (entry.name == "word/document.xml") { xml = zip.readBytes().toString(Charsets.UTF_8); break } } }
    return xml.replace(Regex("(?i)</w:p>"), "\n").replace(Regex("(?i)<w:tab[^>]*/>"), "\t").replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").replace(Regex("\n{3,}"), "\n\n").trim()
}

private fun mimeFromName(lower: String): String = when {
    lower.endsWith(".pdf") -> "application/pdf"; lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; lower.endsWith(".epub") -> "application/epub+zip"
    lower.endsWith(".json") -> "application/json"; lower.endsWith(".csv") -> "text/csv"; lower.endsWith(".md") || lower.endsWith(".markdown") -> "text/markdown"
    lower.endsWith(".png") -> "image/png"; lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"; lower.endsWith(".webp") -> "image/webp"; else -> "text/plain"
}
private fun canonicalAttachmentMime(lower: String, reported: String): String = when {
    lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".epub") || lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> mimeFromName(lower)
    reported.isNotBlank() -> reported; else -> mimeFromName(lower)
}
private const val MAX_CHAT_ATTACHMENT_BYTES = 12 * 1024 * 1024
private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty(); val timeout = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return if (timeout) "$fallback：AI 服务或中转站主动返回了超时/断开。琅嬛本身没有设置生成倒计时，也没有因为等待时间过长主动终止请求。" else message.ifBlank { fallback }
}
