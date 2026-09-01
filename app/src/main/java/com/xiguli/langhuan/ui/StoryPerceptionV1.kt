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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StoryLightLevelV1 {
    BRIGHT, NORMAL, DIM, DARK;

    val label: String get() = when (this) {
        BRIGHT -> "明亮"
        NORMAL -> "正常"
        DIM -> "昏暗"
        DARK -> "黑暗"
    }
}

@Serializable
enum class StoryObscurityV1 {
    CLEAR, LIGHT, HEAVY;

    val label: String get() = when (this) {
        CLEAR -> "清晰"
        LIGHT -> "轻雾/轻烟"
        HEAVY -> "浓雾/浓烟"
    }
}

@Serializable
enum class StoryPortalStateV1 {
    OPEN, CLOSED, SEALED;

    val label: String get() = when (this) {
        OPEN -> "开放"
        CLOSED -> "关闭"
        SEALED -> "封闭"
    }
}

@Serializable
enum class StoryAudibilityV1 {
    CLEAR, MUFFLED, NONE;

    val label: String get() = when (this) {
        CLEAR -> "清楚听见"
        MUFFLED -> "只能听见模糊动静"
        NONE -> "听不见"
    }
}

@Serializable
data class StoryPerceptionPlaceV1(
    val placeId: String,
    val light: StoryLightLevelV1 = StoryLightLevelV1.NORMAL,
    val obscurity: StoryObscurityV1 = StoryObscurityV1.CLEAR,
    val ambientNoise: Int = 15,
    val manual: Boolean = false,
)

@Serializable
data class StoryPerceptionRouteV1(
    val routeId: String,
    val portalState: StoryPortalStateV1 = StoryPortalStateV1.OPEN,
    val visionPassWhenOpen: Boolean = true,
    val soundLossOpen: Int = 18,
    val soundLossClosed: Int = 48,
    val manual: Boolean = false,
)

@Serializable
data class StoryPerceptionWitnessV1(
    val turnId: String,
    val actor: String,
    val observerPlaceId: String,
    val subjectPlaceId: String,
    val canSee: Boolean,
    val audibility: StoryAudibilityV1,
    val reason: String,
)

