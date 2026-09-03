package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * Resolves the persisted chapter before delegating to ReaderExperience.
 *
 * ReaderExperience used to treat readingChapter == null as an unbounded loading gate. If chapter
 * loading and Compose resume happened in the wrong order, that gate could stay on screen forever.
 * This entry guard makes the state transition explicit and bounded: resolve from the chapters that
 * are already in LibraryExperienceState, synchronously ask the ViewModel to select it, retry a few
 * frames if another state refresh races with us, and finally show an actionable error instead of an
 * infinite spinner.
 */
@Composable
fun ReaderExperienceEntryGuard(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook ?: return
    val context = LocalContext.current
    var failedAttempts by remember(book.id) { mutableIntStateOf(0) }

    val targetChapterNumber = remember(book.id, state.chapters) {
        if (state.chapters.isEmpty()) null
        else {
            val saved = ReaderProgressStoreV11.load(
                context = context,
                bookId = book.id,
                fallbackChapter = book.currentChapter.coerceAtLeast(1),
            )
            state.chapters.firstOrNull { it.chapterNumber == saved.chapterNumber }?.chapterNumber
                ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }?.chapterNumber
                ?: state.chapters.minByOrNull { it.chapterNumber }?.chapterNumber
        }
    }

    LaunchedEffect(book.id, targetChapterNumber, state.readingChapter?.id, state.chapters.size) {
        if (state.readingChapter != null || targetChapterNumber == null) return@LaunchedEffect
        repeat(4) { attempt ->
            viewModel.openReader(targetChapterNumber)
            delay(24)
            if (viewModel.state.value.readingChapter != null) {
                failedAttempts = 0
                return@LaunchedEffect
            }
            failedAttempts = attempt + 1
        }
    }

    when {
        state.readingChapter != null -> ReaderExperience(
            viewModel = viewModel,
            studioState = studioState,
            onBackToShelf = onBackToShelf,
            onEnterWriting = onEnterWriting,
            onOpenEditor = onOpenEditor,
            onOpenAiSetup = onOpenAiSetup,
            startOnInfo = startOnInfo,
        )

        state.isBusy -> ReaderEntryLoading(book.title)

        state.chapters.isEmpty() -> ReaderEntryFailure(
            title = "没有可阅读章节",
            detail = "这本书当前没有加载到章节正文。返回书架后重新打开，或检查导入结果。",
            onRetry = { viewModel.openBook(book.id) },
            onBack = onBackToShelf,
        )

        failedAttempts >= 4 -> ReaderEntryFailure(
            title = "阅读位置恢复失败",
            detail = "章节已经加载，但当前阅读章节没有成功写入。可以重新尝试，不会覆盖已保存的阅读进度。",
            onRetry = {
                failedAttempts = 0
                targetChapterNumber?.let(viewModel::openReader)
            },
            onBack = onBackToShelf,
        )

        else -> ReaderEntryLoading(book.title)
    }
}

@Composable
private fun ReaderEntryLoading(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
        Text(
            text = "正在恢复阅读位置",
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderEntryFailure(
    title: String,
    detail: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text("重新尝试") }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("返回书架") }
    }
}
