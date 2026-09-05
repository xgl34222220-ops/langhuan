package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.ExportFormat
import com.xiguli.langhuan.domain.*
import com.xiguli.langhuan.engine.ChapterPlanSuggestion
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.ui.agent.RunInspectorPanel
import com.xiguli.langhuan.ui.glass.liquidGlassLens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import com.xiguli.langhuan.ui.theme.LocalLanghuanAppearance
import com.xiguli.langhuan.ui.theme.LocalLanghuanTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.squircle.squircleClip

private enum class AppPage(val label: String, val icon: ImageVector) {
    Library("书架", Icons.Rounded.Book),
    Studio("创作", Icons.Rounded.EditNote),
    Plan("规划", Icons.Rounded.Timeline),
    Memory("记忆", Icons.Rounded.Memory),
    Settings("设置", Icons.Rounded.Settings),
}

@Composable
fun LanghuanApp(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableIntStateOf(0) }
    val appearance = LocalLanghuanAppearance.current
    val haze = rememberHazeState(blurEnabled = appearance.blurEnabled)
    val backdrop = rememberLayerBackdrop()
    val liquid = appearance.blurEnabled && appearance.glassEnabled && isRuntimeShaderSupported()
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importDocument(uri)
    }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportDocument(uri, ExportFormat.TXT)
    }
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) viewModel.exportDocument(uri, ExportFormat.MARKDOWN)
    }
    val epubLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        if (uri != null) viewModel.exportDocument(uri, ExportFormat.EPUB)
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (liquid) Modifier.layerBackdrop(backdrop) else Modifier.hazeSource(haze))) {
            Backdrop()
            AnimatedContent(selected, label = "page") { page ->
                when (AppPage.entries[page]) {
                    AppPage.Library -> LibraryPage(
                        state,
                        viewModel,
                        onImport = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/epub+zip", "application/octet-stream")) },
                    )
                    AppPage.Studio -> StudioPage(state, viewModel)
                    AppPage.Plan -> PlanPage(state, viewModel)
                    AppPage.Memory -> MemoryPage(state, viewModel)
                    AppPage.Settings -> SettingsPage(
                        state,
                        viewModel,
                        onExport = { format ->
                            val name = state.snapshot.novel.title
                            when (format) {
                                ExportFormat.TXT -> txtLauncher.launch("$name.txt")
                                ExportFormat.MARKDOWN -> mdLauncher.launch("$name.md")
                                ExportFormat.EPUB -> epubLauncher.launch("$name.epub")
                            }
                        },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
        MiuixDock(AppPage.entries[selected], { selected = it.ordinal }, haze, backdrop.takeIf { liquid }, Modifier.align(Alignment.BottomCenter))
    }

    state.result?.let { result -> GenerationResultDialog(state, result, viewModel) }
    state.pendingPlan?.let { PlanSuggestionDialog(it, state.isSaving, viewModel::acceptPlannedChapter, viewModel::dismissPlan) }
    state.rewriteSuggestion?.let { RewriteSuggestionDialog(it, viewModel::applyRewrite, viewModel::dismissRewrite) }
}

@Composable private fun Backdrop() {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalLanghuanTokens.current
    val dark = scheme.background.luminance() < .5f
    Box(Modifier.fillMaxSize().background(tokens.pageBackground).drawBehind {
        drawRect(Brush.radialGradient(listOf(scheme.primary.copy(alpha = if (dark) .09f else .12f), Color.Transparent), Offset(size.width * .92f, 0f), size.width * .92f))
        drawRect(Brush.radialGradient(listOf(scheme.secondary.copy(alpha = .07f), Color.Transparent), Offset(0f, size.height * .8f), size.width))
    })
}

@Composable private fun Page(title: String, subtitle: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.displaySmall, color = LocalLanghuanTokens.current.textPrimary)
            Text(subtitle, color = LocalLanghuanTokens.current.textSecondary)
        }
        content()
    }
}

@Composable private fun LibraryPage(state: StudioUiState, vm: StudioViewModel, onImport: () -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    Page("琅嬛", "多作品书架 · 长篇创作从项目开始") {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ showCreate = true }, Modifier.weight(1f), shape = LanghuanShape.card) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("新建小说")
                }
                OutlinedButton(onImport, Modifier.weight(1f), shape = LanghuanShape.card, enabled = !state.isImporting) {
                    Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(if (state.isImporting) "导入中" else "导入稿件")
                }
            }
        }
        items(state.stories, key = { it.id }) { story ->
            val active = story.id == state.snapshot.novel.id
            MiuixCard(onClick = { vm.selectStory(story.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).squircleClip(16.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(story.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (active) { Spacer(Modifier.width(8.dp)); Pill("当前", LocalLanghuanTokens.current.success) }
                        }
                        Text("${story.genre} · 第${story.currentChapter}章", color = LocalLanghuanTokens.current.textSecondary)
                    }
                    Text("${story.currentWords / 1000.0}k", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(story.currentWords.toFloat() / story.targetWords.coerceAtLeast(1))
            }
        }
        item { Heading("当前作品章节") }
        items(state.chapters, key = { it.number }) { chapter ->
            ChapterRow(chapter) { vm.selectChapter(chapter.number) }
        }
    }
    if (showCreate) NewStoryDialog(
        saving = state.isCreatingStory,
        onDismiss = { showCreate = false },
        onSave = { title, genre, premise, theme, target ->
            vm.createStory(title, genre, premise, theme, target)
            showCreate = false
        },
    )
}