@Serializable
data class StoryPerceptionSceneV1(
    val sessionId: String,
    val placeStates: List<StoryPerceptionPlaceV1> = emptyList(),
    val routeStates: List<StoryPerceptionRouteV1> = emptyList(),
    val lastObservedTurnId: String = "",
    val witnesses: List<StoryPerceptionWitnessV1> = emptyList(),
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StoryPerceptionArchiveV1(
    val novelId: String,
    val scenes: List<StoryPerceptionSceneV1> = emptyList(),
)

data class StoryPerceptionUiStateV1(
    val novelId: String = "",
    val scene: StoryPerceptionSceneV1? = null,
    val scenes: List<StoryPerceptionSceneV1> = emptyList(),
    val notice: String? = null,
)

data class StorySenseResultV1(
    val canSee: Boolean,
    val audibility: StoryAudibilityV1,
    val reason: String,
)

class StoryPerceptionViewModelV1(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryPerceptionUiStateV1())
    val state: StateFlow<StoryPerceptionUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession, spatial: StorySpatialSceneV1?, atmosphere: String) {
        if (novelId.isBlank() || spatial == null) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) {
            syncSpatial(spatial, atmosphere)
            return
        }
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
            ?: StoryPerceptionSceneV1(sessionId = session.id)
        val seeded = normalizePerceptionSceneV1(existing, spatial, atmosphere)
        val scenes = archive.scenes.map { if (it.sessionId == session.id) seeded else it }
            .let { list -> if (list.any { it.sessionId == session.id }) list else list + seeded }
        _state.value = StoryPerceptionUiStateV1(novelId = novelId, scene = seeded, scenes = scenes)
        saveArchive(StoryPerceptionArchiveV1(novelId, scenes))
    }

    fun syncSpatial(spatial: StorySpatialSceneV1, atmosphere: String) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != spatial.sessionId) return
        val updated = normalizePerceptionSceneV1(scene, spatial, atmosphere)
        if (updated != scene) persistScene(updated.copy(revision = scene.revision + 1), null)
    }

    fun setEnvironment(placeId: String, light: StoryLightLevelV1, obscurity: StoryObscurityV1, ambientNoise: Int) = mutate("场景视听环境已保存") { scene ->
        val item = StoryPerceptionPlaceV1(
            placeId = placeId,
            light = light,
            obscurity = obscurity,
            ambientNoise = ambientNoise.coerceIn(0, 100),
            manual = true,
        )
        scene.copy(placeStates = scene.placeStates.filterNot { it.placeId == placeId } + item)
    }

    fun resetEnvironment(placeId: String, spatial: StorySpatialSceneV1, atmosphere: String) = mutate("场景环境已恢复自动判断") { scene ->
        val clean = scene.copy(placeStates = scene.placeStates.filterNot { it.placeId == placeId })
        normalizePerceptionSceneV1(clean, spatial, atmosphere)
    }

    fun setPortal(routeId: String, state: StoryPortalStateV1, visionPassWhenOpen: Boolean) = mutate("通道视听规则已保存") { scene ->
        val old = scene.routeStates.firstOrNull { it.routeId == routeId }
        val item = (old ?: StoryPerceptionRouteV1(routeId)).copy(
            portalState = state,
            visionPassWhenOpen = visionPassWhenOpen,
            manual = true,
        )
        scene.copy(routeStates = scene.routeStates.filterNot { it.routeId == routeId } + item)
    }

    fun resetPortal(routeId: String, spatial: StorySpatialSceneV1) = mutate("通道规则已恢复自动判断") { scene ->
        val route = spatial.routes.firstOrNull { it.id == routeId } ?: return@mutate scene
        val default = defaultPerceptionRouteV1(spatial, route)
        scene.copy(routeStates = scene.routeStates.filterNot { it.routeId == routeId } + default)
    }

    fun observeTurn(session: StoryPlaySession, spatial: StorySpatialSceneV1) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val turn = session.turns.lastOrNull() ?: return
        if (turn.id == scene.lastObservedTurnId) return
        val playerPlaceId = spatial.playerPlaceId
        val playerName = session.playerProfile.name.trim()
        val witnesses = spatial.actorLocations
            .filter { it.placeId.isNotBlank() && it.actor != STORY_PERCEPTION_PLAYER_ACTOR_V1 && (playerName.isBlank() || it.actor != playerName) }
            .map { actor ->
                val sense = storySenseBetweenPlacesV1(spatial, scene, actor.placeId, playerPlaceId)
                StoryPerceptionWitnessV1(
                    turnId = turn.id,
                    actor = actor.actor,
                    observerPlaceId = actor.placeId,
                    subjectPlaceId = playerPlaceId,
                    canSee = sense.canSee,
                    audibility = sense.audibility,
                    reason = sense.reason,
                )
            }
        persistScene(
            scene.copy(
                lastObservedTurnId = turn.id,
                witnesses = (scene.witnesses + witnesses).takeLast(320),
                revision = scene.revision + 1,
            ),
            "本轮角色视听范围已核验",
        )
    }

    private fun mutate(message: String?, transform: (StoryPerceptionSceneV1) -> StoryPerceptionSceneV1) {
        val scene = _state.value.scene ?: return
        persistScene(transform(scene).copy(revision = scene.revision + 1), message)
    }

    private fun persistScene(updated: StoryPerceptionSceneV1, notice: String?) {
        val current = _state.value
        val normalized = updated.copy(updatedAt = System.currentTimeMillis())
        val scenes = current.scenes.map { if (it.sessionId == normalized.sessionId) normalized else it }
            .let { list -> if (list.any { it.sessionId == normalized.sessionId }) list else list + normalized }
        _state.update { it.copy(scene = normalized, scenes = scenes, notice = notice) }
        if (current.novelId.isNotBlank()) saveArchive(StoryPerceptionArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_perception_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StoryPerceptionArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryPerceptionArchiveV1(novelId)
        return runCatching { json.decodeFromString(StoryPerceptionArchiveV1.serializer(), file.readText()) }
            .getOrElse { StoryPerceptionArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StoryPerceptionArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StoryPerceptionArchiveV1.serializer(), archive)) }
    }
}

