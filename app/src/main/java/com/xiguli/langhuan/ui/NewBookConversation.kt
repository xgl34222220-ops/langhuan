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
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.PromptAttachment
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore
import com.xiguli.langhuan.engine.UniversalAiGateway
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
    val referenceCue = listOf("模板", "参考", "原作", "蒸馏", "这本", "那本", "这部", "那部", "Story DNA", "DNA")
        .any { value.contains(it, ignoreCase = true) }
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
    val createdStoryId: String? = null,
    val error: String? = null,
    val selectedReferenceTemplateIds: List<String> = emptyList(),
)

@Serializable
private data class NewBookConversationDraft(
    val schemaVersion: Int = 9,
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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
    }.getOrElse {
        clear()
        null
    }

    fun persist(state: NewBookConversationState) {
        if (state.messages.size <= 1 && state.proposal == null && state.foundation == null &&
            state.selectedReferenceTemplateIds.isEmpty() && state.pendingAttachments.isEmpty()
        ) {
            clear()
            return
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
        try {
            output.write(bytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (_: Throwable) {
            atomicFile.failWrite(output)
        }
    }

    fun clear() = runCatching { atomicFile.delete() }.let { Unit }

    private fun compactStoredMessage(message: CreationChatMessage): CreationChatMessage =
        if (message.role == "user") {
            message.copy(text = message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd())
        } else message
}

class NewBookConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val foundationApplier = StoryFoundationApplier(application)
    private val draftStore = NewBookConversationDraftStore(application)
    private val referenceReportStore = ReferenceDistillationReportStore(application)
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
                if (suppressDraftPersistence) draftStore.clear() else draftStore.persist(current)
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
        _state.update {
            it.copy(
                messages = history,
                pendingAttachments = emptyList(),
                isBusy = true,
                busyLabel = when {
                    referenceQuestion -> "正在读取所选模板的 Story DNA 事实……"
                    else -> "AI 正在继续和你聊这本书……"
                },
                error = null,
            )
        }

        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(isBusy = false, busyLabel = "", error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }

            if (referenceQuestion) {
                runCatching {
                    NewBookConversationEngine(gateway).reply(
                        messages = history,
                        currentProposal = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders(),
                        referenceContext = referenceReportStore.promptContext(before.selectedReferenceTemplateIds),
                    )
                }.onSuccess { turn ->
                    _state.update {
                        it.copy(
                            messages = it.messages + CreationChatMessage("assistant", turn.reply),
                            proposal = before.proposal,
                            foundation = before.foundation,
                            foundationStage = before.foundationStage,
                            isBusy = false,
                            busyLabel = "",
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "模板事实读取失败")) }
                }
                return@launch
            }

            runCatching {
                NewBookConversationEngine(gateway).reply(
                    messages = history,
                    currentProposal = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders(),
                    referenceContext = referenceReportStore.promptContext(before.selectedReferenceTemplateIds),
                )
            }.onSuccess { turn ->
                _state.update {
                    it.copy(
                        messages = it.messages + CreationChatMessage("assistant", turn.reply),
                        proposal = turn.proposal?.sanitizePlaceholders() ?: it.proposal,
                        blueprintDirty = blueprintDirtyAfterConversation(
                            alreadyDirty = it.blueprintDirty,
                            hasFoundation = before.foundation != null,
                            proposalUpdated = turn.proposal != null,
                        ),
                        isBusy = false,
                        busyLabel = "",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "AI 构思失败")) }
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
                    pendingAttachments = (it.pendingAttachments + imported).distinctBy { item ->
                        item.fileName to (item.extractedText.ifBlank { item.base64Data }.hashCode())
                    },
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
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0).orEmpty().ifBlank { "附件" }
                declaredSize = cursor.getLong(1)
            }
        }
        require(declaredSize <= MAX_CHAT_ATTACHMENT_BYTES || declaredSize < 0) {
            "$displayName 超过 12 MB。长篇小说请使用“参考蒸馏”，普通聊天附件需控制在 12 MB 内。"
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取 $displayName")
        require(bytes.isNotEmpty()) { "$displayName 是空文件" }
        require(bytes.size <= MAX_CHAT_ATTACHMENT_BYTES) {
            "$displayName 超过 12 MB。长篇小说请使用“参考蒸馏”，普通聊天附件需控制在 12 MB 内。"
        }
        val lower = displayName.lowercase()
        val reportedMime = resolver.getType(uri).orEmpty()
        val mime = canonicalAttachmentMime(lower, reportedMime)
        val extracted = when {
            lower.endsWith(".epub") -> StoryExchange.`import`(displayName, bytes).chapters
                .joinToString("\n\n") { "${it.title}\n${it.content}" }
            lower.endsWith(".docx") -> extractDocxText(bytes)
            mime.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md") ||
                lower.endsWith(".markdown") || lower.endsWith(".json") || lower.endsWith(".csv") ->
                bytes.toString(Charsets.UTF_8)
            mime == "application/pdf" || mime.startsWith("image/") -> ""
            else -> error("暂不支持 $displayName；可上传 TXT、Markdown、JSON、CSV、EPUB、DOCX、PDF 或图片。")
        }.trim()
        require(extracted.isNotBlank() || mime == "application/pdf" || mime.startsWith("image/")) {
            "$displayName 没有解析到可读内容"
        }
        return CreationChatAttachment(
            id = UUID.randomUUID().toString(),
            fileName = displayName,
            mimeType = mime,
            extractedText = extracted,
            base64Data = if (extracted.isBlank()) Base64.getEncoder().encodeToString(bytes) else "",
        )
    }

    fun generateFoundation(regenerate: Boolean = false) {
        val before = _state.value
        val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders() ?: return
        if (before.isBusy) return
        val resumeStage = if (regenerate || before.blueprintDirty) 0 else before.foundationStage.coerceIn(0, 2)
        val resumeFoundation = if (regenerate) null else before.foundation?.sanitizeFoundationPlaceholders()
        foundationJob = viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            _state.update {
                it.copy(
                    proposal = baseline,
                    isBusy = true,
                    blueprintDirty = before.blueprintDirty,
                    busyLabel = "正在把整段会谈的最新决定合并为最终方案……",
                    error = null,
                )
            }
            val instruction = if (regenerate) {
                "以会谈里最后确认的决定为准，重新设计一套明显不同但更自洽的角色、规则、分卷结构和前期章节路线。任何已被用户否定或替换的旧方案都不得复用。"
            } else {
                "以会谈里最后确认的决定为唯一准绳，把当前最新新书方案扩展成可直接开始长篇写作的完整建书蓝图。旧简介、旧能力、旧冲突若已被后续决定替换，禁止回滚。"
            }
            runCatching {
                val refreshed = runCatching {
          kotlinx.coroutines.withTimeout(75_000L) {
              ProposalConsolidator(gateway).consolidate(
                  current = baseline,
                  messages = before.messages,
              )
          }
      }.getOrElse { baseline }
                _state.update {
                    it.copy(
                        proposal = refreshed,
                        busyLabel = if (resumeStage > 0) {
                            "最新方案已合并，检查蓝图断点 $resumeStage/3……"
                        } else {
                            "最新方案已合并，开始分阶段生成建书蓝图……"
                        },
                    )
                }
                ProgressiveFoundationEngine(gateway).build(
                    proposal = refreshed,
                    messages = before.messages,
                    current = resumeFoundation,
                    instruction = instruction,
                    referenceContext = referenceReportStore.promptContext(before.selectedReferenceTemplateIds),
                    resumeStage = resumeStage,
                    onStage = { label -> _state.update { it.copy(busyLabel = label) } },
                    onCheckpoint = { stage, checkpoint ->
                        val cleanCheckpoint = checkpoint.sanitizeFoundationPlaceholders()
                        _state.update {
                            it.copy(
                            foundation = cleanCheckpoint,
                            proposal = cleanCheckpoint.toProposal(),
                            foundationStage = stage,
                            blueprintDirty = before.blueprintDirty,
                            )
                        }
                    },
                )
            }.onSuccess { foundation ->
                val cleanFoundation = foundation.sanitizeFoundationPlaceholders()
                _state.update {
                    it.copy(
                        foundation = cleanFoundation,
                        proposal = cleanFoundation.toProposal(),
                        foundationStage = inferFoundationStage(cleanFoundation).coerceAtLeast(1),
                        blueprintDirty = false,
                        messages = it.messages + CreationChatMessage(
                            "assistant",
                            "当前有效蓝图已经保存。核心蓝图完成后即可正式建书；章纲或伏笔没补完也不会再把整本书锁死。",
                        ),
                        isBusy = false,
                        busyLabel = "",
                    )
                }
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
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
        if (runningFoundation == null) {
            _state.update { it.copy(error = "AI 还在处理当前聊天，请等这一轮回复结束后再正式建书。") }
            return
        }
        runningFoundation.cancel()
        foundationJob = null
        _state.update { it.copy(isBusy = false, busyLabel = "", error = null) }
        snapshot = _state.value
    }

    val foundation = snapshot.foundation?.sanitizeFoundationPlaceholders() ?: return
    if (snapshot.blueprintDirty) {
        _state.update { it.copy(error = "你在聊天里又改了要求，当前蓝图还没同步最新决定。请先同步当前聊天，再正式建书。") }
        return
    }
    val stage = maxOf(snapshot.foundationStage, inferFoundationStage(foundation))
    if (stage < 1) {
        _state.update { it.copy(error = "核心蓝图还没有形成。至少完成世界规则、核心人物和分卷后才能正式建书。") }
        return
    }

    viewModelScope.launch {
        _state.update {
            it.copy(
                foundation = foundation,
                proposal = foundation.toProposal(),
                foundationStage = stage,
                blueprintDirty = false,
                isBusy = true,
                busyLabel = if (stage < 3) {
                    "正在用当前有效核心蓝图建书；未完成的章纲/伏笔可稍后补齐……"
                } else {
                    "正在把蓝图写入小说圣经、三级大纲和长期记忆……"
                },
                error = null,
            )
        }
        runCatching { foundationApplier.create(foundation) }
            .onSuccess { created ->
                suppressDraftPersistence = true
                _state.update {
                    it.copy(
                        isBusy = false,
                        busyLabel = "",
                        createdStoryId = created.snapshot.novel.id,
                        messages = it.messages + CreationChatMessage(
                            "assistant",
                            if (stage >= 3) {
                                "《${created.snapshot.novel.title}》已经正式建好。完整蓝图已经进入项目结构与长期记忆。"
                            } else {
                                "《${created.snapshot.novel.title}》已经正式建好。核心世界、人物和分卷已经写入项目；未完成的详细章纲/伏笔不会阻止开书，可以在项目里继续补齐。"
                            },
                        ),
                    )
                }
                draftStore.clear()
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = error.message ?: "正式建书失败") }
            }
    }
}

    fun reset() {
        suppressDraftPersistence = false
        draftStore.clear()
        _state.value = NewBookConversationState()
    }

    fun consumeCreatedStory() {
        _state.update { it.copy(createdStoryId = null) }
    }

    fun setReferenceTemplateIds(ids: List<String>) {
        val valid = referenceReportStore.listReports().map { it.taskId }.toSet()
        val next = ids.filter(valid::contains).distinct()
        _state.update {
            val changed = next != it.selectedReferenceTemplateIds
            it.copy(
                selectedReferenceTemplateIds = next,
                foundationStage = if (changed && it.foundation != null) 0 else it.foundationStage,
                blueprintDirty = it.blueprintDirty || (changed && it.foundation != null),
            )
        }
    }

    private suspend fun activeGateway(): AiGateway? {
        val id = activeProviderId ?: return null
        return repository.providerConfig(id)?.let(::UniversalAiGateway)
    }
}

