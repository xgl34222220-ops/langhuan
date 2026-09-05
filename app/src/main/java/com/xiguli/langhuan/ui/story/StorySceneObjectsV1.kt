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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.reader.LibraryExperienceState
import com.xiguli.langhuan.ui.reader.ReaderBookUi
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StorySceneObjectKindV1 {
    DOOR, LIGHT, WINDOW, CURTAIN, BARRIER, CONTAINER, SWITCH, OTHER;

    val label: String get() = when (this) {
        DOOR -> "门"
        LIGHT -> "灯/光源"
        WINDOW -> "窗"
        CURTAIN -> "窗帘/遮挡"
        BARRIER -> "障碍物"
        CONTAINER -> "容器"
        SWITCH -> "开关/设备"
        OTHER -> "其他物件"
    }
}

@Serializable
enum class StorySceneObjectActionV1 {
    OPEN, CLOSE, LOCK, UNLOCK, POWER_ON, POWER_OFF, BREAK, REPAIR, MOVE_ASIDE, MOVE_BLOCK;

    val label: String get() = when (this) {
        OPEN -> "打开"
        CLOSE -> "关闭"
        LOCK -> "上锁"
        UNLOCK -> "解锁"
        POWER_ON -> "开启"
        POWER_OFF -> "关闭电源"
        BREAK -> "破坏"
        REPAIR -> "修复"
        MOVE_ASIDE -> "移开"
        MOVE_BLOCK -> "堵住"
    }
}

@Serializable
data class StorySceneObjectV1(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: StorySceneObjectKindV1 = StorySceneObjectKindV1.OTHER,
    val placeId: String = "",
    val linkedRouteId: String = "",
    val open: Boolean = false,
    val locked: Boolean = false,
    val powered: Boolean = true,
    val broken: Boolean = false,
    val movedAside: Boolean = false,
    val description: String = "",
    val source: String = "手动",
)

