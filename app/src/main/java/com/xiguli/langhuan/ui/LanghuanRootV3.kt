package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class RootRouteV3 {
    SHELF,
    BOOK,
    CREATION,
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
 * Reader-first 根交互：书架和阅读是第一层，AI 创作与故事能力作为自然延伸。
 *
 * Startup rule: only shelf-critical ViewModels are created here. Feature ViewModels
 * are created only when their route is actually entered, so an unrelated feature
 * cannot crash or slow the launcher path.
 */
@Composable
fun LanghuanRootV3(studioVm: StudioViewModel) {
    val studioState by studioVm.state.collectAsStateWithLifecycle()
    val libraryVm: LibraryExperienceViewModel = viewModel()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val localImportVm: LocalBookImportViewModelV1 = viewModel()
    val localImportState by localImportVm.state.collectAsStateWithLifecycle()

    var route by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterAiSetup by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterSkills by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterEditor by remember { mutableStateOf(RootRouteV3.BOOK) }
    var writingStoryId by remember { mutableStateOf<String?>(null) }
    var editorStoryId by remember { mutableStateOf<String?>(null) }
    var editorChapter by remember { mutableStateOf<Int?>(null) }
    var coverStoryId by remember { mutableStateOf<String?>(null) }

    val localBookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) localImportVm.importUri(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) studioVm.exportProjectBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) studioVm.importDocument(uri)
    }

    LaunchedEffect(libraryState.openedBook?.id) {
        libraryState.openedBook?.id?.let { id -> studioVm.selectStory(id) }
    }

    LaunchedEffect(localImportState.importedBookId, libraryState.stories) {
        val id = localImportState.importedBookId ?: return@LaunchedEffect
        if (libraryState.stories.any { it.id == id }) {
            libraryVm.openBook(id)
            studioVm.selectStory(id)
            localImportVm.consumeImportedBook()
            route = RootRouteV3.BOOK
        }
    }

    LaunchedEffect(route, libraryState.openedBook?.id, coverStoryId) {
        when {
            route == RootRouteV3.BOOK && libraryState.openedBook == null -> route = RootRouteV3.SHELF
            route == RootRouteV3.COVER_STUDIO && coverStoryId == null && libraryState.openedBook == null -> route = RootRouteV3.SHELF
        }
    }

    fun openBook(id: String) {
        libraryVm.openBook(id)
        studioVm.selectStory(id)
        route = RootRouteV3.BOOK
    }

    fun backToBook() {
        val id = libraryState.openedBook?.id ?: writingStoryId
        if (id != null) {
            libraryVm.openBook(id)
            route = RootRouteV3.BOOK
        } else {
            route = RootRouteV3.SHELF
        }
    }

    fun openAiSetup(from: RootRouteV3) {
        returnAfterAiSetup = from
        route = RootRouteV3.AI_SETUP
    }

    fun openSkills(from: RootRouteV3) {
        returnAfterSkills = from
        route = RootRouteV3.SKILLS
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (route) {
                RootRouteV3.SHELF -> {
                    ReaderShelfV7(
                        state = libraryState,
                        importState = localImportState,
                        onOpenBook = ::openBook,
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
                        ReaderFirstBookV7(
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
                        )
                    }
                }

                RootRouteV3.CREATION -> {
                    val creationVm: NewBookConversationViewModel = viewModel()
                    ResearchNewBookConversationPage(
                        viewModel = creationVm,
                        onClose = { route = RootRouteV3.SHELF },
                        onConfigureAi = { openAiSetup(RootRouteV3.CREATION) },
                        onSwitchModel = { openAiSetup(RootRouteV3.CREATION) },
                        onCreated = { id ->
                            creationVm.reset()
                            libraryVm.openBook(id)
                            studioVm.selectStory(id)
                            route = RootRouteV3.BOOK
                        },
                    )
                    CreationQuickActionsOverlay(creationVm)
                }

                RootRouteV3.WRITING -> {
                    val writingVm: WritingFlowViewModel = viewModel()
                    val id = writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    WritingFlowPage(
                        novelId = id,
                        viewModel = writingVm,
                        onClose = {
                            libraryVm.openBook(id)
                            route = RootRouteV3.BOOK
                        },
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
                    ChapterEditorPage(
                        novelId = id,
                        initialChapter = editorChapter,
                        viewModel = editorVm,
                        onClose = {
                            if (returnAfterEditor == RootRouteV3.WRITING) {
                                writingVm.invalidateAfterExternalEdit(id)
                            }
                            libraryVm.openBook(id)
                            route = returnAfterEditor
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
                        onClose = {
                            route = if (libraryState.openedBook != null) RootRouteV3.BOOK else RootRouteV3.SHELF
                        },
                    )
                }

                RootRouteV3.AI_SETUP -> {
                    AiProviderSetupPage(
                        state = studioState,
                        vm = studioVm,
                        onBack = {
                            route = if (returnAfterAiSetup == RootRouteV3.CREATION && !studioState.provider.ready) {
                                RootRouteV3.SHELF
                            } else returnAfterAiSetup
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
