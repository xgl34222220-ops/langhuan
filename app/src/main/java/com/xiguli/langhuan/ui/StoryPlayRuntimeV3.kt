package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
enum class StoryKnowledgeKindV3 {
    KNOWN,
    FORBIDDEN;

    val label: String get() = when (this) {
        KNOWN -> "角色已知"
        FORBIDDEN -> "禁止提前知道"
    }
}

@Serializable
data class StoryKnowledgeEntryV3(
    val id: String = UUID.randomUUID().toString(),
    val character: String,
    val fact: String,
    val source: String = "",
    val kind: StoryKnowledgeKindV3 = StoryKnowledgeKindV3.KNOWN,
    val learnedAtTurnId: String = "",
)

@Serializable
data class StoryRelationshipV3(
    val id: String = UUID.randomUUID().toString(),
    val from: String,
    val to: String,
    val label: String,
    val value: String = "",
    val evidence: String = "",
)

@Serializable
data class StoryWorldStateV3(
    val location: String = "",
    val time: String = "",
    val atmosphere: String = "",
    val situation: String = "",
    val notes: String = "",
)

@Serializable
data class StoryRuntimeTurnSnapshotV3(
    val turnId: String,
    val knowledge: List<StoryKnowledgeEntryV3> = emptyList(),
    val relationships: List<StoryRelationshipV3> = emptyList(),
    val world: StoryWorldStateV3 = StoryWorldStateV3(),
)

