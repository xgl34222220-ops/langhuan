package com.xiguli.langhuan.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.*
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.ui.glass.liquidGlassLens
import com.xiguli.langhuan.ui.theme.LocalLanghuanAppearance
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
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
    Library("书架", Icons.Rounded.Book), Studio("创作", Icons.Rounded.EditNote),
    Memory("记忆", Icons.Rounded.Memory), Settings("设置", Icons.Rounded.Settings),
}

@Composable
fun LanghuanApp(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableIntStateOf(0) }
    var showCreateStory by remember { mutableStateOf(false) }
    var showBibleEditor by remember { mutableStateOf(false) }
    var editingBible by remember { mutableStateOf<BibleEntry?>(null) }
    val appearance = LocalLanghuanAppearance.current
    val haze = rememberHazeState(blurEnabled = appearance.blurEnabled)
    val backdrop = rememberLayerBackdrop()
    val liquid = appearance.blurEnabled && appearance.glassEnabled && isRuntimeShaderSupported()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (liquid) Modifier.layerBackdrop(backdrop) else Modifier.hazeSource(haze))) {
            Backdrop()
            AnimatedContent(selected, label = "page") { page ->
                when (AppPage.entries[page]) {
                    AppPage.Library -> LibraryPage(
                        state = state,
                        createStory = { showCreateStory = true },
                        selectStory = viewModel::selectStory,
                    )
                    AppPage.Studio -> StudioPage(
                        state = state,
                        generate = viewModel::generateChapter,
                        editContent = viewModel::setDraftContent,
                        saveDraft = viewModel::saveDraftVersion,
                        restoreVersion = viewModel::restoreVersion,
                    )
                    AppPage.Memory -> MemoryPage(
                        s = state.snapshot,
                        addBible = {
                            editingBible = null
                            showBibleEditor = true
                        },
                        editBible = {
                            editingBible = it
                            showBibleEditor = true
                        },
                        deleteBible = viewModel::deleteBibleEntry,
                    )
                    AppPage.Settings -> SettingsPage(
                        state.provider,
                        viewModel::setProviderName,
                        viewModel::setBaseUrl,
                        viewModel::setApiKey,
                        viewModel::detectProvider,
                        viewModel::selectModel,
                        viewModel::setManualModel,
                        viewModel::newProvider,
                        viewModel::editProvider,
                        viewModel::activateProvider,
                        viewModel::deleteProvider,
                        viewModel::saveProvider,
                    )
                }
            }
        }
        MiuixDock(AppPage.entries[selected], { selected = it.ordinal }, haze, backdrop.takeIf { liquid }, Modifier.align(Alignment.BottomCenter))
    }

    state.result?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (result.canCommit) "一致性检查通过" else "发现设定冲突") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(result.chapter.summary)
                if (result.issues.isEmpty()) Pill("0 个冲突 · 可以保存", LocalMiuixTokens.current.success)
                result.issues.forEach { IssueRow(it) }
            } },
            confirmButton = {
                Button(onClick = if (result.canCommit) viewModel::commitResult else viewModel::dismissResult, enabled = !state.isSaving) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (result.canCommit) "保存正文与记忆" else "返回修改")
                }
            },
        )
    }

    if (showCreateStory) {
        NewStoryDialog(
            busy = state.isCreatingStory,
            dismiss = { if (!state.isCreatingStory) showCreateStory = false },
            create = { title, genre, premise, theme, targetWords ->
                viewModel.createStory(title, genre, premise, theme, targetWords)
                showCreateStory = false
                selected = AppPage.Studio.ordinal
            },
        )
    }

    if (showBibleEditor) {
        BibleEditorDialog(
            entry = editingBible,
            busy = state.isSaving,
            dismiss = { if (!state.isSaving) showBibleEditor = false },
            save = { category, name, content, locked ->
                viewModel.saveBibleEntry(editingBible?.id, category, name, content, locked)
                showBibleEditor = false
            },
        )
    }
}

@Composable private fun Backdrop() {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMiuixTokens.current
    val dark = scheme.background.luminance() < .5f
    Box(Modifier.fillMaxSize().background(tokens.pageBackground).drawBehind {
        drawRect(Brush.radialGradient(listOf(scheme.primary.copy(alpha = if (dark) .09f else .12f), Color.Transparent), Offset(size.width * .92f, 0f), size.width * .92f))
        drawRect(Brush.radialGradient(listOf(scheme.secondary.copy(alpha = .07f), Color.Transparent), Offset(0f, size.height * .8f), size.width))
    })
}

