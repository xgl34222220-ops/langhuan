package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Startup-safe shelf entry. The production shelf stays on the Material3-only compatibility path
 * while exposing the full library actions, including book deletion.
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
    ReaderShelfV6(
        state = state,
        importState = importState,
        onOpenBook = onOpenBook,
        onImportLocal = onImportLocal,
        onDeleteBook = onDeleteBook,
        onCreate = onCreate,
        onAiSetup = onAiSetup,
        onRunCenter = onRunCenter,
        onSkills = onSkills,
    )
}
