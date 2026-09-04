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
import androidx.lifecycle.viewmodel.compose.viewModel
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
enum class StoryPlaceKindV1 {
    ROOM, BUILDING, AREA, CITY, REGION, UNKNOWN;

    val label: String get() = when (this) {
        ROOM -> "房间"
        BUILDING -> "建筑"
        AREA -> "区域"
        CITY -> "城市"
        REGION -> "地区"
        UNKNOWN -> "地点"
    }
}

@Serializable
enum class StoryTravelModeV1 {
    WALK, VEHICLE, TRANSIT, CUSTOM;

    val label: String get() = when (this) {
        WALK -> "步行"
        VEHICLE -> "驾车"
        TRANSIT -> "公共交通"
        CUSTOM -> "自定义"
    }
}

@Serializable
data class StoryPlaceV1(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aliases: List<String> = emptyList(),
    val parentId: String = "",
    val kind: StoryPlaceKindV1 = StoryPlaceKindV1.UNKNOWN,
    val description: String = "",
    val canonChapter: Int = 0,
    val source: String = "分支",
)

@Serializable
data class StoryRouteEdgeV1(
    val id: String = UUID.randomUUID().toString(),
    val fromId: String,
    val toId: String,
    val minutes: Int,
    val mode: StoryTravelModeV1 = StoryTravelModeV1.WALK,
    val bidirectional: Boolean = true,
    val description: String = "",
    val source: String = "手动",
)

@Serializable
data class StoryActorLocationV1(
    val actor: String,
    val placeId: String = "",
    val rawLocation: String = "",
    val updatedMinute: Long = 0L,
)

