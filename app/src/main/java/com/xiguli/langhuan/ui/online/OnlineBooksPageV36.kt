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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Edit
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
    embedded: Boolean = false,
    startWithSources: Boolean = false,
    onConfigureAi: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (startWithSources) 1 else 0) }
    var query by rememberSaveable { mutableStateOf(state.query) }
    var urlDialog by remember { mutableStateOf(false) }
    var aiSheet by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFromFile) }

    var previousSourceEntry by rememberSaveable { mutableStateOf(startWithSources) }
    LaunchedEffect(startWithSources) {
        if (previousSourceEntry != startWithSources) {
            tab = if (startWithSources) 1 else 0
            previousSourceEntry = startWithSources
        }
    }
    LaunchedEffect(state.createdStoryId) { state.createdStoryId?.let { id -> viewModel.consumeCreated(); onOpenCreated(id) } }
    BackHandler(enabled = tab == 1 && state.detail == null && !aiSheet && !urlDialog) { tab = 0 }
    BackHandler(enabled = state.detail != null && state.download == null) { viewModel.closeDetail() }

    Column(Modifier.fillMaxSize().background(t.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
            Column(Modifier.weight(1f).padding(start = if (embedded) 0.dp else 8.dp)) {
                Text(if (tab == 0) "书城" else "书源管理", color = t.foreground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(if (tab == 0) "发现下一本好书" else "连接你信任的阅读世界", Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            }
            OnlineTabV36(if (tab == 0) "管理书源" else "返回书城", selected = false) { tab = if (tab == 0) 1 else 0 }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(LanghuanMotionV31.MEDIUM)) togetherWith fadeOut(tween(LanghuanMotionV31.FAST)) },
            label = "onlineTab",
            modifier = Modifier.weight(1f),
        ) { current ->
            if (current == 0) {
                OnlineSearchTabV36(state, query, onQuery = { query = it }, onSearch = { viewModel.search(query) }, onStop = viewModel::stopSearch, onOpen = viewModel::openDetail, onGoSources = { tab = 1 }, onDiscover = viewModel::discover, onLoadMore = viewModel::loadMoreDiscovery)
            } else {
                OnlineSourcesTabV36(
                    state = state,
                    onPaste = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(viewModel::importSources) },
                    onUrl = { urlDialog = true },
                    onFile = { fileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onToggle = viewModel::toggleSource,
                    onDelete = viewModel::deleteSource,
                    onEdit = viewModel::editSource,
                    onBeginEdit = viewModel::beginSourceEdit,
                    onChangeDraft = viewModel::updateSourceEditDraft,
                    onCancelEdit = viewModel::cancelSourceEdit,
                    onExport = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(viewModel.exportSources())) },
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
                onSave = { if (viewModel.saveAiSource()) aiSheet = false },
                onConfigureAi = onConfigureAi,
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
private fun OnlineTabV36(label: String, selected: Boolean, multiline: Boolean = false, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val bg by animateColorAsState(if (selected) t.accent else Color.Transparent, tween(LanghuanMotionV31.MEDIUM), label = "onlineTabBg")
    Text(
        label,
        Modifier.then(if (multiline) Modifier.widthIn(max = 280.dp).heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(999.dp)).background(bg).springClickV31(pressedScale = .94f, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) t.accentForeground else t.mutedForeground,
        style = MaterialTheme.typography.labelLarge,
        maxLines = if (multiline) 2 else 1,
        overflow = TextOverflow.Ellipsis,
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
    onDiscover: (SourceDiscoveryV41) -> Unit,
    onLoadMore: () -> Unit,
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
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = onSearch, enabled = query.isNotBlank() && !state.searching) {
                    Icon(Icons.Rounded.Search, "搜索书名或作者", tint = t.primary)
                }
            },
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
        val enabled = state.sources.count { it.enabled && it.searchUrl.isNotBlank() && it.searchList.isNotBlank() }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("全源搜索", color = t.foreground, style = MaterialTheme.typography.labelLarge)
            Text("  ·  $enabled 个书源已启用", Modifier.weight(1f), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onGoSources) { Text("管理") }
        }
        val discoveries = remember(state.sources) { state.sources.flatMap(::sourceDiscoveriesV41) }
        val discoveryIssues = remember(state.sources) { state.sources.flatMap { src -> sourceDiscoveryCatalogV41(src).issues.map { "${src.name}：$it" } } }
        discoveryIssues.firstOrNull()?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall) }
        if (discoveries.isNotEmpty()) {
            Text("发现 · 分类与榜单", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = t.foreground, style = MaterialTheme.typography.labelLarge)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoveries, key = { it.sourceId + "::" + it.template }) { section ->
                    val sourceName = state.sources.firstOrNull { it.id == section.sourceId }?.name.orEmpty()
                    OnlineTabV36("$sourceName · ${section.label}", state.discoverySection == section, multiline = true) { onDiscover(section) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        state.discoveryLabel?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = t.foreground, style = MaterialTheme.typography.titleSmall) }
        AnimatedVisibility(state.searching) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LanghuanMotionStatus(if (state.discoveryLabel != null) "正在读取发现分类…" else "正在搜索 ${state.searchedSources}/$enabled 个书源", Modifier.weight(1f))
                TextButton(onClick = onStop) { Text("停止") }
            }
        }
        when {
            state.sources.isEmpty() -> OnlineEmptyV36("为书城添加第一盏灯", "导入你有权使用的书源，或让 AI 为你生成。\n书城会从这些网站搜索真实书籍。", "添加书源 / AI 生成", onGoSources)
            enabled == 0 && discoveries.isEmpty() -> OnlineEmptyV36("书源还没有启用", "到书源管理打开至少一个书源，再来寻找喜欢的故事。", "启用书源", onGoSources)
            state.results.isEmpty() && !state.searching && state.discoveryPageError != null -> OnlineEmptyV36("分类暂时打不开", state.discoveryPageError, "重试本页", onLoadMore)
            state.results.isEmpty() && !state.searching && state.discoveryLabel != null -> OnlineEmptyV36("此分类暂时没有书籍", "试试其他分类、搜索书名，或检查这条书源的发现规则。", "检查书源", onGoSources)
            state.results.isEmpty() && !state.searching && state.query.isNotBlank() -> OnlineEmptyV36("没有找到相关书籍", "试试更短的书名、作者名，或换一个可用书源。", "检查书源", onGoSources)
            state.results.isEmpty() && !state.searching -> OnlineEmptyV36("故事，从一个名字开始", "输入书名或作者，会同时搜索你启用的 $enabled 个书源。", null, null)
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                if (state.discoverySection != null && !state.searching) {
                    item(key = "discovery-footer") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            state.discoveryPageError?.let { Text(it, color = t.destructive, style = MaterialTheme.typography.bodySmall) }
                            if (state.discoveryPageError != null || state.discoveryHasMore) {
                                TextButton(onClick = onLoadMore) { Text(if (state.discoveryPageError != null) "重试本页" else "加载更多") }
                            } else {
                                Text("已加载 ${state.results.size} 本 · 没有更多书籍", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
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
    onEdit: (String, String) -> Unit,
    onBeginEdit: (String) -> Unit,
    onChangeDraft: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onExport: () -> Unit,
    onAi: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    var pendingDelete by remember { mutableStateOf<BookSourceV36?>(null) }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    val pendingEdit = state.sources.firstOrNull { it.id == state.sourceEditId }
    var exportConfirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.sources.count { it.enabled }} 个已启用 · 共 ${state.sources.size} 个书源", Modifier.weight(1f), color = t.foreground, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { exportConfirm = true }, enabled = state.sources.isNotEmpty()) { Text("导出") }
            }
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
        if (state.sources.isNotEmpty()) item {
            OutlinedTextField(sourceQuery, { sourceQuery = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("搜索名称、分组或网站") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                shape = RoundedCornerShape(16.dp))
        }
        itemsIndexed(state.sources.filter { sourceQuery.isBlank() || it.name.contains(sourceQuery, true) || it.group.contains(sourceQuery, true) || it.baseUrl.contains(sourceQuery, true) }, key = { _, it -> it.id }) { _, source ->
            Surface(Modifier.fillMaxWidth().animateItem(), shape = RoundedCornerShape(14.dp), color = t.card) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, color = t.foreground, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(source.baseUrl, color = t.mutedForeground, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(source.group.takeIf { it.isNotBlank() }, if (source.enabled) "已启用" else "已停用", "网页规则").joinToString(" · "), Modifier.padding(top = 5.dp), color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = source.enabled, onCheckedChange = { onToggle(source.id) })
                    LanghuanIconButton(Icons.Rounded.Edit, "编辑书源「${source.name}」", { onBeginEdit(source.id) })
                    LanghuanIconButton(Icons.Rounded.DeleteOutline, "删除书源", { pendingDelete = source })
                }
            }
        }
    }
    if (exportConfirm) AlertDialog(
        onDismissRequest = { exportConfirm = false }, title = { Text("复制书源 JSON") },
        text = { Text("将全部书源复制到剪贴板。自定义请求头也会包含在内，请勿把带登录凭据的书源分享给他人。") },
        confirmButton = { TextButton(onClick = { onExport(); exportConfirm = false }) { Text("复制") } },
        dismissButton = { TextButton(onClick = { exportConfirm = false }) { Text("取消") } }, containerColor = t.card,
    )
    pendingEdit?.let { source ->
        val raw = state.sourceEditDraft
        val validation = state.sourceEditError
        val saving = state.sourceEditSaving
        AlertDialog(
            onDismissRequest = onCancelEdit, title = { Text("编辑「${source.name}」") },
            text = {
                Column {
                    Text("保存只校验格式，网站可用性请返回书城实际搜索确认。", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(raw, onChangeDraft, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp), label = { Text("书源 JSON") }, enabled = !saving)
                    validation?.let { Text(it, color = t.destructive, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = { onEdit(source.id, raw) }) {
                    Text(if (saving) "校验中…" else "保存规则")
                }
            },
            dismissButton = { TextButton(onClick = onCancelEdit) { Text("取消") } }, containerColor = t.card,
        )
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
                            (if (download.saving) "正在保存到书架…" else "下载中 ${download.done}/${download.total}") + if (download.failed > 0) " · ${download.failed} 章失败" else "",
                            Modifier.weight(1f),
                            color = t.mutedForeground,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!download.saving) TextButton(onClick = onCancel) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)); Text("取消") }
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
        bitmap = kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bytes = fetchSourceBytesV36(url, maxBytes = 2 * 1024 * 1024)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000L) { "封面尺寸无效或过大" }
                var sample = 1
                while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 720) sample *= 2
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
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
    onConfigureAi: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var site by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)) {
        Text("AI 生成书源", color = t.foreground, style = MaterialTheme.typography.titleLarge)
        Text(
            "AI 会验证搜索、详情、目录、正文，并识别网站已有的分类与排行榜，检查发现分页。只添加网页中真实存在且通过验证的入口；动态 JS 书源会明确提示暂不支持。",
            Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = t.mutedForeground,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onConfigureAi, enabled = !state.aiRunning) { Text("AI 服务与模型设置") }
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
                                null -> if (step.completed) Text("—", color = t.mutedForeground) else com.xiguli.langhuan.ui.design.LanghuanTypingDotsV31(t.primary, dot = 4.dp)
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
                    Text("「${report.source.name}」搜索与阅读测试通过", color = t.foreground, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "搜到 ${report.searchCount} 本 · 《${report.bookName}》目录 ${report.chapterCount} 章",
                        Modifier.padding(top = 4.dp),
                        color = t.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(if (report.discoveryLabels.isEmpty()) "未添加发现入口：网站没有可验证的静态分类，或验证未通过" else "已验证发现：${report.discoveryLabels.joinToString("、")}", Modifier.padding(top = 6.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    report.discoveryEvidence.forEach { proof ->
                        Text("${proof.label} · ${proof.bookCount} 本 · 目录/正文已验证" + if (proof.nextPageUrl != null) " · 下一页${proof.nextPageBookCount?.let { " $it 本" } ?: "未通过"}" else "", Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                        Text(proof.url, color = t.mutedForeground, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    report.discoveryWarnings.forEach { Text(it, Modifier.padding(top = 4.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall) }
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
