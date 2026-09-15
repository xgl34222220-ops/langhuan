package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/** Stable shelf entry. The LuoShu functional shell now covers the legacy feature set. */
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
) = ShelfLuoShuFunctionalV1(
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
