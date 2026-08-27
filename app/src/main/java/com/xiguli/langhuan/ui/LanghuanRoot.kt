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
    val context = LocalContext.current
    var showAgent by remember { mutableStateOf(false) }
    var showShelf by remember { mutableStateOf(true) }

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
            (context as? Activity)?.recreate()
        }
    }

    if (libraryState.stories.isEmpty()) showShelf = true

    Box(Modifier.fillMaxSize()) {
        LanghuanApp(viewModel)

        if (!showShelf && !showAgent) {
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

        if (showShelf && !showAgent) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
