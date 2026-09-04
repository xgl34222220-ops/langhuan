package com.xiguli.langhuan.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
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
enum class OffscreenEventVisibilityV1 {
    PRIVATE,
    SHARED,
    PUBLIC;

    val label: String get() = when (this) {
        PRIVATE -> "私有"
        SHARED -> "参与者共享"
        PUBLIC -> "公开"
    }
}

@Serializable
enum class OffscreenReturnCueV1 {
    STAY_AWAY,
    NEARBY,
    RETURN_NOW;

    val label: String get() = when (this) {
        STAY_AWAY -> "继续离场"
        NEARBY -> "正在靠近"
        RETURN_NOW -> "准备回场"
    }
}

@Serializable
data class NpcOffscreenActorV1(
    val name: String,
    val location: String = "",
    val activity: String = "",
    val emotion: String = "平静",
    val goal: String = "",
    val returnCue: OffscreenReturnCueV1 = OffscreenReturnCueV1.STAY_AWAY,
    val lastSimulatedBeat: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NpcOffscreenEventV1(
    val id: String = UUID.randomUUID().toString(),
    val owner: String,
    val participants: List<String> = emptyList(),
    val location: String = "",
    val summary: String,
    val result: String = "",
    val visibility: OffscreenEventVisibilityV1 = OffscreenEventVisibilityV1.PRIVATE,
    val evidence: String = "",
    val beat: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NpcOffscreenSceneV1(
    val sessionId: String,
    val actors: List<NpcOffscreenActorV1> = emptyList(),
    val events: List<NpcOffscreenEventV1> = emptyList(),
    val autoSimulate: Boolean = true,
    val maxActorsPerBeat: Int = 3,
    val lastInputKey: String = "",
    val beatCounter: Int = 0,
    val bridgedEventIds: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class NpcOffscreenArchiveV1(
    val novelId: String,
    val scenes: List<NpcOffscreenSceneV1> = emptyList(),
)

data class NpcOffscreenUiStateV1(
    val novelId: String = "",
    val scene: NpcOffscreenSceneV1? = null,
    val scenes: List<NpcOffscreenSceneV1> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val syncToken: Long = 0L,
)

internal data class NpcOffscreenChangeV1(
    val owner: String,
    val field: String,
    val value: String,
    val evidence: String = "",
)

class NpcOffscreenViewModelV1(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(NpcOffscreenUiStateV1())
    val state: StateFlow<NpcOffscreenUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession, lifeScene: NpcLifeSceneV1) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) return
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val seeded = existing ?: NpcOffscreenSceneV1(
            sessionId = session.id,
            actors = lifeScene.states
                .filter { it.name != session.playerProfile.name }
                .map { npc ->
                    NpcOffscreenActorV1(
                        name = npc.name,
                        emotion = npc.emotion,
                        goal = npc.currentGoal,
                        returnCue = when (npc.presence) {
                            NpcPresenceV1.PRESENT -> OffscreenReturnCueV1.STAY_AWAY
                            NpcPresenceV1.NEARBY -> OffscreenReturnCueV1.NEARBY
                            NpcPresenceV1.AWAY -> OffscreenReturnCueV1.STAY_AWAY
                        },
                    )
                },
        )
        val scenes = if (archive.scenes.any { it.sessionId == session.id }) archive.scenes else archive.scenes + seeded
        _state.value = NpcOffscreenUiStateV1(novelId = novelId, scene = seeded, scenes = scenes)
        saveArchive(NpcOffscreenArchiveV1(novelId, scenes))
    }

    fun setAutoSimulate(enabled: Boolean) = mutate("离场 NPC 自动生活已修改") { it.copy(autoSimulate = enabled) }

    fun setBudget(value: Int) = mutate("后台生活每拍人数已修改") {
        it.copy(maxActorsPerBeat = value.coerceIn(1, 4))
    }

    fun updateActor(actor: NpcOffscreenActorV1) = mutate("离场角色状态已保存") { scene ->
        val actors = scene.actors.map { if (it.name == actor.name) actor else it }
            .let { list -> if (list.any { it.name == actor.name }) list else list + actor }
        scene.copy(actors = actors.sortedBy { it.name })
    }

    fun markEventsBridged(ids: Collection<String>) {
        if (ids.isEmpty()) return
        mutate(null) { scene ->
            scene.copy(bridgedEventIds = (scene.bridgedEventIds + ids).distinct().takeLast(240))
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun simulate(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        lifeScene: NpcLifeSceneV1,
        memoryScene: NpcMemorySceneV1,
        force: Boolean = false,
    ) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy || scene.sessionId != session.id) return
        val selected = selectOffscreenNpcV1(
            lifeScene = lifeScene,
            memoryScene = memoryScene,
            playerName = session.playerProfile.name,
            maxActors = scene.maxActorsPerBeat,
        )
        if (selected.isEmpty()) return

        val world = runtime?.world ?: StoryWorldStateV3()
        val latestTurn = session.turns.lastOrNull()
        val inputKey = buildString {
            append(session.id).append('|')
            append(latestTurn?.id.orEmpty()).append('|')
            append(lifeScene.beatCounter).append('|')
            append(world.location).append('|').append(world.time).append('|').append(world.situation).append('|')
            append(selected.joinToString(",") { it.name })
        }
        if (!force && inputKey == scene.lastInputKey) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val gateway = activeGateway()
                var next = scene
                val allowedOwners = lifeScene.states.map { it.name }.filter { it != session.playerProfile.name }.toSet()
                val publicEvents = scene.events.filter { it.visibility == OffscreenEventVisibilityV1.PUBLIC }.takeLast(10)
                val nextBeat = scene.beatCounter + 1

                selected.forEach { lifeNpc ->
                    val owner = lifeNpc.name
                    val actor = next.actors.firstOrNull { it.name == owner }
                        ?: NpcOffscreenActorV1(name = owner, emotion = lifeNpc.emotion, goal = lifeNpc.currentGoal)
                    val slice = memorySliceForNpcV1(memoryScene, owner)
                    val activePlan = activePlanForNpcV1(memoryScene, owner)
                    val ownerMemories = slice.memories
                        .sortedWith(compareByDescending<NpcLongMemoryV1> { it.importance }.thenByDescending { it.lastUsedAt })
                        .take(12)
                        .joinToString("\n") { memory ->
                            "- ${memory.privacy.label}/重要度${memory.importance}：${memory.summary}"
                        }
                        .ifBlank { "无" }
                    val planText = activePlan?.let { plan ->
                        buildString {
                            append(plan.goal)
                            if (plan.steps.isNotEmpty()) append("｜步骤=").append(plan.steps.joinToString(" > "))
                            if (plan.privateReason.isNotBlank()) append("｜私下原因=").append(plan.privateReason)
                            append("｜优先级=").append(plan.priority)
                        }
                    } ?: "无活跃计划"
                    val others = next.actors
                        .filter { it.name != owner && it.name in allowedOwners }
                        .take(16)
                        .joinToString("\n") { other ->
                            "- ${other.name}｜地点=${other.location.ifBlank { "未知" }}｜公开活动=${other.activity.ifBlank { "未知" }}"
                        }
                        .ifBlank { "无" }
                    val publicText = publicEvents.joinToString("\n") { event ->
                        "- ${event.owner}｜${event.location.ifBlank { "地点未知" }}｜${event.summary}${event.result.takeIf { it.isNotBlank() }?.let { "；结果=$it" }.orEmpty()}"
                    }.ifBlank { "无" }

                    val output = gateway.generate(
                        PromptBundle(
                            system = """
                                你是琅嬛“离场 NPC 后台生活模拟器”。你每次只模拟一个 NPC，因此绝对不能读取其他 NPC 的私有记忆。
                                当前故事锚定原著第 ${session.anchorChapter} 章，只允许使用截至该章可见的信息、当前分支已经发生的事件、该 NPC 自己的长期记忆，以及明确公开的信息。严禁未来原著剧透。

                                规则：
                                1. 角色离开玩家镜头后仍会按自己的目标、地点、能力、时间推进，但不要强行制造大事件。
                                2. 不允许瞬移。若要回到玩家场景，必须考虑地点与时间；无法立刻抵达时只能标记“附近”。
                                3. 可让离场 NPC 相遇，但“参与者共享事件”只能写双方实际能观察到的内容，不能夹带任何人的隐藏意图或私有记忆。
                                4. 离场 NPC 不能知道玩家现场刚发生但没有传播给他的事情。
                                5. 不创建允许名单之外的具名原著角色。
                                6. 私有事件不会进入共享世界；公开事件才可以成为世界层已发生信息。

                                必须返回 GeneratedChapter JSON：title="npc-offscreen"；content="ok"；summary=一句本次后台推进说明；touchedForeshadowingIds=[]。
                                stateChanges 的 subject 必须固定为 "OFF:$owner"，field 只能是：
                                - “地点”：after=角色当前真实地点
                                - “行动”：after=当前正在做什么
                                - “情绪”：after=当前情绪
                                - “目标”：after=当前阶段目标
                                - “回场”：after 只能是“离场 / 附近 / 回场”
                                - “事件:私有”：after="摘要||结果"
                                - “事件:共享”：after="参与角色1,参与角色2||摘要||结果"；参与角色必须来自允许名单
                                - “事件:公开”：after="摘要||结果"
                                没有合理事件时，只更新必要状态，不要硬编事件。
                            """.trimIndent(),
                            user = """
                                作品：《${book.title}》｜分支：${session.title}
                                锚点：第${session.anchorChapter}章 ${session.anchorTitle}
                                玩家所在场景：地点=${world.location.ifBlank { "未记录" }}；时间=${world.time.ifBlank { "未记录" }}；局势=${world.situation.ifBlank { "未记录" }}
                                后台生活拍：$nextBeat

                                【本次只模拟】
                                $owner
                                当前离场状态：地点=${actor.location.ifBlank { "未知" }}；活动=${actor.activity.ifBlank { "无记录" }}；情绪=${actor.emotion}；目标=${actor.goal.ifBlank { lifeNpc.currentGoal.ifBlank { "未定" } }}；回场倾向=${actor.returnCue.label}

                                【$owner 自己的长期记忆】
                                $ownerMemories

                                【$owner 自己的计划】
                                $planText

                                【其他角色公开位置/活动，不含其私有记忆】
                                $others

                                【当前分支公开后台事件】
                                $publicText

                                【允许出现的 NPC 名单】
                                ${allowedOwners.joinToString("、")}
                            """.trimIndent(),
                        )
                    )
                    val changes = output.stateChanges.mapNotNull { change ->
                        if (change.subject.trim() != "OFF:$owner") return@mapNotNull null
                        val field = change.field.trim()
                        if (field !in OFFSCREEN_FIELDS_V1) return@mapNotNull null
                        val value = change.after.trim()
                        if (value.isBlank()) return@mapNotNull null
                        NpcOffscreenChangeV1(owner, field, value, change.evidence.trim())
                    }
                    next = applyOffscreenChangesV1(
                        scene = next,
                        owner = owner,
                        changes = changes,
                        allowedOwners = allowedOwners,
                        beat = nextBeat,
                    )
                }

                next.copy(
                    lastInputKey = inputKey,
                    beatCounter = nextBeat,
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                persistScene(updated, "离场 NPC 的后台生活已推进")
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "后台生活模拟失败") }
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

    private fun mutate(message: String?, transform: (NpcOffscreenSceneV1) -> NpcOffscreenSceneV1) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy) return
        persistScene(transform(scene).copy(updatedAt = System.currentTimeMillis()), message)
    }

    private fun persistScene(updated: NpcOffscreenSceneV1, notice: String? = null) {
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
        if (current.novelId.isNotBlank()) saveArchive(NpcOffscreenArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_npc_offscreen_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): NpcOffscreenArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return NpcOffscreenArchiveV1(novelId)
        return runCatching { json.decodeFromString(NpcOffscreenArchiveV1.serializer(), file.readText()) }
            .getOrElse { NpcOffscreenArchiveV1(novelId) }
    }

    private fun saveArchive(archive: NpcOffscreenArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(NpcOffscreenArchiveV1.serializer(), archive)) }
    }
}

