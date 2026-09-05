package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import com.xiguli.langhuan.ui.canon.CanonEntityObservationV1
import com.xiguli.langhuan.ui.canon.CanonEntityTypeV1
import com.xiguli.langhuan.ui.canon.CanonEventObservationV1
import com.xiguli.langhuan.ui.canon.CanonKnowledgeObservationV1
import com.xiguli.langhuan.ui.canon.CanonRelationObservationV1
import com.xiguli.langhuan.ui.canon.CanonSourceDigestV1
import com.xiguli.langhuan.ui.canon.OriginalCanonArchiveStoreV1
import com.xiguli.langhuan.ui.canon.OriginalCanonArchiveV1
import com.xiguli.langhuan.ui.reader.LibraryExperienceState
import com.xiguli.langhuan.ui.reader.ReaderBookUi
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoryRoleEntryEventV1(
    val chapter: Int,
    val partIndex: Int,
    val storyTime: String = "",
    val location: String = "",
    val summary: String,
    val consequences: List<String> = emptyList(),
    val evidence: String = "",
)

@Serializable
data class StoryRoleEntrySnapshotV1(
    val sessionId: String,
    val roleName: String,
    val roleIdentity: String,
    val anchorChapter: Int,
    val sourceChapter: Int = 0,
    val sourceKey: String,
    val location: String = "",
    val storyTime: String = "",
    val companions: List<String> = emptyList(),
    val conditionSignals: List<String> = emptyList(),
    val carriedItems: List<String> = emptyList(),
    val declaredGoal: String = "",
    val recentEvents: List<StoryRoleEntryEventV1> = emptyList(),
    val knownFacts: List<String> = emptyList(),
    val relationshipHints: List<String> = emptyList(),
    val evidenceLines: List<String> = emptyList(),
    val autoApply: Boolean = true,
    val appliedKey: String = "",
    val appliedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StoryRoleEntrySnapshotArchiveV1(
    val novelId: String,
    val snapshots: List<StoryRoleEntrySnapshotV1> = emptyList(),
)

data class StoryRoleEntrySnapshotUiStateV1(
    val novelId: String = "",
    val loading: Boolean = false,
    val snapshot: StoryRoleEntrySnapshotV1? = null,
    val snapshots: List<StoryRoleEntrySnapshotV1> = emptyList(),
    val notice: String? = null,
    val error: String? = null,
)

class StoryRoleEntrySnapshotViewModelV1(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val canonStore = OriginalCanonArchiveStoreV1(application)
    private val _state = MutableStateFlow(StoryRoleEntrySnapshotUiStateV1())
    val state: StateFlow<StoryRoleEntrySnapshotUiStateV1> = _state.asStateFlow()
    private var lastLoadKey = ""

    fun open(novelId: String, session: StoryPlaySession) {
        if (novelId.isBlank()) return
        val profile = session.playerProfile
        val loadKey = listOf(novelId, session.id, session.anchorChapter.toString(), profile.name, profile.identity).joinToString("|")
        if (loadKey == lastLoadKey && !_state.value.loading) return
        lastLoadKey = loadKey
        viewModelScope.launch {
            _state.update { it.copy(novelId = novelId, loading = true, error = null, notice = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val archive = loadArchive(novelId)
                    val previous = archive.snapshots.firstOrNull { it.sessionId == session.id }
                    val canon = canonStore.load(novelId)
                    val built = canon?.let { buildStoryRoleEntrySnapshotV1(it, session) }
                    if (built == null) {
                        Triple<StoryRoleEntrySnapshotV1?, List<StoryRoleEntrySnapshotV1>, String?>(
                            null,
                            archive.snapshots.filterNot { it.sessionId == session.id },
                            when {
                                !isCanonStoryRoleIdentityV1(profile.identity) -> null
                                canon == null || canon.digests.isEmpty() -> "这本书还没有可用的原著抽取数据"
                                else -> "当前章节边界内没有找到这个角色的可靠入场证据"
                            },
                        )
                    } else {
                        val sameSource = previous?.sourceKey == built.sourceKey && previous.roleName == built.roleName
                        val merged = built.copy(
                            autoApply = previous?.autoApply ?: true,
                            appliedKey = if (sameSource) previous?.appliedKey.orEmpty() else "",
                            appliedAt = if (sameSource) previous?.appliedAt ?: 0L else 0L,
                        )
                        val snapshots = archive.snapshots.map { if (it.sessionId == session.id) merged else it }
                            .let { list -> if (list.any { it.sessionId == session.id }) list else list + merged }
                        saveArchive(StoryRoleEntrySnapshotArchiveV1(novelId, snapshots))
                        Triple<StoryRoleEntrySnapshotV1?, List<StoryRoleEntrySnapshotV1>, String?>(merged, snapshots, null)
                    }
                }
            }.onSuccess { (snapshot, snapshots, message) ->
                _state.value = StoryRoleEntrySnapshotUiStateV1(
                    novelId = novelId,
                    loading = false,
                    snapshot = snapshot,
                    snapshots = snapshots,
                    notice = message,
                )
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "读取原著角色入场快照失败") }
            }
        }
    }

    fun setAutoApply(enabled: Boolean) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        persist(snapshot.copy(autoApply = enabled), if (enabled) "已开启自动恢复原著入场状态" else "已关闭自动恢复")
    }

    fun markApplied(sourceKey: String, manual: Boolean = false) {
        val snapshot = _state.value.snapshot ?: return
        if (snapshot.sourceKey != sourceKey) return
        if (!manual && snapshot.appliedKey == sourceKey) return
        persist(
            snapshot.copy(appliedKey = sourceKey, appliedAt = System.currentTimeMillis()),
            if (manual) "已重新应用原著角色入场快照" else "已自动恢复原著角色入场状态",
        )
    }

    private fun persist(updated: StoryRoleEntrySnapshotV1, notice: String?) {
        val current = _state.value
        val snapshot = updated.copy(updatedAt = System.currentTimeMillis())
        val snapshots = current.snapshots.map { if (it.sessionId == snapshot.sessionId) snapshot else it }
            .let { list -> if (list.any { it.sessionId == snapshot.sessionId }) list else list + snapshot }
        _state.update { it.copy(snapshot = snapshot, snapshots = snapshots, notice = notice) }
        if (current.novelId.isNotBlank()) saveArchive(StoryRoleEntrySnapshotArchiveV1(current.novelId, snapshots))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_role_entry_snapshot_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StoryRoleEntrySnapshotArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryRoleEntrySnapshotArchiveV1(novelId)
        return runCatching { json.decodeFromString(StoryRoleEntrySnapshotArchiveV1.serializer(), file.readText()) }
            .getOrElse { StoryRoleEntrySnapshotArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StoryRoleEntrySnapshotArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StoryRoleEntrySnapshotArchiveV1.serializer(), archive)) }
    }
}