@Composable private fun Page(title: String, subtitle: String, content: LazyListScope.() -> Unit) {
    val tokens = LocalMiuixTokens.current
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.displaySmall, color = tokens.textPrimary)
            Text(subtitle, color = tokens.textSecondary)
        }
        content()
    }
}

@Composable private fun LibraryPage(
    state: StudioUiState,
    createStory: () -> Unit,
    selectStory: (String) -> Unit,
) = Page("琅嬛", "管理作品，选择一部长篇继续创作") {
    val s = state.snapshot
    item { MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.novel.title, style = MaterialTheme.typography.headlineMedium)
                Text(s.novel.genre, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Pill("当前作品", LocalMiuixTokens.current.success)
        }
        Spacer(Modifier.height(15.dp)); Text(s.novel.premise); Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.novel.currentWords / 1000.0}k 字", fontWeight = FontWeight.Bold)
            Text("目标 ${s.novel.targetWords / 10_000} 万", color = LocalMiuixTokens.current.textSecondary)
        }
        Spacer(Modifier.height(8.dp)); ProgressBar(s.novel.currentWords.toFloat() / s.novel.targetWords.coerceAtLeast(1))
    } }
    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Heading("作品书架")
        Button(createStory, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("新建小说")
        }
    } }
    items(state.stories, key = { it.id }) { story ->
        StoryShelfRow(story, story.id == s.novel.id) { selectStory(story.id) }
    }
    item { Heading("故事健康度") }
    item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Metric("锁定设定", s.bible.count { it.locked }, Modifier.weight(1f))
        Metric("活跃伏笔", s.relevantForeshadowing.size, Modifier.weight(1f))
        Metric("人物状态", s.characters.size, Modifier.weight(1f))
    } }
    item { MiuixCard {
        Text("当前章节", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("第 ${s.novel.currentChapter} 章 · ${s.activeOutline.lastOrNull()?.title.orEmpty()}", style = MaterialTheme.typography.titleLarge)
        Text(s.activeOutline.lastOrNull()?.objective.orEmpty(), color = LocalMiuixTokens.current.textSecondary)
    } }
}

@Composable private fun StoryShelfRow(story: StoryShelfUi, active: Boolean, click: () -> Unit) {
    val shape = RoundedCornerShape(23.dp)
    Row(
        Modifier.fillMaxWidth().squircleClip(23.dp)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else LocalMiuixTokens.current.cardBackground.copy(alpha = .94f))
            .border(if (active) 1.1.dp else .5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = click).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).squircleClip(16.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(story.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) { Spacer(Modifier.width(7.dp)); Pill("当前", LocalMiuixTokens.current.success) }
            }
            Text("${story.genre} · 第${story.currentChapter}章 · ${story.currentWords} 字", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = LocalMiuixTokens.current.textSecondary)
    }
}