@Serializable
data class StorySceneObjectInteractionV1(
    val id: String = UUID.randomUUID().toString(),
    val turnId: String,
    val actor: String,
    val objectId: String,
    val objectName: String,
    val action: StorySceneObjectActionV1,
    val accepted: Boolean,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StorySceneObjectTravelGuardV1(
    val turnId: String,
    val objectId: String,
    val objectName: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StorySceneObjectSceneV1(
    val sessionId: String,
    val objects: List<StorySceneObjectV1> = emptyList(),
    val interactions: List<StorySceneObjectInteractionV1> = emptyList(),
    val travelGuards: List<StorySceneObjectTravelGuardV1> = emptyList(),
    val lastObservedTurnId: String = "",
    val autoSeedDoors: Boolean = true,
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StorySceneObjectArchiveV1(
    val novelId: String,
    val scenes: List<StorySceneObjectSceneV1> = emptyList(),
)

data class StorySceneObjectUiStateV1(
    val novelId: String = "",
    val scene: StorySceneObjectSceneV1? = null,
    val scenes: List<StorySceneObjectSceneV1> = emptyList(),
    val notice: String? = null,
)

data class StorySceneObjectRouteEffectV1(
    val routeId: String,
    val portalState: StoryPortalStateV1,
    val visionPassWhenOpen: Boolean,
    val movementBlocked: Boolean,
    val reason: String,
)

data class StorySceneObjectActionResultV1(
    val objectAfter: StorySceneObjectV1,
    val accepted: Boolean,
    val reason: String,
)

class StorySceneObjectViewModelV1(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StorySceneObjectUiStateV1())
    val state: StateFlow<StorySceneObjectUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession, spatial: StorySpatialSceneV1?, perception: StoryPerceptionSceneV1?) {
        if (novelId.isBlank() || spatial == null) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) {
            syncSpatial(spatial, perception)
            return
        }
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
            ?: StorySceneObjectSceneV1(sessionId = session.id)
        val normalized = normalizeStorySceneObjectsV1(existing, spatial, perception)
        val scenes = archive.scenes.map { if (it.sessionId == session.id) normalized else it }
            .let { list -> if (list.any { it.sessionId == session.id }) list else list + normalized }
        _state.value = StorySceneObjectUiStateV1(novelId = novelId, scene = normalized, scenes = scenes)
        saveArchive(StorySceneObjectArchiveV1(novelId, scenes))
    }

    fun syncSpatial(spatial: StorySpatialSceneV1, perception: StoryPerceptionSceneV1?) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != spatial.sessionId) return
        val updated = normalizeStorySceneObjectsV1(scene, spatial, perception)
        if (updated != scene) persistScene(updated.copy(revision = scene.revision + 1), null)
    }

    fun setAutoSeedDoors(enabled: Boolean, spatial: StorySpatialSceneV1, perception: StoryPerceptionSceneV1?) {
        val scene = _state.value.scene ?: return
        val next = normalizeStorySceneObjectsV1(scene.copy(autoSeedDoors = enabled), spatial, perception)
        persistScene(next.copy(revision = scene.revision + 1), if (enabled) "门类物件自动补齐已开启" else "门类物件自动补齐已关闭")
    }

    fun addObject(name: String, kind: StorySceneObjectKindV1, placeId: String, routeId: String, description: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        mutate("场景物件已加入") { scene ->
            if (scene.objects.any { normalizeStorySceneObjectNameV1(it.name) == normalizeStorySceneObjectNameV1(clean) }) return@mutate scene
            scene.copy(objects = (scene.objects + StorySceneObjectV1(
                name = clean.take(100),
                kind = kind,
                placeId = placeId,
                linkedRouteId = routeId,
                open = kind == StorySceneObjectKindV1.CURTAIN,
                powered = kind != StorySceneObjectKindV1.LIGHT && kind != StorySceneObjectKindV1.SWITCH || true,
                description = description.trim().take(360),
                source = "手动",
            )).take(MAX_STORY_SCENE_OBJECTS_V1))
        }
    }

    fun deleteObject(id: String) = mutate("场景物件已删除") { scene ->
        scene.copy(objects = scene.objects.filterNot { it.id == id })
    }

    fun interact(objectId: String, action: StorySceneObjectActionV1, actor: String = "作者") {
        val scene = _state.value.scene ?: return
        val index = scene.objects.indexOfFirst { it.id == objectId }
        if (index < 0) return
        val old = scene.objects[index]
        val result = applyStorySceneObjectActionV1(old, action)
        val interaction = StorySceneObjectInteractionV1(
            turnId = "manual-${System.currentTimeMillis()}",
            actor = actor,
            objectId = old.id,
            objectName = old.name,
            action = action,
            accepted = result.accepted,
            reason = result.reason,
        )
        val objects = scene.objects.toMutableList().apply { set(index, result.objectAfter) }
        persistScene(
            scene.copy(objects = objects, interactions = (scene.interactions + interaction).takeLast(240), revision = scene.revision + 1),
            if (result.accepted) "${old.name}：${action.label}" else "${old.name} 操作失败：${result.reason}",
        )
    }

    fun observeTurn(session: StoryPlaySession) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val turn = session.turns.lastOrNull() ?: return
        if (turn.id == scene.lastObservedTurnId) return
        var objects = scene.objects
        val interactions = mutableListOf<StorySceneObjectInteractionV1>()
        val changes = extractStorySceneObjectActionsV1(turn)
        changes.forEach { change ->
            val index = resolveStorySceneObjectIndexV1(objects, change.first)
            if (index < 0) {
                interactions += StorySceneObjectInteractionV1(
                    turnId = turn.id,
                    actor = session.playerProfile.name.ifBlank { "玩家" },
                    objectId = "",
                    objectName = change.first,
                    action = change.second,
                    accepted = false,
                    reason = "物件不存在，不能凭空改变环境",
                )
            } else {
                val old = objects[index]
                val result = applyStorySceneObjectActionV1(old, change.second)
                objects = objects.toMutableList().apply { set(index, result.objectAfter) }
                interactions += StorySceneObjectInteractionV1(
                    turnId = turn.id,
                    actor = session.playerProfile.name.ifBlank { "玩家" },
                    objectId = old.id,
                    objectName = old.name,
                    action = change.second,
                    accepted = result.accepted,
                    reason = result.reason,
                )
            }
        }
        persistScene(
            scene.copy(
                objects = objects,
                interactions = (scene.interactions + interactions).takeLast(240),
                lastObservedTurnId = turn.id,
                revision = scene.revision + 1,
            ),
            when {
                interactions.isEmpty() -> null
                interactions.any { !it.accepted } -> "本轮部分物件交互被状态规则拦截"
                else -> "本轮场景物件状态已更新"
            },
        )
    }

    fun markTravelBlocked(turnId: String, objectId: String, objectName: String, reason: String) {
        val scene = _state.value.scene ?: return
        if (scene.travelGuards.any { it.turnId == turnId }) return
        persistScene(
            scene.copy(
                travelGuards = (scene.travelGuards + StorySceneObjectTravelGuardV1(turnId, objectId, objectName, reason)).takeLast(120),
                revision = scene.revision + 1,
            ),
            "移动被“$objectName”拦截",
        )
    }

    private fun mutate(message: String?, transform: (StorySceneObjectSceneV1) -> StorySceneObjectSceneV1) {
        val scene = _state.value.scene ?: return
        persistScene(transform(scene).copy(revision = scene.revision + 1), message)
    }

    private fun persistScene(updated: StorySceneObjectSceneV1, notice: String?) {
        val current = _state.value
        val normalized = updated.copy(updatedAt = System.currentTimeMillis())
        val scenes = current.scenes.map { if (it.sessionId == normalized.sessionId) normalized else it }
            .let { list -> if (list.any { it.sessionId == normalized.sessionId }) list else list + normalized }
        _state.update { it.copy(scene = normalized, scenes = scenes, notice = notice) }
        if (current.novelId.isNotBlank()) saveArchive(StorySceneObjectArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_scene_objects_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StorySceneObjectArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StorySceneObjectArchiveV1(novelId)
        return runCatching { json.decodeFromString(StorySceneObjectArchiveV1.serializer(), file.readText()) }
            .getOrElse { StorySceneObjectArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StorySceneObjectArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StorySceneObjectArchiveV1.serializer(), archive)) }
    }
}