internal fun selectOffscreenNpcV1(
    lifeScene: NpcLifeSceneV1,
    memoryScene: NpcMemorySceneV1,
    playerName: String,
    maxActors: Int,
): List<NpcLifeStateV1> = lifeScene.states
    .asSequence()
    .filter { it.name != playerName && it.presence != NpcPresenceV1.PRESENT }
    .sortedWith(
        compareByDescending<NpcLifeStateV1> { if (it.presence == NpcPresenceV1.NEARBY) 1 else 0 }
            .thenByDescending { activePlanForNpcV1(memoryScene, it.name)?.priority ?: 0 }
            .thenBy { it.name },
    )
    .take(maxActors.coerceIn(1, 4))
    .toList()

internal fun applyOffscreenChangesV1(
    scene: NpcOffscreenSceneV1,
    owner: String,
    changes: List<NpcOffscreenChangeV1>,
    allowedOwners: Set<String>,
    beat: Int,
): NpcOffscreenSceneV1 {
    if (owner !in allowedOwners) return scene
    var actor = scene.actors.firstOrNull { it.name == owner } ?: NpcOffscreenActorV1(name = owner)
    val events = scene.events.toMutableList()
    val now = System.currentTimeMillis()

    changes.forEach { change ->
        if (change.owner != owner) return@forEach
        when (change.field) {
            "地点" -> actor = actor.copy(location = change.value.take(220))
            "行动" -> actor = actor.copy(activity = change.value.take(320))
            "情绪" -> actor = actor.copy(emotion = change.value.take(120))
            "目标" -> actor = actor.copy(goal = change.value.take(300))
            "回场" -> actor = actor.copy(returnCue = parseOffscreenReturnCueV1(change.value))
            "事件:私有" -> parseOffscreenEventPayloadV1(change.value)?.let { payload ->
                events += NpcOffscreenEventV1(
                    owner = owner,
                    location = actor.location,
                    summary = payload.first,
                    result = payload.second,
                    visibility = OffscreenEventVisibilityV1.PRIVATE,
                    evidence = change.evidence.take(320),
                    beat = beat,
                    createdAt = now,
                )
            }
            "事件:公开" -> parseOffscreenEventPayloadV1(change.value)?.let { payload ->
                events += NpcOffscreenEventV1(
                    owner = owner,
                    location = actor.location,
                    summary = payload.first,
                    result = payload.second,
                    visibility = OffscreenEventVisibilityV1.PUBLIC,
                    evidence = change.evidence.take(320),
                    beat = beat,
                    createdAt = now,
                )
            }
            "事件:共享" -> parseSharedOffscreenEventPayloadV1(change.value, owner, allowedOwners)?.let { payload ->
                events += NpcOffscreenEventV1(
                    owner = owner,
                    participants = payload.first,
                    location = actor.location,
                    summary = payload.second,
                    result = payload.third,
                    visibility = OffscreenEventVisibilityV1.SHARED,
                    evidence = change.evidence.take(320),
                    beat = beat,
                    createdAt = now,
                )
            }
        }
    }

    actor = actor.copy(lastSimulatedBeat = beat, updatedAt = now)
    val actors = scene.actors.map { if (it.name == owner) actor else it }
        .let { list -> if (list.any { it.name == owner }) list else list + actor }
    return scene.copy(
        actors = actors.sortedBy { it.name },
        events = compactOffscreenEventsV1(events),
    )
}