internal fun normalizePerceptionSceneV1(
    scene: StoryPerceptionSceneV1,
    spatial: StorySpatialSceneV1,
    atmosphere: String,
): StoryPerceptionSceneV1 {
    val validPlaceIds = spatial.places.map { it.id }.toSet()
    val validRouteIds = spatial.routes.map { it.id }.toSet()
    val existingPlaces = scene.placeStates.filter { it.placeId in validPlaceIds }.associateBy { it.placeId }
    val placeStates = spatial.places.map { place ->
        existingPlaces[place.id] ?: defaultPerceptionPlaceV1(place)
    }.toMutableList()

    val playerIndex = placeStates.indexOfFirst { it.placeId == spatial.playerPlaceId }
    if (playerIndex >= 0 && !placeStates[playerIndex].manual) {
        placeStates[playerIndex] = applyAtmosphereToPerceptionV1(placeStates[playerIndex], atmosphere)
    }

    val existingRoutes = scene.routeStates.filter { it.routeId in validRouteIds }.associateBy { it.routeId }
    val routeStates = spatial.routes.map { route -> existingRoutes[route.id] ?: defaultPerceptionRouteV1(spatial, route) }
    return scene.copy(placeStates = placeStates, routeStates = routeStates)
}

internal fun defaultPerceptionPlaceV1(place: StoryPlaceV1): StoryPerceptionPlaceV1 = StoryPerceptionPlaceV1(
    placeId = place.id,
    light = StoryLightLevelV1.NORMAL,
    obscurity = StoryObscurityV1.CLEAR,
    ambientNoise = when (place.kind) {
        StoryPlaceKindV1.CITY, StoryPlaceKindV1.AREA -> 28
        StoryPlaceKindV1.BUILDING -> 20
        StoryPlaceKindV1.ROOM -> 12
        else -> 18
    },
)

internal fun defaultPerceptionRouteV1(spatial: StorySpatialSceneV1, route: StoryRouteEdgeV1): StoryPerceptionRouteV1 {
    val from = spatial.places.firstOrNull { it.id == route.fromId }
    val to = spatial.places.firstOrNull { it.id == route.toId }
    val roomBoundary = from?.kind == StoryPlaceKindV1.ROOM || to?.kind == StoryPlaceKindV1.ROOM
    val parentChild = from?.parentId == to?.id || to?.parentId == from?.id
    val longDistance = route.minutes > 5
    return when {
        longDistance -> StoryPerceptionRouteV1(route.id, StoryPortalStateV1.OPEN, false, 100, 100)
        roomBoundary -> StoryPerceptionRouteV1(route.id, StoryPortalStateV1.CLOSED, false, 20, 48)
        parentChild -> StoryPerceptionRouteV1(route.id, StoryPortalStateV1.OPEN, true, 16, 44)
        else -> StoryPerceptionRouteV1(route.id, StoryPortalStateV1.OPEN, route.minutes <= 2, 24, 52)
    }
}

