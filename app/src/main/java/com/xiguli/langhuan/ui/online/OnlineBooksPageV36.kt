package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMotionStatus
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.LanghuanSkeletonV31
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.enterOnceV31
import com.xiguli.langhuan.ui.design.rememberEnterRegistryV31
import com.xiguli.langhuan.ui.design.springClickV31

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineBooksPageV36(
    viewModel: OnlineBooksViewModelV36,
    onBack: () -> Unit,
    onOpenCreated: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (state.sources.isEmpty()) 1 else 0) }
    var query by rememberSaveable { mutableStateOf(state.query) }
    var urlDialog by remember { mutableStateOf(false) }
    var aiSheet by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFromFile) }

    LaunchedEffect(state.createdStoryId) { state.createdStoryId?.let { id -> viewModel.consumeCreated(); onOpenCreated(id) } }
    BackHandler(enabled = state.detail != null && state.download == null) { viewModel.closeDetail() }

    Column(Modifier.fillMaxSize().background(t.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
            Text("在线找书", Modifier.padding(start = 8.dp).weight(1f), color = t.foreground, style = MaterialTheme.typography.titleLarge)
            OnlineTabV36("搜索", tab == 0) { tab = 0 }
            Spacer(Modifier.width(6.dp))
            OnlineTabV36("书源 ${state.sources.size}", tab == 1) { tab = 1 }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(LanghuanMotionV31.MEDIUM)) togetherWith fadeOut(tween(LanghuanMotionV31.FAST)) },
            label = "onlineTab",
            modifier = Modifier.weight(1f),
        ) { current ->
            if (current == 0) {
                OnlineSearchTabV36(state, query, onQuery = { query = it }, onSearch = { viewModel.search(query) }, onStop = viewModel::stopSearch, onOpen = viewModel::openDetail, onGoSources = { tab = 1 })
            } else {
                OnlineSourcesTabV36(
                    state = state,
                    onPaste = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(viewModel::importSources) },
                    onUrl = { urlDialog = true },
                    onFile = { fileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onToggle = viewModel::toggleSource,
                    onDelete = viewModel::deleteSource,
                    onAi = { aiSheet = true },
                )
            }
        }
        AnimatedVisibility(state.message != null || state.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val text = state.error ?: state.message.orEmpty()
            Surface(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).springClickV31(pressedScale = .98f) { viewModel.clearMessage() },
                shape = RoundedCornerShape(14.dp),
                color = if (state.error != null) t.destructive.copy(alpha = .12f) else t.muted,
            ) {
                Text(text, Modifier.padding(14.dp), color = if (state.error != null) t.destructive else t.foreground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    state.detail?.let { detail ->
        ModalBottomSheet(onDismissRequest = { viewModel.closeDetail() }, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            OnlineDetailSheetV36(detail, state.detailLoading, state.download, onAdd = viewModel::addToShelf, onCancel = viewModel::cancelDownload)
        }
    }

    if (aiSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.aiRunning) { aiSheet = false; viewModel.cancelAi() } },
            containerColor = t.card,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        ) {
            OnlineAiSheetV36(
                state = state,
                onStart = viewModel::buildWithAi,
                onCancel = viewModel::cancelAi,
                onSave = { viewModel.saveAiSource(); aiSheet = false },
            )
        }
    }

    if (urlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("从网址导入书源") },
            text = { OutlinedTextField(url, { url = it }, label = { Text("书源 JSON 地址") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { urlDialog = false; if (url.isNotBlank()) viewModel.importFromUrl(url) }) { Text("导入") } },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("取消") } },
            containerColor = t.card,
        )
    }
}

