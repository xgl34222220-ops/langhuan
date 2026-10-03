package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.engine.ProjectConversationStore
import com.xiguli.langhuan.LanghuanApplication

private enum class RootRouteV4 {
    SHELF,
    BOOK,
    CREATION,
    CREATION_RESEARCH,
    TAVERN,
    WRITING,
    EDITOR,
    AGENT,
    INTELLIGENCE,
    RUN_CENTER,
    AI_SETUP,
    COVER_STUDIO,
    SKILLS,
    ONLINE,
}

@Composable
fun LanghuanRootV4(studioVm: StudioViewModel, externalBooks: ExternalBookImportCoordinatorV1? = null) {
    val studioState by studioVm.state.collectAsStateWithLifecycle()
    val libraryVm: LibraryExperienceViewModel = viewModel()
    val editorVm: ChapterEditorViewModel = viewModel()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val localImportVm: LocalBookImportViewModelV1 = viewModel()
    val localImportState by localImportVm.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val projectConversationStore = remember(appContext) { ProjectConversationStore(appContext) }

    // Configuration recreation must keep an open reader on screen. Other tools retain their
    // existing shelf fallback; their transient editors are not reconstructed from only a route.
    var route by rememberSaveable(stateSaver = Saver<RootRouteV4, String>(
        save = { if (it == RootRouteV4.BOOK) "book" else "shelf" },
        restore = { if (it == "book") RootRouteV4.BOOK else RootRouteV4.SHELF },
    )) { mutableStateOf(RootRouteV4.SHELF) }
    LaunchedEffect(route, libraryState.libraryLoaded, libraryState.openedBook) {
        // After process death the ViewModel may no longer hold the book. Return to a usable
        // shelf; tapping the book reloads its durable sentence anchor from ReaderProgressStore.
        if (route == RootRouteV4.BOOK && libraryState.libraryLoaded && libraryState.openedBook == null) {
            route = RootRouteV4.SHELF
        }
    }
    com.xiguli.langhuan.ui.design.PaperReaderSystemBarsV44(
        lightBackground = route in setOf(RootRouteV4.SHELF, RootRouteV4.ONLINE) || !androidx.compose.foundation.isSystemInDarkTheme(),
        readerActive = route == RootRouteV4.BOOK,
    )
    var returnAfterAiSetup by remember { mutableStateOf(RootRouteV4.SHELF) }
    var returnAfterSkills by remember { mutableStateOf(RootRouteV4.SHELF) }
    var returnAfterEditor by remember { mutableStateOf(RootRouteV4.BOOK) }
    var writingStoryId by remember { mutableStateOf<String?>(null) }
    var editorStoryId by remember { mutableStateOf<String?>(null) }
    var editorChapter by remember { mutableStateOf<Int?>(null) }
    var coverStoryId by remember { mutableStateOf<String?>(null) }
    var openBookOnInfo by remember { mutableStateOf(false) }
    var tavernStoryId by remember { mutableStateOf<String?>(null) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var pendingBookRoute by remember { mutableStateOf<RootRouteV4?>(null) }
    var pendingBookFreshReload by remember { mutableStateOf(false) }

    val localBookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) localImportVm.importUri(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) studioVm.exportProjectBackup(uri)
    }
    var pendingExport by remember { mutableStateOf<Pair<String, com.xiguli.langhuan.data.ExportFormat>?>(null) }
    val exportLaunchers = com.xiguli.langhuan.data.ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            val job = pendingExport
            pendingExport = null
            if (uri != null && job != null) libraryVm.exportBook(job.first, job.second, uri)
        }
    }
    fun exportBook(id: String, format: com.xiguli.langhuan.data.ExportFormat) {
        val title = libraryState.stories.firstOrNull { it.id == id }?.title ?: "琅嬛作品"
        pendingExport = id to format
        exportLaunchers[format]?.launch("$title.${format.extension}")
    }

    val onlineVm: OnlineBooksViewModelV36 = viewModel()
    val onlineState by onlineVm.state.collectAsStateWithLifecycle()
    var pendingOnlineOpen by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Library success messages that are not owned by a page appear as a lightweight app toast.
    LaunchedEffect(libraryState.message) {
        val text = libraryState.message ?: return@LaunchedEffect
        toast = text to false
        libraryVm.clearMessage()
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2600)
            toast = null
        }
    }

    // A blank hand-written book enters chapter 1 immediately.
    LaunchedEffect(libraryState.createdBlankStoryId) {
        val id = libraryState.createdBlankStoryId ?: return@LaunchedEffect
        libraryVm.consumeCreatedBlankStory()
        studioVm.selectStory(id)
        editorStoryId = id
        editorChapter = 1
        returnAfterEditor = RootRouteV4.SHELF
        route = RootRouteV4.EDITOR
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) studioVm.importDocument(uri)
    }

    fun requestBook(id: String, target: RootRouteV4, showInfo: Boolean = false) {
        if (libraryState.isBusy) return
        // A stale pending id must never lock the whole shelf. A fresh tap owns the request.
        pendingBookFreshReload = false
        pendingBookId = null
        pendingBookRoute = null
        openBookOnInfo = showInfo
        pendingBookFreshReload = libraryState.openedBook?.id == id && libraryState.readingChapter != null
        pendingBookId = id
        pendingBookRoute = target
        if (target == RootRouteV4.TAVERN) {
            tavernStoryId = id
            studioVm.selectStory(id)
        }
        libraryVm.openBook(id)
    }

    var epubOpening by remember { mutableStateOf(false) }
    val epubReaderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        epubOpening = false
        if (result.resultCode == com.xiguli.langhuan.ui.epub.EpubReaderActivity.RESULT_TEXT_READER) {
            result.data?.getStringExtra(com.xiguli.langhuan.ui.epub.EpubReaderActivity.EXTRA_BOOK_ID)?.let { id ->
                requestBook(id, RootRouteV4.BOOK, showInfo = false)
            }
        }
    }
    fun openBook(id: String) {
        if (epubOpening || libraryState.isBusy) return
        if (libraryState.stories.none { it.id == id }) {
            toast = "找不到这本小说，请刷新书架" to true
            return
        }
        if (com.xiguli.langhuan.ui.epub.EpubReaderEntry.isEpub(appContext, id)) {
            pendingBookId = null
            pendingBookRoute = null
            pendingBookFreshReload = false
            epubOpening = true
            try {
                epubReaderLauncher.launch(com.xiguli.langhuan.ui.epub.EpubReaderEntry.intent(appContext, id))
            } catch (error: Exception) {
                epubOpening = false
                toast = "无法打开 EPUB 原版：${error.message.orEmpty()}" to true
            }
        } else requestBook(id, RootRouteV4.BOOK, showInfo = false)
    }

    LaunchedEffect(pendingOnlineOpen, libraryState.stories) {
        val id = pendingOnlineOpen ?: return@LaunchedEffect
        if (libraryState.stories.any { it.id == id }) {
            pendingOnlineOpen = null
            openBook(id)
        }
    }

    fun openTavern(id: String) {
        tavernStoryId = id
        studioVm.selectStory(id)
        if (studioState.provider.ready) {
            requestBook(id, RootRouteV4.TAVERN, showInfo = false)
        } else {
            libraryVm.openBook(id)
            returnAfterAiSetup = RootRouteV4.TAVERN
            route = RootRouteV4.AI_SETUP
        }
    }

    fun backToBook() {
        val id = libraryState.openedBook?.id ?: writingStoryId
        if (id != null) openBook(id) else route = RootRouteV4.SHELF
    }

    fun openAiSetup(from: RootRouteV4) {
        returnAfterAiSetup = from
        route = RootRouteV4.AI_SETUP
    }

    fun openSkills(from: RootRouteV4) {
        returnAfterSkills = from
        route = RootRouteV4.SKILLS
    }

    fun enterCreatedProject(id: String, creationVm: NewBookConversationViewModel) {
        val creationMessages = creationVm.state.value.messages.map { it.role to it.text }
        projectConversationStore.handoffFromCreation(id, creationMessages)
        creationVm.reset()
        writingStoryId = id
        libraryVm.openBook(id)
        studioVm.selectStory(id)
        route = RootRouteV4.WRITING
    }

    LaunchedEffect(
        pendingBookId,
        pendingBookRoute,
        pendingBookFreshReload,
        libraryState.openedBook?.id,
        libraryState.chapters,
        libraryState.readingChapter?.id,
        libraryState.isBusy,
    ) {
        val id = pendingBookId ?: return@LaunchedEffect
        val targetRoute = pendingBookRoute ?: return@LaunchedEffect
        if (pendingBookFreshReload) {
            if (libraryState.isBusy) return@LaunchedEffect
            if (libraryState.readingChapter != null) return@LaunchedEffect
        }
        val book = libraryState.openedBook?.takeIf { it.id == id } ?: return@LaunchedEffect
        val chapters = libraryState.chapters.sortedBy { it.readingOrder }
        if (chapters.isNotEmpty()) {
            val saved = ReaderProgressStoreV11.load(appContext, id, book.currentChapter.coerceAtLeast(1))
            val requestedEditorChapter = editorChapter?.takeIf { targetRoute == RootRouteV4.BOOK }
            val target = requestedEditorChapter?.let { number -> chapters.firstOrNull { it.chapterNumber == number } }
                ?: chapters.firstOrNull { it.chapterNumber == saved.chapterNumber }
                ?: chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                ?: chapters.first()
            libraryVm.openReader(target.chapterNumber)
        }
        pendingBookFreshReload = false
        pendingBookId = null
        pendingBookRoute = null
        route = targetRoute
    }

    LaunchedEffect(libraryState.error, pendingBookId) {
        if (pendingBookId != null && libraryState.error != null) {
            pendingBookFreshReload = false
            pendingBookId = null
            pendingBookRoute = null
        }
    }

    // Reading a book must not silently switch the Studio's persisted active project.
    // Studio selection happens only when the user explicitly enters writing/story tools.

    LaunchedEffect(localImportState.importedBookId, localImportState.externalRequestUri, libraryState.stories) {
        val id = localImportState.importedBookId ?: return@LaunchedEffect
        // Finish the import result dialog before opening another Activity for an EPUB.
        if (localImportState.externalRequestUri != null) return@LaunchedEffect
        if (libraryState.stories.any { it.id == id }) {
            localImportVm.consumeImportedBook()
            openBook(id)
        }
    }

    // Root-level safety net for system back. Nested screens register later and take precedence.
    BackHandler(enabled = route != RootRouteV4.SHELF) {
        when (route) {
            RootRouteV4.SHELF -> Unit
            RootRouteV4.BOOK -> {
                libraryVm.closeBook()
                editorChapter = null
                route = RootRouteV4.SHELF
            }
            RootRouteV4.CREATION -> route = RootRouteV4.SHELF
            RootRouteV4.CREATION_RESEARCH -> route = RootRouteV4.CREATION
            RootRouteV4.TAVERN -> {
                tavernStoryId = null
                libraryVm.closeBook()
                route = RootRouteV4.SHELF
            }
            RootRouteV4.WRITING -> openBook(writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id)
            RootRouteV4.EDITOR -> {
                val id = editorStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                when (returnAfterEditor) {
                    RootRouteV4.WRITING -> {
                        writingStoryId = id
                        route = RootRouteV4.WRITING
                    }
                    RootRouteV4.SHELF -> {
                        libraryVm.closeBook()
                        editorStoryId = null
                        editorChapter = null
                        route = RootRouteV4.SHELF
                    }
                    else -> openBook(id)
                }
            }
            RootRouteV4.AGENT, RootRouteV4.INTELLIGENCE -> backToBook()
            RootRouteV4.RUN_CENTER -> route = if (libraryState.openedBook != null) RootRouteV4.BOOK else RootRouteV4.SHELF
            RootRouteV4.AI_SETUP -> route = when {
                !studioState.provider.ready && returnAfterAiSetup in setOf(
                    RootRouteV4.CREATION,
                    RootRouteV4.CREATION_RESEARCH,
                    RootRouteV4.TAVERN,
                ) -> RootRouteV4.SHELF
                else -> returnAfterAiSetup
            }
            RootRouteV4.COVER_STUDIO -> route = RootRouteV4.BOOK
            RootRouteV4.SKILLS -> route = returnAfterSkills
            RootRouteV4.ONLINE -> route = RootRouteV4.SHELF
        }
    }

    val routeStates = rememberSaveableStateHolder()
    if (externalBooks != null) ExternalBookImportHostV1(externalBooks, localImportVm)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = route,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { rootRouteTransitionV30(initialState, targetState) },
            label = "rootRoute",
        ) { currentRoute ->
          routeStates.SaveableStateProvider(currentRoute.name) {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (currentRoute) {
                RootRouteV4.SHELF -> {
                    LanghuanHomeV4(
                        state = libraryState,
                        importState = localImportState,
                        onOpenBook = ::openBook,
                        onImportLocal = { localBookLauncher.launch(arrayOf("*/*")) },
                        onDeleteBook = libraryVm::deleteBook,
                        onCreate = {
                            if (studioState.provider.ready) route = RootRouteV4.CREATION
                            else openAiSetup(RootRouteV4.CREATION)
                        },
                        onOpenTavern = ::openTavern,
                        onAiSetup = { openAiSetup(RootRouteV4.SHELF) },
                        onRunCenter = { route = RootRouteV4.RUN_CENTER },
                        onSkills = { openSkills(RootRouteV4.SHELF) },
                        onOnline = { route = RootRouteV4.ONLINE },
                    )
                }

                RootRouteV4.BOOK -> {
                    if (libraryState.openedBook != null) {
                        ReaderNativeExperienceV4(
                            viewModel = libraryVm,
                            studioState = studioState,
                            onBackToShelf = {
                                libraryVm.closeBook()
                                editorChapter = null
                                route = RootRouteV4.SHELF
                            },
                            onEnterWriting = { id ->
                                writingStoryId = id
                                studioVm.selectStory(id)
                                route = RootRouteV4.WRITING
                            },
                            onOpenEditor = { id, chapter ->
                                editorVm.prepareForEntry(id, chapter)
                                editorStoryId = id
                                editorChapter = chapter
                                returnAfterEditor = RootRouteV4.BOOK
                                route = RootRouteV4.EDITOR
                            },
                            onOpenAiSetup = { openAiSetup(RootRouteV4.BOOK) },
                            startOnInfo = openBookOnInfo,
                        )
                    }
                }

                RootRouteV4.CREATION -> {
                    val creationVm: NewBookConversationViewModel = viewModel()
                    CreationChatV4(
                        viewModel = creationVm,
                        onClose = { route = RootRouteV4.SHELF },
                        onConfigureAi = { openAiSetup(RootRouteV4.CREATION) },
                        onAdvancedResearch = { route = RootRouteV4.CREATION_RESEARCH },
                        onCreated = { id -> enterCreatedProject(id, creationVm) },
                    )
                }

                RootRouteV4.CREATION_RESEARCH -> {
                    val creationVm: NewBookConversationViewModel = viewModel()
                    ResearchNewBookConversationPage(
                        viewModel = creationVm,
                        onClose = { route = RootRouteV4.CREATION },
                        onConfigureAi = { openAiSetup(RootRouteV4.CREATION_RESEARCH) },
                        onSwitchModel = { openAiSetup(RootRouteV4.CREATION_RESEARCH) },
                        onCreated = { id -> enterCreatedProject(id, creationVm) },
                    )
                }

                RootRouteV4.TAVERN -> {
                    val book = libraryState.openedBook
                    if (book == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    } else {
                        StoryCleanExperience(
                            book = book,
                            libraryState = libraryState,
                            aiReady = studioState.provider.ready,
                            onAiSetup = { openAiSetup(RootRouteV4.TAVERN) },
                            onAdopted = { libraryVm.openBook(book.id) },
                            onLoadCurrentChapter = {
                                (libraryState.readingChapter ?: libraryState.chapters.minByOrNull { it.readingOrder })
                                    ?.let { libraryVm.openReader(it.chapterNumber) }
                            },
                            onBack = {
                                tavernStoryId = null
                                libraryVm.closeBook()
                                route = RootRouteV4.SHELF
                            },
                        )
                    }
                }

                RootRouteV4.WRITING -> {
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    WritingFlowPage(
                        novelId = id,
                        viewModel = writingVm,
                        onClose = { openBook(id) },
                        onAiSetup = { writingStoryId = id; openAiSetup(RootRouteV4.WRITING) },
                        onEditChapter = { storyId, chapter ->
                            editorVm.prepareForEntry(storyId, chapter)
                            editorStoryId = storyId
                            editorChapter = chapter
                            returnAfterEditor = RootRouteV4.WRITING
                            route = RootRouteV4.EDITOR
                        },
                    )
                }

                RootRouteV4.EDITOR -> {
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = editorStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    ChapterEditorExperience(
                        novelId = id,
                        initialChapter = editorChapter,
                        viewModel = editorVm,
                        onClose = {
                            when (returnAfterEditor) {
                                RootRouteV4.WRITING -> {
                                    writingVm.invalidateAfterExternalEdit(id)
                                    route = RootRouteV4.WRITING
                                }
                                RootRouteV4.SHELF -> {
                                    libraryVm.closeBook()
                                    editorStoryId = null
                                    editorChapter = null
                                    route = RootRouteV4.SHELF
                                }
                                else -> openBook(id)
                            }
                        },
                    )
                }

                RootRouteV4.AGENT -> {
                    AgentPage(
                        state = studioState,
                        vm = studioVm,
                        onProjectBackup = { backupLauncher.launch("${studioState.snapshot.novel.title}.lhproj") },
                        onProjectRestore = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        onClose = ::backToBook,
                    )
                }

                RootRouteV4.INTELLIGENCE -> StoryIntelligencePage(state = studioState, onClose = ::backToBook)

                RootRouteV4.RUN_CENTER -> {
                    val runCenterVm: RunCenterViewModel = viewModel()
                    val runCenterState by runCenterVm.state.collectAsStateWithLifecycle()
                    val appContext2 = LocalContext.current.applicationContext as LanghuanApplication
                    val runtimeState by appContext2.chapterRunRuntime.state.collectAsStateWithLifecycle()
                    LaunchedEffect(runCenterState.openRequest?.token) {
                        runCenterState.openRequest?.let { request ->
                            runCenterVm.consumeOpenRequest()
                            writingStoryId = request.novelId
                            libraryVm.openBook(request.novelId)
                            studioVm.selectStory(request.novelId)
                            route = RootRouteV4.WRITING
                        }
                    }
                    RunCenterScreenV50(
                        state = runCenterState,
                        runtime = runtimeState,
                        onBack = { route = if (libraryState.openedBook != null) RootRouteV4.BOOK else RootRouteV4.SHELF },
                        onOpenTask = runCenterVm::open,
                        onRetryTask = runCenterVm::open,
                        onCancelTask = runCenterVm::abandon,
                        onCancelCurrent = { appContext2.chapterRunRuntime.stopCurrentGeneration() },
                    )
                }

                RootRouteV4.AI_SETUP -> {
                    AiProviderSetupPage(
                        state = studioState,
                        vm = studioVm,
                        onBack = {
                            route = when {
                                !studioState.provider.ready && returnAfterAiSetup in setOf(
                                    RootRouteV4.CREATION,
                                    RootRouteV4.CREATION_RESEARCH,
                                    RootRouteV4.TAVERN,
                                ) -> RootRouteV4.SHELF
                                else -> returnAfterAiSetup
                            }
                        },
                        onDone = { route = returnAfterAiSetup },
                    )
                }

                RootRouteV4.COVER_STUDIO -> {
                    @Suppress("UNUSED_VARIABLE")
                    val coverGuard: CoverPersistenceGuardViewModel = viewModel()
                    val id = coverStoryId ?: libraryState.openedBook?.id
                    if (id != null) {
                        CoverStudioV3(
                            bookId = id,
                            libraryViewModel = libraryVm,
                            onClose = { route = RootRouteV4.BOOK },
                        )
                    }
                }

                RootRouteV4.ONLINE -> {
                    var onlineSub by rememberSaveable { mutableStateOf("main") }
                    var browseSourceId by rememberSaveable { mutableStateOf<String?>(null) }
                    var discoverySourceId by rememberSaveable { mutableStateOf<String?>(null) }
                    var onlineQuery by rememberSaveable { mutableStateOf("") }
                    val recentSearches = remember { mutableStateListOf<String>() }
                    var aiSiteUrl by rememberSaveable { mutableStateOf("") }
                    var aiTestBook by rememberSaveable { mutableStateOf("") }
                    var importDialogOpen by remember { mutableStateOf(false) }
                    var importText by remember { mutableStateOf("") }
                    val clipboard = LocalClipboardManager.current
                    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            onlineVm.importFromFile(uri)
                            importDialogOpen = false
                        }
                    }

                    // 书籍详情"加入书架/开始阅读"后的建书完成流转（替代旧 onOpenCreated 回调）
                    LaunchedEffect(onlineState.createdStoryId) {
                        val id = onlineState.createdStoryId ?: return@LaunchedEffect
                        onlineVm.consumeCreated()
                        route = RootRouteV4.SHELF
                        pendingOnlineOpen = id
                    }

                    val browseSource = browseSourceId?.let { id -> onlineState.sources.firstOrNull { it.id == id } }
                    val discoverySource = discoverySourceId?.let { id -> onlineState.sources.firstOrNull { it.id == id } }

                    // 书源规则编辑弹窗（旧页同款能力，走 VM 已有 begin/edit/cancel API）
                    if (onlineState.sourceEditId != null) {
                        val editId = onlineState.sourceEditId!!
                        AlertDialog(
                            onDismissRequest = onlineVm::cancelSourceEdit,
                            title = { Text("编辑书源规则") },
                            text = {
                                Column {
                                    TextField(
                                        value = onlineState.sourceEditDraft,
                                        label = { Text("书源 JSON") },
                                        onValueChange = onlineVm::updateSourceEditDraft,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                                    )
                                    onlineState.sourceEditError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { onlineVm.editSource(editId, onlineState.sourceEditDraft) },
                                    enabled = !onlineState.sourceEditSaving,
                                ) { Text(if (onlineState.sourceEditSaving) "校验中…" else "保存规则") }
                            },
                            dismissButton = { TextButton(onClick = onlineVm::cancelSourceEdit) { Text("取消") } },
                        )
                    }

                    if (importDialogOpen) {
                        AlertDialog(
                            onDismissRequest = { importDialogOpen = false },
                            title = { Text("导入书源") },
                            text = {
                                Column {
                                    Text("粘贴书源 JSON，或输入书源网址。")
                                    Spacer(Modifier.height(8.dp))
                                    TextField(
                                        value = importText,
                                        onValueChange = { importText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("JSON 或 https://…") },
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val t = importText.trim()
                                    if (t.startsWith("http")) onlineVm.importFromUrl(t) else onlineVm.importSources(t)
                                    importText = ""
                                    importDialogOpen = false
                                }) { Text("导入") }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(onClick = { importFileLauncher.launch(arrayOf("*/*")) }) { Text("选文件") }
                                    TextButton(onClick = { importDialogOpen = false }) { Text("取消") }
                                }
                            },
                        )
                    }

                    when {
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
                                    toast = "书源 JSON 已复制" to false
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
                                onBack = { onlineSub = "main" },
                                onOpenSource = { browseSourceId = it.id },
                                onToggleSource = onlineVm::toggleSource,
                                onImportSource = { importDialogOpen = true },
                                onAiGenerateSource = { onlineSub = "ai" },
                            )
                        }
                        onlineSub == "ai" -> {
                            AiBookSourceScreenV50(
                                state = onlineState,
                                siteUrl = aiSiteUrl,
                                testBookName = aiTestBook,
                                onBack = { onlineSub = "manage" },
                                onSiteUrlChange = { aiSiteUrl = it },
                                onTestBookNameChange = { aiTestBook = it },
                                onConfigureAi = { openAiSetup(RootRouteV4.ONLINE) },
                                onStart = { url, keyword -> onlineVm.buildWithAi(url, keyword) },
                                onStartWithBrowser = { url, keyword -> onlineVm.buildWithAi(url, keyword, useBrowser = true) },
                                onCancel = onlineVm::cancelAi,
                                onSave = { onlineVm.saveAiSource() },
                            )
                        }
                        else -> {
                            OnlineBooksScreenV50(
                                state = onlineState,
                                query = onlineQuery,
                                recentSearches = recentSearches,
                                onBack = { route = RootRouteV4.SHELF },
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
                                onRecentSearch = { q ->
                                    onlineQuery = q
                                    onlineVm.search(q)
                                },
                                onClearRecentSearches = { recentSearches.clear() },
                                onDiscover = onlineVm::discover,
                                onLoadMore = onlineVm::loadMoreDiscovery,
                                onOpenBook = onlineVm::openDetail,
                                onCloseDetail = onlineVm::closeDetail,
                                onViewSource = { id -> browseSourceId = id },
                                onAddToShelf = onlineVm::addToShelf,
                                onRead = onlineVm::readAddedBook,
                                onDownload = onlineVm::downloadDetail,
                                onCancelDownload = onlineVm::cancelDownload,
                                onChapterClick = { chapter ->
                                    toast = "「${chapter.title}」先加入书架后再阅读" to false
                                },
                            )
                        }
                    }
                }

                RootRouteV4.SKILLS -> {
                    val skillVm: WritingSkillViewModel = viewModel()
                    SkillsPageV3(
                        viewModel = skillVm,
                        onClose = { route = returnAfterSkills },
                    )
                }
            }
          }
          }
        }
    }

    toast?.let { (text, isError) ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
                shape = RoundedCornerShape(999.dp),
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text,
                    Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
        }
    }

    if (onlineState.detail == null) onlineState.download?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("离线下载") },
            text = { Column { Text("已缓存 ${progress.done}/${progress.total} 章"); Text("停止后，已完成的章节仍可离线阅读") } },
            confirmButton = { TextButton(onClick = onlineVm::cancelDownload) { Text("停止缓存") } },
        )
    }

    libraryState.error?.let { error ->
        AlertDialog(
            onDismissRequest = libraryVm::clearMessage,
            title = { Text("打开失败") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = libraryVm::clearMessage) { Text("知道了") } },
        )
    }

    localImportState.error?.let { error ->
        AlertDialog(
            onDismissRequest = localImportVm::clearFeedback,
            title = { Text("导入失败") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = localImportVm::clearFeedback) { Text("知道了") } },
        )
    }
}

/**
 * App-level route motion. Opening a book zooms the page up out of the shelf; everything else is
 * a short parallax slide. Going back to the shelf always plays the reverse.
 */
private fun AnimatedContentTransitionScope<RootRouteV4>.rootRouteTransitionV30(
    from: RootRouteV4,
    to: RootRouteV4,
): ContentTransform {
    val enterEase = tween<Float>(320, easing = FastOutSlowInEasing)
    return when {
        to == RootRouteV4.BOOK && from == RootRouteV4.SHELF ->
            (fadeIn(tween(220)) + scaleIn(enterEase, initialScale = .92f)) togetherWith
                (fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 1.03f))
        from == RootRouteV4.BOOK && to == RootRouteV4.SHELF ->
            (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 1.03f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(280), targetScale = .92f))
        to == RootRouteV4.SHELF ->
            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
        else ->
            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(200)))
    }.apply { targetContentZIndex = if (to == RootRouteV4.SHELF) 0f else 1f }
}