@Composable private fun StudioPage(
    state: StudioUiState,
    generate: () -> Unit,
    editContent: (String) -> Unit,
    saveDraft: () -> Unit,
    restoreVersion: (String) -> Unit,
) = Page("创作台", "混合向量 RAG → 流式生成 → 一致性门禁 → 版本入库") {
    val s = state.snapshot
    item { MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("第 ${state.draft.chapterNumber} 章", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(state.draft.title, style = MaterialTheme.typography.headlineSmall)
            }
            Pill("v${state.draft.version}", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp)); Text("本章唯一目标", fontWeight = FontWeight.Bold); Text(state.draft.objective)
    } }
    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("三级大纲", "${s.activeOutline.size}/3", Icons.Rounded.Timeline); Chip("人物状态", "${s.characters.size}", Icons.Rounded.Psychology)
        Chip("时间线", "${s.recentTimeline.size}", Icons.Rounded.History); Chip("版本", "${state.versions.size}", Icons.Rounded.Restore)
    } }
    item { MiuixCard {
        Text("防跑偏约束", style = MaterialTheme.typography.titleMedium)
        Rule("锁定设定不可改写"); Rule("混合向量 RAG 检索相关历史")
        Rule("人物知识、地点和伤势必须连续"); Rule("生成完成后再做一致性门禁")
    } }
    item { MiuixCard {
        val ready = state.provider.ready
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ready) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, null, tint = if (ready) LocalMiuixTokens.current.success else LocalMiuixTokens.current.textSecondary)
            Column(Modifier.padding(start = 10.dp)) {
                Text(if (ready) state.provider.activeProviderLabel else "离线体验模式", fontWeight = FontWeight.Bold)
                Text(if (ready) state.provider.generationModel else "到设置中接入 AI 中转站", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    } }
    item { Button(generate, enabled = !state.isGenerating && !state.isSaving, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp)) {
        if (state.isGenerating) { CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(9.dp)) }
        Text(if (state.isGenerating) "正在流式生成…" else "生成本章正文", style = MaterialTheme.typography.titleMedium)
    } }
    item { AnimatedVisibility(state.isGenerating && state.streamPreview.isNotBlank()) {
        MiuixCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("实时正文", Modifier.padding(start = 9.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(state.streamPreview, color = LocalMiuixTokens.current.textSecondary, maxLines = 16, overflow = TextOverflow.Ellipsis)
        }
    } }
    item { Heading("正文编辑器") }
    item { MiuixCard {
        OutlinedTextField(
            value = state.draft.content,
            onValueChange = editContent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
            label = { Text("章节正文") },
            placeholder = { Text("AI 生成通过一致性检查并保存后会出现在这里，也可以手工编辑。") },
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${state.draft.content.length} 字符", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
            Button(saveDraft, enabled = state.isDraftDirty && !state.isSaving && !state.isGenerating, shape = RoundedCornerShape(15.dp)) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(6.dp)); Text("保存新版本")
            }
        }
    } }
    if (state.versions.isNotEmpty()) {
        item { Heading("版本历史") }
        items(state.versions.take(8), key = { it.id }) { version ->
            VersionRow(version, version.version == state.draft.version, state.isRestoringVersion) { restoreVersion(version.id) }
        }
    }
    item { AnimatedVisibility(state.error != null) { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) } }
}

