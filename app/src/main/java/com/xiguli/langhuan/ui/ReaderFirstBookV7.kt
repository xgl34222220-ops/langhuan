package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Startup-safe compatibility entry.
 *
 * V8-V11 remain available on their implementation files, but production reading
 * temporarily uses the Material-only V6 reader until the Miuix runtime crash is
 * reproduced and fixed on-device.
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
    ReaderFirstBookV6(
        viewModel = viewModel,
        studioState = studioState,
        onBackToShelf = onBackToShelf,
        onEnterWriting = onEnterWriting,
        onOpenEditor = onOpenEditor,
        onOpenAiSetup = onOpenAiSetup,
    )
}
