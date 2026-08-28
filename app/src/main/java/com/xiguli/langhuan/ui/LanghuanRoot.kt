package com.xiguli.langhuan.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val ROOT_PREFS = "langhuan_root_state"
private const val RESUME_WORKSPACE_ONCE = "resume_workspace_once"

@Composable
fun LanghuanRoot(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryViewModel: LibraryExperienceViewModel = viewModel()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val creationViewModel: NewBookConversationViewModel = viewModel()
    val writingViewModel: WritingFlowViewModel = viewModel()
    val editorViewModel: ChapterEditorViewModel = viewModel()
    val quickModelViewModel: ProviderQuickSwitchViewModel = viewModel()
    val coverGuardViewModel: CoverPersistenceGuardViewModel = viewModel()
    val context = LocalContext.current
    val rootPrefs = remember { context.getSharedPreferences(ROOT_PREFS, 0) }
    val resumeWorkspace = remember {
        rootPrefs.getBoolean(RESUME_WORKSPACE_ONCE, false).also { shouldResume ->
            if (shouldResume) rootPrefs.edit().remove(RESUME_WORKSPACE_ONCE).apply()
        }
    }
    var showAgent by remember { mutableStateOf(false) }
    var showShelf by remember { mutableStateOf(!resumeWorkspace) }
    var showCreation by remember { mutableStateOf(false) }
    var showWritingFlow by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showIntelligence by remember { mutableStateOf(false) }
    var showModelSwitch by remember { mutableStateOf(false) }
    var showAiSetup by remember { mutableStateOf(false) }
    var pendingCreationAfterAiSetup by remember { mutableStateOf(false) }
    var writingStoryId by remember { mutableStateOf<String?>(null) }
    var editorStoryId by remember { mutableStateOf<String?>(null) }
    var editorChapter by remember { mutableStateOf<Int?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val keepCoverGuardAlive = coverGuardViewModel

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportProjectBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importDocument(uri)
    }
    val referenceDistillLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.enqueueReferenceDistillation(uri)
    }

    LaunchedEffect(libraryState.workspaceStoryId) {
        libraryState.workspaceStoryId?.let { id ->
            viewModel.selectStory(id)
            libraryViewModel.consumeWorkspaceStory()
        }
    }

    LaunchedEffect(libraryState.requestActivityReload) {
        if (libraryState.requestActivityReload) {
            libraryViewModel.consumeActivityReload()
            val activity = context as? Activity
            if (activity != null) {
                val intent = activity.intent
                activity.finish()
                activity.startActivity(intent)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        }
    }

    LaunchedEffect(libraryState.stories.isEmpty()) {
        if (libraryState.stories.isEmpty()) showShelf = true
    }

    fun reloadWorkspaceAfterOverlay() {
        rootPrefs.edit().putBoolean(RESUME_WORKSPACE_ONCE, true).apply()
        val activity = context as? Activity
        if (activity != null) {
            val intent = activity.intent
            activity.finish()
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        } else {
            showWritingFlow = false
            showEditor = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        LanghuanApp(viewModel)

        if (!showShelf && !showAgent && !showCreation && !showWritingFlow && !showEditor && !showIntelligence && !showModelSwitch && !showAiSetup) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(onClick = { showShelf = true }) {
                    Icon(Icons.Rounded.AutoStories, "阅读书架")
                }
                SmallFloatingActionButton(onClick = { showIntelligence = true }) {
                    Icon(Icons.Rounded.Insights, "长篇监控")
                }
                ExtendedFloatingActionButton(
                    onClick = { showModelSwitch = true },
                    icon = { Icon(Icons.Rounded.Tune, null) },
                    text = { Text("模型") },
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        editorStoryId = state.snapshot.novel.id
                        editorChapter = state.snapshot.novel.currentChapter
                        showEditor = true
                    },
                    icon = { Icon(Icons.Rounded.Description, null) },
                    text = { Text("编辑正文") },
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        writingStoryId = state.snapshot.novel.id
                        showWritingFlow = true
                    },
                    icon = { Icon(Icons.Rounded.EditNote, null) },
                    text = { Text("写作流") },
                )
                ExtendedFloatingActionButton(
                    onClick = { showAgent = true },
                    icon = { Icon(Icons.Rounded.Psychology, null) },
                    text = { Text("Agent") },
                )
            }
        }

        if (showShelf && !showAgent && !showCreation && !showWritingFlow && !showEditor && !showIntelligence && !showAiSetup) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (libraryState.openedBook == null && libraryState.readingChapter == null) {
                    AiFirstShelf(
                        state = libraryState,
                        aiReady = state.provider.ready,
                        aiProviderLabel = state.provider.activeProviderLabel,
                        aiModel = state.provider.generationModel,
                        onOpenBook = libraryViewModel::openBook,
                        onStartCreation = { showCreation = true },
                        onConfigureAi = {
                            pendingCreationAfterAiSetup = !state.provider.ready
                            showAiSetup = true
                        },
                        onDistillReference = {
                            referenceDistillLauncher.launch(
                                arrayOf("text/plain", "text/markdown", "application/epub+zip", "application/octet-stream")
                            )
                        },
                        onCloseShelf = { if (libraryState.stories.isNotEmpty()) showShelf = false },
                    )
                } else {
                    ReaderFirstLibraryV2(
                        viewModel = libraryViewModel,
                        onEnterWriting = { id ->
                            viewModel.selectStory(id)
                            writingStoryId = id
                            showShelf = false
                            showWritingFlow = true
                        },
                        onCloseShelf = { if (libraryState.stories.isNotEmpty()) showShelf = false },
                    )
                }
            }
        }

        if (showAiSetup && !showAgent && !showWritingFlow && !showEditor && !showIntelligence) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AiProviderSetupPage(
                    state = state,
                    vm = viewModel,
                    onBack = {
                        pendingCreationAfterAiSetup = false
                        showAiSetup = false
                    },
                    onDone = {
                        showAiSetup = false
                        if (pendingCreationAfterAiSetup) {
                            pendingCreationAfterAiSetup = false
                            showCreation = true
                        }
                    },
                )
            }
        }

        if (showCreation && !showAgent && !showWritingFlow && !showEditor && !showIntelligence && !showAiSetup) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ResearchNewBookConversationPage(
                    viewModel = creationViewModel,
                    onClose = { showCreation = false },
                    onCreated = { id ->
                        writingStoryId = id
                        showCreation = false
                        showShelf = false
                        showWritingFlow = true
                        libraryViewModel.openBook(id)
                        creationViewModel.reset()
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        pendingCreationAfterAiSetup = false
                        showAiSetup = true
                    }
                ) {
                    Icon(Icons.Rounded.Key, "管理 Key / AI 服务")
                }
                SmallFloatingActionButton(
                    onClick = { showModelSwitch = true },
                ) {
                    Icon(Icons.Rounded.Tune, "切换 AI 服务 / 模型")
                }
            }
        }

        if (showWritingFlow && !showAgent && !showEditor && !showIntelligence && !showAiSetup) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                WritingFlowPage(
                    novelId = writingStoryId ?: state.snapshot.novel.id,
                    viewModel = writingViewModel,
                    onClose = ::reloadWorkspaceAfterOverlay,
                )
            }
        }

        if (showEditor && !showAgent && !showWritingFlow && !showIntelligence && !showAiSetup) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ChapterEditorPage(
                    novelId = editorStoryId ?: state.snapshot.novel.id,
                    initialChapter = editorChapter ?: state.snapshot.novel.currentChapter,
                    viewModel = editorViewModel,
                    onClose = ::reloadWorkspaceAfterOverlay,
                )
            }
        }

        if (showIntelligence && !showAgent && !showWritingFlow && !showEditor && !showCreation && !showAiSetup) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                StoryIntelligencePage(state = state, onClose = { showIntelligence = false })
            }
        }

        if (showAgent && !showIntelligence && !showEditor && !showAiSetup) {
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                AgentPage(
                    state = state,
                    vm = viewModel,
                    onProjectBackup = {
                        backupLauncher.launch("${state.snapshot.novel.title}.lhproj")
                    },
                    onProjectRestore = {
                        restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    },
                    onClose = { showAgent = false },
                )
            }
        }
    }

    if (showModelSwitch) {
        ProviderQuickSwitchSheet(
            viewModel = quickModelViewModel,
            preferredProviderId = state.provider.activeProviderId,
            onProviderActivated = viewModel::activateProvider,
            onDismiss = { showModelSwitch = false },
        )
    }
}