@Serializable
data class StorySpatialTravelDecisionV1(
    val turnId: String,
    val actor: String,
    val fromPlaceId: String,
    val toPlaceId: String,
    val requiredMinutes: Int,
    val actualMinutes: Int,
    val accepted: Boolean,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StorySpatialSceneV1(
    val sessionId: String,
    val anchorChapter: Int,
    val places: List<StoryPlaceV1> = emptyList(),
    val routes: List<StoryRouteEdgeV1> = emptyList(),
    val actorLocations: List<StoryActorLocationV1> = emptyList(),
    val playerPlaceId: String = "",
    val autoSeedCanon: Boolean = true,
    val lastObservedTurnId: String = "",
    val decisions: List<StorySpatialTravelDecisionV1> = emptyList(),
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StorySpatialArchiveV1(
    val novelId: String,
    val scenes: List<StorySpatialSceneV1> = emptyList(),
)

data class StorySpatialUiStateV1(
    val novelId: String = "",
    val scene: StorySpatialSceneV1? = null,
    val scenes: List<StorySpatialSceneV1> = emptyList(),
    val notice: String? = null,
    val error: String? = null,
)

data class StoryRoutePathV1(
    val placeIds: List<String>,
    val edgeIds: List<String>,
    val totalMinutes: Int,
)

data class StorySpatialMoveCheckV1(
    val accepted: Boolean,
    val requiredMinutes: Int,
    val reason: String,
)

class StorySpatialViewModelV1(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val canonStore = OriginalCanonArchiveStoreV1(application)
    private val _state = MutableStateFlow(StorySpatialUiStateV1())
    val state: StateFlow<StorySpatialUiStateV1> = _state.asStateFlow()

    fun open(
        novelId: String,
        session: StoryPlaySession,
        worldLocation: String,
        offscreen: NpcOffscreenSceneV1?,
        clockMinute: Long,
    ) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) {
            syncRuntime(session, worldLocation, offscreen, clockMinute)
            return
        }
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val seeded = if (existing == null) {
            seedStorySpatialSceneV1(
                session = session,
                canon = canonStore.load(novelId),
                worldLocation = worldLocation,
                offscreen = offscreen,
                clockMinute = clockMinute,
            )
        } else {
            mergeRuntimeLocationsV1(existing, session, worldLocation, offscreen, clockMinute)
        }
        val scenes = archive.scenes.map { if (it.sessionId == session.id) seeded else it }
            .let { list -> if (list.any { it.sessionId == session.id }) list else list + seeded }
        _state.value = StorySpatialUiStateV1(novelId = novelId, scene = seeded, scenes = scenes)
        saveArchive(StorySpatialArchiveV1(novelId, scenes))
    }

    fun syncRuntime(
        session: StoryPlaySession,
        worldLocation: String,
        offscreen: NpcOffscreenSceneV1?,
        clockMinute: Long,
    ) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val updated = mergeRuntimeLocationsV1(scene, session, worldLocation, offscreen, clockMinute)
        if (updated != scene) persistScene(updated.copy(revision = scene.revision + 1), null)
    }

    fun rebuildFromCanon(session: StoryPlaySession, worldLocation: String, offscreen: NpcOffscreenSceneV1?, clockMinute: Long) {
        val current = _state.value
        val scene = current.scene ?: return
        val canon = canonStore.load(current.novelId)
        val rebuilt = seedStorySpatialSceneV1(session, canon, worldLocation, offscreen, clockMinute)
        val manualPlaces = scene.places.filter { it.source == "手动" }
        val manualRoutes = scene.routes.filter { it.source == "手动" }
        val mergedPlaces = mergeStoryPlacesV1(rebuilt.places + manualPlaces)
        val idByName = mergedPlaces.associateBy { normalizeStoryPlaceNameV1(it.name) }
        val normalizedManualRoutes = manualRoutes.mapNotNull { edge ->
            val oldFrom = scene.places.firstOrNull { it.id == edge.fromId }?.name ?: return@mapNotNull null
            val oldTo = scene.places.firstOrNull { it.id == edge.toId }?.name ?: return@mapNotNull null
            val from = idByName[normalizeStoryPlaceNameV1(oldFrom)] ?: return@mapNotNull null
            val to = idByName[normalizeStoryPlaceNameV1(oldTo)] ?: return@mapNotNull null
            edge.copy(fromId = from.id, toId = to.id)
        }
        val rebuiltRoutes = inferCanonicalRoutesV1(canon, session.anchorChapter, mergedPlaces)
        persistScene(
            rebuilt.copy(
                places = mergedPlaces,
                routes = compactStoryRoutesV1(rebuiltRoutes + normalizedManualRoutes),
                revision = scene.revision + 1,
            ),
            "已按第 ${session.anchorChapter} 章边界重新建立地点与路线",
        )
    }

    fun setAutoSeedCanon(enabled: Boolean) = mutate(if (enabled) "原著地点自动补齐已开启" else "原著地点自动补齐已关闭") {
        it.copy(autoSeedCanon = enabled)
    }

    fun addPlace(name: String, kind: StoryPlaceKindV1, parentId: String, description: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        mutate("地点已加入空间图") { scene ->
            if (resolveStoryPlaceV1(scene, clean) != null) return@mutate scene
            scene.copy(
                places = scene.places + StoryPlaceV1(
                    name = clean.take(120),
                    parentId = parentId.takeIf { id -> scene.places.any { it.id == id } }.orEmpty(),
                    kind = kind,
                    description = description.trim().take(420),
                    source = "手动",
                ),
            )
        }
    }

    fun deletePlace(id: String) = mutate("地点已删除") { scene ->
        scene.copy(
            places = scene.places.filterNot { it.id == id }.map { if (it.parentId == id) it.copy(parentId = "") else it },
            routes = scene.routes.filterNot { it.fromId == id || it.toId == id },
            actorLocations = scene.actorLocations.map { if (it.placeId == id) it.copy(placeId = "") else it },
            playerPlaceId = scene.playerPlaceId.takeUnless { it == id }.orEmpty(),
        )
    }

    fun addRoute(
        fromId: String,
        toId: String,
        minutes: Int,
        mode: StoryTravelModeV1,
        bidirectional: Boolean,
        description: String,
    ) {
        val scene = _state.value.scene ?: return
        if (fromId == toId || scene.places.none { it.id == fromId } || scene.places.none { it.id == toId }) return
        mutate("路线已保存") { current ->
            val edge = StoryRouteEdgeV1(
                fromId = fromId,
                toId = toId,
                minutes = minutes.coerceIn(1, 10_080),
                mode = mode,
                bidirectional = bidirectional,
                description = description.trim().take(300),
                source = "手动",
            )
            val filtered = current.routes.filterNot { existing ->
                sameStoryRouteEndpointsV1(existing, edge)
            }
            current.copy(routes = compactStoryRoutesV1(filtered + edge))
        }
    }

    fun deleteRoute(id: String) = mutate("路线已删除") { scene ->
        scene.copy(routes = scene.routes.filterNot { it.id == id })
    }

    fun setPlayerPlace(id: String) = mutate("玩家当前位置已修改") { scene ->
        val place = scene.places.firstOrNull { it.id == id } ?: return@mutate scene
        val actor = scene.actorLocations.filterNot { it.actor == STORY_PLAYER_ACTOR_V1 } + StoryActorLocationV1(
            actor = STORY_PLAYER_ACTOR_V1,
            placeId = place.id,
            rawLocation = place.name,
        )
        scene.copy(playerPlaceId = place.id, actorLocations = actor)
    }

    fun observeTurn(
        session: StoryPlaySession,
        worldLocation: String,
        temporal: StoryDmTemporalSceneV1?,
        clockMinute: Long,
    ) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val turn = session.turns.lastOrNull() ?: return
        if (turn.id == scene.lastObservedTurnId) return

        var working = mergeRuntimeLocationsV1(scene, session, worldLocation = "", offscreen = null, clockMinute = clockMinute)
        val from = working.places.firstOrNull { it.id == working.playerPlaceId }
        val metadataTarget = extractStorySpatialTargetV1(turn)
        val worldTarget = worldLocation.takeIf { raw ->
            raw.isNotBlank() && from != null && normalizeStoryPlaceNameV1(raw) != normalizeStoryPlaceNameV1(from.name)
        }
        val targetName = metadataTarget ?: worldTarget
        val target = targetName?.let { resolveStoryPlaceV1(working, it) }
        val actual = extractStoryDmTurnDurationV1(turn)
            ?: temporal?.decisions?.lastOrNull { it.turnId == turn.id }?.durationMinutes
            ?: inferStoryTurnDurationV1(turn.player, turn.narration)

        val decision = when {
            targetName.isNullOrBlank() -> null
            target == null -> StorySpatialTravelDecisionV1(
                turnId = turn.id,
                actor = STORY_PLAYER_ACTOR_V1,
                fromPlaceId = from?.id.orEmpty(),
                toPlaceId = "",
                requiredMinutes = 0,
                actualMinutes = actual,
                accepted = false,
                reason = "目标地点“$targetName”不在当前空间图，不能直接瞬移",
            )
            from == null -> StorySpatialTravelDecisionV1(
                turnId = turn.id,
                actor = STORY_PLAYER_ACTOR_V1,
                fromPlaceId = "",
                toPlaceId = target.id,
                requiredMinutes = 0,
                actualMinutes = actual,
                accepted = true,
                reason = "建立分支初始位置",
            )
            else -> {
                val check = evaluateStorySpatialMoveV1(working, from.id, target.id, actual)
                StorySpatialTravelDecisionV1(
                    turnId = turn.id,
                    actor = STORY_PLAYER_ACTOR_V1,
                    fromPlaceId = from.id,
                    toPlaceId = target.id,
                    requiredMinutes = check.requiredMinutes,
                    actualMinutes = actual,
                    accepted = check.accepted,
                    reason = check.reason,
                )
            }
        }

        if (decision != null && decision.accepted && decision.toPlaceId.isNotBlank()) {
            val destination = working.places.firstOrNull { it.id == decision.toPlaceId }
            if (destination != null) {
                working = working.copy(
                    playerPlaceId = destination.id,
                    actorLocations = working.actorLocations.filterNot { it.actor == STORY_PLAYER_ACTOR_V1 } + StoryActorLocationV1(
                        actor = STORY_PLAYER_ACTOR_V1,
                        placeId = destination.id,
                        rawLocation = destination.name,
                        updatedMinute = clockMinute,
                    ),
                )
            }
        }
        persistScene(
            working.copy(
                lastObservedTurnId = turn.id,
                decisions = if (decision == null) working.decisions else (working.decisions + decision).distinctBy { it.turnId }.takeLast(160),
                revision = working.revision + 1,
            ),
            decision?.let { if (it.accepted) "空间移动已核验：${it.reason}" else "已拦截空间跳跃：${it.reason}" },
        )
    }

    private fun mutate(message: String?, transform: (StorySpatialSceneV1) -> StorySpatialSceneV1) {
        val scene = _state.value.scene ?: return
        persistScene(transform(scene).copy(revision = scene.revision + 1), message)
    }

    private fun persistScene(updated: StorySpatialSceneV1, notice: String?) {
        val current = _state.value
        val normalized = updated.copy(updatedAt = System.currentTimeMillis())
        val scenes = current.scenes.map { if (it.sessionId == normalized.sessionId) normalized else it }
            .let { list -> if (list.any { it.sessionId == normalized.sessionId }) list else list + normalized }
        _state.update { it.copy(scene = normalized, scenes = scenes, notice = notice, error = null) }
        if (current.novelId.isNotBlank()) saveArchive(StorySpatialArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_spatial_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StorySpatialArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StorySpatialArchiveV1(novelId)
        return runCatching { json.decodeFromString(StorySpatialArchiveV1.serializer(), file.readText()) }
            .getOrElse { StorySpatialArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StorySpatialArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StorySpatialArchiveV1.serializer(), archive)) }
    }
}