@Composable
private fun OnlineTabV36(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val bg by animateColorAsState(if (selected) t.accent else Color.Transparent, tween(LanghuanMotionV31.MEDIUM), label = "onlineTabBg")
    Text(
        label,
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg).springClickV31(pressedScale = .94f, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) t.accentForeground else t.mutedForeground,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun OnlineSearchTabV36(
    state: OnlineBooksStateV36,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onStop: () -> Unit,
    onOpen: (OnlineBookV36) -> Unit,
    onGoSources: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val enter = rememberEnterRegistryV31()
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("书名或作者") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = t.card,
                unfocusedContainerColor = t.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        val enabled = state.sources.count { it.enabled }
        AnimatedVisibility(state.searching) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LanghuanMotionStatus("正在搜索 ${state.searchedSources}/$enabled 个书源", Modifier.weight(1f))
                TextButton(onClick = onStop) { Text("停止") }
            }
        }
        when {
            state.sources.isEmpty() -> OnlineEmptyV36("还没有书源", "琅嬛不内置任何书源。到「书源」页导入你自己的书源（支持阅读 Legado 格式）。", "去导入", onGoSources)
            state.results.isEmpty() && !state.searching -> OnlineEmptyV36("输入书名开始搜索", "会同时在 $enabled 个已启用书源里查找。", null, null)
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.results, key = { _, it -> it.sourceId + it.bookUrl }) { index, book ->
                    Surface(
                        Modifier.fillMaxWidth().animateItem().enterOnceV31(enter, book.sourceId + book.bookUrl, index).springClickV31(pressedScale = .98f) { onOpen(book) },
                        shape = RoundedCornerShape(16.dp),
                        color = t.card,
                        shadowElevation = 1.dp,
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            OnlineCoverV36(book.cover, book.name, Modifier.width(54.dp).aspectRatio(.72f))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(book.name, color = t.foreground, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(book.author.ifBlank { "佚名" }, Modifier.padding(top = 2.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                if (book.latest.isNotBlank()) Text("最新：${book.latest}", Modifier.padding(top = 2.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(book.sourceName, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(99.dp)).background(t.muted).padding(horizontal = 8.dp, vertical = 2.dp), color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSourcesTabV36(
    state: OnlineBooksStateV36,
    onPaste: () -> Unit,
    onUrl: () -> Unit,
    onFile: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAi: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    var pendingDelete by remember { mutableStateOf<BookSourceV36?>(null) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "书源由你自己导入和负责。请只使用你有权访问的网站内容。支持阅读（Legado）格式中基于网页规则的书源；需要 JS 或 JSON 接口的书源会被跳过。",
                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                color = t.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).springClickV31(pressedScale = .98f, onClick = onAi),
                shape = RoundedCornerShape(18.dp),
                color = t.accent,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(24.dp), tint = t.accentForeground)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("AI 生成书源", color = t.accentForeground, style = MaterialTheme.typography.titleMedium)
                        Text("给一个网站链接和一本书名，AI 写规则并实测", color = t.accentForeground.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineImportButtonV36(Icons.Rounded.ContentPaste, "剪贴板", Modifier.weight(1f), onPaste)
                OnlineImportButtonV36(Icons.Rounded.Link, "网址", Modifier.weight(1f), onUrl)
                OnlineImportButtonV36(Icons.Rounded.FolderOpen, "文件", Modifier.weight(1f), onFile)
            }
        }
        itemsIndexed(state.sources, key = { _, it -> it.id }) { _, source ->
            Surface(Modifier.fillMaxWidth().animateItem(), shape = RoundedCornerShape(14.dp), color = t.card) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, color = t.foreground, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(source.baseUrl, color = t.mutedForeground, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = source.enabled, onCheckedChange = { onToggle(source.id) })
                    LanghuanIconButton(Icons.Rounded.DeleteOutline, "删除书源", { pendingDelete = source })
                }
            }
        }
    }
    pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除书源「${source.name}」？") },
            text = { Text("已经加入书架的书不受影响，但无法再从这个书源检查更新。") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(source.id) }) { Text("删除", color = t.destructive) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            containerColor = t.card,
        )
    }
}

