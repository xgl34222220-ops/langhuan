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

private enum class RootRouteV3 {
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

/**
 * Root navigation now treats opening a book as one transaction:
 * request -> load book/chapters -> resolve saved chapter -> switch route once.
 * It never switches to BOOK while openedBook is still null, so the old first-tap flash/bounce
 * cannot occur.
 */
@Composable
fun LanghuanRootV3(studioVm: StudioViewModel) {
    val studioState by studioVm.state.collectAsStateWithLifecycle()
    val libraryVm: LibraryExperienceViewModel = viewModel()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val localImportVm: LocalBookImportViewModelV1 = viewModel()
    val localImportState by localImportVm.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val projectConversationStore = remember(appContext) { ProjectConversationStore(appContext) }

    var route by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterAiSetup by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterSkills by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterEditor by remember { mutableStateOf(RootRouteV3.BOOK) }
    var writingStoryId by remember { mutableStateOf<String?>(null) }
    var editorStoryId by remember { mutableStateOf<String?>(null) }
    var editorChapter by remember { mutableStateOf<Int?>(null) }
    var coverStoryId by remember { mutableStateOf<String?>(null) }
    var openBookOnInfo by remember { mutableStateOf(false) }
    var tavernStoryId by remember { mutableStateOf<String?>(null) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var pendingBookRoute by remember { mutableStateOf<RootRouteV3?>(null) }

    val localBookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) localImportVm.importUri(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) studioVm.exportProjectBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) studioVm.importDocument(uri)
    }

    fun requestBook(id: String, target: RootRouteV3, showInfo: Boolean = false) {
        if (pendingBookId != null) return
        openBookOnInfo = showInfo
        pendingBookId = id
        pendingBookRoute = target
        if (target == RootRouteV3.TAVERN) tavernStoryId = id
        studioVm.selectStory(id)
        libraryVm.openBook(id)
    }

    fun openBook(id: String) = requestBook(id, RootRouteV3.BOOK, showInfo = false)

    fun openTavern(id: String) {
        tavernStoryId = id
        studioVm.selectStory(id)
        if (studioState.provider.ready) {
            requestBook(id, RootRouteV3.TAVERN, showInfo = false)
        } else {
            libraryVm.openBook(id)
            returnAfterAiSetup = RootRouteV3.TAVERN
            route = RootRouteV3.AI_SETUP
        }
    }

    fun backToBook() {
        val id = libraryState.openedBook?.id ?: writingStoryId
        if (id != null) openBook(id) else route = RootRouteV3.SHELF
    }

    fun openAiSetup(from: RootRouteV3) {
        returnAfterAiSetup = from
        route = RootRouteV3.AI_SETUP
    }

    fun openSkills(from: RootRouteV3) {
        returnAfterSkills = from
        route = RootRouteV3.SKILLS
    }

    fun enterCreatedProject(id: String, creationVm: NewBookConversationViewModel) {
        val creationMessages = creationVm.state.value.messages.map { it.role to it.text }
        projectConversationStore.handoffFromCreation(id, creationMessages)
        creationVm.reset()
        writingStoryId = id
        libraryVm.openBook(id)
        studioVm.selectStory(id)
        route = RootRouteV3.WRITING
    }

    // Book loading finishes while the shelf stays on screen. Only now do we resolve the saved
    // chapter and make one route transition. This replaces the old BOOK -> null -> SHELF bounce.
    LaunchedEffect(pendingBookId, pendingBookRoute, libraryState.openedBook?.id, libraryState.chapters) {
        val id = pendingBookId ?: return@LaunchedEffect
        val targetRoute = pendingBookRoute ?: return@LaunchedEffect
        val book = libraryState.openedBook?.takeIf { it.id == id } ?: return@LaunchedEffect
        val chapters = libraryState.chapters.sortedBy { it.chapterNumber }
        if (chapters.isNotEmpty()) {
            val saved = ReaderProgressStoreV11.load(appContext, id, book.currentChapter.coerceAtLeast(1))
            val target = chapters.firstOrNull { it.chapterNumber == saved.chapterNumber }
                ?: chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                ?: chapters.first()
            libraryVm.openReader(target.chapterNumber)
        }
        pendingBookId = null
        pendingBookRoute = null
        route = targetRoute
    }

    LaunchedEffect(libraryState.error, pendingBookId) {
        if (pendingBookId != null && libraryState.error != null) {
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
                RootRouteV3.SHELF -> {
                    ShelfCoreExperience(
                        state = libraryState,
                        importState = localImportState,
                        openingBookId = pendingBookId,
                        onOpenBook = ::openBook,
                        onOpenTavern = ::openTavern,
                        onImportLocal = { localBookLauncher.launch(arrayOf("*/*")) },
                        onDeleteBook = libraryVm::deleteBook,
                        onCreate = {
                            if (studioState.provider.ready) route = RootRouteV3.CREATION
                            else openAiSetup(RootRouteV3.CREATION)
                        },
                        onAiSetup = { openAiSetup(RootRouteV3.SHELF) },
                        onRunCenter = { route = RootRouteV3.RUN_CENTER },
                        onSkills = { openSkills(RootRouteV3.SHELF) },
                    )
                }

                RootRouteV3.BOOK -> {
                    if (libraryState.openedBook != null) {
                        ReaderCoreExperience(
                            viewModel = libraryVm,
                            studioState = studioState,
                            onBackToShelf = {
                                libraryVm.closeBook()
                                route = RootRouteV3.SHELF
                            },
                            onEnterWriting = { id ->
                                writingStoryId = id
                                studioVm.selectStory(id)
                                route = RootRouteV3.WRITING
                            },
                            onOpenEditor = { id, chapter ->
                                editorStoryId = id
                                editorChapter = chapter
                                returnAfterEditor = RootRouteV3.BOOK
                                route = RootRouteV3.EDITOR
                            },
                            onOpenAiSetup = { openAiSetup(RootRouteV3.BOOK) },
                            startOnInfo = openBookOnInfo,
                        )
                    }
                }

                RootRouteV3.CREATION -> {
                    val creationVm: NewBookConversationViewModel = viewModel()
                    CreationChatV4(
                        viewModel = creationVm,
                        onClose = { route = RootRouteV3.SHELF },
                        onConfigureAi = { openAiSetup(RootRouteV3.CREATION) },
                        onAdvancedResearch = { route = RootRouteV3.CREATION_RESEARCH },
                        onCreated = { id -> enterCreatedProject(id, creationVm) },
                    )
                }

                RootRouteV3.CREATION_RESEARCH -> {
                    val creationVm: NewBookConversationViewModel = viewModel()
                    ResearchNewBookConversationPage(
                        viewModel = creationVm,
                        onClose = { route = RootRouteV3.CREATION },
                        onConfigureAi = { openAiSetup(RootRouteV3.CREATION_RESEARCH) },
                        onSwitchModel = { openAiSetup(RootRouteV3.CREATION_RESEARCH) },
                        onCreated = { id -> enterCreatedProject(id, creationVm) },
                    )
                }

                RootRouteV3.TAVERN -> {
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
                                onAiSetup = { openAiSetup(RootRouteV3.TAVERN) },
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
                                        route = RootRouteV3.SHELF
                                    }
                                ) { Icon(Icons.Rounded.ArrowBack, "返回书架") }
                            }
                        }
                    }
                }

                RootRouteV3.WRITING -> {
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    WritingFlowPage(
                        novelId = id,
                        viewModel = writingVm,
                        onClose = { openBook(id) },
                        onEditChapter = { storyId, chapter ->
                            editorStoryId = storyId
                            editorChapter = chapter
                            returnAfterEditor = RootRouteV3.WRITING
                            route = RootRouteV3.EDITOR
                        },
                    )
                }

                RootRouteV3.EDITOR -> {
                    val editorVm: ChapterEditorViewModel = viewModel()
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = editorStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    ChapterEditorExperience(
                        novelId = id,
                        initialChapter = editorChapter,
                        viewModel = editorVm,
                        onClose = {
                            if (returnAfterEditor == RootRouteV3.WRITING) {
                                writingVm.invalidateAfterExternalEdit(id)
                                route = RootRouteV3.WRITING
                            } else {
                                openBook(id)
                            }
                        },
                    )
                }

                RootRouteV3.AGENT -> {
                    AgentPage(
                        state = studioState,
                        vm = studioVm,
                        onProjectBackup = { backupLauncher.launch("${studioState.snapshot.novel.title}.lhproj") },
                        onProjectRestore = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        onClose = ::backToBook,
                    )
                }

                RootRouteV3.INTELLIGENCE -> {
                    StoryIntelligencePage(state = studioState, onClose = ::backToBook)
                }

                RootRouteV3.RUN_CENTER -> {
                    val runCenterVm: RunCenterViewModel = viewModel()
                    val runCenterState by runCenterVm.state.collectAsStateWithLifecycle()
                    LaunchedEffect(runCenterState.openRequest?.token) {
                        runCenterState.openRequest?.let { request ->
                            runCenterVm.consumeOpenRequest()
                            writingStoryId = request.novelId
                            libraryVm.openBook(request.novelId)
                            studioVm.selectStory(request.novelId)
                            route = RootRouteV3.WRITING
                        }
                    }
                    RunCenterPage(
                        viewModel = runCenterVm,
                        onClose = { route = if (libraryState.openedBook != null) RootRouteV3.BOOK else RootRouteV3.SHELF },
                    )
                }

                RootRouteV3.AI_SETUP -> {
                    AiProviderSetupPage(
                        state = studioState,
                        vm = studioVm,
                        onBack = {
                            route = when {
                                !studioState.provider.ready && returnAfterAiSetup in setOf(
                                    RootRouteV3.CREATION,
                                    RootRouteV3.CREATION_RESEARCH,
                                    RootRouteV3.TAVERN,
                                ) -> RootRouteV3.SHELF
                                else -> returnAfterAiSetup
                            }
                        },
                        onDone = { route = returnAfterAiSetup },
                    )
                }

                RootRouteV3.COVER_STUDIO -> {
                    @Suppress("UNUSED_VARIABLE")
                    val coverGuard: CoverPersistenceGuardViewModel = viewModel()
                    val id = coverStoryId ?: libraryState.openedBook?.id
                    if (id != null) {
                        CoverStudioV3(
                            bookId = id,
                            libraryViewModel = libraryVm,
                            onClose = { route = RootRouteV3.BOOK },
                        )
                    }
                }

                RootRouteV3.SKILLS -> {
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
