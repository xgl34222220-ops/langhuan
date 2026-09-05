package com.xiguli.langhuan.ui.story

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import com.xiguli.langhuan.ui.reader.LibraryExperienceState
import com.xiguli.langhuan.ui.reader.ReaderBookUi
import com.xiguli.langhuan.ui.theme.LanghuanShape
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
enum class StoryVariableAccess {
    AI_READ_WRITE, AI_READ_ONLY, AUTHOR_LOCKED;
    val label: String get() = when (this) {
        AI_READ_WRITE -> "AI 可读写"
        AI_READ_ONLY -> "AI 只读"
        AUTHOR_LOCKED -> "作者锁定"
    }
}

@Serializable
data class StoryPlayerProfile(
    val name: String = "",
    val identity: String = "",
    val traits: String = "",
    val knownFacts: String = "",
    val forbiddenKnowledge: String = "",
)

@Serializable
data class StoryPlayVariable(
    val subject: String,
    val field: String,
    val value: String,
    val evidence: String = "",
    val access: StoryVariableAccess = StoryVariableAccess.AI_READ_WRITE,
)

@Serializable
data class StoryPlayTurn(
    val id: String = UUID.randomUUID().toString(),
    val player: String = "",
    val narration: String = "",
    val choices: List<String> = emptyList(),
    val variablesAfter: List<StoryPlayVariable> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StoryPlaySession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "故事分支",
    val anchorChapter: Int,
    val anchorTitle: String = "",
    val worldSnapshot: String = "",
    val playerProfile: StoryPlayerProfile = StoryPlayerProfile(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val turns: List<StoryPlayTurn> = emptyList(),
    val variables: List<StoryPlayVariable> = emptyList(),
    val chapterDraftCandidate: String = "",
)

@Serializable
data class StoryPlayArchive(
    val novelId: String,
    val activeSessionId: String = "",
    val sessions: List<StoryPlaySession> = emptyList(),
)

data class StoryPlayUiState(
    val novelId: String = "",
    val active: StoryPlaySession? = null,
    val sessions: List<StoryPlaySession> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class StoryPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryPlayUiState())
    val state: StateFlow<StoryPlayUiState> = _state.asStateFlow()

    fun open(novelId: String, anchorChapter: Int, anchorTitle: String, anchorText: String) {
        if (_state.value.novelId == novelId && _state.value.active != null) return
        viewModelScope.launch {
            val archive = loadArchive(novelId)
            val seed = anchorText.takeLast(2_800)
            var active = archive.sessions.firstOrNull { it.id == archive.activeSessionId }
                ?: archive.sessions.maxByOrNull { it.updatedAt }
                ?: StoryPlaySession(
                    anchorChapter = anchorChapter,
                    anchorTitle = anchorTitle,
                    worldSnapshot = seed,
                    title = "从第 $anchorChapter 章开始",
                )
            if (active.worldSnapshot.isBlank()) active = active.copy(worldSnapshot = seed, anchorTitle = active.anchorTitle.ifBlank { anchorTitle })
            val sessions = if (archive.sessions.any { it.id == active.id }) {
                archive.sessions.map { if (it.id == active.id) active else it }
            } else archive.sessions + active
            saveArchive(StoryPlayArchive(novelId, active.id, sessions))
            _state.value = StoryPlayUiState(novelId = novelId, active = active, sessions = sessions)
        }
    }

    fun selectSession(id: String) {
        val current = _state.value
        val selected = current.sessions.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(active = selected, error = null, notice = null) }
        saveCurrent(current.novelId, selected, current.sessions)
    }

    fun newBranch(anchorChapter: Int, anchorTitle: String, anchorText: String) {
        val current = _state.value
        if (current.novelId.isBlank() || current.busy) return
        val session = StoryPlaySession(
            anchorChapter = anchorChapter,
            anchorTitle = anchorTitle,
            worldSnapshot = anchorText.takeLast(2_800),
            playerProfile = current.active?.playerProfile ?: StoryPlayerProfile(),
            title = "分支 ${current.sessions.size + 1} · 第 $anchorChapter 章",
        )
        val sessions = current.sessions + session
        _state.update { it.copy(active = session, sessions = sessions, notice = "已创建独立故事分支", error = null) }
        saveCurrent(current.novelId, session, sessions)
    }

    fun updateProfile(profile: StoryPlayerProfile) = mutate("角色卡已保存") { it.copy(playerProfile = profile) }

    fun setVariableAccess(subject: String, field: String, access: StoryVariableAccess) = mutate("变量权限已更新") { session ->
        session.copy(variables = session.variables.map { v -> if (v.subject == subject && v.field == field) v.copy(access = access) else v })
    }

    fun rewindTo(turnId: String) = mutate("已回溯到该节点，后续演绎已移除") { session ->
        val index = session.turns.indexOfFirst { it.id == turnId }
        if (index < 0) session else {
            val kept = session.turns.take(index + 1)
            val restored = kept.lastOrNull()?.variablesAfter?.takeIf { it.isNotEmpty() } ?: session.variables
            session.copy(turns = kept, variables = restored, chapterDraftCandidate = "")
        }
    }

    fun act(book: ReaderBookUi, chapterText: String, action: String) {
        val clean = action.trim()
        val session = _state.value.active ?: return
        if (clean.isBlank() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val gateway = activeGateway()
                val visibleVars = session.variables.filter { it.access != StoryVariableAccess.AUTHOR_LOCKED }
                val vars = visibleVars.joinToString("\n") { "${it.subject}.${it.field}=${it.value} [${it.access.label}]" }
                val recent = session.turns.takeLast(10).joinToString("\n\n") { "玩家：${it.player}\n叙事：${it.narration}" }
                val p = session.playerProfile
                val output = gateway.generate(PromptBundle(
                    system = """
                        你是琅嬛互动小说导演 DM。当前是原小说的独立分支，绝不能改写原著正文。
                        严格尊重世界观、时代技术、人物性格和知识边界；角色不知道的信息不能凭空知道。
                        玩家“禁止提前知道”的内容不得泄露，除非本轮剧情出现了明确的新证据。
                        每轮只推进一个连续场景，不替玩家做重大决定，不突然跳时间，不擅自结局。
                        变量标注为 AI 只读时只能参考，不能在 stateChanges 修改；作者锁定变量不会提供给你。
                        必须返回 GeneratedChapter JSON：title=story-turn；content=180-520字沉浸叙事；summary=3-4个可执行选择，每行一个；
                        stateChanges=仅记录本轮真实发生的状态变化，subject/field/before/after/evidence 完整；touchedForeshadowingIds=[]。
                        不要输出 JSON 外文字。
                    """.trimIndent(),
                    user = """
                        作品：《${book.title}》\n类型：${book.genre}\n简介：${book.premise}
                        分支锚点：第 ${session.anchorChapter} 章 ${session.anchorTitle}
                        世界快照：\n${session.worldSnapshot.ifBlank { chapterText.takeLast(2_800) }}
                        玩家角色：姓名=${p.name.ifBlank { "未命名" }}；身份=${p.identity.ifBlank { "未指定" }}；特征=${p.traits.ifBlank { "未指定" }}
                        玩家当前已知：${p.knownFacts.ifBlank { "仅知道剧情中已展示的信息" }}
                        禁止提前知道：${p.forbiddenKnowledge.ifBlank { "无额外声明" }}
                        当前可见变量：\n${vars.ifBlank { "暂无" }}
                        最近互动：\n${recent.ifBlank { "这是第一轮" }}
                        玩家本轮动作：$clean
                    """.trimIndent(),
                ))
                val changes = output.stateChanges.mapNotNull { c ->
                    val s = c.subject.trim(); val f = c.field.trim(); val value = c.after.trim()
                    if (s.isBlank() || f.isBlank() || value.isBlank()) null else StoryPlayVariable(s, f, value, c.evidence.trim())
                }
                val updatedVars = mergeVariables(session.variables, changes)
                val choices = output.summary.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotBlank() }.take(4)
                val turn = StoryPlayTurn(
                    player = clean,
                    narration = output.content.trim().ifBlank { error("AI 没有返回故事正文") },
                    choices = choices,
                    variablesAfter = updatedVars,
                )
                session.copy(
                    turns = session.turns + turn,
                    variables = updatedVars,
                    chapterDraftCandidate = "",
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess(::persistSuccess).onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: "故事生成失败") }
            }
        }
    }

    fun generateChapterDraft(book: ReaderBookUi) {
        val session = _state.value.active ?: return
        if (session.turns.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val transcript = session.turns.joinToString("\n\n") { "玩家动作：${it.player}\n剧情结果：${it.narration}" }
                val output = activeGateway().generate(PromptBundle(
                    system = """
                        你是中文网络小说编辑。把互动演绎记录改写为自然连贯的小说章节草稿。
                        不得出现“玩家、DM、选项、游戏”等界面痕迹，不新增演绎中没有依据的关键事实，不改变人物知识边界。
                        输出 GeneratedChapter JSON：title=合适章节名；content=1200-2600字小说正文；summary=一段章节摘要；stateChanges=[]；touchedForeshadowingIds=[]。
                    """.trimIndent(),
                    user = "作品：《${book.title}》\n锚点：第${session.anchorChapter}章 ${session.anchorTitle}\n演绎记录：\n$transcript",
                ))
                val body = output.content.trim().ifBlank { error("AI 没有返回章节草稿") }
                session.copy(
                    chapterDraftCandidate = buildString {
                        append(output.title.trim().ifBlank { "演绎章节草稿" }).append("\n\n").append(body)
                    },
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                persistSuccess(updated)
                _state.update { it.copy(notice = "章节草稿已生成；原正文没有被覆盖") }
            }.onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "章节草稿生成失败") } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    private suspend fun activeGateway(): UniversalAiGateway {
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull() ?: error("还没有可用 AI，请先配置模型")
        val config = repository.providerConfig(provider.id) ?: error("AI 配置不可用")
        return UniversalAiGateway(config)
    }

    private fun mergeVariables(old: List<StoryPlayVariable>, incoming: List<StoryPlayVariable>): List<StoryPlayVariable> {
        val map = old.associateBy { "${it.subject}\u0000${it.field}" }.toMutableMap()
        incoming.forEach { v ->
            val key = "${v.subject}\u0000${v.field}"
            val existing = map[key]
            when {
                existing == null -> map[key] = v
                existing.access == StoryVariableAccess.AI_READ_WRITE -> map[key] = v.copy(access = existing.access)
            }
        }
        return map.values.sortedWith(compareBy({ it.subject }, { it.field }))
    }

    private fun mutate(message: String, transform: (StoryPlaySession) -> StoryPlaySession) {
        val current = _state.value
        val active = current.active ?: return
        val updated = transform(active).copy(updatedAt = System.currentTimeMillis())
        val sessions = current.sessions.map { if (it.id == updated.id) updated else it }
        _state.update { it.copy(active = updated, sessions = sessions, notice = message, error = null) }
        saveCurrent(current.novelId, updated, sessions)
    }

    private fun persistSuccess(updated: StoryPlaySession) {
        val current = _state.value
        val sessions = current.sessions.map { if (it.id == updated.id) updated else it }
        _state.update { it.copy(active = updated, sessions = sessions, busy = false) }
        saveCurrent(current.novelId, updated, sessions)
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_play").apply { mkdirs() }.resolve("$novelId.json")
    private fun loadArchive(novelId: String): StoryPlayArchive {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryPlayArchive(novelId)
        return runCatching { json.decodeFromString(StoryPlayArchive.serializer(), file.readText()) }.getOrElse { StoryPlayArchive(novelId) }
    }
    private fun saveCurrent(novelId: String, active: StoryPlaySession, sessions: List<StoryPlaySession>) {
        if (novelId.isNotBlank()) saveArchive(StoryPlayArchive(novelId, active.id, sessions))
    }
    private fun saveArchive(archive: StoryPlayArchive) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StoryPlayArchive.serializer(), archive)) }
    }
}