@Composable
private fun OnlineImportButtonV36(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier.springClickV31(pressedScale = .95f, onClick = onClick), shape = RoundedCornerShape(14.dp), color = t.card, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp), tint = t.primary)
            Text(label, Modifier.padding(top = 4.dp), color = t.foreground, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun OnlineEmptyV36(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    val t = LocalLanghuanUiTokens.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Search, null, Modifier.size(40.dp), tint = t.mutedForeground)
            Text(title, Modifier.padding(top = 12.dp), color = t.foreground, style = MaterialTheme.typography.titleMedium)
            Text(body, Modifier.padding(top = 6.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            if (action != null && onAction != null) {
                Surface(Modifier.padding(top = 14.dp).springClickV31(onClick = onAction), shape = RoundedCornerShape(999.dp), color = t.primary) {
                    Text(action, Modifier.padding(horizontal = 20.dp, vertical = 9.dp), color = t.primaryForeground, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OnlineCoverV36(url: String, title: String, modifier: Modifier) {
    val t = LocalLanghuanUiTokens.current
    val bitmap = rememberOnlineCoverV36(url)
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(t.muted), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(title.take(2), color = t.mutedForeground, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OnlineDetailSheetV36(
    detail: OnlineDetailV36,
    loading: Boolean,
    download: OnlineDownloadV36?,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row {
            OnlineCoverV36(detail.book.cover, detail.book.name, Modifier.width(78.dp).aspectRatio(.72f))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(detail.book.name, color = t.foreground, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail.book.author.ifBlank { "佚名" }, Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (loading) "正在读取目录…" else "共 ${detail.chapters.size} 章 · ${detail.book.sourceName}",
                    Modifier.padding(top = 4.dp),
                    color = t.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            detail.book.intro.ifBlank { "暂无简介" },
            Modifier.padding(top = 14.dp).heightIn(max = 160.dp),
            color = t.foreground,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 7,
            overflow = TextOverflow.Ellipsis,
        )
        if (loading) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { LanghuanSkeletonV31(Modifier.fillMaxWidth(.7f - it * .15f).height(12.dp)) }
            }
        } else if (detail.chapters.isNotEmpty()) {
            Text("最新：${detail.chapters.last().title}", Modifier.padding(top = 12.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedContent(targetState = download != null, label = "downloadState", modifier = Modifier.padding(top = 18.dp)) { downloading ->
            if (downloading && download != null) {
                Column {
                    val fraction = if (download.total == 0) 0f else download.done.toFloat() / download.total
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "下载中 ${download.done}/${download.total}" + if (download.failed > 0) " · ${download.failed} 章失败" else "",
                            Modifier.weight(1f),
                            color = t.mutedForeground,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onCancel) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)); Text("取消") }
                    }
                }
            } else {
                Surface(
                    Modifier.fillMaxWidth().springClickV31(enabled = !loading && detail.chapters.isNotEmpty(), pressedScale = .97f, onClick = onAdd),
                    shape = RoundedCornerShape(16.dp),
                    color = if (!loading && detail.chapters.isNotEmpty()) t.primary else t.muted,
                ) {
                    Text(
                        "加入书架（下载全部 ${detail.chapters.size} 章）",
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        color = if (!loading && detail.chapters.isNotEmpty()) t.primaryForeground else t.mutedForeground,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(18.dp))
    }
}

private val onlineCoverCacheV36 = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int = value.width * value.height * 4
}

/** Downloads and downsamples a remote cover off the main thread; cached for the session. */
@Composable
private fun rememberOnlineCoverV36(url: String): androidx.compose.ui.graphics.ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(onlineCoverCacheV36.get(url)) }
    LaunchedEffect(url) {
        if (bitmap != null || !url.startsWith("http")) return@LaunchedEffect
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile")
                val bytes = try { connection.inputStream.use { it.readBytes() } } finally { connection.disconnect() }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 240) sample *= 2
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        }?.also { onlineCoverCacheV36.put(url, it) }
    }
    return bitmap
}

@Composable
private fun OnlineAiSheetV36(
    state: OnlineBooksStateV36,
    onStart: (String, String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var site by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp)) {
        Text("AI 生成书源", color = t.foreground, style = MaterialTheme.typography.titleLarge)
        Text(
            "AI 会依次分析搜索页、目录页和正文页，每一步都用真实网页验证；失败时会带着结果让 AI 再改一次。需要网站不依赖 JS 加载内容。",
            Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = t.mutedForeground,
            style = MaterialTheme.typography.bodySmall,
        )
        val editable = !state.aiRunning && state.aiReport == null
        OutlinedTextField(site, { site = it }, Modifier.fillMaxWidth(), label = { Text("网站链接") }, singleLine = true, enabled = editable)
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("该站能搜到的一本书名（用于测试）") }, singleLine = true, enabled = editable)

        if (state.aiSteps.isNotEmpty()) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.aiSteps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            when (step.ok) {
                                true -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = t.success)
                                false -> Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = t.destructive)
                                null -> com.xiguli.langhuan.ui.design.LanghuanTypingDotsV31(t.primary, dot = 4.dp)
                            }
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("${index + 1}. ${step.label}", color = t.foreground, style = MaterialTheme.typography.bodyMedium)
                            if (step.detail.isNotBlank()) Text(step.detail, color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        state.aiError?.let { error ->
            Text(error, Modifier.padding(top = 12.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall)
        }

        state.aiReport?.let { report ->
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(14.dp), color = t.muted) {
                Column(Modifier.padding(14.dp)) {
                    Text("「${report.source.name}」测试通过", color = t.foreground, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "搜到 ${report.searchCount} 本 · 《${report.bookName}》目录 ${report.chapterCount} 章",
                        Modifier.padding(top = 4.dp),
                        color = t.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(report.sample, Modifier.padding(top = 8.dp), color = t.foreground, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                state.aiRunning -> OnlineSheetButtonV36("停止", primary = false, Modifier.weight(1f), onCancel)
                state.aiReport != null -> {
                    OnlineSheetButtonV36("重新生成", primary = false, Modifier.weight(1f)) { onCancel(); onStart(site, keyword) }
                    OnlineSheetButtonV36("保存书源", primary = true, Modifier.weight(1f), onSave)
                }
                else -> OnlineSheetButtonV36(if (state.aiError != null) "重试" else "开始生成", primary = true, Modifier.weight(1f)) { onStart(site, keyword) }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(18.dp))
    }
}

@Composable
private fun OnlineSheetButtonV36(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier.springClickV31(pressedScale = .97f, onClick = onClick), shape = RoundedCornerShape(14.dp), color = if (primary) t.primary else t.muted) {
        Text(
            label,
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            color = if (primary) t.primaryForeground else t.foreground,
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
