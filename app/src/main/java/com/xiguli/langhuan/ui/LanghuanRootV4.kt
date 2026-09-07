package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.engine.ProjectConversationStore

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
}

@Composable
fun LanghuanRootV4(studioVm: StudioViewModel) {
    val studioState by studioVm.state.collectAsStateWithLifecycle()
    val libraryVm: LibraryExperienceViewModel = viewModel()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val localImportVm: LocalBookImportViewModelV1 = viewModel()
    val localImportState by localImportVm.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val projectConversationStore = remember(appContext) { ProjectConversationStore(appContext) }

    var route by remember { mutableStateOf(RootRouteV4.SHELF) }
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
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) studioVm.importDocument(uri)
    }

    fun requestBook(id: String, target: RootRouteV4, showInfo: Boolean = false) {
        if (pendingBookId != null || libraryState.isBusy) return
        openBookOnInfo = showInfo
        pendingBookFreshReload = libraryState.openedBook?.id == id && libraryState.readingChapter != null
        pendingBookId = id
        pendingBookRoute = target
        if (target == RootRouteV4.TAVERN) tavernStoryId = id
        studioVm.selectStory(id)
        libraryVm.openBook(id)
    }

    fun openBook(id: String) = requestBook(id, RootRouteV4.BOOK, showInfo = false)

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
        val chapters = libraryState.chapters.sortedBy { it.chapterNumber }
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

    LaunchedEffect(libraryState.openedBook?.id) {
        libraryState.openedBook?.id?.let { id -> studioVm.selectStory(id) }
    }

    LaunchedEffect(localImportState.importedBookId, libraryState.stories) {
        val id = localImportState.importedBookId ?: return@LaunchedEffect
        if (libraryState.stories.any { it.id == id }) {
            localImportVm.consumeImportedBook()
            openBook(id)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (route) {
                RootRouteV4.SHELF -> {
                    ShelfLibraryV5(
                        state = libraryState,
                        importState = localImportState,
                        openingBookId = pendingBookId,
                        onOpenBook = ::openBook,
                        onOpenTavern = ::openTavern,
                        onImportLocal = { localBookLauncher.launch(arrayOf("*/*")) },
                        onDeleteBook = libraryVm::deleteBook,
                        onCreate = {
                            if (studioState.provider.ready) route = RootRouteV4.CREATION
                            else openAiSetup(RootRouteV4.CREATION)
                        },
                        onAiSetup = { openAiSetup(RootRouteV4.SHELF) },
                        onRunCenter = { route = RootRouteV4.RUN_CENTER },
                        onSkills = { openSkills(RootRouteV4.SHELF) },
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
                        Box(Modifier.fillMaxSize()) {
                            StoryCleanExperience(
                                book = book,
                                libraryState = libraryState,
                                aiReady = studioState.provider.ready,
                                onAiSetup = { openAiSetup(RootRouteV4.TAVERN) },
                                onAdopted = { libraryVm.openBook(book.id) },
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                                shadowElevation = 5.dp,
                            ) {
                                IconButton(
                                    onClick = {
                                        tavernStoryId = null
                                        libraryVm.closeBook()
                                        route = RootRouteV4.SHELF
                                    },
                                ) { Icon(Icons.Rounded.ArrowBack, "返回书架") }
                            }
                        }
                    }
                }

                RootRouteV4.WRITING -> {
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    WritingFlowPage(
                        novelId = id,
                        viewModel = writingVm,
                        onClose = { openBook(id) },
                        onEditChapter = { storyId, chapter ->
                            editorStoryId = storyId
                            editorChapter = chapter
                            returnAfterEditor = RootRouteV4.WRITING
                            route = RootRouteV4.EDITOR
                        },
                    )
                }

                RootRouteV4.EDITOR -> {
                    val editorVm: ChapterEditorViewModel = viewModel()
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = editorStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    ChapterEditorExperience(
                        novelId = id,
                        initialChapter = editorChapter,
                        viewModel = editorVm,
                        onClose = {
                            if (returnAfterEditor == RootRouteV4.WRITING) {
                                writingVm.invalidateAfterExternalEdit(id)
                                route = RootRouteV4.WRITING
                            } else {
                                openBook(id)
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
                    LaunchedEffect(runCenterState.openRequest?.token) {
                        runCenterState.openRequest?.let { request ->
                            runCenterVm.consumeOpenRequest()
                            writingStoryId = request.novelId
                            libraryVm.openBook(request.novelId)
                            studioVm.selectStory(request.novelId)
                            route = RootRouteV4.WRITING
                        }
                    }
                    RunCenterPage(
                        viewModel = runCenterVm,
                        onClose = { route = if (libraryState.openedBook != null) RootRouteV4.BOOK else RootRouteV4.SHELF },
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

    localImportState.error?.let { error ->
        AlertDialog(
            onDismissRequest = localImportVm::clearFeedback,
            title = { Text("导入失败") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = localImportVm::clearFeedback) { Text("知道了") } },
        )
    }
}
