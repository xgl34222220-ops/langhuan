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
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
    RUN_CENTER,
    AI_SETUP,
    SKILLS,
    ONLINE,
    /** V94 bottom-bar tabs: 创作 and 我的 (书架 = SHELF, 书城 = ONLINE). */
    CREATE_HUB,
    MINE,
}

/** V94: the four bottom-bar destinations, in bar order. */
private val ROOT_TABS_V94 = listOf(RootRouteV4.SHELF, RootRouteV4.ONLINE, RootRouteV4.CREATE_HUB, RootRouteV4.MINE)

/** Run Center checkpoint polling while the screen is visible. */
internal const val RUN_CENTER_REFRESH_INTERVAL_MS_V85 = 3_000L

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

    // Retained reader/online ViewModels own their configuration-recreation state, including
    // an unsaved source draft. Restoring the online route is also safe with a fresh ViewModel.
    // Other transient tools still need more than a saved route to reconstruct their editors.
    var route by rememberSaveable(stateSaver = Saver<RootRouteV4, String>(
        save = { when (it) { RootRouteV4.BOOK -> "book"; RootRouteV4.ONLINE -> "online"; RootRouteV4.CREATE_HUB -> "create"; RootRouteV4.MINE -> "mine"; else -> "shelf" } },
        restore = { when (it) { "book" -> RootRouteV4.BOOK; "online" -> RootRouteV4.ONLINE; "create" -> RootRouteV4.CREATE_HUB; "mine" -> RootRouteV4.MINE; else -> RootRouteV4.SHELF } },
    )) { mutableStateOf(RootRouteV4.SHELF) }
    LaunchedEffect(route, libraryState.libraryLoaded, libraryState.openedBook) {
        // After process death the ViewModel may no longer hold the book. Return to a usable
        // shelf; tapping the book reloads its durable sentence anchor from ReaderProgressStore.
        if (route == RootRouteV4.BOOK && libraryState.libraryLoaded && libraryState.openedBook == null) {
            route = RootRouteV4.SHELF
        }
    }
    com.xiguli.langhuan.ui.design.PaperReaderSystemBarsV44(
        lightBackground = route in ROOT_TABS_V94 || !androidx.compose.foundation.isSystemInDarkTheme(),
        readerActive = route == RootRouteV4.BOOK,
    )
    var returnAfterAiSetup by remember { mutableStateOf(RootRouteV4.SHELF) }
    var returnAfterSkills by remember { mutableStateOf(RootRouteV4.SHELF) }
    var returnAfterEditor by remember { mutableStateOf(RootRouteV4.BOOK) }
    // V94: pages opened from a tab return to that tab instead of always falling back to 书架.
    var returnAfterRunCenter by remember { mutableStateOf<RootRouteV4?>(null) }
    var returnAfterWriting by remember { mutableStateOf<RootRouteV4?>(null) }
    var onlineRootLevel by remember { mutableStateOf(true) }
    var onlineManageRequest by remember { mutableIntStateOf(0) }
    var writingStoryId by remember { mutableStateOf<String?>(null) }
    var editorStoryId by remember { mutableStateOf<String?>(null) }
    var editorChapter by remember { mutableStateOf<Int?>(null) }
    var openBookOnInfo by remember { mutableStateOf(false) }
    var tavernStoryId by remember { mutableStateOf<String?>(null) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var pendingBookRoute by remember { mutableStateOf<RootRouteV4?>(null) }
    var pendingBookFreshReload by remember { mutableStateOf(false) }

    val localBookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) localImportVm.importUri(uri)
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
        val epub = com.xiguli.langhuan.ui.epub.EpubReaderEntry.isEpub(appContext, id)
        val editionPrefs = com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90.prefs(appContext)
        if (epub && com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90.opensAsText(editionPrefs, id)) {
            // The reader last chose 文字版 for this book: reopen it there instead of 原版.
            requestBook(id, RootRouteV4.BOOK, showInfo = false)
        } else if (epub) {
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

    /** Text reader → 原版: remember the choice, leave the text reader and open the EPUB renderer. */
    fun openOriginalEdition(id: String) {
        com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90.save(
            com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90.prefs(appContext),
            id,
            com.xiguli.langhuan.ui.epub.ReaderEditionV90.ORIGINAL,
        )
        libraryVm.closeBook()
        editorChapter = null
        route = RootRouteV4.SHELF
        openBook(id)
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

    /** V94 创作 tab → 继续写: the writing workspace for one of the user's own books. */
    fun openWriting(id: String) {
        writingStoryId = id
        returnAfterWriting = RootRouteV4.CREATE_HUB
        libraryVm.openBook(id)
        studioVm.selectStory(id)
        route = RootRouteV4.WRITING
    }

    fun closeWriting(id: String) {
        val back = returnAfterWriting
        returnAfterWriting = null
        if (back != null) {
            libraryVm.closeBook()
            route = back
        } else openBook(id)
    }

    fun leaveRunCenter() {
        val back = returnAfterRunCenter
        returnAfterRunCenter = null
        route = back ?: if (libraryState.openedBook != null) RootRouteV4.BOOK else RootRouteV4.SHELF
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
            RootRouteV4.WRITING -> closeWriting(writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id)
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
            RootRouteV4.RUN_CENTER -> leaveRunCenter()
            RootRouteV4.AI_SETUP -> route = when {
                !studioState.provider.ready && returnAfterAiSetup in setOf(
                    RootRouteV4.CREATION,
                    RootRouteV4.CREATION_RESEARCH,
                    RootRouteV4.TAVERN,
                ) -> RootRouteV4.SHELF
                else -> returnAfterAiSetup
            }
            RootRouteV4.SKILLS -> route = returnAfterSkills
            // Tabs: back returns to 书架 first; from 书架 the system closes the app.
            RootRouteV4.ONLINE, RootRouteV4.CREATE_HUB, RootRouteV4.MINE -> route = RootRouteV4.SHELF
        }
    }

    val runActiveV94 = rememberRunCenterActiveV92()
    val bottomBarVisible = route in ROOT_TABS_V94 && (route != RootRouteV4.ONLINE || onlineRootLevel)

    var tabReselectV95 by remember { mutableStateOf(com.xiguli.langhuan.ui.design.TabReselectV95()) }

    val routeStates = rememberSaveableStateHolder()
    if (externalBooks != null) ExternalBookImportHostV1(externalBooks, localImportVm)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(com.xiguli.langhuan.ui.design.LocalTabReselectV95 provides tabReselectV95) {
        AnimatedContent(
            targetState = route,
            modifier = Modifier.fillMaxWidth().weight(1f),
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
                        onOnline = { route = RootRouteV4.ONLINE },
                        onRenameBook = libraryVm::renameBook,
                        onCancelImport = localImportVm::cancelImport,
                        insideTabs = true,
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
                            onOpenOriginalEdition = remember(libraryState.openedBook?.id) {
                                libraryState.openedBook?.id
                                    ?.takeIf { com.xiguli.langhuan.ui.epub.EpubReaderEntry.isEpub(appContext, it) }
                            }?.let { id -> { openOriginalEdition(id) } },
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
                        onClose = { closeWriting(id) },
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

                RootRouteV4.RUN_CENTER -> {
                    val runCenterVm: RunCenterViewModel = viewModel()
                    val runCenterState by runCenterVm.state.collectAsStateWithLifecycle()
                    val appContext2 = LocalContext.current.applicationContext as LanghuanApplication
                    val runtimeState by appContext2.chapterRunRuntime.state.collectAsStateWithLifecycle()
                    val runCenterLifecycle = LocalLifecycleOwner.current.lifecycle
                    // Load saved checkpoints on entry, then keep them fresh only while visible.
                    // Silent refreshes re-read checkpoints but reuse cached book/chapter titles.
                    LaunchedEffect(runCenterVm, runCenterLifecycle) {
                        runCenterVm.refresh()
                        runCenterLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            while (true) {
                                kotlinx.coroutines.delay(RUN_CENTER_REFRESH_INTERVAL_MS_V85)
                                runCenterVm.refresh(silent = true)
                            }
                        }
                    }
                    // A run starting, finishing or failing rewrites its checkpoint immediately.
                    LaunchedEffect(runCenterVm, runtimeState.active, runtimeState.chapterNumber) {
                        runCenterVm.refresh(silent = true)
                    }
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
                        onBack = ::leaveRunCenter,
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

                RootRouteV4.ONLINE -> {
                    OnlineHostV94(
                        onlineVm = onlineVm,
                        onlineState = onlineState,
                        embedded = true,
                        onBack = { route = RootRouteV4.SHELF },
                        onOpenAiSetup = { openAiSetup(RootRouteV4.ONLINE) },
                        onCreatedStory = { id ->
                            route = RootRouteV4.SHELF
                            pendingOnlineOpen = id
                        },
                        onToast = { text, error -> toast = text to error },
                        onRootLevelChange = { onlineRootLevel = it },
                        openManageRequest = onlineManageRequest,
                    )
                }

                RootRouteV4.CREATE_HUB -> {
                    CreateTabV94(
                        books = libraryState.stories,
                        libraryLoaded = libraryState.libraryLoaded,
                        aiReady = studioState.provider.ready,
                        runActive = runActiveV94,
                        onNewAiBook = {
                            if (studioState.provider.ready) route = RootRouteV4.CREATION
                            else openAiSetup(RootRouteV4.CREATION)
                        },
                        onNewBlankBook = libraryVm::createBlankStory,
                        onContinueWriting = ::openWriting,
                        onRunCenter = {
                            returnAfterRunCenter = RootRouteV4.CREATE_HUB
                            route = RootRouteV4.RUN_CENTER
                        },
                        onSkills = { openSkills(RootRouteV4.CREATE_HUB) },
                    )
                }

                RootRouteV4.MINE -> {
                    val shelfProgressPrefs = remember(appContext) {
                        appContext.getSharedPreferences(ShelfReadingProgressStoreV92.PREFS, android.content.Context.MODE_PRIVATE)
                    }
                    val finished = remember(libraryState.stories, libraryState.openedBook?.id) {
                        libraryState.stories.count { ShelfReadingProgressStoreV92.load(shelfProgressPrefs, it.id).finished }
                    }
                    MineTabV94(
                        bookCount = if (libraryState.libraryLoaded) libraryState.stories.size else null,
                        finishedCount = finished,
                        sourceCount = onlineState.sources.size,
                        enabledSourceCount = onlineState.sources.count { it.enabled },
                        aiLabel = studioState.provider.activeProvider?.let { provider ->
                            listOf(provider.name, provider.model).filter(String::isNotBlank).joinToString(" · ")
                        },
                        onSources = {
                            onlineManageRequest++
                            route = RootRouteV4.ONLINE
                        },
                        onImportLocal = { localBookLauncher.launch(arrayOf("*/*")) },
                        onAiSetup = { openAiSetup(RootRouteV4.MINE) },
                    )
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
        // V94 bottom navigation: shown on the four tab roots, hidden in the reader and every
        // full-screen page (editor, writing, settings, source editor/import, book detail...).
        if (bottomBarVisible) {
            com.xiguli.langhuan.ui.design.LanghuanBottomBarV94(
                tabs = rootBottomTabsV94(runActiveV94),
                selectedKey = route.name,
                onSelect = { key ->
                    val target = RootRouteV4.valueOf(key)
                    route = target
                },
                // V95: tapping the current tab again scrolls that tab back to its top.
                onReselect = { key -> tabReselectV95 = com.xiguli.langhuan.ui.design.TabReselectV95(key, tabReselectV95.token + 1) },
            )
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

/** V94 tab bar entries. The run badge sits on 创作, where 运行中心 now lives. */
internal fun rootBottomTabsV94(runActive: Boolean): List<com.xiguli.langhuan.ui.design.BottomTabV94> {
    val icons = com.xiguli.langhuan.ui.design.LanghuanTabIconsV95
    return listOf(
        com.xiguli.langhuan.ui.design.BottomTabV94(RootRouteV4.SHELF.name, "书架", icons.ShelfOutline, icons.ShelfFilled),
        com.xiguli.langhuan.ui.design.BottomTabV94(RootRouteV4.ONLINE.name, "书城", icons.StoreOutline, icons.StoreFilled),
        com.xiguli.langhuan.ui.design.BottomTabV94(
            RootRouteV4.CREATE_HUB.name, "创作",
            icons.CreateOutline, icons.CreateFilled,
            badge = runActive, badgeDescription = HOME_RUN_ACTIVE_LABEL_V92,
        ),
        com.xiguli.langhuan.ui.design.BottomTabV94(RootRouteV4.MINE.name, "我的", icons.MineOutline, icons.MineFilled),
    )
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
        // Switching bottom-bar tabs is a quick cross-fade, never a page slide.
        from in ROOT_TABS_V94 && to in ROOT_TABS_V94 ->
            fadeIn(tween(160)) togetherWith fadeOut(tween(120))
        to == RootRouteV4.BOOK && from == RootRouteV4.SHELF ->
            (fadeIn(tween(220)) + scaleIn(enterEase, initialScale = .92f)) togetherWith
                (fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 1.03f))
        from == RootRouteV4.BOOK && to == RootRouteV4.SHELF ->
            (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 1.03f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(280), targetScale = .92f))
        to in ROOT_TABS_V94 ->
            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
        else ->
            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(200)))
    }.apply { targetContentZIndex = if (to in ROOT_TABS_V94) 0f else 1f }
}