internal fun seedStorySpatialSceneV1(
    session: StoryPlaySession,
    canon: OriginalCanonArchiveV1?,
    worldLocation: String,
    offscreen: NpcOffscreenSceneV1?,
    clockMinute: Long,
): StorySpatialSceneV1 {
    val canonPlaces = canon?.digests.orEmpty()
        .filter { it.chapterNumber <= session.anchorChapter }
        .flatMap { digest ->
            val entities = digest.entities.filter { it.type == CanonEntityTypeV1.LOCATION }.map { entity ->
                StoryPlaceV1(
                    name = entity.name.trim(),
                    aliases = entity.aliases.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8),
                    kind = inferStoryPlaceKindV1(entity.name),
                    description = entity.description.take(420),
                    canonChapter = entity.chapterNumber,
                    source = "原著",
                )
            }
            val events = digest.events.mapNotNull { event ->
                event.location.trim().takeIf { it.isNotBlank() }?.let { location ->
                    StoryPlaceV1(
                        name = location,
                        kind = inferStoryPlaceKindV1(location),
                        canonChapter = event.chapterNumber,
                        source = "原著事件",
                    )
                }
            }
            entities + events
        }
    val runtimePlaces = buildList {
        worldLocation.trim().takeIf { it.isNotBlank() }?.let { add(StoryPlaceV1(name = it, kind = inferStoryPlaceKindV1(it), source = "当前分支")) }
        offscreen?.actors.orEmpty().forEach { actor ->
            actor.location.trim().takeIf { it.isNotBlank() }?.let { add(StoryPlaceV1(name = it, kind = inferStoryPlaceKindV1(it), source = "离场NPC")) }
        }
    }
    var places = mergeStoryPlacesV1(canonPlaces + runtimePlaces).take(MAX_STORY_PLACES_V1)
    places = inferStoryPlaceParentsV1(places)
    val routes = inferCanonicalRoutesV1(canon, session.anchorChapter, places)
    val base = StorySpatialSceneV1(
        sessionId = session.id,
        anchorChapter = session.anchorChapter,
        places = places,
        routes = routes,
    )
    return mergeRuntimeLocationsV1(base, session, worldLocation, offscreen, clockMinute)
}

