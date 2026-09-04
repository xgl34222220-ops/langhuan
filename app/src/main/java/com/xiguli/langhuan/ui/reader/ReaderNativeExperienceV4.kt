package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/** Compatibility entry kept for the current root router. */
@Composable
fun ReaderNativeExperienceV4(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) = ReaderQingmoReplicaV8(
    viewModel = viewModel,
    studioState = studioState,
    onBackToShelf = onBackToShelf,
    onEnterWriting = onEnterWriting,
    onOpenEditor = onOpenEditor,
    onOpenAiSetup = onOpenAiSetup,
    startOnInfo = startOnInfo,
)
