package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class NpcPresenceV1 {
    PRESENT,
    NEARBY,
    AWAY;

    val label: String get() = when (this) {
        PRESENT -> "在场"
        NEARBY -> "附近"
        AWAY -> "离场"
    }
}

@Serializable
data class NpcLifeStateV1(
    val name: String,
    val presence: NpcPresenceV1 = NpcPresenceV1.AWAY,
    val currentGoal: String = "",
    val emotion: String = "平静",
    val hiddenIntent: String = "",
    val playerAttitude: String = "中性",
    val shortMemory: List<String> = emptyList(),
    val cooldownUntilBeat: Int = 0,
    val lastUpdatedTurnId: String = "",
    val sourceChapter: Int = 0,
)

@Serializable
data class NpcLifeSceneV1(
    val sessionId: String,
    val states: List<NpcLifeStateV1> = emptyList(),
    val autoUpdate: Boolean = true,
    val autoPresence: Boolean = true,
    val beatCounter: Int = 0,
    val lastInputKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class NpcLifeArchiveV1(
    val novelId: String,
    val scenes: List<NpcLifeSceneV1> = emptyList(),
)

data class NpcLifeUiStateV1(
    val novelId: String = "",
    val scene: NpcLifeSceneV1? = null,
    val scenes: List<NpcLifeSceneV1> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val syncToken: Long = 0L,
)

internal data class NpcLifeChangeV1(
    val name: String,
    val field: String,
    val value: String,
    val evidence: String = "",
)

class NpcLifeViewModelV1(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(NpcLifeUiStateV1())
    val state: StateFlow<NpcLifeUiStateV1> = _state.asStateFlow()

    fun open(
        novelId: String,
        session: StoryPlaySession,
        candidates: List<StoryCanonRoleCandidateV1>,
        initialCast: List<String>,
    ) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) return
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val scene = existing ?: NpcLifeSceneV1(
            sessionId = session.id,
            states = seedNpcLifeStatesV1(
                candidates = candidates,
                initialCast = initialCast,
                playerName = session.playerProfile.name,
                anchorChapter = session.anchorChapter,
            ),
        )
        val scenes = if (archive.scenes.any { it.sessionId == session.id }) archive.scenes else archive.scenes + scene
        _state.value = NpcLifeUiStateV1(novelId = novelId, scene = scene, scenes = scenes)
        saveArchive(NpcLifeArchiveV1(novelId, scenes))
    }

    fun setAutoUpdate(enabled: Boolean) = mutate("NPC 自动状态更新已修改") { it.copy(autoUpdate = enabled) }
    fun setAutoPresence(enabled: Boolean) = mutate("NPC 自动进出场已修改") { it.copy(autoPresence = enabled) }

    fun updateNpc(updated: NpcLifeStateV1) = mutate("NPC 状态已保存") { scene ->
        val list = scene.states.map { if (it.name == updated.name) updated else it }
            .let { current -> if (current.any { it.name == updated.name }) current else current + updated }
        scene.copy(states = list.sortedBy { it.name })
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun reconcile(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        candidates: List<StoryCanonRoleCandidateV1>,
        cast: List<String>,
        directorBeatId: String,
        directorBeat: String,
        force: Boolean = false,
    ) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy || scene.sessionId != session.id) return
        val latestTurn = session.turns.lastOrNull()
        val inputKey = buildString {
            append(session.id).append('|')
            append(latestTurn?.id.orEmpty()).append('|')
            append(directorBeatId).append('|')
            append(cast.sorted().joinToString(","))
        }
        if (!force && inputKey == scene.lastInputKey) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val gateway = activeGateway()
                val knownNames = (scene.states.map { it.name } + cast + candidates.map { it.name })
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != session.playerProfile.name }
                    .distinct()
                    .take(24)
                val roleText = candidates
                    .filter { it.name in knownNames }
                    .take(20)
                    .joinToString("\n") { role ->
                        buildString {
                            append("- ").append(role.name)
                            if (role.description.isNotBlank()) append("：").append(role.description.take(280))
                            if (role.knownFacts.isNotEmpty()) {
                                append("｜已知=").append(role.knownFacts.takeLast(4).joinToString("；") { it.fact.take(100) })
                            }
                        }
                    }
                val stateText = scene.states.joinToString("\n") { npc ->
                    val cooldown = (npc.cooldownUntilBeat - scene.beatCounter).coerceAtLeast(0)
                    "${npc.name}｜${npc.presence.label}｜目标=${npc.currentGoal.ifBlank { "未定" }}｜情绪=${npc.emotion}｜隐藏意图=${npc.hiddenIntent.ifBlank { "无明确记录" }}｜对玩家=${npc.playerAttitude}｜冷却=$cooldown｜短记忆=${npc.shortMemory.takeLast(4).joinToString("；").ifBlank { "无" }}"
                }
                val recentTurns = session.turns.takeLast(5).joinToString("\n\n") { turn ->
                    "玩家：${turn.player.ifBlank { "（无）" }}\n剧情：${turn.narration.takeLast(700)}"
                }
                val world = runtime?.world ?: StoryWorldStateV3()
                val nextBeat = scene.beatCounter + 1
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是琅嬛的 NPC 生命状态调度器，不负责直接写小说正文。
                            当前故事从原著第 ${session.anchorChapter} 章进入，因此只能使用截至该章可见的信息和当前分支已经真实发生的信息；绝对禁止利用后续原著剧情。
                            你要让 NPC 像持续生活的人：有自己的目标、情绪、隐藏意图、对玩家态度、短期记忆、主动行为冷却，并根据场景决定在场/附近/离场。
                            不要为了热闹让所有人都进场；没有合理到场理由的人必须保持离场或附近。不要让角色瞬移。
                            “隐藏意图”只供导演和 AI 决策，不得在剧情中无证据直接泄露。
                            冷却表示该 NPC 还需要多少个场景拍才适合再次主动抢话；普通回应不受冷却限制。

                            必须返回 GeneratedChapter JSON：title="npc-life"；content="ok"；summary=一句简短调度说明；touchedForeshadowingIds=[]。
                            stateChanges 只能按以下格式：subject="NPC:角色名"；field 只能是“在场/目标/情绪/隐藏意图/对玩家态度/短期记忆/冷却”。
                            - 在场 after 只能是“在场 / 附近 / 离场”。
                            - 冷却 after 填 0-3 的整数，表示从现在起还需等待多少个场景拍。
                            - 短期记忆只记录本分支刚发生且角色确实感知到的事实，不得记录未来秘密。
                            没有变化的字段不要输出。不要创建候选名单以外的具名原著角色。
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》｜分支：${session.title}
                            锚点：第${session.anchorChapter}章 ${session.anchorTitle}
                            当前场景：地点=${world.location.ifBlank { "未记录" }}；时间=${world.time.ifBlank { "未记录" }}；环境=${world.atmosphere.ifBlank { "未记录" }}；局势=${world.situation.ifBlank { "未记录" }}
                            玩家角色：${session.playerProfile.name.ifBlank { "未命名" }}｜${session.playerProfile.identity.ifBlank { "身份未指定" }}
                            当前在场演员：${cast.joinToString("、").ifBlank { "无" }}
                            本次状态拍编号：$nextBeat

                            【允许调度的角色】
                            ${knownNames.joinToString("、").ifBlank { "暂无" }}

                            【截至锚点角色资料】
                            ${roleText.ifBlank { "暂无额外角色资料" }}

                            【当前 NPC 生命状态】
                            ${stateText.ifBlank { "尚未建立" }}

                            【最近玩家互动】
                            ${recentTurns.ifBlank { "尚未发生玩家互动" }}

                            【最近 NPC 场景拍】
                            ${directorBeat.takeLast(1_600).ifBlank { "暂无" }}
                        """.trimIndent(),
                    )
                )

                val changes = output.stateChanges.mapNotNull { change ->
                    val subject = change.subject.trim()
                    if (!subject.startsWith("NPC:")) return@mapNotNull null
                    val name = subject.substringAfter("NPC:").trim()
                    if (name !in knownNames) return@mapNotNull null
                    val field = change.field.trim()
                    if (field !in NPC_LIFE_FIELDS_V1) return@mapNotNull null
                    val value = change.after.trim()
                    if (value.isBlank()) return@mapNotNull null
                    NpcLifeChangeV1(name, field, value, change.evidence.trim())
                }
                val applied = applyNpcLifeChangesV1(
                    states = ensureNpcStatesV1(scene.states, candidates, knownNames, session.anchorChapter),
                    changes = changes,
                    beatCounter = nextBeat,
                    turnId = latestTurn?.id.orEmpty(),
                )
                scene.copy(
                    states = applied,
                    beatCounter = nextBeat,
                    lastInputKey = inputKey,
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                persistScene(updated, "NPC 生命状态已更新")
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "NPC 状态更新失败") }
            }
        }
    }

    private suspend fun activeGateway(): UniversalAiGateway {
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
            ?: error("还没有可用 AI，请先配置模型")
        val config = repository.providerConfig(provider.id) ?: error("AI 配置不可用")
        return UniversalAiGateway(config)
    }

    private fun mutate(message: String, transform: (NpcLifeSceneV1) -> NpcLifeSceneV1) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy) return
        persistScene(transform(scene).copy(updatedAt = System.currentTimeMillis()), message)
    }

    private fun persistScene(updated: NpcLifeSceneV1, notice: String? = null) {
        val current = _state.value
        val scenes = current.scenes.map { if (it.sessionId == updated.sessionId) updated else it }
            .let { list -> if (list.any { it.sessionId == updated.sessionId }) list else list + updated }
        _state.update {
            it.copy(
                scene = updated,
                scenes = scenes,
                busy = false,
                notice = notice,
                syncToken = System.nanoTime(),
            )
        }
        if (current.novelId.isNotBlank()) saveArchive(NpcLifeArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_npc_life_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): NpcLifeArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return NpcLifeArchiveV1(novelId)
        return runCatching { json.decodeFromString(NpcLifeArchiveV1.serializer(), file.readText()) }
            .getOrElse { NpcLifeArchiveV1(novelId) }
    }

    private fun saveArchive(archive: NpcLifeArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(NpcLifeArchiveV1.serializer(), archive)) }
    }
}

internal fun seedNpcLifeStatesV1(
    candidates: List<StoryCanonRoleCandidateV1>,
    initialCast: List<String>,
    playerName: String,
    anchorChapter: Int,
): List<NpcLifeStateV1> {
    val cast = initialCast.filter { it.isNotBlank() && it != playerName }.toSet()
    return candidates
        .filter { it.name.isNotBlank() && it.name != playerName }
        .sortedWith(compareByDescending<StoryCanonRoleCandidateV1> { it.name in cast }.thenByDescending { it.lastChapter }.thenByDescending { it.mentions })
        .take(MAX_NPC_LIFE_V1)
        .map { role ->
            NpcLifeStateV1(
                name = role.name,
                presence = if (role.name in cast) NpcPresenceV1.PRESENT else NpcPresenceV1.AWAY,
                currentGoal = role.description.take(160),
                sourceChapter = minOf(anchorChapter, role.lastChapter),
            )
        }
}

private fun ensureNpcStatesV1(
    states: List<NpcLifeStateV1>,
    candidates: List<StoryCanonRoleCandidateV1>,
    names: List<String>,
    anchorChapter: Int,
): List<NpcLifeStateV1> {
    val byName = states.associateBy { it.name }.toMutableMap()
    names.forEach { name ->
        if (name !in byName) {
            val role = candidates.firstOrNull { it.name == name || name in it.aliases }
            byName[name] = NpcLifeStateV1(
                name = name,
                currentGoal = role?.description.orEmpty().take(160),
                sourceChapter = minOf(anchorChapter, role?.lastChapter ?: anchorChapter),
            )
        }
    }
    return byName.values.sortedBy { it.name }.take(MAX_NPC_LIFE_V1)
}

internal fun applyNpcLifeChangesV1(
    states: List<NpcLifeStateV1>,
    changes: List<NpcLifeChangeV1>,
    beatCounter: Int,
    turnId: String,
): List<NpcLifeStateV1> {
    val map = states.associateBy { it.name }.toMutableMap()
    changes.forEach { change ->
        val old = map[change.name] ?: NpcLifeStateV1(change.name)
        val next = when (change.field) {
            "在场" -> old.copy(presence = parsePresenceV1(change.value))
            "目标" -> old.copy(currentGoal = change.value.take(260))
            "情绪" -> old.copy(emotion = change.value.take(120))
            "隐藏意图" -> old.copy(hiddenIntent = change.value.take(320))
            "对玩家态度" -> old.copy(playerAttitude = change.value.take(180))
            "短期记忆" -> old.copy(shortMemory = (old.shortMemory + change.value.take(320)).distinct().takeLast(6))
            "冷却" -> {
                val wait = change.value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 3) ?: 0
                old.copy(cooldownUntilBeat = beatCounter + wait)
            }
            else -> old
        }
        map[change.name] = next.copy(lastUpdatedTurnId = turnId)
    }
    return map.values.sortedBy { it.name }.take(MAX_NPC_LIFE_V1)
}

private fun parsePresenceV1(value: String): NpcPresenceV1 = when {
    value.contains("附近") -> NpcPresenceV1.NEARBY
    value.contains("离") || value.contains("不在") -> NpcPresenceV1.AWAY
    else -> NpcPresenceV1.PRESENT
}

internal fun desiredNpcCastV1(
    states: List<NpcLifeStateV1>,
    playerName: String,
): List<String> = buildList {
    if (playerName.isNotBlank()) add(playerName)
    states.filter { it.presence == NpcPresenceV1.PRESENT }.forEach { add(it.name) }
}.distinct().take(8)

internal fun renderNpcLifeNoteV1(scene: NpcLifeSceneV1): String = buildString {
    append(NPC_LIFE_MARKER_V1).append('\n')
    scene.states.forEach { npc ->
        val cooldown = (npc.cooldownUntilBeat - scene.beatCounter).coerceAtLeast(0)
        append("- ").append(npc.name)
            .append("｜").append(npc.presence.label)
            .append("｜目标=").append(npc.currentGoal.ifBlank { "未定" })
            .append("｜情绪=").append(npc.emotion)
            .append("｜隐藏意图=").append(npc.hiddenIntent.ifBlank { "无明确记录" })
            .append("｜对玩家=").append(npc.playerAttitude)
            .append("｜主动冷却=").append(cooldown)
        if (npc.shortMemory.isNotEmpty()) append("｜短期记忆=").append(npc.shortMemory.takeLast(4).joinToString("；"))
        append('\n')
    }
    append(NPC_LIFE_END_V1)
}.trim()

internal fun mergeNpcLifeNoteV1(existing: String, lifeBlock: String): String {
    val withoutOld = removeMarkedBlockV1(existing, NPC_LIFE_MARKER_V1, NPC_LIFE_END_V1)
    val canonIndex = withoutOld.indexOf("【原著章节边界证据】")
    return if (canonIndex >= 0) {
        buildString {
            val before = withoutOld.substring(0, canonIndex).trim()
            val canon = withoutOld.substring(canonIndex).trim()
            if (before.isNotBlank()) append(before).append("\n\n")
            append(lifeBlock.trim()).append("\n\n").append(canon)
        }.trim()
    } else {
        listOf(withoutOld.trim(), lifeBlock.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
    }
}

private fun removeMarkedBlockV1(text: String, start: String, end: String): String {
    val startIndex = text.indexOf(start)
    if (startIndex < 0) return text
    val endIndex = text.indexOf(end, startIndex)
    if (endIndex < 0) return text.substring(0, startIndex).trim()
    return (text.substring(0, startIndex) + text.substring(endIndex + end.length)).trim()
}

internal fun mergeNpcLifeSituationV1(existing: String, scene: NpcLifeSceneV1): String {
    val base = existing.substringBefore(NPC_LIFE_SITUATION_V1).trim()
    val active = scene.states.filter { it.presence != NpcPresenceV1.AWAY }
    if (active.isEmpty()) return base
    val compact = active.take(6).joinToString("；") { npc ->
        val cooldown = (npc.cooldownUntilBeat - scene.beatCounter).coerceAtLeast(0)
        "${npc.name}:${npc.presence.label}/${npc.emotion}/目标=${npc.currentGoal.ifBlank { "未定" }}/意图=${npc.hiddenIntent.ifBlank { "未明" }}/对玩家=${npc.playerAttitude}/冷却=$cooldown"
    }
    return listOf(base, "$NPC_LIFE_SITUATION_V1 $compact").filter { it.isNotBlank() }.joinToString("｜")
}

@Composable
fun StoryPlayPanelV8(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val catalogVm: StoryRoleCatalogViewModelV1 = viewModel()
    val catalog by catalogVm.state.collectAsStateWithLifecycle()
    val directorVm: TavernDirectorViewModelV1 = viewModel()
    val director by directorVm.state.collectAsStateWithLifecycle()
    val lifeVm: NpcLifeViewModelV1 = viewModel()
    val life by lifeVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val runtime = storyState.runtime
    val directorScene = director.scene
    val lifeScene = life.scene
    val anchorChapter = session?.anchorChapter
        ?: libraryState.readingChapter?.chapterNumber
        ?: book.currentChapter.coerceAtLeast(1)
    var showLife by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, anchorChapter) { catalogVm.load(book.id, anchorChapter) }

    LaunchedEffect(book.id, session?.id, catalog.candidates, directorScene?.cast) {
        val active = session ?: return@LaunchedEffect
        val initialCast = directorScene?.cast
            ?: recommendTavernCastV1(catalog.candidates, active.worldSnapshot, active.playerProfile.name)
        lifeVm.open(book.id, active, catalog.candidates, initialCast)
    }

    val latestTurn = session?.turns?.lastOrNull()
    LaunchedEffect(
        session?.id,
        latestTurn?.id,
        directorScene?.latestBeatId,
        lifeScene?.autoUpdate,
        life.busy,
        storyState.busy,
        director.busy,
        aiReady,
    ) {
        val active = session ?: return@LaunchedEffect
        val state = lifeScene ?: return@LaunchedEffect
        if (state.autoUpdate && aiReady && !life.busy && !storyState.busy && !director.busy) {
            lifeVm.reconcile(
                book = book,
                session = active,
                runtime = runtime,
                candidates = catalog.candidates,
                cast = directorScene?.cast.orEmpty(),
                directorBeatId = directorScene?.latestBeatId.orEmpty(),
                directorBeat = directorScene?.latestBeat.orEmpty(),
            )
        }
    }

    LaunchedEffect(life.syncToken, lifeScene?.autoPresence, directorScene?.cast, session?.playerProfile?.name) {
        val active = session ?: return@LaunchedEffect
        val state = lifeScene ?: return@LaunchedEffect
        val tavern = directorScene ?: return@LaunchedEffect
        if (!state.autoPresence || director.busy) return@LaunchedEffect
        val desired = desiredNpcCastV1(state.states, active.playerProfile.name)
        val current = tavern.cast.distinct()
        current.filter { it !in desired }.forEach(directorVm::toggleCast)
        desired.filter { it !in current }.forEach(directorVm::toggleCast)
    }

    LaunchedEffect(life.syncToken, runtime?.world, lifeScene) {
        val state = lifeScene ?: return@LaunchedEffect
        val world = runtime?.world ?: return@LaunchedEffect
        val next = world.copy(
            situation = mergeNpcLifeSituationV1(world.situation, state),
            notes = mergeNpcLifeNoteV1(world.notes, renderNpcLifeNoteV1(state)),
        )
        if (next != world) storyVm.updateWorld(next)
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV7(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )

        SmallFloatingActionButton(
            onClick = { showLife = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 154.dp),
        ) {
            if (life.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Groups, "NPC 生命状态")
        }
    }

    if (showLife && session != null && lifeScene != null) {
        NpcLifeDialogV1(
            scene = lifeScene,
            aiReady = aiReady,
            busy = life.busy,
            error = life.error,
            notice = life.notice,
            onDismiss = { showLife = false },
            onAutoUpdate = lifeVm::setAutoUpdate,
            onAutoPresence = lifeVm::setAutoPresence,
            onRefresh = {
                lifeVm.reconcile(
                    book = book,
                    session = session,
                    runtime = runtime,
                    candidates = catalog.candidates,
                    cast = directorScene?.cast.orEmpty(),
                    directorBeatId = directorScene?.latestBeatId.orEmpty(),
                    directorBeat = directorScene?.latestBeat.orEmpty(),
                    force = true,
                )
            },
            onUpdateNpc = lifeVm::updateNpc,
            onClearError = lifeVm::clearError,
            onClearNotice = lifeVm::clearNotice,
        )
    }
}

@Composable
private fun NpcLifeDialogV1(
    scene: NpcLifeSceneV1,
    aiReady: Boolean,
    busy: Boolean,
    error: String?,
    notice: String?,
    onDismiss: () -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onAutoPresence: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUpdateNpc: (NpcLifeStateV1) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
) {
    var edit by remember { mutableStateOf<NpcLifeStateV1?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("NPC 生命状态") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动更新", fontWeight = FontWeight.SemiBold)
                        Text("每次玩家/NPC 场景变化后更新目标、情绪、记忆和意图。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = scene.autoUpdate, onCheckedChange = onAutoUpdate, enabled = !busy)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动进出场", fontWeight = FontWeight.SemiBold)
                        Text("按角色所在地和目标自动同步酒馆演员表。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = scene.autoPresence, onCheckedChange = onAutoPresence, enabled = !busy)
                }
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = aiReady && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("立即刷新所有 NPC 状态")
                }
                error?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = LanghuanShape.cover) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClearError) { Icon(Icons.Rounded.Close, "关闭") }
                        }
                    }
                }
                notice?.let {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = LanghuanShape.cover) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClearNotice) { Icon(Icons.Rounded.Close, "关闭") }
                        }
                    }
                }
                HorizontalDivider()
                if (scene.states.isEmpty()) {
                    Text("当前还没有可跟踪的 NPC。先进入故事或抽取原著角色。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(scene.states, key = { it.name }) { npc ->
                            NpcLifeCardV1(npc, scene.beatCounter, onEdit = { edit = npc })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss, enabled = !busy) { Text("完成") } },
    )

    edit?.let { npc ->
        NpcLifeEditDialogV1(
            initial = npc,
            onDismiss = { edit = null },
            onSave = {
                onUpdateNpc(it)
                edit = null
            },
        )
    }
}

@Composable
private fun NpcLifeCardV1(npc: NpcLifeStateV1, beatCounter: Int, onEdit: () -> Unit) {
    val cooldown = (npc.cooldownUntilBeat - beatCounter).coerceAtLeast(0)
    Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(npc.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                AssistChip(onClick = {}, label = { Text(npc.presence.label) })
                IconButton(onEdit) { Icon(Icons.Rounded.Edit, "编辑") }
            }
            Text("目标：${npc.currentGoal.ifBlank { "未定" }}", style = MaterialTheme.typography.bodySmall)
            Text("情绪：${npc.emotion} · 对玩家：${npc.playerAttitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("隐藏意图：${npc.hiddenIntent.ifBlank { "无明确记录" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            Text("主动冷却：${if (cooldown == 0) "可主动行动" else "还需 $cooldown 拍"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (npc.shortMemory.isNotEmpty()) {
                Text("短期记忆：${npc.shortMemory.takeLast(3).joinToString("；")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NpcLifeEditDialogV1(
    initial: NpcLifeStateV1,
    onDismiss: () -> Unit,
    onSave: (NpcLifeStateV1) -> Unit,
) {
    var presence by remember(initial.name) { mutableStateOf(initial.presence) }
    var goal by remember(initial.name) { mutableStateOf(initial.currentGoal) }
    var emotion by remember(initial.name) { mutableStateOf(initial.emotion) }
    var intent by remember(initial.name) { mutableStateOf(initial.hiddenIntent) }
    var attitude by remember(initial.name) { mutableStateOf(initial.playerAttitude) }
    var memory by remember(initial.name) { mutableStateOf(initial.shortMemory.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${initial.name}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NpcPresenceV1.values().forEach { item ->
                        FilterChip(selected = presence == item, onClick = { presence = item }, label = { Text(item.label) })
                    }
                }
                OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth(), label = { Text("当前目标") })
                OutlinedTextField(emotion, { emotion = it }, Modifier.fillMaxWidth(), label = { Text("情绪") })
                OutlinedTextField(intent, { intent = it }, Modifier.fillMaxWidth(), label = { Text("隐藏意图（仅导演）") }, minLines = 2)
                OutlinedTextField(attitude, { attitude = it }, Modifier.fillMaxWidth(), label = { Text("对玩家态度") })
                OutlinedTextField(memory, { memory = it }, Modifier.fillMaxWidth(), label = { Text("短期记忆，一行一条") }, minLines = 3)
            }
        },
        confirmButton = {
            Button({
                onSave(
                    initial.copy(
                        presence = presence,
                        currentGoal = goal.trim(),
                        emotion = emotion.trim().ifBlank { "平静" },
                        hiddenIntent = intent.trim(),
                        playerAttitude = attitude.trim().ifBlank { "中性" },
                        shortMemory = memory.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().takeLast(6),
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private val NPC_LIFE_FIELDS_V1 = setOf("在场", "目标", "情绪", "隐藏意图", "对玩家态度", "短期记忆", "冷却")
private const val MAX_NPC_LIFE_V1 = 18
private const val NPC_LIFE_MARKER_V1 = "【NPC生命状态｜仅导演】"
private const val NPC_LIFE_END_V1 = "【NPC生命状态结束】"
private const val NPC_LIFE_SITUATION_V1 = "【NPC态势】"
