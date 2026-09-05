package com.xiguli.langhuan.ui.cover

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.reader.LibraryExperienceViewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val CoverStudioJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Composable
fun CoverStudioV3(
    bookId: String,
    libraryViewModel: LibraryExperienceViewModel,
    onClose: () -> Unit,
) {
    val state by libraryViewModel.state.collectAsStateWithLifecycle()
    val coverViewModel: CoverStudioViewModel = viewModel()
    val generation by coverViewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook?.takeIf { it.id == bookId }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val t = LocalLanghuanUiTokens.current
    var history by remember(bookId) { mutableStateOf<List<File>>(emptyList()) }
    var applying by remember { mutableStateOf(false) }

    fun refreshHistory() {
        history = coverHistory(appContext, bookId)
    }

    LaunchedEffect(bookId, book?.coverPath, generation.candidatePath) {
        refreshHistory()
    }

    LaunchedEffect(generation.notice, generation.error) {
        val text = generation.error ?: generation.notice
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            coverViewModel.clearNotice()
        }
    }

    if (book == null) {
        Surface(Modifier.fillMaxSize(), color = t.background) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.accent) }
        }
        return
    }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onClose)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("封面工作室", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                            Text("AI 生成背景 · 琅嬛本地排中文 · 确认后才覆盖", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        LanghuanBadge(if (generation.candidatePath != null) "待确认" else "已保存", accent = generation.candidatePath != null)
                    }
                }

                generation.candidatePath?.let { candidatePath ->
                    item {
                        CoverCandidateCard(
                            candidatePath = candidatePath,
                            title = book.title,
                            sourceLabel = generation.sourceLabel,
                            applying = applying,
                            onApply = {
                                scope.launch {
                                    applying = true
                                    val result = runCatching {
                                        val official = promoteCandidate(appContext, bookId, candidatePath)
                                        applyCoverPath(appContext, bookId, official.absolutePath)
                                    }
                                    if (result.isSuccess) {
                                        coverViewModel.consumeCandidate()
                                        libraryViewModel.openBook(bookId)
                                        refreshHistory()
                                        snackbar.showSnackbar("已设为当前封面并永久保存")
                                    } else {
                                        snackbar.showSnackbar(result.exceptionOrNull()?.message ?: "保存封面失败")
                                    }
                                    applying = false
                                }
                            },
                            onDiscard = {
                                runCatching { File(candidatePath).delete() }
                                coverViewModel.consumeCandidate()
                            },
                        )
                    }
                }

                item {
                    CurrentCoverCard(
                        path = book.coverPath,
                        title = book.title,
                        busy = generation.busy,
                        applying = applying,
                        onGenerate = { coverViewModel.generate(book) },
                        onExport = {
                            scope.launch {
                                val result = runCatching { exportCoverToGallery(context, File(book.coverPath), book.title) }
                                snackbar.showSnackbar(result.fold({ "已保存到系统相册" }, { it.message ?: "保存到相册失败" }))
                            }
                        },
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = LanghuanShape.chip,
                            color = t.muted,
                            border = BorderStroke(1.dp, t.border),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = t.mutedForeground)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("封面历史", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                            Text("只有确认采用的封面才会进入版本历史", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        LanghuanIconButton(Icons.Rounded.Refresh, "刷新", { refreshHistory() })
                    }
                }

                if (history.isEmpty()) {
                    item {
                        LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                            Text(
                                "还没有历史封面。生成并采用一个新方案后，这里会保留版本。",
                                color = t.mutedForeground,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    items(history, key = { it.absolutePath }) { file ->
                        val active = file.absolutePath == book.coverPath
                        CoverHistoryRow(
                            file = file,
                            title = book.title,
                            active = active,
                            enabled = !active && !applying,
                            onRestore = {
                                scope.launch {
                                    applying = true
                                    val result = runCatching { applyCoverPath(appContext, bookId, file.absolutePath) }
                                    if (result.isSuccess) {
                                        libraryViewModel.openBook(bookId)
                                        snackbar.showSnackbar("已恢复这张封面")
                                    } else {
                                        snackbar.showSnackbar(result.exceptionOrNull()?.message ?: "恢复封面失败")
                                    }
                                    applying = false
                                }
                            },
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
        }
    }
}

@Composable
private fun CoverCandidateCard(
    candidatePath: String,
    title: String,
    sourceLabel: String,
    applying: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.panel,
        color = t.warmSurface,
        contentColor = t.foreground,
        border = BorderStroke(1.dp, t.accent.copy(alpha = .18f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = LanghuanShape.chip,
                    color = t.card,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = t.accent)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text("新方案待确认", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text(sourceLabel.ifBlank { "封面生成" }, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge("PREVIEW", accent = true)
            }

            Spacer(Modifier.height(14.dp))
            CoverPreviewV3(candidatePath, title, Modifier.width(240.dp).height(342.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "这张图目前只是预览，没有覆盖当前封面。AI 只负责无文字背景，中文书名由琅嬛本地渲染。",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onApply,
                enabled = !applying,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = LanghuanShape.card,
                colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
            ) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (applying) "正在保存…" else "采用并设为当前封面")
            }
            TextButton(onClick = onDiscard, enabled = !applying) {
                Text("放弃这个方案", color = t.mutedForeground)
            }
        }
    }
}

@Composable
private fun CurrentCoverCard(
    path: String,
    title: String,
    busy: Boolean,
    applying: Boolean,
    onGenerate: () -> Unit,
    onExport: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前封面", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text(
                        if (File(path).isFile) "已持久化保存" else "当前封面文件不可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (File(path).isFile) t.mutedForeground else t.destructive,
                    )
                }
                LanghuanBadge(if (File(path).isFile) "CURRENT" else "ERROR", accent = File(path).isFile)
            }
            Spacer(Modifier.height(12.dp))
            CoverPreviewV3(path, title, Modifier.width(220.dp).height(314.dp))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onGenerate,
                enabled = !busy && !applying,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = LanghuanShape.card,
                colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 1.8.dp, color = t.primaryForeground)
                else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (busy) "正在生成背景与排版…" else "生成新方案")
            }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(
                onClick = onExport,
                enabled = File(path).isFile && !applying,
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.card,
                border = BorderStroke(1.dp, t.border),
            ) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("保存当前封面到相册")
            }
        }
    }
}

