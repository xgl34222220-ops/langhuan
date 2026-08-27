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
import androidx.compose.material.icons.rounded.Psychology
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

@Composable
fun LanghuanRoot(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryViewModel: LibraryExperienceViewModel = viewModel()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val creationViewModel: NewBookConversationViewModel = viewModel()
    val context = LocalContext.current
    var showAgent by remember { mutableStateOf(false) }
    var showShelf by remember { mutableStateOf(true) }
    var showCreation by remember { mutableStateOf(false) }

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
        if (libraryState.stories.isEmpty()) {
            showShelf = true
            showCreation = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        LanghuanApp(viewModel)

        if (!showShelf && !showAgent && !showCreation) {
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
                ExtendedFloatingActionButton(
                    onClick = { showAgent = true },
                    icon = { Icon(Icons.Rounded.Psychology, null) },
                    text = { Text("Agent") },
                )
            }
        }

        if (showShelf && !showAgent && !showCreation) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (libraryState.openedBook == null && libraryState.readingChapter == null) {
                    AiFirstShelf(
                        state = libraryState,
                        onOpenBook = libraryViewModel::openBook,
                        onStartCreation = {
                            creationViewModel.reset()
                            showCreation = true
                        },
                        onCloseShelf = { if (libraryState.stories.isNotEmpty()) showShelf = false },
                    )
                } else {
                    ReaderFirstLibrary(
                        viewModel = libraryViewModel,
                        onEnterWorkspace = { id ->
                            viewModel.selectStory(id)
                            showShelf = false
                        },
                        onCloseShelf = { if (libraryState.stories.isNotEmpty()) showShelf = false },
                    )
                }
            }
        }

        if (showCreation && !showAgent) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                NewBookConversationPage(
                    viewModel = creationViewModel,
                    onClose = { showCreation = false },
                    onCreated = { id ->
                        showCreation = false
                        showShelf = true
                        libraryViewModel.openBook(id)
                    },
                )
            }
        }

        if (showAgent) {
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
}