internal fun buildStoryRoleEntrySnapshotV1(
    canon: OriginalCanonArchiveV1,
    session: StoryPlaySession,
): StoryRoleEntrySnapshotV1? {
    val roleName = session.playerProfile.name.trim()
    if (roleName.isBlank() || !isCanonStoryRoleIdentityV1(session.playerProfile.identity)) return null
    val visible = canon.digests.asSequence()
        .filter { it.chapterNumber in 1..session.anchorChapter }
        .sortedWith(compareBy<CanonSourceDigestV1> { it.chapterNumber }.thenBy { it.partIndex })
        .toList()
    if (visible.isEmpty()) return null

    val aliases = collectStoryRoleEntryAliasesV1(visible, roleName)
    val aliasKeys = aliases.map(::normalizeStoryRoleEntryNameV1).filter { it.isNotBlank() }.toSet()
    fun isRole(value: String): Boolean = normalizeStoryRoleEntryNameV1(value) in aliasKeys

    val roleEvents = visible.flatMap { digest ->
        digest.events.filter { event -> event.participants.any(::isRole) }
    }.sortedWith(compareBy<CanonEventObservationV1> { it.chapterNumber }.thenBy { it.partIndex })

    val roleKnowledge = visible.flatMap { digest ->
        digest.knowledge.filter { isRole(it.character) }
    }.sortedWith(compareBy<CanonKnowledgeObservationV1> { it.chapterNumber }.thenBy { it.partIndex })

    val relationships = visible.flatMap { digest ->
        digest.relations.filter { isRole(it.from) || isRole(it.to) }
    }.sortedWith(compareBy<CanonRelationObservationV1> { it.chapterNumber }.thenBy { it.partIndex })

    if (roleEvents.isEmpty() && roleKnowledge.isEmpty() && relationships.isEmpty()) return null

    val latestLocationEvent = roleEvents.asReversed().firstOrNull { it.location.isNotBlank() }
    val latestTimeEvent = roleEvents.asReversed().firstOrNull { it.storyTime.isNotBlank() }
    val latestEvent = roleEvents.lastOrNull()
    val companions = latestEvent?.participants.orEmpty()
        .filterNot(::isRole)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy(::normalizeStoryRoleEntryNameV1)
        .take(8)

    val explicitItems = visible.flatMap { digest ->
        digest.entities.filter { it.type == CanonEntityTypeV1.ITEM }
    }.filter { item -> storyRoleEntryItemExplicitlyHeldV1(item, aliases) }
        .distinctBy { normalizeStoryRoleEntryTextV1(it.name) }
        .takeLast(10)

    val conditionSignals = buildList {
        roleEvents.takeLast(10).forEach { event ->
            val sourceText = listOf(event.summary, event.consequences.joinToString("；"), event.evidence)
                .filter { it.isNotBlank() }.joinToString("；")
            if (STORY_ROLE_ENTRY_CONDITION_REGEX_V1.containsMatchIn(sourceText)) {
                add("第${event.chapterNumber}章：${sourceText.take(180)}")
            }
        }
        roleKnowledge.takeLast(18).forEach { item ->
            val sourceText = listOf(item.fact, item.evidence).filter { it.isNotBlank() }.joinToString("；")
            if (STORY_ROLE_ENTRY_CONDITION_REGEX_V1.containsMatchIn(sourceText)) {
                add("第${item.chapterNumber}章：${sourceText.take(180)}")
            }
        }
    }.distinctBy(::normalizeStoryRoleEntryTextV1).takeLast(8)

    val declaredGoal = roleKnowledge.asReversed().firstOrNull { item ->
        STORY_ROLE_ENTRY_GOAL_REGEX_V1.containsMatchIn(item.fact)
    }?.let { "第${it.chapterNumber}章：${it.fact.trim()}" }.orEmpty()

    val recentEvents = roleEvents.takeLast(6).map { event ->
        StoryRoleEntryEventV1(
            chapter = event.chapterNumber,
            partIndex = event.partIndex,
            storyTime = event.storyTime.trim(),
            location = event.location.trim(),
            summary = event.summary.trim(),
            consequences = event.consequences.map { it.trim() }.filter { it.isNotBlank() }.take(4),
            evidence = event.evidence.trim().take(180),
        )
    }

    val knownFacts = roleKnowledge.takeLast(20)
        .map { "第${it.chapterNumber}章：${it.fact.trim()}" }
        .filter { it.length > 5 }
        .distinctBy(::normalizeStoryRoleEntryTextV1)

    val relationshipHints = relationships.takeLast(12).map { relation ->
        val other = if (isRole(relation.from)) relation.to else relation.from
        buildString {
            append("第${relation.chapterNumber}章：与").append(other.trim()).append(" · ").append(relation.label.trim())
            if (relation.value.isNotBlank()) append(" · ").append(relation.value.trim())
        }
    }.distinctBy(::normalizeStoryRoleEntryTextV1).takeLast(8)

    val evidenceLines = buildList {
        latestEvent?.evidence?.takeIf { it.isNotBlank() }?.let { add("第${latestEvent.chapterNumber}章事件证据：${it.take(180)}") }
        explicitItems.takeLast(4).forEach { item ->
            item.evidence.takeIf { it.isNotBlank() }?.let { add("第${item.chapterNumber}章物品证据：${it.take(180)}") }
        }
        roleKnowledge.takeLast(4).forEach { item ->
            item.evidence.takeIf { it.isNotBlank() }?.let { add("第${item.chapterNumber}章知识证据：${it.take(180)}") }
        }
    }.distinct().takeLast(10)

    val sourceChapter = listOfNotNull(
        latestEvent?.chapterNumber,
        roleKnowledge.lastOrNull()?.chapterNumber,
        relationships.lastOrNull()?.chapterNumber,
    ).maxOrNull() ?: 0
    val location = latestLocationEvent?.location?.trim().orEmpty()
    val storyTime = latestTimeEvent?.storyTime?.trim().orEmpty()
    val sourceKeySeed = listOf(
        roleName,
        session.anchorChapter.toString(),
        sourceChapter.toString(),
        location,
        storyTime,
        recentEvents.joinToString("|") { "${it.chapter}:${it.partIndex}:${it.summary}" },
        knownFacts.joinToString("|").takeLast(2_000),
    ).joinToString("#")

    return StoryRoleEntrySnapshotV1(
        sessionId = session.id,
        roleName = roleName,
        roleIdentity = session.playerProfile.identity.trim(),
        anchorChapter = session.anchorChapter,
        sourceChapter = sourceChapter,
        sourceKey = sourceKeySeed.hashCode().toString(),
        location = location,
        storyTime = storyTime,
        companions = companions,
        conditionSignals = conditionSignals,
        carriedItems = explicitItems.map { item ->
            buildString {
                append(item.name.trim())
                if (item.description.isNotBlank()) append(" · ").append(item.description.trim().take(100))
            }
        },
        declaredGoal = declaredGoal,
        recentEvents = recentEvents,
        knownFacts = knownFacts,
        relationshipHints = relationshipHints,
        evidenceLines = evidenceLines,
    )
}