private data class ConversationTurn(
    val reply: String,
    val proposal: NewBookProposal? = null,
)

private class NewBookConversationEngine(
    private val gateway: AiGateway,
) {
    suspend fun reply(
        messages: List<CreationChatMessage>,
        currentProposal: NewBookProposal? = null,
        referenceContext: String = "",
    ): ConversationTurn {
        val transcript = conversationTranscript(messages, keepLatestResearch = true)
        val latest = messages.lastOrNull { it.role == "user" }?.text
            ?.substringBefore(RESEARCH_CONTEXT_MARKER)
            ?.trim()
            .orEmpty()
        val output = gateway.generateStreaming(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书策划搭档。通过自然对话把模糊想法发展成原创长篇小说方案，不要先让用户填表。

                    规则：
                    1. 先理解用户想要的阅读体验。回复篇幅必须服从当前问题：简单选择直接确认，复杂比较、设定推演或研究结论就完整讲清；不得用机械字数限制删掉必要的理由、区别或设定。只追问真正会改变路线且会谈中仍未解决的问题，已经明确回答过的问题禁止重复追问，不要用“如果你愿意我可以……”收尾。
                    2. 当前轮若带有琅嬛联网资料/长期研究档案，用它核对公开事实；用户明确纠正的作者-作品关系属于项目事实，网页暂未核验只能标待核验，不能反复否定。
                    3. 参考模板有两种用途，必须区分：
                       - 原作事实问答：用户问模板/原作主角姓名、人物、能力、世界观、规则、势力、地点、剧情结构等时，可以并且应该直接说出所选 STORY DNA 已保存的原作事实、人物名和专名；报告没保存的事实就明确说“当前蒸馏报告未确认”。
                       - 新书创作：只能迁移高层机制，必须重新设计原创角色、规则、谜团和剧情；不得复用原作专名、具体能力规则、标志性句式和剧情骨架。
                       “禁止照搬”只约束新书创作，绝不能用来拒绝回答用户对模板本身的事实问题。
                    4. 会谈是唯一事实源。越新的用户明确决定优先级越高；用户说“B吧/就这个/改成/不要/换掉/天生不怕吧”这类短句也属于有效决定，必须结合前文理解并覆盖旧方案。
                       用户说“他们/它们/这两本/前面那几本”时，必须承接最近明确出现或隐藏研究上下文已经解析出的作品，绝不能把代词本身当作书名或新设定。
                    5. 如果已经存在“当前方案”，用户这一轮又修改了主角能力、身份、目标、世界规则、核心冲突、题材、阅读体验、书名或简介，你必须返回一套完整更新后的方案，不能只聊天后继续保留旧 proposal。
                    6. 用户这一轮如果是提问（尤其是模板/原作事实提问），只回答问题，不重写方案、不追问已回答事项、不输出新 proposal。此时输出 GeneratedChapter JSON：title="__CHAT__"；content=直接答案；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    7. 只有确实仍缺一个会改变主线的关键选择、或者用户只是普通聊天而没有做创作决定时，才输出 GeneratedChapter JSON：title="__CHAT__"；content=像正常 AI 聊天一样直接回应，或提出一个必要问题；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。能从会谈确定就直接确定，禁止为了显得专业而反复确认。
                    8. 信息足够或已有方案需要更新时输出完整方案：title=2-12字正式书名；content=完整连贯的平台简介，篇幅服从故事信息；summary=像正常聊天一样直接回应用户：说明你如何理解这条要求、具体改了什么及必要理由，也可以继续讨论，不要写“内部策划摘要”；stateChanges只返回1项，其中 subject=实际小说类型，field=一句实际主题命题，before=目标总字数纯数字，after=一句话核心钩子，evidence=封面视觉简报；touchedForeshadowingIds=[]。
                    9. 平台简介只写故事起点、主角眼前目标、核心异常/规则和当下代价/悬念，不泄露中后期答案和终局反转。只要核心设定已经改变，就必须按最新事实重写简介，禁止为了省事复用旧简介。
                    10. 若存在“用户显式选择的参考双层 DNA”，只允许使用这些已选档案；绝不能自动读取或混入其它未选择的蒸馏作品。
                    11. 用户上传的文件是本轮会谈资料。必须先阅读文件内容再回答，并区分“文件明确写明”与“你的推断”；不得假装没收到附件，也不得把附件中的参考作品内容自动当成新书已确认设定。
                    12. 若附件被识别为用户自己的“作品设定/世界观/大纲”，不能只做摘要：先把文件明确写出的书名、题材、篇幅、故事起点、人物、规则、势力、能力与代价、主线、分卷和伏笔同步进当前方案；再主动检查规则闭环、能力与代价平衡、人物动机、冲突升级、谜底释放顺序、分卷重复和前后矛盾。文件原文属于已确认基线；你补出的内容必须明确称为“优化建议/待确认”，用户认可前不得冒充锁定事实。
                    13. 对作品设定文件的正常回复至少包含：你识别到了什么、最值得保留的核心、具体风险或空缺、可以直接采用的补强方案。要结合文件里的实际名称和规则说话，禁止只说“设定很完整、可以继续完善”这类空话。
                """.trimIndent(),
                user = """
                    ${if (referenceContext.isBlank()) "【参考双层 DNA】本次未选择任何蒸馏模板。" else referenceContext}

                    ${currentProposal?.let(::proposalContext) ?: "【当前方案】尚未形成完整方案。"}

                    【本次新书创作会谈：后出现的用户决定覆盖前面的旧方案】
                    $transcript

                    【最新用户输入】
                    $latest
                """.trimIndent(),
                attachments = messagesPromptAttachments(messages),
            )
        ) { }

        if (output.title.trim() == CHAT_SENTINEL || output.stateChanges.isEmpty()) {
            val reply = output.content.trim().ifBlank { "再告诉我一点你最在意的感觉，我继续帮你收紧方向。" }
            if (currentProposal != null && looksLikeCreativeDecision(latest)) {
                val reconciled = runCatching {
                    ProposalConsolidator(gateway).consolidate(currentProposal, messages)
                }.getOrNull()
                if (reconciled != null) {
                    return ConversationTurn(
                        reply = "$reply\n\n你刚才这条决定已经同步进当前方案，不会继续沿用旧简介。",
                        proposal = reconciled,
                    )
                }
            }
            return ConversationTurn(reply = reply)
        }

        val meta = output.stateChanges.first()
        val rawProposal = NewBookProposal(
            title = sanitizeTitle(output.title),
            genre = sanitizeMetaValue(meta.subject, GENRE_PLACEHOLDERS, currentProposal?.genre ?: "未分类"),
            premise = sanitizeSynopsis(output.content),
            theme = sanitizeMetaValue(meta.field, THEME_PLACEHOLDERS, currentProposal?.theme ?: DEFAULT_THEME),
            targetWords = meta.before.filter(Char::isDigit).toIntOrNull()?.coerceIn(10_000, 5_000_000)
                ?: currentProposal?.targetWords
                ?: 500_000,
            coreHook = meta.after.trim().ifBlank { currentProposal?.coreHook ?: "一个看似普通的选择，逐渐暴露出更大的真相。" },
            coverBrief = meta.evidence.trim().ifBlank { currentProposal?.coverBrief.orEmpty() },
            rationale = output.summary.trim().ifBlank { currentProposal?.rationale.orEmpty() },
            decisionLedger = currentProposal?.decisionLedger.orEmpty(),
        ).sanitizePlaceholders()
        val proposal = SynopsisQualityEditor(gateway).ensure(
            proposal = rawProposal,
            decisionLedger = buildString {
                if (!currentProposal?.decisionLedger.isNullOrBlank()) {
                    appendLine("已有确认事实账本：")
                    appendLine(currentProposal?.decisionLedger)
                }
                appendLine("越新的用户决定优先。最近用户输入：$latest")
                messages.filter { it.role == "user" }.forEach { message ->
                    appendLine("- ${message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trim()}")
                }
            },
            force = true,
        )
        return ConversationTurn(
            reply = proposal.rationale.ifBlank {
                if (currentProposal == null) "我理解了，这条要求已经进入当前方案。你可以继续补充或直接纠正，方案会跟着改。"
                else "明白，已经按你刚才的要求修改。你继续说，后面的决定会覆盖前面的旧版本。"
            },
            proposal = proposal,
        )
    }

    private fun proposalContext(proposal: NewBookProposal): String = buildString {
        appendLine("【当前方案缓存：若与后续用户决定冲突，必须覆盖】")
        appendLine("书名：${proposal.title}")
        appendLine("类型：${proposal.genre}")
        appendLine("简介：${proposal.premise}")
        appendLine("主题：${proposal.theme}")
        appendLine("目标字数：${proposal.targetWords}")
        appendLine("核心钩子：${proposal.coreHook}")
        appendLine("内部策划：${proposal.rationale}")
        if (proposal.decisionLedger.isNotBlank()) {
            appendLine("确认事实账本：")
            appendLine(proposal.decisionLedger)
        }
    }

    private fun looksLikeCreativeDecision(text: String): Boolean {
        if (text.isBlank() || isQuestionLike(text)) return false
        val markers = listOf(
            "改成", "改为", "修改", "调整", "改下", "换成", "换一个", "不要", "删掉", "去掉", "保留", "不行", "不对", "不是", "重新", "再来", "还是", "就这个", "就选", "选A", "选B", "选C", "a吧", "b吧", "c吧",
            "主角", "能力", "身份", "性格", "目标", "世界", "规则", "设定", "剧情", "冲突", "主题", "类型",
            "题材", "风格", "氛围", "简介", "书名", "名字", "天生", "希望", "应该", "改吧", "这样吧", "按这个", "用这个",
        )
        return markers.any { text.contains(it, ignoreCase = true) }
    }
}

internal fun blueprintDirtyAfterConversation(
    alreadyDirty: Boolean,
    hasFoundation: Boolean,
    proposalUpdated: Boolean,
): Boolean = alreadyDirty || (hasFoundation && proposalUpdated)

private fun StoryFoundation.toProposal() = NewBookProposal(
    title = title,
    genre = sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"),
    premise = premise,
    theme = sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME),
    targetWords = targetWords,
    coreHook = coreHook,
    coverBrief = coverBrief,
    rationale = storyPromise,
    decisionLedger = creationBrief,
)

private fun NewBookProposal.sanitizePlaceholders(): NewBookProposal = copy(
    genre = sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"),
    theme = sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME),
)

private fun StoryFoundation.sanitizeFoundationPlaceholders(): StoryFoundation = copy(
    genre = sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"),
    theme = sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME),
)

private fun inferFoundationStage(foundation: StoryFoundation?): Int {
    foundation ?: return 0
    if (foundation.foreshadowing.isNotEmpty()) return 3
    val chapterCount = foundation.volumes.firstOrNull { it.order == 1 }?.chapters?.size ?: 0
    if (chapterCount >= 6) return 2
    if (foundation.bible.isNotEmpty() || foundation.characters.isNotEmpty() || foundation.volumes.isNotEmpty()) return 1
    return 0
}

private fun sanitizeMetaValue(value: String, placeholders: Set<String>, fallback: String): String {
    val clean = value.trim()
    return if (clean.isBlank() || placeholders.any { clean.equals(it, ignoreCase = true) }) fallback else clean
}

private fun conversationTranscript(
    messages: List<CreationChatMessage>,
    keepLatestResearch: Boolean,
): String {
    val lastUser = messages.indexOfLast { it.role == "user" }
    return messages.mapIndexed { index, message ->
        var text = message.text
        if (message.role == "user" && (!keepLatestResearch || index != lastUser)) {
            text = text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()
        }
        text = if (message.role == "user" && keepLatestResearch && index == lastUser) {
            val plain = text.substringBefore(RESEARCH_CONTEXT_MARKER)
            val research = text.substringAfter(RESEARCH_CONTEXT_MARKER, "")
            if (research.isBlank()) plain else "$plain$RESEARCH_CONTEXT_MARKER\n$research"
        } else {
            text
        }
        val attachmentContext = attachmentContext(message.attachments)
        val combined = if (attachmentContext.isBlank()) text else "$text\n$attachmentContext"
        if (message.role == "user") "用户：$combined" else "琅嬛：$combined"
    }.joinToString("\n")
}

internal fun attachmentContext(attachments: List<CreationChatAttachment>): String = buildString {
    attachments.forEach { attachment ->
        appendLine("【用户附件：${attachment.fileName}｜${attachment.mimeType}｜识别用途=${attachmentPurpose(attachment)}】")
        if (attachment.extractedText.isNotBlank()) appendLine(attachment.extractedText)
        else appendLine("附件以原生文件形式随请求发送，请直接读取其内容。")
        appendLine("【附件结束】")
    }
}.trim()

internal fun defaultAttachmentInstruction(attachments: List<CreationChatAttachment>): String {
    val purposes = attachments.map(::attachmentPurpose).distinct()
    return if ("作品设定" in purposes) {
        "请自动识别并整理我上传的作品设定：明确写出的内容同步进当前方案；检查规则闭环、人物动机、能力代价、主线推进、谜底释放和分卷升级，给出具体优化完善方案。你新增或改动的设定先标为待确认，不要擅自覆盖原文。"
    } else {
        "请先识别我上传的文件属于设定、人物、大纲、正文还是参考资料，再读取其中内容，结合当前会谈给出具体分析和可执行的优化建议。"
    }
}

internal fun attachmentPurpose(attachment: CreationChatAttachment): String {
    val name = attachment.fileName.lowercase()
    val text = attachment.extractedText
    return when {
        listOf("作品设定", "世界观", "设定集", "故事圣经", "小说圣经").any { name.contains(it) } ||
            listOf("世界规则", "故事梗概", "核心设定", "势力", "分卷大纲").count { text.contains(it) } >= 2 -> "作品设定"
        listOf("大纲", "章纲", "卷纲").any { name.contains(it) } -> "故事大纲"
        listOf("人物", "角色", "人设").any { name.contains(it) } -> "人物设定"
        listOf("正文", "章节", "第1章", "第一章").any { name.contains(it) } -> "小说正文"
        attachment.mimeType.startsWith("image/") -> "图片资料"
        attachment.mimeType == "application/pdf" -> "PDF资料"
        else -> "参考资料"
    }
}

internal fun messagesPromptAttachments(messages: List<CreationChatMessage>): List<PromptAttachment> =
    messages.flatMap { message ->
        message.attachments.mapNotNull { attachment ->
            attachment.base64Data.takeIf(String::isNotBlank)?.let {
                PromptAttachment(attachment.fileName, attachment.mimeType, it)
            }
        }
    }

internal fun extractDocxText(bytes: ByteArray): String {
    var xml = ""
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "word/document.xml") {
                xml = zip.readBytes().toString(Charsets.UTF_8)
                break
            }
        }
    }
    return xml
        .replace(Regex("(?i)</w:p>"), "\n")
        .replace(Regex("(?i)<w:tab[^>]*/>"), "\t")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun mimeFromName(lower: String): String = when {
    lower.endsWith(".pdf") -> "application/pdf"
    lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    lower.endsWith(".epub") -> "application/epub+zip"
    lower.endsWith(".json") -> "application/json"
    lower.endsWith(".csv") -> "text/csv"
    lower.endsWith(".md") || lower.endsWith(".markdown") -> "text/markdown"
    lower.endsWith(".png") -> "image/png"
    lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
    lower.endsWith(".webp") -> "image/webp"
    else -> "text/plain"
}

private fun canonicalAttachmentMime(lower: String, reported: String): String = when {
    lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".epub") ||
        lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".md") ||
        lower.endsWith(".markdown") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") || lower.endsWith(".webp") -> mimeFromName(lower)
    reported.isNotBlank() -> reported
    else -> mimeFromName(lower)
}

private const val MAX_CHAT_ATTACHMENT_BYTES = 12 * 1024 * 1024

private fun sanitizeTitle(value: String): String {
    val clean = value.trim().removePrefix("《").removeSuffix("》")
        .replace(Regex("[\n\r]"), "")
    if (clean.filterNot(Char::isWhitespace).length in 2..12 && !Regex("[，,。！？!?：:；;]").containsMatchIn(clean)) {
        return clean
    }
    return clean.replace(Regex("[《》“”\"'，,。！？!?：:；;、\\s]"), "").take(8).ifBlank { "无归之境" }
}

private fun sanitizeSynopsis(value: String): String {
    val text = value.trim().replace(Regex("\\n{3,}"), "\n\n")
    if (text.isBlank()) return "一个普通人的生活因一次无法解释的异常被彻底打破。为了找回最重要的人，他不得不主动进入未知，而每一次选择都会让真相更近，也让退路更少。"
    val spoiler = Regex("(?:最终.{0,20}(?:发现|揭开)|直到.{0,20}(?:发现|明白)|真正被选中的|原来.{0,30}(?:才是|就是))")
    val sentences = text.split(Regex("(?<=[。！？!?])"))
    val kept = mutableListOf<String>()
    for (sentence in sentences) {
        val part = sentence.trim()
        if (part.isBlank()) continue
        if (spoiler.containsMatchIn(part) && kept.joinToString("").length >= 70) break
        kept += part
    }
    return kept.joinToString("").ifBlank { text }
}

private fun friendlyAiError(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    val timeout = message.contains("timed out", true) || message.contains("timeout", true) || message.contains("超时")
    return if (timeout) {
        "$fallback：当前阶段请求超时。已经成功完成的蓝图阶段会保留为断点；直接重试会从下一阶段继续，不需要重新发送整批网页资料，也不会重复生成已完成阶段。"
    } else {
        message.ifBlank { fallback }
    }
}
