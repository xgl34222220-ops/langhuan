package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry kept for the current root route. The implementation now lives in V8.
 */
@Composable
fun ReaderFirstBookV7(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
) {
    ReaderFirstBookV8(
        viewModel = viewModel,
        studioState = studioState,
        onBackToShelf = onBackToShelf,
        onEnterWriting = onEnterWriting,
        onOpenEditor = onOpenEditor,
        onOpenAiSetup = onOpenAiSetup,
    )
}
