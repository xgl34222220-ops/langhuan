package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable
import com.xiguli.langhuan.ui.reader.LibraryExperienceState
import com.xiguli.langhuan.ui.reader.ReaderBookUi

/**
 * Compatibility entry only. The previous implementation mounted StoryPlayPanelV17 invisibly.
 * That legacy runtime surface has been removed; all calls now go straight to StoryCoreExperience.
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
    StoryCoreExperience(
        book = book,
        libraryState = libraryState,
        aiReady = aiReady,
        onAiSetup = onAiSetup,
    )
}
