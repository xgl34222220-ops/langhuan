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
 * Compatibility entry kept for the current root router.
 *
 * Two invariants are enforced here instead of leaving them to the visual reader shell:
 * 1. A HorizontalPager is recreated when the chapter changes, so a last-page index from chapter N
 *    can never become the initial page of chapter N+1 (and vice versa).
 * 2. The paginated reader is unmounted while the Activity is not RESUMED. Android can transiently
 *    change safe insets / container size while moving to recents or returning from background. If
 *    pagination stays live during that transition, a layout-token change can remap the pager and
 *    look like an unsolicited page turn. The old reader disposes first (persisting its stable text
 *    anchor), then mounts again only after the resumed window has settled for a short interval.
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
        // Wait past the transient system-bar / recents gesture frame before accepting reader input
        // or recalculating page geometry.
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
        ReaderQingmoFunctionalV9(
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