internal fun shouldAutoApplyStoryRoleEntrySnapshotV1(
    snapshot: StoryRoleEntrySnapshotV1,
    session: StoryPlaySession,
): Boolean {
    if (!snapshot.autoApply || snapshot.sourceKey == snapshot.appliedKey || session.turns.isNotEmpty()) return false
    if (!isCanonStoryRoleIdentityV1(session.playerProfile.identity)) return false
    return normalizeStoryRoleEntryNameV1(snapshot.roleName) == normalizeStoryRoleEntryNameV1(session.playerProfile.name)
}

internal fun applyStoryRoleEntrySnapshotToWorldV1(
    snapshot: StoryRoleEntrySnapshotV1,
    session: StoryPlaySession,
    world: StoryWorldStateV3,
    force: Boolean,
): StoryWorldStateV3 {
    val defaultChapterTime = "第 ${session.anchorChapter} 章"
    val canReplaceTime = force || world.time.isBlank() || world.time == defaultChapterTime
    val canReplaceSituation = force || world.situation.isBlank() || world.situation == session.anchorTitle
    val latest = snapshot.recentEvents.lastOrNull()
    val situation = latest?.summary?.takeIf { it.isNotBlank() }.orEmpty()
    return world.copy(
        location = if (snapshot.location.isNotBlank() && (force || world.location.isBlank())) snapshot.location else world.location,
        time = if (snapshot.storyTime.isNotBlank() && canReplaceTime) snapshot.storyTime else world.time,
        situation = if (situation.isNotBlank() && canReplaceSituation) situation else world.situation,
    )
}

