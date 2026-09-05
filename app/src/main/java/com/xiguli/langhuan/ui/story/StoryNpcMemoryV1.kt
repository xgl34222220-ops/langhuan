package com.xiguli.langhuan.ui.story

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
enum class NpcMemoryPrivacyV1 {
    PRIVATE,
    PUBLIC;

    val label: String get() = when (this) {
        PRIVATE -> "私有"
        PUBLIC -> "公开"
    }
}

@Serializable
data class NpcLongMemoryV1(
    val id: String = UUID.randomUUID().toString(),
    val owner: String,
    val summary: String,
    val privacy: NpcMemoryPrivacyV1 = NpcMemoryPrivacyV1.PRIVATE,
    val importance: Int = 3,
    val evidence: String = "",
    val createdAtTurnId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class NpcPlanStatusV1 {
    ACTIVE,
    BLOCKED,
    DONE,
    ABANDONED;

    val label: String get() = when (this) {
        ACTIVE -> "进行中"
        BLOCKED -> "受阻"
        DONE -> "已完成"
        ABANDONED -> "已放弃"
    }
}

@Serializable
data class NpcPlanV1(
    val id: String = UUID.randomUUID().toString(),
    val owner: String,
    val goal: String,
    val steps: List<String> = emptyList(),
    val currentStep: Int = 0,
    val status: NpcPlanStatusV1 = NpcPlanStatusV1.ACTIVE,
    val privateReason: String = "",
    val priority: Int = 3,
    val createdAtTurnId: String = "",
    val updatedAtTurnId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NpcMemorySceneV1(
    val sessionId: String,
    val memories: List<NpcLongMemoryV1> = emptyList(),
    val plans: List<NpcPlanV1> = emptyList(),
    val autoConsolidate: Boolean = true,
    val lastInputKey: String = "",
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class NpcMemoryArchiveV1(
    val novelId: String,
    val scenes: List<NpcMemorySceneV1> = emptyList(),
)

data class NpcMemoryUiStateV1(
    val novelId: String = "",
    val scene: NpcMemorySceneV1? = null,
    val scenes: List<NpcMemorySceneV1> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val syncToken: Long = 0L,
)

internal data class NpcMemoryChangeV1(
    val owner: String,
    val field: String,
    val value: String,
    val evidence: String = "",
)

internal data class NpcMemorySliceV1(
    val owner: String,
    val memories: List<NpcLongMemoryV1>,
    val plans: List<NpcPlanV1>,
)

class NpcMemoryViewModelV1(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(NpcMemoryUiStateV1())
    val state: StateFlow<NpcMemoryUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) return
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val scene = existing ?: NpcMemorySceneV1(sessionId = session.id)
        val scenes = if (archive.scenes.any { it.sessionId == session.id }) archive.scenes else archive.scenes + scene
        _state.value = NpcMemoryUiStateV1(novelId = novelId, scene = scene, scenes = scenes)
        saveArchive(NpcMemoryArchiveV1(novelId, scenes))
    }

    fun setAutoConsolidate(enabled: Boolean) = mutate("长期记忆自动沉淀已修改") {
        it.copy(autoConsolidate = enabled)
    }

    fun addMemory(owner: String, text: String, privacy: NpcMemoryPrivacyV1, importance: Int = 3) {
        val cleanOwner = owner.trim()
        val cleanText = text.trim()
        if (cleanOwner.isBlank() || cleanText.isBlank()) return
        mutate("已加入 ${cleanOwner} 的长期记忆") { scene ->
            val next = NpcLongMemoryV1(
                owner = cleanOwner,
                summary = cleanText.take(420),
                privacy = privacy,
                importance = importance.coerceIn(1, 5),
                evidence = "作者手动添加",
            )
            scene.copy(memories = compactNpcMemoriesV1(scene.memories + next))
        }
    }

    fun deleteMemory(id: String) = mutate("长期记忆已删除") { scene ->
        scene.copy(memories = scene.memories.filterNot { it.id == id })
    }

    fun deletePlan(id: String) = mutate("角色计划已删除") { scene ->
        scene.copy(plans = scene.plans.filterNot { it.id == id })
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun reconcile(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        lifeScene: NpcLifeSceneV1,
        force: Boolean = false,
    ) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy || scene.sessionId != session.id) return
        val latestTurn = session.turns.lastOrNull()
        val lifeDigest = lifeScene.states.joinToString("|") {
            "${it.name}:${it.presence}:${it.currentGoal}:${it.emotion}:${it.hiddenIntent}:${it.playerAttitude}:${it.shortMemory.takeLast(2)}"
        }.hashCode()
        val inputKey = "${session.id}|${latestTurn?.id.orEmpty()}|${lifeScene.beatCounter}|$lifeDigest"
        if (!force && inputKey == scene.lastInputKey) return

        val owners = lifeScene.states.map { it.name.trim() }
            .filter { it.isNotBlank() && it != session.playerProfile.name }
            .distinct()
            .take(MAX_MEMORY_NPCS_V1)
        if (owners.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val gateway = activeGateway()
                val world = runtime?.world ?: StoryWorldStateV3()
                val lifeText = lifeScene.states.filter { it.name in owners }.joinToString("\n") { npc ->
                    "${npc.name}｜${npc.presence.label}｜目标=${npc.currentGoal.ifBlank { "未定" }}｜情绪=${npc.emotion}｜隐藏意图=${npc.hiddenIntent.ifBlank { "无" }}｜对玩家=${npc.playerAttitude}｜短期记忆=${npc.shortMemory.takeLast(4).joinToString("；").ifBlank { "无" }}"
                }
                val memoryText = owners.joinToString("\n\n") { owner ->
                    val slice = memorySliceForNpcV1(scene, owner)
                    buildString {
                        append("【").append(owner).append("】\n")
                        if (slice.memories.isEmpty()) append("长期记忆：无\n") else {
                            append("长期记忆：\n")
                            slice.memories.takeLast(10).forEach { memory ->
                                append("- ").append(memory.privacy.label).append("/重要度").append(memory.importance)
                                    .append("：").append(memory.summary).append('\n')
                            }
                        }
                        val activePlans = slice.plans.filter { it.status == NpcPlanStatusV1.ACTIVE || it.status == NpcPlanStatusV1.BLOCKED }
                        if (activePlans.isEmpty()) append("计划：无") else {
                            append("计划：\n")
                            activePlans.take(4).forEach { plan ->
                                append("- ").append(plan.status.label).append("/P").append(plan.priority)
                                    .append("：").append(plan.goal)
                                if (plan.steps.isNotEmpty()) append("｜步骤=").append(plan.steps.joinToString(" > "))
                                if (plan.privateReason.isNotBlank()) append("｜私下原因=").append(plan.privateReason)
                                append('\n')
                            }
                        }
                    }
                }
                val recent = session.turns.takeLast(7).joinToString("\n\n") { turn ->
                    "玩家：${turn.player.ifBlank { "（无）" }}\n剧情：${turn.narration.takeLast(900)}"
                }
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是琅嬛的“角色记忆与计划管理器”，不直接写小说正文。
                            当前分支锚定原著第 ${session.anchorChapter} 章。只允许使用截至锚点可见的角色信息、当前分支已经真实发生的事件，以及角色本人确实能感知/得知的信息；绝对禁止使用未来原著剧情。

                            你的工作：
                            1. 把值得长期保留的经历从短期状态沉淀成长期记忆；琐碎动作不要记。
                            2. 区分“私有记忆”和“公开记忆”。私有记忆只属于该角色，其他 NPC 不得把它当作已知事实。
                            3. 维护角色自己的计划链：目标、接下来 1-4 步、私下原因、优先级。角色可以改计划、受阻或完成，不要永远执行同一目标。
                            4. 只有在场角色通常能把现场事件写入记忆；附近/离场角色除非明确收到消息，否则不得凭空知道。
                            5. 记忆必须是角色视角事实，不是全知旁白，不得把隐藏真相自动塞给角色。

                            必须返回 GeneratedChapter JSON：title="npc-memory"；content="ok"；summary=一句本次整理说明；touchedForeshadowingIds=[]。
                            stateChanges 仅允许：subject="MEM:角色名"，且角色名必须来自允许名单。
                            field 仅允许以下四种：
                            - “长期记忆:私有”：after 格式“重要度1-5|记忆内容”
                            - “长期记忆:公开”：after 格式“重要度1-5|记忆内容”
                            - “计划”：after 格式“目标||步骤1>步骤2>步骤3||私下原因||优先级1-5”
                            - “计划完成”：after 填现有计划的目标文本
                            没有新增长期价值时可以完全不输出 stateChanges。不要重复已有记忆。
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》｜分支：${session.title}
                            锚点：第${session.anchorChapter}章 ${session.anchorTitle}
                            玩家：${session.playerProfile.name.ifBlank { "未命名" }}
                            当前场景：地点=${world.location.ifBlank { "未记录" }}；时间=${world.time.ifBlank { "未记录" }}；局势=${world.situation.ifBlank { "未记录" }}

                            【允许维护的 NPC】
                            ${owners.joinToString("、")}

                            【当前 NPC 生命状态】
                            $lifeText

                            【每个 NPC 自己的长期记忆与计划】
                            $memoryText

                            【最近分支事件】
                            ${recent.ifBlank { "暂无玩家回合；可仅根据当前场景建立初始计划，但不要虚构经历。" }}
                        """.trimIndent(),
                    )
                )
                val changes = output.stateChanges.mapNotNull { change ->
                    val subject = change.subject.trim()
                    if (!subject.startsWith("MEM:")) return@mapNotNull null
                    val owner = subject.substringAfter("MEM:").trim()
                    if (owner !in owners) return@mapNotNull null
                    val field = change.field.trim()
                    if (field !in NPC_MEMORY_FIELDS_V1) return@mapNotNull null
                    val value = change.after.trim()
                    if (value.isBlank()) return@mapNotNull null
                    NpcMemoryChangeV1(owner, field, value, change.evidence.trim())
                }
                applyNpcMemoryChangesV1(
                    scene = scene,
                    changes = changes,
                    allowedOwners = owners.toSet(),
                    turnId = latestTurn?.id.orEmpty(),
                ).copy(
                    lastInputKey = inputKey,
                    revision = scene.revision + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                persistScene(updated, "角色长期记忆与计划已整理")
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "角色记忆整理失败") }
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

    private fun mutate(message: String, transform: (NpcMemorySceneV1) -> NpcMemorySceneV1) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy) return
        persistScene(transform(scene).copy(revision = scene.revision + 1, updatedAt = System.currentTimeMillis()), message)
    }

    private fun persistScene(updated: NpcMemorySceneV1, notice: String? = null) {
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
        if (current.novelId.isNotBlank()) saveArchive(NpcMemoryArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_npc_memory_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): NpcMemoryArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return NpcMemoryArchiveV1(novelId)
        return runCatching { json.decodeFromString(NpcMemoryArchiveV1.serializer(), file.readText()) }
            .getOrElse { NpcMemoryArchiveV1(novelId) }
    }

    private fun saveArchive(archive: NpcMemoryArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(NpcMemoryArchiveV1.serializer(), archive)) }
    }
}

internal fun memorySliceForNpcV1(scene: NpcMemorySceneV1, owner: String): NpcMemorySliceV1 =
    NpcMemorySliceV1(
        owner = owner,
        memories = scene.memories.filter { it.owner == owner },
        plans = scene.plans.filter { it.owner == owner },
    )

internal fun applyNpcMemoryChangesV1(
    scene: NpcMemorySceneV1,
    changes: List<NpcMemoryChangeV1>,
    allowedOwners: Set<String>,
    turnId: String,
): NpcMemorySceneV1 {
    var memories = scene.memories
    var plans = scene.plans
    val now = System.currentTimeMillis()

    changes.forEach { change ->
        if (change.owner !in allowedOwners) return@forEach
        when (change.field) {
            "长期记忆:私有", "长期记忆:公开" -> {
                val parsed = parseNpcMemoryPayloadV1(change.value)
                if (parsed.second.isBlank()) return@forEach
                val privacy = if (change.field.endsWith("私有")) NpcMemoryPrivacyV1.PRIVATE else NpcMemoryPrivacyV1.PUBLIC
                val incoming = NpcLongMemoryV1(
                    owner = change.owner,
                    summary = parsed.second.take(420),
                    privacy = privacy,
                    importance = parsed.first,
                    evidence = change.evidence.take(320),
                    createdAtTurnId = turnId,
                    createdAt = now,
                    lastUsedAt = now,
                )
                memories = upsertNpcMemoryV1(memories, incoming)
            }
            "计划" -> {
                val plan = parseNpcPlanPayloadV1(change.owner, change.value, turnId, now) ?: return@forEach
                val normalized = normalizeMemoryTextV1(plan.goal)
                val existing = plans.firstOrNull {
                    it.owner == change.owner && normalizeMemoryTextV1(it.goal) == normalized && it.status != NpcPlanStatusV1.DONE
                }
                plans = if (existing == null) {
                    plans + plan
                } else {
                    plans.map {
                        if (it.id == existing.id) plan.copy(id = existing.id, createdAtTurnId = existing.createdAtTurnId) else it
                    }
                }
            }
            "计划完成" -> {
                val target = normalizeMemoryTextV1(change.value)
                plans = plans.map { plan ->
                    if (
                        plan.owner == change.owner && plan.status != NpcPlanStatusV1.DONE &&
                        (normalizeMemoryTextV1(plan.goal) == target || normalizeMemoryTextV1(plan.goal).contains(target) || target.contains(normalizeMemoryTextV1(plan.goal)))
                    ) plan.copy(status = NpcPlanStatusV1.DONE, updatedAtTurnId = turnId, updatedAt = now) else plan
                }
            }
        }
    }

    return scene.copy(
        memories = compactNpcMemoriesV1(memories),
        plans = compactNpcPlansV1(plans),
    )
}

private fun parseNpcMemoryPayloadV1(value: String): Pair<Int, String> {
    val parts = value.split('|', limit = 2)
    if (parts.size < 2) return 3 to value.trim()
    val importance = parts[0].filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 5) ?: 3
    return importance to parts[1].trim()
}

private fun parseNpcPlanPayloadV1(owner: String, value: String, turnId: String, now: Long): NpcPlanV1? {
    val parts = value.split("||")
    val goal = parts.getOrNull(0)?.trim().orEmpty()
    if (goal.isBlank()) return null
    val steps = parts.getOrNull(1).orEmpty().split('>')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(4)
    val reason = parts.getOrNull(2)?.trim().orEmpty().take(360)
    val priority = parts.getOrNull(3).orEmpty().filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 5) ?: 3
    return NpcPlanV1(
        owner = owner,
        goal = goal.take(300),
        steps = steps,
        privateReason = reason,
        priority = priority,
        createdAtTurnId = turnId,
        updatedAtTurnId = turnId,
        updatedAt = now,
    )
}

private fun upsertNpcMemoryV1(existing: List<NpcLongMemoryV1>, incoming: NpcLongMemoryV1): List<NpcLongMemoryV1> {
    val normalized = normalizeMemoryTextV1(incoming.summary)
    val duplicate = existing.firstOrNull {
        it.owner == incoming.owner && normalizeMemoryTextV1(it.summary) == normalized
    }
    return if (duplicate == null) {
        existing + incoming
    } else {
        existing.map { old ->
            if (old.id == duplicate.id) {
                old.copy(
                    privacy = if (old.privacy == NpcMemoryPrivacyV1.PRIVATE || incoming.privacy == NpcMemoryPrivacyV1.PRIVATE) NpcMemoryPrivacyV1.PRIVATE else NpcMemoryPrivacyV1.PUBLIC,
                    importance = maxOf(old.importance, incoming.importance),
                    evidence = incoming.evidence.ifBlank { old.evidence },
                    lastUsedAt = incoming.lastUsedAt,
                )
            } else old
        }
    }
}

internal fun compactNpcMemoriesV1(memories: List<NpcLongMemoryV1>): List<NpcLongMemoryV1> =
    memories.groupBy { it.owner }.values.flatMap { owned ->
        owned.distinctBy { normalizeMemoryTextV1(it.summary) }
            .sortedWith(compareByDescending<NpcLongMemoryV1> { it.importance }.thenByDescending { it.lastUsedAt })
            .take(MAX_LONG_MEMORIES_PER_NPC_V1)
    }.sortedWith(compareBy<NpcLongMemoryV1> { it.owner }.thenBy { it.createdAt })

private fun compactNpcPlansV1(plans: List<NpcPlanV1>): List<NpcPlanV1> =
    plans.groupBy { it.owner }.values.flatMap { owned ->
        val active = owned.filter { it.status == NpcPlanStatusV1.ACTIVE || it.status == NpcPlanStatusV1.BLOCKED }
            .sortedWith(compareByDescending<NpcPlanV1> { it.priority }.thenByDescending { it.updatedAt })
            .take(MAX_ACTIVE_PLANS_PER_NPC_V1)
        val history = owned.filter { it.status == NpcPlanStatusV1.DONE || it.status == NpcPlanStatusV1.ABANDONED }
            .sortedByDescending { it.updatedAt }
            .take(MAX_PLAN_HISTORY_PER_NPC_V1)
        active + history
    }.sortedWith(compareBy<NpcPlanV1> { it.owner }.thenByDescending { it.priority }.thenByDescending { it.updatedAt })

private fun normalizeMemoryTextV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s，。！？、,.!?：:；;‘’“”\"'（）()【】\\[\\]]+"), "")
    .take(260)

internal fun activePlanForNpcV1(scene: NpcMemorySceneV1, owner: String): NpcPlanV1? =
    scene.plans.asSequence()
        .filter { it.owner == owner && it.status == NpcPlanStatusV1.ACTIVE }
        .sortedWith(compareByDescending<NpcPlanV1> { it.priority }.thenByDescending { it.updatedAt })
        .firstOrNull()

internal fun applyNpcPlansToLifeV1(
    states: List<NpcLifeStateV1>,
    memoryScene: NpcMemorySceneV1,
): List<NpcLifeStateV1> = states.map { npc ->
    val plan = activePlanForNpcV1(memoryScene, npc.name) ?: return@map npc
    val step = plan.steps.getOrNull(plan.currentStep.coerceIn(0, (plan.steps.size - 1).coerceAtLeast(0))).orEmpty()
    npc.copy(
        currentGoal = plan.goal.ifBlank { npc.currentGoal },
        hiddenIntent = buildString {
            if (plan.privateReason.isNotBlank()) append(plan.privateReason)
            if (step.isNotBlank()) {
                if (isNotEmpty()) append("；")
                append("下一步：").append(step)
            }
        }.ifBlank { npc.hiddenIntent },
    )
}

@Composable
fun StoryPlayPanelV9(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val lifeVm: NpcLifeViewModelV1 = viewModel()
    val life by lifeVm.state.collectAsStateWithLifecycle()
    val memoryVm: NpcMemoryViewModelV1 = viewModel()
    val memory by memoryVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val lifeScene = life.scene
    val memoryScene = memory.scene
    var showMemory by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id, lifeScene?.sessionId) {
        val active = session ?: return@LaunchedEffect
        if (lifeScene == null) return@LaunchedEffect
        memoryVm.open(book.id, active)
    }

    val latestTurn = session?.turns?.lastOrNull()
    LaunchedEffect(
        aiReady,
        session?.id,
        latestTurn?.id,
        lifeScene?.beatCounter,
        lifeScene?.updatedAt,
        memoryScene?.autoConsolidate,
        memory.busy,
        life.busy,
        storyState.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val lifeState = lifeScene ?: return@LaunchedEffect
        val memoryState = memoryScene ?: return@LaunchedEffect
        if (aiReady && memoryState.autoConsolidate && !memory.busy && !life.busy && !storyState.busy) {
            memoryVm.reconcile(book, active, storyState.runtime, lifeState)
        }
    }

    LaunchedEffect(memory.syncToken, lifeScene?.states, memoryScene?.plans) {
        val lifeState = lifeScene ?: return@LaunchedEffect
        val memoryState = memoryScene ?: return@LaunchedEffect
        val planned = applyNpcPlansToLifeV1(lifeState.states, memoryState)
        planned.zip(lifeState.states).forEach { (next, old) ->
            if (next.currentGoal != old.currentGoal || next.hiddenIntent != old.hiddenIntent) {
                lifeVm.updateNpc(next)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV8(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )

        SmallFloatingActionButton(
            onClick = { showMemory = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 214.dp),
        ) {
            if (memory.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.MenuBook, "角色长期记忆")
        }
    }

    if (showMemory && session != null && memoryScene != null && lifeScene != null) {
        NpcMemoryDialogV1(
            scene = memoryScene,
            npcNames = lifeScene.states.map { it.name }.distinct(),
            aiReady = aiReady,
            busy = memory.busy,
            error = memory.error,
            notice = memory.notice,
            onDismiss = { showMemory = false },
            onAuto = memoryVm::setAutoConsolidate,
            onRefresh = { memoryVm.reconcile(book, session, storyState.runtime, lifeScene, force = true) },
            onAddMemory = memoryVm::addMemory,
            onDeleteMemory = memoryVm::deleteMemory,
            onDeletePlan = memoryVm::deletePlan,
            onClearError = memoryVm::clearError,
            onClearNotice = memoryVm::clearNotice,
        )
    }
}

@Composable
private fun NpcMemoryDialogV1(
    scene: NpcMemorySceneV1,
    npcNames: List<String>,
    aiReady: Boolean,
    busy: Boolean,
    error: String?,
    notice: String?,
    onDismiss: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onAddMemory: (String, String, NpcMemoryPrivacyV1, Int) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
) {
    var selected by remember(scene.sessionId, npcNames) { mutableStateOf(npcNames.firstOrNull().orEmpty()) }
    var newMemory by remember(scene.sessionId) { mutableStateOf("") }
    var privacy by remember(scene.sessionId) { mutableStateOf(NpcMemoryPrivacyV1.PRIVATE) }
    var importance by remember(scene.sessionId) { mutableIntStateOf(3) }
    val slice = memorySliceForNpcV1(scene, selected)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("角色长期记忆 / 计划") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动沉淀", fontWeight = FontWeight.SemiBold)
                        Text("把重要经历沉淀为角色自己的长期记忆，并持续维护计划链。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(scene.autoConsolidate, onAuto, enabled = !busy)
                }
                FilledTonalButton(onRefresh, enabled = aiReady && !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("立即整理记忆与计划")
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
                if (npcNames.isEmpty()) {
                    Text("当前还没有可跟踪 NPC。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ScrollableTabRow(selectedTabIndex = npcNames.indexOf(selected).coerceAtLeast(0), edgePadding = 0.dp) {
                        npcNames.forEach { name ->
                            Tab(selected = selected == name, onClick = { selected = name }, text = { Text(name, maxLines = 1) })
                        }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text("长期记忆", fontWeight = FontWeight.Bold)
                            Text("私有记忆只属于 ${selected.ifBlank { "该角色" }}，不会作为其他 NPC 的可读知识。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (slice.memories.isEmpty()) {
                            item { Text("还没有长期记忆。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(slice.memories.sortedByDescending { it.createdAt }, key = { it.id }) { memory ->
                                Surface(shape = LanghuanShape.cover, tonalElevation = 1.dp) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${memory.privacy.label} · 重要度 ${memory.importance}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(memory.summary, style = MaterialTheme.typography.bodySmall)
                                            if (memory.evidence.isNotBlank()) Text("依据：${memory.evidence}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton({ onDeleteMemory(memory.id) }, enabled = !busy) { Icon(Icons.Rounded.Delete, "删除记忆") }
                                    }
                                }
                            }
                        }
                        item {
                            HorizontalDivider()
                            Text("计划链", fontWeight = FontWeight.Bold)
                        }
                        val plans = slice.plans.sortedWith(compareByDescending<NpcPlanV1> { it.status == NpcPlanStatusV1.ACTIVE }.thenByDescending { it.priority })
                        if (plans.isEmpty()) {
                            item { Text("暂无长期计划。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(plans, key = { it.id }) { plan ->
                                Surface(shape = LanghuanShape.cover, tonalElevation = 1.dp) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("${plan.status.label} · P${plan.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                            Text(plan.goal, fontWeight = FontWeight.SemiBold)
                                            if (plan.steps.isNotEmpty()) Text("下一步：${plan.steps.joinToString(" → ")}", style = MaterialTheme.typography.bodySmall)
                                            if (plan.privateReason.isNotBlank()) Text("私下原因：${plan.privateReason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton({ onDeletePlan(plan.id) }, enabled = !busy) { Icon(Icons.Rounded.Delete, "删除计划") }
                                    }
                                }
                            }
                        }
                        item {
                            HorizontalDivider()
                            Text("作者手动补记忆", fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = newMemory,
                                onValueChange = { newMemory = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("记忆内容") },
                                minLines = 2,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NpcMemoryPrivacyV1.values().forEach { item ->
                                    FilterChip(selected = privacy == item, onClick = { privacy = item }, label = { Text(item.label) })
                                }
                            }
                            Text("重要度：$importance", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = importance.toFloat(),
                                onValueChange = { importance = it.toInt().coerceIn(1, 5) },
                                valueRange = 1f..5f,
                                steps = 3,
                            )
                            Button(
                                onClick = {
                                    onAddMemory(selected, newMemory, privacy, importance)
                                    newMemory = ""
                                },
                                enabled = selected.isNotBlank() && newMemory.isNotBlank() && !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("加入长期记忆") }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss, enabled = !busy) { Text("完成") } },
    )
}

private val NPC_MEMORY_FIELDS_V1 = setOf("长期记忆:私有", "长期记忆:公开", "计划", "计划完成")
private const val MAX_MEMORY_NPCS_V1 = 18
private const val MAX_LONG_MEMORIES_PER_NPC_V1 = 18
private const val MAX_ACTIVE_PLANS_PER_NPC_V1 = 4
private const val MAX_PLAN_HISTORY_PER_NPC_V1 = 2