@Composable private fun StudioPage(state: StudioUiState, vm: StudioViewModel) {
    var editor by remember(state.draft.id) { mutableStateOf(TextFieldValue(state.draft.content)) }
    var rewriteInstruction by remember { mutableStateOf("润色表达，增强画面感与节奏，不改变事实") }
    LaunchedEffect(state.draft.content) {
        if (state.draft.content != editor.text) {
            val cursor = editor.selection.end.coerceAtMost(state.draft.content.length)
            editor = TextFieldValue(state.draft.content, TextRange(cursor))
        }
    }
    val start = minOf(editor.selection.start, editor.selection.end)
    val end = maxOf(editor.selection.start, editor.selection.end)
    val selectedCount = (end - start).coerceAtLeast(0)

    Page("创作台", "章节切换、流式生成、正文编辑、局部重写与版本回滚") {
        item { MiuixCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("第 ${state.draft.chapterNumber} 章", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(state.draft.title, style = MaterialTheme.typography.headlineSmall)
                    Text(state.draft.objective, color = LocalLanghuanTokens.current.textSecondary)
                }
                Pill("v${state.draft.version}", MaterialTheme.colorScheme.primary)
            }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.chapters.forEach { chapter ->
                FilterChip(chapter.selected, { vm.selectChapter(chapter.number) }, label = { Text("${chapter.number} · ${chapter.title}", maxLines = 1) })
            }
        } }
        item { MiuixCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.provider.ready) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, null, tint = if (state.provider.ready) LocalLanghuanTokens.current.success else LocalLanghuanTokens.current.textSecondary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(if (state.provider.ready) state.provider.activeProviderLabel else "离线体验模式", fontWeight = FontWeight.Bold)
                    Text(if (state.provider.ready) state.provider.generationModel else "真实生成请到设置添加 AI", color = LocalLanghuanTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(vm::generateChapter, enabled = !state.isGenerating && !state.isSaving, modifier = Modifier.fillMaxWidth().height(54.dp), shape = LanghuanShape.card) {
                if (state.isGenerating) CircularProgressIndicator(Modifier.size(19.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp)); Text(if (state.isGenerating) "正在检索记忆并生成…" else "AI 生成本章正文")
            }
        } }
        if (state.runEvents.isNotEmpty()) item { RunInspectorPanel(state.runEvents, "章节 Run Inspector") }
        if (state.streamPreview.isNotBlank() && state.isGenerating) item {
            MiuixCard {
                val activeLabel = state.runEvents.lastOrNull { it.status == RunStatus.RUNNING }?.stage?.label ?: "实时生成预览"
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(activeLabel, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp)); Text(state.streamPreview, maxLines = 18, overflow = TextOverflow.Ellipsis)
            }
        }
        item { MiuixCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Heading("正文编辑器")
                Spacer(Modifier.weight(1f))
                if (state.isDraftDirty) Pill("未保存", LocalLanghuanTokens.current.warning) else Pill("已保存", LocalLanghuanTokens.current.success)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = editor,
                onValueChange = { value -> editor = value; vm.setDraftContent(value.text) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 620.dp),
                placeholder = { Text("正文会出现在这里，也可以直接手写或粘贴。") },
                shape = LanghuanShape.card,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${editor.text.length} 字 · 已选 $selectedCount 字", color = LocalLanghuanTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                TextButton(vm::saveDraftVersion, enabled = state.isDraftDirty && !state.isSaving) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(4.dp)); Text("保存版本") }
            }
        } }
        item { MiuixCard {
            Text("AI 局部重写", style = MaterialTheme.typography.titleMedium)
            Text("先在上面的正文中选中文字，再输入要求。AI 只替换选中片段，确认前不会改正文。", color = LocalLanghuanTokens.current.textSecondary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(rewriteInstruction, { rewriteInstruction = it }, Modifier.fillMaxWidth(), label = { Text("改写要求") }, shape = LanghuanShape.card)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.requestRewrite(start, end, rewriteInstruction) },
                enabled = selectedCount > 0 && !state.isRewriting && state.provider.ready,
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.card,
            ) {
                if (state.isRewriting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoFixHigh, null)
                Spacer(Modifier.width(7.dp)); Text(if (state.isRewriting) "正在重写…" else "重写选中片段")
            }
        } }
        if (state.versions.isNotEmpty()) {
            item { Heading("版本历史") }
            items(state.versions, key = { it.id }) { version -> VersionRow(version, state.isRestoringVersion) { vm.restoreVersion(version.id) } }
        }
    }
}

