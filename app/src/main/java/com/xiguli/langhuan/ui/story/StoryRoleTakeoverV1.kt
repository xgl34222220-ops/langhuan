package com.xiguli.langhuan.ui.story

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StoryRoleTakeoverModeV1 {
    CANON_IMMERSION,
    DESTINY_REWRITE;

    val label: String get() = when (this) {
        CANON_IMMERSION -> "原著代入"
        DESTINY_REWRITE -> "命运改写"
    }

    val description: String get() = when (this) {
        CANON_IMMERSION -> "保持截至进入章节已经成立的人设、关系与因果；你的选择仍然可以自然改变后续。"
        DESTINY_REWRITE -> "进入章节之后允许彻底分叉；原著后续死亡、关系和事件都不是强制命运。"
    }
}

@Serializable
data class StoryRoleTakeoverSceneV1(
    val sessionId: String,
    val enabled: Boolean = false,
    val mode: StoryRoleTakeoverModeV1 = StoryRoleTakeoverModeV1.CANON_IMMERSION,
    val roleName: String = "",
    val roleIdentity: String = "",
    val canonicalRole: Boolean = false,
    val lockDialogue: Boolean = true,
    val lockVoluntaryActions: Boolean = true,
    val lockInnerDecisions: Boolean = true,
    val preserveCanonPersonality: Boolean = true,
    val revision: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class StoryRoleTakeoverArchiveV1(
    val novelId: String,
    val scenes: List<StoryRoleTakeoverSceneV1> = emptyList(),
)

data class StoryRoleTakeoverUiStateV1(
    val novelId: String = "",
    val scene: StoryRoleTakeoverSceneV1? = null,
    val scenes: List<StoryRoleTakeoverSceneV1> = emptyList(),
    val notice: String? = null,
)

class StoryRoleTakeoverViewModelV1(application: Application) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(StoryRoleTakeoverUiStateV1())
    val state: StateFlow<StoryRoleTakeoverUiStateV1> = _state.asStateFlow()

    fun open(novelId: String, session: StoryPlaySession) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.scene?.sessionId == session.id) {
            syncProfile(session)
            return
        }
        val archive = loadArchive(novelId)
        val existing = archive.scenes.firstOrNull { it.sessionId == session.id }
        val normalized = normalizeStoryRoleTakeoverSceneV1(existing, session)
        val scenes = archive.scenes.map { if (it.sessionId == session.id) normalized else it }
            .let { list -> if (list.any { it.sessionId == session.id }) list else list + normalized }
        _state.value = StoryRoleTakeoverUiStateV1(novelId = novelId, scene = normalized, scenes = scenes)
        saveArchive(StoryRoleTakeoverArchiveV1(novelId, scenes))
    }

    fun syncProfile(session: StoryPlaySession) {
        val scene = _state.value.scene ?: return
        if (scene.sessionId != session.id) return
        val updated = normalizeStoryRoleTakeoverSceneV1(scene, session)
        if (updated != scene) persistScene(updated.copy(revision = scene.revision + 1), "玩家角色控制权已同步")
    }

    fun setEnabled(enabled: Boolean) = mutate(if (enabled) "玩家角色已切换为本人接管" else "玩家角色接管已关闭") {
        it.copy(enabled = enabled && it.roleName.isNotBlank())
    }

    fun setMode(mode: StoryRoleTakeoverModeV1) = mutate("故事运行模式已切换为${mode.label}") { it.copy(mode = mode) }

    fun setLockDialogue(enabled: Boolean) = mutate(null) { it.copy(lockDialogue = enabled) }
    fun setLockVoluntaryActions(enabled: Boolean) = mutate(null) { it.copy(lockVoluntaryActions = enabled) }
    fun setLockInnerDecisions(enabled: Boolean) = mutate(null) { it.copy(lockInnerDecisions = enabled) }
    fun setPreserveCanonPersonality(enabled: Boolean) = mutate(null) { it.copy(preserveCanonPersonality = enabled) }

    private fun mutate(message: String?, transform: (StoryRoleTakeoverSceneV1) -> StoryRoleTakeoverSceneV1) {
        val scene = _state.value.scene ?: return
        persistScene(transform(scene).copy(revision = scene.revision + 1), message)
    }

    private fun persistScene(updated: StoryRoleTakeoverSceneV1, notice: String?) {
        val current = _state.value
        val normalized = updated.copy(updatedAt = System.currentTimeMillis())
        val scenes = current.scenes.map { if (it.sessionId == normalized.sessionId) normalized else it }
            .let { list -> if (list.any { it.sessionId == normalized.sessionId }) list else list + normalized }
        _state.update { it.copy(scene = normalized, scenes = scenes, notice = notice) }
        if (current.novelId.isNotBlank()) saveArchive(StoryRoleTakeoverArchiveV1(current.novelId, scenes))
    }

    private fun archiveFile(novelId: String): File = File(getApplication<Application>().filesDir, "story_role_takeover_v1")
        .apply { mkdirs() }
        .resolve("${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun loadArchive(novelId: String): StoryRoleTakeoverArchiveV1 {
        val file = archiveFile(novelId)
        if (!file.isFile) return StoryRoleTakeoverArchiveV1(novelId)
        return runCatching { json.decodeFromString(StoryRoleTakeoverArchiveV1.serializer(), file.readText()) }
            .getOrElse { StoryRoleTakeoverArchiveV1(novelId) }
    }

    private fun saveArchive(archive: StoryRoleTakeoverArchiveV1) {
        runCatching { archiveFile(archive.novelId).writeText(json.encodeToString(StoryRoleTakeoverArchiveV1.serializer(), archive)) }
    }
}

