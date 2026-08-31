package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.PersistentStoryRepository
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
data class StoryPlayTurn(
    val id: String = UUID.randomUUID().toString(),
    val player: String = "",
    val narration: String = "",
    val choices: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StoryPlayVariable(
    val subject: String,
    val field: String,
    val value: String,
    val evidence: String = "",
)

@Serializable
data class StoryPlaySession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "故事分支",
    val anchorChapter: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val turns: List<StoryPlayTurn> = emptyList(),
    val variables: List<StoryPlayVariable> = emptyList(),
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
)

class StoryPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryPlayUiState())
    val state: StateFlow<StoryPlayUiState> = _state.asStateFlow()

    fun open(novelId: String, anchorChapter: Int) {
        if (_state.value.novelId == novelId && _state.value.active != null) return
        viewModelScope.launch {
            val archive = loadArchive(novelId)
            val active = archive.sessions.firstOrNull { it.id == archive.activeSessionId }
                ?: archive.sessions.maxByOrNull { it.updatedAt }
                ?: StoryPlaySession(anchorChapter = anchorChapter, title = "从第 ${anchorChapter} 章开始")
            val fixed = if (archive.sessions.any { it.id == active.id }) archive else archive.copy(
                activeSessionId = active.id,
                sessions = archive.sessions + active,
            ).also(::saveArchive)
            _state.value = StoryPlayUiState(novelId, active, fixed.sessions)
        }
    }

    fun selectSession(id: String) {
        val current = _state.value
        val selected = current.sessions.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(active = selected) }
        saveCurrentArchive(current.novelId, selected, current.sessions)
    }

    fun newBranch(anchorChapter: Int) {
        val novelId = _state.value.novelId
        if (novelId.isBlank() || _state.value.busy) return
        val session = StoryPlaySession(
            anchorChapter = anchorChapter,
            title = "分支 ${_state.value.sessions.size + 1} · 第 ${anchorChapter} 章",
        )
        val sessions = _state.value.sessions + session
        _state.update { it.copy(active = session, sessions = sessions, error = null) }
        saveCurrentArchive(novelId, session, sessions)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun act(
        book: ReaderBookUi,
        chapterText: String,
        action: String,
    ) {
        val cleanAction = action.trim()
        val session = _state.value.active ?: return
        if (_state.value.busy || cleanAction.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                    ?: error("还没有可用 AI，请先配置模型")
                val config = repository.providerConfig(provider.id) ?: error("AI 配置不可用")
                val gateway = UniversalAiGateway(config)
                val recent = session.turns.takeLast(8).joinToString("\n\n") { turn ->
                    "玩家：${turn.player}\n叙事：${turn.narration}"
                }
                val variables = session.variables.joinToString("\n") { "${it.subject}.${it.field}=${it.value}" }
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是琅嬛“进入故事”模式的互动小说导演（DM）。
                            用户进入的是原小说的一个独立分支，绝对不能改写或覆盖原著正文。
                            你必须尊重原书世界观、人物知识边界、时代与技术条件，角色只能根据当前已知信息行动。
                            每轮只推进一个清晰场景，不替玩家做重大决定，不突然跳时间，不擅自结束故事。
                            必须返回 GeneratedChapter JSON：
                            title="story-turn"；
                            content=本轮 180-520 字沉浸式叙事，直接承接玩家动作；
                            summary=给玩家的 3-4 个可执行选择，每行一个，不要解释，也不要写“选项”；
                            stateChanges=仅记录本轮实际发生的状态变化，subject=人物或世界对象，field=变量名，before/after=变化前后，evidence=正文依据；
                            touchedForeshadowingIds=[]。
                            不要把未发生的可能性写进状态变化。不要输出 JSON 以外文字。
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》
                            类型：${book.genre}
                            作品简介：${book.premise}
                            分支锚点：第 ${session.anchorChapter} 章

                            原章正文（仅作为世界与场景依据，禁止修改）：
                            ${chapterText.takeLast(5_000)}

                            当前分支变量：
                            ${variables.ifBlank { "暂无" }}

                            最近互动：
                            ${recent.ifBlank { "这是分支第一轮" }}

                            玩家本轮动作：
                            $cleanAction
                        """.trimIndent(),
                    )
                )
                val choices = output.summary.lines()
                    .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }
                    .take(4)
                val newVariables = mergeVariables(session.variables, output.stateChanges.mapNotNull { change ->
                    val subject = change.subject.trim()
                    val field = change.field.trim()
                    val value = change.after.trim()
                    if (subject.isBlank() || field.isBlank() || value.isBlank()) null
                    else StoryPlayVariable(subject, field, value, change.evidence.trim())
                })
                val turn = StoryPlayTurn(
                    player = cleanAction,
                    narration = output.content.trim().ifBlank { error("AI 没有返回故事正文") },
                    choices = choices,
                )
                session.copy(
                    updatedAt = System.currentTimeMillis(),
                    turns = session.turns + turn,
                    variables = newVariables,
                )
            }.onSuccess { updated ->
                val sessions = _state.value.sessions.map { if (it.id == updated.id) updated else it }
                _state.update { it.copy(active = updated, sessions = sessions, busy = false) }
                saveCurrentArchive(book.id, updated, sessions)
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "故事生成失败") }
            }
        }
    }

    private fun mergeVariables(old: List<StoryPlayVariable>, changes: List<StoryPlayVariable>): List<StoryPlayVariable> {
        val map = old.associateBy { "${it.subject}\u0000${it.field}" }.toMutableMap()
        changes.forEach { map["${it.subject}\u0000${it.field}"] = it }
        return map.values.sortedWith(compareBy({ it.subject }, { it.field }))
    }

    private fun archiveFile(novelId: String): File {
        val dir = File(getApplication<Application>().filesDir, "story_play").apply { mkdirs() }
        return File(dir, "$novelId.json")
    }

    private fun loadArchive(novelId: String): StoryPlayArchive {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryPlayArchive(novelId)
        return runCatching { json.decodeFromString(StoryPlayArchive.serializer(), file.readText()) }
            .getOrElse { StoryPlayArchive(novelId) }
    }

    private fun saveCurrentArchive(novelId: String, active: StoryPlaySession, sessions: List<StoryPlaySession>) {
        if (novelId.isBlank()) return
        saveArchive(StoryPlayArchive(novelId, active.id, sessions))
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

    LaunchedEffect(book.id, anchor?.chapterNumber) {
        vm.open(book.id, anchor?.chapterNumber ?: 1)
    }

    state.error?.let { error ->
        LaunchedEffect(error) { }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("我的故事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    state.active?.title ?: "准备进入故事",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                FilledTonalButton({ branchMenu = true }, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.ForkRight, null)
                    Spacer(Modifier.width(5.dp))
                    Text("分支 ${state.sessions.size.coerceAtLeast(1)}")
                }
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
                        onClick = { branchMenu = false; vm.newBranch(anchor?.chapterNumber ?: 1) },
                    )
                }
            }
        }

        if (!aiReady) {
            Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (session == null || session.turns.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(anchor?.let { "从第 ${it.chapterNumber} 章进入" } ?: "从作品开头进入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("原正文不会被修改。你可以扮演自己、主角或直接描述行动。", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            session?.turns?.let { turns ->
                items(turns, key = { it.id }) { turn ->
                    if (turn.player.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("你：${turn.player}", Modifier.fillMaxWidth().padding(14.dp))
                        }
                    }
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(turn.narration, lineHeight = 28.sp)
                            if (turn.choices.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                turn.choices.forEach { choice ->
                                    OutlinedButton(
                                        onClick = { input = choice },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(16.dp),
                                    ) { Text(choice, Modifier.fillMaxWidth()) }
                                }
                            }
                        }
                    }
                }
            }
            if (session != null && session.variables.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                                Text("故事变量", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold)
                            }
                            session.variables.take(8).forEach { variable ->
                                Text("${variable.subject} · ${variable.field}：${variable.value}", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    IconButton(vm::clearError) { Icon(Icons.Rounded.Close, "关闭") }
                }
            }
        }

        Surface(tonalElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("你要做什么、说什么……") },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    enabled = !state.busy,
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val action = input.trim()
                        if (action.isNotBlank() && anchor != null) {
                            input = ""
                            vm.act(book, anchor.content, action)
                        }
                    },
                    enabled = aiReady && !state.busy && input.isNotBlank() && anchor != null,
                    modifier = Modifier.size(54.dp),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Send, "发送")
                }
            }
        }
    }
}