@Composable private fun PlanPage(state: StudioUiState, vm: StudioViewModel) {
    val outline = if (state.snapshot.outline.isEmpty()) state.snapshot.activeOutline else state.snapshot.outline
    var editOutline by remember { mutableStateOf<OutlineNode?>(null) }
    var addVolume by remember { mutableStateOf(false) }
    var showCreateChapter by remember { mutableStateOf(false) }
    var editScene by remember { mutableStateOf<ScenePlan?>(null) }
    var addScene by remember { mutableStateOf(false) }

    Page("剧情规划", "总纲 → 卷纲 → 章纲 → 场景，所有层级都可编辑") {
        item { MiuixCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("AI 规划下一章", style = MaterialTheme.typography.titleMedium)
                    Text("读取当前大纲、人物、伏笔与历史剧情后给出章纲和场景", color = LocalLanghuanTokens.current.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(vm::planNextChapter, enabled = state.provider.ready && !state.isPlanning && !state.isSaving, modifier = Modifier.fillMaxWidth(), shape = LanghuanShape.card) {
                if (state.isPlanning) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(7.dp)); Text(if (state.isPlanning) "正在规划…" else "规划第${state.draft.chapterNumber + 1}章")
            }
        } }
        item { OutlineSectionHeader("总纲", Icons.Rounded.Flag) }
        items(outline.filter { it.level == OutlineLevel.MASTER }.sortedBy { it.order }, key = { it.id }) { node -> OutlineRow(node, state.snapshot.activeOutline.any { it.id == node.id }, { editOutline = node }, null) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { OutlineSectionHeader("卷纲", Icons.Rounded.MenuBook); Spacer(Modifier.weight(1f)); TextButton({ addVolume = true }) { Icon(Icons.Rounded.Add, null); Text("新建卷") } } }
        items(outline.filter { it.level == OutlineLevel.VOLUME }.sortedBy { it.order }, key = { it.id }) { node -> OutlineRow(node, state.snapshot.activeOutline.any { it.id == node.id }, { editOutline = node }, { vm.deleteOutlineNode(node.id) }) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { OutlineSectionHeader("章纲", Icons.Rounded.ListAlt); Spacer(Modifier.weight(1f)); TextButton({ showCreateChapter = true }) { Icon(Icons.Rounded.Add, null); Text("新建章") } } }
        items(outline.filter { it.level == OutlineLevel.CHAPTER }.sortedBy { it.order }, key = { it.id }) { node ->
            OutlineRow(node, node.order == state.draft.chapterNumber, { editOutline = node }, if (node.order == state.draft.chapterNumber) null else { { vm.deleteOutlineNode(node.id) } })
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { OutlineSectionHeader("当前章场景", Icons.Rounded.Route); Spacer(Modifier.weight(1f)); TextButton({ addScene = true }) { Icon(Icons.Rounded.Add, null); Text("场景") } } }
        items(state.draft.scenePlan.sortedBy { it.order }, key = { it.order }) { scene -> SceneRow(scene, { editScene = scene }, if (state.draft.scenePlan.size > 1) { { vm.deleteScene(scene.order) } } else null) }
    }

    editOutline?.let { node -> OutlineEditorDialog(node, outline, { editOutline = null }) { level, parent, title, objective, conflict, turning, locked ->
        vm.saveOutlineNode(node.id, level, parent, title, objective, conflict, turning, locked); editOutline = null
    } }
    if (addVolume) OutlineEditorDialog(null, outline, { addVolume = false }, initialLevel = OutlineLevel.VOLUME) { level, parent, title, objective, conflict, turning, locked ->
        vm.saveOutlineNode(null, level, parent, title, objective, conflict, turning, locked); addVolume = false
    }
    if (showCreateChapter) ChapterEditorDialog({ showCreateChapter = false }) { title, objective, conflict, turning ->
        vm.createChapter(title, objective, conflict, turning); showCreateChapter = false
    }
    editScene?.let { scene -> SceneEditorDialog(scene, { editScene = null }) { viewpoint, location, purpose, conflict, outcome ->
        vm.saveScene(scene.order, viewpoint, location, purpose, conflict, outcome); editScene = null
    } }
    if (addScene) SceneEditorDialog(null, { addScene = false }) { viewpoint, location, purpose, conflict, outcome ->
        vm.saveScene(null, viewpoint, location, purpose, conflict, outcome); addScene = false
    }
}

@Composable private fun MemoryPage(state: StudioUiState, vm: StudioViewModel) {
    var bibleEdit by remember { mutableStateOf<BibleEntry?>(null) }
    var addBible by remember { mutableStateOf(false) }
    var characterEdit by remember { mutableStateOf<CharacterState?>(null) }
    var addCharacter by remember { mutableStateOf(false) }
    var timelineEdit by remember { mutableStateOf<TimelineEvent?>(null) }
    var addTimeline by remember { mutableStateOf(false) }
    var foreshadowEdit by remember { mutableStateOf<Foreshadowing?>(null) }
    var addForeshadow by remember { mutableStateOf(false) }

    Page("长期记忆", "人物、关系、时间线、伏笔和小说圣经会直接参与 RAG 检索") {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Heading("人物与关系"); Spacer(Modifier.weight(1f)); TextButton({ addCharacter = true }) { Icon(Icons.Rounded.Add, null); Text("人物") } } }
        items(state.snapshot.characters, key = { it.id }) { item -> CharacterRow(item, { characterEdit = item }, { vm.deleteCharacter(item.id) }) }
        if (state.snapshot.characters.isEmpty()) item { EmptyCard("还没有人物。先建立主角和关键人物，AI 才能稳定追踪状态与关系。") }

        item { Row(verticalAlignment = Alignment.CenterVertically) { Heading("时间线"); Spacer(Modifier.weight(1f)); TextButton({ addTimeline = true }) { Icon(Icons.Rounded.Add, null); Text("事件") } } }
        items(state.snapshot.recentTimeline.sortedByDescending { it.chapter }, key = { it.id }) { item -> TimelineRow(item, { timelineEdit = item }, { vm.deleteTimeline(item.id) }) }

        item { Row(verticalAlignment = Alignment.CenterVertically) { Heading("伏笔追踪"); Spacer(Modifier.weight(1f)); TextButton({ addForeshadow = true }) { Icon(Icons.Rounded.Add, null); Text("伏笔") } } }
        items(state.snapshot.relevantForeshadowing, key = { it.id }) { item -> ForeshadowRow(item, { foreshadowEdit = item }, { vm.deleteForeshadowing(item.id) }) }

        item { Row(verticalAlignment = Alignment.CenterVertically) { Heading("小说圣经"); Spacer(Modifier.weight(1f)); TextButton({ addBible = true }) { Icon(Icons.Rounded.Add, null); Text("设定") } } }
        items(state.snapshot.bible, key = { it.id }) { item -> BibleRow(item, { bibleEdit = item }, { vm.deleteBibleEntry(item.id) }) }

        if (state.snapshot.longTermSummary.isNotBlank()) item { MiuixCard {
            Text("折叠长期摘要", style = MaterialTheme.typography.titleMedium)
            Text(state.snapshot.longTermSummary, color = LocalLanghuanTokens.current.textSecondary, maxLines = 12, overflow = TextOverflow.Ellipsis)
        } }
    }

    if (addBible) BibleEditorDialog(null, { addBible = false }) { id, category, name, content, locked -> vm.saveBibleEntry(id, category, name, content, locked); addBible = false }
    bibleEdit?.let { item -> BibleEditorDialog(item, { bibleEdit = null }) { id, category, name, content, locked -> vm.saveBibleEntry(id, category, name, content, locked); bibleEdit = null } }
    if (addCharacter) CharacterEditorDialog(null, { addCharacter = false }) { id, name, personality, location, physical, emotional, goal, relations -> vm.saveCharacter(id, name, personality, location, physical, emotional, goal, relations); addCharacter = false }
    characterEdit?.let { item -> CharacterEditorDialog(item, { characterEdit = null }) { id, name, personality, location, physical, emotional, goal, relations -> vm.saveCharacter(id, name, personality, location, physical, emotional, goal, relations); characterEdit = null } }
    if (addTimeline) TimelineEditorDialog(null, state.draft.chapterNumber, { addTimeline = false }) { id, chapter, time, location, people, summary, consequences -> vm.saveTimeline(id, chapter, time, location, people, summary, consequences); addTimeline = false }
    timelineEdit?.let { item -> TimelineEditorDialog(item, state.draft.chapterNumber, { timelineEdit = null }) { id, chapter, time, location, people, summary, consequences -> vm.saveTimeline(id, chapter, time, location, people, summary, consequences); timelineEdit = null } }
    if (addForeshadow) ForeshadowEditorDialog(null, state.draft.chapterNumber, { addForeshadow = false }) { id, title, planted, detail, payoff, start, end, status -> vm.saveForeshadowing(id, title, planted, detail, payoff, start, end, status); addForeshadow = false }
    foreshadowEdit?.let { item -> ForeshadowEditorDialog(item, state.draft.chapterNumber, { foreshadowEdit = null }) { id, title, planted, detail, payoff, start, end, status -> vm.saveForeshadowing(id, title, planted, detail, payoff, start, end, status); foreshadowEdit = null } }
}