private fun parseOffscreenEventPayloadV1(value: String): Pair<String, String>? {
    val parts = value.split("||", limit = 2)
    val summary = parts.getOrNull(0)?.trim().orEmpty()
    if (summary.isBlank()) return null
    return summary.take(420) to parts.getOrNull(1)?.trim().orEmpty().take(420)
}

private data class SharedOffscreenPayloadV1(
    val first: List<String>,
    val second: String,
    val third: String,
)

private fun parseSharedOffscreenEventPayloadV1(
    value: String,
    owner: String,
    allowedOwners: Set<String>,
): SharedOffscreenPayloadV1? {
    val parts = value.split("||", limit = 3)
    val participants = parts.getOrNull(0).orEmpty()
        .split(',', '，', '、')
        .map { it.trim() }
        .filter { it.isNotBlank() && it in allowedOwners }
        .plus(owner)
        .distinct()
        .take(6)
    val summary = parts.getOrNull(1)?.trim().orEmpty()
    if (summary.isBlank()) return null
    return SharedOffscreenPayloadV1(
        first = participants,
        second = summary.take(420),
        third = parts.getOrNull(2)?.trim().orEmpty().take(420),
    )
}

private fun parseOffscreenReturnCueV1(value: String): OffscreenReturnCueV1 = when {
    value.contains("回场") || value.contains("返回") -> OffscreenReturnCueV1.RETURN_NOW
    value.contains("附近") || value.contains("靠近") -> OffscreenReturnCueV1.NEARBY
    else -> OffscreenReturnCueV1.STAY_AWAY
}