internal fun mergeRuntimeLocationsV1(
    scene: StorySpatialSceneV1,
    session: StoryPlaySession,
    worldLocation: String,
    offscreen: NpcOffscreenSceneV1?,
    clockMinute: Long,
): StorySpatialSceneV1 {
    var places = scene.places
    fun ensurePlace(raw: String, source: String): StoryPlaceV1? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        resolveStoryPlaceV1(scene.copy(places = places), clean)?.let { return it }
        val created = StoryPlaceV1(name = clean.take(120), kind = inferStoryPlaceKindV1(clean), source = source)
        places = mergeStoryPlacesV1(places + created).take(MAX_STORY_PLACES_V1)
        return resolveStoryPlaceV1(scene.copy(places = places), clean)
    }

    val actors = scene.actorLocations.toMutableList()
    val currentPlayer = scene.places.firstOrNull { it.id == scene.playerPlaceId }
    val worldPlace = ensurePlace(worldLocation, "当前分支")
    var playerPlaceId = scene.playerPlaceId
    if (playerPlaceId.isBlank() && worldPlace != null) playerPlaceId = worldPlace.id
    if (playerPlaceId.isBlank() && currentPlayer != null) playerPlaceId = currentPlayer.id
    if (playerPlaceId.isNotBlank()) {
        val place = places.firstOrNull { it.id == playerPlaceId }
        actors.removeAll { it.actor == STORY_PLAYER_ACTOR_V1 }
        actors += StoryActorLocationV1(STORY_PLAYER_ACTOR_V1, playerPlaceId, place?.name.orEmpty(), clockMinute)
    }

    offscreen?.actors.orEmpty().forEach { actor ->
        val place = ensurePlace(actor.location, "离场NPC")
        actors.removeAll { it.actor == actor.name }
        actors += StoryActorLocationV1(actor.name, place?.id.orEmpty(), actor.location, clockMinute)
    }
    val playerName = session.playerProfile.name.trim()
    if (playerName.isNotBlank() && playerPlaceId.isNotBlank()) {
        actors.removeAll { it.actor == playerName }
        val place = places.firstOrNull { it.id == playerPlaceId }
        actors += StoryActorLocationV1(playerName, playerPlaceId, place?.name.orEmpty(), clockMinute)
    }
    return scene.copy(
        places = inferStoryPlaceParentsV1(places),
        actorLocations = actors.distinctBy { it.actor }.sortedBy { it.actor },
        playerPlaceId = playerPlaceId,
    )
}

internal fun mergeStoryPlacesV1(input: List<StoryPlaceV1>): List<StoryPlaceV1> {
    val result = mutableListOf<StoryPlaceV1>()
    input.filter { it.name.isNotBlank() }.forEach { place ->
        val names = (place.aliases + place.name).map(::normalizeStoryPlaceNameV1).filter { it.isNotBlank() }.toSet()
        val index = result.indexOfFirst { existing ->
            val existingNames = (existing.aliases + existing.name).map(::normalizeStoryPlaceNameV1).filter { it.isNotBlank() }.toSet()
            names.any { it in existingNames }
        }
        if (index < 0) {
            result += place
        } else {
            val old = result[index]
            result[index] = old.copy(
                aliases = (old.aliases + place.aliases + place.name.takeIf { normalizeStoryPlaceNameV1(place.name) != normalizeStoryPlaceNameV1(old.name) }.orEmpty())
                    .filter { it.isNotBlank() }.distinct().take(12),
                description = old.description.ifBlank { place.description },
                canonChapter = listOf(old.canonChapter, place.canonChapter).filter { it > 0 }.minOrNull() ?: 0,
                source = if (old.source == "手动") old.source else place.source.takeIf { it == "手动" } ?: old.source,
            )
        }
    }
    return result.sortedWith(compareBy<StoryPlaceV1> { it.canonChapter.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.name })
}

internal fun inferStoryPlaceParentsV1(places: List<StoryPlaceV1>): List<StoryPlaceV1> = places.map { child ->
    if (child.parentId.isNotBlank()) return@map child
    val childNorm = normalizeStoryPlaceNameV1(child.name)
    val parent = places.asSequence()
        .filter { it.id != child.id }
        .map { it to normalizeStoryPlaceNameV1(it.name) }
        .filter { (_, parentNorm) -> parentNorm.length >= 2 && childNorm.length > parentNorm.length && childNorm.contains(parentNorm) }
        .maxByOrNull { (_, parentNorm) -> parentNorm.length }
        ?.first
    if (parent == null) child else child.copy(parentId = parent.id)
}

internal fun inferCanonicalRoutesV1(
    canon: OriginalCanonArchiveV1?,
    anchorChapter: Int,
    places: List<StoryPlaceV1>,
): List<StoryRouteEdgeV1> {
    val edges = mutableListOf<StoryRouteEdgeV1>()
    places.filter { it.parentId.isNotBlank() }.forEach { child ->
        if (places.any { it.id == child.parentId }) {
            edges += StoryRouteEdgeV1(
                fromId = child.parentId,
                toId = child.id,
                minutes = 2,
                mode = StoryTravelModeV1.WALK,
                bidirectional = true,
                description = "父子地点层级",
                source = "层级推断",
            )
        }
    }
    val events = canon?.digests.orEmpty()
        .filter { it.chapterNumber <= anchorChapter }
        .flatMap { it.events }
        .filter { it.location.isNotBlank() }
        .sortedWith(compareBy<CanonEventObservationV1> { it.chapterNumber }.thenBy { it.partIndex })
    val byActor = mutableMapOf<String, MutableList<CanonEventObservationV1>>()
    events.forEach { event -> event.participants.forEach { actor -> byActor.getOrPut(actor) { mutableListOf() } += event } }
    byActor.values.forEach { sequence ->
        sequence.zipWithNext().forEach { (a, b) ->
            if (a.location == b.location || b.chapterNumber - a.chapterNumber > 2) return@forEach
            val from = resolveStoryPlaceByListV1(places, a.location) ?: return@forEach
            val to = resolveStoryPlaceByListV1(places, b.location) ?: return@forEach
            if (from.id == to.id) return@forEach
            edges += StoryRouteEdgeV1(
                fromId = from.id,
                toId = to.id,
                minutes = if (a.chapterNumber == b.chapterNumber) 15 else 30,
                mode = StoryTravelModeV1.CUSTOM,
                bidirectional = true,
                description = "同一角色在原著相邻事件中出现于两地，仅作为最低路程参考",
                source = "原著事件推断",
            )
        }
    }
    return compactStoryRoutesV1(edges).take(MAX_STORY_ROUTES_V1)
}