@Composable private fun SettingsPage(state: StudioUiState, vm: StudioViewModel, onExport: (ExportFormat) -> Unit) = Page("设置", "AI 服务、模型切换与整书导出") {
    val p = state.provider
    item { MiuixCard {
        Text("整书导出", style = MaterialTheme.typography.titleMedium)
        Text("章节按顺序从独立章节库读取，可导出 TXT、Markdown 或标准 EPUB。", color = LocalLanghuanTokens.current.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { format -> OutlinedButton({ onExport(format) }, enabled = !state.isExporting, shape = LanghuanShape.card) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(5.dp)); Text(format.name) } }
        }
    } }
    if (p.savedProviders.isNotEmpty()) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Heading("已保存 AI"); Spacer(Modifier.weight(1f)); Pill("${p.savedProviders.size} 个", MaterialTheme.colorScheme.primary) } }
        items(p.savedProviders, key = { it.id }) { provider -> SavedProviderRow(provider, provider.id == p.activeProviderId, { vm.activateProvider(provider.id) }, { vm.editProvider(provider.id) }, { vm.deleteProvider(provider.id) }) }
        item { OutlinedButton(vm::newProvider, Modifier.fillMaxWidth(), shape = LanghuanShape.card) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("添加新的 AI 服务") } }
    }
    item { ProviderEditor(p, vm) }
}

@Composable private fun ProviderEditor(p: ProviderUiState, vm: StudioViewModel) {
    MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.padding(start = 11.dp)) {
                Text(if (p.editingProviderId == null) "添加 AI 服务" else "编辑 AI 服务", style = MaterialTheme.typography.titleMedium)
                Text("OpenAI · Claude · Gemini · Azure · Ollama", color = LocalLanghuanTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(p.providerName, vm::setProviderName, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true, shape = LanghuanShape.card)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(p.baseUrl, vm::setBaseUrl, Modifier.fillMaxWidth(), label = { Text("API 地址") }, singleLine = true, shape = LanghuanShape.card)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(p.apiKey, vm::setApiKey, Modifier.fillMaxWidth(), label = { Text(if (p.hasStoredKey) "API Key（留空沿用已保存）" else "API Key") }, visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Rounded.Key, null) }, singleLine = true, shape = LanghuanShape.card)
        Spacer(Modifier.height(10.dp))
        Button(vm::detectProvider, enabled = p.baseUrl.isNotBlank() && !p.isDetecting, modifier = Modifier.fillMaxWidth(), shape = LanghuanShape.card) {
            if (p.isDetecting) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(7.dp)); Text(if (p.isDetecting) "正在探测…" else "自动识别并获取模型")
        }
        p.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        p.discovery?.let { d ->
            Spacer(Modifier.height(12.dp))
            Text("${d.providerLabel} · ${d.protocol.label}", fontWeight = FontWeight.Bold)
            Text(d.message, color = LocalLanghuanTokens.current.textSecondary)
            Spacer(Modifier.height(8.dp))
            d.models.take(12).forEach { model -> ModelRow(model, p.selectedModel == model.id) { vm.selectModel(model) }; Spacer(Modifier.height(6.dp)) }
            OutlinedTextField(p.manualModel, vm::setManualModel, Modifier.fillMaxWidth(), label = { Text("模型名 / 部署名") }, singleLine = true, shape = LanghuanShape.card)
            if (p.transientReady) {
                Spacer(Modifier.height(10.dp))
                Button(vm::saveProvider, enabled = !p.isSaving, modifier = Modifier.fillMaxWidth(), shape = LanghuanShape.card) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(7.dp)); Text("保存并设为当前 AI") }
            }
        }
    }
}

