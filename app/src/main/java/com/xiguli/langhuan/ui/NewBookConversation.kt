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
import com.xiguli.langhuan.data.NewStoryRequest
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
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
    val isBusy: Boolean = false,
    val createdStoryId: String? = null,
    val error: String? = null,
)

class NewBookConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val projects = StoryProjectManager(application)
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
        if (clean.isBlank() || _state.value.isBusy) return
        val userMessage = CreationChatMessage("user", clean)
        val history = _state.value.messages + userMessage
        _state.update { it.copy(messages = history, isBusy = true, error = null) }

        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(isBusy = false, error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            runCatching { NewBookConversationEngine(gateway).reply(history) }
                .onSuccess { turn ->
                    _state.update {
                        it.copy(
                            messages = it.messages + CreationChatMessage("assistant", turn.reply),
                            proposal = turn.proposal ?: it.proposal,
                            isBusy = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isBusy = false, error = e.message ?: "AI 构思失败") }
                }
        }
    }

    fun createCurrentProposal() {
        val proposal = _state.value.proposal ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching {
                projects.createStory(
                    NewStoryRequest(
                        title = proposal.title,
                        genre = proposal.genre,
                        premise = proposal.premise,
                        theme = proposal.theme,
                        targetWords = proposal.targetWords,
                    )
                )
            }.onSuccess { created ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        createdStoryId = created.snapshot.novel.id,
                        messages = it.messages + CreationChatMessage(
                            "assistant",
                            "《${created.snapshot.novel.title}》已经建好了。接下来可以从作品详情继续生成封面，也可以直接进入创作工作台完善总纲。",
                        ),
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message ?: "创建小说失败") }
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
                    5. 当信息已经足够时，输出一套可直接建书的方案：
                       - title = 建议书名，不要套模板；
                       - content = 150-260 字作品简介，像真正小说平台简介；
                       - summary = 80-180 字解释这套方案的核心卖点、差异化和叙事气质；
                       - stateChanges 必须只返回 1 项，其中 subject=小说类型，field=主题命题，before=目标总字数（只写数字），after=一句话核心钩子，evidence=封面视觉简报；
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
                append("\n不满意就直接继续说你想改哪里，我会基于这一版继续迭代。")
            },
            proposal = proposal,
        )
    }
}

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
                        Text("先聊想法，满意后再创建", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, "返回")
                    }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("比如：我想写一本中式悬疑……") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
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

            items(state.messages) { message ->
                ChatBubble(message)
            }

            state.proposal?.let { proposal ->
                item {
                    ProposalCard(
                        proposal = proposal,
                        busy = state.isBusy,
                        onCreate = viewModel::createCurrentProposal,
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
                        Text("AI 正在整理你的想法……")
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
    onCreate: () -> Unit,
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
            Text("不满意就继续在下面跟 AI 说怎么改；创建前不会写入书架。", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("创建这本书")
            }
        }
    }
}