internal fun desiredPresenceAfterOffscreenV1(
    previous: NpcPresenceV1,
    actor: NpcOffscreenActorV1,
    playerLocation: String,
): NpcPresenceV1 {
    if (previous == NpcPresenceV1.PRESENT) return NpcPresenceV1.PRESENT
    return when (actor.returnCue) {
        OffscreenReturnCueV1.STAY_AWAY -> NpcPresenceV1.AWAY
        OffscreenReturnCueV1.NEARBY -> NpcPresenceV1.NEARBY
        OffscreenReturnCueV1.RETURN_NOW -> {
            val actorLoc = normalizeOffscreenLocationV1(actor.location)
            val playerLoc = normalizeOffscreenLocationV1(playerLocation)
            if (actorLoc.isNotBlank() && playerLoc.isNotBlank() && actorLoc == playerLoc) NpcPresenceV1.PRESENT
            else NpcPresenceV1.NEARBY
        }
    }
}

internal fun memoriesFromOffscreenEventV1(event: NpcOffscreenEventV1): List<Triple<String, String, NpcMemoryPrivacyV1>> {
    val text = buildString {
        append(event.summary)
        if (event.result.isNotBlank()) append("；").append(event.result)
        if (event.location.isNotBlank()) append("（地点：").append(event.location).append("）")
    }
    val owners = when (event.visibility) {
        OffscreenEventVisibilityV1.PRIVATE -> listOf(event.owner)
        OffscreenEventVisibilityV1.SHARED, OffscreenEventVisibilityV1.PUBLIC -> (event.participants + event.owner).distinct()
    }
    val privacy = if (event.visibility == OffscreenEventVisibilityV1.PUBLIC) NpcMemoryPrivacyV1.PUBLIC else NpcMemoryPrivacyV1.PRIVATE
    return owners.map { Triple(it, text, privacy) }
}

