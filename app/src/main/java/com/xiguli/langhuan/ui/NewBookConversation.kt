package com.xiguli.langhuan.ui

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.FoundationBibleItem
import com.xiguli.langhuan.data.FoundationChapter
import com.xiguli.langhuan.data.FoundationCharacter
import com.xiguli.langhuan.data.FoundationForeshadow
import com.xiguli.langhuan.data.FoundationVolume
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.data.StoryFoundationApplier
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
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
    val isBusy: Boolean = false,
    val busyLabel: String = "",
    val createdStoryId: String? = null,
    val error: String? = null,
)

@Serializable
private data class NewBookConversationDraft(
    val schemaVersion: Int = 2,
    val messages: List<CreationChatMessage>,
    val proposal: NewBookProposal? = null,
    val foundation: StoryFoundation? = null,
)

private class NewBookConversationDraftStore(application: Application) {
    private val atomicFile = AtomicFile(File(application.filesDir, NEW_BOOK_DRAFT_FILE))
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun restore(): NewBookConversationState? {
        return runCatching {
            val bytes = atomicFile.openRead().use { it.readBytes() }
            if (bytes.isEmpty()) return@runCatching null
            val draft = json.decodeFromString<NewBookConversationDraft>(bytes.toString(Charsets.UTF_8))
            if (draft.messages.isEmpty()) return@runCatching null
            NewBookConversationState(
                messages = draft.messages.map(::compactStoredMessage),
                proposal = draft.proposal,
                foundation = draft.foundation,
                isBusy = false,
                busyLabel = "",
                createdStoryId = null,
                error = null,
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun persist(state: NewBookConversationState) {
        if (!state.hasPersistentDraft()) {
            clear()
            return
        }
        val payload = json.encodeToString(
            NewBookConversationDraft(
                messages = state.messages.map(::compactStoredMessage),
                proposal = state.proposal,
                foundation = state.foundation,
            )
        ).toByteArray(Charsets.UTF_8)
        val output = runCatching { atomicFile.startWrite() }.getOrNull() ?: return
        try {
            output.write(payload)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (_: Throwable) {
            atomicFile.failWrite(output)
        }
    }

    fun clear() {
        runCatching { atomicFile.delete() }
    }

    private fun compactStoredMessage(message: CreationChatMessage): CreationChatMessage =
        if (message.role == "user" && message.text.contains(RESEARCH_CONTEXT_MARKER)) {
            message.copy(text = message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd())
        } else message
}

private fun NewBookConversationState.hasPersistentDraft(): Boolean =
    messages.size > 1 || proposal != null || foundation != null

class NewBookConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val foundationApplier = StoryFoundationApplier(application)
    private val draftStore = NewBookConversationDraftStore(application)
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
        _state.update {
            it.copy(
                messages = history,
                isBusy = true,
                busyLabel = if (BookIdentityRefiner.isIdentityOnlyInstruction(clean)) {
                    "AI 正在只重写书名 / 平台简介……"
                } else if (before.foundation == null) {
                    "AI 正在继续构思……"
                } else {
                    "AI 正在按你的要求重构建书蓝图……"
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

            if (BookIdentityRefiner.isIdentityOnlyInstruction(clean)) {
                val currentProposal = before.foundation?.toProposal() ?: before.proposal
                if (currentProposal != null) {
                    runCatching {
                        BookIdentityRefiner.refine(
                            gateway = gateway,
                            proposal = currentProposal,
                            transcript = transcript(history),
                            forceRewrite = true,
                            userInstruction = clean,
                        )
                    }.onSuccess { refined ->
                        _state.update {
                            it.copy(
                                proposal = refined,
                                foundation = before.foundation?.copy(
                                    title = refined.title,
                                    premise = refined.premise,
                                ),
                                messages = it.messages + CreationChatMessage(
                                    "assistant",
                                    "我只重写了书名 / 对外简介，没有动世界规则、角色、主线、大纲和伏笔。简介已经重新通过长度、剧透和设定堆砌检查。",
                                ),
                                isBusy = false,
                                busyLabel = "",
                            )
                        }
                    }.onFailure { e ->
                        _state.update { it.copy(isBusy = false, busyLabel = "", error = e.message ?: "书名 / 简介重写失败") }
                    }
                    return@launch
                }
            }

            if (before.foundation != null) {
                val fallback = before.foundation.toProposal()
                runCatching {
                    NewBookFoundationEngine(gateway).build(
                        proposal = fallback,
                        messages = history,
                        current = before.foundation,
                        instruction = clean,
                    )
                }.onSuccess { foundation ->
                    _state.update {
                        it.copy(
                            foundation = foundation,
                            proposal = foundation.toProposal(),
                            messages = it.messages + CreationChatMessage(
                                "assistant",
                                "我已经按你的要求更新了建书蓝图。现在展示的是最新版本；还想改角色、规则、卷纲或前期节奏，继续直接说就行。",
                            ),
                            isBusy = false,
                            busyLabel = "",
                        )
                    }
                }.onFailure { e ->
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = e.message ?: "蓝图修改失败") }
                }
            } else {
                runCatching { NewBookConversationEngine(gateway).reply(history) }
                    .onSuccess { turn ->
                        _state.update {
                            it.copy(
                                messages = it.messages + CreationChatMessage("assistant", turn.reply),
                                proposal = turn.proposal ?: it.proposal,
                                isBusy = false,
                                busyLabel = "",
                            )
                        }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(isBusy = false, busyLabel = "", error = e.message ?: "AI 构思失败") }
                    }
            }
        }
    }

    fun generateFoundation(regenerate: Boolean = false) {
        val before = _state.value
        val proposal = before.foundation?.toProposal() ?: before.proposal ?: return
        if (before.isBusy) return
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            _state.update {
                it.copy(
                    isBusy = true,
                    busyLabel = "AI 正在搭建世界观、角色、卷纲和前期章纲……",
                    error = null,
                )
            }
            val instruction = if (regenerate) {
                "在保留用户已经确认的题材、主题与阅读体验前提下，重新设计一套明显不同但更自洽的角色、规则、分卷结构和前期章节路线。"
            } else {
                "把已经确认的新书方案扩展成可以直接开始长篇写作的完整建书蓝图。"
            }
            runCatching {
                NewBookFoundationEngine(gateway).build(
                    proposal = proposal,
                    messages = before.messages,
                    current = if (regenerate) null else before.foundation,
                    instruction = instruction,
                )
            }.onSuccess { foundation ->
                _state.update {
                    it.copy(
                        foundation = foundation,
                        proposal = foundation.toProposal(),
                        messages = it.messages + CreationChatMessage(
                            "assistant",
                            "建书蓝图已经生成：世界规则、核心角色、总纲、分卷和第一卷前期章纲都整理好了。你可以继续跟我说怎么改，满意后再正式写入书架和长期记忆。",
                        ),
                        isBusy = false,
                        busyLabel = "",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = e.message ?: "建书蓝图生成失败") }
            }
        }
    }