internal fun findShortestStoryRouteV1(scene: StorySpatialSceneV1, fromId: String, toId: String): StoryRoutePathV1? {
    if (fromId == toId) return StoryRoutePathV1(listOf(fromId), emptyList(), 0)
    if (scene.places.none { it.id == fromId } || scene.places.none { it.id == toId }) return null
    val distances = mutableMapOf<String, Int>().withDefault { Int.MAX_VALUE }
    val previousPlace = mutableMapOf<String, String>()
    val previousEdge = mutableMapOf<String, String>()
    val unvisited = scene.places.map { it.id }.toMutableSet()
    distances[fromId] = 0

    while (unvisited.isNotEmpty()) {
        val current = unvisited.minByOrNull { distances.getValue(it) } ?: break
        val currentDistance = distances.getValue(current)
        if (currentDistance == Int.MAX_VALUE) break
        unvisited.remove(current)
        if (current == toId) break
        scene.routes.forEach { edge ->
            val neighbor = when {
                edge.fromId == current -> edge.toId
                edge.bidirectional && edge.toId == current -> edge.fromId
                else -> null
            } ?: return@forEach
            if (neighbor !in unvisited) return@forEach
            val nextDistance = currentDistance + edge.minutes.coerceAtLeast(1)
            if (nextDistance < distances.getValue(neighbor)) {
                distances[neighbor] = nextDistance
                previousPlace[neighbor] = current
                previousEdge[neighbor] = edge.id
            }
        }
    }
    val total = distances.getValue(toId)
    if (total == Int.MAX_VALUE) return null
    val places = mutableListOf(toId)
    val edges = mutableListOf<String>()
    var cursor = toId
    while (cursor != fromId) {
        val prev = previousPlace[cursor] ?: return null
        val edge = previousEdge[cursor] ?: return null
        edges += edge
        places += prev
        cursor = prev
    }
    return StoryRoutePathV1(places.reversed(), edges.reversed(), total)
}

internal fun evaluateStorySpatialMoveV1(scene: StorySpatialSceneV1, fromId: String, toId: String, actualMinutes: Int): StorySpatialMoveCheckV1 {
    if (fromId == toId) return StorySpatialMoveCheckV1(true, 0, "仍在同一地点")
    val path = findShortestStoryRouteV1(scene, fromId, toId)
        ?: return StorySpatialMoveCheckV1(false, 0, "空间图中没有从当前位置通往目标地点的路线")
    return if (actualMinutes >= path.totalMinutes) {
        StorySpatialMoveCheckV1(true, path.totalMinutes, "路线最少 ${path.totalMinutes} 分钟，本轮实际 ${actualMinutes} 分钟")
    } else {
        StorySpatialMoveCheckV1(false, path.totalMinutes, "路线至少需要 ${path.totalMinutes} 分钟，但本轮只经过 ${actualMinutes} 分钟")
    }
}

internal fun extractStorySpatialTargetV1(turn: StoryPlayTurn): String? = turn.variablesAfter
    .lastOrNull { it.subject.trim() == STORY_SPATIAL_SUBJECT_V1 && it.field.trim() == STORY_SPATIAL_TARGET_FIELD_V1 }
    ?.value?.trim()?.takeIf { it.isNotBlank() }

internal fun renderStorySpatialDirectorNoteV1(scene: StorySpatialSceneV1): String = buildString {
    append(STORY_SPATIAL_NOTE_START_V1).append('\n')
    val current = scene.places.firstOrNull { it.id == scene.playerPlaceId }
    append("玩家当前空间位置：").append(current?.name ?: "未定位").append('\n')
    val recentDecision = scene.decisions.lastOrNull()
    if (recentDecision != null && !recentDecision.accepted) {
        append("上一轮移动校验失败：").append(recentDecision.reason).append("。不得把失败目标当作已经到达。\n")
    }
    val actorLines = scene.actorLocations.filter { it.actor != STORY_PLAYER_ACTOR_V1 }.take(16)
    if (actorLines.isNotEmpty()) {
        append("角色空间位置（只有同地或剧情明确建立远程通信时才能直接互动）：\n")
        actorLines.forEach { actor ->
            val place = scene.places.firstOrNull { it.id == actor.placeId }
            append("- ").append(actor.actor).append(" @ ").append(place?.name ?: actor.rawLocation.ifBlank { "未知" }).append('\n')
        }
    }
    if (current != null) {
        val reachable = scene.places.asSequence()
            .filter { it.id != current.id }
            .mapNotNull { place -> findShortestStoryRouteV1(scene, current.id, place.id)?.let { place to it } }
            .sortedBy { it.second.totalMinutes }
            .take(14)
            .toList()
        if (reachable.isNotEmpty()) {
            append("从当前位置可达的最短路程：\n")
            reachable.forEach { (place, path) ->
                val names = path.placeIds.mapNotNull { id -> scene.places.firstOrNull { it.id == id }?.name }
                append("- ").append(place.name).append("：至少 ").append(path.totalMinutes).append(" 分钟")
                if (names.size > 2) append("｜路径=").append(names.joinToString(" → "))
                append('\n')
            }
        }
    }
    append("空间硬规则：世界.地点只能沿空间图中的可达路线改变；不存在路线时不得瞬移。跨地点移动的 STORY_CLOCK 本轮耗时必须不少于最短路程。\n")
    append("空间硬规则：人物不在同一地点时，不能直接看见/听见/参与对方现场行为；离场 NPC 的后台地点同样受此约束。\n")
    append("DM 输出协议：若本轮确实跨地点移动，stateChanges 必须额外加入 subject=\"").append(STORY_SPATIAL_SUBJECT_V1)
        .append("\"，field=\"").append(STORY_SPATIAL_TARGET_FIELD_V1)
        .append("\"，after=空间图中的准确目标地点名，evidence=移动依据。未完成移动时不要把世界.地点改成终点。\n")
    append(STORY_SPATIAL_NOTE_END_V1)
}

internal fun mergeStorySpatialDirectorNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_SPATIAL_NOTE_START_V1) + ".*?" + Regex.escape(STORY_SPATIAL_NOTE_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    return listOf(stripped, block.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

internal fun normalizeStoryPlaceNameV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s，。！？、,.!?：:；;‘’“”\"'（）()【】\\[\\]<>《》·_-]+"), "")
    .removeSuffix("内")
    .removeSuffix("里")
    .take(160)

internal fun inferStoryPlaceKindV1(name: String): StoryPlaceKindV1 = when {
    listOf("室", "房", "卧室", "客厅", "厨房", "走廊", "大厅", "办公室", "病房").any(name::contains) -> StoryPlaceKindV1.ROOM
    listOf("楼", "医院", "学校", "酒店", "公司", "大厦", "车站", "机场", "商场", "宅", "馆").any(name::contains) -> StoryPlaceKindV1.BUILDING
    listOf("市", "城", "县", "镇").any(name::contains) -> StoryPlaceKindV1.CITY
    listOf("省", "国", "洲", "地区").any(name::contains) -> StoryPlaceKindV1.REGION
    listOf("街", "路", "区", "公园", "广场", "小区", "村").any(name::contains) -> StoryPlaceKindV1.AREA
    else -> StoryPlaceKindV1.UNKNOWN
}

private fun resolveStoryPlaceByListV1(places: List<StoryPlaceV1>, raw: String): StoryPlaceV1? {
    val needle = normalizeStoryPlaceNameV1(raw)
    if (needle.isBlank()) return null
    return places.firstOrNull { place ->
        (place.aliases + place.name).any { normalizeStoryPlaceNameV1(it) == needle }
    }
}

internal fun resolveStoryPlaceV1(scene: StorySpatialSceneV1, raw: String): StoryPlaceV1? = resolveStoryPlaceByListV1(scene.places, raw)

private fun sameStoryRouteEndpointsV1(a: StoryRouteEdgeV1, b: StoryRouteEdgeV1): Boolean =
    (a.fromId == b.fromId && a.toId == b.toId) ||
        (a.bidirectional && b.bidirectional && a.fromId == b.toId && a.toId == b.fromId)

private fun compactStoryRoutesV1(routes: List<StoryRouteEdgeV1>): List<StoryRouteEdgeV1> {
    val result = mutableListOf<StoryRouteEdgeV1>()
    routes.filter { it.fromId != it.toId }.sortedBy { it.minutes }.forEach { edge ->
        val index = result.indexOfFirst { sameStoryRouteEndpointsV1(it, edge) }
        if (index < 0) result += edge else if (edge.minutes < result[index].minutes || edge.source == "手动") result[index] = edge
    }
    return result.take(MAX_STORY_ROUTES_V1)
}

