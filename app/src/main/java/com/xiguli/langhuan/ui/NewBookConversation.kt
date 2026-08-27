package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHAT_SENTINEL = "__CHAT__"

data class CreationChatMessage(
    val role: String,
    val text: String,
)

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

class NewBookConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val foundationApplier = StoryFoundationApplier(application)
    private val _state = MutableStateFlow(NewBookConversationState())
    val state: StateFlow<NewBookConversationState> = _state.asStateFlow()
    private var activeProviderId: String? = null

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                activeProviderId = providers.firstOrNull { it.isDefault }?.id ?: providers.firstOrNull()?.id
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
                busyLabel = if (before.foundation == null) "AI 正在继续构思……" else "AI 正在按你的要求重构建书蓝图……",
                error = null,
            )
        }

        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(isBusy = false, busyLabel = "", error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
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
                }.onFailure { e ->
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = e.message ?: "正式建书失败") }
                }
        }
    }

    fun reset() {
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
        val transcript = messages.takeLast(18).joinToString("\n") { message ->
            if (message.role == "user") "用户：${message.text}" else "琅嬛：${message.text}"
        }
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书策划搭档。你的任务不是让用户先填表，而是通过自然对话逐步把一个模糊想法发展成原创长篇小说方案。

                    对话规则：
                    1. 先理解用户真正想要的阅读体验，再决定是否追问。一次最多追问 1-2 个最关键问题，不要像问卷。
                    2. 用户可能问“你知道某本小说吗”“你知道某位作者吗”。如果你确实掌握，就简短说明你理解的公开、高层特征；如果不确定，必须明确说不确定并让用户补充，不得假装知道。
                    3. 用户要求“按某作品/作者风格”时，只能提炼高层次创作特征，例如氛围、节奏、叙事距离、谜题结构、信息释放方式、情绪强度，再创作全新的角色、世界规则、核心谜团和情节。不要复刻标志性句式，不要换名照搬人物、设定或剧情骨架。
                    4. 当信息还不足以形成靠谱方案时，输出 GeneratedChapter JSON：title 必须为 __CHAT__；content=你这一轮自然、简洁的回复或追问；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    5. 当信息已经足够时，输出一套可直接进入“建书蓝图”阶段的方案：
                       - title = 建议书名，不要套模板；
                       - content = 150-260 字作品简介，像真正小说平台简介；
                       - summary = 80-180 字解释核心卖点、差异化和叙事气质；
                       - stateChanges 只返回 1 项，其中 subject=小说类型，field=主题命题，before=目标总字数（只写数字），after=一句话核心钩子，evidence=封面视觉简报；
                       - touchedForeshadowingIds=[]。
                    6. 只要用户继续说“名字换一个”“更诡异一点”“不要系统”“简介短一点”等，就继续迭代，并重新给出最新完整方案。
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
        val proposal = NewBookProposal(
            title = output.title.trim().ifBlank { "未命名小说" },
            genre = meta.subject.trim().ifBlank { "未分类" },
            premise = output.content.trim().ifBlank { "围绕一个不可回避的核心冲突展开。" },
            theme = meta.field.trim().ifBlank { "人在真相与代价之间如何选择" },
            targetWords = target,
            coreHook = meta.after.trim().ifBlank { "一个看似普通的选择，逐渐暴露出更大的真相。" },
            coverBrief = meta.evidence.trim(),
            rationale = output.summary.trim(),
        )
        return ConversationTurn(
            reply = buildString {
                append("我已经把方向收束成一套可以落地的方案。")
                if (proposal.rationale.isNotBlank()) append("\n").append(proposal.rationale)
                append("\n不满意就继续说你想改哪里；满意的话，下一步我会把它扩成世界观、人物、总纲、卷纲和前期章纲。")
            },
            proposal = proposal,
        )
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
        val transcript = messages.takeLast(18).joinToString("\n") { message ->
            if (message.role == "user") "用户：${message.text}" else "琅嬛：${message.text}"
        }
        val currentText = current?.toPromptSummary()?.let { "\n当前蓝图：\n$it\n" }.orEmpty()
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的长篇小说总架构师。把已经确认的新书方向扩展为可直接进入长期创作的原创蓝图。不要写正文，不要堆空泛设定，所有规则都必须服务于人物选择和后续剧情。

                    必须输出 GeneratedChapter JSON，按下面规则编码蓝图：
                    - title：最终书名。
                    - content：最终作品简介，150-260字。
                    - summary：80-180字“故事承诺”，说明读者长期会获得什么体验、主线如何逐步升级。
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
                    简介：${proposal.premise}
                    主题：${proposal.theme}
                    目标字数：${proposal.targetWords}
                    核心钩子：${proposal.coreHook}
                    封面方向：${proposal.coverBrief}
                    方案理由：${proposal.rationale}

                    用户会谈：
                    $transcript
                    $currentText
                    本轮要求：$instruction

                    请输出最新、完整的一整套蓝图，不要只输出改动部分。
                """.trimIndent(),
            )
        )
        return parseFoundation(output, proposal)
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
    appendLine("简介：$premise")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBookConversationPage(
    viewModel: NewBookConversationViewModel,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.createdStoryId) {
        state.createdStoryId?.let { id ->
            viewModel.consumeCreatedStory()
            onCreated(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("和 AI 聊出一本小说", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                state.foundation != null -> "蓝图阶段：继续聊天修改，满意后正式建书"
                                state.proposal != null -> "方案已成形，下一步搭世界、人物和三级大纲"
                                else -> "先聊想法，满意后再定方案"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = viewModel::reset, enabled = !state.isBusy) {
                        Icon(Icons.Rounded.Refresh, "重新开始")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (state.foundation == null) "比如：我想写一本中式悬疑……"
                                else "比如：第二卷太拖，主角再克制一点，前五章节奏加快……"
                            )
                        },
                        minLines = 1,
                        maxLines = 5,
                        enabled = !state.isBusy,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val text = input
                            input = ""
                            viewModel.send(text)
                        },
                        enabled = input.isNotBlank() && !state.isBusy,
                    ) {
                        Icon(Icons.Rounded.Send, "发送")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (state.messages.size <= 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("你可以直接这样说", style = MaterialTheme.typography.labelLarge)
                        StarterChip("我想写一本中式悬疑，主角是普通人") { viewModel.send(it) }
                        StarterChip("你知道《迷雾之上》吗？我喜欢那种逐层揭谜的压迫感") { viewModel.send(it) }
                        StarterChip("我喜欢冷峻、克制、诡谲的悬疑感，帮我原创一个完全不同的故事") { viewModel.send(it) }
                    }
                }
            }

            items(state.messages) { message -> ChatBubble(message) }

            if (state.foundation == null) {
                state.proposal?.let { proposal ->
                    item {
                        ProposalCard(
                            proposal = proposal,
                            busy = state.isBusy,
                            onNext = { viewModel.generateFoundation(false) },
                        )
                    }
                }
            } else {
                item {
                    FoundationCard(
                        foundation = state.foundation,
                        busy = state.isBusy,
                        onRegenerate = { viewModel.generateFoundation(true) },
                        onCreate = viewModel::createCurrentFoundation,
                    )
                }
            }

            if (state.isBusy) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(state.busyLabel.ifBlank { "AI 正在处理……" })
                    }
                }
            }

            state.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StarterChip(text: String, onClick: (String) -> Unit) {
    AssistChip(
        onClick = { onClick(text) },
        label = { Text(text) },
        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
    )
}

@Composable
private fun ChatBubble(message: CreationChatMessage) {
    val user = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (user) "你" else "琅嬛 AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: NewBookProposal,
    busy: Boolean,
    onNext: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("新书方案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(proposal.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${proposal.genre} · 目标 ${proposal.targetWords / 10_000} 万字", style = MaterialTheme.typography.labelLarge)
            Text("简介", fontWeight = FontWeight.SemiBold)
            Text(proposal.premise)
            Text("核心钩子", fontWeight = FontWeight.SemiBold)
            Text(proposal.coreHook)
            Text("主题", fontWeight = FontWeight.SemiBold)
            Text(proposal.theme)
            if (proposal.coverBrief.isNotBlank()) {
                Text("封面方向", fontWeight = FontWeight.SemiBold)
                Text(proposal.coverBrief, style = MaterialTheme.typography.bodyMedium)
            }
            Text("这里还只是方向，不会立刻建一个空工程。下一步先让 AI 把世界、人物和大纲搭完整。", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onNext,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("下一步：生成建书蓝图")
            }
        }
    }
}

@Composable
private fun FoundationCard(
    foundation: StoryFoundation,
    busy: Boolean,
    onRegenerate: () -> Unit,
    onCreate: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("建书蓝图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(foundation.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${foundation.genre} · ${foundation.targetWords / 10_000} 万字", style = MaterialTheme.typography.labelLarge)

            BlueprintSection("故事承诺", foundation.storyPromise)
            BlueprintSection("叙事风格基线", foundation.styleGuide)
            BlueprintSection("总纲", "${foundation.masterObjective}\n核心冲突：${foundation.masterConflict}\n关键转折：${foundation.masterTurningPoint}")

            Text("小说圣经 · ${foundation.bible.size} 条", fontWeight = FontWeight.Bold)
            foundation.bible.take(18).forEach { item ->
                Text("${item.category.name} · ${item.name}\n${item.content}", style = MaterialTheme.typography.bodyMedium)
            }

            Text("核心角色 · ${foundation.characters.size} 人", fontWeight = FontWeight.Bold)
            foundation.characters.forEachIndexed { index, character ->
                Text(
                    "${if (index == 0) "主角 · " else ""}${character.name}｜${character.personality.joinToString("、")}\n目标：${character.goal}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("分卷与前期路线 · ${foundation.volumes.size} 卷", fontWeight = FontWeight.Bold)
            foundation.volumes.forEach { volume ->
                Text("第${volume.order}卷 · ${volume.title}", fontWeight = FontWeight.SemiBold)
                Text("目标：${volume.objective}\n冲突：${volume.conflict}\n转折：${volume.turningPoint}", style = MaterialTheme.typography.bodyMedium)
                if (volume.chapters.isNotEmpty()) {
                    volume.chapters.forEach { chapter ->
                        Text(
                            "${chapter.order}. ${chapter.title} — ${chapter.objective}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (foundation.foreshadowing.isNotEmpty()) {
                Text("伏笔计划 · ${foundation.foreshadowing.size} 条", fontWeight = FontWeight.Bold)
                foundation.foreshadowing.forEach { item ->
                    Text(
                        "${item.title}｜${item.expectedChapterStart}-${item.expectedChapterEnd}章\n${item.detail} → ${item.expectedPayoff}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text("这一步仍未写入书架。继续在下面聊天，就会修改这套蓝图；确认后才一次性写入小说圣经、角色状态、三级大纲和 RAG 长期记忆。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("整套重做")
                }
                Button(
                    onClick = onCreate,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("正式建书")
                }
            }
        }
    }
}

@Composable
private fun BlueprintSection(title: String, content: String) {
    if (content.isBlank()) return
    Text(title, fontWeight = FontWeight.Bold)
    Text(content, style = MaterialTheme.typography.bodyMedium)
}
