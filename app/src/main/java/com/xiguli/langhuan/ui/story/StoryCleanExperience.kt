package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Tavern entry surface.
 *
 * Novel character distillation is the primary experience. Imported chat personas and the
 * existing story branch runtime remain available as secondary routes inside that surface.
 */
@Composable
fun StoryCleanExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val keepSignatureStable = onAdopted
    TavernNovelCharacterExperienceV3(
        book = book,
        libraryState = libraryState,
        aiReady = aiReady,
        onAiSetup = onAiSetup,
    )
}