@Serializable
data class StoryRuntimeSessionV3(
    val sessionId: String,
    val knowledge: List<StoryKnowledgeEntryV3> = emptyList(),
    val relationships: List<StoryRelationshipV3> = emptyList(),
    val world: StoryWorldStateV3 = StoryWorldStateV3(),
    val turnSnapshots: List<StoryRuntimeTurnSnapshotV3> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StoryRuntimeArchiveV3(
    val novelId: String,
    val sessions: List<StoryRuntimeSessionV3> = emptyList(),
)

data class StoryPlayV3UiState(
    val novelId: String = "",
    val active: StoryPlaySession? = null,
    val sessions: List<StoryPlaySession> = emptyList(),
    val runtime: StoryRuntimeSessionV3? = null,
    val runtimes: List<StoryRuntimeSessionV3> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class StoryPlayV3ViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryPlayV3UiState())
    val state: StateFlow<StoryPlayV3UiState> = _state.asStateFlow()

    fun open(novelId: String, anchorChapter: Int, anchorTitle: String, anchorText: String) {
        if (_state.value.novelId == novelId && _state.value.active != null) return
        viewModelScope.launch {
            val playArchive = loadPlayArchive(novelId)
            val seed = anchorText.takeLast(2_800)
            var active = playArchive.sessions.firstOrNull { it.id == playArchive.activeSessionId }
                ?: playArchive.sessions.maxByOrNull { it.updatedAt }
                ?: StoryPlaySession(
                    anchorChapter = anchorChapter,
                    anchorTitle = anchorTitle,
                    worldSnapshot = seed,
                    title = "从第 $anchorChapter 章开始",
                )
            if (active.worldSnapshot.isBlank()) {
                active = active.copy(
                    worldSnapshot = seed,
                    anchorTitle = active.anchorTitle.ifBlank { anchorTitle },
                )
            }
            val sessions = if (playArchive.sessions.any { it.id == active.id }) {
                playArchive.sessions.map { if (it.id == active.id) active else it }
            } else {
                playArchive.sessions + active
            }

            val runtimeArchive = loadRuntimeArchive(novelId)
            val activeRuntime = runtimeArchive.sessions.firstOrNull { it.sessionId == active.id }
                ?: StoryRuntimeSessionV3(
                    sessionId = active.id,
                    world = StoryWorldStateV3(
                        time = "第 ${active.anchorChapter} 章",
                        situation = active.anchorTitle,
                    ),
                )
            val runtimes = if (runtimeArchive.sessions.any { it.sessionId == active.id }) {
                runtimeArchive.sessions.map { if (it.sessionId == active.id) activeRuntime else it }
            } else {
                runtimeArchive.sessions + activeRuntime
            }

            savePlayArchive(StoryPlayArchive(novelId, active.id, sessions))
            saveRuntimeArchive(StoryRuntimeArchiveV3(novelId, runtimes))
            _state.value = StoryPlayV3UiState(
                novelId = novelId,
                active = active,
                sessions = sessions,
                runtime = activeRuntime,
                runtimes = runtimes,
            )
        }
    }

    fun selectSession(id: String) {
        val current = _state.value
        if (current.busy) return
        val selected = current.sessions.firstOrNull { it.id == id } ?: return
        val runtime = current.runtimes.firstOrNull { it.sessionId == id }
            ?: StoryRuntimeSessionV3(
                sessionId = id,
                world = StoryWorldStateV3(
                    time = "第 ${selected.anchorChapter} 章",
                    situation = selected.anchorTitle,
                ),
            )
        val runtimes = if (current.runtimes.any { it.sessionId == id }) current.runtimes else current.runtimes + runtime
        _state.update { it.copy(active = selected, runtime = runtime, runtimes = runtimes, error = null, notice = null) }
        persist(current.novelId, selected, current.sessions, runtime, runtimes)
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
        val runtime = StoryRuntimeSessionV3(
            sessionId = session.id,
            world = StoryWorldStateV3(time = "第 $anchorChapter 章", situation = anchorTitle),
        )
        val sessions = current.sessions + session
        val runtimes = current.runtimes + runtime
        _state.update {
            it.copy(
                active = session,
                sessions = sessions,
                runtime = runtime,
                runtimes = runtimes,
                notice = "已从当前章节创建全新独立分支",
                error = null,
            )
        }
        persist(current.novelId, session, sessions, runtime, runtimes)
    }

    fun renameBranch(id: String, title: String) {
        val clean = title.trim()
        val current = _state.value
        if (clean.isBlank() || current.busy) return
        val sessions = current.sessions.map { if (it.id == id) it.copy(title = clean, updatedAt = System.currentTimeMillis()) else it }
        val active = sessions.firstOrNull { it.id == current.active?.id } ?: return
        val runtime = current.runtime ?: return
        _state.update { it.copy(active = active, sessions = sessions, notice = "分支已重命名", error = null) }
        persist(current.novelId, active, sessions, runtime, current.runtimes)
    }

    fun duplicateBranch(id: String) {
        val current = _state.value
        if (current.busy) return
        val source = current.sessions.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            title = source.title + " · 副本",
            createdAt = now,
            updatedAt = now,
        )
        val sourceRuntime = current.runtimes.firstOrNull { it.sessionId == id }
            ?: StoryRuntimeSessionV3(sessionId = id)
        val runtimeCopy = sourceRuntime.copy(sessionId = copy.id, updatedAt = now)
        val sessions = current.sessions + copy
        val runtimes = current.runtimes + runtimeCopy
        _state.update {
            it.copy(
                active = copy,
                sessions = sessions,
                runtime = runtimeCopy,
                runtimes = runtimes,
                notice = "已复制为独立分支，之后的状态互不影响",
                error = null,
            )
        }
        persist(current.novelId, copy, sessions, runtimeCopy, runtimes)
    }

    fun deleteBranch(id: String) {
        val current = _state.value
        if (current.busy) return
        if (current.sessions.size <= 1) {
            _state.update { it.copy(error = "至少保留一个故事分支") }
            return
        }
        val sessions = current.sessions.filterNot { it.id == id }
        val runtimes = current.runtimes.filterNot { it.sessionId == id }
        val active = if (current.active?.id == id) sessions.maxByOrNull { it.updatedAt } else current.active
        val safeActive = active ?: sessions.first()
        val runtime = runtimes.firstOrNull { it.sessionId == safeActive.id }
            ?: StoryRuntimeSessionV3(sessionId = safeActive.id)
        val safeRuntimes = if (runtimes.any { it.sessionId == runtime.sessionId }) runtimes else runtimes + runtime
        _state.update {
            it.copy(
                active = safeActive,
                sessions = sessions,
                runtime = runtime,
                runtimes = safeRuntimes,
                notice = "分支已删除；其它分支和原著不受影响",
                error = null,
            )
        }
        persist(current.novelId, safeActive, sessions, runtime, safeRuntimes)
    }

    fun updateProfile(profile: StoryPlayerProfile) = mutateSession("角色卡已保存") { it.copy(playerProfile = profile) }

    fun setVariableAccess(subject: String, field: String, access: StoryVariableAccess) = mutateSession("变量权限已更新") { session ->
        session.copy(variables = session.variables.map { variable ->
            if (variable.subject == subject && variable.field == field) variable.copy(access = access) else variable
        })
    }

    fun updateWorld(world: StoryWorldStateV3) = mutateRuntime("世界状态已保存") { it.copy(world = world) }

    fun addKnowledge(character: String, fact: String, source: String, kind: StoryKnowledgeKindV3) {
        val c = character.trim()
        val f = fact.trim()
        if (c.isBlank() || f.isBlank()) return
        mutateRuntime("人物知识账本已更新") { runtime ->
            val entry = StoryKnowledgeEntryV3(character = c, fact = f, source = source.trim(), kind = kind)
            val filtered = runtime.knowledge.filterNot {
                it.character.equals(c, true) && it.fact.equals(f, true) && it.kind == kind
            }
            runtime.copy(knowledge = filtered + entry)
        }
    }

    fun deleteKnowledge(id: String) = mutateRuntime("知识条目已删除") { runtime ->
        runtime.copy(knowledge = runtime.knowledge.filterNot { it.id == id })
    }

    fun addRelationship(from: String, to: String, label: String, value: String, evidence: String) {
        val a = from.trim()
        val b = to.trim()
        val l = label.trim()
        if (a.isBlank() || b.isBlank() || l.isBlank()) return
        mutateRuntime("角色关系已更新") { runtime ->
            val relation = StoryRelationshipV3(a, b, l, value.trim(), evidence.trim())
            val filtered = runtime.relationships.filterNot {
                it.from.equals(a, true) && it.to.equals(b, true) && it.label.equals(l, true)
            }
            runtime.copy(relationships = filtered + relation)
        }
    }

    fun deleteRelationship(id: String) = mutateRuntime("关系条目已删除") { runtime ->
        runtime.copy(relationships = runtime.relationships.filterNot { it.id == id })
    }

    fun rewindTo(turnId: String) {
        val current = _state.value
        val session = current.active ?: return
        val runtime = current.runtime ?: return
        val index = session.turns.indexOfFirst { it.id == turnId }
        if (index < 0 || current.busy) return
        val keptTurns = session.turns.take(index + 1)
        val restoredVariables = keptTurns.lastOrNull()?.variablesAfter?.takeIf { it.isNotEmpty() } ?: session.variables
        val snapshotIndex = runtime.turnSnapshots.indexOfFirst { it.turnId == turnId }
        val keptSnapshots = if (snapshotIndex >= 0) runtime.turnSnapshots.take(snapshotIndex + 1) else runtime.turnSnapshots
        val snapshot = keptSnapshots.lastOrNull()
        val updatedSession = session.copy(
            turns = keptTurns,
            variables = restoredVariables,
            chapterDraftCandidate = "",
            updatedAt = System.currentTimeMillis(),
        )
        val updatedRuntime = runtime.copy(
            knowledge = snapshot?.knowledge ?: runtime.knowledge,
            relationships = snapshot?.relationships ?: runtime.relationships,
            world = snapshot?.world ?: runtime.world,
            turnSnapshots = keptSnapshots,
            updatedAt = System.currentTimeMillis(),
        )
        persistSuccess(updatedSession, updatedRuntime, "已回溯到该节点；人物知识、关系、世界状态与变量已一起恢复")
    }

    fun act(book: ReaderBookUi, chapterText: String, action: String) {
        val clean = action.trim()
        val session = _state.value.active ?: return
        val runtime = _state.value.runtime ?: return
        if (clean.isBlank() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val gateway = activeGateway()
                val visibleVars = session.variables.filter { it.access != StoryVariableAccess.AUTHOR_LOCKED }
                val vars = visibleVars.joinToString("\n") { "${it.subject}.${it.field}=${it.value} [${it.access.label}]" }
                val recent = session.turns.takeLast(10).joinToString("\n\n") { "玩家：${it.player}\n叙事：${it.narration}" }
                val knowledge = renderKnowledge(runtime.knowledge)
                val relations = renderRelationships(runtime.relationships)
                val world = renderWorld(runtime.world)
                val p = session.playerProfile
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是琅嬛互动小说导演 DM。当前是原小说的独立分支，绝不能改写原著正文。
                            严格尊重世界观、时代技术、人物性格、人物知识账本和关系网。角色只能根据“自己已知”的事实、当前场景亲眼可见信息和本轮明确得到的新证据行动。
                            标记为“禁止提前知道”的事实，角色不得提及、暗示、据此行动，也不能借旁白偷偷剧透；只有剧情中出现明确证据后，才能通过知识变更把它转成角色已知。
                            玩家“禁止提前知道”的内容同样不得泄露。不同分支互不共享动态状态。
                            每轮只推进一个连续场景，不替玩家做重大决定，不突然跳时间，不擅自结局。
                            变量标注为 AI 只读时只能参考，不能在 stateChanges 修改；作者锁定变量不会提供给你。

                            必须返回 GeneratedChapter JSON：title=story-turn；content=180-520字沉浸叙事；summary=3-4个可执行选择，每行一个；touchedForeshadowingIds=[]。
                            stateChanges 只记录本轮真正发生的变化，并按以下约定编码：
                            1. 普通数值/状态：subject=对象名，field=字段名，before/after/evidence 完整。
                            2. 某角色新获得一个事实：subject="知识:角色名"，field="新知"，after=该角色现在确实知道的事实，evidence=获得依据。
                            3. 角色关系变化：subject="关系:角色A→角色B"，field=关系维度，如“信任/敌意/合作”，after=当前值或简短状态，evidence=变化原因。
                            4. 世界动态变化：subject="世界"，field 只能是“地点/时间/环境/局势”，after=新状态，evidence=依据。
                            没变化就不要伪造 stateChanges。不要输出 JSON 外文字。
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》
                            类型：${book.genre}
                            简介：${book.premise}
                            分支锚点：第 ${session.anchorChapter} 章 ${session.anchorTitle}
                            原始世界快照：
                            ${session.worldSnapshot.ifBlank { chapterText.takeLast(2_800) }}

                            【当前动态世界状态】
                            $world

                            【玩家角色】
                            姓名=${p.name.ifBlank { "未命名" }}；身份=${p.identity.ifBlank { "未指定" }}；特征=${p.traits.ifBlank { "未指定" }}
                            玩家当前已知：${p.knownFacts.ifBlank { "仅知道剧情中已展示的信息" }}
                            玩家禁止提前知道：${p.forbiddenKnowledge.ifBlank { "无额外声明" }}

                            【人物知识账本｜必须严格隔离到各自角色】
                            $knowledge

                            【角色关系网】
                            $relations

                            【当前可见变量】
                            ${vars.ifBlank { "暂无" }}

                            【最近互动】
                            ${recent.ifBlank { "这是第一轮" }}

                            玩家本轮动作：$clean
                        """.trimIndent(),
                    )
                )

                val turnId = UUID.randomUUID().toString()
                var nextRuntime = runtime
                val ordinaryChanges = mutableListOf<StoryPlayVariable>()
                output.stateChanges.forEach { change ->
                    val subject = change.subject.trim()
                    val field = change.field.trim()
                    val after = change.after.trim()
                    val evidence = change.evidence.trim()
                    when {
                        subject.startsWith("知识:") && after.isNotBlank() -> {
                            val character = subject.substringAfter("知识:").trim()
                            if (character.isNotBlank()) {
                                val entry = StoryKnowledgeEntryV3(
                                    character = character,
                                    fact = after,
                                    source = evidence,
                                    kind = StoryKnowledgeKindV3.KNOWN,
                                    learnedAtTurnId = turnId,
                                )
                                val list = nextRuntime.knowledge.filterNot {
                                    it.character.equals(character, true) && it.fact.equals(after, true) && it.kind == StoryKnowledgeKindV3.KNOWN
                                } + entry
                                nextRuntime = nextRuntime.copy(knowledge = list)
                            }
                        }
                        subject.startsWith("关系:") && after.isNotBlank() -> {
                            val pair = parseRelationPair(subject.substringAfter("关系:"))
                            if (pair != null) {
                                val label = field.ifBlank { "关系" }
                                val relation = StoryRelationshipV3(
                                    from = pair.first,
                                    to = pair.second,
                                    label = label,
                                    value = after,
                                    evidence = evidence,
                                )
                                val list = nextRuntime.relationships.filterNot {
                                    it.from.equals(pair.first, true) && it.to.equals(pair.second, true) && it.label.equals(label, true)
                                } + relation
                                nextRuntime = nextRuntime.copy(relationships = list)
                            }
                        }
                        subject == "世界" && after.isNotBlank() -> {
                            val nextWorld = when (field) {
                                "地点" -> nextRuntime.world.copy(location = after)
                                "时间" -> nextRuntime.world.copy(time = after)
                                "环境" -> nextRuntime.world.copy(atmosphere = after)
                                "局势" -> nextRuntime.world.copy(situation = after)
                                else -> nextRuntime.world
                            }
                            nextRuntime = nextRuntime.copy(world = nextWorld)
                        }
                        subject.isNotBlank() && field.isNotBlank() && after.isNotBlank() -> {
                            ordinaryChanges += StoryPlayVariable(subject, field, after, evidence)
                        }
                    }
                }

                val updatedVars = mergeVariables(session.variables, ordinaryChanges)
                val choices = output.summary.lines()
                    .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }
                    .take(4)
                val turn = StoryPlayTurn(
                    id = turnId,
                    player = clean,
                    narration = output.content.trim().ifBlank { error("AI 没有返回故事正文") },
                    choices = choices,
                    variablesAfter = updatedVars,
                )
                val updatedSession = session.copy(
                    turns = session.turns + turn,
                    variables = updatedVars,
                    chapterDraftCandidate = "",
                    updatedAt = System.currentTimeMillis(),
                )
                val updatedRuntime = nextRuntime.copy(
                    turnSnapshots = nextRuntime.turnSnapshots + StoryRuntimeTurnSnapshotV3(
                        turnId = turnId,
                        knowledge = nextRuntime.knowledge,
                        relationships = nextRuntime.relationships,
                        world = nextRuntime.world,
                    ),
                    updatedAt = System.currentTimeMillis(),
                )
                updatedSession to updatedRuntime
            }.onSuccess { (updatedSession, updatedRuntime) ->
                persistSuccess(updatedSession, updatedRuntime)
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "故事生成失败") }
            }
        }
    }

    fun generateChapterDraft(book: ReaderBookUi) {
        val session = _state.value.active ?: return
        val runtime = _state.value.runtime ?: return
        if (session.turns.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val transcript = session.turns.joinToString("\n\n") { "玩家动作：${it.player}\n剧情结果：${it.narration}" }
                val context = buildString {
                    appendLine("世界状态：${renderWorld(runtime.world)}")
                    appendLine("人物知识：${renderKnowledge(runtime.knowledge)}")
                    appendLine("角色关系：${renderRelationships(runtime.relationships)}")
                }
                val output = activeGateway().generate(
                    PromptBundle(
                        system = """
                            你是中文网络小说编辑。把互动演绎记录改写为自然连贯的小说章节草稿。
                            不得出现“玩家、DM、选项、游戏”等界面痕迹，不新增演绎中没有依据的关键事实，不改变人物知识边界。
                            尤其不能让角色在正文里说出知识账本标记为“禁止提前知道”的事实。
                            输出 GeneratedChapter JSON：title=合适章节名；content=1200-2600字小说正文；summary=一段章节摘要；stateChanges=[]；touchedForeshadowingIds=[]。
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》
                            锚点：第${session.anchorChapter}章 ${session.anchorTitle}
                            $context
                            演绎记录：
                            $transcript
                        """.trimIndent(),
                    )
                )
                val body = output.content.trim().ifBlank { error("AI 没有返回章节草稿") }
                session.copy(
                    chapterDraftCandidate = buildString {
                        append(output.title.trim().ifBlank { "演绎章节草稿" }).append("\n\n").append(body)
                    },
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updatedSession ->
                persistSuccess(updatedSession, runtime, "章节草稿已生成；原正文没有被覆盖")
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "章节草稿生成失败") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    private suspend fun activeGateway(): UniversalAiGateway {
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
            ?: error("还没有可用 AI，请先配置模型")
        val config = repository.providerConfig(provider.id) ?: error("AI 配置不可用")
        return UniversalAiGateway(config)
    }

    private fun mergeVariables(old: List<StoryPlayVariable>, incoming: List<StoryPlayVariable>): List<StoryPlayVariable> {
        val map = old.associateBy { "${it.subject}\u0000${it.field}" }.toMutableMap()
        incoming.forEach { variable ->
            val key = "${variable.subject}\u0000${variable.field}"
            val existing = map[key]
            when {
                existing == null -> map[key] = variable
                existing.access == StoryVariableAccess.AI_READ_WRITE -> map[key] = variable.copy(access = existing.access)
            }
        }
        return map.values.sortedWith(compareBy({ it.subject }, { it.field }))
    }

    private fun mutateSession(message: String, transform: (StoryPlaySession) -> StoryPlaySession) {
        val current = _state.value
        val active = current.active ?: return
        val runtime = current.runtime ?: return
        if (current.busy) return
        val updated = transform(active).copy(updatedAt = System.currentTimeMillis())
        val sessions = current.sessions.map { if (it.id == updated.id) updated else it }
        _state.update { it.copy(active = updated, sessions = sessions, notice = message, error = null) }
        persist(current.novelId, updated, sessions, runtime, current.runtimes)
    }

    private fun mutateRuntime(message: String, transform: (StoryRuntimeSessionV3) -> StoryRuntimeSessionV3) {
        val current = _state.value
        val active = current.active ?: return
        val runtime = current.runtime ?: return
        if (current.busy) return
        val updated = transform(runtime).copy(updatedAt = System.currentTimeMillis())
        val runtimes = current.runtimes.map { if (it.sessionId == updated.sessionId) updated else it }
        _state.update { it.copy(runtime = updated, runtimes = runtimes, notice = message, error = null) }
        persist(current.novelId, active, current.sessions, updated, runtimes)
    }

    private fun persistSuccess(updatedSession: StoryPlaySession, updatedRuntime: StoryRuntimeSessionV3, message: String? = null) {
        val current = _state.value
        val sessions = current.sessions.map { if (it.id == updatedSession.id) updatedSession else it }
        val runtimes = current.runtimes.map { if (it.sessionId == updatedRuntime.sessionId) updatedRuntime else it }
        _state.update {
            it.copy(
                active = updatedSession,
                sessions = sessions,
                runtime = updatedRuntime,
                runtimes = runtimes,
                busy = false,
                notice = message ?: it.notice,
            )
        }
        persist(current.novelId, updatedSession, sessions, updatedRuntime, runtimes)
    }

    private fun persist(
        novelId: String,
        active: StoryPlaySession,
        sessions: List<StoryPlaySession>,
        runtime: StoryRuntimeSessionV3,
        runtimes: List<StoryRuntimeSessionV3>,
    ) {
        if (novelId.isBlank()) return
        savePlayArchive(StoryPlayArchive(novelId, active.id, sessions))
        val normalizedRuntimes = if (runtimes.any { it.sessionId == runtime.sessionId }) runtimes else runtimes + runtime
        saveRuntimeArchive(StoryRuntimeArchiveV3(novelId, normalizedRuntimes))
    }

    private fun playFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_play")
        .apply { mkdirs() }
        .resolve("$novelId.json")

    private fun runtimeFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_runtime_v3")
        .apply { mkdirs() }
        .resolve("$novelId.json")

    private fun loadPlayArchive(novelId: String): StoryPlayArchive {
        val file = playFile(novelId)
        if (!file.isFile) return StoryPlayArchive(novelId)
        return runCatching { json.decodeFromString(StoryPlayArchive.serializer(), file.readText()) }
            .getOrElse { StoryPlayArchive(novelId) }
    }

    private fun savePlayArchive(archive: StoryPlayArchive) {
        runCatching { playFile(archive.novelId).writeText(json.encodeToString(StoryPlayArchive.serializer(), archive)) }
    }

    private fun loadRuntimeArchive(novelId: String): StoryRuntimeArchiveV3 {
        val file = runtimeFile(novelId)
        if (!file.isFile) return StoryRuntimeArchiveV3(novelId)
        return runCatching { json.decodeFromString(StoryRuntimeArchiveV3.serializer(), file.readText()) }
            .getOrElse { StoryRuntimeArchiveV3(novelId) }
    }

    private fun saveRuntimeArchive(archive: StoryRuntimeArchiveV3) {
        runCatching { runtimeFile(archive.novelId).writeText(json.encodeToString(StoryRuntimeArchiveV3.serializer(), archive)) }
    }

    private fun renderKnowledge(entries: List<StoryKnowledgeEntryV3>): String {
        if (entries.isEmpty()) return "暂无结构化知识条目；角色只能使用当前场景明确展示的信息。"
        return entries.groupBy { it.character }.entries.joinToString("\n") { (character, list) ->
            buildString {
                append(character).append("：")
                list.forEach { entry ->
                    append("\n- [").append(entry.kind.label).append("] ").append(entry.fact)
                    if (entry.source.isNotBlank()) append("｜依据=").append(entry.source)
                }
            }
        }
    }

    private fun renderRelationships(entries: List<StoryRelationshipV3>): String {
        if (entries.isEmpty()) return "暂无结构化关系条目。"
        return entries.joinToString("\n") { relation ->
            "${relation.from}→${relation.to}｜${relation.label}=${relation.value.ifBlank { "已建立" }}${if (relation.evidence.isBlank()) "" else "｜依据=${relation.evidence}"}"
        }
    }

    private fun renderWorld(world: StoryWorldStateV3): String = buildString {
        append("地点=").append(world.location.ifBlank { "未记录" })
        append("；时间=").append(world.time.ifBlank { "未记录" })
        append("；环境=").append(world.atmosphere.ifBlank { "未记录" })
        append("；局势=").append(world.situation.ifBlank { "未记录" })
        if (world.notes.isNotBlank()) append("；作者备注=").append(world.notes)
    }

    private fun parseRelationPair(raw: String): Pair<String, String>? {
        val normalized = raw.replace("->", "→")
        val parts = normalized.split("→", limit = 2).map { it.trim() }
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }
}

private enum class StoryStateTabV3(val label: String) {
    WORLD("世界"), KNOWLEDGE("知识"), RELATIONSHIP("关系"), VARIABLE("变量")
}

@Composable
fun StoryPlayPanelV3(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val vm: StoryPlayV3ViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val anchor = libraryState.readingChapter
        ?: libraryState.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
        ?: libraryState.chapters.lastOrNull()

    var input by remember(book.id, state.active?.id) { mutableStateOf("") }
    var branchMenu by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showState by remember { mutableStateOf(false) }
    var showBranchManager by remember { mutableStateOf(false) }
    var rewindTarget by remember { mutableStateOf<StoryPlayTurn?>(null) }
    var showDraft by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StoryPlaySession?>(null) }
    var deleteTarget by remember { mutableStateOf<StoryPlaySession?>(null) }

    LaunchedEffect(book.id, anchor?.chapterNumber) {
        vm.open(book.id, anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
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
            IconButton({ showProfile = true }) { Icon(Icons.Rounded.Person, "玩家身份") }
            IconButton({ showState = true }) { Icon(Icons.Rounded.Hub, "故事状态") }
            Box {
                IconButton({ branchMenu = true }) { Icon(Icons.Rounded.ForkRight, "故事分支") }
                DropdownMenu(branchMenu, { branchMenu = false }) {
                    state.sessions.sortedByDescending { it.updatedAt }.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.title) },
                            leadingIcon = { if (session.id == state.active?.id) Icon(Icons.Rounded.Check, null) },
                            onClick = {
                                branchMenu = false
                                vm.selectSession(session.id)
                            },
                            enabled = !state.busy,
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
                        enabled = !state.busy,
                    )
                    DropdownMenuItem(
                        text = { Text("管理分支") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = {
                            branchMenu = false
                            showBranchManager = true
                        },
                        enabled = !state.busy,
                    )
                }
            }
        }

        if (!aiReady) {
            Surface(
                Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null)
                    Text("故事模式需要先配置 AI", Modifier.padding(start = 8.dp).weight(1f))
                    TextButton(onAiSetup) { Text("去配置") }
                }
            }
        }

        val session = state.active
        val runtime = state.runtime
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                session?.playerProfile?.name?.ifBlank { "未设置玩家身份" } ?: "未设置玩家身份",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                session?.playerProfile?.identity?.ifBlank { "可扮演自己、原创角色或原著角色" }
                                    ?: "可扮演自己、原创角色或原著角色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton({ showProfile = true }) { Text("编辑") }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Hub, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 9.dp).weight(1f)) {
                            Text("故事状态引擎", fontWeight = FontWeight.Bold)
                            Text(
                                "知识 ${runtime?.knowledge?.size ?: 0} · 关系 ${runtime?.relationships?.size ?: 0} · 变量 ${session?.variables?.size ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton({ showState = true }) { Text("查看") }
                    }
                }
            }

            if (session == null || session.turns.isEmpty()) item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            anchor?.let { "从第 ${it.chapterNumber} 章进入" } ?: "从作品开头进入",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "进入后保存独立世界快照；人物知识、关系、变量和分支状态都单独记录。",
                            Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                                    ) {
                                        Text(choice, Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            TextButton({ rewindTarget = turn }, Modifier.align(Alignment.End), enabled = !state.busy) {
                                Icon(Icons.Rounded.History, null)
                                Spacer(Modifier.width(4.dp))
                                Text("从这里重来")
                            }
                        }
                    }
                }
            }

            if (session != null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showState = true }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.Hub, null)
                        Spacer(Modifier.width(5.dp))
                        Text("状态中心")
                    }
                    Button(
                        onClick = {
                            vm.generateChapterDraft(book)
                            showDraft = true
                        },
                        modifier = Modifier.weight(1f),
                        enabled = aiReady && !state.busy && session.turns.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.EditNote, null)
                        Spacer(Modifier.width(5.dp))
                        Text("转章节草稿")
                    }
                }
            }
        }

        state.error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, Modifier.weight(1f))
                    IconButton(vm::clearError) { Icon(Icons.Rounded.Close, "关闭") }
                }
            }
        }
        state.notice?.let { message ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(vm::clearNotice) { Icon(Icons.Rounded.Close, "关闭") }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                FilledIconButton(
                    onClick = {
                        val action = input
                        input = ""
                        vm.act(book, anchor?.content.orEmpty(), action)
                    },
                    enabled = aiReady && !state.busy && input.isNotBlank(),
                    modifier = Modifier.size(54.dp),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Send, "发送")
                }
            }
        }
    }

    if (showProfile) StoryPlayerProfileDialogV3(
        initial = state.active?.playerProfile ?: StoryPlayerProfile(),
        onDismiss = { showProfile = false },
        onSave = {
            vm.updateProfile(it)
            showProfile = false
        },
    )

    if (showState) StoryStateCenterDialogV3(
        session = state.active,
        runtime = state.runtime,
        onDismiss = { showState = false },
        onWorldSave = vm::updateWorld,
        onVariableAccess = vm::setVariableAccess,
        onKnowledgeAdd = vm::addKnowledge,
        onKnowledgeDelete = vm::deleteKnowledge,
        onRelationshipAdd = vm::addRelationship,
        onRelationshipDelete = vm::deleteRelationship,
    )

    if (showBranchManager) StoryBranchManagerDialogV3(
        sessions = state.sessions,
        activeId = state.active?.id,
        onDismiss = { showBranchManager = false },
        onSelect = vm::selectSession,
        onRename = { renameTarget = it },
        onDuplicate = vm::duplicateBranch,
        onDelete = { deleteTarget = it },
    )

    renameTarget?.let { target ->
        StoryRenameBranchDialogV3(
            session = target,
            onDismiss = { renameTarget = null },
            onSave = {
                vm.renameBranch(target.id, it)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分支？") },
            text = { Text("将删除「${target.title}」的互动、变量、人物知识和关系状态。原著正文和其它分支不会变化。") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteBranch(target.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("取消") } },
        )
    }

    rewindTarget?.let { turn ->
        AlertDialog(
            onDismissRequest = { rewindTarget = null },
            title = { Text("从这里重新演绎？") },
            text = { Text("保留这一轮，删除该分支之后的互动，并恢复这一节点的人物知识、关系、世界状态和变量。原著正文不会变化。") },
            confirmButton = {
                Button({
                    vm.rewindTo(turn.id)
                    rewindTarget = null
                }) { Text("确认回溯") }
            },
            dismissButton = { TextButton({ rewindTarget = null }) { Text("取消") } },
        )
    }

    if (showDraft) StoryChapterDraftDialogV3(
        busy = state.busy,
        draft = state.active?.chapterDraftCandidate.orEmpty(),
        onDismiss = { showDraft = false },
    )
}

@Composable
private fun StoryPlayerProfileDialogV3(
    initial: StoryPlayerProfile,
    onDismiss: () -> Unit,
    onSave: (StoryPlayerProfile) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var identity by remember(initial) { mutableStateOf(initial.identity) }
    var traits by remember(initial) { mutableStateOf(initial.traits) }
    var known by remember(initial) { mutableStateOf(initial.knownFacts) }
    var forbidden by remember(initial) { mutableStateOf(initial.forbiddenKnowledge) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("玩家身份 / 角色卡") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("姓名") }, singleLine = true)
                OutlinedTextField(identity, { identity = it }, Modifier.fillMaxWidth(), label = { Text("身份") })
                OutlinedTextField(traits, { traits = it }, Modifier.fillMaxWidth(), label = { Text("性格 / 特征") })
                OutlinedTextField(known, { known = it }, Modifier.fillMaxWidth(), label = { Text("当前已知信息") }, minLines = 2)
                OutlinedTextField(
                    forbidden,
                    { forbidden = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("禁止提前知道") },
                    minLines = 2,
                    supportingText = { Text("DM 会把这里和人物知识账本一起当作硬边界。") },
                )
            }
        },
        confirmButton = {
            Button({ onSave(StoryPlayerProfile(name.trim(), identity.trim(), traits.trim(), known.trim(), forbidden.trim())) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StoryStateCenterDialogV3(
    session: StoryPlaySession?,
    runtime: StoryRuntimeSessionV3?,
    onDismiss: () -> Unit,
    onWorldSave: (StoryWorldStateV3) -> Unit,
    onVariableAccess: (String, String, StoryVariableAccess) -> Unit,
    onKnowledgeAdd: (String, String, String, StoryKnowledgeKindV3) -> Unit,
    onKnowledgeDelete: (String) -> Unit,
    onRelationshipAdd: (String, String, String, String, String) -> Unit,
    onRelationshipDelete: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(StoryStateTabV3.WORLD) }
    var showKnowledgeAdd by remember { mutableStateOf(false) }
    var showRelationshipAdd by remember { mutableStateOf(false) }
    val currentWorld = runtime?.world ?: StoryWorldStateV3()
    var location by remember(currentWorld) { mutableStateOf(currentWorld.location) }
    var time by remember(currentWorld) { mutableStateOf(currentWorld.time) }
    var atmosphere by remember(currentWorld) { mutableStateOf(currentWorld.atmosphere) }
    var situation by remember(currentWorld) { mutableStateOf(currentWorld.situation) }
    var notes by remember(currentWorld) { mutableStateOf(currentWorld.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("故事状态中心") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoryStateTabV3.values().forEach { item ->
                        FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.label) })
                    }
                }
                HorizontalDivider()
                when (tab) {
                    StoryStateTabV3.WORLD -> {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("当前分支动态世界状态", fontWeight = FontWeight.Bold)
                            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点") })
                            OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth(), label = { Text("时间") })
                            OutlinedTextField(atmosphere, { atmosphere = it }, Modifier.fillMaxWidth(), label = { Text("环境") })
                            OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), label = { Text("局势") })
                            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("作者备注") }, minLines = 2)
                            Button(
                                onClick = { onWorldSave(StoryWorldStateV3(location.trim(), time.trim(), atmosphere.trim(), situation.trim(), notes.trim())) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("保存世界状态") }
                        }
                    }
                    StoryStateTabV3.KNOWLEDGE -> {
                        Column(Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("人物知识账本", fontWeight = FontWeight.Bold)
                                    Text("每个角色知道什么、绝对不能提前知道什么。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalButton({ showKnowledgeAdd = true }) {
                                    Icon(Icons.Rounded.Add, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("添加")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val knowledge = runtime?.knowledge.orEmpty()
                            if (knowledge.isEmpty()) {
                                Text("暂无条目。AI 在剧情中发现角色明确获得新事实时也会自动记账。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(knowledge, key = { it.id }) { entry ->
                                        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("${entry.character} · ${entry.kind.label}", fontWeight = FontWeight.SemiBold)
                                                    Text(entry.fact, style = MaterialTheme.typography.bodySmall)
                                                    if (entry.source.isNotBlank()) Text("依据：${entry.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton({ onKnowledgeDelete(entry.id) }) { Icon(Icons.Rounded.Delete, "删除") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    StoryStateTabV3.RELATIONSHIP -> {
                        Column(Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("角色关系网", fontWeight = FontWeight.Bold)
                                    Text("关系是有方向的，例如 A→B 的信任不等于 B→A。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalButton({ showRelationshipAdd = true }) {
                                    Icon(Icons.Rounded.Add, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("添加")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val relations = runtime?.relationships.orEmpty()
                            if (relations.isEmpty()) {
                                Text("暂无关系条目。AI 只在本轮真实发生关系变化时自动更新。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(relations, key = { it.id }) { relation ->
                                        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("${relation.from} → ${relation.to}", fontWeight = FontWeight.SemiBold)
                                                    Text("${relation.label}：${relation.value.ifBlank { "已建立" }}", style = MaterialTheme.typography.bodySmall)
                                                    if (relation.evidence.isNotBlank()) Text("依据：${relation.evidence}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton({ onRelationshipDelete(relation.id) }) { Icon(Icons.Rounded.Delete, "删除") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    StoryStateTabV3.VARIABLE -> {
                        val variables = session?.variables.orEmpty()
                        if (variables.isEmpty()) {
                            Text("还没有变量。AI 发现真实状态变化后会自动创建。")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(variables, key = { "${it.subject}:${it.field}" }) { variable ->
                                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                            Text("${variable.subject} · ${variable.field}", fontWeight = FontWeight.Bold)
                                            Text(variable.value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                StoryVariableAccess.values().forEach { access ->
                                                    FilterChip(
                                                        selected = variable.access == access,
                                                        onClick = { onVariableAccess(variable.subject, variable.field, access) },
                                                        label = { Text(access.label, style = MaterialTheme.typography.labelSmall) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("完成") } },
    )

    if (showKnowledgeAdd) StoryKnowledgeAddDialogV3(
        onDismiss = { showKnowledgeAdd = false },
        onAdd = { character, fact, source, kind ->
            onKnowledgeAdd(character, fact, source, kind)
            showKnowledgeAdd = false
        },
    )
    if (showRelationshipAdd) StoryRelationshipAddDialogV3(
        onDismiss = { showRelationshipAdd = false },
        onAdd = { from, to, label, value, evidence ->
            onRelationshipAdd(from, to, label, value, evidence)
            showRelationshipAdd = false
        },
    )
}

@Composable
private fun StoryKnowledgeAddDialogV3(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, StoryKnowledgeKindV3) -> Unit,
) {
    var character by remember { mutableStateOf("") }
    var fact by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(StoryKnowledgeKindV3.KNOWN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加知识条目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(character, { character = it }, Modifier.fillMaxWidth(), label = { Text("角色") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoryKnowledgeKindV3.values().forEach { item ->
                        FilterChip(selected = kind == item, onClick = { kind = item }, label = { Text(item.label) })
                    }
                }
                OutlinedTextField(fact, { fact = it }, Modifier.fillMaxWidth(), label = { Text("事实") }, minLines = 2)
                OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("依据 / 触发条件（可选）") })
            }
        },
        confirmButton = {
            Button({ onAdd(character, fact, source, kind) }, enabled = character.isNotBlank() && fact.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StoryRelationshipAddDialogV3(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加角色关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, Modifier.fillMaxWidth(), label = { Text("角色 A") })
                OutlinedTextField(to, { to = it }, Modifier.fillMaxWidth(), label = { Text("角色 B") })
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("关系维度，如信任 / 敌意") })
                OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("当前值 / 状态") })
                OutlinedTextField(evidence, { evidence = it }, Modifier.fillMaxWidth(), label = { Text("依据（可选）") })
            }
        },
        confirmButton = {
            Button(
                { onAdd(from, to, label, value, evidence) },
                enabled = from.isNotBlank() && to.isNotBlank() && label.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StoryBranchManagerDialogV3(
    sessions: List<StoryPlaySession>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (StoryPlaySession) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (StoryPlaySession) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理故事分支") },
        text = {
            if (sessions.isEmpty()) Text("暂无分支")
            else LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions.sortedByDescending { it.updatedAt }, key = { it.id }) { session ->
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(session.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "第 ${session.anchorChapter} 章 · ${session.turns.size} 轮${if (session.id == activeId) " · 当前" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (session.id != activeId) TextButton({ onSelect(session.id) }) { Text("切换") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ onRename(session) }) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(3.dp)); Text("重命名") }
                                TextButton({ onDuplicate(session.id) }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(3.dp)); Text("复制") }
                                TextButton({ onDelete(session) }, enabled = sessions.size > 1) { Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(3.dp)); Text("删除") }
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
private fun StoryRenameBranchDialogV3(
    session: StoryPlaySession,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by remember(session.id) { mutableStateOf(session.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名分支") },
        text = { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("分支名称") }) },
        confirmButton = { Button({ onSave(title) }, enabled = title.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StoryChapterDraftDialogV3(busy: Boolean, draft: String, onDismiss: () -> Unit) {
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
