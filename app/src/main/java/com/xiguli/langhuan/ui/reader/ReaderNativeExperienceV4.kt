package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Stable reader entry. The resume guard only follows the Activity lifecycle, preventing
 * recents/system-gesture false turns; the reading surface itself stays mounted for the whole
 * session, across chapters.
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
    /** Non-null for EPUB books: switch this book back to the 原版 renderer. */
    onOpenOriginalEdition: (() -> Unit)? = null,
) {
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

    // The V30 engine owns chapter navigation itself: it keeps the neighbouring chapters laid
    // out and turns across chapter boundaries without remounting, so there is deliberately no
    // per-chapter key here any more (that remount was what blanked the page between chapters).
    ReaderWindowSessionV27(resumeRequested)
    ReaderEngineV30(
        viewModel = viewModel,
        studioState = studioState,
        onBackToShelf = onBackToShelf,
        onEnterWriting = onEnterWriting,
        onOpenEditor = onOpenEditor,
        onOpenAiSetup = onOpenAiSetup,
        startOnInfo = startOnInfo,
        interactionEnabled = readerMounted,
        onOpenOriginalEdition = onOpenOriginalEdition,
    )
}