@Composable private fun MemoryPage(
    s: StorySnapshot,
    addBible: () -> Unit,
    editBible: (BibleEntry) -> Unit,
    deleteBible: (String) -> Unit,
) = Page("长期记忆", "本地混合向量 RAG + 分层摘要 + 可编辑小说圣经") {
    item { MemorySection("小说圣经", "锁定世界规则和叙事边界", s.bible.size, Icons.Rounded.Lock) }
    item { MemorySection("人物状态", "位置、伤势、目标、秘密与物品", s.characters.size, Icons.Rounded.Psychology) }
    item { MemorySection("时间线", "事件先后、地点、参与者和后果", s.recentTimeline.size, Icons.Rounded.Timeline) }
    item { MemorySection("伏笔追踪", "埋设、发展、回收时机和状态", s.relevantForeshadowing.size, Icons.Rounded.Hub) }
    if (s.longTermSummary.isNotBlank()) {
        item { Heading("长期折叠摘要") }
        item { MiuixCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
                Text("较早剧情压缩记忆", Modifier.padding(start = 9.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(9.dp))
            Text(s.longTermSummary, color = LocalMiuixTokens.current.textSecondary, maxLines = 12, overflow = TextOverflow.Ellipsis)
        } }
    }
    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Heading("小说圣经")
        Button(addBible, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("新增设定")
        }
    } }
    if (s.bible.isEmpty()) {
        item { MiuixCard {
            Text("还没有锁定设定", fontWeight = FontWeight.Bold)
            Text("先添加世界观、角色规则、地点、风格或禁用设定，AI 写作会优先遵守这些内容。", color = LocalMiuixTokens.current.textSecondary)
        } }
    }
    items(s.bible, key = { it.id }) { e ->
        MiuixCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp)); Pill(e.category.name, MaterialTheme.colorScheme.primary)
                    }
                    Text(e.content, color = LocalMiuixTokens.current.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ editBible(e) }) { Icon(Icons.Rounded.Edit, "编辑") }
                IconButton({ deleteBible(e.id) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable private fun NewStoryDialog(
    busy: Boolean,
    dismiss: () -> Unit,
    create: (String, String, String, String, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("800000") }
    AlertDialog(
        onDismissRequest = dismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("新建小说") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("书名") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(genre, { genre = it }, Modifier.fillMaxWidth(), label = { Text("类型") }, placeholder = { Text("悬疑 / 仙侠 / 科幻…") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(premise, { premise = it }, Modifier.fillMaxWidth(), label = { Text("核心故事命题") }, placeholder = { Text("一句话说明主角、目标和主要矛盾") }, minLines = 3, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(theme, { theme = it }, Modifier.fillMaxWidth(), label = { Text("主题") }, placeholder = { Text("例如：真相与幸福的代价") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(target, { target = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), label = { Text("目标字数") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Text("创建后会自动生成总纲、第一卷、第一章的可编辑基础结构。", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { create(title, genre, premise, theme, target.toIntOrNull() ?: 800_000) },
                enabled = title.isNotBlank() && premise.isNotBlank() && !busy,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp)); Text("创建并开始写作")
            }
        },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable private fun BibleEditorDialog(
    entry: BibleEntry?,
    busy: Boolean,
    dismiss: () -> Unit,
    save: (BibleCategory, String, String, Boolean) -> Unit,
) {
    var category by remember(entry?.id) { mutableStateOf(entry?.category ?: BibleCategory.WORLD) }
    var name by remember(entry?.id) { mutableStateOf(entry?.name.orEmpty()) }
    var content by remember(entry?.id) { mutableStateOf(entry?.content.orEmpty()) }
    var locked by remember(entry?.id) { mutableStateOf(entry?.locked ?: true) }
    AlertDialog(
        onDismissRequest = dismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (entry == null) "新增小说设定" else "编辑小说设定") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("类别", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    BibleCategory.entries.forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(value.name) },
                        )
                    }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, placeholder = { Text("例如：灵力规则 / 主角底线") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), label = { Text("设定内容") }, minLines = 5, shape = RoundedCornerShape(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("锁定设定", fontWeight = FontWeight.Bold)
                        Text("锁定后 AI 必须优先遵守", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = locked, onCheckedChange = { locked = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { save(category, name, content, locked) }, enabled = name.isNotBlank() && content.isNotBlank() && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(6.dp)); Text("保存")
            }
        },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable private fun SettingsPage(
    p: ProviderUiState,
    name: (String) -> Unit,
    base: (String) -> Unit,
    key: (String) -> Unit,
    detect: () -> Unit,
    select: (DiscoveredModel) -> Unit,
    manual: (String) -> Unit,
    createNew: () -> Unit,
    edit: (String) -> Unit,
    activate: (String) -> Unit,
    delete: (String) -> Unit,
    save: () -> Unit,
) = Page("AI 服务", "多个中转站独立保存、随时切换，密钥加密存储") {
    if (p.savedProviders.isNotEmpty()) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Heading("已保存 AI")
            Pill("${p.savedProviders.size} 个", MaterialTheme.colorScheme.primary)
        } }
        items(p.savedProviders, key = { it.id }) { provider ->
            SavedProviderRow(
                provider = provider,
                active = provider.id == p.activeProviderId,
                onActivate = { activate(provider.id) },
                onEdit = { edit(provider.id) },
                onDelete = { delete(provider.id) },
            )
        }
        item { OutlinedButton(createNew, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("添加新的 AI 服务")
        } }
    }

    item { MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.padding(start = 11.dp)) {
                Text(if (p.editingProviderId == null) "添加 AI 服务" else "编辑 AI 服务", style = MaterialTheme.typography.titleMedium)
                Text("OpenAI · Claude · Gemini · Azure · Ollama", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(15.dp))
        OutlinedTextField(p.providerName, name, Modifier.fillMaxWidth(), label = { Text("名称") }, placeholder = { Text("例如：DeepSeek 主力 / Claude 长文") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(p.baseUrl, base, Modifier.fillMaxWidth(), label = { Text("API 地址") }, placeholder = { Text("https://api.example.com/v1") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(p.apiKey, key, Modifier.fillMaxWidth(), label = { Text(if (p.hasStoredKey) "API Key（留空沿用已保存密钥）" else "API Key") }, leadingIcon = { Icon(Icons.Rounded.Key, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp))
        Text(if (p.hasStoredKey) "已有密钥保存在 Android Keystore 加密存储中。" else "密钥不会写入 Room 数据库或明文配置文件。", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(13.dp))
        Button(detect, enabled = p.baseUrl.isNotBlank() && !p.isDetecting, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp)) {
            if (p.isDetecting) CircularProgressIndicator(Modifier.size(19.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp)); Text(if (p.isDetecting) "正在探测协议…" else "自动识别并获取模型")
        }
    } }
    p.error?.let { message -> item { Box(Modifier.fillMaxWidth().squircleClip(20.dp).background(MaterialTheme.colorScheme.errorContainer).padding(15.dp)) { Text(message, color = MaterialTheme.colorScheme.onErrorContainer) } } }
    p.discovery?.let { d ->
        item { MiuixCard { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = LocalMiuixTokens.current.success)
            Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(d.providerLabel, style = MaterialTheme.typography.titleMedium); Text(d.protocol.label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            Pill("已识别", LocalMiuixTokens.current.success)
        }; Text(d.message, color = LocalMiuixTokens.current.textSecondary) } }
        item { Heading("选择模型") }
        items(d.models) { m -> ModelRow(m, p.selectedModel == m.id) { select(m) } }
        item { OutlinedTextField(p.manualModel, manual, Modifier.fillMaxWidth(), label = { Text(if (d.models.isEmpty()) "模型名 / Azure 部署名" else "或手动填写模型名") }, placeholder = { Text("例如 deepseek-chat") }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
        if (p.transientReady) item {
            Button(save, enabled = !p.isSaving, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                if (p.isSaving) CircularProgressIndicator(Modifier.size(19.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(8.dp)); Text(if (p.editingProviderId == null) "保存并设为当前 AI" else "保存修改并设为当前 AI")
            }
        }
    }
}

@Composable private fun VersionRow(
    version: ChapterVersionUi,
    current: Boolean,
    busy: Boolean,
    restore: () -> Unit,
) {
    val shape = RoundedCornerShape(21.dp)
    Row(
        Modifier.fillMaxWidth().squircleClip(21.dp)
            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = .11f) else LocalMiuixTokens.current.cardBackground.copy(alpha = .94f))
            .border(.6.dp, if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).squircleClip(14.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Text("v${version.version}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(version.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${version.content.length} 字符 · ${version.summary.take(42)}", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) Pill("当前", LocalMiuixTokens.current.success)
        else TextButton(restore, enabled = !busy) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(4.dp)); Text("恢复") }
    }
}

@Composable private fun SavedProviderRow(
    provider: SavedProviderUi,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    Row(
        Modifier.fillMaxWidth().squircleClip(23.dp)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else LocalMiuixTokens.current.cardBackground.copy(alpha = .94f))
            .border(if (active) 1.1.dp else .5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onActivate).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).squircleClip(15.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) { Spacer(Modifier.width(7.dp)); Pill("当前", LocalMiuixTokens.current.success) }
            }
            Text("${provider.model} · ${provider.protocol.label}", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onEdit) { Icon(Icons.Rounded.Edit, "编辑") }
        IconButton(onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun MiuixCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().shadow(2.dp, shape).squircleClip(26.dp).background(LocalMiuixTokens.current.cardBackground.copy(alpha = .94f)).border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), shape).padding(19.dp), content = content)
}
@Composable private fun Heading(t: String) = Text(t, style = MaterialTheme.typography.titleLarge)
@Composable private fun Pill(t: String, c: Color) = Surface(shape = CircleShape, color = c.copy(alpha = .13f)) { Text(t, color = c, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
@Composable private fun ProgressBar(progress: Float) = Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), CircleShape)) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
@Composable private fun Metric(label: String, value: Int, modifier: Modifier) = Column(modifier.squircleClip(20.dp).background(LocalMiuixTokens.current.cardBackground.copy(alpha = .9f)).padding(13.dp)) { Text(label, style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary, maxLines = 1); Text(value.toString(), style = MaterialTheme.typography.headlineSmall) }
@Composable private fun Chip(label: String, value: String, icon: ImageVector) = Row(Modifier.squircleClip(18.dp).background(LocalMiuixTokens.current.cardBackground.copy(alpha = .92f)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)); Column(Modifier.padding(start = 7.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun Rule(t: String) = Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CheckCircle, null, tint = LocalMiuixTokens.current.success, modifier = Modifier.size(18.dp)); Text(t, Modifier.padding(start = 9.dp)) }
@Composable private fun MemorySection(t: String, d: String, n: Int, icon: ImageVector) = MiuixCard { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).squircleClip(16.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }; Column(Modifier.padding(start = 13.dp).weight(1f)) { Text(t, style = MaterialTheme.typography.titleMedium); Text(d, color = LocalMiuixTokens.current.textSecondary) }; Text(n.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) } }
@Composable private fun ModelRow(m: DiscoveredModel, selected: Boolean, click: () -> Unit) { val shape = RoundedCornerShape(20.dp); Row(Modifier.fillMaxWidth().squircleClip(20.dp).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .13f) else LocalMiuixTokens.current.cardBackground).border(if (selected) 1.2.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape).clickable(onClick = click).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(m.displayName, fontWeight = FontWeight.Bold); if (m.displayName != m.id) Text(m.id, style = MaterialTheme.typography.bodySmall) }; if (m.reasoning) Pill("推理", MaterialTheme.colorScheme.primary); if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 7.dp)) } }
@Composable private fun IssueRow(i: ConsistencyIssue) { val c = if (i.severity == IssueSeverity.BLOCKING) MaterialTheme.colorScheme.error else LocalMiuixTokens.current.warning; Column { Pill(i.severity.name, c); Text(i.message) } }

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable private fun MiuixDock(current: AppPage, select: (AppPage) -> Unit, haze: HazeState, backdrop: LayerBackdrop?, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(31.dp)
    val surfaceBackdrop = rememberLayerBackdrop()
    val shellTint = if (dark) MaterialTheme.colorScheme.surface.copy(alpha = .39f) else Color.White.copy(alpha = .4f)
    val shell = if (backdrop != null) Modifier.drawBackdrop(
        backdrop, shape = { shape }, effects = { padding = maxOf(padding, 30.dp.toPx()); colorControls(brightness = .02f, contrast = 1.05f, saturation = 1.4f); blur(9.dp.toPx(), 9.dp.toPx()); liquidGlassLens(17.dp.toPx(), 13.dp.toPx(), true, .045f) },
        highlight = { (if (dark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight).copy(alpha = .82f) }, onDrawSurface = { drawRect(shellTint) }
    ) else Modifier.hazeEffect(haze, HazeMaterials.ultraThin()) { blurRadius = 30.dp; noiseFactor = .018f }.background(if (dark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .35f))
    Box(modifier.padding(horizontal = 12.dp).padding(bottom = inset + 10.dp).fillMaxWidth().height(66.dp)) {
        Box(Modifier.fillMaxSize().shadow(18.dp, shape).squircleClip(31.dp).then(if (backdrop != null) Modifier.layerBackdrop(surfaceBackdrop) else Modifier).then(shell).border(.6.dp, Color.White.copy(alpha = .3f), shape))
        DockItems(current, select, surfaceBackdrop.takeIf { backdrop != null }, dark, Modifier.fillMaxSize().padding(6.dp))
    }
}

@Composable private fun DockItems(current: AppPage, select: (AppPage) -> Unit, backdrop: LayerBackdrop?, dark: Boolean, modifier: Modifier) = BoxWithConstraints(modifier) {
    val width = maxWidth / AppPage.entries.size.toFloat()
    val x by animateDpAsState(width * current.ordinal, spring(dampingRatio = .68f, stiffness = Spring.StiffnessMediumLow), label = "dock")
    val shape = RoundedCornerShape(23.dp)
    val indicatorTint = MaterialTheme.colorScheme.primary.copy(alpha = .18f)
    val lens = if (backdrop != null) Modifier.drawBackdrop(backdrop, shape = { shape }, effects = { padding = maxOf(padding, 22.dp.toPx()); blur(3.dp.toPx(), 3.dp.toPx()); liquidGlassLens(13.dp.toPx(), 14.dp.toPx(), true, .08f) }, highlight = { (if (dark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight).copy(alpha = .88f) }, onDrawSurface = { drawRect(indicatorTint) }) else Modifier.background(indicatorTint.copy(alpha = .72f))
    Box(Modifier.offset(x + 4.dp).width(width - 8.dp).height(54.dp).squircleClip(23.dp).then(lens))
    Row(Modifier.fillMaxWidth()) { AppPage.entries.forEach { p -> val chosen = p == current; val c = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f); Column(Modifier.width(width).height(54.dp).clickable(remember(p) { MutableInteractionSource() }, null) { select(p) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(p.icon, p.label, tint = c, modifier = Modifier.size(22.dp)); Text(p.label, color = c, fontSize = 11.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium) } } }
}
