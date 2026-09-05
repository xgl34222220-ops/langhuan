package com.xiguli.langhuan.ui.writing

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.StoredChapterVersion
import com.xiguli.langhuan.ui.theme.LanghuanShape
import com.xiguli.langhuan.ui.verticalScroll
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterEditorPage(
    novelId: String,
    initialChapter: Int?,
    viewModel: ChapterEditorViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var chapterMenu by remember { mutableStateOf(false) }
    var rewriteInstruction by remember { mutableStateOf("") }
    var showVersions by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<StoredChapterVersion?>(null) }
    var confirmResetAuthorProfile by remember { mutableStateOf(false) }

    LaunchedEffect(novelId, initialChapter) { viewModel.load(novelId, initialChapter) }
    LaunchedEffect(state.message, state.error) {
        val notice = state.error ?: state.message
        if (!notice.isNullOrBlank()) {
            snackbar.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    fun closeSafely() = viewModel.flushAndClose(onClose)
    BackHandler { closeSafely() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::closeSafely) { Icon(Icons.Rounded.ArrowBack, "保存并返回") }
                },
                title = {
                    Column {
                        Text("正文编辑", fontWeight = FontWeight.Bold)
                        state.draft?.let { draft ->
                            Text("第${draft.chapterNumber}章 · ${draft.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::saveCheckpoint,
                        enabled = state.ready && !state.busy,
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("建版本")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading || !state.ready) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在载入正文与版本历史……")
                }
            }
            return@Scaffold
        }

        val draft = state.draft ?: return@Scaffold
        val chapters = state.chapters.sortedBy { it.chapterNumber }
        val index = chapters.indexOfFirst { it.chapterNumber == draft.chapterNumber }
        val previous = chapters.getOrNull(index - 1)
        val next = chapters.getOrNull(index + 1)

        var editor by remember(draft.id) {
            mutableStateOf(TextFieldValue(draft.content, TextRange(draft.content.length)))
        }
        LaunchedEffect(draft.id, draft.content) {
            if (editor.text != draft.content) {
                val cursor = min(editor.selection.end, draft.content.length)
                editor = TextFieldValue(draft.content, TextRange(cursor))
            }
        }

        val selectionStart = min(editor.selection.start, editor.selection.end).coerceIn(0, editor.text.length)
        val selectionEnd = max(editor.selection.start, editor.selection.end).coerceIn(selectionStart, editor.text.length)
        val selectedText = editor.text.substring(selectionStart, selectionEnd)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(state.snapshot?.novel?.title.orEmpty(), fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        state.isSaving -> "正在保存……"
                                        state.dirty -> "有未保存修改 · 1.1 秒后自动保存"
                                        else -> "已自动保存 · 历史版本 ${state.versions.size} 个"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                AssistChip(
                                    onClick = { chapterMenu = true },
                                    label = { Text("第${draft.chapterNumber}章") },
                                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) },
                                )
                                DropdownMenu(expanded = chapterMenu, onDismissRequest = { chapterMenu = false }) {
                                    chapters.forEach { chapter ->
                                        DropdownMenuItem(
                                            text = { Text("第${chapter.chapterNumber}章 · ${chapter.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                chapterMenu = false
                                                viewModel.openChapter(chapter.chapterNumber)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { previous?.let { viewModel.openChapter(it.chapterNumber) } },
                                enabled = previous != null && !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.ChevronLeft, null)
                                Text("上一章")
                            }
                            OutlinedButton(
                                onClick = { next?.let { viewModel.openChapter(it.chapterNumber) } },
                                enabled = next != null && !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("下一章")
                                Icon(Icons.Rounded.ChevronRight, null)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("章节标题") },
                    singleLine = true,
                    shape = LanghuanShape.card,
                )
            }

            item {
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("正文", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${editor.text.length} 字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editor,
                            onValueChange = { value ->
                                editor = value
                                viewModel.updateContent(value.text)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 20,
                            label = { Text("在这里直接修改正文") },
                            placeholder = { Text("可以手写、粘贴，也可以选中一段交给 AI 局部重写。") },
                            shape = LanghuanShape.card,
                        )
                    }
                }
            }

            item {
                val profile = state.snapshot?.longForm?.authorProfile
                val learnedRules = profile?.rules.orEmpty()
                    .filter { it.active && it.confidence >= 60 }
                    .sortedByDescending { it.confidence }
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("作者编辑画像", fontWeight = FontWeight.Bold)
                                Text(
                                    "从稳定保存后的实际改稿学习；单次小改不会直接变成长期规则。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = profile?.enabled ?: true,
                                onCheckedChange = viewModel::setAuthorLearningEnabled,
                                enabled = !state.busy,
                            )
                        }
                        Text(
                            "稳定规则 ${learnedRules.size} 条 · 手动改稿 ${profile?.manualEditBatches ?: 0} 批 · 采用 AI 改写 ${profile?.acceptedAiRewrites ?: 0} 次 · 明确拒绝 ${profile?.rejectedAiRewrites ?: 0} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        learnedRules.take(4).forEach { rule ->
                            Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(rule.instruction, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("置信 ${rule.confidence} · ${rule.evidenceCount} 次证据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (learnedRules.isEmpty()) {
                            Text("还没有达到稳定阈值的偏好。继续正常改稿即可，不需要专门训练。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if ((profile?.rules?.isNotEmpty() == true) || (profile?.recentSignals?.isNotEmpty() == true)) {
                            TextButton(onClick = { confirmResetAuthorProfile = true }, enabled = !state.busy) {
                                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("重置画像")
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = LanghuanShape.panel,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .34f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("时间线体检 / 旧稿修复", fontWeight = FontWeight.Bold)
                                Text("先用本地规则找时间锚点和硬冲突，再让 AI 只修时间桥与场景归属；不会直接覆盖原稿。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val chronology = state.chronologyReport
                        if (chronology == null) {
                            Button(
                                onClick = viewModel::analyzeChronology,
                                enabled = !state.isAnalyzingChronology,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isAnalyzingChronology) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Troubleshoot, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (state.isAnalyzingChronology) "正在扫描时间锚点" else "扫描本章时间线")
                            }
                        } else {
                            val riskColor = if (chronology.overallRisk.label == "高") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                            Text("${chronology.overallRisk.label}风险 · ${chronology.anchors.size} 个时间锚点 · ${chronology.findings.size} 个问题", color = riskColor, fontWeight = FontWeight.Bold)
                            chronology.findings.take(6).forEach { finding ->
                                Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("${finding.risk.label} · ${finding.code} · 第${finding.paragraph}段", fontWeight = FontWeight.SemiBold, color = if (finding.risk.label == "高") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        Text(finding.title, fontWeight = FontWeight.SemiBold)
                                        Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("建议：${finding.repair}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (chronology.findings.isEmpty()) {
                                Text("本地规则暂未发现硬冲突。仍可让 AI 做语义级复核，重点检查当前场景/历史资料/梦境时间层是否清楚。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = viewModel::analyzeChronology, modifier = Modifier.weight(1f), enabled = !state.isAnalyzingChronology && !state.busy) {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("重新扫描")
                                }
                                Button(onClick = viewModel::generateChronologyRepair, modifier = Modifier.weight(1f), enabled = !state.busy && draft.content.isNotBlank()) {
                                    if (state.isRepairingChronology) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (state.isRepairingChronology) "修复中" else "AI 最小修复")
                                }
                            }
                        }
                    }
                }
            }

            if (selectedText.isNotBlank()) {
                item {
                    Surface(shape = LanghuanShape.panel, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("AI 局部重写 · 已选 ${selectedText.length} 字", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                selectedText.take(180) + if (selectedText.length > 180) "……" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = rewriteInstruction,
                                onValueChange = { rewriteInstruction = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("怎么改（可选）") },
                                placeholder = { Text("例如：对白更自然；减少网文腔；加强压迫感，但不要改剧情事实") },
                                minLines = 2,
                                shape = LanghuanShape.card,
                            )
                            Button(
                                onClick = { viewModel.rewriteSelection(selectionStart, selectionEnd, rewriteInstruction) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isRewriting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.AutoAwesome, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (state.isRewriting) "正在精修选区" else "让 AI 只重写选中部分")
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("章节事实依赖", fontWeight = FontWeight.Bold)
                                Text("删除、整章大改或回滚前先看这一章影响了什么。依赖判定在本地完成，AI 只生成修复方案。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val report = state.dependencyReport
                        if (report == null) {
                            Button(
                                onClick = viewModel::analyzeDependencies,
                                enabled = !state.isAnalyzingDependencies && !state.dirty,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isAnalyzingDependencies) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Search, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (state.dirty) "等自动保存后检查" else if (state.isAnalyzingDependencies) "正在检查" else "检查本章对后续的影响")
                            }
                        } else {
                            val riskColor = if (report.overallRisk.label == "高") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Text("${report.overallRisk.label}风险 · 高风险 ${report.highCount} · 中风险 ${report.mediumCount} · 影响后续 ${report.downstreamChapterCount} 章", color = riskColor, fontWeight = FontWeight.Bold)
                            Text(report.recommendation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            report.all.take(8).forEach { impact ->
                                Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text("${impact.risk.label} · ${impact.kind.label} · ${impact.title}", fontWeight = FontWeight.SemiBold)
                                        Text(impact.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (report.all.size > 8) Text("还有 ${report.all.size - 8} 项未展开", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = viewModel::analyzeDependencies, modifier = Modifier.weight(1f), enabled = !state.isAnalyzingDependencies) { Text("重新检查") }
                                Button(onClick = viewModel::generateRepairPlan, modifier = Modifier.weight(1f), enabled = !state.isPlanningRepair) {
                                    if (state.isPlanningRepair) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (state.isPlanningRepair) "规划中" else "AI 修复计划")
                                }
                            }
                            state.repairPlan?.let { plan ->
                                Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f)) {
                                    SelectionContainer { Text(plan, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = LanghuanShape.panel, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("版本历史", fontWeight = FontWeight.Bold)
                                Text("自动保存不会刷版本；只有“建版本”、AI 正文保存和回滚才形成历史节点。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showVersions = !showVersions }) { Text(if (showVersions) "收起" else "展开") }
                        }
                    }
                }
            }

            if (showVersions) {
                if (state.versions.isEmpty()) {
                    item {
                        Text("还没有历史版本。编辑到一个阶段后点右上角“建版本”即可留档。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(state.versions, key = { it.id }) { version ->
                        VersionRow(
                            version = version,
                            currentVersion = draft.version,
                            onCompare = { viewModel.compare(version) },
                            onRestore = {
                                restoreTarget = version
                                viewModel.analyzeDependencies()
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    state.chronologyProposal?.let { proposal ->
        var prefix = 0
        val maxPrefix = minOf(proposal.original.length, proposal.repaired.length)
        while (prefix < maxPrefix && proposal.original[prefix] == proposal.repaired[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(proposal.original.length - prefix, proposal.repaired.length - prefix)
        while (suffix < maxSuffix && proposal.original[proposal.original.length - 1 - suffix] == proposal.repaired[proposal.repaired.length - 1 - suffix]) suffix++
        val oldEnd = (proposal.original.length - suffix).coerceAtLeast(prefix)
        val newEnd = (proposal.repaired.length - suffix).coerceAtLeast(prefix)
        val oldChanged = proposal.original.substring(prefix, oldEnd)
        val newChanged = proposal.repaired.substring(prefix, newEnd)
        AlertDialog(
            onDismissRequest = viewModel::dismissChronologyRepair,
            title = { Text("确认时间线修复") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("应用前会自动把当前原稿建立一个永久版本。AI 只被允许修时间桥、场景归属和硬时间矛盾；请仍然核对下面变化。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .4f)) {
                        SelectionContainer { Text(proposal.diagnosis, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                    }
                    Text("原文变化段 · ${oldChanged.length} 字", fontWeight = FontWeight.Bold)
                    Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.surfaceVariant) {
                        SelectionContainer { Text(oldChanged.ifBlank { "（无变化）" }, Modifier.padding(12.dp)) }
                    }
                    Text("修复后变化段 · ${newChanged.length} 字", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .42f)) {
                        SelectionContainer { Text(newChanged.ifBlank { "（无变化）" }, Modifier.padding(12.dp)) }
                    }
                }
            },
            confirmButton = { Button(onClick = viewModel::applyChronologyRepair) { Text("备份原稿并应用") } },
            dismissButton = { TextButton(onClick = viewModel::dismissChronologyRepair) { Text("不要这版") } },
        )
    }

    state.rewriteProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRewrite,
            title = { Text("确认局部重写") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI 只会替换刚才选中的部分。应用后会把这次明确采用的修改要求计入作者编辑画像，但不会写入剧情事实记忆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("原文", fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(proposal.original) }
                    HorizontalDivider()
                    Text("修改后", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SelectionContainer { Text(proposal.replacement) }
                }
            },
            confirmButton = { Button(onClick = viewModel::applyRewrite) { Text("应用替换") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = viewModel::dismissRewrite) { Text("暂不采用") }
                    TextButton(onClick = viewModel::rejectRewriteAndLearn) { Text("不喜欢，记住") }
                }
            },
        )
    }

    if (confirmResetAuthorProfile) {
        AlertDialog(
            onDismissRequest = { confirmResetAuthorProfile = false },
            title = { Text("重置作者编辑画像？") },
            text = { Text("会清空从改稿中学习到的偏好规则和编辑信号，但不会修改正文、设定、大纲或历史版本。") },
            confirmButton = {
                Button(onClick = {
                    confirmResetAuthorProfile = false
                    viewModel.clearAuthorProfile()
                }) { Text("确认重置") }
            },
            dismissButton = { TextButton(onClick = { confirmResetAuthorProfile = false }) { Text("取消") } },
        )
    }

    state.comparison?.let { comparison ->
        AlertDialog(
            onDismissRequest = viewModel::dismissComparison,
            title = { Text("v${comparison.version.version} ↔ 当前稿") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("共同前缀 ${comparison.prefixChars} 字 · 共同后缀 ${comparison.suffixChars} 字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("历史版本变化段 · ${comparison.oldChanged.length} 字", fontWeight = FontWeight.Bold)
                    Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.surfaceVariant) {
                        SelectionContainer { Text(comparison.oldChanged.ifBlank { "（这一段为空）" }, Modifier.padding(12.dp)) }
                    }
                    Text("当前稿变化段 · ${comparison.currentChanged.length} 字", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                        SelectionContainer { Text(comparison.currentChanged.ifBlank { "（这一段为空）" }, Modifier.padding(12.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissComparison) { Text("关闭") } },
        )
    }

    restoreTarget?.let { version ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("恢复 v${version.version}？") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("不会覆盖历史。恢复后的内容会保存成一个新版本。")
                    if (state.isAnalyzingDependencies) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在检查人物、时间线、伏笔和后续章节依赖……")
                        }
                    } else {
                        state.dependencyReport?.let { report ->
                            val riskColor = if (report.overallRisk.label == "高") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Text("${report.overallRisk.label}风险 · ${report.all.size} 项依赖", color = riskColor, fontWeight = FontWeight.Bold)
                            Text(report.recommendation, style = MaterialTheme.typography.bodySmall)
                            report.all.take(5).forEach { Text("• ${it.kind.label}：${it.title}", style = MaterialTheme.typography.bodySmall) }
                            if (report.all.size > 5) Text("• 另有 ${report.all.size - 5} 项，请在“章节事实依赖”中查看", style = MaterialTheme.typography.bodySmall)
                        } ?: Text("尚未得到依赖结果。建议取消后先运行章节事实依赖检查。", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        restoreTarget = null
                        viewModel.restore(version)
                    },
                    enabled = !state.isAnalyzingDependencies && state.dependencyReport != null,
                ) { Text(if (state.dependencyReport?.overallRisk?.label == "高") "已知风险，仍要回滚" else "恢复并创建新版本") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun VersionRow(
    version: StoredChapterVersion,
    currentVersion: Int,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(shape = LanghuanShape.card, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("v${version.version} · ${version.title}", fontWeight = FontWeight.Bold)
                    Text("${version.content.length} 字${if (version.version == currentVersion) " · 当前版本号" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCompare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CompareArrows, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("对比")
                }
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("回滚")
                }
            }
        }
    }
}