internal fun normalizeStorySceneObjectsV1(
    scene: StorySceneObjectSceneV1,
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1?,
): StorySceneObjectSceneV1 {
    val validPlaces = spatial.places.map { it.id }.toSet()
    val validRoutes = spatial.routes.map { it.id }.toSet()
    var objects = scene.objects.filter { it.placeId.isBlank() || it.placeId in validPlaces }
        .map { if (it.linkedRouteId.isNotBlank() && it.linkedRouteId !in validRoutes) it.copy(linkedRouteId = "") else it }
    if (scene.autoSeedDoors) {
        val existingRouteIds = objects.filter { it.kind == StorySceneObjectKindV1.DOOR }.map { it.linkedRouteId }.filter { it.isNotBlank() }.toSet()
        val autoDoors = spatial.routes.mapNotNull { route ->
            if (route.id in existingRouteIds || route.minutes > 5) return@mapNotNull null
            val from = spatial.places.firstOrNull { it.id == route.fromId } ?: return@mapNotNull null
            val to = spatial.places.firstOrNull { it.id == route.toId } ?: return@mapNotNull null
            val roomBoundary = from.kind == StoryPlaceKindV1.ROOM || to.kind == StoryPlaceKindV1.ROOM
            if (!roomBoundary) return@mapNotNull null
            val portal = perception?.routeStates?.firstOrNull { it.routeId == route.id }
            StorySceneObjectV1(
                name = "${from.name}—${to.name}通道门",
                kind = StorySceneObjectKindV1.DOOR,
                placeId = if (from.kind == StoryPlaceKindV1.ROOM) from.id else to.id,
                linkedRouteId = route.id,
                open = portal?.portalState == StoryPortalStateV1.OPEN,
                locked = false,
                powered = true,
                source = "空间图自动",
            )
        }
        objects = (objects + autoDoors).distinctBy { it.id }.take(MAX_STORY_SCENE_OBJECTS_V1)
    }
    return scene.copy(objects = objects)
}

