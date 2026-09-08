package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * Stable reader entry. The active chapter owns a fresh pager subtree, but chapter switches no
 * longer unmount the whole reader or wait through the resume guard. The guard only follows the
 * Activity lifecycle, preventing recents/system-gesture false turns without adding a blank frame
 * between adjacent chapters.
 */
@Composable
fun ReaderNativeExperienceV4(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chapterKey = state.readingChapter?.id ?: "reader-loading"
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRequested by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var readerMounted by remember { mutableStateOf(resumeRequested) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeRequested = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    resumeRequested = false
                    readerMounted = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeRequested) {
        if (!resumeRequested) {
            readerMounted = false
            return@LaunchedEffect
        }
        // Only a real lifecycle resume gets the stabilization delay. Changing chapters must not
        // blank the reading surface for 120ms and then rebuild it like a navigation transition.
        delay(120)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            readerMounted = true
        }
    }

    // Keep the last rendered page in the task snapshot and preserve its pager state.
    // Lifecycle transitions disable input instead of replacing the book with a blank surface.

    // Pager state still resets per chapter so saved page/offset restoration remains deterministic,
    // while the outer reader surface stays mounted and visually continuous.
    ReaderWindowSessionV27(resumeRequested)
    key(chapterKey) {
        ReaderQingmoHeroV13(
            viewModel = viewModel,
            studioState = studioState,
            onBackToShelf = onBackToShelf,
            onEnterWriting = onEnterWriting,
            onOpenEditor = onOpenEditor,
            onOpenAiSetup = onOpenAiSetup,
            startOnInfo = startOnInfo,
            interactionEnabled = readerMounted,
        )
    }
}