internal fun normalizeStoryRoleTakeoverSceneV1(
    existing: StoryRoleTakeoverSceneV1?,
    session: StoryPlaySession,
): StoryRoleTakeoverSceneV1 {
    val profile = session.playerProfile
    val name = profile.name.trim()
    val identity = profile.identity.trim()
    val canonical = isCanonStoryRoleIdentityV1(identity)
    if (existing == null) {
        return StoryRoleTakeoverSceneV1(
            sessionId = session.id,
            enabled = name.isNotBlank(),
            roleName = name,
            roleIdentity = identity,
            canonicalRole = canonical,
            preserveCanonPersonality = canonical,
        )
    }
    return existing.copy(
        sessionId = session.id,
        enabled = existing.enabled && name.isNotBlank(),
        roleName = name,
        roleIdentity = identity,
        canonicalRole = canonical,
    )
}

internal fun isCanonStoryRoleIdentityV1(identity: String): Boolean =
    identity.trim().startsWith("原著角色", ignoreCase = true)

internal fun renderStoryRoleTakeoverDirectorNoteV1(
    scene: StoryRoleTakeoverSceneV1,
    session: StoryPlaySession,
): String {
    if (!scene.enabled || scene.roleName.isBlank()) return ""
    val role = scene.roleName.trim()
    return buildString {
        append(STORY_TAKEOVER_NOTE_START_V1).append('\n')
        append("玩家当前接管角色：").append(role).append('\n')
        append("身份：").append(scene.roleIdentity.ifBlank { "玩家自定义角色" }).append('\n')
        append("进入点：第 ").append(session.anchorChapter).append(" 章 · ").append(session.anchorTitle.ifBlank { "未命名章节" }).append('\n')
        append("运行模式：").append(scene.mode.label).append("。\n")
        append("控制权总规则：角色“").append(role).append("”由真人玩家独占控制。AI 负责世界、环境和其他 NPC，不得为了让剧情顺滑而替玩家角色做关键决定。\n")
        if (scene.lockDialogue) {
            append("台词锁：除非玩家本轮输入明确写出了要说的话，否则不得替“").append(role)
                .append("”新增台词、承诺、拒绝、表态或把沉默改写成说话；可以原样体现玩家已输入的台词，但不得擅自扩写新的意思。\n")
        }
        if (scene.lockVoluntaryActions) {
            append("行动锁：不得替“").append(role)
                .append("”主动移动、攻击、逃跑、答应、拒绝、拿取、丢弃、开门、关门、使用物件或作出其他自主行为。只有玩家本轮明确做出的动作，才能生成对应 STORY_SPACE / STORY_OBJECT / 物品状态变化。\n")
        }
        if (scene.lockInnerDecisions) {
            append("心理锁：不得替“").append(role)
                .append("”写新的内心决定、价值判断、动机结论或“他决定/他想要/他意识到自己必须”等主动意志。允许描写疼痛、眩晕、心跳等非自主感受，但不能借生理描写偷偷替玩家作决定。\n")
        }
        if (scene.preserveCanonPersonality && scene.canonicalRole) {
            append("原著人格：截至第 ").append(session.anchorChapter)
                .append(" 章已经明确的人设、关系、能力与经历继续作为角色底色；但它们只能约束可能性，不能替玩家自动选择。\n")
        }
        append("知识视角：玩家扮演的是角色，不是全知读者。只允许使用“").append(role)
            .append("”截至当前章节真实已知、亲眼看见、清楚听见或明确收到的信息；导演层知道的秘密不能自动变成角色知识。\n")
        append("未来原著边界：绝不能使用第 ").append(session.anchorChapter + 1)
            .append(" 章及之后的原著事实来替玩家预判危险、暗示真相、安排必然命运或修正玩家选择。\n")
        when (scene.mode) {
            StoryRoleTakeoverModeV1.CANON_IMMERSION -> {
                append("原著代入规则：第 ").append(session.anchorChapter)
                    .append(" 章及之前已经发生的事实不可改写；后续尽量延续当前已建立的人物性格、关系与因果惯性，但只能根据当前可见信息自然演进。玩家一旦明确作出不同选择，就接受真实后果，不得强行把剧情掰回原著未来。\n")
            }
            StoryRoleTakeoverModeV1.DESTINY_REWRITE -> {
                append("命运改写规则：第 ").append(session.anchorChapter)
                    .append(" 章及之前仍是不可改写的共同过去；从进入点之后允许彻底分叉。原著后续的死亡、恋爱、背叛、胜负、相遇和结局都不是必须发生的事件，不得用“原著本来如此”强行回轨。\n")
            }
        }
        append("DM 输出要求：只描写玩家动作造成的结果、环境反馈、其他 NPC 的反应和可感知的新信息；当需要“").append(role)
            .append("”再次决定时停在决定点，把控制权交还玩家。\n")
        append(STORY_TAKEOVER_NOTE_END_V1)
    }
}