internal fun applyStorySceneObjectActionV1(
    item: StorySceneObjectV1,
    action: StorySceneObjectActionV1,
): StorySceneObjectActionResultV1 {
    fun ok(next: StorySceneObjectV1, reason: String) = StorySceneObjectActionResultV1(next, true, reason)
    fun no(reason: String) = StorySceneObjectActionResultV1(item, false, reason)
    return when (action) {
        StorySceneObjectActionV1.OPEN -> when {
            item.broken -> ok(item.copy(open = true, locked = false), "已损坏，处于敞开状态")
            item.locked -> no("物件仍然上锁，必须先解锁或破坏")
            else -> ok(item.copy(open = true, movedAside = true), "已打开")
        }
        StorySceneObjectActionV1.CLOSE -> when {
            item.broken && item.kind in listOf(StorySceneObjectKindV1.DOOR, StorySceneObjectKindV1.WINDOW) -> no("物件已损坏，无法正常关闭")
            else -> ok(item.copy(open = false, movedAside = false), "已关闭")
        }
        StorySceneObjectActionV1.LOCK -> when {
            item.kind !in listOf(StorySceneObjectKindV1.DOOR, StorySceneObjectKindV1.WINDOW, StorySceneObjectKindV1.CONTAINER) -> no("该物件没有可上锁结构")
            item.broken -> no("物件已损坏，无法上锁")
            item.open -> no("物件还开着，不能直接上锁")
            else -> ok(item.copy(locked = true), "已上锁")
        }
        StorySceneObjectActionV1.UNLOCK -> when {
            !item.locked -> ok(item, "本来就没有上锁")
            else -> ok(item.copy(locked = false), "已解锁")
        }
        StorySceneObjectActionV1.POWER_ON -> when {
            item.kind !in listOf(StorySceneObjectKindV1.LIGHT, StorySceneObjectKindV1.SWITCH, StorySceneObjectKindV1.OTHER) -> no("该物件不是可通电设备")
            item.broken -> no("设备已损坏，无法开启")
            else -> ok(item.copy(powered = true), "已开启")
        }
        StorySceneObjectActionV1.POWER_OFF -> when {
            item.kind !in listOf(StorySceneObjectKindV1.LIGHT, StorySceneObjectKindV1.SWITCH, StorySceneObjectKindV1.OTHER) -> no("该物件不是可通电设备")
            else -> ok(item.copy(powered = false), "已关闭")
        }
        StorySceneObjectActionV1.BREAK -> when {
            item.broken -> ok(item, "已经处于损坏状态")
            else -> ok(
                item.copy(
                    broken = true,
                    locked = false,
                    open = if (item.kind in listOf(StorySceneObjectKindV1.DOOR, StorySceneObjectKindV1.WINDOW)) true else item.open,
                    powered = if (item.kind in listOf(StorySceneObjectKindV1.LIGHT, StorySceneObjectKindV1.SWITCH)) false else item.powered,
                    movedAside = if (item.kind == StorySceneObjectKindV1.BARRIER) true else item.movedAside,
                ),
                "已损坏",
            )
        }
        StorySceneObjectActionV1.REPAIR -> ok(item.copy(broken = false), "已修复到可操作状态")
        StorySceneObjectActionV1.MOVE_ASIDE -> ok(item.copy(movedAside = true, open = if (item.kind == StorySceneObjectKindV1.CURTAIN) true else item.open), "已移开")
        StorySceneObjectActionV1.MOVE_BLOCK -> ok(item.copy(movedAside = false, open = if (item.kind == StorySceneObjectKindV1.CURTAIN) false else item.open), "已形成遮挡/阻塞")
    }
}

internal fun routeEffectForStorySceneObjectV1(item: StorySceneObjectV1): StorySceneObjectRouteEffectV1? {
    val routeId = item.linkedRouteId.takeIf { it.isNotBlank() } ?: return null
    return when (item.kind) {
        StorySceneObjectKindV1.DOOR -> when {
            item.broken || item.open -> StorySceneObjectRouteEffectV1(routeId, StoryPortalStateV1.OPEN, true, false, "门已打开/损坏")
            else -> StorySceneObjectRouteEffectV1(routeId, StoryPortalStateV1.CLOSED, false, true, if (item.locked) "门已上锁" else "门处于关闭状态")
        }
        StorySceneObjectKindV1.WINDOW -> when {
            item.broken || item.open -> StorySceneObjectRouteEffectV1(routeId, StoryPortalStateV1.OPEN, true, false, "窗户已打开/破损")
            else -> StorySceneObjectRouteEffectV1(routeId, StoryPortalStateV1.CLOSED, false, true, if (item.locked) "窗户已锁" else "窗户关闭")
        }
        StorySceneObjectKindV1.CURTAIN -> StorySceneObjectRouteEffectV1(
            routeId,
            StoryPortalStateV1.OPEN,
            item.open || item.movedAside || item.broken,
            false,
            if (item.open || item.movedAside || item.broken) "遮挡已移开" else "遮挡阻断视线",
        )
        StorySceneObjectKindV1.BARRIER -> if (item.movedAside || item.broken) {
            StorySceneObjectRouteEffectV1(routeId, StoryPortalStateV1.OPEN, true, false, "障碍物已移开")
        } else {
            StorySceneObjectRouteEffectV1(routeId, StoryPortalStateV1.SEALED, false, true, "障碍物堵住通道")
        }
        else -> null
    }
}

internal fun desiredLightForStorySceneObjectV1(item: StorySceneObjectV1): StoryLightLevelV1? = when (item.kind) {
    StorySceneObjectKindV1.LIGHT -> if (item.powered && !item.broken) StoryLightLevelV1.BRIGHT else StoryLightLevelV1.DARK
    else -> null
}