internal fun renderOffscreenPublicNoteV1(scene: NpcOffscreenSceneV1): String {
    val publicEvents = scene.events.filter { it.visibility == OffscreenEventVisibilityV1.PUBLIC }.takeLast(8)
    if (publicEvents.isEmpty()) return ""
    return buildString {
        append(OFFSCREEN_PUBLIC_START_V1).append('\n')
        publicEvents.forEach { event ->
            append("- ").append(event.owner)
            if (event.location.isNotBlank()) append(" @ ").append(event.location)
            append("：").append(event.summary)
            if (event.result.isNotBlank()) append("；").append(event.result)
            append('\n')
        }
        append(OFFSCREEN_PUBLIC_END_V1)
    }
}

internal fun mergeOffscreenPublicNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(OFFSCREEN_PUBLIC_START_V1) + ".*?" + Regex.escape(OFFSCREEN_PUBLIC_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    if (block.isBlank()) return stripped
    return listOf(stripped, block).filter { it.isNotBlank() }.joinToString("\n\n")
}

private fun compactOffscreenEventsV1(events: List<NpcOffscreenEventV1>): List<NpcOffscreenEventV1> = events
    .distinctBy { event ->
        listOf(event.owner, event.visibility.name, normalizeOffscreenTextV1(event.summary), normalizeOffscreenTextV1(event.result), event.beat).joinToString("|")
    }
    .sortedBy { it.createdAt }
    .takeLast(MAX_OFFSCREEN_EVENTS_V1)

