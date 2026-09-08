package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Tavern entry surface.
 *
 * Novel character distillation is the primary experience. Imported chat personas and the
 * existing story branch runtime remain available as secondary routes inside that surface.
 *
 * The nested MaterialTheme is intentional: the legacy tavern internals still use Material3
 * semantic surfaces, so mapping those roles here lets the whole flow inherit Langhuan's visual
 * language without touching distillation/chat/business state.
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
    val scheme = MaterialTheme.colorScheme.copy(
        background = t.background,
        onBackground = t.foreground,
        surface = t.card,
        onSurface = t.cardForeground,
        surfaceVariant = t.muted,
        onSurfaceVariant = t.mutedForeground,
        primary = t.primary,
        onPrimary = t.primaryForeground,
        primaryContainer = t.warmSurface,
        onPrimaryContainer = t.foreground,
        secondary = t.accent,
        onSecondary = t.accentForeground,
        secondaryContainer = t.accent,
        onSecondaryContainer = t.accentForeground,
        outline = t.track,
        outlineVariant = t.track,
        error = t.destructive,
        onError = t.destructiveForeground,
    )

    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
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
}