@Composable private fun GenerationResultDialog(state: StudioUiState, result: GenerationResult, vm: StudioViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissResult,
        shape = LanghuanShape.sheet,
        title = { Text(if (result.canCommit) "一致性检查通过" else "发现设定冲突") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(result.chapter.summary)
            if (result.issues.isEmpty()) Pill("0 个冲突 · 可以保存", LocalLanghuanTokens.current.success)
            result.issues.forEach { IssueRow(it) }
        } },
        confirmButton = { Button(if (result.canCommit) vm::commitResult else vm::dismissResult, enabled = !state.isSaving) { Text(if (result.canCommit) "保存正文与记忆" else "返回修改") } },
        dismissButton = { if (result.canCommit) TextButton(vm::dismissResult) { Text("放弃") } },
    )
}

@Composable private fun PlanSuggestionDialog(plan: ChapterPlanSuggestion, saving: Boolean, accept: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        shape = LanghuanShape.sheet,
        title = { Text("AI 下一章规划") },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleLarge)
            LabelValue("唯一目标", plan.objective); LabelValue("主要冲突", plan.conflict); LabelValue("章末转折", plan.turningPoint)
            Text("场景计划", fontWeight = FontWeight.Bold)
            plan.scenes.forEach { scene -> Text("${scene.order}. ${scene.viewpoint} · ${scene.location}\n${scene.purpose} → ${scene.conflict} → ${scene.outcome}", color = LocalLanghuanTokens.current.textSecondary) }
        } },
        confirmButton = { Button(accept, enabled = !saving) { Text("创建这一章") } },
        dismissButton = { TextButton(dismiss) { Text("放弃") } },
    )
}

@Composable private fun RewriteSuggestionDialog(suggestion: RewriteSuggestion, accept: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        shape = LanghuanShape.sheet,
        title = { Text("局部重写预览") },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { Text(suggestion.replacement) } },
        confirmButton = { Button(accept) { Text("替换选中片段") } },
        dismissButton = { TextButton(dismiss) { Text("保留原文") } },
    )
}

@Composable private fun NewStoryDialog(saving: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }; var genre by remember { mutableStateOf("") }; var premise by remember { mutableStateOf("") }; var theme by remember { mutableStateOf("") }; var target by remember { mutableStateOf("200000") }
    FormDialog("新建小说", onDismiss, { onSave(title, genre, premise, theme, target.toIntOrNull() ?: 200_000) }, title.isNotBlank() && !saving) {
        Field(title, { title = it }, "书名"); Field(genre, { genre = it }, "类型"); Field(premise, { premise = it }, "核心命题", false); Field(theme, { theme = it }, "主题"); Field(target, { target = it.filter(Char::isDigit) }, "目标字数")
    }
}

@Composable private fun ChapterEditorDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var objective by remember { mutableStateOf("") }; var conflict by remember { mutableStateOf("") }; var turning by remember { mutableStateOf("") }
    FormDialog("新建章节", onDismiss, { onSave(title, objective, conflict, turning) }, title.isNotBlank()) {
        Field(title, { title = it }, "章节标题"); Field(objective, { objective = it }, "唯一主目标", false); Field(conflict, { conflict = it }, "主要冲突", false); Field(turning, { turning = it }, "章末转折", false)
    }
}

@Composable private fun OutlineEditorDialog(node: OutlineNode?, outline: List<OutlineNode>, onDismiss: () -> Unit, initialLevel: OutlineLevel? = null, onSave: (OutlineLevel, String?, String, String, String, String, Boolean) -> Unit) {
    var level by remember { mutableStateOf(node?.level ?: initialLevel ?: OutlineLevel.MASTER) }
    var parent by remember { mutableStateOf(node?.parentId) }
    var title by remember { mutableStateOf(node?.title.orEmpty()) }; var objective by remember { mutableStateOf(node?.objective.orEmpty()) }; var conflict by remember { mutableStateOf(node?.conflict.orEmpty()) }; var turning by remember { mutableStateOf(node?.turningPoint.orEmpty()) }; var locked by remember { mutableStateOf(node?.locked ?: true) }
    FormDialog(if (node == null) "新增大纲" else "编辑大纲", onDismiss, { onSave(level, parent, title, objective, conflict, turning, locked) }, title.isNotBlank()) {
        Text("层级", fontWeight = FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlineLevel.entries.forEach { item -> FilterChip(level == item, { level = item }, label = { Text(item.label()) }) } }
        if (level != OutlineLevel.MASTER) {
            val candidates = outline.filter { it.level == if (level == OutlineLevel.VOLUME) OutlineLevel.MASTER else OutlineLevel.VOLUME }
            Text("父级", fontWeight = FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { candidates.forEach { item -> FilterChip(parent == item.id, { parent = item.id }, label = { Text(item.title) }) } }
        }
        Field(title, { title = it }, "标题"); Field(objective, { objective = it }, "目标", false); Field(conflict, { conflict = it }, "冲突", false); Field(turning, { turning = it }, "转折", false)
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(locked, { locked = it }); Text("锁定该大纲", Modifier.padding(start = 8.dp)) }
    }
}

