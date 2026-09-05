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
data class StoryDmTemporalDecisionV1(
    val turnId: String,
    val durationMinutes: Int,
    val source: String,
    val baseClockMinute: Long,
    val expectedClockMinute: Long,
    val applied: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StoryDmTemporalSceneV1(
    val sessionId: String,
    val autoTiming: Boolean = true,
    val lastSeenTurnId: String = "",
    val decisions: List<StoryDmTemporalDecisionV1> = emptyList(),
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StoryDmTemporalArchiveV1(
    val novelId: String,
    val scenes: List<StoryDmTemporalSceneV1> = emptyList(),
)

data class StoryDmTemporalUiStateV1(
    val novelId: String = "",
    val scene: StoryDmTemporalSceneV1? = null,
    val scenes: List<StoryDmTemporalSceneV1> = emptyList(),
    val notice: String? = null,
)

class StoryDmTemporalViewModelV1(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryDmTemporalUiStateV1())
    val state: StateFlow<StoryDmTemporalUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) return
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val seeded = existing ?: StoryDmTemporalSceneV1(
            sessionId = session.id,
            lastSeenTurnId = session.turns.lastOrNull()?.id.orEmpty(),
        )
        val scenes = if (archive.scenes.any { it.sessionId == session.id }) archive.scenes else archive.scenes + seeded
        _state.value = StoryDmTemporalUiStateV1(novelId = novelId, scene = seeded, scenes = scenes)
        saveArchive(StoryDmTemporalArchiveV1(novelId, scenes))
    }

    fun setAutoTiming(enabled: Boolean) = mutate(if (enabled) "DM 自动耗时已开启" else "DM 自动耗时已关闭") { scene ->
        scene.copy(autoTiming = enabled)
    }

    fun observeTurn(session: StoryPlaySession, clock: StoryClockSceneV1) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val turn = session.turns.lastOrNull() ?: return
        if (turn.id == scene.lastSeenTurnId) {
            reconcile(clock)
            return
        }
        if (!scene.autoTiming) {
            persistScene(scene.copy(lastSeenTurnId = turn.id, revision = scene.revision + 1), "本轮未自动推进故事时间")
            return
        }
        val dmDuration = extractStoryDmTurnDurationV1(turn)
        val duration = dmDuration ?: inferStoryTurnDurationV1(turn.player, turn.narration)
        val decision = StoryDmTemporalDecisionV1(
            turnId = turn.id,
            durationMinutes = duration,
            source = if (dmDuration != null) "DM" else "本地回退",
            baseClockMinute = clock.currentMinute,
            expectedClockMinute = clock.currentMinute + duration,
        )
        persistScene(
            scene.copy(
                lastSeenTurnId = turn.id,
                decisions = (scene.decisions + decision).distinctBy { it.turnId }.takeLast(120),
                revision = scene.revision + 1,
            ),
            "本轮耗时 ${duration} 分钟${if (dmDuration == null) "（DM 未返回耗时，已使用本地估算）" else ""}",
        )
    }

    fun reconcile(clock: StoryClockSceneV1) {
        val scene = _state.value.scene ?: return
        val updated = scene.decisions.map { decision ->
            if (!decision.applied && clock.currentMinute >= decision.expectedClockMinute) decision.copy(applied = true) else decision
        }
        if (updated != scene.decisions) {
            persistScene(scene.copy(decisions = updated, revision = scene.revision + 1), null)
        }
    }

    private fun mutate(message: String?, transform: (StoryDmTemporalSceneV1) -> StoryDmTemporalSceneV1) {
        val scene = _state.value.scene ?: return
        persistScene(transform(scene).copy(revision = scene.revision + 1), message)
    }

    private fun persistScene(updated: StoryDmTemporalSceneV1, notice: String?) {
        val current = _state.value
        val normalized = updated.copy(updatedAt = System.currentTimeMillis())
        val scenes = current.scenes.map { if (it.sessionId == normalized.sessionId) normalized else it }
            .let { list -> if (list.any { it.sessionId == normalized.sessionId }) list else list + normalized }
        _state.update { it.copy(scene = normalized, scenes = scenes, notice = notice) }
        if (current.novelId.isNotBlank()) saveArchive(StoryDmTemporalArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_dm_temporal_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StoryDmTemporalArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryDmTemporalArchiveV1(novelId)
        return runCatching { json.decodeFromString(StoryDmTemporalArchiveV1.serializer(), file.readText()) }
            .getOrElse { StoryDmTemporalArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StoryDmTemporalArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StoryDmTemporalArchiveV1.serializer(), archive)) }
    }
}

