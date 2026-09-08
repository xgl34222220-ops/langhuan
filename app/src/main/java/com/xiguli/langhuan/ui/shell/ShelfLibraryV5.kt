package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry kept for the current root router.
 *
 * V5 now points at the Literary MIUIx / Langhuan Glass shell. The previous
 * ShelfQingmoFunctionalV9 implementation remains in-tree as a fallback during migration.
 */
@Composable
fun ShelfLibraryV5(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    openingBookId: String?,
    onOpenBook: (String) -> Unit,
    onOpenTavern: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) = LanghuanLiteraryShelfV1(
    state = state,
    importState = importState,
    openingBookId = openingBookId,
    onOpenBook = onOpenBook,
    onOpenTavern = onOpenTavern,
    onImportLocal = onImportLocal,
    onDeleteBook = onDeleteBook,
    onCreate = onCreate,
    onAiSetup = onAiSetup,
    onRunCenter = onRunCenter,
    onSkills = onSkills,
)
