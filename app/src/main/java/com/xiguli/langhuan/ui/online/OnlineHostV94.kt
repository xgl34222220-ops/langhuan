package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * V94 在线书城宿主：书城首页、书源管理/新建/导入/编辑、AI 生成、书源详情与发现页。
 *
 * 原先这些全部内联在 LanghuanRootV4 的 ONLINE 分支里；抽出来后它既能作为底栏的「书城」标签页
 * （[embedded] = true，没有返回箭头），也保持原有的 ViewModel API 和导航语义。
 * [onRootLevelChange] 告诉外层当前是否停在书城首页（用于显示/隐藏底栏）。
 */
@Composable
internal fun OnlineHostV94(
    onlineVm: OnlineBooksViewModelV36,
    onlineState: OnlineBooksStateV36,
    embedded: Boolean,
    onBack: () -> Unit,
    onOpenAiSetup: () -> Unit,
    onCreatedStory: (String) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onRootLevelChange: (Boolean) -> Unit = {},
    /** Bumped by 「我的 → 书源管理」 to open the manage page directly. */
    openManageRequest: Int = 0,
) {
    var onlineSub by rememberSaveable { mutableStateOf("main") }
    var browseSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var discoverySourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var onlineQuery by rememberSaveable { mutableStateOf("") }
    val recentSearches = remember { mutableStateListOf<String>() }
    var aiSiteUrl by rememberSaveable { mutableStateOf("") }
    var aiTestBook by rememberSaveable { mutableStateOf("") }
    var sourceManageFocusId by rememberSaveable { mutableStateOf<String?>(null) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    var handledManageRequest by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(openManageRequest) {
        if (openManageRequest != handledManageRequest) {
            handledManageRequest = openManageRequest
            browseSourceId = null
            discoverySourceId = null
            onlineSub = "manage"
        }
    }
    val clipboard = LocalClipboardManager.current
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onlineVm.importFromFile(uri)
            importOpen = false
            onlineSub = "manage"
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onlineVm.exportSourcesTo(uri)
    }

    // 书籍详情"加入书架/开始阅读"后的建书完成流转
    LaunchedEffect(onlineState.createdStoryId) {
        val id = onlineState.createdStoryId ?: return@LaunchedEffect
        onlineVm.consumeCreated()
        onCreatedStory(id)
    }
    // Source operations report success through the VM message; show it as the app toast.
    LaunchedEffect(onlineState.message) {
        val text = onlineState.message ?: return@LaunchedEffect
        onToast(text, false)
        onlineVm.consumeMessage()
    }

    val browseSource = browseSourceId?.let { id -> onlineState.sources.firstOrNull { it.id == id } }
    val discoverySource = discoverySourceId?.let { id -> onlineState.sources.firstOrNull { it.id == id } }
    val editing = onlineState.sourceEditId != null
    val rootLevel = !editing && !importOpen && browseSource == null && discoverySource == null &&
        onlineSub == "main" && onlineState.detail == null
    SideEffect { onRootLevelChange(rootLevel) }

    // Registered before the pages so their own handlers (editor, import, detail) take precedence;
    // the manage/AI pages fall back to the store page instead of leaving 书城.
    androidx.activity.compose.BackHandler(enabled = !editing && !importOpen && browseSource == null && discoverySource == null && onlineSub != "main") {
        sourceManageFocusId = null
        onlineSub = if (onlineSub == "ai") "manage" else "main"
    }
    androidx.activity.compose.BackHandler(enabled = !editing && !importOpen && (browseSource != null || discoverySource != null)) {
        if (discoverySource != null) discoverySourceId = null else browseSourceId = null
    }
    androidx.activity.compose.BackHandler(enabled = !editing && !importOpen && browseSource == null && discoverySource == null &&
        onlineSub == "main" && onlineState.detail != null && onlineState.download == null) {
        onlineVm.closeDetail()
    }

    when {
        editing -> {
            val editId = onlineState.sourceEditId!!
            BookSourceEditorScreenV94(
                creating = editId == SOURCE_NEW_ID_V94,
                draft = onlineState.sourceEditDraft,
                error = onlineState.sourceEditError,
                saving = onlineState.sourceEditSaving,
                test = onlineState.sourceTest,
                onDraftChange = onlineVm::updateSourceEditDraft,
                onSave = {
                    if (editId == SOURCE_NEW_ID_V94) {
                        // A freshly created source is highlighted at the top of the manage list.
                        sourceManageFocusId = sourceDraftFormV94(onlineState.sourceEditDraft)
                            ?.let { it.id.ifBlank { manualSourceIdV94(it.baseUrl) } }
                        onlineSub = "manage"
                    }
                    onlineVm.editSource(editId, onlineState.sourceEditDraft)
                },
                onCancel = onlineVm::cancelSourceEdit,
                onTest = { keyword -> onlineVm.testSourceDraft(onlineState.sourceEditDraft, keyword) },
            )
        }
        importOpen -> {
            BookSourceImportScreenV94(
                importing = onlineState.sourceImporting,
                onBack = { importOpen = false },
                onPickFile = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                onImportUrl = { url ->
                    onlineVm.importFromUrl(url)
                    importOpen = false
                    onlineSub = "manage"
                },
                onImportText = { text ->
                    onlineVm.importSources(text)
                    importOpen = false
                    onlineSub = "manage"
                },
                onCreateFromJson = { text ->
                    importOpen = false
                    onlineSub = "manage"
                    onlineVm.beginSourceCreate(text)
                },
            )
        }
        browseSource != null -> {
            BookSourceBrowseScreenV50(
                source = browseSource,
                discoveries = sourceDiscoveriesV41(browseSource),
                previewBooks = emptyList(),
                onBack = { browseSourceId = null },
                onEditRules = { onlineVm.beginSourceEdit(browseSource.id) },
                onEnabledChange = { onlineVm.toggleSource(browseSource.id) },
                onOpenDiscovery = { section ->
                    discoverySourceId = browseSource.id
                    onlineVm.discover(section)
                },
                onOpenBook = { book ->
                    onlineVm.openDetail(book)
                    browseSourceId = null
                },
                onCopySourceJson = {
                    clipboard.setText(AnnotatedString(BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), browseSource)))
                    onToast("书源 JSON 已复制", false)
                },
                onDeleteSource = {
                    onlineVm.deleteSource(browseSource.id)
                    browseSourceId = null
                },
            )
        }
        discoverySource != null -> {
            BookSourceDiscoveryScreenV50(
                source = discoverySource,
                sections = sourceDiscoveriesV41(discoverySource),
                selectedSection = onlineState.discoverySection,
                books = onlineState.results,
                loading = onlineState.searching,
                hasMore = onlineState.discoveryHasMore,
                pageError = onlineState.discoveryPageError,
                onBack = { discoverySourceId = null },
                onEditRules = { onlineVm.beginSourceEdit(discoverySource.id) },
                onEnabledChange = { onlineVm.toggleSource(discoverySource.id) },
                onSelectSection = onlineVm::discover,
                onOpenBook = { book ->
                    onlineVm.openDetail(book)
                    discoverySourceId = null
                },
                onLoadMore = onlineVm::loadMoreDiscovery,
                onStop = onlineVm::stopSearch,
            )
        }
        onlineSub == "manage" -> {
            BookSourceManageScreenV50(
                sources = onlineState.sources,
                sourceStorageError = onlineState.sourceStorageError,
                focusSourceId = sourceManageFocusId,
                onBack = {
                    sourceManageFocusId = null
                    onlineVm.dismissSourceImportReport()
                    onlineSub = "main"
                },
                onOpenSource = {
                    sourceManageFocusId = null
                    browseSourceId = it.id
                },
                onToggleSource = onlineVm::toggleSource,
                onImportSource = { importOpen = true },
                onAiGenerateSource = {
                    sourceManageFocusId = null
                    onlineSub = "ai"
                },
                onCreateSource = { onlineVm.beginSourceCreate() },
                onExportSources = { exportLauncher.launch("琅嬛书源.json") },
                importing = onlineState.sourceImporting,
                importReport = onlineState.sourceImportReport,
                onDismissImportReport = onlineVm::dismissSourceImportReport,
            )
        }
        onlineSub == "ai" -> {
            AiBookSourceScreenV50(
                state = onlineState,
                siteUrl = aiSiteUrl,
                testBookName = aiTestBook,
                onBack = {
                    sourceManageFocusId = null
                    onlineSub = "manage"
                },
                onSiteUrlChange = { aiSiteUrl = it },
                onTestBookNameChange = { aiTestBook = it },
                onConfigureAi = onOpenAiSetup,
                onStart = { url, keyword -> onlineVm.buildWithAi(url, keyword) },
                onStartWithBrowser = { url, keyword -> onlineVm.buildWithAi(url, keyword, useBrowser = true) },
                onCancel = onlineVm::cancelAi,
                onSave = { onlineVm.saveAiSource() },
                onOpenSavedSource = { sourceId ->
                    sourceManageFocusId = sourceId
                    onlineSub = "manage"
                },
            )
        }
        else -> {
            OnlineBooksScreenV50(
                state = onlineState,
                query = onlineQuery,
                recentSearches = recentSearches,
                embedded = embedded,
                onBack = onBack,
                onManageSources = { onlineSub = "manage" },
                onQueryChange = { onlineQuery = it },
                onSearch = { q ->
                    onlineQuery = q
                    val key = q.trim()
                    if (key.isNotEmpty()) {
                        recentSearches.remove(key)
                        recentSearches.add(0, key)
                        if (recentSearches.size > 10) recentSearches.removeAt(recentSearches.lastIndex)
                    }
                    onlineVm.search(q)
                },
                onStopSearch = onlineVm::stopSearch,
                onRetrySearch = onlineVm::retrySearch,
                onRecentSearch = { q ->
                    onlineQuery = q
                    onlineVm.search(q)
                },
                onClearRecentSearches = { recentSearches.clear() },
                onDiscover = onlineVm::discover,
                onLoadMore = onlineVm::loadMoreDiscovery,
                onOpenBook = onlineVm::openDetail,
                onCloseDetail = onlineVm::closeDetail,
                onRetryDetail = onlineVm::retryDetail,
                onStopDetail = onlineVm::stopDetail,
                onViewSource = { id -> browseSourceId = id },
                onAddToShelf = onlineVm::addToShelf,
                onRead = onlineVm::readAddedBook,
                onDownload = onlineVm::downloadDetail,
                onCancelDownload = onlineVm::cancelDownload,
                onChapterClick = { chapter ->
                    onToast("「${chapter.title}」先加入书架后再阅读", false)
                },
            )
        }
    }

}
