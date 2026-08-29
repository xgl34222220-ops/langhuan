package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class RootRouteV3 {
    SHELF,
    BOOK,
    CREATION,
    WRITING,
    AGENT,
    INTELLIGENCE,
    RUN_CENTER,
    AI_SETUP,
    COVER_STUDIO,
}

/**
 * V3 根交互：用单一路由状态替换旧版一堆 showXxx Boolean 互相排斥的覆盖层。
 *
 * 页面关系固定为：
 * 书架 -> 作品工作台 -> 阅读 / 写作 / AI助手 / 长篇监控 / 任务 / 封面工作室。
 * AI 设置是全局页；关闭子页面始终回到调用它的上一级，不再 finish/start Activity。
 */
@Composable
fun LanghuanRootV3(studioVm: StudioViewModel) {
    val studioState by studioVm.state.collectAsStateWithLifecycle()
    val libraryVm: LibraryExperienceViewModel = viewModel()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val creationVm: NewBookConversationViewModel = viewModel()
    val writingVm: WritingFlowViewModel = viewModel()
    val runCenterVm: RunCenterViewModel = viewModel()
    val runCenterState by runCenterVm.state.collectAsStateWithLifecycle()
    val coverGuard: CoverPersistenceGuardViewModel = viewModel()

    var route by remember { mutableStateOf(RootRouteV3.SHELF) }
    var returnAfterAiSetup by remember { mutableStateOf(RootRouteV3.SHELF) }
    var writingStoryId by remember { mutableStateOf<String?>(null) }
    var coverStoryId by remember { mutableStateOf<String?>(null) }

    // 保持守卫与 Activity 生命周期一致，让旧 CoverComposer 写完同一路径后能立即版本化。
    @Suppress("UNUSED_VARIABLE")
    val keepCoverGuardAlive = coverGuard

    val referenceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) studioVm.enqueueReferenceDistillation(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) studioVm.exportProjectBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) studioVm.importDocument(uri)
    }

    LaunchedEffect(libraryState.openedBook?.id) {
        libraryState.openedBook?.id?.let { id ->
            studioVm.selectStory(id)
        }
    }

    // 任务中心请求打开某一章时直接切到写作页，不再通过重启 Activity 重新挂载 UI。
    LaunchedEffect(runCenterState.openRequest?.token) {
        runCenterState.openRequest?.let { request ->
            runCenterVm.consumeOpenRequest()
            writingStoryId = request.novelId
            libraryVm.openBook(request.novelId)
            studioVm.selectStory(request.novelId)
            route = RootRouteV3.WRITING
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (route) {
                RootRouteV3.SHELF -> {
                    ShelfV3(
                        state = libraryState,
                        aiReady = studioState.provider.ready,
                        aiLabel = studioState.provider.activeProviderLabel,
                        onOpenBook = ::openBook,
                        onCreate = {
                            if (studioState.provider.ready) {
                                route = RootRouteV3.CREATION
                            } else {
                                openAiSetup(RootRouteV3.CREATION)
                            }
                        },
                        onReference = {
                            referenceLauncher.launch(
                                arrayOf("text/plain", "text/markdown", "application/epub+zip", "application/octet-stream")
                            )
                        },
                        onAiSetup = { openAiSetup(RootRouteV3.SHELF) },
                        onRunCenter = { route = RootRouteV3.RUN_CENTER },
                    )
                }

                RootRouteV3.BOOK -> {
                    if (libraryState.openedBook == null) {
                        route = RootRouteV3.SHELF
                    } else {
                        ReaderFirstLibraryV3(
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
                            onOpenAgent = { route = RootRouteV3.AGENT },
                            onOpenIntelligence = { route = RootRouteV3.INTELLIGENCE },
                            onOpenRunCenter = { route = RootRouteV3.RUN_CENTER },
                            onOpenAiSetup = { openAiSetup(RootRouteV3.BOOK) },
                            onOpenCoverStudio = { id ->
                                coverStoryId = id
                                route = RootRouteV3.COVER_STUDIO
                            },
                        )
                    }
                }

                RootRouteV3.CREATION -> {
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
                }

                RootRouteV3.WRITING -> {
                    val id = writingStoryId ?: libraryState.openedBook?.id ?: studioState.snapshot.novel.id
                    WritingFlowPage(
                        novelId = id,
                        viewModel = writingVm,
                        onClose = {
                            libraryVm.openBook(id)
                            route = RootRouteV3.BOOK
                        },
                    )
                }

                RootRouteV3.AGENT -> {
                    AgentPage(
                        state = studioState,
                        vm = studioVm,
                        onProjectBackup = {
                            backupLauncher.launch("${studioState.snapshot.novel.title}.lhproj")
                        },
                        onProjectRestore = {
                            restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                        },
                        onClose = ::backToBook,
                    )
                }

                RootRouteV3.INTELLIGENCE -> {
                    StoryIntelligencePage(
                        state = studioState,
                        onClose = ::backToBook,
                    )
                }

                RootRouteV3.RUN_CENTER -> {
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
                            } else {
                                returnAfterAiSetup
                            }
                        },
                        onDone = { route = returnAfterAiSetup },
                    )
                }

                RootRouteV3.COVER_STUDIO -> {
                    val id = coverStoryId ?: libraryState.openedBook?.id
                    if (id == null) {
                        route = RootRouteV3.SHELF
                    } else {
                        CoverStudioV3(
                            bookId = id,
                            libraryViewModel = libraryVm,
                            onClose = { route = RootRouteV3.BOOK },
                        )
                    }
                }
            }
        }
    }
}
