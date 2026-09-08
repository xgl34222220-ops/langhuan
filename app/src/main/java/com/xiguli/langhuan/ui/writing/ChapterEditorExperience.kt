package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
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
                navigationIcon = {
                    Box(Modifier.padding(start = 10.dp)) {
                        LanghuanIconButton(Icons.Rounded.ArrowBack, "保存并返回", ::closeSafely)
                    }
                },
                title = {
                    Column(Modifier.padding(start = 4.dp)) {
                        Text("写作", color = t.foreground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                    LanghuanIconButton(Icons.Rounded.Tune, "高级检查", { showAdvanced = true }, selected = showAdvanced)
                    TextButton(onClick = viewModel::saveCheckpoint, enabled = state.ready && !state.busy) { Text("建版本", color = t.primary) }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.isLoading || !state.ready) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = t.primary)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(t.radiusLg),
                color = t.card,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Surface(
                                onClick = { chapterMenu = true },
                                shape = RoundedCornerShape(999.dp),
                                color = t.muted,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("第 ${draft.chapterNumber} 章", color = t.foreground, style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp), tint = t.mutedForeground)
                                }
                            }
                            DropdownMenu(expanded = chapterMenu, onDismissRequest = { chapterMenu = false }, containerColor = t.card) {
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
                        Button(
                            onClick = { previous?.let { viewModel.openChapter(it.chapterNumber) } },
                            enabled = previous != null && !state.busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, null)
                            Text("上一章")
                        }
                        Button(
                            onClick = { next?.let { viewModel.openChapter(it.chapterNumber) } },
                            enabled = next != null && !state.busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
                        ) {
                            Text("下一章")
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("章节标题") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = t.foreground),
                    shape = RoundedCornerShape(t.radiusMd),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = t.foreground,
                        unfocusedTextColor = t.foreground,
                    ),
                )

                LanghuanCard(modifier = Modifier.fillMaxWidth(), depth = 2, contentPadding = 0.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("正文", color = t.foreground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("${editor.text.length} 字", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        }
                        OutlinedTextField(
                            value = editor,
                            onValueChange = { value -> editor = value; viewModel.updateContent(value.text) },
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            minLines = 22,
                            maxLines = 60,
                            placeholder = { Text("直接写正文；选中一段后，下方会出现 AI 局部精修。", color = t.mutedForeground) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, lineHeight = 29.sp, color = t.foreground),
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = t.primary,
                            ),
                        )
                    }
                }

                if (selectedText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(t.radiusLg),
                        color = t.warmSurface,
                        shadowElevation = 5.dp,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoFixHigh, null, tint = t.primary)
                                Text("AI 局部精修 · 已选 ${selectedText.length} 字", Modifier.padding(start = 8.dp), color = t.foreground, fontWeight = FontWeight.SemiBold)
                            }
                            Text(selectedText.take(180) + if (selectedText.length > 180) "……" else "", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            OutlinedTextField(
                                rewriteInstruction,
                                { rewriteInstruction = it },
                                Modifier.fillMaxWidth(),
                                placeholder = { Text("例如：对白更自然、减少网文腔，但不要改变剧情事实") },
                                minLines = 2,
                                shape = RoundedCornerShape(t.radiusMd),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = t.card.copy(alpha = .72f),
                                    unfocusedContainerColor = t.card.copy(alpha = .58f),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                ),
                            )
                            Button(
                                onClick = { viewModel.rewriteSelection(selectionStart, selectionEnd, rewriteInstruction) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(t.radiusMd),
                                colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.primaryForeground),
                            ) {
                                if (state.isRewriting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primaryForeground)
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
                    Text("原文", fontWeight = FontWeight.SemiBold, color = t.foreground)
                    SelectionContainer { Text(proposal.original, color = t.foreground) }
                    HorizontalDivider(color = t.track)
                    Text("修改后", fontWeight = FontWeight.SemiBold, color = t.primary)
                    SelectionContainer { Text(proposal.replacement, color = t.foreground) }
                }
            },
            confirmButton = { Button(onClick = viewModel::applyRewrite) { Text("应用替换") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRewrite) { Text("不要这版") } },
            containerColor = t.card,
            shape = RoundedCornerShape(t.radiusLg),
        )
    }
}

@Composable
private fun EditorSaveState(state: ChapterEditorUiState) {
    val t = LocalLanghuanUiTokens.current
    val (label, color) = when {
        state.isSaving -> "保存中" to t.warning
        state.dirty -> "待自动保存" to t.primary
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
    LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(t.radiusMd),
                colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
            ) {
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