@Composable private fun SceneEditorDialog(scene: ScenePlan?, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var viewpoint by remember { mutableStateOf(scene?.viewpoint.orEmpty()) }; var location by remember { mutableStateOf(scene?.location.orEmpty()) }; var purpose by remember { mutableStateOf(scene?.purpose.orEmpty()) }; var conflict by remember { mutableStateOf(scene?.conflict.orEmpty()) }; var outcome by remember { mutableStateOf(scene?.outcome.orEmpty()) }
    FormDialog(if (scene == null) "新增场景" else "编辑场景", onDismiss, { onSave(viewpoint, location, purpose, conflict, outcome) }, purpose.isNotBlank()) {
        Field(viewpoint, { viewpoint = it }, "视角人物"); Field(location, { location = it }, "地点"); Field(purpose, { purpose = it }, "场景目的", false); Field(conflict, { conflict = it }, "场景冲突", false); Field(outcome, { outcome = it }, "场景结果", false)
    }
}

@Composable private fun BibleEditorDialog(item: BibleEntry?, onDismiss: () -> Unit, onSave: (String?, BibleCategory, String, String, Boolean) -> Unit) {
    var category by remember { mutableStateOf(item?.category ?: BibleCategory.WORLD) }; var name by remember { mutableStateOf(item?.name.orEmpty()) }; var content by remember { mutableStateOf(item?.content.orEmpty()) }; var locked by remember { mutableStateOf(item?.locked ?: true) }
    FormDialog(if (item == null) "新增设定" else "编辑设定", onDismiss, { onSave(item?.id, category, name, content, locked) }, name.isNotBlank() && content.isNotBlank()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { BibleCategory.entries.forEach { c -> FilterChip(category == c, { category = c }, label = { Text(c.label()) }) } }
        Field(name, { name = it }, "名称"); Field(content, { content = it }, "设定内容", false); Row(verticalAlignment = Alignment.CenterVertically) { Switch(locked, { locked = it }); Text("锁定，不允许 AI 改写", Modifier.padding(start = 8.dp)) }
    }
}

@Composable private fun CharacterEditorDialog(item: CharacterState?, onDismiss: () -> Unit, onSave: (String?, String, String, String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }; var personality by remember { mutableStateOf(item?.personality?.joinToString("、").orEmpty()) }; var location by remember { mutableStateOf(item?.location.orEmpty()) }; var physical by remember { mutableStateOf(item?.physicalState.orEmpty()) }; var emotional by remember { mutableStateOf(item?.emotionalState.orEmpty()) }; var goal by remember { mutableStateOf(item?.goal.orEmpty()) }; var relations by remember { mutableStateOf(item?.relationshipNotes?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty()) }
    FormDialog(if (item == null) "新增人物" else "编辑人物", onDismiss, { onSave(item?.id, name, personality, location, physical, emotional, goal, relations) }, name.isNotBlank()) {
        Field(name, { name = it }, "姓名"); Field(personality, { personality = it }, "性格标签（顿号分隔）"); Field(location, { location = it }, "当前位置"); Field(physical, { physical = it }, "身体状态"); Field(emotional, { emotional = it }, "情绪状态"); Field(goal, { goal = it }, "当前目标", false); Field(relations, { relations = it }, "关系，每行：人物=关系描述", false)
    }
}

@Composable private fun TimelineEditorDialog(item: TimelineEvent?, currentChapter: Int, onDismiss: () -> Unit, onSave: (String?, Int, String, String, String, String, String) -> Unit) {
    var chapter by remember { mutableStateOf((item?.chapter ?: currentChapter).toString()) }; var time by remember { mutableStateOf(item?.storyTime.orEmpty()) }; var location by remember { mutableStateOf(item?.location.orEmpty()) }; var people by remember { mutableStateOf(item?.participants?.joinToString("、").orEmpty()) }; var summary by remember { mutableStateOf(item?.summary.orEmpty()) }; var consequences by remember { mutableStateOf(item?.consequences?.joinToString("、").orEmpty()) }
    FormDialog(if (item == null) "新增时间线事件" else "编辑时间线", onDismiss, { onSave(item?.id, chapter.toIntOrNull() ?: currentChapter, time, location, people, summary, consequences) }, summary.isNotBlank()) {
        Field(chapter, { chapter = it.filter(Char::isDigit) }, "章节"); Field(time, { time = it }, "故事内时间"); Field(location, { location = it }, "地点"); Field(people, { people = it }, "参与者"); Field(summary, { summary = it }, "事件摘要", false); Field(consequences, { consequences = it }, "后果", false)
    }
}

@Composable private fun ForeshadowEditorDialog(item: Foreshadowing?, currentChapter: Int, onDismiss: () -> Unit, onSave: (String?, String, Int, String, String, Int, Int, ForeshadowStatus) -> Unit) {
    var title by remember { mutableStateOf(item?.title.orEmpty()) }; var planted by remember { mutableStateOf((item?.plantedChapter ?: currentChapter).toString()) }; var detail by remember { mutableStateOf(item?.detail.orEmpty()) }; var payoff by remember { mutableStateOf(item?.expectedPayoff.orEmpty()) }; var start by remember { mutableStateOf((item?.expectedChapterStart ?: currentChapter + 1).toString()) }; var end by remember { mutableStateOf((item?.expectedChapterEnd ?: currentChapter + 10).toString()) }; var status by remember { mutableStateOf(item?.status ?: ForeshadowStatus.PLANTED) }
    FormDialog(if (item == null) "新增伏笔" else "编辑伏笔", onDismiss, { onSave(item?.id, title, planted.toIntOrNull() ?: currentChapter, detail, payoff, start.toIntOrNull() ?: currentChapter + 1, end.toIntOrNull() ?: currentChapter + 10, status) }, title.isNotBlank() && detail.isNotBlank()) {
        Field(title, { title = it }, "伏笔标题"); Field(planted, { planted = it.filter(Char::isDigit) }, "埋设章节"); Field(detail, { detail = it }, "具体细节", false); Field(payoff, { payoff = it }, "预期回收", false); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { Field(start, { start = it.filter(Char::isDigit) }, "回收起始章") }; Box(Modifier.weight(1f)) { Field(end, { end = it.filter(Char::isDigit) }, "回收结束章") } }; Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ForeshadowStatus.entries.forEach { s -> FilterChip(status == s, { status = s }, label = { Text(s.label()) }) } }
    }
}