internal fun applyAtmosphereToPerceptionV1(state: StoryPerceptionPlaceV1, atmosphere: String): StoryPerceptionPlaceV1 {
    val raw = atmosphere.lowercase()
    val light = when {
        listOf("漆黑", "黑暗", "无灯", "熄灯", "伸手不见五指").any(raw::contains) -> StoryLightLevelV1.DARK
        listOf("昏暗", "微光", "暗淡", "月光").any(raw::contains) -> StoryLightLevelV1.DIM
        listOf("强光", "明亮", "灯火通明", "日光").any(raw::contains) -> StoryLightLevelV1.BRIGHT
        else -> state.light
    }
    val obscurity = when {
        listOf("浓雾", "浓烟", "烟尘弥漫", "看不清").any(raw::contains) -> StoryObscurityV1.HEAVY
        listOf("薄雾", "轻雾", "烟雾", "烟尘").any(raw::contains) -> StoryObscurityV1.LIGHT
        else -> state.obscurity
    }
    val noise = when {
        listOf("震耳", "巨响", "喧闹", "人声鼎沸").any(raw::contains) -> maxOf(state.ambientNoise, 75)
        listOf("嘈杂", "吵闹", "雨声很大").any(raw::contains) -> maxOf(state.ambientNoise, 55)
        listOf("寂静", "安静", "鸦雀无声").any(raw::contains) -> minOf(state.ambientNoise, 8)
        else -> state.ambientNoise
    }
    return state.copy(light = light, obscurity = obscurity, ambientNoise = noise)
}

internal fun storySenseBetweenPlacesV1(
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1,
    observerPlaceId: String,
    subjectPlaceId: String,
): StorySenseResultV1 {
    if (observerPlaceId.isBlank() || subjectPlaceId.isBlank()) {
        return StorySenseResultV1(false, StoryAudibilityV1.NONE, "至少一方位置未知")
    }
    val observerState = perception.placeStates.firstOrNull { it.placeId == observerPlaceId } ?: StoryPerceptionPlaceV1(observerPlaceId)
    val subjectState = perception.placeStates.firstOrNull { it.placeId == subjectPlaceId } ?: StoryPerceptionPlaceV1(subjectPlaceId)
    if (observerPlaceId == subjectPlaceId) {
        val visible = observerState.light != StoryLightLevelV1.DARK && observerState.obscurity != StoryObscurityV1.HEAVY
        val audibility = if (observerState.ambientNoise >= 85) StoryAudibilityV1.MUFFLED else StoryAudibilityV1.CLEAR
        return StorySenseResultV1(
            canSee = visible,
            audibility = audibility,
            reason = buildString {
                append("同一地点")
                if (!visible) append("，但光线/遮蔽阻断清晰视觉")
                if (audibility == StoryAudibilityV1.MUFFLED) append("，环境噪声过大")
            },
        )
    }

    val canSee = storyCanSeeAcrossRoutesV1(spatial, perception, observerPlaceId, subjectPlaceId, observerState, subjectState)
    val audibility = storyAudibilityAcrossRoutesV1(spatial, perception, observerPlaceId, subjectPlaceId, observerState, subjectState)
    return StorySenseResultV1(
        canSee = canSee,
        audibility = audibility,
        reason = when {
            canSee && audibility == StoryAudibilityV1.CLEAR -> "视线连通且声音清晰可达"
            canSee -> "视线连通，但声音受环境/通道削弱"
            audibility == StoryAudibilityV1.CLEAR -> "看不见，但声音清晰可达"
            audibility == StoryAudibilityV1.MUFFLED -> "看不见，只能听见模糊动静"
            else -> "视线与声音均不能直接到达"
        },
    )
}

