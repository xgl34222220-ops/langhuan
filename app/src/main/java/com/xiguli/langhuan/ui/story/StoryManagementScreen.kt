package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape

private enum class StoryManagementSection(val label: String, val icon: ImageVector) {
    ROLE("角色", Icons.Rounded.Person),
    WORLD("世界", Icons.Rounded.Public),
    NPC("NPC 记忆", Icons.Rounded.Psychology),
    ENTRY("原著入场", Icons.Rounded.History),
    BRANCHES("分支", Icons.Rounded.ForkRight),
    DRAFT("章节草稿", Icons.Rounded.AutoStories),
}

/**
 * Story management surface for normal users.
 *
 * Mature V3/V1 stores and ViewModels stay authoritative. This screen only consolidates their
 * controls so the player no longer has to hunt through the historical V17 floating-action stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryManagementScreen(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
    onClose: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val lifeVm: NpcLifeViewModelV1 = viewModel()
    val lifeState by lifeVm.state.collectAsStateWithLifecycle()
    val memoryVm: NpcMemoryViewModelV1 = viewModel()
    val memoryState by memoryVm.state.collectAsStateWithLifecycle()
    val snapshotVm: StoryRoleEntrySnapshotViewModelV1 = viewModel()
    val snapshotState by snapshotVm.state.collectAsStateWithLifecycle()
    val adoptionVm: StoryDraftAdoptionViewModel = viewModel()
    val adoptionState by adoptionVm.state.collectAsStateWithLifecycle()
    val t = LocalLanghuanUiTokens.current

    val anchor = libraryState.readingChapter
        ?: libraryState.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
        ?: libraryState.chapters.lastOrNull()
    val session = storyState.active
    val lifeScene = lifeState.scene
    val memoryScene = memoryState.scene
    val snapshot = snapshotState.snapshot

    var section by remember(book.id) { mutableStateOf(StoryManagementSection.ROLE) }
    var showLegacySpatial by remember(book.id) { mutableStateOf(false) }

    LaunchedEffect(book.id, anchor?.chapterNumber) {
        storyVm.open(
            book.id,
            anchor?.chapterNumber ?: 1,
            anchor?.title.orEmpty(),
            anchor?.content.orEmpty(),
        )
    }

    LaunchedEffect(book.id, session?.id, session?.anchorChapter, session?.playerProfile) {
        val active = session ?: return@LaunchedEffect
        snapshotVm.open(book.id, active)
    }

    LaunchedEffect(book.id, session?.id, lifeScene?.sessionId) {
        val active = session ?: return@LaunchedEffect
        val life = lifeScene ?: return@LaunchedEffect
        if (life.sessionId == active.id) memoryVm.open(book.id, active)
    }

    // Preserve the original-role takeover safety behaviour while the old V17 controls are hidden.
    LaunchedEffect(snapshot?.sourceKey, snapshot?.appliedKey, snapshot?.autoApply, session?.turns?.size, storyState.busy) {
        val active = session ?: return@LaunchedEffect
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        val entry = snapshot ?: return@LaunchedEffect
        if (storyState.busy || !shouldAutoApplyStoryRoleEntrySnapshotV1(entry, active)) return@LaunchedEffect
        val applied = applyStoryRoleEntrySnapshotToWorldV1(entry, active, currentWorld, force = false)
        if (applied != currentWorld) storyVm.updateWorld(applied)
        snapshotVm.markApplied(entry.sourceKey)
    }

    LaunchedEffect(snapshotState.loading, snapshot?.sourceKey, storyState.runtime?.world?.notes, storyState.busy) {
        val currentWorld = storyState.runtime?.world ?: return@LaunchedEffect
        if (storyState.busy || snapshotState.loading) return@LaunchedEffect
        val block = snapshot?.let(::renderStoryRoleEntrySnapshotDirectorNoteV1).orEmpty()
        val notes = mergeStoryRoleEntrySnapshotDirectorNoteV1(currentWorld.notes, block)
        if (notes != currentWorld.notes) storyVm.updateWorld(currentWorld.copy(notes = notes))
    }

    // Keep the mature NPC memory-plan synchronisation active when this management screen replaces V9.
    LaunchedEffect(
        aiReady,
        session?.id,
        session?.turns?.lastOrNull()?.id,
        lifeScene?.beatCounter,
        lifeScene?.updatedAt,
        memoryScene?.autoConsolidate,
        memoryState.busy,
        lifeState.busy,
        storyState.busy,
    ) {
        val active = session ?: return@LaunchedEffect
        val life = lifeScene ?: return@LaunchedEffect
        val memory = memoryScene ?: return@LaunchedEffect
        if (aiReady && memory.autoConsolidate && !memoryState.busy && !lifeState.busy && !storyState.busy) {
            memoryVm.reconcile(book, active, storyState.runtime, life)
        }
    }

    LaunchedEffect(memoryState.syncToken, lifeScene?.states, memoryScene?.plans) {
        val life = lifeScene ?: return@LaunchedEffect
        val memory = memoryScene ?: return@LaunchedEffect
        val planned = applyNpcPlansToLifeV1(life.states, memory)
        planned.zip(life.states).forEach { (next, old) ->
            if (next.currentGoal != old.currentGoal || next.hiddenIntent != old.hiddenIntent) {
                lifeVm.updateNpc(next)
            }
        }
    }

    if (showLegacySpatial) {
        Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(book, libraryState, aiReady, onAiSetup, onAdopted)
            Surface(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                shape = LanghuanShape.pill,
                color = t.card.copy(alpha = .96f),
                border = BorderStroke(1.dp, t.border),
                shadowElevation = 4.dp,
            ) {
                TextButton(onClick = { showLegacySpatial = false }) {
                    Icon(Icons.Rounded.ArrowBack, null)
                    Spacer(Modifier.width(6.dp))
                    Text("返回故事设置")
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = t.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("故事设置", color = t.foreground, fontWeight = FontWeight.SemiBold)
                        Text(
                            session?.title ?: book.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = t.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, "返回故事", tint = t.foreground)
                    }
                },
                actions = {
                    TextButton(onClick = { showLegacySpatial = true }) { Text("空间 / 感知") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.background),
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StoryManagementSection.entries.forEach { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item.label) },
                        leadingIcon = { Icon(item.icon, null, Modifier.size(16.dp)) },
                    )
                }
            }
            HorizontalDivider(color = t.border)
            when (section) {
                StoryManagementSection.ROLE -> StoryRoleSection(storyVm, storyState)
                StoryManagementSection.WORLD -> StoryWorldSection(storyVm, storyState)
                StoryManagementSection.NPC -> StoryNpcMemorySection(
                    book = book,
                    storyState = storyState,
                    lifeState = lifeState,
                    memoryState = memoryState,
                    memoryVm = memoryVm,
                    aiReady = aiReady,
                    onAiSetup = onAiSetup,
                )
                StoryManagementSection.ENTRY -> StoryEntrySnapshotSection(
                    storyVm = storyVm,
                    storyState = storyState,
                    snapshotVm = snapshotVm,
                    snapshotState = snapshotState,
                )
                StoryManagementSection.BRANCHES -> StoryBranchesSection(storyVm, storyState, anchor)
                StoryManagementSection.DRAFT -> StoryDraftSection(
                    storyVm = storyVm,
                    storyState = storyState,
                    adoptionVm = adoptionVm,
                    adoptionState = adoptionState,
                    book = book,
                    libraryState = libraryState,
                    aiReady = aiReady,
                    onAiSetup = onAiSetup,
                    onAdopted = onAdopted,
                )
            }
        }
    }
}

@Composable
private fun StoryRoleSection(vm: StoryPlayV3ViewModel, state: StoryPlayV3UiState) {
    val t = LocalLanghuanUiTokens.current
    val profile = state.active?.playerProfile ?: StoryPlayerProfile()
    var name by remember(profile) { mutableStateOf(profile.name) }
    var identity by remember(profile) { mutableStateOf(profile.identity) }
    var traits by remember(profile) { mutableStateOf(profile.traits) }
    var known by remember(profile) { mutableStateOf(profile.knownFacts) }
    var forbidden by remember(profile) { mutableStateOf(profile.forbiddenKnowledge) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementHeader(
            Icons.Rounded.Person,
            "我是谁",
            "定义玩家在故事里的身份与知识边界。禁止提前知道的内容不会因为原著数据库存在就直接塞给角色。",
        )
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("姓名") }, singleLine = true)
        OutlinedTextField(identity, { identity = it }, Modifier.fillMaxWidth(), label = { Text("身份") }, placeholder = { Text("例如：原著角色 / 原创调查员 / 普通路人") })
        OutlinedTextField(traits, { traits = it }, Modifier.fillMaxWidth(), label = { Text("性格与特征") }, minLines = 2)
        OutlinedTextField(known, { known = it }, Modifier.fillMaxWidth(), label = { Text("当前已经知道") }, minLines = 3)
        OutlinedTextField(forbidden, { forbidden = it }, Modifier.fillMaxWidth(), label = { Text("禁止提前知道") }, minLines = 3)
        Button(
            onClick = { vm.updateProfile(StoryPlayerProfile(name.trim(), identity.trim(), traits.trim(), known.trim(), forbidden.trim())) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.Save, null)
            Spacer(Modifier.width(7.dp))
            Text("保存角色身份")
        }
        state.notice?.let { Text(it, color = t.success, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun StoryWorldSection(vm: StoryPlayV3ViewModel, state: StoryPlayV3UiState) {
    val t = LocalLanghuanUiTokens.current
    val world = state.runtime?.world ?: StoryWorldStateV3()
    var location by remember(world) { mutableStateOf(world.location) }
    var time by remember(world) { mutableStateOf(world.time) }
    var atmosphere by remember(world) { mutableStateOf(world.atmosphere) }
    var situation by remember(world) { mutableStateOf(world.situation) }
    var notes by remember(world) { mutableStateOf(world.notes) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementHeader(
            Icons.Rounded.Public,
            "此刻的世界",
            "世界状态属于当前故事分支。修改这里只影响互动分支，不会静默改写原小说正文。",
        )
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点") }, singleLine = true)
        OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth(), label = { Text("时间") }, singleLine = true)
        OutlinedTextField(atmosphere, { atmosphere = it }, Modifier.fillMaxWidth(), label = { Text("环境 / 氛围") }, minLines = 2)
        OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), label = { Text("当前局势") }, minLines = 3)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("导演备注") }, minLines = 3)
        Button(
            onClick = { vm.updateWorld(StoryWorldStateV3(location.trim(), time.trim(), atmosphere.trim(), situation.trim(), notes.trim())) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.Save, null)
            Spacer(Modifier.width(7.dp))
            Text("保存世界状态")
        }
        Surface(shape = LanghuanShape.card, color = t.muted, border = BorderStroke(1.dp, t.border)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StoryManagementStat("知识", state.runtime?.knowledge?.size ?: 0)
                StoryManagementStat("关系", state.runtime?.relationships?.size ?: 0)
                StoryManagementStat("变量", state.active?.variables?.size ?: 0)
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun StoryNpcMemorySection(
    book: ReaderBookUi,
    storyState: StoryPlayV3UiState,
    lifeState: NpcLifeUiStateV1,
    memoryState: NpcMemoryUiStateV1,
    memoryVm: NpcMemoryViewModelV1,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val session = storyState.active
    val lifeScene = lifeState.scene
    val scene = memoryState.scene
    val npcNames = remember(lifeScene?.states, scene?.memories, scene?.plans) {
        (
            lifeScene?.states.orEmpty().map { it.name } +
                scene?.memories.orEmpty().map { it.owner } +
                scene?.plans.orEmpty().map { it.owner }
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selected by remember(session?.id, npcNames) { mutableStateOf(npcNames.firstOrNull().orEmpty()) }
    if (selected !in npcNames && npcNames.isNotEmpty()) selected = npcNames.first()
    var newMemory by remember(session?.id) { mutableStateOf("") }
    var privacy by remember(session?.id) { mutableStateOf(NpcMemoryPrivacyV1.PRIVATE) }
    var importance by remember(session?.id) { mutableIntStateOf(3) }

    val memories = scene?.memories.orEmpty().filter { it.owner == selected }.sortedByDescending { it.createdAt }
    val plans = scene?.plans.orEmpty().filter { it.owner == selected }
        .sortedWith(compareByDescending<NpcPlanV1> { it.status == NpcPlanStatusV1.ACTIVE }.thenByDescending { it.priority })

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StoryManagementHeader(
                Icons.Rounded.Psychology,
                "NPC 自己记得什么",
                "私有记忆只属于对应角色，公开记忆才可作为共享事实。计划链用于保持 NPC 的持续目标，不会直接暴露给玩家。",
            )
        }
        if (scene == null || lifeScene == null || session == null) {
            item {
                StoryManagementNotice(
                    "NPC 状态正在初始化",
                    "先返回故事继续一轮互动，系统会从当前在场角色建立 NPC 状态；已有分支数据不会丢失。",
                    warning = true,
                )
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
            return@LazyColumn
        }
        item {
            Surface(shape = LanghuanShape.panel, color = t.card, border = BorderStroke(1.dp, t.border)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动沉淀长期记忆", fontWeight = FontWeight.SemiBold, color = t.foreground)
                        Text("重要经历会整理成长期记忆与计划链。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    Switch(scene.autoConsolidate, memoryVm::setAutoConsolidate, enabled = !memoryState.busy)
                }
            }
        }
        item {
            if (!aiReady) {
                OutlinedButton(onClick = onAiSetup, modifier = Modifier.fillMaxWidth()) { Text("配置 AI 后整理记忆") }
            } else {
                Button(
                    onClick = { memoryVm.reconcile(book, session, storyState.runtime, lifeScene, force = true) },
                    enabled = !memoryState.busy && !storyState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (memoryState.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (memoryState.busy) "正在整理" else "立即整理记忆与计划")
                }
            }
        }
        memoryState.error?.let { error ->
            item {
                StoryManagementNotice("整理失败", error, destructive = true) {
                    TextButton(onClick = memoryVm::clearError) { Text("关闭") }
                }
            }
        }
        memoryState.notice?.let { notice ->
            item {
                StoryManagementNotice("已更新", notice) {
                    TextButton(onClick = memoryVm::clearNotice) { Text("知道了") }
                }
            }
        }
        if (npcNames.isEmpty()) {
            item { StoryManagementNotice("暂无 NPC", "当前分支还没有可跟踪角色。继续互动后，这里会出现角色自己的记忆与计划。") }
        } else {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    npcNames.forEach { name ->
                        FilterChip(selected = selected == name, onClick = { selected = name }, label = { Text(name) })
                    }
                }
            }
            item {
                Text("长期记忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
                Text("私有记忆不会自动变成其他角色知道的事实。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            if (memories.isEmpty()) {
                item { Text("${selected} 还没有长期记忆。", color = t.mutedForeground) }
            } else {
                items(memories, key = { it.id }) { memory ->
                    Surface(shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${memory.privacy.label} · 重要度 ${memory.importance}", style = MaterialTheme.typography.labelSmall, color = t.accent)
                                Text(memory.summary, color = t.foreground)
                                if (memory.evidence.isNotBlank()) Text("依据：${memory.evidence}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            IconButton(onClick = { memoryVm.deleteMemory(memory.id) }, enabled = !memoryState.busy) {
                                Icon(Icons.Rounded.DeleteOutline, "删除记忆", tint = t.destructive)
                            }
                        }
                    }
                }
            }
            item {
                Text("计划链", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
            }
            if (plans.isEmpty()) {
                item { Text("暂无长期计划。", color = t.mutedForeground) }
            } else {
                items(plans, key = { it.id }) { plan ->
                    Surface(shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${plan.status.label} · P${plan.priority}", style = MaterialTheme.typography.labelSmall, color = if (plan.status == NpcPlanStatusV1.ACTIVE) t.success else t.mutedForeground)
                                Text(plan.goal, fontWeight = FontWeight.SemiBold, color = t.foreground)
                                if (plan.steps.isNotEmpty()) Text("步骤：${plan.steps.joinToString(" → ")}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                if (plan.privateReason.isNotBlank()) Text("私下原因：${plan.privateReason}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            IconButton(onClick = { memoryVm.deletePlan(plan.id) }, enabled = !memoryState.busy) {
                                Icon(Icons.Rounded.DeleteOutline, "删除计划", tint = t.destructive)
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider(color = t.border)
                Text("作者手动补记忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
                OutlinedTextField(
                    value = newMemory,
                    onValueChange = { newMemory = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("记忆内容") },
                    minLines = 2,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NpcMemoryPrivacyV1.entries.forEach { item ->
                        FilterChip(selected = privacy == item, onClick = { privacy = item }, label = { Text(item.label) })
                    }
                }
                Text("重要度：$importance", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = t.foreground)
                Slider(value = importance.toFloat(), onValueChange = { importance = it.toInt().coerceIn(1, 5) }, valueRange = 1f..5f, steps = 3)
                Button(
                    onClick = {
                        memoryVm.addMemory(selected, newMemory, privacy, importance)
                        newMemory = ""
                    },
                    enabled = selected.isNotBlank() && newMemory.isNotBlank() && !memoryState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("加入 ${selected.ifBlank { "NPC" }} 的长期记忆") }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(20.dp)) }
    }
}

@Composable
private fun StoryEntrySnapshotSection(
    storyVm: StoryPlayV3ViewModel,
    storyState: StoryPlayV3UiState,
    snapshotVm: StoryRoleEntrySnapshotViewModelV1,
    snapshotState: StoryRoleEntrySnapshotUiStateV1,
) {
    val t = LocalLanghuanUiTokens.current
    val snapshot = snapshotState.snapshot
    val session = storyState.active

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementHeader(
            Icons.Rounded.History,
            "原著角色入场快照",
            "只读取进入章节及之前的可靠原著证据，用来恢复角色当时真正知道什么、在哪里、带着什么。后续原著剧情禁止提前注入。",
        )
        if (snapshotState.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在整理进入点证据……", Modifier.padding(start = 8.dp), color = t.mutedForeground)
            }
        } else if (snapshot == null) {
            StoryManagementNotice(
                "当前没有原著入场快照",
                snapshotState.error ?: snapshotState.notice ?: "只有接管可识别的原著角色时才会生成。原创角色不会被强行套用原著人物状态。",
                warning = snapshotState.error != null,
            )
        } else {
            Surface(shape = LanghuanShape.panel, color = t.card, border = BorderStroke(1.dp, t.border)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动恢复进入状态", fontWeight = FontWeight.SemiBold, color = t.foreground)
                        Text("只在分支尚未发生互动时自动应用一次。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    Switch(snapshot.autoApply, snapshotVm::setAutoApply, enabled = !storyState.busy)
                }
            }
            StorySnapshotField("角色", "${snapshot.roleName} · ${snapshot.roleIdentity}")
            StorySnapshotField("证据边界", "进入第 ${snapshot.anchorChapter} 章 · 最近可靠角色证据第 ${snapshot.sourceChapter.takeIf { it > 0 } ?: snapshot.anchorChapter} 章")
            if (snapshot.location.isNotBlank()) StorySnapshotField("入场地点", snapshot.location)
            if (snapshot.storyTime.isNotBlank()) StorySnapshotField("入场时间", snapshot.storyTime)
            if (snapshot.declaredGoal.isNotBlank()) StorySnapshotField("最近明确目标", snapshot.declaredGoal)
            StorySnapshotList("同场人物证据", snapshot.companions)
            StorySnapshotList("身体 / 处境信号", snapshot.conditionSignals)
            StorySnapshotList("明确携带物品", snapshot.carriedItems)
            StorySnapshotList("截至进入点已知事实", snapshot.knownFacts)
            StorySnapshotList("关系证据", snapshot.relationshipHints)
            if (snapshot.recentEvents.isNotEmpty()) {
                Text("进入前最近事件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
                snapshot.recentEvents.takeLast(5).forEach { event ->
                    Surface(shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("第 ${event.chapter} 章${event.storyTime.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = t.accent)
                            Text(event.summary, color = t.foreground)
                            if (event.evidence.isNotBlank()) Text("依据：${event.evidence}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val active = session
                    val currentWorld = storyState.runtime?.world
                    if (active != null && currentWorld != null) {
                        val applied = applyStoryRoleEntrySnapshotToWorldV1(snapshot, active, currentWorld, force = true)
                        if (applied != currentWorld) storyVm.updateWorld(applied)
                        snapshotVm.markApplied(snapshot.sourceKey, manual = true)
                    }
                },
                enabled = !storyState.busy && session != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Restore, null)
                Spacer(Modifier.width(7.dp))
                Text("重新应用入场状态")
            }
            snapshotState.notice?.let { Text(it, color = t.success, style = MaterialTheme.typography.bodySmall) }
            snapshotState.error?.let { Text(it, color = t.destructive, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun StoryBranchesSection(
    vm: StoryPlayV3ViewModel,
    state: StoryPlayV3UiState,
    anchor: ChapterDraft?,
) {
    val t = LocalLanghuanUiTokens.current
    var renameTarget by remember { mutableStateOf<StoryPlaySession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<StoryPlaySession?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StoryManagementHeader(
                Icons.Rounded.ForkRight,
                "故事分支",
                "复制、回溯和新建分支彼此独立。尝试另一条路线不会覆盖当前路线或原小说。",
            )
        }
        item {
            Button(
                onClick = { vm.newBranch(anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty()) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("从当前章节新建分支")
            }
        }
        items(state.sessions.sortedByDescending { it.updatedAt }, key = { it.id }) { session ->
            val selected = session.id == state.active?.id
            Surface(
                shape = LanghuanShape.panel,
                color = if (selected) t.warmSurface else t.card,
                border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .32f) else t.border),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("第 ${session.anchorChapter} 章 · ${session.turns.size} 轮互动", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        if (selected) Icon(Icons.Rounded.CheckCircle, "当前", tint = t.accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.selectSession(session.id) }, enabled = !selected && !state.busy) { Text(if (selected) "正在使用" else "切换") }
                        TextButton(onClick = { renameTarget = session; renameText = session.title }, enabled = !state.busy) { Text("重命名") }
                        TextButton(onClick = { vm.duplicateBranch(session.id) }, enabled = !state.busy) { Text("复制") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { deleteTarget = session }, enabled = state.sessions.size > 1 && !state.busy) {
                            Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(20.dp)) }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名分支") },
            text = { OutlinedTextField(renameText, { renameText = it }, Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { Button(onClick = { vm.renameBranch(target.id, renameText); renameTarget = null }, enabled = renameText.isNotBlank()) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个故事分支？") },
            text = { Text("只删除「${target.title}」的互动路线，不会删除原小说，也不会影响其它分支。") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteBranch(target.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = t.destructive),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun StoryDraftSection(
    storyVm: StoryPlayV3ViewModel,
    storyState: StoryPlayV3UiState,
    adoptionVm: StoryDraftAdoptionViewModel,
    adoptionState: StoryDraftAdoptionUiState,
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val session = storyState.active
    val candidate = remember(session?.chapterDraftCandidate, session?.id) { storyDraftCandidateForManagement(book.id, session) }
    val original = candidate?.let { parsed -> libraryState.chapters.firstOrNull { it.chapterNumber == parsed.chapterNumber } }

    LaunchedEffect(candidate?.content) { adoptionVm.clearResult() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementHeader(
            Icons.Rounded.AutoStories,
            "把演绎整理成小说章节",
            "先生成独立候选，再对比当前正文。只有你明确点击采用，才会先永久备份原稿，再写入新的章节版本。",
        )
        Surface(shape = LanghuanShape.panel, color = t.card, border = BorderStroke(1.dp, t.border)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${session?.turns?.size ?: 0} 轮互动", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("锚点：第 ${session?.anchorChapter ?: 1} 章 · ${session?.anchorTitle.orEmpty().ifBlank { "当前章节" }}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }
        if (!aiReady) {
            OutlinedButton(onClick = onAiSetup, modifier = Modifier.fillMaxWidth()) { Text("先配置 AI") }
        } else {
            Button(
                onClick = { storyVm.generateChapterDraft(book) },
                enabled = !storyState.busy && session?.turns?.isNotEmpty() == true,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (storyState.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(7.dp))
                Text(if (storyState.busy) "正在整理候选草稿" else if (candidate == null) "生成章节候选草稿" else "重新生成候选草稿")
            }
        }
        if (candidate == null) {
            StoryManagementNotice("还没有候选草稿", "继续故事互动后点击上方按钮，琅嬛会把当前分支整理成独立候选，不会改动原稿。")
        } else {
            StoryManagementNotice(
                "采用前自动保护原稿",
                "确认采用时，会先把当前第 ${candidate.chapterNumber} 章建立永久历史版本，再把候选保存为新版本。",
            )
            Text(
                "当前正文 ${original?.content?.length ?: 0} 字 → 候选 ${candidate.content.length} 字 · 来源：${candidate.sourceSessionTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
            Text("当前正文", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
            Surface(shape = LanghuanShape.card, color = t.muted, border = BorderStroke(1.dp, t.border)) {
                SelectionContainer {
                    Text(
                        original?.content?.ifBlank { "（当前章节暂无正文）" } ?: "（未读取到当前正文）",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.foreground,
                    )
                }
            }
            Text("候选：${candidate.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.accent)
            Surface(shape = LanghuanShape.card, color = t.warmSurface, border = BorderStroke(1.dp, t.accent.copy(alpha = .22f))) {
                SelectionContainer {
                    Text(candidate.content, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = t.foreground)
                }
            }
            adoptionState.error?.let { StoryManagementNotice("采用失败", it, destructive = true) }
            adoptionState.message?.let { StoryManagementNotice("已采用", it) }
            Button(
                onClick = { adoptionVm.adopt(candidate, onAdopted) },
                enabled = !adoptionState.busy && adoptionState.adoptedVersion == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (adoptionState.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        adoptionState.busy -> "正在备份原稿并采用"
                        adoptionState.adoptedVersion != null -> "已采用为新版本"
                        else -> "备份原稿并采用"
                    }
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

private fun storyDraftCandidateForManagement(novelId: String, session: StoryPlaySession?): StoryDraftCandidateV1? {
    val source = session ?: return null
    val raw = source.chapterDraftCandidate.trim()
    if (raw.isBlank()) return null
    val firstBreak = raw.indexOf("\n\n")
    val title = if (firstBreak > 0) raw.substring(0, firstBreak).trim() else "演绎章节草稿"
    val body = if (firstBreak > 0) raw.substring(firstBreak + 2).trim() else raw
    if (body.isBlank()) return null
    return StoryDraftCandidateV1(
        novelId = novelId,
        chapterNumber = source.anchorChapter,
        sourceSessionId = source.id,
        sourceSessionTitle = source.title,
        title = title,
        content = body,
    )
}

@Composable
private fun StoryManagementHeader(icon: ImageVector, title: String, description: String) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = LanghuanShape.card, color = t.warmSurface) {
            Icon(icon, null, Modifier.padding(9.dp).size(20.dp), tint = t.accent)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = t.foreground)
            Text(description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}

@Composable
private fun StoryManagementStat(label: String, value: Int) {
    val t = LocalLanghuanUiTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
    }
}

@Composable
private fun StoryManagementNotice(
    title: String,
    text: String,
    warning: Boolean = false,
    destructive: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val tone = when {
        destructive -> t.destructive
        warning -> t.warning
        else -> t.accent
    }
    Surface(
        shape = LanghuanShape.card,
        color = tone.copy(alpha = .07f),
        border = BorderStroke(1.dp, tone.copy(alpha = .2f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = t.foreground)
                Text(text, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun StorySnapshotField(label: String, value: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
            Text(value, Modifier.padding(top = 3.dp), color = t.foreground)
        }
    }
}

@Composable
private fun StorySnapshotList(label: String, values: List<String>) {
    if (values.isEmpty()) return
    val t = LocalLanghuanUiTokens.current
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
    Surface(shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.takeLast(12).forEach { value -> Text("• $value", style = MaterialTheme.typography.bodySmall, color = t.foreground) }
        }
    }
}
