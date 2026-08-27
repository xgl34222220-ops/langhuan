package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.ExportFormat

@Composable
fun LanghuanRoot(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAgent by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportDocument(uri, ExportFormat.PROJECT)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importDocument(uri)
    }

    Box(Modifier.fillMaxSize()) {
        LanghuanApp(viewModel)

        if (!showAgent) {
            ExtendedFloatingActionButton(
                onClick = { showAgent = true },
                icon = { Icon(Icons.Rounded.Psychology, null) },
                text = { Text("Agent") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 88.dp),
            )
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
