package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Tavern entry surface.
 *
 * The character hub owns imported personas, direct character chat and the handoff into
 * the existing story branch runtime. Keeping this compatibility entry lets shelf/reader
 * callers stay stable while the Tavern UI evolves independently.
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
    TavernCharacterHubV2(
        book = book,
        libraryState = libraryState,
        aiReady = aiReady,
        onAiSetup = onAiSetup,
    )
}
