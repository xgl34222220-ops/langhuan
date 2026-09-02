package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Startup-safe production reader entry.
 *
 * Real Miuix reader experiments remain isolated from startup. Production now uses the stable
 * Material3-only V9 reader with floating chrome and the same visual language as the rebuilt shelf.
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
    ReaderFirstBookStableV9(
        viewModel = viewModel,
        studioState = studioState,
        onBackToShelf = onBackToShelf,
        onEnterWriting = onEnterWriting,
        onOpenEditor = onOpenEditor,
        onOpenAiSetup = onOpenAiSetup,
    )
}