internal fun extractStoryDmTurnDurationV1(turn: StoryPlayTurn): Int? {
    val candidates = turn.variablesAfter.filter {
        it.subject.trim() == STORY_DM_CLOCK_SUBJECT_V1 && it.field.trim() == STORY_DM_DURATION_FIELD_V1
    }
    val raw = candidates.lastOrNull()?.value.orEmpty()
    return Regex("\\d{1,4}").find(raw)?.value?.toIntOrNull()?.coerceIn(1, 1_440)
}

internal fun inferStoryTurnDurationV1(player: String, narration: String): Int {
    val text = "$player\n$narration"
    Regex("(\\d{1,3})\\s*分钟").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it.coerceIn(1, 1_440)
    }
    Regex("(\\d{1,2})\\s*(?:个)?小时").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return (it * 60).coerceIn(1, 1_440)
    }
    return when {
        listOf("睡一觉", "睡到", "过夜", "一觉醒来").any(text::contains) -> 480
        listOf("半小时", "半个小时").any(text::contains) -> 30
        listOf("片刻后", "一会儿后", "稍等").any(text::contains) -> 5
        listOf("前往", "赶往", "出发去", "开车去", "坐车去", "步行去", "回到").any(text::contains) -> 20
        listOf("搜索", "搜查", "调查", "翻找", "检查房间").any(text::contains) -> 10
        listOf("战斗", "打斗", "交手", "追逐", "搏斗").any(text::contains) -> 3
        listOf("问道", "说道", "回答", "交谈", "聊天", "对话").any(text::contains) -> 2
        else -> 5
    }
}

internal fun pendingStoryDmClockAdvanceV1(
    temporal: StoryDmTemporalSceneV1,
    clock: StoryClockSceneV1,
): Int {
    val decision = temporal.decisions.lastOrNull { !it.applied } ?: return 0
    if (clock.currentMinute >= decision.expectedClockMinute) return 0
    return (decision.expectedClockMinute - clock.currentMinute).coerceAtMost(1_440L).toInt()
}

internal fun renderStoryDmTemporalDirectorNoteV1(
    clock: StoryClockSceneV1,
    temporal: StoryDmTemporalSceneV1,
): String = buildString {
    append(STORY_DM_TEMPORAL_START_V1).append('\n')
    append("当前故事时间：").append(storyClockDisplayLabelV1(clock)).append("（内部分钟=").append(clock.currentMinute).append("）\n")
    temporal.decisions.lastOrNull()?.let { last ->
        append("上一轮实际耗时：").append(last.durationMinutes).append(" 分钟｜来源=").append(last.source).append('\n')
    }
    val pending = clock.events.filter { it.status == StoryClockEventStatusV1.PENDING }.sortedBy { it.dueMinute }.take(10)
    if (pending.isEmpty()) {
        append("等待中的因果：无\n")
    } else {
        append("等待中的因果（导演约束，不等于任何角色已经知道）：\n")
        pending.forEach { event ->
            append("- [").append(formatStoryClockTimeV1(clock.anchorTimeLabel, event.dueMinute)).append("] ")
            append(event.visibility.label).append("｜")
            if (event.owner.isNotBlank()) append("owner=").append(event.owner).append("｜")
            if (event.location.isNotBlank()) append(event.location).append("｜")
            append(event.title).append("：").append(event.summary.take(220)).append('\n')
        }
    }
    append("时间硬规则：未到触发时间的赶路、消息、约定、倒计时和延迟后果，不得提前写成已经完成；禁止跨地点瞬移。\n")
    append("信息硬规则：私有/定向排程只是导演层约束，未实际接收该消息的角色不得知道其内容。\n")
    append("DM 输出协议：每轮都必须在 stateChanges 额外加入 subject=\"").append(STORY_DM_CLOCK_SUBJECT_V1)
        .append("\"，field=\"").append(STORY_DM_DURATION_FIELD_V1)
        .append("\"，after=本轮真实经过分钟数的整数（1-1440），evidence=耗时依据。对话通常1-5分钟，搜索/处理事务按实际耗时，跨地点移动必须按路程耗时；不要用世界.时间字段代替这个耗时元数据。\n")
    append(STORY_DM_TEMPORAL_END_V1)
}

internal fun mergeStoryDmTemporalDirectorNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_DM_TEMPORAL_START_V1) + ".*?" + Regex.escape(STORY_DM_TEMPORAL_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    return listOf(stripped, block.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

@Composable
fun StoryPlayPanelV12(
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
    val temporalVm: StoryDmTemporalViewModelV1 = viewModel()
    val temporal by temporalVm.state.collectAsStateWithLifecycle()

    val session = storyState.active
    val clockScene = clock.scene
    val temporalScene = temporal.scene
    var showClock by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id) {
        val active = session ?: return@LaunchedEffect
        clockVm.open(book.id, active, storyState.runtime?.world?.time.orEmpty())
        temporalVm.open(book.id, active)
    }

    val latestTurnId = session?.turns?.lastOrNull()?.id
    LaunchedEffect(session?.id, latestTurnId, clockScene?.sessionId, temporalScene?.sessionId) {
        val active = session ?: return@LaunchedEffect
        val c = clock.scene ?: return@LaunchedEffect
        if (c.sessionId != active.id || temporal.scene?.sessionId != active.id) return@LaunchedEffect
        temporalVm.observeTurn(active, c)
    }

    LaunchedEffect(temporalScene?.revision, clockScene?.revision, clock.busy) {
        val t = temporal.scene ?: return@LaunchedEffect
        val c = clock.scene ?: return@LaunchedEffect
        if (clock.busy) return@LaunchedEffect
        temporalVm.reconcile(c)
        val remaining = pendingStoryDmClockAdvanceV1(t, c)
        if (remaining > 0) clockVm.advance(remaining)
    }

    LaunchedEffect(clockScene?.revision, temporalScene?.revision, storyState.runtime?.world?.notes, storyState.busy) {
        val c = clock.scene ?: return@LaunchedEffect
        val t = temporal.scene ?: return@LaunchedEffect
        val world = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy) return@LaunchedEffect
        val block = renderStoryDmTemporalDirectorNoteV1(c, t)
        val notes = mergeStoryDmTemporalDirectorNoteV1(world.notes, block)
        val label = storyClockDisplayLabelV1(c)
        if (notes != world.notes || world.time != label) {
            storyVm.updateWorld(world.copy(time = label, notes = notes))
        }
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
                if (owner in knownNames) memoryVm.addMemory(owner, memoryText, privacy, importance = 4)
            }
        }
        val world = storyState.runtime?.world
        if (world != null) {
            val notesWithPublic = mergeStoryClockPublicNoteV1(world.notes, renderStoryClockPublicNoteV1(scene))
            val notes = temporal.scene?.let { mergeStoryDmTemporalDirectorNoteV1(notesWithPublic, renderStoryDmTemporalDirectorNoteV1(scene, it)) }
                ?: notesWithPublic
            if (notes != world.notes) storyVm.updateWorld(world.copy(time = storyClockDisplayLabelV1(scene), notes = notes))
        }
        clockVm.markDelivered(fired.map { it.id })
    }

    LaunchedEffect(
        latestTurnId,
        temporalScene?.decisions,
        clockScene?.currentMinute,
        clockScene?.lastPlannerInputKey,
        storyState.busy,
        clock.busy,
        off.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val c = clock.scene ?: return@LaunchedEffect
        val latest = latestTurnId ?: return@LaunchedEffect
        val decision = temporal.scene?.decisions?.lastOrNull { it.turnId == latest } ?: return@LaunchedEffect
        if (!decision.applied || storyState.busy || clock.busy || off.busy || !aiReady) return@LaunchedEffect
        clockVm.planDelayedConsequences(
            book = book,
            session = active,
            runtime = storyState.runtime,
            offscreen = off.scene,
            allowedNames = life.scene?.states?.map { it.name }?.toSet().orEmpty() + active.playerProfile.name.takeIf { it.isNotBlank() }.orEmpty(),
            force = false,
        )
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
            Icon(Icons.Rounded.Schedule, "故事时间与因果")
        }
    }

    if (showClock && session != null && clockScene != null && temporalScene != null) {
        StoryDmTemporalDialogV1(
            scene = clockScene,
            temporal = temporalScene,
            aiReady = aiReady,
            busy = clock.busy,
            notice = temporal.notice ?: clock.notice,
            onDismiss = { showClock = false },
            onAutoTiming = temporalVm::setAutoTiming,
            onAnchor = clockVm::setAnchorTimeLabel,
            onAdvance = clockVm::advance,
            onCancel = clockVm::cancelEvent,
            onPlan = {
                clockVm.planDelayedConsequences(
                    book = book,
                    session = session,
                    runtime = storyState.runtime,
                    offscreen = off.scene,
                    allowedNames = life.scene?.states?.map { it.name }?.toSet().orEmpty() + session.playerProfile.name.takeIf { it.isNotBlank() }.orEmpty(),
                    force = true,
                )
            },
            onSchedule = clockVm::schedule,
        )
    }
}