@Composable private fun FormDialog(title: String, dismiss: () -> Unit, save: () -> Unit, enabled: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        shape = LanghuanShape.sheet,
        title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp), content = content) },
        confirmButton = { Button(save, enabled = enabled) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}

@Composable private fun Field(value: String, change: (String) -> Unit, label: String, singleLine: Boolean = true) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 3, shape = LanghuanShape.cover)

@Composable private fun ChapterRow(chapter: ChapterShelfUi, click: () -> Unit) = MiuixCard(onClick = click) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).squircleClip(14.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text(chapter.number.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 11.dp).weight(1f)) { Text(chapter.title, fontWeight = FontWeight.Bold); Text(chapter.objective, color = LocalLanghuanTokens.current.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        if (chapter.selected) Pill("当前", LocalLanghuanTokens.current.success) else Text("${chapter.words}字", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
    }
}

@Composable private fun OutlineSectionHeader(title: String, icon: ImageVector) = Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge) }

@Composable private fun OutlineRow(node: OutlineNode, active: Boolean, edit: () -> Unit, delete: (() -> Unit)?) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(node.level.label(), if (active) LocalLanghuanTokens.current.success else MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(horizontal = 10.dp).weight(1f)) { Text("${node.order}. ${node.title}", fontWeight = FontWeight.Bold); Text(node.objective, color = LocalLanghuanTokens.current.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; delete?.let { IconButton(it) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) } }
    }
    if (node.conflict.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("冲突：${node.conflict}", style = MaterialTheme.typography.bodySmall) }
    if (node.turningPoint.isNotBlank()) Text("转折：${node.turningPoint}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
}

@Composable private fun SceneRow(scene: ScenePlan, edit: () -> Unit, delete: (() -> Unit)?) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) { Pill("场景 ${scene.order}", MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text("${scene.viewpoint} · ${scene.location}", fontWeight = FontWeight.Bold); Text(scene.purpose, color = LocalLanghuanTokens.current.textSecondary) }; IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; delete?.let { IconButton(it) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) } } }
    Text("${scene.conflict} → ${scene.outcome}", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun CharacterRow(item: CharacterState, edit: () -> Unit, delete: () -> Unit) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).squircleClip(15.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) }; Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold); Text("${item.location} · ${item.emotionalState} · 目标：${item.goal}", color = LocalLanghuanTokens.current.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }; IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; IconButton(delete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) } }
    if (item.relationshipNotes.isNotEmpty()) Text("关系：${item.relationshipNotes.entries.joinToString("；") { "${it.key}=${it.value}" }}", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun TimelineRow(item: TimelineEvent, edit: () -> Unit, delete: () -> Unit) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) { Pill("第${item.chapter}章", MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(item.summary, fontWeight = FontWeight.Bold); Text("${item.storyTime} · ${item.location} · ${item.participants.joinToString("、")}", color = LocalLanghuanTokens.current.textSecondary) }; IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; IconButton(delete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) } }
}

@Composable private fun ForeshadowRow(item: Foreshadowing, edit: () -> Unit, delete: () -> Unit) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) { Pill(item.status.label(), if (item.status == ForeshadowStatus.RESOLVED) LocalLanghuanTokens.current.success else MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(item.title, fontWeight = FontWeight.Bold); Text(item.detail, color = LocalLanghuanTokens.current.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }; IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; IconButton(delete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) } }
    Text("埋设 ${item.plantedChapter} · 预计 ${item.expectedChapterStart}-${item.expectedChapterEnd} 章回收：${item.expectedPayoff}", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun BibleRow(item: BibleEntry, edit: () -> Unit, delete: () -> Unit) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (item.locked) Icons.Rounded.Lock else Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text("${item.category.label()} · ${item.name}", fontWeight = FontWeight.Bold); Text(item.content, color = LocalLanghuanTokens.current.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }; IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; IconButton(delete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) } }
}

@Composable private fun VersionRow(version: ChapterVersionUi, restoring: Boolean, restore: () -> Unit) = MiuixCard {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text("v${version.version} · ${version.title}", fontWeight = FontWeight.Bold); Text("${version.content.length} 字 · ${version.summary}", color = LocalLanghuanTokens.current.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }; TextButton(restore, enabled = !restoring) { Text("恢复") } }
}

@Composable private fun SavedProviderRow(provider: SavedProviderUi, active: Boolean, activate: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    val shape = LanghuanShape.panel
    Row(Modifier.fillMaxWidth().squircleClip(23.dp).background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else LocalLanghuanTokens.current.cardBackground.copy(alpha = .94f)).border(if (active) 1.1.dp else .5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape).clickable(onClick = activate).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(provider.name, fontWeight = FontWeight.Bold); Text("${provider.model} · ${provider.protocol.label}", color = LocalLanghuanTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        IconButton(edit) { Icon(Icons.Rounded.Edit, "编辑") }; IconButton(delete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun ModelRow(m: DiscoveredModel, selected: Boolean, click: () -> Unit) { val shape = LanghuanShape.card; Row(Modifier.fillMaxWidth().squircleClip(18.dp).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .13f) else LocalLanghuanTokens.current.cardBackground).border(if (selected) 1.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape).clickable(onClick = click).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary); Text(m.displayName, Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Bold); if (m.reasoning) Pill("推理", MaterialTheme.colorScheme.primary); if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) } }