internal fun renderStoryRoleEntrySnapshotDirectorNoteV1(snapshot: StoryRoleEntrySnapshotV1): String = buildString {
    append(STORY_ROLE_ENTRY_NOTE_START_V1).append('\n')
    append("这是玩家接管原著角色时的“进入瞬间快照”，只允许使用第 ").append(snapshot.anchorChapter)
        .append(" 章及之前的原著证据。它是分支起点基线，不是永远不变的当前状态；一旦故事继续，Story Runtime、时钟、空间、感知、物件和分支事件优先。\n")
    append("接管角色：").append(snapshot.roleName).append(" · ").append(snapshot.roleIdentity).append('\n')
    if (snapshot.sourceChapter > 0) append("最近可靠角色证据：第 ").append(snapshot.sourceChapter).append(" 章。\n")
    if (snapshot.location.isNotBlank()) append("入场最近可靠地点：").append(snapshot.location).append('\n')
    if (snapshot.storyTime.isNotBlank()) append("入场最近可靠故事时间：").append(snapshot.storyTime).append('\n')
    if (snapshot.companions.isNotEmpty()) {
        append("最近同一原著事件中的人物：").append(snapshot.companions.joinToString("、"))
            .append("。这不自动证明分支当前仍与玩家同处一地，当前空间/感知状态优先。\n")
    }
    if (snapshot.conditionSignals.isNotEmpty()) {
        append("身体/处境证据信号：\n")
        snapshot.conditionSignals.takeLast(6).forEach { append("- ").append(it).append('\n') }
        append("这些只是原著证据中明确出现的状态信号，不得扩写成没有证据的诊断或伤势。\n")
    }
    if (snapshot.carriedItems.isNotEmpty()) {
        append("有明确持有/携带证据的物品：\n")
        snapshot.carriedItems.takeLast(8).forEach { append("- ").append(it).append('\n') }
        append("没有明确持有证据的原著物品不能凭空出现在玩家身上。\n")
    }
    if (snapshot.declaredGoal.isNotBlank()) append("最近明确表达的目标/计划：").append(snapshot.declaredGoal).append('\n')
    if (snapshot.recentEvents.isNotEmpty()) {
        append("刚经历的原著事件：\n")
        snapshot.recentEvents.takeLast(5).forEach { event ->
            append("- 第").append(event.chapter).append("章")
            if (event.storyTime.isNotBlank()) append(" · ").append(event.storyTime)
            if (event.location.isNotBlank()) append(" · ").append(event.location)
            append("：").append(event.summary).append('\n')
        }
    }
    if (snapshot.knownFacts.isNotEmpty()) {
        append("角色截至进入点明确已知：\n")
        snapshot.knownFacts.takeLast(12).forEach { append("- ").append(it).append('\n') }
    }
    if (snapshot.relationshipHints.isNotEmpty()) {
        append("截至进入点的关系证据：\n")
        snapshot.relationshipHints.takeLast(6).forEach { append("- ").append(it).append('\n') }
    }
    append("秘密边界：未进入该角色已知账本、未被其亲眼看见/听见/收到的信息仍然未知；第 ")
        .append(snapshot.anchorChapter + 1).append(" 章及之后的原著信息绝对禁止用于提示、预判或安排命运。\n")
    append(STORY_ROLE_ENTRY_NOTE_END_V1)
}

