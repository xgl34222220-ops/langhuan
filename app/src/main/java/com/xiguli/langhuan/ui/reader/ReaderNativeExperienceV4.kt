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
 * Stable reader entry. The active chapter owns its own pager subtree, and the subtree is unmounted
 * outside RESUMED so recents/system-gesture transitions cannot become page turns.
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
    var resumeRequested by remember(chapterKey) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var readerMounted by remember(chapterKey) { mutableStateOf(resumeRequested) }

    DisposableEffect(lifecycleOwner, chapterKey) {
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

    LaunchedEffect(resumeRequested, chapterKey) {
        if (!resumeRequested) {
            readerMounted = false
            return@LaunchedEffect
        }
        delay(120)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            readerMounted = true
        }
    }

    if (!readerMounted) {
        Box(Modifier.fillMaxSize())
        return
    }

    key(chapterKey) {
        ReaderQingmoHeroV13(
            viewModel = viewModel,
            studioState = studioState,
            onBackToShelf = onBackToShelf,
            onEnterWriting = onEnterWriting,
            onOpenEditor = onOpenEditor,
            onOpenAiSetup = onOpenAiSetup,
            startOnInfo = startOnInfo,
        )
    }
}
