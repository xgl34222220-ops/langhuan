package com.xiguli.langhuan.ui

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.data.StoryFoundationApplier
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore
import com.xiguli.langhuan.engine.UniversalAiGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

@Serializable
data class CreationChatMessage(
    val role: String,
    val text: String,
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
    val isBusy: Boolean = false,
    val busyLabel: String = "",
    val createdStoryId: String? = null,
    val error: String? = null,
    val selectedReferenceTemplateIds: List<String> = emptyList(),
)

@Serializable
private data class NewBookConversationDraft(
    val schemaVersion: Int = 6,
    val messages: List<CreationChatMessage>,
    val proposal: NewBookProposal? = null,
    val foundation: StoryFoundation? = null,
    val foundationStage: Int = 0,
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
            selectedReferenceTemplateIds = draft.selectedReferenceTemplateIds.distinct(),
        )
    }.getOrElse {
        clear()
        null
    }

    fun persist(state: NewBookConversationState) {
        if (state.messages.size <= 1 && state.proposal == null && state.foundation == null && state.selectedReferenceTemplateIds.isEmpty()) {
            clear()
            return
        }
        val bytes = json.encodeToString(
            NewBookConversationDraft(
                messages = state.messages.map(::compactStoredMessage),
                proposal = state.proposal?.sanitizePlaceholders(),
                foundation = state.foundation?.sanitizeFoundationPlaceholders(),
                foundationStage = state.foundationStage.coerceIn(0, 3),
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
        if (clean.isBlank() || before.isBusy) return
        val history = before.messages + CreationChatMessage("user", clean)
        val plainInstruction = clean.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        _state.update {
            it.copy(
                messages = history,
                isBusy = true,
                busyLabel = when {
                    IdentityRefiner.isIdentityOnlyInstruction(plainInstruction) -> "AI 正在按最新会谈重写书名 / 平台简介……"
                    before.foundation != null -> "AI 正在按最新决定重构建书蓝图……"
                    before.proposal != null -> "AI 正在把你的新决定同步进方案……"
                    else -> "AI 正在继续构思……"
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

            if (IdentityRefiner.isIdentityOnlyInstruction(plainInstruction)) {
                val currentProposal = before.foundation?.toProposal() ?: before.proposal
                if (currentProposal != null) {
                    runCatching {
                        _state.update { it.copy(busyLabel = "AI 正在先核对最新聊天决定……") }
                        val aligned = ProposalConsolidator(gateway).consolidate(
                            current = currentProposal.sanitizePlaceholders(),
                            messages = history,
                        )
                        _state.update { it.copy(proposal = aligned, busyLabel = "AI 正在只重写书名 / 平台简介……") }
                        IdentityRefiner.refine(
                            gateway = gateway,
                            proposal = aligned,
                            transcript = conversationTranscript(history, keepLatestResearch = false),
                            instruction = plainInstruction,
                        )
                    }.onSuccess { refined ->
                        _state.update {
                            it.copy(
                                proposal = refined.sanitizePlaceholders(),
                                foundation = before.foundation?.copy(title = refined.title, premise = refined.premise)?.sanitizeFoundationPlaceholders(),
                                messages = it.messages + CreationChatMessage(
                                    "assistant",
                                    "我先按整段会谈核对了最新决定，再只重写书名 / 对外简介；不会再拿旧简介覆盖你后面确认的新设定。",
                                ),
                                isBusy = false,
                                busyLabel = "",
                            )
                        }
                    }.onFailure { error ->
                        _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "书名 / 简介重写失败")) }
                    }
                    return@launch
                }
            }

            if (before.foundation != null) {
                val baseline = before.foundation.sanitizeFoundationPlaceholders().toProposal()
                runCatching {
                    _state.update { it.copy(busyLabel = "AI 正在把最新聊天决定合并进当前方案……") }
                    val refreshed = ProposalConsolidator(gateway).consolidate(
                        current = baseline,
                        messages = history,
                    )
                    _state.update { it.copy(proposal = refreshed, busyLabel = "AI 正在按最新方案重构建书蓝图……") }
                    ProgressiveFoundationEngine(gateway).build(
                        proposal = refreshed,
                        messages = history,
                        current = before.foundation.sanitizeFoundationPlaceholders(),
                        instruction = plainInstruction,
                        referenceContext = referenceReportStore.promptContext(before.selectedReferenceTemplateIds),
                        resumeStage = 0,
                        onStage = { label -> _state.update { it.copy(busyLabel = label) } },
                        onCheckpoint = { stage, checkpoint ->
                            val cleanCheckpoint = checkpoint.sanitizeFoundationPlaceholders()
                            _state.update {
                                it.copy(
                                    foundation = cleanCheckpoint,
                                    proposal = cleanCheckpoint.toProposal(),
                                    foundationStage = stage,
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
                            foundationStage = 3,
                            messages = it.messages + CreationChatMessage(
                                "assistant",
                                "建书蓝图已按你最新确认的决定分阶段重构完成。旧方案只作为历史参考，不会覆盖后面的修改。",
                            ),
                            isBusy = false,
                            busyLabel = "",
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "蓝图修改失败")) }
                }
                return@launch
            }

            runCatching {
                NewBookConversationEngine(gateway).reply(
                    messages = history,
                    currentProposal = before.proposal?.sanitizePlaceholders(),
                    referenceContext = referenceReportStore.promptContext(before.selectedReferenceTemplateIds),
                )
            }.onSuccess { turn ->
                _state.update {
                    it.copy(
                        messages = it.messages + CreationChatMessage("assistant", turn.reply),
                        proposal = turn.proposal?.sanitizePlaceholders() ?: it.proposal,
                        foundationStage = if (it.foundation == null) 0 else it.foundationStage,
                        isBusy = false,
                        busyLabel = "",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "AI 构思失败")) }
            }
        }
    }

    fun generateFoundation(regenerate: Boolean = false) {
        val before = _state.value
        val baseline = (before.foundation?.toProposal() ?: before.proposal)?.sanitizePlaceholders() ?: return
        if (before.isBusy) return
        val resumeStage = if (regenerate) 0 else before.foundationStage.coerceIn(0, 2)
        val resumeFoundation = if (regenerate) null else before.foundation?.sanitizeFoundationPlaceholders()
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            _state.update {
                it.copy(
                    proposal = baseline,
                    isBusy = true,
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
                val refreshed = ProposalConsolidator(gateway).consolidate(
                    current = baseline,
                    messages = before.messages,
                )
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
                        foundationStage = 3,
                        messages = it.messages + CreationChatMessage(
                            "assistant",
                            "建书蓝图已经按整段会谈的最新决定分三阶段生成完成：核心世界与人物、第一卷详细章纲、伏笔计划都已对齐。",
                        ),
                        isBusy = false,
                        busyLabel = "",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "建书蓝图生成失败")) }
            }
        }
    }

    fun createCurrentFoundation() {
        val foundation = _state.value.foundation?.sanitizeFoundationPlaceholders() ?: return
        if (_state.value.isBusy) return
        val stage = maxOf(_state.value.foundationStage, inferFoundationStage(foundation))
        if (stage < 3) {
            _state.update {
                it.copy(error = "建书蓝图目前只完成到 $stage/3。前面成功阶段已经保存，请先点“重试生成蓝图”完成剩余阶段，再正式建书。")
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    foundation = foundation,
                    proposal = foundation.toProposal(),
                    foundationStage = 3,
                    isBusy = true,
                    busyLabel = "正在把蓝图写入小说圣经、三级大纲和长期记忆……",
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
                                "《${created.snapshot.novel.title}》已经正式建好。角色、世界规则、总纲、卷纲、第一卷章纲和伏笔计划已经进入项目结构与长期记忆，可以直接从第一章开始创作。",
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
        val output = gateway.generateStreaming(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书策划搭档。通过自然对话把模糊想法发展成原创长篇小说方案，不要先让用户填表。

                    规则：
                    1. 先理解用户想要的阅读体验，一次最多追问1-2个关键问题。
                    2. 当前轮若带有琅嬛联网资料/长期研究档案，用它核对公开事实；用户明确纠正的作者-作品关系属于项目事实，网页暂未核验只能标待核验，不能反复否定。
                    3. 参考作品/作者只能提炼高层创作特征，必须重新设计原创角色、规则、谜团和剧情；不得复制标志性句式、专名和剧情骨架。
                    4. 会谈是唯一事实源。越新的用户明确决定优先级越高；用户说“B吧/就这个/改成/不要/换掉/天生不怕吧”这类短句也属于有效决定，必须结合前文理解并覆盖旧方案。
                    5. 如果已经存在“当前方案”，用户这一轮又修改了主角能力、身份、目标、世界规则、核心冲突、题材、阅读体验、书名或简介，你必须返回一套完整更新后的方案，不能只聊天后继续保留旧 proposal。
                    6. 只有确实仍缺关键选择、或者用户只是提问而没有做创作决定时，才输出 GeneratedChapter JSON：title="__CHAT__"；content=自然回复或追问；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    7. 信息足够或已有方案需要更新时输出完整方案：title=2-12字正式书名；content=100-220字平台简介；summary=80-180字内部策划摘要；stateChanges只返回1项，其中 subject=实际小说类型，field=一句实际主题命题，before=目标总字数纯数字，after=一句话核心钩子，evidence=封面视觉简报；touchedForeshadowingIds=[]。
                    8. 平台简介只写故事起点、主角眼前目标、核心异常/规则和当下代价/悬念，不泄露中后期答案和终局反转。只要核心设定已经改变，就必须按最新事实重写简介，禁止为了省事复用旧简介。
                    9. 若存在“用户显式选择的参考 Style DNA”，只允许使用这些已选档案；绝不能自动读取或混入其它未选择的蒸馏作品。
                """.trimIndent(),
                user = """
                    ${if (referenceContext.isBlank()) "【参考 Style DNA】本次未选择任何蒸馏模板。" else referenceContext}

                    ${currentProposal?.let(::proposalContext) ?: "【当前方案】尚未形成完整方案。"}

                    【本次新书创作会谈：后出现的用户决定覆盖前面的旧方案】
                    $transcript
                """.trimIndent(),
            )
        ) { }

        if (output.title.trim() == CHAT_SENTINEL || output.stateChanges.isEmpty()) {
            val reply = output.content.trim().ifBlank { "再告诉我一点你最在意的感觉，我继续帮你收紧方向。" }
            val latest = messages.lastOrNull { it.role == "user" }?.text
                ?.substringBefore(RESEARCH_CONTEXT_MARKER)
                ?.trim()
                .orEmpty()
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
        val proposal = NewBookProposal(
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
        ).sanitizePlaceholders()
        return ConversationTurn(
            reply = buildString {
                append(if (currentProposal == null) "我已经把方向收束成一套可以落地的方案。" else "我已经按你刚才的决定更新了当前方案。")
                if (proposal.rationale.isNotBlank()) append("\n").append(proposal.rationale)
                append("\n如果这个方向对，就可以直接生成建书蓝图；也可以继续聊着改。")
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
        appendLine("内部策划：${proposal.rationale.take(420)}")
    }

    private fun looksLikeCreativeDecision(text: String): Boolean {
        if (text.isBlank()) return false
        val markers = listOf(
            "改成", "换成", "不要", "删掉", "就这个", "就选", "选A", "选B", "选C", "a吧", "b吧", "c吧",
            "主角", "能力", "身份", "性格", "目标", "世界", "规则", "设定", "剧情", "冲突", "主题", "类型",
            "题材", "风格", "氛围", "简介", "书名", "名字", "天生", "改吧", "这样吧", "按这个", "用这个",
        )
        return markers.any { text.contains(it, ignoreCase = true) }
    }
}

private object IdentityRefiner {
    fun isIdentityOnlyInstruction(text: String): Boolean {
        val value = text.trim()
        if (listOf("书名", "名字", "标题", "简介", "介绍", "文案").none(value::contains)) return false
        return listOf("世界观", "设定", "角色", "人物", "主线", "剧情", "大纲", "卷纲", "章纲", "规则", "结局", "主题", "类型", "伏笔")
            .none(value::contains)
    }

    suspend fun refine(
        gateway: AiGateway,
        proposal: NewBookProposal,
        transcript: String,
        instruction: String,
    ): NewBookProposal {
        val output = gateway.generateStreaming(
            PromptBundle(
                system = """
                    你是中文网络小说的书名与平台简介编辑。只能改书名和简介，不能改人物、世界规则、主线、结局、类型、主题和核心钩子。
                    输出 GeneratedChapter JSON：title=2-12字正式书名；content=100-220字平台简介；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    简介只写故事起点、主角目标、核心异常/规则、当下代价或悬念；禁止完整剧情梗概、设定罗列和中后期剧透。
                    当前 proposal 已经经过整段会谈重新对齐，不要恢复更早版本的简介或设定。
                """.trimIndent(),
                user = """
                    当前书名：${proposal.title}
                    当前简介：${proposal.premise}
                    类型：${proposal.genre}
                    主题：${proposal.theme}
                    核心钩子：${proposal.coreHook}
                    用户要求：$instruction
                    会谈事实：${transcript.takeLast(3_500)}
                    只重写 title 和 content。
                """.trimIndent(),
            )
        ) { }
        return proposal.copy(
            title = sanitizeTitle(output.title).ifBlank { proposal.title },
            premise = sanitizeSynopsis(output.content).ifBlank { proposal.premise },
        ).sanitizePlaceholders()
    }
}

private fun StoryFoundation.toProposal() = NewBookProposal(
    title = title,
    genre = sanitizeMetaValue(genre, GENRE_PLACEHOLDERS, "未分类"),
    premise = premise,
    theme = sanitizeMetaValue(theme, THEME_PLACEHOLDERS, DEFAULT_THEME),
    targetWords = targetWords,
    coreHook = coreHook,
    coverBrief = coverBrief,
    rationale = storyPromise,
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

/**
 * Normal chat may use the newest hidden research packet once. Foundation generation does not call
 * this function: ProgressiveFoundationEngine strips all raw research evidence and works from the
 * already confirmed proposal + compact user facts instead.
 */
private fun conversationTranscript(
    messages: List<CreationChatMessage>,
    keepLatestResearch: Boolean,
): String {
    val recent = messages.takeLast(18)
    val lastUser = recent.indexOfLast { it.role == "user" }
    return recent.mapIndexed { index, message ->
        var text = message.text
        if (message.role == "user" && (!keepLatestResearch || index != lastUser)) {
            text = text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()
        }
        text = if (message.role == "user" && keepLatestResearch && index == lastUser) {
            val plain = text.substringBefore(RESEARCH_CONTEXT_MARKER).take(1_200)
            val research = text.substringAfter(RESEARCH_CONTEXT_MARKER, "").take(4_500)
            if (research.isBlank()) plain else "$plain$RESEARCH_CONTEXT_MARKER\n$research"
        } else {
            text.take(1_000)
        }
        if (message.role == "user") "用户：$text" else "琅嬛：$text"
    }.joinToString("\n").takeLast(9_000)
}

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
    var length = 0
    for (sentence in sentences) {
        val part = sentence.trim()
        if (part.isBlank()) continue
        if (spoiler.containsMatchIn(part) && length >= 70) break
        if (length + part.length > 220) break
        kept += part
        length += part.length
        if (length >= 140) break
    }
    return kept.joinToString("").ifBlank { text.take(200) }
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
