package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry while the old version-stacked Story UI is being retired.
 * New code lives in the functional story feature screen instead of adding another V18/V19 layer.
 */
@Composable
fun StoryManagementExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
    onClose: () -> Unit,
) {
    StoryManagementScreen(
        book = book,
        libraryState = libraryState,
        aiReady = aiReady,
        onAiSetup = onAiSetup,
        onAdopted = onAdopted,
        onClose = onClose,
    )
}