internal fun mergeStoryRoleTakeoverDirectorNoteV1(original: String, block: String): String {
    val pattern = Regex(
        Regex.escape(STORY_TAKEOVER_NOTE_START_V1) + ".*?" + Regex.escape(STORY_TAKEOVER_NOTE_END_V1),
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    val stripped = original.replace(pattern, "").trim()
    return listOf(stripped, block.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

@Composable
fun StoryPlayPanelV16(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val takeoverVm: StoryRoleTakeoverViewModelV1 = viewModel()
    val takeover by takeoverVm.state.collectAsStateWithLifecycle()
    val session = storyState.active
    val world = storyState.runtime?.world
    var showTakeover by remember(book.id, session?.id) { mutableStateOf(false) }

    LaunchedEffect(book.id, session?.id, session?.playerProfile) {
        val active = session ?: return@LaunchedEffect
        takeoverVm.open(book.id, active)
    }

    LaunchedEffect(takeover.scene?.revision, takeover.scene?.roleName, world?.notes, storyState.busy) {
        val active = session ?: return@LaunchedEffect
        val takeoverScene = takeover.scene ?: return@LaunchedEffect
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy) return@LaunchedEffect
        val block = renderStoryRoleTakeoverDirectorNoteV1(takeoverScene, active)
        val notes = mergeStoryRoleTakeoverDirectorNoteV1(currentWorld.notes, block)
        if (notes != currentWorld.notes) storyVm.updateWorld(currentWorld.copy(notes = notes))
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV15(book, libraryState, aiReady, onAiSetup, onAdopted)
        SmallFloatingActionButton(
            onClick = { showTakeover = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 582.dp),
        ) {
            Icon(Icons.Rounded.Person, "角色接管")
        }
    }

    if (showTakeover && session != null && takeover.scene != null) {
        StoryRoleTakeoverDialogV1(
            scene = takeover.scene!!,
            anchorChapter = session.anchorChapter,
            notice = takeover.notice,
            onDismiss = { showTakeover = false },
            onEnabled = takeoverVm::setEnabled,
            onMode = takeoverVm::setMode,
            onDialogue = takeoverVm::setLockDialogue,
            onAction = takeoverVm::setLockVoluntaryActions,
            onMind = takeoverVm::setLockInnerDecisions,
            onPersonality = takeoverVm::setPreserveCanonPersonality,
        )
    }
}

@Composable
private fun StoryRoleTakeoverDialogV1(
    scene: StoryRoleTakeoverSceneV1,
    anchorChapter: Int,
    notice: String?,
    onDismiss: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onMode: (StoryRoleTakeoverModeV1) -> Unit,
    onDialogue: (Boolean) -> Unit,
    onAction: (Boolean) -> Unit,
    onMind: (Boolean) -> Unit,
    onPersonality: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("原著角色接管")
                    Text(
                        scene.roleName.ifBlank { "尚未选择身份" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 660.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Text(
                            if (scene.canonicalRole) "原著角色 · 第 $anchorChapter 章视角" else "当前玩家身份",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            scene.roleIdentity.ifBlank { "请先用左下角“选择身份”绑定原著人物。" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("本人接管这个角色", fontWeight = FontWeight.Bold)
                        Text("开启后 AI 不再替这个角色做关键决定。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(scene.enabled, onEnabled, enabled = scene.roleName.isNotBlank())
                }

                Text("故事模式", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StoryRoleTakeoverModeV1.entries.forEach { mode ->
                        FilterChip(
                            selected = scene.mode == mode,
                            onClick = { onMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(scene.mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider()
                Text("玩家控制权", fontWeight = FontWeight.Bold)
                TakeoverSwitchV1("锁定台词", "AI 不能把你的沉默变成说话，也不能擅自补台词。", scene.lockDialogue, onDialogue)
                TakeoverSwitchV1("锁定自主行动", "移动、攻击、开门、拿东西等必须由你明确决定。", scene.lockVoluntaryActions, onAction)
                TakeoverSwitchV1("锁定心理决定", "AI 可以写感受，但不能替你决定想法、立场和动机。", scene.lockInnerDecisions, onMind)
                if (scene.canonicalRole) {
                    TakeoverSwitchV1("保留原著人格底色", "截至当前章节的人设、能力和关系继续成立，但不替你做选择。", scene.preserveCanonPersonality, onPersonality)
                }

                Surface(shape = LanghuanShape.card, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "未来原著仍然锁死：AI 不会因为读过整本书就让你提前知道第 ${anchorChapter + 1} 章之后的秘密。命运改写也只改进入点之后的分支，不会覆盖原著正文。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (!notice.isNullOrBlank()) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { Button(onDismiss) { Text("完成") } },
    )
}

@Composable
private fun TakeoverSwitchV1(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChecked)
    }
}

private const val STORY_TAKEOVER_NOTE_START_V1 = "【玩家角色接管｜导演层】"
private const val STORY_TAKEOVER_NOTE_END_V1 = "【/玩家角色接管】"