private fun normalizeOffscreenLocationV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s，。！？、,.!?：:；;‘’“”\"'（）()【】\\[\\]-]+"), "")
    .take(160)

private fun normalizeOffscreenTextV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s，。！？、,.!?：:；;‘’“”\"'（）()【】\\[\\]]+"), "")
    .take(220)

@Composable
fun StoryPlayPanelV10(
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
    val offVm: NpcOffscreenViewModelV1 = viewModel()
    val off by offVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val lifeScene = life.scene
    val memoryScene = memory.scene
    val offScene = off.scene
    var showOffscreen by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id, lifeScene?.sessionId) {
        val active = session ?: return@LaunchedEffect
        val lifeState = lifeScene ?: return@LaunchedEffect
        offVm.open(book.id, active, lifeState)
    }

    val latestTurn = session?.turns?.lastOrNull()
    LaunchedEffect(
        aiReady,
        session?.id,
        latestTurn?.id,
        lifeScene?.beatCounter,
        lifeScene?.updatedAt,
        memoryScene?.revision,
        offScene?.autoSimulate,
        off.busy,
        life.busy,
        memory.busy,
        storyState.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val lifeState = lifeScene ?: return@LaunchedEffect
        val memoryState = memoryScene ?: return@LaunchedEffect
        val offState = offScene ?: return@LaunchedEffect
        if (aiReady && offState.autoSimulate && !off.busy && !life.busy && !memory.busy && !storyState.busy) {
            offVm.simulate(book, active, storyState.runtime, lifeState, memoryState)
        }
    }

    LaunchedEffect(off.syncToken, memory.busy, life.busy, storyState.busy) {
        val offState = offScene ?: return@LaunchedEffect
        val lifeState = lifeScene ?: return@LaunchedEffect
        val runtime = storyState.runtime
        if (memory.busy || life.busy || storyState.busy) return@LaunchedEffect

        val newEvents = offState.events.filter { it.id !in offState.bridgedEventIds }
        newEvents.forEach { event ->
            memoriesFromOffscreenEventV1(event).forEach { (owner, text, privacy) ->
                if (owner in lifeState.states.map { it.name }) {
                    memoryVm.addMemory(owner, text, privacy, importance = if (event.visibility == OffscreenEventVisibilityV1.PRIVATE) 3 else 4)
                }
            }
        }

        offState.actors.forEach { actor ->
            val old = lifeState.states.firstOrNull { it.name == actor.name } ?: return@forEach
            if (old.presence == NpcPresenceV1.PRESENT && actor.returnCue == OffscreenReturnCueV1.STAY_AWAY) return@forEach
            val desiredPresence = desiredPresenceAfterOffscreenV1(old.presence, actor, runtime?.world?.location.orEmpty())
            val next = old.copy(
                presence = desiredPresence,
                emotion = actor.emotion.ifBlank { old.emotion },
                currentGoal = actor.goal.ifBlank { old.currentGoal },
                shortMemory = (old.shortMemory + offState.events.filter { it.owner == actor.name }.takeLast(2).map { it.summary })
                    .distinct()
                    .takeLast(6),
            )
            if (next != old) lifeVm.updateNpc(next)
        }

        val publicBlock = renderOffscreenPublicNoteV1(offState)
        val world = runtime?.world
        if (world != null) {
            val mergedNotes = mergeOffscreenPublicNoteV1(world.notes, publicBlock)
            if (mergedNotes != world.notes) storyVm.updateWorld(world.copy(notes = mergedNotes))
        }
        offVm.markEventsBridged(newEvents.map { it.id })
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV9(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )

        SmallFloatingActionButton(
            onClick = { showOffscreen = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 274.dp),
        ) {
            if (off.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.History, "离场角色后台生活")
        }
    }

    if (showOffscreen && session != null && lifeScene != null && memoryScene != null && offScene != null) {
        NpcOffscreenDialogV1(
            scene = offScene,
            lifeScene = lifeScene,
            busy = off.busy,
            error = off.error,
            notice = off.notice,
            onDismiss = { showOffscreen = false },
            onAuto = offVm::setAutoSimulate,
            onBudget = offVm::setBudget,
            onRefresh = { offVm.simulate(book, session, storyState.runtime, lifeScene, memoryScene, force = true) },
        )
    }
}