internal fun extractStorySceneObjectActionsV1(turn: StoryPlayTurn): List<Pair<String, StorySceneObjectActionV1>> =
    turn.variablesAfter.asSequence()
        .filter { it.subject.trim() == STORY_SCENE_OBJECT_SUBJECT_V1 }
        .mapNotNull { change ->
            val name = change.field.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val token = change.value.trim().uppercase()
            val action = StorySceneObjectActionV1.entries.firstOrNull { it.name == token } ?: return@mapNotNull null
            name to action
        }
        .toList()

internal fun resolveStorySceneObjectIndexV1(objects: List<StorySceneObjectV1>, raw: String): Int {
    val normalized = normalizeStorySceneObjectNameV1(raw)
    return objects.indexOfFirst { it.id == raw || normalizeStorySceneObjectNameV1(it.name) == normalized }
}

internal fun normalizeStorySceneObjectNameV1(value: String): String = value.lowercase()
    .replace(Regex("[\\s，。！？、,.!?：:；;‘’“”\"'（）()【】\\[\\]<>《》·_-]+"), "")
    .take(120)

internal fun findBlockingStorySceneObjectV1(
    scene: StorySceneObjectSceneV1,
    spatial: StorySpatialSceneV1,
    fromPlaceId: String,
    toPlaceId: String,
): StorySceneObjectV1? {
    if (fromPlaceId.isBlank() || toPlaceId.isBlank() || fromPlaceId == toPlaceId) return null
    val path = findShortestStoryRouteV1(spatial, fromPlaceId, toPlaceId) ?: return null
    val blockedByRoute = scene.objects.mapNotNull { item ->
        val effect = routeEffectForStorySceneObjectV1(item) ?: return@mapNotNull null
        if (effect.movementBlocked && effect.routeId in path.edgeIds) item else null
    }
    return blockedByRoute.firstOrNull()
}

internal fun renderStorySceneObjectsDirectorNoteV1(
    scene: StorySceneObjectSceneV1,
    spatial: StorySpatialSceneV1,
): String = buildString {
    append(STORY_SCENE_OBJECT_NOTE_START_V1).append('\n')
    val currentPlaceId = spatial.playerPlaceId
    val current = spatial.places.firstOrNull { it.id == currentPlaceId }
    append("玩家当前位置物件状态：").append(current?.name ?: "未定位").append('\n')
    val nearbyRouteIds = spatial.routes.filter { it.fromId == currentPlaceId || it.toId == currentPlaceId }.map { it.id }.toSet()
    val relevant = scene.objects.filter { it.placeId == currentPlaceId || it.linkedRouteId in nearbyRouteIds }.take(18)
    if (relevant.isEmpty()) append("- 当前没有登记可交互物件\n")
    relevant.forEach { item ->
        append("- ").append(item.name).append(" [").append(item.kind.label).append("]：")
        val states = buildList {
            if (item.kind in listOf(StorySceneObjectKindV1.DOOR, StorySceneObjectKindV1.WINDOW, StorySceneObjectKindV1.CURTAIN, StorySceneObjectKindV1.CONTAINER)) add(if (item.open) "打开" else "关闭")
            if (item.locked) add("上锁")
            if (item.kind in listOf(StorySceneObjectKindV1.LIGHT, StorySceneObjectKindV1.SWITCH)) add(if (item.powered) "通电" else "断电")
            if (item.broken) add("损坏")
            if (item.kind == StorySceneObjectKindV1.BARRIER) add(if (item.movedAside) "已移开" else "阻塞中")
        }
        append(states.joinToString("、").ifBlank { "正常" })
        if (item.description.isNotBlank()) append("；").append(item.description)
        append('\n')
    }
    scene.travelGuards.lastOrNull()?.let { guard ->
        append("最近一次物件移动拦截：").append(guard.objectName).append("；").append(guard.reason).append("。不得把被拦截的目标当成已到达。\n")
    }
    append("物件硬规则：门/窗/障碍物关闭、上锁或阻塞时，必须先完成对应交互，不能穿过；物件状态改变后立即影响空间通行和视听传播。\n")
    append("物件硬规则：灯关闭/损坏后应按黑暗处理；窗帘或障碍形成遮挡后不得继续描述被遮挡的视觉信息。\n")
    append("物件硬规则：上锁物件不能直接 OPEN，必须先 UNLOCK 或合理 BREAK；损坏的门窗不能假装正常关闭，上锁也不能凭空恢复。\n")
    append("DM 输出协议：只要本轮真实改变场景物件，stateChanges 必须加入 subject=\"").append(STORY_SCENE_OBJECT_SUBJECT_V1)
        .append("\"，field=空间中的准确物件名，after=OPEN|CLOSE|LOCK|UNLOCK|POWER_ON|POWER_OFF|BREAK|REPAIR|MOVE_ASIDE|MOVE_BLOCK，evidence=动作依据。\n")
    append("DM 输出协议：若交互失败，不得同时把世界地点、光线、门状态或人物位置写成成功后的结果。\n")
    append(STORY_SCENE_OBJECT_NOTE_END_V1)
}

internal fun mergeStorySceneObjectsDirectorNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_SCENE_OBJECT_NOTE_START_V1) + ".*?" + Regex.escape(STORY_SCENE_OBJECT_NOTE_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    return listOf(stripped, block.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

@Composable
fun StoryPlayPanelV15(
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
    val objectVm: StorySceneObjectViewModelV1 = viewModel()
    val objectState by objectVm.state.collectAsStateWithLifecycle()
    val session = storyState.active
    val spatialScene = spatial.scene
    val perceptionScene = perception.scene
    val objectScene = objectState.scene
    var showObjects by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id, spatialScene?.revision, perceptionScene?.revision) {
        val active = session ?: return@LaunchedEffect
        objectVm.open(book.id, active, spatialScene, perceptionScene)
    }

    val latestTurnId = session?.turns?.lastOrNull()?.id
    LaunchedEffect(session?.id, latestTurnId) {
        val active = session ?: return@LaunchedEffect
        objectVm.observeTurn(active)
    }

    LaunchedEffect(objectScene?.revision, perceptionScene?.revision) {
        val scene = objectScene ?: return@LaunchedEffect
        val p = perception.scene ?: return@LaunchedEffect
        scene.objects.mapNotNull(::routeEffectForStorySceneObjectV1).forEach { effect ->
            val old = p.routeStates.firstOrNull { it.routeId == effect.routeId }
            if (old != null && (old.portalState != effect.portalState || old.visionPassWhenOpen != effect.visionPassWhenOpen)) {
                perceptionVm.setPortal(effect.routeId, effect.portalState, effect.visionPassWhenOpen)
            }
        }
        scene.objects.forEach { item ->
            val light = desiredLightForStorySceneObjectV1(item) ?: return@forEach
            if (item.placeId.isBlank()) return@forEach
            val old = p.placeStates.firstOrNull { it.placeId == item.placeId } ?: return@forEach
            if (old.light != light) perceptionVm.setEnvironment(item.placeId, light, old.obscurity, old.ambientNoise)
        }
    }

    LaunchedEffect(objectScene?.revision, spatialScene?.revision, latestTurnId) {
        val scene = objectScene ?: return@LaunchedEffect
        val s = spatial.scene ?: return@LaunchedEffect
        val turnId = latestTurnId ?: return@LaunchedEffect
        if (scene.travelGuards.any { it.turnId == turnId }) return@LaunchedEffect
        val decision = s.decisions.lastOrNull { it.turnId == turnId && it.accepted } ?: return@LaunchedEffect
        val blocker = findBlockingStorySceneObjectV1(scene, s, decision.fromPlaceId, decision.toPlaceId) ?: return@LaunchedEffect
        val from = s.places.firstOrNull { it.id == decision.fromPlaceId }
        if (from != null) {
            spatialVm.setPlayerPlace(from.id)
            val world = storyState.runtime?.world
            if (world != null && normalizeStoryPlaceNameV1(world.location) != normalizeStoryPlaceNameV1(from.name)) {
                storyVm.updateWorld(world.copy(location = from.name))
            }
        }
        objectVm.markTravelBlocked(turnId, blocker.id, blocker.name, routeEffectForStorySceneObjectV1(blocker)?.reason ?: "物件阻塞通道")
    }

    LaunchedEffect(objectScene?.revision, storyState.runtime?.world?.notes, storyState.busy) {
        val scene = objectScene ?: return@LaunchedEffect
        val s = spatial.scene ?: return@LaunchedEffect
        val world = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy) return@LaunchedEffect
        val block = renderStorySceneObjectsDirectorNoteV1(scene, s)
        val notes = mergeStorySceneObjectsDirectorNoteV1(world.notes, block)
        if (notes != world.notes) storyVm.updateWorld(world.copy(notes = notes))
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV14(book, libraryState, aiReady, onAiSetup, onAdopted)
        SmallFloatingActionButton(
            onClick = { showObjects = true },
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 518.dp),
        ) {
            Icon(Icons.Rounded.Tune, "场景物件")
        }
    }

    if (showObjects && spatialScene != null && objectScene != null) {
        StorySceneObjectsDialogV1(
            scene = objectScene,
            spatial = spatialScene,
            perception = perceptionScene,
            notice = objectState.notice,
            onDismiss = { showObjects = false },
            onAutoSeed = { enabled -> objectVm.setAutoSeedDoors(enabled, spatialScene, perceptionScene) },
            onAdd = objectVm::addObject,
            onDelete = objectVm::deleteObject,
            onInteract = { id, action -> objectVm.interact(id, action) },
        )
    }
}

