package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

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
    val t = LocalLanghuanUiTokens.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = t.background,
        contentColor = t.foreground,
    ) {
        TavernNovelCharacterExperienceV3(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
        )
    }
}
