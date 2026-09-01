package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Startup-safe compatibility entry.
 *
 * The real Miuix shelf remains in the repository for later runtime validation,
 * but the production route temporarily uses the Material-only V6 shelf so the
 * app can start without touching Miuix UI classes.
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
        onCreate = onCreate,
        onAiSetup = onAiSetup,
        onRunCenter = onRunCenter,
        onSkills = onSkills,
    )
}