    fun createCurrentFoundation() {
        val foundation = _state.value.foundation ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
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
                }.onFailure { e ->
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = e.message ?: "正式建书失败") }
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
    suspend fun reply(messages: List<CreationChatMessage>): ConversationTurn {
        val transcript = transcript(messages)
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书策划搭档。你的任务不是让用户先填表，而是通过自然对话逐步把一个模糊想法发展成原创长篇小说方案。

                    对话规则：
                    1. 先理解用户真正想要的阅读体验，再决定是否追问。一次最多追问 1-2 个最关键问题，不要像问卷。
                    2. 用户可能问“你知道某本小说吗”“你知道某位作者吗”。琅嬛的实时检索和长期研究档案如果已经出现在当前上下文里，用它们核对公开事实；用户明确提供/纠正的作者-作品关系属于项目事实，网页暂未核验只能标待核验，不能反复否定用户已经说明的关系。本轮搜索失败也不能清空历史档案或你原本有把握的高层知识。
                    3. 用户要求参考某作品/作者时，只能提炼高层创作特征，再创作全新的角色、世界规则、核心谜团和情节。不要复刻标志性句式，不要换名照搬人物、设定或剧情骨架。
                    4. 当信息还不足以形成靠谱方案时，输出 GeneratedChapter JSON：title 必须为 __CHAT__；content=你这一轮自然、简洁的回复或追问；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    5. 当信息已经足够时，输出可进入建书蓝图阶段的方案：
                       - title = 正式小说名，优先 2-8 个汉字，最长 12 个可见字符。它必须像书名，不得写成“我为……”“为了……”“当我……”这类广告标题或一句完整剧情说明；不要逗号、句号、冒号。
                       - content = “平台简介”，只写 120-190 个中文字符左右，2-4 个短段/句群。只允许：故事起点 + 主角眼前目标 + 最核心异常/规则 + 立即面临的代价/悬念。
                       - 平台简介禁止：完整讲完主线；罗列多个副本/场景名；解释主题；解释创作思路；泄露中后期反转、幕后黑手、终局答案；出现“最终、直到他发现、原来、真正被选中的其实”等剧透式总结。
                       - summary = 80-180 字内部策划摘要，核心卖点、差异化、叙事气质放这里，不要塞进 content。
                       - stateChanges 只返回 1 项，其中 subject=小说类型，field=主题命题，before=目标总字数（只写数字），after=一句话核心钩子，evidence=封面视觉简报；
                       - touchedForeshadowingIds=[]。
                    6. 用户要求“重写简介/换书名”时，不要顺便改人物、规则、主线和结局，只重写对外身份信息。
                """.trimIndent(),
                user = """
                    下面是本次新书创作会谈。请根据全部上下文继续对话，不要丢失用户前面已经确认的要求。

                    $transcript
                """.trimIndent(),
            )
        )

        if (output.title.trim() == CHAT_SENTINEL || output.stateChanges.isEmpty()) {
            return ConversationTurn(
                reply = output.content.trim().ifBlank { "再告诉我一点你最在意的感觉，我继续帮你收紧方向。" },
            )
        }

        val meta = output.stateChanges.first()
        val target = meta.before.filter(Char::isDigit).toIntOrNull()?.coerceIn(10_000, 5_000_000) ?: 500_000
        val rawProposal = NewBookProposal(
            title = output.title.trim().ifBlank { "未命名小说" },
            genre = meta.subject.trim().ifBlank { "未分类" },
            premise = output.content.trim().ifBlank { "围绕一个不可回避的核心冲突展开。" },
            theme = meta.field.trim().ifBlank { "人在真相与代价之间如何选择" },
            targetWords = target,
            coreHook = meta.after.trim().ifBlank { "一个看似普通的选择，逐渐暴露出更大的真相。" },
            coverBrief = meta.evidence.trim(),
            rationale = output.summary.trim(),
        )
        val proposal = BookIdentityRefiner.refine(
            gateway = gateway,
            proposal = rawProposal,
            transcript = transcript,
            forceRewrite = false,
            userInstruction = "",
        )
        return ConversationTurn(
            reply = buildString {
                append("我已经把方向收束成一套可以落地的方案。")
                if (proposal.rationale.isNotBlank()) append("\n").append(proposal.rationale)
                append("\n书名和平台简介已通过质量门；不满意可以只重写它们，不会牵连世界观和大纲。")
            },
            proposal = proposal,
        )
    }
}

private data class IdentityQuality(
    val acceptable: Boolean,
    val issues: List<String>,
)

private object BookIdentityRefiner {
    fun isIdentityOnlyInstruction(text: String): Boolean {
        val value = text.trim()
        val identityWords = listOf("书名", "名字", "标题", "简介", "介绍", "文案")
        if (identityWords.none(value::contains)) return false
        val structuralWords = listOf("世界观", "设定", "角色", "人物", "主线", "剧情", "大纲", "卷纲", "章纲", "规则", "结局", "主题", "类型", "伏笔")
        return structuralWords.none(value::contains)
    }

    suspend fun refine(
        gateway: AiGateway,
        proposal: NewBookProposal,
        transcript: String,
        forceRewrite: Boolean,
        userInstruction: String,
    ): NewBookProposal {
        var candidate = proposal.copy(
            title = proposal.title.trim(),
            premise = proposal.premise.trim(),
        )
        var quality = inspect(candidate.title, candidate.premise)
        if (!forceRewrite && quality.acceptable) return candidate

        repeat(2) { attempt ->
            val output = runCatching {
                gateway.generate(
                    PromptBundle(
                        system = """
                            你是中文网络小说的“书名 + 平台简介”责任编辑。你只能修改书名和平台简介，绝对不能修改故事事实、人物、世界规则、主线、结局方向、类型、主题和核心钩子。

                            必须输出 GeneratedChapter JSON：
                            - title：正式小说名，2-8个汉字为佳，最长12个可见字符；必须像书名，不是广告标题，不是一整句剧情，不用逗号/句号/冒号。
                            - content：平台简介，120-190个中文字符左右，2-4个紧凑句群。结构只允许：故事起点 → 主角目标 → 核心异常/规则 → 当下代价或悬念。
                            - summary=""；stateChanges=[]；touchedForeshadowingIds=[]。

                            禁止事项：
                            1. 不得罗列三个以上副本名、怪物名、地点名或规则案例。
                            2. 不得把总纲、世界观说明、主题命题、故事承诺写进简介。
                            3. 不得提前说出中后期真相、幕后操控者、终局反转、谁才是真正目标等答案。
                            4. 不用“最终、直到他发现、原来、其实、真正被选中的……”做剧透式收尾。
                            5. 不得新增当前方案里没有的关键设定。
                            6. 语言要像用户在小说平台点进详情页看到的简介：短、清楚、有钩子，不写成剧情梗概。
                        """.trimIndent(),
                        user = """
                            当前类型：${proposal.genre}
                            当前主题（仅供理解，不要写进简介）：${proposal.theme}
                            当前核心钩子（仅供理解）：${proposal.coreHook}
                            当前书名：${candidate.title}
                            当前简介：${candidate.premise}
                            用户本轮要求：${userInstruction.ifBlank { "让书名和简介更像正式小说平台成品" }}
                            当前质量问题：${quality.issues.joinToString("；").ifBlank { "用户主动要求重写" }}

                            会谈事实参考：
                            ${transcript.takeLast(5000)}

                            只重写 title 和 content，不解释。
                        """.trimIndent(),
                    )
                )
            }.getOrNull()

            if (output != null) {
                candidate = proposal.copy(
                    title = output.title.trim().ifBlank { candidate.title },
                    premise = output.content.trim().ifBlank { candidate.premise },
                )
                quality = inspect(candidate.title, candidate.premise)
                if (quality.acceptable) return candidate
            }

            if (attempt == 0 && quality.acceptable) return candidate
        }

        return proposal.copy(
            title = localTitleFallback(candidate.title, candidate.premise),
            premise = localSynopsisFallback(candidate.premise),
        )
    }

    private fun inspect(title: String, premise: String): IdentityQuality {
        val issues = mutableListOf<String>()
        val titleVisible = title.filterNot(Char::isWhitespace)
        val synopsisVisible = premise.filterNot(Char::isWhitespace)
        if (titleVisible.length !in 2..12) issues += "书名应为2-12个可见字符"
        if (Regex("[，,。！？!?：:；;]").containsMatchIn(title)) issues += "书名像一句宣传文案，含句子标点"
        if (Regex("^(我为|为了|只为|当我|如果|开局|穿越后|重生后)").containsMatchIn(titleVisible)) issues += "书名使用营销句式"

        if (synopsisVisible.length !in 100..220) issues += "平台简介应控制在约100-220个可见字符"
        if (premise.count { it == '、' } >= 4) issues += "简介在罗列设定/副本名"
        if (listOf("核心钩子", "主题命题", "故事承诺", "世界观设定", "创作思路").any(premise::contains)) {
            issues += "简介混入内部策划字段"
        }
        val spoilerPatterns = listOf(
            Regex("直到.{0,20}(?:发现|明白|知道)"),
            Regex("最终.{0,20}(?:发现|揭开|明白|知道)"),
            Regex("真正被选中的"),
            Regex("原来.{0,30}(?:才是|一直是|就是)"),
            Regex("其实.{0,30}(?:才是|一直是|就是)"),
        )
        if (spoilerPatterns.any { it.containsMatchIn(premise) }) issues += "简介提前泄露中后期答案/反转"
        return IdentityQuality(issues.isEmpty(), issues)
    }

    private fun localTitleFallback(title: String, premise: String): String {
        val clean = title
            .replace(Regex("[《》“”\"'，,。！？!?：:；;、\\s]"), "")
            .replace(Regex("^(我为|为了|只为|当我|如果|开局|穿越后|重生后)"), "")
        if (clean.length in 2..10) return clean
        return when {
            "梦" in premise && ("失踪" in premise || "寻找" in premise || "找" in premise) -> "梦域寻踪"
            "梦" in premise -> "梦域"
            "时间" in premise -> "失序时刻"
            "深渊" in premise -> "深渊回声"
            "怪谈" in premise || "诡" in premise -> "异闻录"
            else -> clean.take(8).ifBlank { "无归之境" }
        }
    }

    private fun localSynopsisFallback(text: String): String {
        val normalized = text.replace(Regex("\\s+"), "").trim()
        if (normalized.isBlank()) return "一个普通人的生活因一次无法解释的异常被彻底打破。为了找回最重要的人，他不得不主动进入危险未知之中，而每一次选择都会让真相更近，也让退路更少。"

        val spoiler = Regex("(?:直到.{0,20}(?:发现|明白|知道)|最终.{0,20}(?:发现|揭开|明白|知道)|真正被选中的|原来|其实)")
        val sentences = normalized.split(Regex("(?<=[。！？!?])"))
        val kept = mutableListOf<String>()
        var length = 0
        for (sentence in sentences) {
            val s = sentence.trim()
            if (s.isBlank()) continue
            if (spoiler.containsMatchIn(s) && length >= 80) break
            if (s.count { it == '、' } >= 3 && length >= 80) break
            if (length + s.length > 200) break
            kept += s
            length += s.length
            if (length >= 150) break
        }
        val joined = kept.joinToString("")
        return when {
            joined.length in 100..220 -> joined
            normalized.length <= 200 -> normalized
            else -> normalized.take(190).trimEnd('，', '、', '：', ':') + "……"
        }
    }
}

private class NewBookFoundationEngine(
    private val gateway: AiGateway,
) {
    suspend fun build(
        proposal: NewBookProposal,
        messages: List<CreationChatMessage>,
        current: StoryFoundation?,
        instruction: String,
    ): StoryFoundation {
        val transcript = transcript(messages)
        val currentText = current?.toPromptSummary()?.let { "\n当前蓝图：\n$it\n" }.orEmpty()
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的长篇小说总架构师。把已经确认的新书方向扩展为可直接进入长期创作的原创蓝图。不要写正文，不要堆空泛设定，所有规则都必须服务于人物选择和后续剧情。

                    必须输出 GeneratedChapter JSON，按下面规则编码蓝图：
                    - title：最终正式书名，2-8个汉字为佳，最长12个可见字符；不要营销长句。
                    - content：最终“平台简介”，120-190个中文字符左右。只写故事起点、主角目标、核心异常/规则和当下悬念；禁止写完整剧情梗概、罗列副本、解释主题、泄露中后期反转和终局答案。
                    - summary：80-180字“故事承诺”，说明读者长期会获得什么体验、主线如何逐步升级。内部策划信息放这里，不要塞进 content。
                    - touchedForeshadowingIds=[]。
                    - stateChanges 用作结构化蓝图记录，字段必须严格遵守以下编码：

                    1) 恰好1条 META：subject="META"；field=小说类型；before=目标总字数纯数字；after=主题命题；evidence=一句话核心钩子。
                    2) 恰好1条 STYLE：subject="STYLE"；field="叙事风格"；before=故事承诺补充；after=详细写作风格基线；evidence=封面视觉方向。
                    3) 恰好1条 MASTER：subject="MASTER"；field=总纲标题；before=全书总目标；after=全书核心冲突；evidence=全书最大转折/终局方向。
                    4) 8-18条小说圣经：subject="BIBLE:分类"，分类只能是 WORLD/RULE/CHARACTER/FACTION/LOCATION/ITEM/STYLE/FORBIDDEN；field=设定名称；before=可直接约束写作的具体内容；after=别名用顿号分隔或留空；evidence留空。至少包含 WORLD、RULE、STYLE、FORBIDDEN；有势力或地点时必须建对应条目。
                    5) 3-8条角色：subject="CHAR"；field=角色名；before=3-6个性格词，用顿号分隔；after=当前长期目标；evidence 必须严格为“地点||身体状态||情绪状态||已知秘密(顿号分隔)||持有物(顿号分隔)||人物A=关系说明；人物B=关系说明”。第一条必须是主角。
                    6) 3-6条卷纲：subject="VOLUME:序号"；field=卷名；before=本卷目标；after=本卷核心冲突；evidence=本卷关键转折。序号从1开始连续。
                    7) 第一卷必须生成10-14条详细章纲：subject="CHAPTER:1:章序号"；field=章名；before=本章明确目标；after=本章具体冲突；evidence=章末转折。不要提前写正文。后续卷暂不展开章纲，等写作推进后再动态规划。
                    8) 3-6条伏笔计划：subject="FORESHADOW"；field=伏笔名；before=伏笔细节；after=预期回收方式；evidence="预计开始章-预计结束章"，例如“3-18”。

                    长篇稳定性要求：
                    - 设定数量宁可少而硬，不要百科全书式堆砌。
                    - 每卷都要让人物状态、信息差和核心问题发生不可逆变化。
                    - 第一卷10-14章必须形成清晰因果链，不能只是连续遭遇怪事。
                    - 必须包含明确的禁写规则，防止后续AI为了刺激临时改世界规则、人物智商或核心主题。
                    - 如果用户提到某作品/作者，只提炼高层叙事特征，重新设计原创人物、规则和谜团，不复制标志性表达或剧情骨架。
                """.trimIndent(),
                user = """
                    已确认方案：
                    书名：${proposal.title}
                    类型：${proposal.genre}
                    平台简介：${proposal.premise}
                    主题：${proposal.theme}
                    目标字数：${proposal.targetWords}
                    核心钩子：${proposal.coreHook}
                    封面方向：${proposal.coverBrief}
                    内部策划摘要：${proposal.rationale}

                    用户会谈：
                    $transcript
                    $currentText
                    本轮要求：$instruction

                    请输出最新、完整的一整套蓝图，不要只输出改动部分。
                """.trimIndent(),
            )
        )
        val raw = parseFoundation(output, proposal)
        val refined = BookIdentityRefiner.refine(
            gateway = gateway,
            proposal = raw.toProposal(),
            transcript = transcript,
            forceRewrite = false,
            userInstruction = "",
        )
        return raw.copy(title = refined.title, premise = refined.premise)
    }

    private fun parseFoundation(output: GeneratedChapter, fallback: NewBookProposal): StoryFoundation {
        val changes = output.stateChanges
        val meta = changes.firstOrNull { it.subject.equals("META", ignoreCase = true) }
        val style = changes.firstOrNull { it.subject.equals("STYLE", ignoreCase = true) }
        val master = changes.firstOrNull { it.subject.equals("MASTER", ignoreCase = true) }

        val bible = changes.mapNotNull { change ->
            if (!change.subject.startsWith("BIBLE:", ignoreCase = true)) return@mapNotNull null
            val code = change.subject.substringAfter(':').trim().uppercase()
            val category = runCatching { BibleCategory.valueOf(code) }.getOrNull() ?: return@mapNotNull null
            FoundationBibleItem(
                category = category,
                name = change.field.trim(),
                content = change.before.trim(),
                aliases = splitList(change.after),
                locked = true,
            )
        }.take(32)

        val characters = changes.filter { it.subject.equals("CHAR", ignoreCase = true) }.map { change ->
            val parts = change.evidence.split("||")
            FoundationCharacter(
                name = change.field.trim(),
                personality = splitList(change.before).ifEmpty { listOf("克制") },
                location = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "故事起点" },
                physicalState = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "正常" },
                emotionalState = parts.getOrNull(2)?.trim().orEmpty().ifBlank { "平静" },
                goal = change.after.trim(),
                knownSecrets = splitList(parts.getOrNull(3).orEmpty()),
                possessions = splitList(parts.getOrNull(4).orEmpty()),
                relationships = parseRelationships(parts.getOrNull(5).orEmpty()),
            )
        }.take(16).ifEmpty {
            listOf(
                FoundationCharacter(
                    name = "主角",
                    personality = listOf("克制", "谨慎", "有判断力"),
                    location = "故事起点",
                    physicalState = "正常",
                    emotionalState = "平静",
                    goal = "弄清核心异常，并守住自己不能失去的东西",
                )
            )
        }

        val volumeRecords = changes.mapNotNull { change ->
            if (!change.subject.startsWith("VOLUME:", ignoreCase = true)) return@mapNotNull null
            val order = change.subject.substringAfter(':').trim().toIntOrNull() ?: return@mapNotNull null
            order to change
        }.sortedBy { it.first }.take(6)

        val chapterRecords = changes.mapNotNull { change ->
            if (!change.subject.startsWith("CHAPTER:", ignoreCase = true)) return@mapNotNull null
            val parts = change.subject.split(':')
            val volumeOrder = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val chapterOrder = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            Triple(volumeOrder, chapterOrder, change)
        }

        val volumes = volumeRecords.map { (order, change) ->
            FoundationVolume(
                order = order,
                title = change.field.trim().ifBlank { "第${order}卷" },
                objective = change.before.trim(),
                conflict = change.after.trim(),
                turningPoint = change.evidence.trim(),
                chapters = chapterRecords
                    .filter { it.first == order }
                    .sortedBy { it.second }
                    .take(if (order == 1) 14 else 0)
                    .map { (_, chapterOrder, chapter) ->
                        FoundationChapter(
                            order = chapterOrder,
                            title = chapter.field.trim().ifBlank { "第${chapterOrder}章" },
                            objective = chapter.before.trim(),
                            conflict = chapter.after.trim(),
                            turningPoint = chapter.evidence.trim(),
                        )
                    },
            )
        }.ifEmpty {
            listOf(
                FoundationVolume(
                    order = 1,
                    title = "第一卷",
                    objective = "建立主角、规则和核心问题，并让主角主动进入主线。",
                    conflict = "主角现实目标与核心异常正面冲突。",
                    turningPoint = "主角获得无法忽视的新证据。",
                    chapters = chapterRecords.filter { it.first == 1 }.sortedBy { it.second }.map { (_, chapterOrder, chapter) ->
                        FoundationChapter(chapterOrder, chapter.field, chapter.before, chapter.after, chapter.evidence)
                    },
                )
            )
        }

        val foreshadowing = changes.filter { it.subject.equals("FORESHADOW", ignoreCase = true) }.map { change ->
            val range = Regex("(\\d+)\\D+(\\d+)").find(change.evidence)
            val start = range?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 3
            val end = range?.groupValues?.getOrNull(2)?.toIntOrNull() ?: maxOf(start, 18)
            FoundationForeshadow(
                title = change.field.trim(),
                detail = change.before.trim(),
                expectedPayoff = change.after.trim(),
                expectedChapterStart = start,
                expectedChapterEnd = end,
            )
        }.take(12)

        val target = meta?.before?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(10_000, 5_000_000) ?: fallback.targetWords
        return StoryFoundation(
            title = output.title.trim().ifBlank { fallback.title },
            genre = meta?.field?.trim().orEmpty().ifBlank { fallback.genre },
            premise = output.content.trim().ifBlank { fallback.premise },
            theme = meta?.after?.trim().orEmpty().ifBlank { fallback.theme },
            targetWords = target,
            coreHook = meta?.evidence?.trim().orEmpty().ifBlank { fallback.coreHook },
            storyPromise = output.summary.trim().ifBlank { style?.before?.trim().orEmpty() }.ifBlank { fallback.rationale },
            styleGuide = style?.after?.trim().orEmpty().ifBlank { "保持人物行动有因果，信息逐层释放，避免无铺垫反转与临时改规则。" },
            coverBrief = style?.evidence?.trim().orEmpty().ifBlank { fallback.coverBrief },
            masterTitle = master?.field?.trim().orEmpty().ifBlank { "总纲" },
            masterObjective = master?.before?.trim().orEmpty().ifBlank { fallback.premise },
            masterConflict = master?.after?.trim().orEmpty().ifBlank { "主人公追求核心目标的同时，必须承担不断升级的真实代价。" },
            masterTurningPoint = master?.evidence?.trim().orEmpty().ifBlank { "终局重新定义此前所有线索，并迫使主人公做出不可逆选择。" },
            bible = bible,
            characters = characters,
            volumes = volumes,
            foreshadowing = foreshadowing,
        )
    }
}