@Composable
fun StoryPlayPanelV1(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val vm: StoryPlayViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val anchor = libraryState.readingChapter
        ?: libraryState.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
        ?: libraryState.chapters.lastOrNull()
    var input by remember(book.id, state.active?.id) { mutableStateOf("") }
    var branchMenu by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showVariables by remember { mutableStateOf(false) }
    var rewindTarget by remember { mutableStateOf<StoryPlayTurn?>(null) }
    var showDraft by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, anchor?.chapterNumber) {
        vm.open(book.id, anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的故事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(state.active?.title ?: "准备进入故事", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ showProfile = true }) { Icon(Icons.Rounded.Person, "玩家身份") }
            Box {
                IconButton({ branchMenu = true }) { Icon(Icons.Rounded.ForkRight, "故事分支") }
                DropdownMenu(branchMenu, { branchMenu = false }) {
                    state.sessions.sortedByDescending { it.updatedAt }.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.title) },
                            leadingIcon = { if (session.id == state.active?.id) Icon(Icons.Rounded.Check, null) },
                            onClick = { branchMenu = false; vm.selectSession(session.id) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("从当前章节新建分支") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = {
                            branchMenu = false
                            vm.newBranch(anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
                        },
                    )
                }
            }
        }

        if (!aiReady) {
            Surface(Modifier.padding(horizontal = 16.dp), shape = LanghuanShape.panel, color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null)
                    Text("故事模式需要先配置 AI", Modifier.padding(start = 8.dp).weight(1f))
                    TextButton(onAiSetup) { Text("去配置") }
                }
            }
        }

        val session = state.active
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(session?.playerProfile?.name?.ifBlank { "未设置玩家身份" } ?: "未设置玩家身份", fontWeight = FontWeight.Bold)
                            Text(session?.playerProfile?.identity?.ifBlank { "可扮演自己、原创角色或原著角色" } ?: "可扮演自己、原创角色或原著角色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ showProfile = true }) { Text("编辑") }
                    }
                }
            }

            if (session == null || session.turns.isEmpty()) item {
                Surface(shape = LanghuanShape.sheet, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(anchor?.let { "从第 ${it.chapterNumber} 章进入" } ?: "从作品开头进入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("进入时保存独立世界快照。原正文不动，可以随时回溯或另开分支。", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            session?.turns?.let { turns ->
                items(turns, key = { it.id }) { turn ->
                    if (turn.player.isNotBlank()) {
                        Surface(shape = LanghuanShape.card, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("你：${turn.player}", Modifier.fillMaxWidth().padding(14.dp))
                        }
                    }
                    Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(turn.narration, lineHeight = 28.sp)
                            if (turn.choices.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                turn.choices.forEach { choice ->
                                    OutlinedButton({ input = choice }, Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = LanghuanShape.card) {
                                        Text(choice, Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            TextButton({ rewindTarget = turn }, Modifier.align(Alignment.End)) {
                                Icon(Icons.Rounded.History, null); Spacer(Modifier.width(4.dp)); Text("从这里重来")
                            }
                        }
                    }
                }
            }

            if (session != null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showVariables = true }, Modifier.weight(1f), shape = LanghuanShape.card) {
                        Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(5.dp)); Text("变量 ${session.variables.size}")
                    }
                    Button(
                        onClick = { vm.generateChapterDraft(book); showDraft = true },
                        modifier = Modifier.weight(1f),
                        enabled = aiReady && !state.busy && session.turns.isNotEmpty(),
                        shape = LanghuanShape.card,
                    ) {
                        Icon(Icons.Rounded.EditNote, null); Spacer(Modifier.width(5.dp)); Text("转章节草稿")
                    }
                }
            }
        }

        state.error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f)); IconButton(vm::clearError) { Icon(Icons.Rounded.Close, "关闭") }
                }
            }
        }
        state.notice?.let { message ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); IconButton(vm::clearNotice) { Icon(Icons.Rounded.Close, "关闭") }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text("你要做什么、说什么……") }, minLines = 1, maxLines = 4,
                    shape = LanghuanShape.panel, enabled = !state.busy,
                )
                FilledIconButton(
                    onClick = { val action = input; input = ""; vm.act(book, anchor?.content.orEmpty(), action) },
                    enabled = aiReady && !state.busy && input.isNotBlank(), modifier = Modifier.size(54.dp),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Send, "发送")
                }
            }
        }
    }

    if (showProfile) PlayerProfileDialog(
        initial = state.active?.playerProfile ?: StoryPlayerProfile(),
        onDismiss = { showProfile = false },
        onSave = { vm.updateProfile(it); showProfile = false },
    )
    if (showVariables) VariableAccessDialog(
        variables = state.active?.variables.orEmpty(),
        onDismiss = { showVariables = false },
        onChange = vm::setVariableAccess,
    )
    rewindTarget?.let { turn ->
        AlertDialog(
            onDismissRequest = { rewindTarget = null },
            title = { Text("从这里重新演绎？") },
            text = { Text("保留这一轮，删除该分支之后的互动与状态；原著正文不会变化。") },
            confirmButton = { Button({ vm.rewindTo(turn.id); rewindTarget = null }) { Text("确认回溯") } },
            dismissButton = { TextButton({ rewindTarget = null }) { Text("取消") } },
        )
    }
    if (showDraft) ChapterDraftDialog(state.busy, state.active?.chapterDraftCandidate.orEmpty()) { showDraft = false }
}

