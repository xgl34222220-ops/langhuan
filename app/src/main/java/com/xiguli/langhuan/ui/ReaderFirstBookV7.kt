package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Crash hotfix: temporarily route production reading back to the proven V10 reader.
 * Reader V11 remains in source for follow-up runtime diagnosis, but is not reachable from the app.
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
    ReaderFirstBookV10(
        viewModel = viewModel,
        studioState = studioState,
        onBackToShelf = onBackToShelf,
        onEnterWriting = onEnterWriting,
        onOpenEditor = onOpenEditor,
        onOpenAiSetup = onOpenAiSetup,
    )
}