private fun storyCanSeeAcrossRoutesV1(
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1,
    fromId: String,
    toId: String,
    observer: StoryPerceptionPlaceV1,
    subject: StoryPerceptionPlaceV1,
): Boolean {
    if (observer.light == StoryLightLevelV1.DARK || subject.light == StoryLightLevelV1.DARK) return false
    if (observer.obscurity == StoryObscurityV1.HEAVY || subject.obscurity == StoryObscurityV1.HEAVY) return false
    data class Node(val id: String, val hops: Int)
    val queue = ArrayDeque<Node>()
    val seen = mutableSetOf(fromId)
    queue += Node(fromId, 0)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (node.hops >= 2) continue
        spatial.routes.forEach { route ->
            val next = when {
                route.fromId == node.id -> route.toId
                route.bidirectional && route.toId == node.id -> route.fromId
                else -> return@forEach
            }
            if (next in seen) return@forEach
            val routeState = perception.routeStates.firstOrNull { it.routeId == route.id } ?: defaultPerceptionRouteV1(spatial, route)
            if (route.minutes > 3 || routeState.portalState != StoryPortalStateV1.OPEN || !routeState.visionPassWhenOpen) return@forEach
            val nextState = perception.placeStates.firstOrNull { it.placeId == next } ?: StoryPerceptionPlaceV1(next)
            if (nextState.light == StoryLightLevelV1.DARK || nextState.obscurity == StoryObscurityV1.HEAVY) return@forEach
            if (next == toId) return true
            seen += next
            queue += Node(next, node.hops + 1)
        }
    }
    return false
}

private fun storyAudibilityAcrossRoutesV1(
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1,
    fromId: String,
    toId: String,
    observer: StoryPerceptionPlaceV1,
    subject: StoryPerceptionPlaceV1,
): StoryAudibilityV1 {
    data class Node(val id: String, val loss: Int, val hops: Int)
    val best = mutableMapOf(fromId to 0)
    val queue = java.util.PriorityQueue<Node>(compareBy { it.loss })
    queue += Node(fromId, 0, 0)
    while (queue.isNotEmpty()) {
        val node = queue.poll()
        if (node.loss != best[node.id] || node.hops >= 3) continue
        if (node.id == toId) break
        spatial.routes.forEach { route ->
            val next = when {
                route.fromId == node.id -> route.toId
                route.bidirectional && route.toId == node.id -> route.fromId
                else -> return@forEach
            }
            val routeState = perception.routeStates.firstOrNull { it.routeId == route.id } ?: defaultPerceptionRouteV1(spatial, route)
            val edgeLoss = when (routeState.portalState) {
                StoryPortalStateV1.OPEN -> routeState.soundLossOpen
                StoryPortalStateV1.CLOSED -> routeState.soundLossClosed
                StoryPortalStateV1.SEALED -> 10_000
            }
            if (edgeLoss >= 1000) return@forEach
            val nextLoss = node.loss + edgeLoss.coerceIn(0, 200)
            if (nextLoss < (best[next] ?: Int.MAX_VALUE)) {
                best[next] = nextLoss
                queue += Node(next, nextLoss, node.hops + 1)
            }
        }
    }
    val pathLoss = best[toId] ?: return StoryAudibilityV1.NONE
    val totalLoss = pathLoss + observer.ambientNoise / 2 + subject.ambientNoise / 4
    return when {
        totalLoss <= 45 -> StoryAudibilityV1.CLEAR
        totalLoss <= 85 -> StoryAudibilityV1.MUFFLED
        else -> StoryAudibilityV1.NONE
    }
}

internal fun currentPlayerPerceptionsV1(
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1,
    playerName: String = "",
): List<Pair<StoryActorLocationV1, StorySenseResultV1>> = spatial.actorLocations
    .filter { it.placeId.isNotBlank() && it.actor != STORY_PERCEPTION_PLAYER_ACTOR_V1 && (playerName.isBlank() || it.actor != playerName) }
    .map { actor -> actor to storySenseBetweenPlacesV1(spatial, perception, spatial.playerPlaceId, actor.placeId) }
    .sortedWith(compareByDescending<Pair<StoryActorLocationV1, StorySenseResultV1>> { it.second.canSee }
        .thenBy { it.second.audibility.ordinal })