@Composable
private fun PlayerProfileDialog(initial: StoryPlayerProfile, onDismiss: () -> Unit, onSave: (StoryPlayerProfile) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var identity by remember(initial) { mutableStateOf(initial.identity) }
    var traits by remember(initial) { mutableStateOf(initial.traits) }
    var known by remember(initial) { mutableStateOf(initial.knownFacts) }
    var forbidden by remember(initial) { mutableStateOf(initial.forbiddenKnowledge) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("玩家身份 / 角色卡") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("姓名") }, singleLine = true)
                OutlinedTextField(identity, { identity = it }, Modifier.fillMaxWidth(), label = { Text("身份") })
                OutlinedTextField(traits, { traits = it }, Modifier.fillMaxWidth(), label = { Text("性格 / 特征") }, maxLines = 3)
                OutlinedTextField(known, { known = it }, Modifier.fillMaxWidth(), label = { Text("当前已知信息") }, maxLines = 4)
                OutlinedTextField(forbidden, { forbidden = it }, Modifier.fillMaxWidth(), label = { Text("禁止提前知道") }, maxLines = 4)
                Text("知识边界会直接约束 DM，避免角色凭空知道后续秘密。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button({ onSave(StoryPlayerProfile(name.trim(), identity.trim(), traits.trim(), known.trim(), forbidden.trim())) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun VariableAccessDialog(
    variables: List<StoryPlayVariable>,
    onDismiss: () -> Unit,
    onChange: (String, String, StoryVariableAccess) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("故事变量权限") },
        text = {
            if (variables.isEmpty()) Text("还没有变量。AI 发现真实状态变化后会自动创建。")
            else LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(variables, key = { "${it.subject}:${it.field}" }) { variable ->
                    Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("${variable.subject} · ${variable.field}", fontWeight = FontWeight.Bold)
                            Text(variable.value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                StoryVariableAccess.values().forEach { access ->
                                    FilterChip(
                                        selected = variable.access == access,
                                        onClick = { onChange(variable.subject, variable.field, access) },
                                        label = { Text(access.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ChapterDraftDialog(busy: Boolean, draft: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("演绎转章节草稿") },
        text = {
            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("正在整理演绎记录……", Modifier.padding(start = 10.dp))
                }
                draft.isBlank() -> Text("还没有生成草稿。")
                else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    item {
                        Text("这是独立候选草稿，不会自动覆盖原章节。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Text(draft, lineHeight = 26.sp)
                    }
                }
            }
        },
        confirmButton = { if (!busy) TextButton(onDismiss) { Text("完成") } },
    )
}