@Composable
private fun CoverHistoryRow(
    file: File,
    title: String,
    active: Boolean,
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onRestore),
        shape = LanghuanShape.card,
        color = if (active) t.warmSurface else t.card,
        border = BorderStroke(1.dp, if (active) t.accent.copy(alpha = .18f) else t.border),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverPreviewV3(file.absolutePath, title, Modifier.width(70.dp).height(100.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(if (active) "当前封面" else "历史方案", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                Text(
                    file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = t.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("${(file.length() / 1024L).coerceAtLeast(1)} KB", color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
            }
            if (active) LanghuanBadge("当前", accent = true)
            else Text("点击恢复", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
        }
    }
}

@Composable
fun CoverPreviewV3(path: String, title: String, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    val stamp = runCatching { File(path).lastModified() }.getOrDefault(0L)
    val bitmap = remember(path, stamp) {
        path.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = title,
            modifier = modifier.clip(LanghuanShape.cover),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(LanghuanShape.cover)
                .background(t.muted),
            contentAlignment = Alignment.Center,
        ) {
            Text(title.take(10), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground)
        }
    }
}

private fun coverHistory(context: Context, bookId: String): List<File> = File(context.filesDir, "covers")
    .listFiles()
    ?.filter { it.isFile && it.length() > 0L && it.name.startsWith("$bookId-") && it.extension.equals("png", true) }
    ?.sortedByDescending { it.lastModified() }
    .orEmpty()

private suspend fun promoteCandidate(context: Context, bookId: String, path: String): File = withContext(Dispatchers.IO) {
    val candidate = File(path)
    require(candidate.isFile && candidate.length() > 0L) { "候选封面文件不存在" }
    val dir = File(context.filesDir, "covers").apply { mkdirs() }
    var stamp = System.currentTimeMillis()
    var target = File(dir, "$bookId-$stamp.png")
    while (target.exists()) {
        stamp += 1
        target = File(dir, "$bookId-$stamp.png")
    }
    candidate.copyTo(target, overwrite = false)
    candidate.delete()
    target
}

private suspend fun applyCoverPath(context: Context, bookId: String, path: String) = withContext(Dispatchers.IO) {
    val file = File(path)
    require(file.isFile && file.length() > 0L) { "封面文件不存在" }
    val dao = LanghuanDatabase.get(context).storyStateDao()
    val row = dao.get(bookId) ?: error("找不到这本小说")
    val snapshot = CoverStudioJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
    val updated = snapshot.copy(novel = snapshot.novel.copy(coverPath = file.absolutePath))
    dao.upsert(
        row.copy(
            snapshotJson = CoverStudioJson.encodeToString(StorySnapshot.serializer(), updated),
            updatedAt = System.currentTimeMillis(),
        )
    )
}

private suspend fun exportCoverToGallery(context: Context, source: File, title: String) = withContext(Dispatchers.IO) {
    require(source.isFile && source.length() > 0L) { "当前封面文件不存在" }
    val resolver = context.contentResolver
    val displayName = "${title.ifBlank { "琅嬛封面" }}-${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/琅嬛")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("系统相册无法创建文件")
    try {
        resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            ?: error("无法写入系统相册")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}