internal fun renderStoryPerceptionDirectorNoteV1(
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1,
    playerName: String,
): String = buildString {
    append(STORY_PERCEPTION_NOTE_START_V1).append('\n')
    val current = spatial.places.firstOrNull { it.id == spatial.playerPlaceId }
    val env = perception.placeStates.firstOrNull { it.placeId == spatial.playerPlaceId }
    append("玩家当前感知位置：").append(current?.name ?: "未定位").append('\n')
    if (env != null) {
        append("场景环境：光线=").append(env.light.label)
            .append("；遮蔽=").append(env.obscurity.label)
            .append("；环境噪声=").append(env.ambientNoise).append("/100\n")
    }
    val sensed = currentPlayerPerceptionsV1(spatial, perception, playerName)
    val visible = sensed.filter { it.second.canSee }.map { it.first.actor }
    val clear = sensed.filter { it.second.audibility == StoryAudibilityV1.CLEAR }.map { it.first.actor }
    val muffled = sensed.filter { it.second.audibility == StoryAudibilityV1.MUFFLED }.map { it.first.actor }
    append("当前可以直接看见的角色：").append(visible.joinToString("、").ifBlank { "无" }).append('\n')
    append("当前可以清楚听见的角色：").append(clear.joinToString("、").ifBlank { "无" }).append('\n')
    append("只能听见模糊动静的角色：").append(muffled.joinToString("、").ifBlank { "无" }).append('\n')

    val latestTurn = perception.lastObservedTurnId
    val witnesses = perception.witnesses.filter { it.turnId == latestTurn }
    if (witnesses.isNotEmpty()) {
        append("上一轮哪些角色实际有机会感知玩家现场：\n")
        witnesses.take(16).forEach { item ->
            append("- ").append(item.actor).append("：视觉=").append(if (item.canSee) "可见" else "不可见")
                .append("；听觉=").append(item.audibility.label).append("；").append(item.reason).append('\n')
        }
    }
    append("感知硬规则：角色只能把自己实际看见、清楚听见，或通过明确通信收到的内容当作已知事实。不同地点不自动共享现场信息。\n")
    append("视觉硬规则：黑暗、浓雾/浓烟、关闭或封闭的门墙会阻断视线；看不见时不得描写角色准确表情、手势、屏幕内容或隐蔽物品。\n")
    append("听觉硬规则：标记为“只能听见模糊动静”时，只能知道附近有声音、方向、粗略情绪/强弱，不得获得准确台词、名字、秘密或完整事件内容。听不见时不得据此行动。\n")
    append("知识账本硬规则：只有满足感知条件或明确收到消息，才允许新增“知识:角色名” stateChanges；不得因为导演知道某事就让角色同步知道。\n")
    append(STORY_PERCEPTION_NOTE_END_V1)
}

internal fun mergeStoryPerceptionDirectorNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_PERCEPTION_NOTE_START_V1) + ".*?" + Regex.escape(STORY_PERCEPTION_NOTE_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    return listOf(stripped, block.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

@Composable
fun StoryPlayPanelV14(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val spatialVm: StorySpatialViewModelV1 = viewModel()
    val spatial by spatialVm.state.collectAsStateWithLifecycle()
    val perceptionVm: StoryPerceptionViewModelV1 = viewModel()
    val perception by perceptionVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val spatialScene = spatial.scene
    val perceptionScene = perception.scene
    val world = storyState.runtime?.world
    var showPerception by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id, spatialScene?.sessionId) {
        val active = session ?: return@LaunchedEffect
        val sp = spatial.scene ?: return@LaunchedEffect
        perceptionVm.open(book.id, active, sp, world?.atmosphere.orEmpty())
    }

    LaunchedEffect(spatialScene?.revision, world?.atmosphere) {
        val sp = spatial.scene ?: return@LaunchedEffect
        perceptionVm.syncSpatial(sp, world?.atmosphere.orEmpty())
    }

    val latestTurnId = session?.turns?.lastOrNull()?.id
    LaunchedEffect(session?.id, latestTurnId, spatialScene?.revision) {
        val active = session ?: return@LaunchedEffect
        val sp = spatial.scene ?: return@LaunchedEffect
        perceptionVm.observeTurn(active, sp)
    }

    LaunchedEffect(perceptionScene?.revision, spatialScene?.revision, world?.notes, storyState.busy) {
        val p = perception.scene ?: return@LaunchedEffect
        val sp = spatial.scene ?: return@LaunchedEffect
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy) return@LaunchedEffect
        val note = renderStoryPerceptionDirectorNoteV1(sp, p, session?.playerProfile?.name.orEmpty())
        val notes = mergeStoryPerceptionDirectorNoteV1(currentWorld.notes, note)
        if (notes != currentWorld.notes) storyVm.updateWorld(currentWorld.copy(notes = notes))
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV13(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )
        SmallFloatingActionButton(
            onClick = { showPerception = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 514.dp),
        ) {
            Icon(Icons.Rounded.Visibility, "场景感知")
        }
    }

    if (showPerception && spatialScene != null && perceptionScene != null) {
        StoryPerceptionDialogV1(
            spatial = spatialScene,
            perception = perceptionScene,
            playerName = session?.playerProfile?.name.orEmpty(),
            notice = perception.notice,
            onDismiss = { showPerception = false },
            onEnvironment = perceptionVm::setEnvironment,
            onResetEnvironment = { placeId -> perceptionVm.resetEnvironment(placeId, spatialScene, world?.atmosphere.orEmpty()) },
            onPortal = perceptionVm::setPortal,
            onResetPortal = { routeId -> perceptionVm.resetPortal(routeId, spatialScene) },
        )
    }
}