private fun StoryFoundation.toProposal() = NewBookProposal(
    title = title,
    genre = genre,
    premise = premise,
    theme = theme,
    targetWords = targetWords,
    coreHook = coreHook,
    coverBrief = coverBrief,
    rationale = storyPromise,
)

private fun StoryFoundation.toPromptSummary(): String = buildString {
    appendLine("书名：$title")
    appendLine("类型：$genre")
    appendLine("平台简介：$premise")
    appendLine("主题：$theme")
    appendLine("核心钩子：$coreHook")
    appendLine("故事承诺：$storyPromise")
    appendLine("风格基线：$styleGuide")
    appendLine("总纲：$masterObjective / $masterConflict / $masterTurningPoint")
    appendLine("圣经：${bible.joinToString("；") { "${it.category.name}:${it.name}=${it.content}" }}")
    appendLine("角色：${characters.joinToString("；") { "${it.name}[${it.personality.joinToString("、")}]目标=${it.goal}" }}")
    volumes.forEach { volume ->
        appendLine("第${volume.order}卷 ${volume.title}：${volume.objective} / ${volume.conflict} / ${volume.turningPoint}")
        volume.chapters.forEach { chapter ->
            appendLine("  ${chapter.order}. ${chapter.title}：${chapter.objective} / ${chapter.conflict} / ${chapter.turningPoint}")
        }
    }
    appendLine("伏笔：${foreshadowing.joinToString("；") { "${it.title}:${it.detail}->${it.expectedPayoff}" }}")
}

/**
 * Raw web evidence is useful only on the turn that produced it. Older turns keep the user's wording
 * but drop hidden result dumps; durable research evidence is supplied separately by the archive.
 * This prevents a long research chat from re-sending tens of thousands of stale web characters.
 */
private fun transcript(messages: List<CreationChatMessage>): String {
    val recent = messages.takeLast(18)
    val lastUser = recent.indexOfLast { it.role == "user" }
    return recent.mapIndexed { index, message ->
        val text = if (message.role == "user" && index != lastUser) {
            message.text.substringBefore(RESEARCH_CONTEXT_MARKER).trimEnd()
        } else {
            message.text
        }
        if (message.role == "user") "用户：$text" else "琅嬛：$text"
    }.joinToString("\n")
}

private fun splitList(text: String): List<String> = text
    .split(Regex("[、,，;；]"))
    .map { it.trim() }
    .filter { it.isNotBlank() }

private fun parseRelationships(text: String): Map<String, String> = text
    .split(Regex("[;；]"))
    .mapNotNull { item ->
        val index = item.indexOf('=')
        if (index <= 0) null else item.substring(0, index).trim() to item.substring(index + 1).trim()
    }
    .filter { it.first.isNotBlank() && it.second.isNotBlank() }
    .toMap()
