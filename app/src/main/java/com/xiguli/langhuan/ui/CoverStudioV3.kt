package com.xiguli.langhuan.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.Rounded.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("封面工作室", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("AI 背景 · 本地中文排版 · 预览后再采用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("当前封面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(220.dp).height(314.dp))
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (File(book.coverPath).isFile) "已持久化保存" else "当前封面文件不可用",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { coverViewModel.generate(book) },
                            enabled = !generation.busy && !applying,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            if (generation.busy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (generation.busy) "正在生成背景与排版…" else "生成新封面")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = runCatching { exportCoverToGallery(context, File(book.coverPath), book.title) }
                                    snackbar.showSnackbar(result.fold({ "已保存到系统相册" }, { it.message ?: "保存到相册失败" }))
                                }
                            },
                            enabled = File(book.coverPath).isFile && !applying,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("保存当前封面到相册")
                        }
                    }
                }
            }

            generation.candidatePath?.let { candidatePath ->
                item {
                    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("新方案预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        generation.sourceLabel.ifBlank { "封面生成" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            CoverPreviewV3(candidatePath, book.title, Modifier.width(240.dp).height(342.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "这张图还没有覆盖当前封面。AI 只负责无文字背景，中文书名由琅嬛本地渲染。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
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
                                enabled = !applying,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(17.dp),
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null)
                                Spacer(Modifier.width(7.dp))
                                Text("设为当前封面")
                            }
                            TextButton(
                                onClick = {
                                    runCatching { File(candidatePath).delete() }
                                    coverViewModel.consumeCandidate()
                                },
                                enabled = !applying,
                            ) { Text("放弃这个方案") }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("封面历史", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("只有确认采用的封面才进入历史，可随时恢复", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ refreshHistory() }) { Icon(Icons.Rounded.Refresh, "刷新") }
                }
            }

            if (history.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Text(
                            "还没有历史封面。生成并采用一个新方案后，这里会保留版本。",
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(history, key = { it.absolutePath }) { file ->
                    val active = file.absolutePath == book.coverPath
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !active && !applying) {
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
                        shape = RoundedCornerShape(22.dp),
                        tonalElevation = if (active) 3.dp else 1.dp,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoverPreviewV3(file.absolutePath, book.title, Modifier.width(76.dp).height(108.dp))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(if (active) "当前封面" else "历史方案", fontWeight = FontWeight.Bold)
                                Text(
                                    file.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "${(file.length() / 1024L).coerceAtLeast(1)} KB",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (active) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
    }
}

@Composable
fun CoverPreviewV3(path: String, title: String, modifier: Modifier = Modifier) {
    val stamp = runCatching { File(path).lastModified() }.getOrDefault(0L)
    val bitmap = remember(path, stamp) {
        path.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = title,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(title.take(10), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
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
