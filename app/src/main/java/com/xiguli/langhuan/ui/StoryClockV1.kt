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
enum class StoryClockEventVisibilityV1 {
    PRIVATE,
    TARGETED,
    PUBLIC;

    val label: String get() = when (this) {
        PRIVATE -> "私有"
        TARGETED -> "定向"
        PUBLIC -> "公开"
    }
}

@Serializable
enum class StoryClockEventStatusV1 {
    PENDING,
    FIRED,
    CANCELLED;

    val label: String get() = when (this) {
        PENDING -> "等待"
        FIRED -> "已发生"
        CANCELLED -> "已取消"
    }
}

@Serializable
data class StoryClockEventV1(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val summary: String,
    val consequence: String = "",
    val location: String = "",
    val owner: String = "",
    val participants: List<String> = emptyList(),
    val visibility: StoryClockEventVisibilityV1 = StoryClockEventVisibilityV1.PUBLIC,
    val dueMinute: Long,
    val createdMinute: Long = 0L,
    val prerequisiteEventIds: List<String> = emptyList(),
    val sourceEventId: String = "",
    val status: StoryClockEventStatusV1 = StoryClockEventStatusV1.PENDING,
    val firedMinute: Long? = null,
    val evidence: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StoryClockSceneV1(
    val sessionId: String,
    val anchorTimeLabel: String = "",
    val currentMinute: Long = 0L,
    val minutesPerTurn: Int = 5,
    val autoAdvance: Boolean = true,
    val lastObservedTurnId: String = "",
    val events: List<StoryClockEventV1> = emptyList(),
    val deliveredEventIds: List<String> = emptyList(),
    val lastPlannerInputKey: String = "",
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StoryClockArchiveV1(
    val novelId: String,
    val scenes: List<StoryClockSceneV1> = emptyList(),
)

data class StoryClockUiStateV1(
    val novelId: String = "",
    val scene: StoryClockSceneV1? = null,
    val scenes: List<StoryClockSceneV1> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val syncToken: Long = 0L,
)

internal data class StoryClockPlanChangeV1(
    val visibility: StoryClockEventVisibilityV1,
    val delayMinutes: Int,
    val location: String,
    val owner: String,
    val participants: List<String>,
    val title: String,
    val summary: String,
    val consequence: String,
    val prerequisiteIds: List<String>,
    val evidence: String = "",
)

class StoryClockViewModelV1(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryClockUiStateV1())
    val state: StateFlow<StoryClockUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession, worldTime: String) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) return
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val seeded = existing ?: StoryClockSceneV1(
            sessionId = session.id,
            anchorTimeLabel = worldTime.trim(),
            lastObservedTurnId = session.turns.lastOrNull()?.id.orEmpty(),
        )
        val scenes = if (archive.scenes.any { it.sessionId == session.id }) archive.scenes else archive.scenes + seeded
        _state.value = StoryClockUiStateV1(novelId = novelId, scene = seeded, scenes = scenes)
        saveArchive(StoryClockArchiveV1(novelId, scenes))
    }

    fun setAutoAdvance(enabled: Boolean) = mutate("故事时钟自动推进已修改") {
        it.copy(autoAdvance = enabled)
    }

    fun setMinutesPerTurn(value: Int) = mutate("每回合故事时间已修改") {
        it.copy(minutesPerTurn = value.coerceIn(1, 60))
    }

    fun setAnchorTimeLabel(value: String) = mutate("故事时间锚点已保存") {
        it.copy(anchorTimeLabel = value.trim().take(120))
    }

    fun observeTurn(session: StoryPlaySession) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val latest = session.turns.lastOrNull()?.id.orEmpty()
        if (latest.isBlank() || latest == scene.lastObservedTurnId) return
        val next = if (scene.autoAdvance) {
            advanceStoryClockV1(scene, scene.minutesPerTurn.toLong())
        } else scene
        persistScene(
            next.copy(
                lastObservedTurnId = latest,
                revision = next.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun advance(minutes: Int) {
        val scene = _state.value.scene ?: return
        if (minutes <= 0) return
        persistScene(
            advanceStoryClockV1(scene, minutes.toLong()).copy(
                revision = scene.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
            "故事时间推进了 ${minutes} 分钟",
        )
    }

    fun schedule(
        title: String,
        summary: String,
        delayMinutes: Int,
        visibility: StoryClockEventVisibilityV1,
        location: String = "",
        owner: String = "",
        participants: List<String> = emptyList(),
        consequence: String = "",
        prerequisiteEventIds: List<String> = emptyList(),
        evidence: String = "作者手动排程",
    ) {
        val scene = _state.value.scene ?: return
        val cleanTitle = title.trim()
        val cleanSummary = summary.trim()
        if (cleanTitle.isBlank() || cleanSummary.isBlank()) return
        val event = StoryClockEventV1(
            title = cleanTitle.take(160),
            summary = cleanSummary.take(520),
            consequence = consequence.trim().take(520),
            location = location.trim().take(180),
            owner = owner.trim().take(120),
            participants = participants.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8),
            visibility = visibility,
            dueMinute = scene.currentMinute + delayMinutes.coerceIn(1, 43_200),
            createdMinute = scene.currentMinute,
            prerequisiteEventIds = prerequisiteEventIds.distinct().take(8),
            evidence = evidence.take(320),
        )
        persistScene(
            fireDueClockEventsV1(scene.copy(events = compactStoryClockEventsV1(scene.events + event))).copy(
                revision = scene.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
            "已加入延迟事件：${event.title}",
        )
    }

    fun cancelEvent(id: String) = mutate("延迟事件已取消") { scene ->
        scene.copy(events = scene.events.map { event ->
            if (event.id == id && event.status == StoryClockEventStatusV1.PENDING) {
                event.copy(status = StoryClockEventStatusV1.CANCELLED)
            } else event
        })
    }

    fun markDelivered(ids: Collection<String>) {
        if (ids.isEmpty()) return
        mutate(null) { scene ->
            scene.copy(deliveredEventIds = (scene.deliveredEventIds + ids).distinct().takeLast(320))
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun planDelayedConsequences(
        book: ReaderBookUi,
        session: StoryPlaySession,
        runtime: StoryRuntimeSessionV3?,
        offscreen: NpcOffscreenSceneV1?,
        allowedNames: Set<String>,
        force: Boolean = false,
    ) {
        val scene = _state.value.scene ?: return
        if (_state.value.busy || scene.sessionId != session.id) return
        val latestTurn = session.turns.lastOrNull()
        val recentOffscreen = offscreen?.events?.takeLast(8).orEmpty()
        val inputKey = buildString {
            append(session.id).append('|').append(latestTurn?.id.orEmpty()).append('|')
            append(scene.currentMinute).append('|')
            append(recentOffscreen.joinToString(",") { it.id })
        }
        if (!force && inputKey == scene.lastPlannerInputKey) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching {
                val gateway = activeGateway()
                val world = runtime?.world ?: StoryWorldStateV3()
                val pending = scene.events.filter { it.status == StoryClockEventStatusV1.PENDING }
                    .take(12)
                    .joinToString("\n") { event ->
                        "- id=${event.id}｜T+${event.dueMinute}分｜${event.visibility.label}｜${event.title}｜${event.summary}"
                    }.ifBlank { "无" }
                val turns = session.turns.takeLast(6).joinToString("\n\n") { turn ->
                    "玩家：${turn.player.ifBlank { "（无）" }}\n剧情：${turn.narration.takeLast(900)}"
                }.ifBlank { "暂无新回合" }
                val offText = recentOffscreen.joinToString("\n") { event ->
                    "- id=${event.id}｜拍${event.beat}｜${event.visibility.label}｜${event.owner}｜${event.location.ifBlank { "地点未知" }}｜${event.summary}${event.result.takeIf { it.isNotBlank() }?.let { "；$it" }.orEmpty()}"
                }.ifBlank { "无" }

                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是琅嬛的“故事时钟与因果排程器”，不写正文，只判断已经发生的行为会不会在未来产生延迟结果。
                            当前分支锚定原著第 ${session.anchorChapter} 章。严禁使用该章之后的原著信息，严禁为了戏剧性硬造后果。

                            规则：
                            1. 只有需要真实耗时的事情才排程，例如赶路、等人、约定、倒计时、消息传递、任务截止、伤势/天气/机关的延迟变化。
                            2. 当场已经发生的结果不要重复排程。
                            3. 不允许瞬移；路程和消息传播必须给合理的分钟数。
                            4. 私有事件只能属于 owner；定向事件只送达 participants；公开事件才允许进入共享世界状态。
                            5. 只能使用允许名单内的具名角色；未来原著角色不得出现。
                            6. 如果某个后果必须等另一个已排程事件发生，填写依赖事件 id；没有可靠依赖就留空。
                            7. 同一因果不要重复创建多个近义排程。没有必要的延迟后果时 stateChanges 可以为空。

                            必须返回 GeneratedChapter JSON：title="story-clock"；content="ok"；summary=一句排程说明；touchedForeshadowingIds=[]。
                            stateChanges 的 subject 固定为 "CLOCK"，field 只能是：
                            - “延迟:公开”
                            - “延迟:定向”
                            - “延迟:私有”
                            after 必须严格为：
                            +分钟||地点||owner||参与者1,参与者2||标题||摘要||后果||依赖事件id1,依赖事件id2
                            例：+25||医院走廊||程野||程野,顾宁||程野抵达医院||程野完成赶路到达医院||他可以进入下一场景||
                        """.trimIndent(),
                        user = """
                            作品：《${book.title}》｜分支：${session.title}
                            当前故事时间：${storyClockDisplayLabelV1(scene)}（内部分钟=${scene.currentMinute}）
                            当前世界：地点=${world.location.ifBlank { "未记录" }}；时间=${world.time.ifBlank { scene.anchorTimeLabel.ifBlank { "未记录" } }}；局势=${world.situation.ifBlank { "未记录" }}

                            【允许角色】
                            ${allowedNames.joinToString("、").ifBlank { "无具名角色" }}

                            【最近玩家/剧情回合】
                            $turns

                            【最近离场事件】
                            $offText

                            【当前等待中的排程】
                            $pending
                        """.trimIndent(),
                    )
                )

                val changes = output.stateChanges.mapNotNull { change ->
                    if (change.subject.trim() != "CLOCK") return@mapNotNull null
                    val visibility = when (change.field.trim()) {
                        "延迟:公开" -> StoryClockEventVisibilityV1.PUBLIC
                        "延迟:定向" -> StoryClockEventVisibilityV1.TARGETED
                        "延迟:私有" -> StoryClockEventVisibilityV1.PRIVATE
                        else -> return@mapNotNull null
                    }
                    parseStoryClockPlanChangeV1(
                        value = change.after,
                        visibility = visibility,
                        allowedNames = allowedNames,
                        validDependencyIds = scene.events.map { it.id }.toSet() + recentOffscreen.map { it.id }.toSet(),
                        evidence = change.evidence,
                    )
                }
                applyStoryClockPlanChangesV1(scene, changes).copy(
                    lastPlannerInputKey = inputKey,
                    revision = scene.revision + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                persistScene(updated, "故事时钟已推演延迟后果")
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "因果排程失败") }
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

    private fun mutate(message: String?, transform: (StoryClockSceneV1) -> StoryClockSceneV1) {
        val current = _state.value
        val scene = current.scene ?: return
        if (current.busy) return
        persistScene(
            fireDueClockEventsV1(transform(scene)).copy(
                revision = scene.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
            message,
        )
    }

    private fun persistScene(updated: StoryClockSceneV1, notice: String? = null) {
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
        if (current.novelId.isNotBlank()) saveArchive(StoryClockArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_clock_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StoryClockArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryClockArchiveV1(novelId)
        return runCatching { json.decodeFromString(StoryClockArchiveV1.serializer(), file.readText()) }
            .getOrElse { StoryClockArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StoryClockArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StoryClockArchiveV1.serializer(), archive)) }
    }
}

internal fun advanceStoryClockV1(scene: StoryClockSceneV1, minutes: Long): StoryClockSceneV1 {
    if (minutes <= 0L) return fireDueClockEventsV1(scene)
    return fireDueClockEventsV1(scene.copy(currentMinute = scene.currentMinute + minutes))
}

internal fun fireDueClockEventsV1(scene: StoryClockSceneV1): StoryClockSceneV1 {
    var events = scene.events
    var changed: Boolean
    do {
        changed = false
        val firedIds = events.filter { it.status == StoryClockEventStatusV1.FIRED }.map { it.id }.toSet()
        events = events.map { event ->
            if (
                event.status == StoryClockEventStatusV1.PENDING &&
                event.dueMinute <= scene.currentMinute &&
                event.prerequisiteEventIds.all { it in firedIds }
            ) {
                changed = true
                event.copy(status = StoryClockEventStatusV1.FIRED, firedMinute = scene.currentMinute)
            } else event
        }
    } while (changed)
    return scene.copy(events = events)
}

internal fun storyClockDisplayLabelV1(scene: StoryClockSceneV1): String =
    formatStoryClockTimeV1(scene.anchorTimeLabel, scene.currentMinute)

internal fun formatStoryClockTimeV1(anchor: String, offsetMinutes: Long): String {
    val clean = anchor.trim()
    val match = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)").find(clean)
    if (match != null) {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val total = hour * 60L + minute + offsetMinutes
        val day = Math.floorDiv(total, 1_440L)
        val minuteOfDay = Math.floorMod(total, 1_440L).toInt()
        val hh = minuteOfDay / 60
        val mm = minuteOfDay % 60
        val clock = "%02d:%02d".format(hh, mm)
        return when {
            day > 0 -> "第 +${day} 天 $clock"
            day < 0 -> "第 ${day} 天 $clock"
            else -> clock
        }
    }
    if (offsetMinutes <= 0L) return clean.ifBlank { "故事锚点" }
    val hours = offsetMinutes / 60
    val mins = offsetMinutes % 60
    val delta = buildString {
        if (hours > 0) append(hours).append("小时")
        if (mins > 0 || hours == 0L) append(mins).append("分钟")
    }
    return "${clean.ifBlank { "故事锚点" }} + $delta"
}

internal fun applyStoryClockPlanChangesV1(
    scene: StoryClockSceneV1,
    changes: List<StoryClockPlanChangeV1>,
): StoryClockSceneV1 {
    var events = scene.events
    changes.forEach { change ->
        val normalized = normalizeStoryClockTextV1(change.title + change.summary)
        val duplicate = events.any { existing ->
            existing.status == StoryClockEventStatusV1.PENDING &&
                normalizeStoryClockTextV1(existing.title + existing.summary) == normalized
        }
        if (duplicate) return@forEach
        events += StoryClockEventV1(
            title = change.title.take(160),
            summary = change.summary.take(520),
            consequence = change.consequence.take(520),
            location = change.location.take(180),
            owner = change.owner.take(120),
            participants = change.participants.distinct().take(8),
            visibility = change.visibility,
            dueMinute = scene.currentMinute + change.delayMinutes.coerceIn(1, 43_200),
            createdMinute = scene.currentMinute,
            prerequisiteEventIds = change.prerequisiteIds.distinct().take(8),
            evidence = change.evidence.take(320),
        )
    }
    return fireDueClockEventsV1(scene.copy(events = compactStoryClockEventsV1(events)))
}

internal fun parseStoryClockPlanChangeV1(
    value: String,
    visibility: StoryClockEventVisibilityV1,
    allowedNames: Set<String>,
    validDependencyIds: Set<String>,
    evidence: String = "",
): StoryClockPlanChangeV1? {
    val parts = value.split("||")
    if (parts.size < 6) return null
    val delay = parts.getOrNull(0).orEmpty().filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 43_200) ?: return null
    val location = parts.getOrNull(1).orEmpty().trim()
    val ownerRaw = parts.getOrNull(2).orEmpty().trim()
    val owner = ownerRaw.takeIf { it.isBlank() || it in allowedNames }.orEmpty()
    if (visibility == StoryClockEventVisibilityV1.PRIVATE && owner.isBlank()) return null
    val participants = parts.getOrNull(3).orEmpty()
        .split(',', '，', '、')
        .map { it.trim() }
        .filter { it.isNotBlank() && it in allowedNames }
        .plus(owner.takeIf { it.isNotBlank() }.orEmpty())
        .filter { it.isNotBlank() }
        .distinct()
        .take(8)
    if (visibility == StoryClockEventVisibilityV1.TARGETED && participants.isEmpty()) return null
    val title = parts.getOrNull(4).orEmpty().trim()
    val summary = parts.getOrNull(5).orEmpty().trim()
    if (title.isBlank() || summary.isBlank()) return null
    val consequence = parts.getOrNull(6).orEmpty().trim()
    val deps = parts.getOrNull(7).orEmpty()
        .split(',', '，', '、')
        .map { it.trim() }
        .filter { it.isNotBlank() && it in validDependencyIds }
        .distinct()
        .take(8)
    return StoryClockPlanChangeV1(
        visibility = visibility,
        delayMinutes = delay,
        location = location.take(180),
        owner = owner,
        participants = participants,
        title = title.take(160),
        summary = summary.take(520),
        consequence = consequence.take(520),
        prerequisiteIds = deps,
        evidence = evidence.take(320),
    )
}

internal fun clockDeliveryRecipientsV1(event: StoryClockEventV1): List<Pair<String, NpcMemoryPrivacyV1>> = when (event.visibility) {
    StoryClockEventVisibilityV1.PRIVATE -> listOfNotNull(
        event.owner.takeIf { it.isNotBlank() }?.let { it to NpcMemoryPrivacyV1.PRIVATE },
    )
    StoryClockEventVisibilityV1.TARGETED -> (event.participants + event.owner)
        .filter { it.isNotBlank() }
        .distinct()
        .map { it to NpcMemoryPrivacyV1.PRIVATE }
    StoryClockEventVisibilityV1.PUBLIC -> event.participants
        .filter { it.isNotBlank() }
        .distinct()
        .map { it to NpcMemoryPrivacyV1.PUBLIC }
}

internal fun renderStoryClockPublicNoteV1(scene: StoryClockSceneV1): String {
    val publicEvents = scene.events
        .filter { it.status == StoryClockEventStatusV1.FIRED && it.visibility == StoryClockEventVisibilityV1.PUBLIC }
        .takeLast(10)
    if (publicEvents.isEmpty()) return ""
    return buildString {
        append(STORY_CLOCK_PUBLIC_START_V1).append('\n')
        publicEvents.forEach { event ->
            append("- [").append(formatStoryClockTimeV1(scene.anchorTimeLabel, event.firedMinute ?: event.dueMinute)).append("] ")
            if (event.location.isNotBlank()) append(event.location).append("｜")
            append(event.title).append("：").append(event.summary)
            if (event.consequence.isNotBlank()) append("；后果=").append(event.consequence)
            append('\n')
        }
        append(STORY_CLOCK_PUBLIC_END_V1)
    }
}

internal fun mergeStoryClockPublicNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_CLOCK_PUBLIC_START_V1) + ".*?" + Regex.escape(STORY_CLOCK_PUBLIC_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    if (block.isBlank()) return stripped
    return listOf(stripped, block).filter { it.isNotBlank() }.joinToString("\n\n")
}

private fun compactStoryClockEventsV1(events: List<StoryClockEventV1>): List<StoryClockEventV1> = events
    .distinctBy { it.id }
    .sortedWith(compareBy<StoryClockEventV1> { it.dueMinute }.thenBy { it.createdAt })
    .takeLast(MAX_STORY_CLOCK_EVENTS_V1)

private fun normalizeStoryClockTextV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s，。！？、,.!?：:；;‘’“”\"'（）()【】\\[\\]-]+"), "")
    .take(360)

@Composable
fun StoryPlayPanelV11(
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
    val clockVm: StoryClockViewModelV1 = viewModel()
    val clock by clockVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val clockScene = clock.scene
    var showClock by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id) {
        val active = session ?: return@LaunchedEffect
        clockVm.open(book.id, active, storyState.runtime?.world?.time.orEmpty())
    }

    val latestTurnId = session?.turns?.lastOrNull()?.id
    LaunchedEffect(session?.id, latestTurnId, clockScene?.sessionId) {
        val active = session ?: return@LaunchedEffect
        if (clockScene?.sessionId == active.id) clockVm.observeTurn(active)
    }

    LaunchedEffect(
        clock.syncToken,
        clockScene?.events,
        memory.scene?.sessionId,
        memory.busy,
        storyState.busy,
        off.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val scene = clock.scene ?: return@LaunchedEffect
        if (scene.sessionId != active.id || memory.busy || storyState.busy || off.busy) return@LaunchedEffect
        val fired = scene.events.filter {
            it.status == StoryClockEventStatusV1.FIRED && it.id !in scene.deliveredEventIds
        }
        if (fired.isEmpty()) return@LaunchedEffect

        val knownNames = life.scene?.states?.map { it.name }?.toSet().orEmpty()
        fired.forEach { event ->
            val memoryText = buildString {
                append(event.title).append("：").append(event.summary)
                if (event.consequence.isNotBlank()) append("；").append(event.consequence)
                if (event.location.isNotBlank()) append("（地点：").append(event.location).append("）")
                append("［").append(formatStoryClockTimeV1(scene.anchorTimeLabel, event.firedMinute ?: scene.currentMinute)).append("］")
            }
            clockDeliveryRecipientsV1(event).forEach { (owner, privacy) ->
                if (owner in knownNames) {
                    memoryVm.addMemory(owner, memoryText, privacy, importance = 4)
                }
            }
        }

        val world = storyState.runtime?.world
        if (world != null) {
            val mergedNotes = mergeStoryClockPublicNoteV1(world.notes, renderStoryClockPublicNoteV1(scene))
            if (mergedNotes != world.notes) storyVm.updateWorld(world.copy(notes = mergedNotes))
        }
        clockVm.markDelivered(fired.map { it.id })
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV10(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )

        SmallFloatingActionButton(
            onClick = { showClock = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 334.dp),
        ) {
            if (clock.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Schedule, "故事时钟")
        }
    }

    if (showClock && session != null && clockScene != null) {
        StoryClockDialogV1(
            scene = clockScene,
            aiReady = aiReady,
            busy = clock.busy,
            error = clock.error,
            notice = clock.notice,
            knownNames = life.scene?.states?.map { it.name }?.distinct().orEmpty(),
            onDismiss = { showClock = false },
            onAuto = clockVm::setAutoAdvance,
            onMinutesPerTurn = clockVm::setMinutesPerTurn,
            onAnchor = clockVm::setAnchorTimeLabel,
            onAdvance = clockVm::advance,
            onSchedule = clockVm::schedule,
            onCancel = clockVm::cancelEvent,
            onPlan = {
                clockVm.planDelayedConsequences(
                    book = book,
                    session = session,
                    runtime = storyState.runtime,
                    offscreen = off.scene,
                    allowedNames = life.scene?.states?.map { it.name }?.toSet().orEmpty(),
                    force = true,
                )
            },
        )
    }
}

@Composable
private fun StoryClockDialogV1(
    scene: StoryClockSceneV1,
    aiReady: Boolean,
    busy: Boolean,
    error: String?,
    notice: String?,
    knownNames: List<String>,
    onDismiss: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onMinutesPerTurn: (Int) -> Unit,
    onAnchor: (String) -> Unit,
    onAdvance: (Int) -> Unit,
    onSchedule: (
        String,
        String,
        Int,
        StoryClockEventVisibilityV1,
        String,
        String,
        List<String>,
        String,
        List<String>,
        String,
    ) -> Unit,
    onCancel: (String) -> Unit,
    onPlan: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var anchorDraft by remember(scene.anchorTimeLabel) { mutableStateOf(scene.anchorTimeLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("故事时钟 · 因果队列")
                    Text(storyClockDisplayLabelV1(scene), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
                OutlinedTextField(
                    value = anchorDraft,
                    onValueChange = { anchorDraft = it },
                    label = { Text("时间锚点，例如 14:30 / 深夜") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton({ onAnchor(anchorDraft) }) { Icon(Icons.Rounded.Check, "保存时间锚点") }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每个故事回合自动走 ${scene.minutesPerTurn} 分钟", fontWeight = FontWeight.Bold)
                        Text("同一回合只推进一次，切回页面不会重复计时。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(scene.autoAdvance, onAuto)
                }
                Slider(
                    value = scene.minutesPerTurn.toFloat(),
                    onValueChange = { onMinutesPerTurn(it.toInt()) },
                    valueRange = 1f..30f,
                    steps = 28,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        AssistChip(
                            onClick = { onAdvance(minutes) },
                            label = { Text("+$minutes 分") },
                            enabled = !busy,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAdd = true }, enabled = !busy) {
                        Icon(Icons.Rounded.AddAlarm, null)
                        Spacer(Modifier.width(6.dp))
                        Text("新增排程")
                    }
                    FilledTonalButton(onClick = onPlan, enabled = aiReady && !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(6.dp))
                        Text("AI 推演后果")
                    }
                }
                if (!aiReady) {
                    Text("AI 未连接时仍可手动排程和推进时钟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (!notice.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                val pending = scene.events.filter { it.status == StoryClockEventStatusV1.PENDING }
                    .sortedBy { it.dueMinute }
                val history = scene.events.filter { it.status != StoryClockEventStatusV1.PENDING }
                    .sortedByDescending { it.firedMinute ?: it.dueMinute }
                    .take(12)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    item {
                        Text("等待中的因果 · ${pending.size}", fontWeight = FontWeight.Bold)
                    }
                    if (pending.isEmpty()) {
                        item { Text("当前没有等待触发的延迟事件。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(pending, key = { it.id }) { event ->
                        StoryClockEventCardV1(scene, event, onCancel)
                    }
                    if (history.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text("最近历史", fontWeight = FontWeight.Bold)
                        }
                        items(history, key = { "history-${it.id}" }) { event ->
                            StoryClockEventCardV1(scene, event, onCancel)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("完成") } },
    )

    if (showAdd) {
        AddStoryClockEventDialogV1(
            knownNames = knownNames,
            onDismiss = { showAdd = false },
            onSubmit = { title, summary, delay, visibility, location, owner, participants, consequence ->
                onSchedule(
                    title,
                    summary,
                    delay,
                    visibility,
                    location,
                    owner,
                    participants,
                    consequence,
                    emptyList(),
                    "作者手动排程",
                )
                showAdd = false
            },
        )
    }
}

@Composable
private fun StoryClockEventCardV1(
    scene: StoryClockSceneV1,
    event: StoryClockEventV1,
    onCancel: (String) -> Unit,
) {
    Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (event.status) {
                        StoryClockEventStatusV1.PENDING -> Icons.Rounded.HourglassBottom
                        StoryClockEventStatusV1.FIRED -> Icons.Rounded.Bolt
                        StoryClockEventStatusV1.CANCELLED -> Icons.Rounded.Block
                    },
                    null,
                    Modifier.size(19.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(event.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                AssistChip(onClick = {}, label = { Text(event.visibility.label) })
            }
            Text(
                "${event.status.label} · ${formatStoryClockTimeV1(scene.anchorTimeLabel, event.dueMinute)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(event.summary, style = MaterialTheme.typography.bodySmall)
            if (event.consequence.isNotBlank()) {
                Text("后果：${event.consequence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (event.location.isNotBlank() || event.participants.isNotEmpty()) {
                Text(
                    listOfNotNull(
                        event.location.takeIf { it.isNotBlank() }?.let { "地点 $it" },
                        event.participants.takeIf { it.isNotEmpty() }?.joinToString("、")?.let { "涉及 $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (event.prerequisiteEventIds.isNotEmpty()) {
                Text("等待 ${event.prerequisiteEventIds.size} 个前置因果", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (event.status == StoryClockEventStatusV1.PENDING) {
                TextButton(onClick = { onCancel(event.id) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("取消排程")
                }
            }
        }
    }
}

@Composable
private fun AddStoryClockEventDialogV1(
    knownNames: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, StoryClockEventVisibilityV1, String, String, List<String>, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var consequence by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var delayText by remember { mutableStateOf("30") }
    var owner by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(StoryClockEventVisibilityV1.PUBLIC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增延迟事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(summary, { summary = it }, label = { Text("届时发生什么") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(consequence, { consequence = it }, label = { Text("触发后的后果（可选）") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        delayText,
                        { delayText = it.filter(Char::isDigit).take(5) },
                        label = { Text("多少分钟后") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(location, { location = it }, label = { Text("地点") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoryClockEventVisibilityV1.entries.forEach { option ->
                        FilterChip(
                            selected = visibility == option,
                            onClick = { visibility = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (visibility != StoryClockEventVisibilityV1.PUBLIC) {
                    OutlinedTextField(
                        owner,
                        { owner = it },
                        label = { Text(if (visibility == StoryClockEventVisibilityV1.PRIVATE) "私有事件所属角色" else "发起/所属角色") },
                        singleLine = true,
                        supportingText = { if (knownNames.isNotEmpty()) Text("已有：${knownNames.take(8).joinToString("、")}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (visibility == StoryClockEventVisibilityV1.TARGETED || visibility == StoryClockEventVisibilityV1.PUBLIC) {
                    OutlinedTextField(
                        participants,
                        { participants = it },
                        label = { Text("涉及/接收角色，用逗号分隔") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            val delay = delayText.toIntOrNull()?.coerceIn(1, 43_200) ?: 0
            val parsedParticipants = participants.split(',', '，', '、').map { it.trim() }.filter { it.isNotBlank() }.distinct()
            Button(
                onClick = { onSubmit(title, summary, delay, visibility, location, owner, parsedParticipants, consequence) },
                enabled = title.isNotBlank() && summary.isNotBlank() && delay > 0 &&
                    (visibility != StoryClockEventVisibilityV1.PRIVATE || owner.isNotBlank()),
            ) { Text("加入队列") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private const val MAX_STORY_CLOCK_EVENTS_V1 = 240
private const val STORY_CLOCK_PUBLIC_START_V1 = "【故事时钟公开因果｜导演层】"
private const val STORY_CLOCK_PUBLIC_END_V1 = "【/故事时钟公开因果】"