@Composable
private fun StorySceneObjectsDialogV1(
    scene: StorySceneObjectSceneV1,
    spatial: StorySpatialSceneV1,
    perception: StoryPerceptionSceneV1?,
    notice: String?,
    onDismiss: () -> Unit,
    onAutoSeed: (Boolean) -> Unit,
    onAdd: (String, StorySceneObjectKindV1, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onInteract: (String, StorySceneObjectActionV1) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val currentPlace = spatial.places.firstOrNull { it.id == spatial.playerPlaceId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null)
                    Spacer(Modifier.width(8.dp))
                    Text("场景物件 · 环境交互")
                }
                Text("当前位置：${currentPlace?.name ?: "未定位"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 700.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动补齐空间图中的门", fontWeight = FontWeight.Bold)
                        Text("只依据当前分支已有地点/路线，不凭空生成窗户或设备。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(scene.autoSeedDoors, onAutoSeed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ showAdd = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("添加物件") }
                    val env = perception?.placeStates?.firstOrNull { it.placeId == spatial.playerPlaceId }
                    if (env != null) FilledTonalButton({}, enabled = false) { Text("${env.light.label} · 噪声${env.ambientNoise}") }
                }
                if (!notice.isNullOrBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("物件 ${scene.objects.size}") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("交互记录") })
                    Tab(tab == 2, { tab = 2 }, text = { Text("移动拦截") })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (tab) {
                        0 -> items(scene.objects, key = { it.id }) { item ->
                            StorySceneObjectCardV1(item, spatial, onDelete, onInteract)
                        }
                        1 -> items(scene.interactions.reversed(), key = { it.id }) { event ->
                            Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                    Text("${event.objectName} · ${event.action.label}", fontWeight = FontWeight.Bold)
                                    Text("${event.actor} · ${if (event.accepted) "成功" else "失败"} · ${event.reason}", style = MaterialTheme.typography.bodySmall, color = if (event.accepted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        2 -> items(scene.travelGuards.reversed(), key = { it.turnId }) { guard ->
                            Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                    Text("${guard.objectName} 阻止移动", fontWeight = FontWeight.Bold)
                                    Text(guard.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("完成") } },
    )

    if (showAdd) {
        AddStorySceneObjectDialogV1(
            spatial = spatial,
            onDismiss = { showAdd = false },
            onSubmit = { name, kind, placeId, routeId, desc -> onAdd(name, kind, placeId, routeId, desc); showAdd = false },
        )
    }
}

@Composable
private fun StorySceneObjectCardV1(
    item: StorySceneObjectV1,
    spatial: StorySpatialSceneV1,
    onDelete: (String) -> Unit,
    onInteract: (String, StorySceneObjectActionV1) -> Unit,
) {
    val place = spatial.places.firstOrNull { it.id == item.placeId }
    val route = spatial.routes.firstOrNull { it.id == item.linkedRouteId }
    val routeLabel = route?.let {
        val from = spatial.places.firstOrNull { p -> p.id == it.fromId }?.name ?: "?"
        val to = spatial.places.firstOrNull { p -> p.id == it.toId }?.name ?: "?"
        "$from ↔ $to"
    }
    Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            val stateText = buildList {
                if (item.kind in listOf(StorySceneObjectKindV1.DOOR, StorySceneObjectKindV1.WINDOW, StorySceneObjectKindV1.CURTAIN, StorySceneObjectKindV1.CONTAINER)) add(if (item.open) "打开" else "关闭")
                if (item.locked) add("上锁")
                if (item.kind in listOf(StorySceneObjectKindV1.LIGHT, StorySceneObjectKindV1.SWITCH)) add(if (item.powered) "开启" else "关闭")
                if (item.broken) add("损坏")
                if (item.kind == StorySceneObjectKindV1.BARRIER) add(if (item.movedAside) "已移开" else "阻塞")
            }.joinToString(" · ").ifBlank { "正常" }
            Text(stateText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOfNotNull(place?.name, routeLabel, item.source).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                when (item.kind) {
                    StorySceneObjectKindV1.DOOR, StorySceneObjectKindV1.WINDOW, StorySceneObjectKindV1.CONTAINER -> {
                        TextButton({ onInteract(item.id, if (item.open) StorySceneObjectActionV1.CLOSE else StorySceneObjectActionV1.OPEN) }) { Text(if (item.open) "关闭" else "打开") }
                        TextButton({ onInteract(item.id, if (item.locked) StorySceneObjectActionV1.UNLOCK else StorySceneObjectActionV1.LOCK) }) { Text(if (item.locked) "解锁" else "上锁") }
                    }
                    StorySceneObjectKindV1.LIGHT, StorySceneObjectKindV1.SWITCH -> TextButton({ onInteract(item.id, if (item.powered) StorySceneObjectActionV1.POWER_OFF else StorySceneObjectActionV1.POWER_ON) }) { Text(if (item.powered) "关闭" else "开启") }
                    StorySceneObjectKindV1.CURTAIN, StorySceneObjectKindV1.BARRIER -> TextButton({ onInteract(item.id, if (item.movedAside || item.open) StorySceneObjectActionV1.MOVE_BLOCK else StorySceneObjectActionV1.MOVE_ASIDE) }) { Text(if (item.movedAside || item.open) "复位遮挡" else "移开") }
                    else -> Unit
                }
                TextButton({ onInteract(item.id, if (item.broken) StorySceneObjectActionV1.REPAIR else StorySceneObjectActionV1.BREAK) }) { Text(if (item.broken) "修复" else "破坏") }
                if (item.source == "手动") TextButton({ onDelete(item.id) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun AddStorySceneObjectDialogV1(
    spatial: StorySpatialSceneV1,
    onDismiss: () -> Unit,
    onSubmit: (String, StorySceneObjectKindV1, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(StorySceneObjectKindV1.OTHER) }
    var placeId by remember { mutableStateOf(spatial.playerPlaceId) }
    var routeId by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var kindMenu by remember { mutableStateOf(false) }
    var placeMenu by remember { mutableStateOf(false) }
    var routeMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加场景物件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("物件名") }, singleLine = true)
                Box {
                    OutlinedButton({ kindMenu = true }, Modifier.fillMaxWidth()) { Text("类型：${kind.label}") }
                    DropdownMenu(kindMenu, { kindMenu = false }) {
                        StorySceneObjectKindV1.entries.forEach { value -> DropdownMenuItem({ Text(value.label) }, { kind = value; kindMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton({ placeMenu = true }, Modifier.fillMaxWidth()) { Text("所在地点：${spatial.places.firstOrNull { it.id == placeId }?.name ?: "无"}") }
                    DropdownMenu(placeMenu, { placeMenu = false }) {
                        DropdownMenuItem({ Text("无") }, { placeId = ""; placeMenu = false })
                        spatial.places.forEach { p -> DropdownMenuItem({ Text(p.name) }, { placeId = p.id; placeMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton({ routeMenu = true }, Modifier.fillMaxWidth()) {
                        val r = spatial.routes.firstOrNull { it.id == routeId }
                        val label = r?.let {
                            val a = spatial.places.firstOrNull { p -> p.id == it.fromId }?.name ?: "?"
                            val b = spatial.places.firstOrNull { p -> p.id == it.toId }?.name ?: "?"
                            "$a ↔ $b"
                        } ?: "不绑定路线"
                        Text("影响通道：$label")
                    }
                    DropdownMenu(routeMenu, { routeMenu = false }) {
                        DropdownMenuItem({ Text("不绑定路线") }, { routeId = ""; routeMenu = false })
                        spatial.routes.forEach { r ->
                            val a = spatial.places.firstOrNull { it.id == r.fromId }?.name ?: "?"
                            val b = spatial.places.firstOrNull { it.id == r.toId }?.name ?: "?"
                            DropdownMenuItem({ Text("$a ↔ $b") }, { routeId = r.id; routeMenu = false })
                        }
                    }
                }
                OutlinedTextField(desc, { desc = it }, label = { Text("说明（可选）") }, minLines = 2)
            }
        },
        confirmButton = { Button({ onSubmit(name, kind, placeId, routeId, desc) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

internal const val STORY_SCENE_OBJECT_SUBJECT_V1 = "STORY_OBJECT"
private const val STORY_SCENE_OBJECT_NOTE_START_V1 = "【场景物件状态｜导演层】"
private const val STORY_SCENE_OBJECT_NOTE_END_V1 = "【/场景物件状态】"
private const val MAX_STORY_SCENE_OBJECTS_V1 = 160
