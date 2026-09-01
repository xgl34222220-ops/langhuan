package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry for the current root. Shelf implementation now lives in V8.
 */
@Composable
fun ReaderShelfV7(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    ReaderShelfV8(
        state = state,
        importState = importState,
        onOpenBook = onOpenBook,
        onOpenBookInfo = onOpenBook,
        onImportLocal = onImportLocal,
        onDeleteBook = onDeleteBook,
        onCreate = onCreate,
        onAiSetup = onAiSetup,
        onRunCenter = onRunCenter,
        onSkills = onSkills,
    )
}
