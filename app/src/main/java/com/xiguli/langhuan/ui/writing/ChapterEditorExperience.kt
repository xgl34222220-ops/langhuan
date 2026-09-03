package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlin.math.max
import kotlin.math.min

/**
 * Focused day-to-day chapter editor. Complex chronology/dependency/version tools remain available
 * through the legacy inspector until each inspector is migrated into functional files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterEditorExperience(
    novelId: String,
    initialChapter: Int?,
    viewModel: ChapterEditorViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalLanghuanUiTokens.current
    val snackbar = remember { SnackbarHostState() }
    var showAdvanced by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(false) }
    var rewriteInstruction by remember { mutableStateOf("") }

    LaunchedEffect(novelId, initialChapter) { viewModel.load(novelId, initialChapter) }
    LaunchedEffect(state.message, state.error) {
        val notice = state.error ?: state.message
        if (!notice.isNullOrBlank()) {
            snackbar.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    fun closeSafely() = viewModel.flushAndClose(onClose)
    BackHandler {
        if (showAdvanced) showAdvanced = false else closeSafely()
    }

    if (showAdvanced) {
        ChapterEditorPage(
            novelId = novelId,
            initialChapter = state.draft?.chapterNumber ?: initialChapter,
            viewModel = viewModel,
            onClose = { showAdvanced = false },
        )
        return
    }

    Scaffold(
        containerColor = t.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = ::closeSafely) { Icon(Icons.Rounded.ArrowBack, "保存并返回", tint = t.foreground) } },
                title = {
                    Column {
                        Text("正文编辑", color = t.foreground, fontWeight = FontWeight.SemiBold)
                        Text(
                            state.draft?.let { "第 ${it.chapterNumber} 章 · ${it.title}" } ?: "正在载入",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAdvanced = true }, enabled = state.ready) { Icon(Icons.Rounded.Tune, "高级检查", tint = t.foreground) }
                    TextButton(onClick = viewModel::saveCheckpoint, enabled = state.ready && !state.busy) { Text("建版本") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.isLoading || !state.ready) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("正在载入正文……", color = t.mutedForeground)
                }
            }
            return@Scaffold
        }

        val draft = state.draft ?: return@Scaffold
        val chapters = state.chapters.sortedBy { it.chapterNumber }
        val index = chapters.indexOfFirst { it.chapterNumber == draft.chapterNumber }.coerceAtLeast(0)
        val previous = chapters.getOrNull(index - 1)
        val next = chapters.getOrNull(index + 1)

        var editor by remember(draft.id) { mutableStateOf(TextFieldValue(draft.content, TextRange(draft.content.length))) }
        LaunchedEffect(draft.id, draft.content) {
            if (editor.text != draft.content) {
                val cursor = min(editor.selection.end, draft.content.length)
                editor = TextFieldValue(draft.content, TextRange(cursor))
            }
        }

        val selectionStart = min(editor.selection.start, editor.selection.end).coerceIn(0, editor.text.length)
        val selectionEnd = max(editor.selection.start, editor.selection.end).coerceIn(selectionStart, editor.text.length)
        val selectedText = editor.text.substring(selectionStart, selectionEnd)

        Column(Modifier.fillMaxSize().padding(inner)) {
            Surface(
                color = t.card,
                border = BorderStroke(1.dp, t.border),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            AssistChip(
                                onClick = { chapterMenu = true },
                                label = { Text("第 ${draft.chapterNumber} 章") },
                                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                            )
                            DropdownMenu(expanded = chapterMenu, onDismissRequest = { chapterMenu = false }) {
                                chapters.forEach { chapter ->
                                    DropdownMenuItem(
                                        text = { Text("第 ${chapter.chapterNumber} 章 · ${chapter.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = { chapterMenu = false; viewModel.openChapter(chapter.chapterNumber) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        EditorSaveState(state)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { previous?.let { viewModel.openChapter(it.chapterNumber) } },
                            enabled = previous != null && !state.busy,
                            modifier = Modifier.weight(1f),
                        ) { Icon(Icons.Rounded.ChevronLeft, null); Text("上一章") }
                        OutlinedButton(
                            onClick = { next?.let { viewModel.openChapter(it.chapterNumber) } },
                            enabled = next != null && !state.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("下一章"); Icon(Icons.Rounded.ChevronRight, null) }
                    }
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("章节标题") },
                    singleLine = true,
                    shape = RoundedCornerShape(t.radiusMd),
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusLg),
                    color = t.card,
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("正文", color = t.foreground, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("${editor.text.length} 字", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        }
                        OutlinedTextField(
                            value = editor,
                            onValueChange = { value -> editor = value; viewModel.updateContent(value.text) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            minLines = 22,
                            maxLines = 60,
                            placeholder = { Text("直接写正文；选中一段后，下方会出现 AI 局部精修。") },
                            textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, lineHeight = 29.sp),
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = t.background,
                                unfocusedContainerColor = t.background,
                                focusedBorderColor = t.ring,
                                unfocusedBorderColor = t.border,
                            ),
                        )
                    }
                }

                if (selectedText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(t.radiusLg),
                        color = t.warmSurface,
                        border = BorderStroke(1.dp, t.accent.copy(alpha = .24f)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoFixHigh, null, tint = t.accent)
                                Text("AI 局部精修 · 已选 ${selectedText.length} 字", Modifier.padding(start = 8.dp), color = t.foreground, fontWeight = FontWeight.SemiBold)
                            }
                            Text(selectedText.take(180) + if (selectedText.length > 180) "……" else "", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            OutlinedTextField(
                                rewriteInstruction,
                                { rewriteInstruction = it },
                                Modifier.fillMaxWidth(),
                                label = { Text("怎么改（可留空）") },
                                placeholder = { Text("例如：对白更自然、减少网文腔，但不要改变剧情事实") },
                                minLines = 2,
                            )
                            Button(
                                onClick = { viewModel.rewriteSelection(selectionStart, selectionEnd, rewriteInstruction) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isRewriting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.AutoAwesome, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (state.isRewriting) "正在精修" else "只重写选中部分")
                            }
                        }
                    }
                }

                EditorAdvancedSummary(state = state, onOpen = { showAdvanced = true })
                Spacer(Modifier.navigationBarsPadding().height(22.dp))
            }
        }
    }

    state.rewriteProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRewrite,
            title = { Text("确认局部精修") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("只替换刚才选中的正文，剧情事实与选区外内容保持不变。", color = t.mutedForeground)
                    Text("原文", fontWeight = FontWeight.SemiBold)
                    SelectionContainer { Text(proposal.original) }
                    HorizontalDivider(color = t.border)
                    Text("修改后", fontWeight = FontWeight.SemiBold, color = t.accent)
                    SelectionContainer { Text(proposal.replacement) }
                }
            },
            confirmButton = { Button(onClick = viewModel::applyRewrite) { Text("应用替换") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRewrite) { Text("不要这版") } },
        )
    }
}

@Composable
private fun EditorSaveState(state: ChapterEditorUiState) {
    val t = LocalLanghuanUiTokens.current
    val (label, color) = when {
        state.isSaving -> "保存中" to t.warning
        state.dirty -> "待自动保存" to t.accent
        else -> "已保存" to t.success
    }
    Surface(shape = RoundedCornerShape(99.dp), color = color.copy(alpha = .10f)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.isSaving) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = color)
            else Icon(if (state.dirty) Icons.Rounded.Edit else Icons.Rounded.Check, null, Modifier.size(13.dp), tint = color)
            Text(label, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun EditorAdvancedSummary(state: ChapterEditorUiState, onOpen: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val chronology = state.chronologyReport
    val dependency = state.dependencyReport
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusLg),
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(t.radiusMd), color = t.muted) {
                    Icon(Icons.Rounded.FactCheck, null, Modifier.padding(8.dp).size(18.dp), tint = t.foreground)
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("高级检查", color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text("时间线、事实依赖、作者画像、版本比较与恢复", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            if (chronology != null || dependency != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chronology?.let {
                        EditorInspectorBadge("时间 ${it.overallRisk.label}风险", if (it.overallRisk.label == "高") t.destructive else t.warning)
                    }
                    dependency?.let {
                        EditorInspectorBadge("依赖 ${it.overallRisk.label}风险", if (it.overallRisk.label == "高") t.destructive else t.warning)
                    }
                }
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(7.dp))
                Text("打开高级检查")
            }
        }
    }
}

@Composable
private fun EditorInspectorBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(99.dp), color = color.copy(alpha = .10f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