internal fun mergeStoryRoleEntrySnapshotDirectorNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_ROLE_ENTRY_NOTE_START_V1) + ".*?" + Regex.escape(STORY_ROLE_ENTRY_NOTE_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    return listOf(stripped, block.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

private fun collectStoryRoleEntryAliasesV1(visible: List<CanonSourceDigestV1>, roleName: String): List<String> {
    val names = linkedSetOf(roleName.trim())
    repeat(4) {
        val keys = names.map(::normalizeStoryRoleEntryNameV1).filter { it.isNotBlank() }.toSet()
        visible.flatMap { it.entities }
            .filter { it.type == CanonEntityTypeV1.CHARACTER }
            .forEach { entity ->
                val candidateNames = (listOf(entity.name) + entity.aliases).map { it.trim() }.filter { it.isNotBlank() }
                if (candidateNames.any { normalizeStoryRoleEntryNameV1(it) in keys }) names += candidateNames
            }
    }
    return names.toList()
}

private fun storyRoleEntryItemExplicitlyHeldV1(item: CanonEntityObservationV1, aliases: List<String>): Boolean {
    val text = listOf(item.description, item.evidence).filter { it.isNotBlank() }.joinToString("；")
    if (text.isBlank() || !STORY_ROLE_ENTRY_POSSESSION_REGEX_V1.containsMatchIn(text)) return false
    return aliases.any { alias -> alias.isNotBlank() && text.contains(alias, ignoreCase = true) }
}

private fun normalizeStoryRoleEntryNameV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s·•._—-]"), "")
    .trim()

private fun normalizeStoryRoleEntryTextV1(value: String): String = value
    .lowercase()
    .replace(Regex("\\s+"), "")
    .replace(Regex("[，。；：、,.!！?？\"“”'‘’（）()【】\\[\\]-]"), "")
    .trim()

@Composable
fun StoryPlayPanelV17(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val snapshotVm: StoryRoleEntrySnapshotViewModelV1 = viewModel()
    val snapshotState by snapshotVm.state.collectAsStateWithLifecycle()
    val session = storyState.active
    val world = storyState.runtime?.world
    val snapshot = snapshotState.snapshot
    var showSnapshot by remember(book.id, session?.id) { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id, session?.anchorChapter, session?.playerProfile) {
        val active = session ?: return@LaunchedEffect
        snapshotVm.open(book.id, active)
    }

    LaunchedEffect(snapshot?.sourceKey, snapshot?.appliedKey, snapshot?.autoApply, session?.turns?.size, storyState.busy) {
        val active = session ?: return@LaunchedEffect
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        val entry = snapshot ?: return@LaunchedEffect
        if (storyState.busy || !shouldAutoApplyStoryRoleEntrySnapshotV1(entry, active)) return@LaunchedEffect
        val applied = applyStoryRoleEntrySnapshotToWorldV1(entry, active, currentWorld, force = false)
        if (applied != currentWorld) storyVm.updateWorld(applied)
        snapshotVm.markApplied(entry.sourceKey)
    }

    LaunchedEffect(snapshotState.loading, snapshot?.sourceKey, world?.notes, storyState.busy) {
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy || snapshotState.loading) return@LaunchedEffect
        val block = snapshot?.let(::renderStoryRoleEntrySnapshotDirectorNoteV1).orEmpty()
        val notes = mergeStoryRoleEntrySnapshotDirectorNoteV1(currentWorld.notes, block)
        if (notes != currentWorld.notes) storyVm.updateWorld(currentWorld.copy(notes = notes))
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV16(book, libraryState, aiReady, onAiSetup, onAdopted)
        SmallFloatingActionButton(
            onClick = { showSnapshot = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 646.dp),
        ) {
            Icon(Icons.Rounded.History, "原著角色入场快照")
        }
    }

    if (showSnapshot) {
        StoryRoleEntrySnapshotDialogV1(
            loading = snapshotState.loading,
            snapshot = snapshot,
            notice = snapshotState.notice,
            error = snapshotState.error,
            onDismiss = { showSnapshot = false },
            onAutoApply = snapshotVm::setAutoApply,
            onApply = {
                val active = session
                val currentWorld = storyState.runtime?.world
                val entry = snapshot
                if (active != null && currentWorld != null && entry != null) {
                    val applied = applyStoryRoleEntrySnapshotToWorldV1(entry, active, currentWorld, force = true)
                    if (applied != currentWorld) storyVm.updateWorld(applied)
                    snapshotVm.markApplied(entry.sourceKey, manual = true)
                }
            },
        )
    }
}