@Composable
fun StoryPlayPanelV13(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val offVm: NpcOffscreenViewModelV1 = viewModel()
    val off by offVm.state.collectAsStateWithLifecycle()
    val clockVm: StoryClockViewModelV1 = viewModel()
    val clock by clockVm.state.collectAsStateWithLifecycle()
    val temporalVm: StoryDmTemporalViewModelV1 = viewModel()
    val temporal by temporalVm.state.collectAsStateWithLifecycle()
    val spatialVm: StorySpatialViewModelV1 = viewModel()
    val spatial by spatialVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val world = storyState.runtime?.world
    val scene = spatial.scene
    var showSpatial by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id) {
        val active = session ?: return@LaunchedEffect
        spatialVm.open(
            novelId = book.id,
            session = active,
            worldLocation = world?.location.orEmpty(),
            offscreen = off.scene,
            clockMinute = clock.scene?.currentMinute ?: 0L,
        )
    }

    LaunchedEffect(session?.id, world?.location, off.syncToken, off.scene?.actors, clock.scene?.currentMinute) {
        val active = session ?: return@LaunchedEffect
        spatialVm.syncRuntime(active, world?.location.orEmpty(), off.scene, clock.scene?.currentMinute ?: 0L)
    }

    val latestTurnId = session?.turns?.lastOrNull()?.id
    LaunchedEffect(session?.id, latestTurnId, temporal.scene?.revision, clock.scene?.currentMinute) {
        val active = session ?: return@LaunchedEffect
        spatialVm.observeTurn(
            session = active,
            worldLocation = storyState.runtime?.world?.location.orEmpty(),
            temporal = temporal.scene,
            clockMinute = clock.scene?.currentMinute ?: 0L,
        )
    }

    LaunchedEffect(scene?.revision, world?.notes, storyState.busy) {
        val spatialScene = spatial.scene ?: return@LaunchedEffect
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy) return@LaunchedEffect
        val note = renderStorySpatialDirectorNoteV1(spatialScene)
        var notes = mergeStorySpatialDirectorNoteV1(currentWorld.notes, note)
        var location = currentWorld.location
        val last = spatialScene.decisions.lastOrNull()
        val currentTurnId = session?.turns?.lastOrNull()?.id
        if (last != null && last.turnId == currentTurnId && !last.accepted) {
            val expected = spatialScene.places.firstOrNull { it.id == spatialScene.playerPlaceId }?.name.orEmpty()
            if (expected.isNotBlank() && normalizeStoryPlaceNameV1(location) != normalizeStoryPlaceNameV1(expected)) {
                location = expected
            }
        }
        if (notes != currentWorld.notes || location != currentWorld.location) {
            storyVm.updateWorld(currentWorld.copy(location = location, notes = notes))
        }
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV12(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )
        SmallFloatingActionButton(
            onClick = { showSpatial = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 454.dp),
        ) {
            Icon(Icons.Rounded.Map, "故事空间")
        }
    }

    if (showSpatial && session != null && scene != null) {
        StorySpatialDialogV1(
            scene = scene,
            notice = spatial.notice,
            onDismiss = { showSpatial = false },
            onRebuild = { spatialVm.rebuildFromCanon(session, world?.location.orEmpty(), off.scene, clock.scene?.currentMinute ?: 0L) },
            onAuto = spatialVm::setAutoSeedCanon,
            onAddPlace = spatialVm::addPlace,
            onDeletePlace = spatialVm::deletePlace,
            onAddRoute = spatialVm::addRoute,
            onDeleteRoute = spatialVm::deleteRoute,
            onSetPlayer = { id ->
                spatialVm.setPlayerPlace(id)
                val place = scene.places.firstOrNull { it.id == id }
                val currentWorld = storyState.runtime?.world
                if (place != null && currentWorld != null) storyVm.updateWorld(currentWorld.copy(location = place.name))
            },
        )
    }
}

