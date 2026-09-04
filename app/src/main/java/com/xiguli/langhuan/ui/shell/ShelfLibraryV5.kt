package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry kept for the current root router.
 * The actual shelf is the Qingmo-inspired V6 implementation.
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
) = ShelfQingmoV6(
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