@Composable
private fun NpcOffscreenDialogV1(
    scene: NpcOffscreenSceneV1,
    lifeScene: NpcLifeSceneV1,
    busy: Boolean,
    error: String?,
    notice: String?,
    onDismiss: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onBudget: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null)
                Spacer(Modifier.width(8.dp))
                Text("离场角色 · 后台生活")
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动推进", fontWeight = FontWeight.Bold)
                        Text("角色离开镜头后仍按自己的计划继续生活。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = scene.autoSimulate, onCheckedChange = onAuto)
                }
                Spacer(Modifier.height(8.dp))
                Text("每个后台拍最多模拟 ${scene.maxActorsPerBeat} 人", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = scene.maxActorsPerBeat.toFloat(),
                    onValueChange = { onBudget(it.toInt()) },
                    valueRange = 1f..4f,
                    steps = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onRefresh, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("推进一拍")
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("后台拍 ${scene.beatCounter}") },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                    )
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (!notice.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                val awayNames = lifeScene.states.filter { it.presence != NpcPresenceV1.PRESENT }.map { it.name }.toSet()
                val actors = scene.actors.filter { it.name in awayNames }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (actors.isEmpty()) {
                        item { Text("当前没有离场或附近的 NPC。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(actors, key = { it.name }) { actor ->
                        val recent = scene.events.filter { it.owner == actor.name || actor.name in it.participants }.takeLast(2)
                        Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Person, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(actor.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(actor.returnCue.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("地点：${actor.location.ifBlank { "未知" }}", style = MaterialTheme.typography.bodySmall)
                                Text("正在做：${actor.activity.ifBlank { "暂无记录" }}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("目标：${actor.goal.ifBlank { "未定" }} · 情绪：${actor.emotion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                recent.forEach { event ->
                                    Spacer(Modifier.height(5.dp))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            if (event.visibility == OffscreenEventVisibilityV1.PUBLIC) Icons.Rounded.Visibility else Icons.Rounded.Lock,
                                            null,
                                            Modifier.size(16.dp).padding(top = 2.dp),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "${event.visibility.label}：${event.summary}${event.result.takeIf { it.isNotBlank() }?.let { "；$it" }.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
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
}

private val OFFSCREEN_FIELDS_V1 = setOf("地点", "行动", "情绪", "目标", "回场", "事件:私有", "事件:共享", "事件:公开")
private const val MAX_OFFSCREEN_EVENTS_V1 = 160
private const val OFFSCREEN_PUBLIC_START_V1 = "【离场NPC公开动态｜导演层】"
private const val OFFSCREEN_PUBLIC_END_V1 = "【/离场NPC公开动态】"