@Composable private fun MiuixCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = LanghuanShape.sheet
    var modifier = Modifier.fillMaxWidth().shadow(2.dp, shape).squircleClip(26.dp).background(LocalLanghuanTokens.current.cardBackground.copy(alpha = .94f)).border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), shape)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Column(modifier.padding(18.dp), content = content)
}

@Composable private fun Heading(t: String) = Text(t, style = MaterialTheme.typography.titleLarge)
@Composable private fun Pill(t: String, c: Color) = Surface(shape = CircleShape, color = c.copy(alpha = .13f)) { Text(t, color = c, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
@Composable private fun ProgressBar(progress: Float) = Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), CircleShape)) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
@Composable private fun EmptyCard(text: String) = MiuixCard { Text(text, color = LocalLanghuanTokens.current.textSecondary) }
@Composable private fun LabelValue(label: String, value: String) { Text(label, fontWeight = FontWeight.Bold); Text(value, color = LocalLanghuanTokens.current.textSecondary) }
@Composable private fun IssueRow(i: ConsistencyIssue) { val c = if (i.severity == IssueSeverity.BLOCKING) MaterialTheme.colorScheme.error else LocalLanghuanTokens.current.warning; Column { Pill(i.severity.name, c); Text(i.message); if (i.repairInstruction.isNotBlank()) Text(i.repairInstruction, color = LocalLanghuanTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall) } }

private fun OutlineLevel.label() = when (this) { OutlineLevel.MASTER -> "总纲"; OutlineLevel.VOLUME -> "卷纲"; OutlineLevel.CHAPTER -> "章纲" }
private fun BibleCategory.label() = when (this) { BibleCategory.WORLD -> "世界"; BibleCategory.RULE -> "规则"; BibleCategory.CHARACTER -> "人物"; BibleCategory.FACTION -> "势力"; BibleCategory.LOCATION -> "地点"; BibleCategory.ITEM -> "物品"; BibleCategory.STYLE -> "文风"; BibleCategory.FORBIDDEN -> "禁止" }
private fun ForeshadowStatus.label() = when (this) { ForeshadowStatus.PLANTED -> "已埋设"; ForeshadowStatus.DEVELOPING -> "发展中"; ForeshadowStatus.RESOLVED -> "已回收"; ForeshadowStatus.ABANDONED -> "已废弃" }

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable private fun MiuixDock(current: AppPage, select: (AppPage) -> Unit, haze: HazeState, backdrop: LayerBackdrop?, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = LanghuanShape.sheet
    val surfaceBackdrop = rememberLayerBackdrop()
    val shellTint = if (dark) MaterialTheme.colorScheme.surface.copy(alpha = .39f) else Color.White.copy(alpha = .4f)
    val shell = if (backdrop != null) Modifier.drawBackdrop(
        backdrop, shape = { shape }, effects = { padding = maxOf(padding, 30.dp.toPx()); colorControls(brightness = .02f, contrast = 1.05f, saturation = 1.4f); blur(9.dp.toPx(), 9.dp.toPx()); liquidGlassLens(17.dp.toPx(), 13.dp.toPx(), true, .045f) },
        highlight = { (if (dark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight).copy(alpha = .82f) }, onDrawSurface = { drawRect(shellTint) }
    ) else Modifier.hazeEffect(haze, HazeMaterials.ultraThin()) { blurRadius = 30.dp; noiseFactor = .018f }.background(if (dark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .35f))
    Box(modifier.padding(horizontal = 10.dp).padding(bottom = inset + 10.dp).fillMaxWidth().height(66.dp)) {
        Box(Modifier.fillMaxSize().shadow(18.dp, shape).squircleClip(31.dp).then(if (backdrop != null) Modifier.layerBackdrop(surfaceBackdrop) else Modifier).then(shell).border(.6.dp, Color.White.copy(alpha = .3f), shape))
        DockItems(current, select, surfaceBackdrop.takeIf { backdrop != null }, dark, Modifier.fillMaxSize().padding(6.dp))
    }
}

@Composable private fun DockItems(current: AppPage, select: (AppPage) -> Unit, backdrop: LayerBackdrop?, dark: Boolean, modifier: Modifier) = BoxWithConstraints(modifier) {
    val width = maxWidth / AppPage.entries.size.toFloat()
    val x by animateDpAsState(width * current.ordinal, spring(dampingRatio = .68f, stiffness = Spring.StiffnessMediumLow), label = "dock")
    val shape = LanghuanShape.panel
    val indicatorTint = MaterialTheme.colorScheme.primary.copy(alpha = .18f)
    val lens = if (backdrop != null) Modifier.drawBackdrop(backdrop, shape = { shape }, effects = { padding = maxOf(padding, 22.dp.toPx()); blur(3.dp.toPx(), 3.dp.toPx()); liquidGlassLens(13.dp.toPx(), 14.dp.toPx(), true, .08f) }, highlight = { (if (dark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight).copy(alpha = .88f) }, onDrawSurface = { drawRect(indicatorTint) }) else Modifier.background(indicatorTint.copy(alpha = .72f))
    Box(Modifier.offset(x + 3.dp).width(width - 6.dp).height(54.dp).squircleClip(23.dp).then(lens))
    Row(Modifier.fillMaxWidth()) { AppPage.entries.forEach { p -> val chosen = p == current; val c = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f); Column(Modifier.width(width).height(54.dp).clickable(remember(p) { MutableInteractionSource() }, null) { select(p) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(p.icon, p.label, tint = c, modifier = Modifier.size(21.dp)); Text(p.label, color = c, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium) } } }
}
