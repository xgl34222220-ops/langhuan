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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import com.xiguli.langhuan.ui.design.PaperPageTitleV44
import com.xiguli.langhuan.ui.design.PaperCardV44
import com.xiguli.langhuan.ui.design.PaperSectionLabelV44
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
    PaperReaderThemeV44 {
        OnlineBooksPaperContentV44(viewModel, onBack, onOpenCreated, embedded, startWithSources, onConfigureAi)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineBooksPaperContentV44(
    viewModel: OnlineBooksViewModelV36,
    onBack: () -> Unit,
    onOpenCreated: (String) -> Unit,
    embedded: Boolean,
    startWithSources: Boolean,
    onConfigureAi: () -> Unit,
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
            PaperPageTitleV44(if (tab == 0) "书城" else "书源管理", Modifier.weight(1f))
            TextButton(onClick = { tab = if (tab == 0) 1 else 0 }) {
                Text(if (tab == 0) "管理书源" else "返回书城", color = t.primary)
            }
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
        AnimatedVisibility(state.message != null || state.error != null || state.sourceStorageError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val text = state.sourceStorageError ?: state.error ?: state.message.orEmpty()
            Surface(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).springClickV31(pressedScale = .98f) { viewModel.clearMessage() },
                shape = RoundedCornerShape(14.dp),
                color = if (state.error != null || state.sourceStorageError != null) t.destructive.copy(alpha = .12f) else t.muted,
            ) {
                Text(text, Modifier.padding(14.dp), color = if (state.error != null || state.sourceStorageError != null) t.destructive else t.foreground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    state.detail?.let { detail ->
        ModalBottomSheet(onDismissRequest = { viewModel.closeDetail() }, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            OnlineDetailSheetV36(detail, state.detailLoading, state.download, state.addingToShelf, onAdd = viewModel::addToShelf, onRead = viewModel::readAddedBook, onDownload = viewModel::downloadDetail, onCancel = viewModel::cancelDownload)
        }
    }

    if (aiSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.aiRunning) { aiSheet = false; viewModel.cancelAi() } },
            containerColor = t.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("书名或作者") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = onSearch, enabled = query.isNotBlank() && !state.searching) {
                    Icon(Icons.Rounded.Search, "搜索书名或作者", tint = t.primary)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = t.muted,
                unfocusedContainerColor = t.muted,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        val enabled = state.sources.count { it.enabled && it.searchUrl.isNotBlank() && it.searchList.isNotBlank() }
        val discoveries = remember(state.sources) { state.sources.flatMap(::sourceDiscoveriesV41) }
        val discoveryIssues = remember(state.sources) { state.sources.flatMap { src -> sourceDiscoveryCatalogV41(src).issues.map { "${src.name}：$it" } } }
        var sectionGroup by rememberSaveable { mutableStateOf("全部") }
        fun isRanking(label: String) = isRankingDiscoveryLabelV44(label)
        val filteredSections = discoveries.filter { section -> when (sectionGroup) {
            "排行榜" -> isRanking(section.label)
            "分类" -> !isRanking(section.label)
            else -> true
        } }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), color = t.card,
            border = BorderStroke(1.dp, t.border), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.discoverySection?.let { section -> state.sources.firstOrNull { it.id == section.sourceId }?.name } ?: "全源搜索",
                    Modifier.weight(1f), color = t.foreground, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$enabled 个可搜索书源", color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
            }
        }
        discoveryIssues.firstOrNull()?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall) }
        if (discoveries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("全部", "分类", "排行榜").forEach { label ->
                    Column(Modifier.weight(1f).springClickV31 {
                        sectionGroup = label
                        discoveries.firstOrNull { section -> label == "全部" || (isRanking(section.label) == (label == "排行榜")) }?.let(onDiscover)
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, Modifier.padding(vertical = 12.dp), color = if (sectionGroup == label) t.primary else t.mutedForeground,
                            fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.width(32.dp).height(2.dp).background(if (sectionGroup == label) t.primary else Color.Transparent))
                    }
                }
            }
            if (filteredSections.isEmpty()) {
                Text("已启用书源尚未提供${sectionGroup}入口", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            } else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredSections, key = { it.sourceId + "::" + it.template }) { section ->
                    val sourceName = state.sources.firstOrNull { it.id == section.sourceId }?.name.orEmpty()
                    OnlineTabV36("$sourceName · ${section.label}", state.discoverySection == section, multiline = true) { onDiscover(section) }
                }
            }
        }
        AnimatedVisibility(state.searching) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LanghuanMotionStatus(if (state.discoveryLabel != null) "正在读取发现分类…" else "正在搜索 ${state.searchedSources}/$enabled 个书源", Modifier.weight(1f))
                TextButton(onClick = onStop) { Text("停止") }
            }
        }
        when {
            state.sourceStorageError != null -> OnlineEmptyV36("书源暂时无法读取", "原始配置仍保存在本机，可到书源管理导出留档。", "查看书源", onGoSources)
            state.sources.isEmpty() -> OnlineEmptyV36("为书城添加第一盏灯", "导入你有权使用的书源，或让 AI 为你生成。\n书城会从这些网站搜索真实书籍。", "添加书源 / AI 生成", onGoSources)
            enabled == 0 && discoveries.isEmpty() -> OnlineEmptyV36("书源还没有启用", "到书源管理打开至少一个书源，再来寻找喜欢的故事。", "启用书源", onGoSources)
            state.discoverySection != null && sectionGroup != "全部" && state.discoverySection !in filteredSections && !state.searching -> OnlineEmptyV36("暂未提供${sectionGroup}入口", "可以在全部发现入口中继续浏览，或添加其他书源。", null, null)
            state.results.isEmpty() && !state.searching && state.discoveryPageError != null -> OnlineEmptyV36("分类暂时打不开", state.discoveryPageError, "重试本页", onLoadMore)
            state.results.isEmpty() && !state.searching && state.discoveryLabel != null -> OnlineEmptyV36("此分类暂时没有书籍", "试试其他分类、搜索书名，或检查这条书源的发现规则。", "检查书源", onGoSources)
            state.results.isEmpty() && !state.searching && state.query.isNotBlank() -> OnlineEmptyV36("没有找到相关书籍", "试试更短的书名、作者名，或换一个可用书源。", "检查书源", onGoSources)
            state.results.isEmpty() && !state.searching -> OnlineEmptyV36("故事，从一个名字开始", "输入书名或作者，会同时搜索你启用的 $enabled 个书源。", null, null)
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    PaperSectionLabelV44(state.discoveryLabel ?: if (state.query.isNotBlank()) "搜索结果 · ${state.results.size} 本" else "发现好书")
                }
                if (state.discoverySection != null && !isRanking(state.discoverySection.label)) {
                    items(state.results.chunked(3).size) { rowIndex ->
                        val books = state.results.chunked(3)[rowIndex]
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            books.forEach { book ->
                                Column(Modifier.weight(1f).springClickV31(pressedScale = .97f) { onOpen(book) }) {
                                    OnlineCoverV36(book.cover, book.name, Modifier.fillMaxWidth().aspectRatio(.69f))
                                    Text(book.name, Modifier.padding(top = 9.dp), color = t.foreground, fontFamily = FontFamily.Serif,
                                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(book.author.ifBlank { "佚名" }, Modifier.padding(top = 4.dp), color = t.mutedForeground,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(book.sourceName, Modifier.padding(top = 3.dp), color = t.mutedForeground,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(3 - books.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else itemsIndexed(state.results, key = { _, it -> it.sourceId + it.bookUrl }) { index, book ->
                    Column(Modifier.fillMaxWidth().animateItem().enterOnceV31(enter, book.sourceId + book.bookUrl, index)
                        .springClickV31(pressedScale = .98f) { onOpen(book) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.discoverySection != null) Text((index + 1).toString().padStart(2, '0'), Modifier.width(30.dp),
                                color = if (index < 3) t.primary else t.mutedForeground, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium)
                            OnlineCoverV36(book.cover, book.name, Modifier.width(55.dp).aspectRatio(.69f))
                            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                                Text(book.name, color = t.foreground, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(book.author.ifBlank { "佚名" }, Modifier.padding(top = 5.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                if (book.latest.isNotBlank()) Text(book.latest, Modifier.padding(top = 3.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(book.sourceName, Modifier.padding(top = 5.dp), color = t.primary, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = t.mutedForeground)
                        }
                        Box(Modifier.fillMaxWidth().padding(top = 16.dp).height(1.dp).background(t.border.copy(alpha = .6f)))
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
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextField(sourceQuery, { sourceQuery = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("按名称、分组或网站搜索") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(28.dp), colors = TextFieldDefaults.colors(
                        focusedContainerColor = t.muted, unfocusedContainerColor = t.muted,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.sourceStorageError != null) "读取异常 · 原始配置已保留" else "${state.sources.size} 个书源 · 已启用 ${state.sources.count { it.enabled }} 个", Modifier.weight(1f), color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { exportConfirm = true }, enabled = state.sources.isNotEmpty() || state.sourceStorageError != null) { Text(if (state.sourceStorageError != null) "导出原始数据" else "导出", color = t.primary) }
                }
            }
            if (state.sources.isEmpty()) item {
                PaperCardV44(Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.MenuBook, null, Modifier.size(32.dp), tint = t.primary)
                    Text(if (state.sourceStorageError != null) "暂未载入已有书源" else "连接你的阅读世界", Modifier.padding(top = 12.dp), fontFamily = FontFamily.Serif, color = t.foreground, style = MaterialTheme.typography.titleLarge)
                    Text(if (state.sourceStorageError != null) "已有配置读取失败，可先导出原始数据留档。修复前暂停导入和编辑，以保留原数据。" else "导入你有权使用的书源，或从网站链接生成规则。书城会显示网站实际提供的书籍与发现入口。", Modifier.padding(top = 8.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val filtered = state.sources.filter { sourceQuery.isBlank() || it.name.contains(sourceQuery, true) || it.group.contains(sourceQuery, true) || it.baseUrl.contains(sourceQuery, true) }
            if (state.sources.isNotEmpty() && filtered.isEmpty()) item {
                Text("没有匹配的书源", Modifier.padding(vertical = 24.dp), color = t.mutedForeground)
            }
            itemsIndexed(filtered, key = { _, it -> it.id }) { _, source ->
                val catalog = remember(source) { sourceDiscoveryCatalogV41(source.copy(enabled = true, enabledExplore = true)) }
                val capabilities = buildList {
                    if (source.searchUrl.isNotBlank() && source.searchList.isNotBlank()) add("搜索")
                    if (catalog.sections.any { !isRankingDiscoveryLabelV44(it.label) }) add("分类")
                    if (catalog.sections.any { isRankingDiscoveryLabelV44(it.label) }) add("榜单")
                    if (source.contentText.isNotBlank()) add("正文")
                }
                PaperCardV44(Modifier.fillMaxWidth().animateItem(), contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(t.muted), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MenuBook, null, Modifier.size(26.dp), tint = t.foreground)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(source.name, color = t.foreground, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(source.baseUrl, Modifier.padding(top = 3.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(checked = source.enabled, onCheckedChange = { onToggle(source.id) }, modifier = Modifier.padding(start = 6.dp))
                    }
                    if (capabilities.isNotEmpty()) LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(capabilities) { capability ->
                            Text(capability, Modifier.clip(RoundedCornerShape(8.dp)).background(t.accent).padding(horizontal = 9.dp, vertical = 5.dp),
                                color = t.accentForeground, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(catalog.issues.firstOrNull() ?: if (source.enabled) "已启用 · 可在书城实测" else "已停用",
                            Modifier.weight(1f), color = if (catalog.issues.isNotEmpty()) t.destructive else t.mutedForeground,
                            style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        LanghuanIconButton(Icons.Rounded.Edit, "编辑书源「${source.name}」", { onBeginEdit(source.id) })
                        LanghuanIconButton(Icons.Rounded.DeleteOutline, "删除书源", { pendingDelete = source })
                    }
                }
            }
            item {
                PaperSectionLabelV44("导入书源", Modifier.padding(top = 8.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnlineImportButtonV36(Icons.Rounded.ContentPaste, "剪贴板", Modifier.weight(1f), onPaste)
                    OnlineImportButtonV36(Icons.Rounded.Link, "网址", Modifier.weight(1f), onUrl)
                    OnlineImportButtonV36(Icons.Rounded.FolderOpen, "文件", Modifier.weight(1f), onFile)
                }
                Text("支持 Legado 静态网页规则。依赖脚本或 JSON 接口的规则会明确报出未支持项。", Modifier.padding(top = 12.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).springClickV31(pressedScale = .98f, onClick = onAi),
            shape = RoundedCornerShape(20.dp), color = t.primary, shadowElevation = 2.dp) {
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(24.dp), tint = t.primaryForeground)
                Text("AI 生成书源", Modifier.padding(start = 12.dp), color = t.primaryForeground, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (exportConfirm) AlertDialog(
        onDismissRequest = { exportConfirm = false }, title = { Text(if (state.sourceStorageError != null) "复制原始书源数据" else "复制书源 JSON") },
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
        else {
            Box(Modifier.fillMaxSize().padding(start = 5.dp).background(t.accent.copy(alpha = .6f)))
            Box(Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxSize().background(t.primary.copy(alpha = .12f)))
            Text(title.take(12), Modifier.padding(12.dp), color = t.foreground, fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.titleSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OnlineDetailSheetV36(
    detail: OnlineDetailV36,
    loading: Boolean,
    download: OnlineDownloadV36?,
    adding: Boolean,
    onAdd: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit,
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
                    if (loading) "正在读取完整目录…" else if (detail.chapters.isEmpty()) "目录未读取完成 · ${detail.book.sourceName}" else "已解析 ${detail.chapters.size} 章 · ${detail.book.sourceName}",
                    Modifier.padding(top = 4.dp),
                    color = t.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (!loading && detail.chapters.isNotEmpty() && detail.catalogueProof?.hasCompletenessEvidence != true) {
            Text("目录完整性尚未确认，已解析数量不代表全书总章数", Modifier.padding(top = 8.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
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
            Text("目录末条：${detail.chapters.last().title}", Modifier.padding(top = 12.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedContent(targetState = download != null, label = "downloadState", modifier = Modifier.padding(top = 18.dp)) { downloading ->
            if (downloading && download != null) {
                Column {
                    val fraction = if (download.total == 0) 0f else download.done.toFloat() / download.total
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (download.saving) "正在保存缓存…" else "离线缓存 ${download.done}/${download.total}") + if (download.failed > 0) " · ${download.failed} 章失败" else "",
                            Modifier.weight(1f),
                            color = t.mutedForeground,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!download.saving) TextButton(onClick = onCancel) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)); Text("取消") }
                    }
                }
            } else {
                Surface(
                    Modifier.fillMaxWidth().springClickV31(enabled = !loading && !adding && detail.chapters.isNotEmpty(), pressedScale = .97f, onClick = if (detail.shelfStoryId != null) onRead else onAdd),
                    shape = RoundedCornerShape(16.dp),
                    color = if (!loading && detail.chapters.isNotEmpty()) t.primary else t.muted,
                ) {
                    Text(
                        if (adding) "正在收藏…" else if (detail.shelfStoryId != null) "开始阅读" else "加入书架",
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        color = if (!loading && detail.chapters.isNotEmpty()) t.primaryForeground else t.mutedForeground,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (download == null) {
            if (detail.shelfStoryId != null) TextButton(onClick = onDownload, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("离线下载", color = t.primary)
            }
            Text(if (detail.shelfStoryId == null) "收藏只保存目录，阅读时按需加载正文" else "已加入书架 · 需要无网阅读时可单独离线下载",
                Modifier.fillMaxWidth().padding(top = 8.dp), color = t.mutedForeground,
                style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
internal fun OnlineAiSheetV36(
    state: OnlineBooksStateV36,
    onStart: (String, String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onConfigureAi: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var site by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("AI 生成书源", Modifier.align(Alignment.CenterHorizontally), color = t.foreground,
            fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("理解网站结构，生成规则并逐项实测", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
            color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
        val editable = !state.aiRunning && state.aiReport == null
        PaperCardV44(Modifier.fillMaxWidth()) {
            PaperSectionLabelV44("网站地址")
            OutlinedTextField(site, { site = it }, Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text("网站链接") }, placeholder = { Text("https://") }, singleLine = true,
                enabled = editable, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri))
            OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("该站能搜到的一本书名（用于测试）") }, singleLine = true, enabled = editable, shape = RoundedCornerShape(12.dp))
        }
        PaperCardV44(Modifier.fillMaxWidth()) {
            PaperSectionLabelV44("AI 配置")
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(28.dp), tint = t.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.aiProviderLabel ?: "尚未配置服务", color = t.foreground, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("使用已保存的服务与模型", Modifier.padding(top = 3.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onConfigureAi, enabled = !state.aiRunning) { Text("设置", color = t.primary) }
            }
        }
        PaperCardV44(Modifier.fillMaxWidth()) {
            PaperSectionLabelV44("将验证的能力")
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("搜索", "分类", "榜单", "翻页").forEach { label ->
                    Text(label, Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(t.accent).padding(vertical = 9.dp),
                        color = t.accentForeground, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Text("只保留网页中真实存在且验证通过的入口。网站没有的分类与榜单，不会编造。", Modifier.padding(top = 10.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
        }
        if (state.aiSteps.isNotEmpty()) {
            PaperCardV44(Modifier.fillMaxWidth()) {
                PaperSectionLabelV44("预览与验证", Modifier.padding(bottom = 12.dp))
                state.aiSteps.forEachIndexed { index, step ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 2.dp).size(22.dp), contentAlignment = Alignment.Center) {
                            when (step.ok) {
                                true -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = t.success)
                                false -> Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = t.destructive)
                                null -> if (step.completed) Text("—", color = t.mutedForeground) else com.xiguli.langhuan.ui.design.LanghuanTypingDotsV31(t.primary, dot = 4.dp)
                            }
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("${index + 1}. ${step.label}", color = t.foreground, style = MaterialTheme.typography.bodyMedium)
                            if (step.detail.isNotBlank()) AiStepDetailV55(step.detail, step.ok == false)
                            val reportDetails = state.aiReport?.discoveryWarnings.orEmpty().map { it.trim() }.toSet()
                            val details = step.details.filterNot { it.trim() in reportDetails }
                            AiDiagnosticsV51("第${index + 1}步详情", details)
                        }
                    }
                }
            }
        }

        // A failed step already owns its complete diagnosis. Keep a separate error only
        // for input/configuration failures that happened before any matching step.
        state.aiError?.takeUnless { error -> state.aiSteps.any { it.ok == false && it.detail == error } }?.let { error ->
            Surface(Modifier.fillMaxWidth(), color = t.destructive.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                Text(error, Modifier.padding(14.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall)
            }
        }

        state.aiReport?.let { report ->
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(14.dp), color = t.muted) {
                Column(Modifier.padding(14.dp)) {
                    Text("「${report.source.name}」书源草稿", color = t.foreground, style = MaterialTheme.typography.titleSmall)
                    Text("搜索返回 ${report.searchCount} 本 · 抽样《${report.bookName}》", Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    Text(sourceCatalogueSummaryV50(report.chapterCount, report.catalogueProof), Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    report.readingWarnings.forEach { Text(it, Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall) }
                    Text(if (report.discoveryLabels.isEmpty()) "未添加发现入口：没有可验证的静态分类，或检查未通过" else "已添加抽样可读入口：${report.discoveryLabels.joinToString("、")}", Modifier.padding(top = 6.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    report.discoveryEvidence.forEach { proof ->
                        Text("${proof.label} · 本页解析 ${proof.bookCount} 本 · 抽查 1 本" + if (proof.nextPageUrl != null) " · 下一页${proof.nextPageBookCount?.let { "解析 $it 本" } ?: "未通过"}" else "", Modifier.padding(top = 6.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                        Text("《${proof.sampleBook}》 · ${proof.sampleChapter.ifBlank { "章节未记录" }}", color = t.foreground, style = MaterialTheme.typography.bodySmall)
                        Text(sourceCatalogueSummaryV50(proof.chapterCount, proof.catalogueProof), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                        Text(proof.url, color = t.mutedForeground, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        proof.readingWarnings.forEach { Text(it, Modifier.padding(top = 2.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall) }
                    }
                    AiDiagnosticsV51("发现详情", report.discoveryWarnings)
                    Text("正文抽样 · ${report.sampleChapter.ifBlank { "章节未记录" }}", Modifier.padding(top = 10.dp), color = t.foreground, style = MaterialTheme.typography.titleSmall)
                    Text(sourceSampleScopeV50(report.chapterProof), Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    if (report.sampleBookUrl.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer {
                        Text("书籍页：${report.sampleBookUrl}", Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
                    }
                    if (report.sampleChapterUrl.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(report.sampleChapterUrl, Modifier.padding(top = 4.dp), color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(report.sample, Modifier.padding(top = 8.dp), color = t.foreground, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                state.aiRunning -> OnlineSheetButtonV36("停止", primary = false, Modifier.weight(1f), onCancel)
                state.aiReport != null -> {
                    OnlineSheetButtonV36("重新生成", primary = false, Modifier.weight(1f)) { onCancel(); onStart(site, keyword) }
                    OnlineSheetButtonV36(if (state.aiReport.source.enabledExplore) "保存书源" else "保存搜索书源", primary = true, Modifier.weight(1f), onSave)
                }
                else -> OnlineSheetButtonV36(if (state.aiError != null) "重试" else "开始生成", primary = true, Modifier.weight(1f)) { onStart(site, keyword) }
            }
        }
        state.aiReport?.takeIf { !it.source.enabledExplore }?.let {
            Text("将保存搜索与阅读规则；发现入口尚未通过", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.navigationBarsPadding().height(18.dp))
    }
}

/** Use actual line overflow: a short message can still exceed three lines at a large font scale. */
@Composable
private fun AiStepDetailV55(text: String, error: Boolean) {
    val t = LocalLanghuanUiTokens.current
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowed by remember(text) { mutableStateOf(false) }
    Text(text, color = if (error) t.destructive else t.mutedForeground,
        style = MaterialTheme.typography.bodySmall,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflowed = it.hasVisualOverflow })
    if (overflowed || expanded) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(vertical = 4.dp)) {
        Text(if (expanded) "收起提示" else "展开完整提示", color = t.primary, style = MaterialTheme.typography.labelLarge)
    }
}

/** A failed optional stage has one readable summary; diagnostics remain available on demand. */
@Composable
private fun AiDiagnosticsV51(label: String, messages: List<String>) {
    val distinct = messages.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (distinct.isEmpty()) return
    val t = LocalLanghuanUiTokens.current
    var expanded by remember(distinct) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(vertical = 4.dp)) {
        Text(if (expanded) "收起$label" else "查看$label（${distinct.size}项）", color = t.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        distinct.forEach { detail -> Text(detail, color = t.mutedForeground, style = MaterialTheme.typography.bodySmall) }
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

private fun isRankingDiscoveryLabelV44(label: String): Boolean =
    label.contains("榜") || label.contains("排行") || Regex("(?i)^(?:top|rank(?:ing)?)(?:\\b|[0-9_ -])").containsMatchIn(label.trim())
