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
import androidx.compose.ui.text.style.TextOverflow
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
enum class TavernAutonomyV1 {
    QUIET,
    BALANCED,
    ACTIVE;

    val label: String get() = when (this) {
        QUIET -> "克制"
        BALANCED -> "自然"
        ACTIVE -> "积极"
    }

    val prompt: String get() = when (this) {
        QUIET -> "NPC 主要回应玩家和现场刺激，不抢戏；一次只做一个小动作。"
        BALANCED -> "NPC 有自己的目标，会自然说话、移动、观察和做决定，但不替玩家决定。"
        ACTIVE -> "NPC 主动性较强，可以先开口、相互行动、制造小冲突或推动现场局势，但不能强迫玩家接受重大结果。"
    }
}

@Serializable
data class TavernSceneV1(
    val sessionId: String,
    val cast: List<String> = emptyList(),
    val autonomy: TavernAutonomyV1 = TavernAutonomyV1.BALANCED,
    val autoOpening: Boolean = true,
    val autoNpcFollowUp: Boolean = true,
    val opening: String = "",
    val latestBeatId: String = "",
    val latestBeatKind: String = "",
    val latestBeatTitle: String = "",
    val latestBeat: String = "",
    val latestChoices: List<String> = emptyList(),
    val injectedBeatId: String = "",
    val acknowledgedBeatId: String = "",
    val lastProcessedTurnId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class TavernArchiveV1(
    val novelId: String,
    val scenes: List<TavernSceneV1> = emptyList(),
)

data class TavernDirectorUiStateV1(
    val novelId: String = "",
    val scene: TavernSceneV1? = null,
    val scenes: List<TavernSceneV1> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class TavernDirectorViewModelV1(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(TavernDirectorUiStateV1())
    val state: StateFlow<TavernDirectorUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession, recommendedCast: List<String>) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) return
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val scene = existing ?: TavernSceneV1(
            sessionId = session.id,
            cast = recommendedCast.distinct().take(MAX_CAST_V1),
            lastProcessedTurnId = session.turns.lastOrNull()?.id.orEmpty(),
        )
        val scenes = if (archive.scenes.any { it.sessionId == session.id }) archive.scenes else archive.scenes + scene
        _state.value = TavernDirectorUiStateV1(novelId = novelId, scene = scene, scenes = scenes)
        saveArchive(TavernArchiveV1(novelId, scenes))
    }

    fun toggleCast(name: String) = mutate { scene ->
        val clean = name.trim()
        if (clean.isBlank()) scene else {
            val next = if (clean in scene.cast) scene.cast - clean else (scene.cast + clean).distinct().take(MAX_CAST_V1)
            scene.copy(cast = next)
        }
    }

    fun setAutonomy(value: TavernAutonomyV1) = mutate { it.copy(autonomy = value) }
    fun setAutoOpening(enabled: Boolean) = mutate { it.copy(autoOpening = enabled) }
    fun setAutoNpcFollowUp(enabled: Boolean) = mutate { it.copy(autoNpcFollowUp = enabled) }
    fun acknowledgeBeat() = mutate { scene -> scene.copy(acknowledgedBeatId = scene.latestBeatId) }
    fun markInjected(id: String) = mutate { scene -> if (scene.latestBeatId == id) scene.copy(injectedBeatId = id) else scene }

    fun generateOpening(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        candidates: List<StoryCanonRoleCandidateV1>,
    ) = generateBeat(book, session, runtime, candidates, kind = "opening", triggerTurnId = "")

    fun generateNpcBeat(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        candidates: List<StoryCanonRoleCandidateV1>,
        triggerTurnId: String = "",
    ) = generateBeat(book, session, runtime, candidates, kind = "npc", triggerTurnId = triggerTurnId)

    fun clearError() = _state.update { it.copy(error = null) }

    private fun generateBeat(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        candidates: List<StoryCanonRoleCandidateV1>,
        kind: String,
        triggerTurnId: String,
    ) {
        val scene = _state.value.scene ?: return
        if (_state.value.busy || scene.sessionId != session.id) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val gateway = activeGateway()
                val castDetails = scene.cast.mapNotNull { name ->
                    candidates.firstOrNull { it.name == name || name in it.aliases }
                }.joinToString("\n") { role ->
                    buildString {
                        append("- ").append(role.name)
                        if (role.description.isNotBlank()) append("：").append(role.description.take(360))
                        val facts = role.knownFacts.takeLast(5)
                        if (facts.isNotEmpty()) append("｜截至锚点已知=").append(facts.joinToString("；") { it.fact.take(120) })
                    }
                }
                val recent = session.turns.takeLast(6).joinToString("\n\n") { turn ->
                    "玩家：${turn.player.ifBlank { "（无）" }}\n剧情：${turn.narration.takeLast(900)}"
                }
                val world = runtime?.world ?: StoryWorldStateV3()
                val knowledge = runtime?.knowledge.orEmpty().takeLast(60).joinToString("\n") { entry ->
                    "${entry.character}｜${entry.kind.label}｜${entry.fact}${if (entry.source.isBlank()) "" else "｜依据=${entry.source}"}"
                }
                val relations = runtime?.relationships.orEmpty().takeLast(40).joinToString("\n") { relation ->
                    "${relation.from}→${relation.to}｜${relation.label}=${relation.value.ifBlank { "已建立" }}"
                }
                val player = session.playerProfile
                val modeRule = if (kind == "opening") {
                    "这是进入故事后的自动开场。玩家还没有做任何新动作。先让现场活起来：环境、正在发生的事情和在场 NPC 可以主动说话或行动，但绝不能替玩家决定动作、台词、心理或重大选择。"
                } else {
                    "这是 NPC 自主行动拍。基于刚发生的剧情，让在场 NPC 按自己的目标继续一小步；允许多角色自然对话和互相行动，但不要把玩家写成已经同意、已经行动或已经产生特定心理。"
                }
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是琅嬛“酒馆式互动故事”场景导演。
                            当前内容属于独立故事分支，原著正文不可修改。只允许使用截至第 ${session.anchorChapter} 章可见的信息，以及当前分支已经真实发生的信息。
                            绝对禁止引用锚点之后的原著剧情、秘密、人物关系变化或未来设定。人物只能按自己的知识边界行动。
                            $modeRule
                            当前 NPC 自主程度：${scene.autonomy.label}。${scene.autonomy.prompt}
                            多角色同场时，每个人必须有不同目的、语气和注意力，不要所有人轮流解释设定。
                            场景推进必须连续，不瞬移、不无依据跳时、不突然收尾、不替玩家作重大决定。
                            必须返回 GeneratedChapter JSON：title=本次场景拍的短标题；content=220-700字沉浸式正文；summary=2-4条玩家可以直接采取的回应/动作，每行一条；stateChanges=[]；touchedForeshadowingIds=[]。
                            不要输出 JSON 外文字。
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》｜类型：${book.genre}
                            分支：${session.title}｜锚点：第${session.anchorChapter}章 ${session.anchorTitle}
                            玩家角色：${player.name.ifBlank { "未命名" }}｜${player.identity.ifBlank { "身份未设定" }}
                            玩家已知：${player.knownFacts.ifBlank { "仅知道已展示信息" }}
                            玩家禁知：${player.forbiddenKnowledge.ifBlank { "不得知道未来原著信息" }}

                            【原著锚点快照】
                            ${session.worldSnapshot.takeLast(2_800)}

                            【当前场景】
                            地点=${world.location.ifBlank { "未记录" }}；时间=${world.time.ifBlank { "未记录" }}；环境=${world.atmosphere.ifBlank { "未记录" }}；局势=${world.situation.ifBlank { "未记录" }}

                            【在场演员】
                            ${scene.cast.joinToString("、").ifBlank { "尚未指定固定 NPC；只能使用当前正文明确出现的人物或普通背景角色" }}
                            ${castDetails.ifBlank { "暂无额外角色卡" }}

                            【人物知识账本】
                            ${knowledge.ifBlank { "暂无结构化条目" }}

                            【关系网】
                            ${relations.ifBlank { "暂无结构化条目" }}

                            【最近互动】
                            ${recent.ifBlank { "尚未开始互动" }}
                        """.trimIndent(),
                    )
                )
                val content = output.content.trim().ifBlank { error("AI 没有返回场景内容") }
                val choices = output.summary.lines()
                    .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }
                    .take(4)
                scene.copy(
                    opening = if (kind == "opening") content else scene.opening,
                    latestBeatId = UUID.randomUUID().toString(),
                    latestBeatKind = kind,
                    latestBeatTitle = output.title.trim().ifBlank { if (kind == "opening") "自动开场" else "NPC 主动行动" },
                    latestBeat = content,
                    latestChoices = choices,
                    acknowledgedBeatId = "",
                    lastProcessedTurnId = triggerTurnId.ifBlank { scene.lastProcessedTurnId },
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                persistScene(updated)
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "场景导演生成失败") }
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

    private fun mutate(transform: (TavernSceneV1) -> TavernSceneV1) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy) return
        val updated = transform(scene).copy(updatedAt = System.currentTimeMillis())
        persistScene(updated)
    }

    private fun persistScene(updated: TavernSceneV1) {
        val current = _state.value
        val scenes = current.scenes.map { if (it.sessionId == updated.sessionId) updated else it }
            .let { list -> if (list.any { it.sessionId == updated.sessionId }) list else list + updated }
        _state.update { it.copy(scene = updated, scenes = scenes, busy = false) }
        if (current.novelId.isNotBlank()) saveArchive(TavernArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_tavern_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): TavernArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return TavernArchiveV1(novelId)
        return runCatching { json.decodeFromString(TavernArchiveV1.serializer(), file.readText()) }
            .getOrElse { TavernArchiveV1(novelId) }
    }

    private fun saveArchive(archive: TavernArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(TavernArchiveV1.serializer(), archive)) }
    }
}

internal fun recommendTavernCastV1(
    candidates: List<StoryCanonRoleCandidateV1>,
    snapshot: String,
    playerName: String,
): List<String> {
    val text = snapshot.takeLast(4_800)
    val matched = candidates.asSequence()
        .filter { role ->
            role.name.isNotBlank() && (text.contains(role.name) || role.aliases.any { alias -> alias.isNotBlank() && text.contains(alias) })
        }
        .sortedWith(compareByDescending<StoryCanonRoleCandidateV1> { it.lastChapter }.thenByDescending { it.mentions })
        .map { it.name }
        .filter { it != playerName }
        .distinct()
        .take(5)
        .toList()
    return (listOf(playerName).filter { it.isNotBlank() } + matched).distinct().take(MAX_CAST_V1)
}

internal fun mergeTavernDirectorNoteV1(
    existing: String,
    beatTitle: String,
    beat: String,
    cast: List<String>,
): String {
    val canonIndex = existing.indexOf(CANON_MARKER_V1)
    val beforeCanon = if (canonIndex >= 0) existing.substring(0, canonIndex) else existing
    val canon = if (canonIndex >= 0) existing.substring(canonIndex) else ""
    val manual = beforeCanon.substringBefore(TAVERN_MARKER_V1).trim()
    val director = buildString {
        append(TAVERN_MARKER_V1).append('\n')
        append("标题：").append(beatTitle.ifBlank { "当前场景推进" }).append('\n')
        if (cast.isNotEmpty()) append("在场：").append(cast.joinToString("、")).append('\n')
        append(beat.trim().takeLast(3_800))
    }
    return buildString {
        if (manual.isNotBlank()) append(manual).append("\n\n")
        append(director)
        if (canon.isNotBlank()) append("\n\n").append(canon.trim())
    }.trim()
}

internal fun advanceTavernTimeV1(current: String, step: String): String {
    val base = current.trim().takeLast(80)
    return if (base.isBlank()) step else "$base · $step"
}

private enum class TavernDirectorTabV1(val label: String) {
    DIRECTOR("导演"), CAST("在场角色"), SCENE("场景")
}

@Composable
fun StoryPlayPanelV7(
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
    val session = storyState.active
    val runtime = storyState.runtime
    val scene = director.scene
    val anchor = libraryState.readingChapter
        ?: libraryState.chapters.firstOrNull { it.chapterNumber == session?.anchorChapter }
        ?: libraryState.chapters.lastOrNull()

    var showDirector by remember { mutableStateOf(false) }
    var showBeat by remember { mutableStateOf(false) }

    val anchorChapter = session?.anchorChapter ?: anchor?.chapterNumber ?: book.currentChapter.coerceAtLeast(1)
    LaunchedEffect(book.id, anchorChapter) { catalogVm.load(book.id, anchorChapter) }

    LaunchedEffect(book.id, session?.id, catalog.candidates, session?.playerProfile?.name) {
        val active = session ?: return@LaunchedEffect
        val recommended = recommendTavernCastV1(catalog.candidates, active.worldSnapshot, active.playerProfile.name)
        directorVm.open(book.id, active, recommended)
    }

    LaunchedEffect(
        aiReady,
        session?.id,
        session?.playerProfile?.name,
        session?.turns?.size,
        scene?.autoOpening,
        scene?.opening,
        director.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val currentScene = scene ?: return@LaunchedEffect
        if (
            aiReady && currentScene.autoOpening && currentScene.opening.isBlank() &&
            active.turns.isEmpty() && active.playerProfile.name.isNotBlank() && !director.busy
        ) {
            directorVm.generateOpening(book, active, runtime, catalog.candidates)
        }
    }

    val latestTurn = session?.turns?.lastOrNull()
    LaunchedEffect(
        aiReady,
        session?.id,
        latestTurn?.id,
        scene?.autoNpcFollowUp,
        scene?.lastProcessedTurnId,
        director.busy,
        storyState.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val currentScene = scene ?: return@LaunchedEffect
        val turn = latestTurn ?: return@LaunchedEffect
        if (
            aiReady && currentScene.autoNpcFollowUp && turn.id != currentScene.lastProcessedTurnId &&
            !director.busy && !storyState.busy
        ) {
            directorVm.generateNpcBeat(book, active, runtime, catalog.candidates, triggerTurnId = turn.id)
        }
    }

    LaunchedEffect(scene?.latestBeatId, scene?.injectedBeatId, runtime?.world) {
        val currentScene = scene ?: return@LaunchedEffect
        if (currentScene.latestBeatId.isBlank() || currentScene.latestBeatId == currentScene.injectedBeatId) return@LaunchedEffect
        val world = runtime?.world ?: return@LaunchedEffect
        val mergedNotes = mergeTavernDirectorNoteV1(
            existing = world.notes,
            beatTitle = currentScene.latestBeatTitle,
            beat = currentScene.latestBeat,
            cast = currentScene.cast,
        )
        storyVm.updateWorld(
            world.copy(
                situation = currentScene.latestBeatTitle.ifBlank { world.situation },
                notes = mergedNotes,
            )
        )
        directorVm.markInjected(currentScene.latestBeatId)
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV6(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )

        SmallFloatingActionButton(
            onClick = { showDirector = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 154.dp),
        ) {
            if (director.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.TheaterComedy, "酒馆导演台")
        }

        if (
            scene != null && scene.latestBeat.isNotBlank() &&
            scene.latestBeatId != scene.acknowledgedBeatId
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 68.dp)
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (scene.latestBeatKind == "opening") Icons.Rounded.AutoStories else Icons.Rounded.Groups,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(scene.latestBeatTitle.ifBlank { "场景继续" }, fontWeight = FontWeight.Bold)
                            Text(
                                if (scene.latestBeatKind == "opening") "自动开场" else "NPC 主动行动",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton({ showBeat = true }) { Text("展开") }
                        IconButton(directorVm::acknowledgeBeat) { Icon(Icons.Rounded.Close, "收起") }
                    }
                    Text(
                        scene.latestBeat.replace('\n', ' '),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showDirector && session != null && scene != null) {
        TavernDirectorDialogV1(
            scene = scene,
            runtime = runtime,
            candidates = catalog.candidates,
            loadingCatalog = catalog.loading,
            aiReady = aiReady,
            busy = director.busy,
            error = director.error,
            onDismiss = { showDirector = false },
            onToggleCast = directorVm::toggleCast,
            onAutonomy = directorVm::setAutonomy,
            onAutoOpening = directorVm::setAutoOpening,
            onAutoNpc = directorVm::setAutoNpcFollowUp,
            onGenerateOpening = { directorVm.generateOpening(book, session, runtime, catalog.candidates) },
            onGenerateNpc = { directorVm.generateNpcBeat(book, session, runtime, catalog.candidates) },
            onSaveWorld = storyVm::updateWorld,
            onClearError = directorVm::clearError,
            onShowBeat = { showBeat = true },
        )
    }

    if (showBeat && session != null && scene != null) {
        TavernBeatDialogV1(
            scene = scene,
            busy = director.busy || storyState.busy,
            onDismiss = {
                directorVm.acknowledgeBeat()
                showBeat = false
            },
            onRespond = { choice ->
                directorVm.acknowledgeBeat()
                showBeat = false
                storyVm.act(book, anchor?.content.orEmpty(), choice)
            },
            onContinueWatching = {
                directorVm.generateNpcBeat(book, session, runtime, catalog.candidates)
            },
        )
    }
}

@Composable
private fun TavernDirectorDialogV1(
    scene: TavernSceneV1,
    runtime: StoryRuntimeSessionV3?,
    candidates: List<StoryCanonRoleCandidateV1>,
    loadingCatalog: Boolean,
    aiReady: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onToggleCast: (String) -> Unit,
    onAutonomy: (TavernAutonomyV1) -> Unit,
    onAutoOpening: (Boolean) -> Unit,
    onAutoNpc: (Boolean) -> Unit,
    onGenerateOpening: () -> Unit,
    onGenerateNpc: () -> Unit,
    onSaveWorld: (StoryWorldStateV3) -> Unit,
    onClearError: () -> Unit,
    onShowBeat: () -> Unit,
) {
    var tab by remember { mutableStateOf(TavernDirectorTabV1.DIRECTOR) }
    var search by remember { mutableStateOf("") }
    val sourceWorld = runtime?.world ?: StoryWorldStateV3()
    var location by remember(sourceWorld) { mutableStateOf(sourceWorld.location) }
    var time by remember(sourceWorld) { mutableStateOf(sourceWorld.time) }
    var atmosphere by remember(sourceWorld) { mutableStateOf(sourceWorld.atmosphere) }
    var situation by remember(sourceWorld) { mutableStateOf(sourceWorld.situation) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("酒馆导演台") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TavernDirectorTabV1.values().forEach { item ->
                        FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.label) })
                    }
                }
                HorizontalDivider()
                when (tab) {
                    TavernDirectorTabV1.DIRECTOR -> Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("NPC 自主程度", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TavernAutonomyV1.values().forEach { item ->
                                FilterChip(
                                    selected = scene.autonomy == item,
                                    onClick = { onAutonomy(item) },
                                    label = { Text(item.label) },
                                    enabled = !busy,
                                )
                            }
                        }
                        Text(scene.autonomy.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("自动开场", fontWeight = FontWeight.SemiBold)
                                Text("选好身份后，先让场景和 NPC 活起来。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(scene.autoOpening, onAutoOpening, enabled = !busy)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("每轮后 NPC 自主行动", fontWeight = FontWeight.SemiBold)
                                Text("玩家行动结束后，再让 NPC 按自己的目标推进一拍。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(scene.autoNpcFollowUp, onAutoNpc, enabled = !busy)
                        }

                        Button(
                            onClick = onGenerateOpening,
                            enabled = aiReady && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.AutoStories, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (scene.opening.isBlank()) "生成自动开场" else "重新生成开场")
                        }
                        FilledTonalButton(
                            onClick = onGenerateNpc,
                            enabled = aiReady && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Groups, null)
                            Spacer(Modifier.width(6.dp))
                            Text("让 NPC 主动行动")
                        }
                        if (scene.latestBeat.isNotBlank()) {
                            OutlinedButton(onShowBeat, Modifier.fillMaxWidth(), enabled = !busy) { Text("查看最近场景拍") }
                        }
                        error?.let { message ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                IconButton(onClearError) { Icon(Icons.Rounded.Close, "关闭") }
                            }
                        }
                    }

                    TavernDirectorTabV1.CAST -> Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("搜索原著角色 / 别名") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前在场：${scene.cast.joinToString("、").ifBlank { "未指定" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (loadingCatalog) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            val filtered = candidates.filter { role ->
                                search.isBlank() || role.name.contains(search, true) || role.aliases.any { it.contains(search, true) }
                            }.take(60)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(filtered, key = { it.name }) { role ->
                                    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = role.name in scene.cast,
                                                onCheckedChange = { onToggleCast(role.name) },
                                                enabled = !busy && (role.name in scene.cast || scene.cast.size < MAX_CAST_V1),
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(role.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "可见至第${role.lastChapter}章 · 出场${role.mentions}次${if (role.aliases.isEmpty()) "" else " · 别名 ${role.aliases.take(3).joinToString("/")}"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TavernDirectorTabV1.SCENE -> Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("快速调整当前场景", fontWeight = FontWeight.Bold)
                        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点") })
                        OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth(), label = { Text("时间") })
                        OutlinedTextField(atmosphere, { atmosphere = it }, Modifier.fillMaxWidth(), label = { Text("环境 / 氛围") })
                        OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), label = { Text("当前局势") }, minLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { time = advanceTavernTimeV1(time, "片刻后") },
                                label = { Text("片刻后") },
                                enabled = !busy,
                            )
                            AssistChip(
                                onClick = { time = advanceTavernTimeV1(time, "约30分钟后") },
                                label = { Text("+30分钟") },
                                enabled = !busy,
                            )
                            AssistChip(
                                onClick = { time = advanceTavernTimeV1(time, "进入下一时段") },
                                label = { Text("下一时段") },
                                enabled = !busy,
                            )
                        }
                        Button(
                            onClick = {
                                onSaveWorld(
                                    sourceWorld.copy(
                                        location = location.trim(),
                                        time = time.trim(),
                                        atmosphere = atmosphere.trim(),
                                        situation = situation.trim(),
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) { Text("保存场景状态") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss, enabled = !busy) { Text("完成") } },
    )
}

@Composable
private fun TavernBeatDialogV1(
    scene: TavernSceneV1,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRespond: (String) -> Unit,
    onContinueWatching: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(scene.latestBeatTitle.ifBlank { "场景继续" }) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (scene.latestBeatKind == "opening") "自动开场 · 玩家尚未被替做决定" else "NPC 自主行动 · 不消耗玩家动作",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(scene.latestBeat)
                if (scene.latestChoices.isNotEmpty()) {
                    HorizontalDivider()
                    Text("你可以直接回应", fontWeight = FontWeight.Bold)
                    scene.latestChoices.forEach { choice ->
                        OutlinedButton(
                            onClick = { onRespond(choice) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(choice, Modifier.fillMaxWidth()) }
                    }
                }
                FilledTonalButton(
                    onClick = onContinueWatching,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    Icon(Icons.Rounded.Visibility, null)
                    Spacer(Modifier.width(6.dp))
                    Text("我先不行动，让 NPC 继续")
                }
            }
        },
        confirmButton = { TextButton(onDismiss, enabled = !busy) { Text("回到故事") } },
    )
}

private const val MAX_CAST_V1 = 8
private const val TAVERN_MARKER_V1 = "【酒馆导演事件】"
private const val CANON_MARKER_V1 = "【原著章节边界证据】"
