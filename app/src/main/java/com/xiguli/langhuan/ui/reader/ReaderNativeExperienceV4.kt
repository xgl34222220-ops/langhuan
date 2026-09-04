package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Compatibility entry kept for the current root router.
 *
 * The reader surface owns a HorizontalPager. That pager must be recreated when the active chapter
 * changes; otherwise Compose retains the previous chapter's page index. Crossing forward from a
 * last page would then open the next chapter at its tail, while crossing backward from a first page
 * would open the previous chapter at its head. Keying the reader subtree by chapter id makes the
 * persisted chapter anchor (head for next, tail for previous) the single source of truth.
 */
@Composable
fun ReaderNativeExperienceV4(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chapterKey = state.readingChapter?.id ?: "reader-loading"

    key(chapterKey) {
        ReaderQingmoFunctionalV9(
            viewModel = viewModel,
            studioState = studioState,
            onBackToShelf = onBackToShelf,
            onEnterWriting = onEnterWriting,
            onOpenEditor = onOpenEditor,
            onOpenAiSetup = onOpenAiSetup,
            startOnInfo = startOnInfo,
        )
    }
}