@Composable
private fun StoryDmTemporalDialogV1(
    scene: StoryClockSceneV1,
    temporal: StoryDmTemporalSceneV1,
    aiReady: Boolean,
    busy: Boolean,
    notice: String?,
    onDismiss: () -> Unit,
    onAutoTiming: (Boolean) -> Unit,
    onAnchor: (String) -> Unit,
    onAdvance: (Int) -> Unit,
    onCancel: (String) -> Unit,
    onPlan: () -> Unit,
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
) {
    var anchor by remember(scene.anchorTimeLabel) { mutableStateOf(scene.anchorTimeLabel) }
    var addEvent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("故事时间 · DM 因果")
                    Text(storyClockDisplayLabelV1(scene), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
                OutlinedTextField(
                    value = anchor,
                    onValueChange = { anchor = it },
                    label = { Text("时间锚点，例如 14:30 / 深夜") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton({ onAnchor(anchor) }) { Icon(Icons.Rounded.Check, "保存") } },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DM 自动计算本轮耗时", fontWeight = FontWeight.Bold)
                        Text("DM 会把实际经过分钟数写入隐藏导演协议；缺失时用本地估算。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(temporal.autoTiming, onAutoTiming)
                }
                temporal.decisions.lastOrNull()?.let { last ->
                    Text("上一轮：${last.durationMinutes} 分钟 · ${last.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        AssistChip(onClick = { onAdvance(minutes) }, label = { Text("+$minutes 分") }, enabled = !busy)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPlan, enabled = aiReady && !busy) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(5.dp))
                        Text("AI 重算延迟后果")
                    }
                    OutlinedButton(onClick = { addEvent = true }, enabled = !busy) {
                        Icon(Icons.Rounded.AddAlarm, null)
                        Spacer(Modifier.width(5.dp))
                        Text("手动排程")
                    }
                }
                if (!notice.isNullOrBlank()) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                val pending = scene.events.filter { it.status == StoryClockEventStatusV1.PENDING }.sortedBy { it.dueMinute }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("等待中的因果 · ${pending.size}", fontWeight = FontWeight.Bold) }
                    if (pending.isEmpty()) item { Text("目前没有未到时的事件。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(pending, key = { it.id }) { event ->
                        Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatStoryClockTimeV1(scene.anchorTimeLabel, event.dueMinute), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text(event.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton({ onCancel(event.id) }, enabled = !busy) { Icon(Icons.Rounded.Close, "取消") }
                                }
                                Text("${event.visibility.label}${event.owner.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}${event.location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                                Text(event.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("完成") } },
    )

    if (addEvent) {
        StoryDmQuickScheduleDialogV1(
            onDismiss = { addEvent = false },
            onSubmit = { title, summary, delay, visibility ->
                onSchedule(title, summary, delay, visibility, "", "", emptyList(), "", emptyList(), "作者手动排程")
                addEvent = false
            },
        )
    }
}

@Composable
private fun StoryDmQuickScheduleDialogV1(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, StoryClockEventVisibilityV1) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("15") }
    var visibility by remember { mutableStateOf(StoryClockEventVisibilityV1.PUBLIC) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动加入延迟事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
                OutlinedTextField(summary, { summary = it }, label = { Text("到时发生什么") }, minLines = 2)
                OutlinedTextField(delay, { delay = it.filter(Char::isDigit).take(5) }, label = { Text("多少分钟后") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoryClockEventVisibilityV1.entries.forEach { item ->
                        FilterChip(selected = visibility == item, onClick = { visibility = item }, label = { Text(item.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(title, summary, delay.toIntOrNull()?.coerceIn(1, 43_200) ?: 15, visibility) },
                enabled = title.isNotBlank() && summary.isNotBlank(),
            ) { Text("加入") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private const val STORY_DM_CLOCK_SUBJECT_V1 = "导演时钟"
private const val STORY_DM_DURATION_FIELD_V1 = "本轮耗时分钟"
private const val STORY_DM_TEMPORAL_START_V1 = "【故事时钟与行动约束｜导演层】"
private const val STORY_DM_TEMPORAL_END_V1 = "【/故事时钟与行动约束】"
