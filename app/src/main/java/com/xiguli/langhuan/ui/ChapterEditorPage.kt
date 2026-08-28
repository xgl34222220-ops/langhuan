package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.StoredChapterVersion
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
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
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
                    shape = RoundedCornerShape(18.dp),
                )
            }

            item {
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
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
                            shape = RoundedCornerShape(18.dp),
                        )
                    }
                }
            }

            if (selectedText.isNotBlank()) {
                item {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
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
                                shape = RoundedCornerShape(16.dp),
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
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
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
                            onRestore = { restoreTarget = version },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    state.rewriteProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRewrite,
            title = { Text("确认局部重写") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI 只会替换刚才选中的部分。应用后仍会先进入自动保存，不会自动写入长期事实记忆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("原文", fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(proposal.original) }
                    HorizontalDivider()
                    Text("修改后", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SelectionContainer { Text(proposal.replacement) }
                }
            },
            confirmButton = { Button(onClick = viewModel::applyRewrite) { Text("应用替换") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRewrite) { Text("不要这版") } },
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
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        SelectionContainer { Text(comparison.oldChanged.ifBlank { "（这一段为空）" }, Modifier.padding(12.dp)) }
                    }
                    Text("当前稿变化段 · ${comparison.currentChanged.length} 字", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
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
            text = { Text("不会覆盖历史。恢复后的内容会保存成一个新的版本，所以仍然可以再回到当前稿。") },
            confirmButton = {
                Button(onClick = {
                    restoreTarget = null
                    viewModel.restore(version)
                }) { Text("恢复并创建新版本") }
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
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
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
