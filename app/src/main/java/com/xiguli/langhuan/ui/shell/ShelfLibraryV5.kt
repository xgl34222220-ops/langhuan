package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Stable shelf entry for the current root router.
 *
 * LuoShu is the visual baseline, but the functional V9 shelf currently owns book editing, profile,
 * custom-shelf and history flows that have not all been ported to the newer native shelf yet.
 * Keep the complete functional route active while those surfaces are rebuilt in-place; do not trade
 * working features for a cosmetic route switch.
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
) = ShelfQingmoFunctionalV9(
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