@Composable
private fun StoryPerceptionDialogV1(
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1,
    playerName: String,
    notice: String?,
    onDismiss: () -> Unit,
    onEnvironment: (String, StoryLightLevelV1, StoryObscurityV1, Int) -> Unit,
    onResetEnvironment: (String) -> Unit,
    onPortal: (String, StoryPortalStateV1, Boolean) -> Unit,
    onResetPortal: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val current = spatial.places.firstOrNull { it.id == spatial.playerPlaceId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("故事感知 · 视听")
                    Text("当前位置：${current?.name ?: "未定位"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 690.dp)) {
                if (!notice.isNullOrBlank()) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                }
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("场景") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("通道") })
                    Tab(tab == 2, { tab = 2 }, text = { Text("感知") })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (tab) {
                        0 -> items(spatial.places, key = { it.id }) { place ->
                            val state = perception.placeStates.firstOrNull { it.placeId == place.id } ?: defaultPerceptionPlaceV1(place)
                            PerceptionPlaceCardV1(place, state, onEnvironment, onResetEnvironment)
                        }
                        1 -> items(spatial.routes, key = { it.id }) { route ->
                            val state = perception.routeStates.firstOrNull { it.routeId == route.id } ?: defaultPerceptionRouteV1(spatial, route)
                            val from = spatial.places.firstOrNull { it.id == route.fromId }?.name ?: "?"
                            val to = spatial.places.firstOrNull { it.id == route.toId }?.name ?: "?"
                            PerceptionRouteCardV1(route, state, from, to, onPortal, onResetPortal)
                        }
                        else -> {
                            val sensed = currentPlayerPerceptionsV1(spatial, perception, playerName)
                            item {
                                Text("玩家当前能感知到谁", fontWeight = FontWeight.Bold)
                            }
                            if (sensed.isEmpty()) item { Text("当前没有已定位的其他角色。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(sensed, key = { it.first.actor }) { (actor, sense) ->
                                val place = spatial.places.firstOrNull { it.id == actor.placeId }?.name ?: actor.rawLocation
                                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                    Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                        Text(actor.actor, fontWeight = FontWeight.Bold)
                                        Text("$place · 视觉=${if (sense.canSee) "可见" else "不可见"} · ${sense.audibility.label}", style = MaterialTheme.typography.bodySmall)
                                        Text(sense.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            val last = perception.lastObservedTurnId
                            val witnesses = perception.witnesses.filter { it.turnId == last }
                            if (witnesses.isNotEmpty()) {
                                item { Text("上一轮 NPC 现场感知", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                                items(witnesses, key = { "${it.turnId}-${it.actor}" }) { witness ->
                                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                        Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                            Text(witness.actor, fontWeight = FontWeight.Bold)
                                            Text("视觉=${if (witness.canSee) "可见" else "不可见"} · ${witness.audibility.label}", style = MaterialTheme.typography.bodySmall)
                                            Text(witness.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun PerceptionPlaceCardV1(
    place: StoryPlaceV1,
    state: StoryPerceptionPlaceV1,
    onEnvironment: (String, StoryLightLevelV1, StoryObscurityV1, Int) -> Unit,
    onReset: (String) -> Unit,
) {
    var lightMenu by remember { mutableStateOf(false) }
    var obscurityMenu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(17.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(place.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.manual) "手动" else "自动", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    AssistChip(onClick = { lightMenu = true }, label = { Text("光线：${state.light.label}") })
                    DropdownMenu(lightMenu, { lightMenu = false }) {
                        StoryLightLevelV1.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, {
                            lightMenu = false
                            onEnvironment(place.id, item, state.obscurity, state.ambientNoise)
                        }) }
                    }
                }
                Box {
                    AssistChip(onClick = { obscurityMenu = true }, label = { Text("遮蔽：${state.obscurity.label}") })
                    DropdownMenu(obscurityMenu, { obscurityMenu = false }) {
                        StoryObscurityV1.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, {
                            obscurityMenu = false
                            onEnvironment(place.id, state.light, item, state.ambientNoise)
                        }) }
                    }
                }
            }
            Text("环境噪声 ${state.ambientNoise}/100", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.ambientNoise.toFloat(),
                onValueChange = { onEnvironment(place.id, state.light, state.obscurity, it.toInt()) },
                valueRange = 0f..100f,
            )
            if (state.manual) TextButton({ onReset(place.id) }) { Text("恢复自动判断") }
        }
    }
}

@Composable
private fun PerceptionRouteCardV1(
    route: StoryRouteEdgeV1,
    state: StoryPerceptionRouteV1,
    from: String,
    to: String,
    onPortal: (String, StoryPortalStateV1, Boolean) -> Unit,
    onReset: (String) -> Unit,
) {
    var stateMenu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(17.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(11.dp)) {
            Text("$from ${if (route.bidirectional) "↔" else "→"} $to", fontWeight = FontWeight.Bold)
            Text("${route.minutes} 分钟 · ${route.mode.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AssistChip(onClick = { stateMenu = true }, label = { Text("通道：${state.portalState.label}") })
                    DropdownMenu(stateMenu, { stateMenu = false }) {
                        StoryPortalStateV1.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, {
                            stateMenu = false
                            onPortal(route.id, item, state.visionPassWhenOpen)
                        }) }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("开放时可直视", style = MaterialTheme.typography.bodySmall)
                    Switch(state.visionPassWhenOpen, { onPortal(route.id, state.portalState, it) })
                }
            }
            Text(
                "声音衰减：开放 ${state.soundLossOpen} / 关闭 ${state.soundLossClosed}${if (state.portalState == StoryPortalStateV1.SEALED) " / 封闭=完全阻断" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.manual) TextButton({ onReset(route.id) }) { Text("恢复自动判断") }
        }
    }
}

private const val STORY_PERCEPTION_PLAYER_ACTOR_V1 = "@PLAYER"
private const val STORY_PERCEPTION_NOTE_START_V1 = "【场景感知约束｜导演层】"
private const val STORY_PERCEPTION_NOTE_END_V1 = "【/场景感知约束】"