@Composable
private fun StoryRoleEntrySnapshotDialogV1(
    loading: Boolean,
    snapshot: StoryRoleEntrySnapshotV1?,
    notice: String?,
    error: String?,
    onDismiss: () -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onApply: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("原著角色 · 入场快照") },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                snapshot == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(error ?: notice ?: "先选择一个原著角色，并确保已经完成原著抽取。")
                    Text(
                        "入场快照只从当前进入章节及之前的原著证据恢复，不会读取未来章节。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 650.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(snapshot.roleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("第 ${snapshot.anchorChapter} 章进入 · 最近证据第 ${snapshot.sourceChapter.coerceAtLeast(1)} 章", color = MaterialTheme.colorScheme.primary)
                                Text(snapshot.roleIdentity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("自动恢复起点状态", fontWeight = FontWeight.Bold)
                                Text("只在这个分支还没有任何互动回合时自动应用；不会覆盖已经玩起来的分支。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(snapshot.autoApply, onAutoApply)
                        }
                    }
                    if (snapshot.location.isNotBlank() || snapshot.storyTime.isNotBlank()) {
                        item { SnapshotSectionV1("此刻在哪 / 什么时间", listOfNotNull(
                            snapshot.location.takeIf { it.isNotBlank() }?.let { "地点：$it" },
                            snapshot.storyTime.takeIf { it.isNotBlank() }?.let { "时间：$it" },
                        )) }
                    }
                    if (snapshot.companions.isNotEmpty()) item { SnapshotSectionV1("身边/最近同事件人物", snapshot.companions) }
                    if (snapshot.conditionSignals.isNotEmpty()) item { SnapshotSectionV1("身体与处境", snapshot.conditionSignals) }
                    if (snapshot.carriedItems.isNotEmpty()) item { SnapshotSectionV1("明确持有/携带的物品", snapshot.carriedItems) }
                    if (snapshot.declaredGoal.isNotBlank()) item { SnapshotSectionV1("最近明确目标", listOf(snapshot.declaredGoal)) }
                    if (snapshot.recentEvents.isNotEmpty()) {
                        item { Text("刚经历什么", fontWeight = FontWeight.Bold) }
                        items(snapshot.recentEvents.takeLast(5), key = { "${it.chapter}:${it.partIndex}:${it.summary}" }) { event ->
                            Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("第 ${event.chapter} 章${event.storyTime.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    Text(event.summary, fontWeight = FontWeight.Medium)
                                    if (event.location.isNotBlank()) Text("地点：${event.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (snapshot.knownFacts.isNotEmpty()) item { SnapshotSectionV1("这个角色明确知道什么", snapshot.knownFacts.takeLast(12)) }
                    if (snapshot.relationshipHints.isNotEmpty()) item { SnapshotSectionV1("关系状态", snapshot.relationshipHints) }
                    item {
                        Surface(shape = LanghuanShape.card, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "不知道的秘密仍然不知道：未进入角色知识账本、没有亲眼看见/听见/收到的信息都不能使用；第 ${snapshot.anchorChapter + 1} 章及之后原著仍然完全锁死。",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    if (!notice.isNullOrBlank()) item { Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (snapshot != null) Button(onApply) { Text("应用为当前起点") }
            else TextButton(onDismiss) { Text("完成") }
        },
        dismissButton = { if (snapshot != null) TextButton(onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SnapshotSectionV1(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            lines.filter { it.isNotBlank() }.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val STORY_ROLE_ENTRY_CONDITION_REGEX_V1 = Regex(
    "受伤|伤口|流血|失血|骨折|中毒|发烧|高烧|昏迷|眩晕|虚弱|疲惫|生病|病倒|醉|被绑|被困|囚禁|失踪|怀孕|濒死|重伤",
    RegexOption.IGNORE_CASE,
)
private val STORY_ROLE_ENTRY_GOAL_REGEX_V1 = Regex(
    "目标|计划|打算|准备|决定|必须|想要|要去|前往|寻找|调查|保护|营救|救出|逃离|阻止|追查|找到",
    RegexOption.IGNORE_CASE,
)
private val STORY_ROLE_ENTRY_POSSESSION_REGEX_V1 = Regex(
    "持有|拿着|拿到|携带|随身|手中|手里|口袋|背着|保管|拥有|佩戴|藏着|带着",
    RegexOption.IGNORE_CASE,
)
private const val STORY_ROLE_ENTRY_NOTE_START_V1 = "【原著角色入场快照｜导演层】"
private const val STORY_ROLE_ENTRY_NOTE_END_V1 = "【/原著角色入场快照】"