@Composable
private fun StorySpatialDialogV1(
    scene: StorySpatialSceneV1,
    notice: String?,
    onDismiss: () -> Unit,
    onRebuild: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onAddPlace: (String, StoryPlaceKindV1, String, String) -> Unit,
    onDeletePlace: (String) -> Unit,
    onAddRoute: (String, String, Int, StoryTravelModeV1, Boolean, String) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onSetPlayer: (String) -> Unit,
) {
    var showPlace by remember { mutableStateOf(false) }
    var showRoute by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val current = scene.places.firstOrNull { it.id == scene.playerPlaceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Map, null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("故事空间 · 路程")
                    Text("当前位置：${current?.name ?: "未定位"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 690.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("原著地点自动补齐", fontWeight = FontWeight.Bold)
                        Text("只读取锚点第 ${scene.anchorChapter} 章及之前。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(scene.autoSeedCanon, onAuto)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onRebuild) { Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(5.dp)); Text("重建") }
                    Button({ showPlace = true }) { Icon(Icons.Rounded.AddLocationAlt, null); Spacer(Modifier.width(5.dp)); Text("地点") }
                    Button({ showRoute = true }, enabled = scene.places.size >= 2) { Icon(Icons.Rounded.Route, null); Spacer(Modifier.width(5.dp)); Text("路线") }
                }
                if (!notice.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("地点 ${scene.places.size}") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("路线 ${scene.routes.size}") })
                    Tab(tab == 2, { tab = 2 }, text = { Text("移动校验") })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (tab) {
                        0 -> items(scene.places, key = { it.id }) { place ->
                            val parent = scene.places.firstOrNull { it.id == place.parentId }
                            val actors = scene.actorLocations.filter { it.placeId == place.id && it.actor != STORY_PLAYER_ACTOR_V1 }.map { it.actor }
                            Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (place.id == scene.playerPlaceId) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(place.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(place.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    val detail = buildList {
                                        if (parent != null) add("上级=${parent.name}")
                                        if (place.canonChapter > 0) add("原著第${place.canonChapter}章起")
                                        add(place.source)
                                    }.joinToString(" · ")
                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (actors.isNotEmpty()) Text("角色：${actors.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton({ onSetPlayer(place.id) }, enabled = place.id != scene.playerPlaceId) { Text("设为当前位置") }
                                        if (place.source == "手动") TextButton({ onDeletePlace(place.id) }) { Text("删除") }
                                    }
                                }
                            }
                        }
                        1 -> items(scene.routes, key = { it.id }) { edge ->
                            val from = scene.places.firstOrNull { it.id == edge.fromId }?.name ?: "?"
                            val to = scene.places.firstOrNull { it.id == edge.toId }?.name ?: "?"
                            Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                    Text("$from ${if (edge.bidirectional) "↔" else "→"} $to", fontWeight = FontWeight.Bold)
                                    Text("${edge.minutes} 分钟 · ${edge.mode.label} · ${edge.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (edge.description.isNotBlank()) Text(edge.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (edge.source == "手动") TextButton({ onDeleteRoute(edge.id) }) { Text("删除路线") }
                                }
                            }
                        }
                        else -> {
                            val decisions = scene.decisions.asReversed().take(20)
                            if (decisions.isEmpty()) item { Text("还没有跨地点移动记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(decisions, key = { it.turnId }) { d ->
                                val from = scene.places.firstOrNull { it.id == d.fromPlaceId }?.name ?: "未定位"
                                val to = scene.places.firstOrNull { it.id == d.toPlaceId }?.name ?: "未知目标"
                                Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                                    Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (d.accepted) Icons.Rounded.CheckCircle else Icons.Rounded.Block, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("$from → $to", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(if (d.accepted) "通过" else "拦截", color = if (d.accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        }
                                        Text("实际 ${d.actualMinutes} 分钟 · 最少 ${d.requiredMinutes} 分钟", style = MaterialTheme.typography.bodySmall)
                                        Text(d.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    if (showPlace) {
        AddStoryPlaceDialogV1(scene.places, { showPlace = false }) { name, kind, parentId, description ->
            onAddPlace(name, kind, parentId, description)
            showPlace = false
        }
    }
    if (showRoute) {
        AddStoryRouteDialogV1(scene.places, { showRoute = false }) { from, to, minutes, mode, bidirectional, description ->
            onAddRoute(from, to, minutes, mode, bidirectional, description)
            showRoute = false
        }
    }
}

@Composable
private fun AddStoryPlaceDialogV1(
    places: List<StoryPlaceV1>,
    onDismiss: () -> Unit,
    onSubmit: (String, StoryPlaceKindV1, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(StoryPlaceKindV1.UNKNOWN) }
    var parentId by remember { mutableStateOf("") }
    var kindMenu by remember { mutableStateOf(false) }
    var parentMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("地点名") }, singleLine = true)
                Box {
                    OutlinedButton({ kindMenu = true }, Modifier.fillMaxWidth()) { Text("类型：${kind.label}") }
                    DropdownMenu(kindMenu, { kindMenu = false }) {
                        StoryPlaceKindV1.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, { kind = item; kindMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton({ parentMenu = true }, Modifier.fillMaxWidth()) {
                        Text("上级地点：${places.firstOrNull { it.id == parentId }?.name ?: "无"}")
                    }
                    DropdownMenu(parentMenu, { parentMenu = false }) {
                        DropdownMenuItem({ Text("无") }, { parentId = ""; parentMenu = false })
                        places.forEach { place -> DropdownMenuItem({ Text(place.name) }, { parentId = place.id; parentMenu = false }) }
                    }
                }
                OutlinedTextField(description, { description = it }, label = { Text("说明（可选）") }, minLines = 2)
            }
        },
        confirmButton = { Button({ onSubmit(name, kind, parentId, description) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddStoryRouteDialogV1(
    places: List<StoryPlaceV1>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, StoryTravelModeV1, Boolean, String) -> Unit,
) {
    var fromId by remember { mutableStateOf(places.firstOrNull()?.id.orEmpty()) }
    var toId by remember { mutableStateOf(places.getOrNull(1)?.id.orEmpty()) }
    var minutesText by remember { mutableStateOf("10") }
    var mode by remember { mutableStateOf(StoryTravelModeV1.WALK) }
    var bidirectional by remember { mutableStateOf(true) }
    var description by remember { mutableStateOf("") }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增路线") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton({ fromMenu = true }, Modifier.fillMaxWidth()) { Text("起点：${places.firstOrNull { it.id == fromId }?.name ?: "选择"}") }
                    DropdownMenu(fromMenu, { fromMenu = false }) { places.forEach { p -> DropdownMenuItem({ Text(p.name) }, { fromId = p.id; fromMenu = false }) } }
                }
                Box {
                    OutlinedButton({ toMenu = true }, Modifier.fillMaxWidth()) { Text("终点：${places.firstOrNull { it.id == toId }?.name ?: "选择"}") }
                    DropdownMenu(toMenu, { toMenu = false }) { places.forEach { p -> DropdownMenuItem({ Text(p.name) }, { toId = p.id; toMenu = false }) } }
                }
                OutlinedTextField(minutesText, { minutesText = it.filter(Char::isDigit).take(5) }, label = { Text("最少耗时（分钟）") }, singleLine = true)
                Box {
                    OutlinedButton({ modeMenu = true }, Modifier.fillMaxWidth()) { Text("方式：${mode.label}") }
                    DropdownMenu(modeMenu, { modeMenu = false }) {
                        StoryTravelModeV1.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, { mode = item; modeMenu = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("双向可通行", Modifier.weight(1f))
                    Switch(bidirectional, { bidirectional = it })
                }
                OutlinedTextField(description, { description = it }, label = { Text("路线说明（可选）") })
            }
        },
        confirmButton = {
            val minutes = minutesText.toIntOrNull() ?: 0
            Button({ onSubmit(fromId, toId, minutes, mode, bidirectional, description) }, enabled = fromId.isNotBlank() && toId.isNotBlank() && fromId != toId && minutes > 0) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private const val STORY_PLAYER_ACTOR_V1 = "@PLAYER"
internal const val STORY_SPATIAL_SUBJECT_V1 = "STORY_SPACE"
internal const val STORY_SPATIAL_TARGET_FIELD_V1 = "移动目标"
private const val STORY_SPATIAL_NOTE_START_V1 = "【故事空间约束｜导演层】"
private const val STORY_SPATIAL_NOTE_END_V1 = "【/故事空间约束】"
private const val MAX_STORY_PLACES_V1 = 240
private const val MAX_STORY_ROUTES_V1 = 360
