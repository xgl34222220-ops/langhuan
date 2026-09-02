package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Novel Skill OS V8 entry.
 *
 * The visible authoring surface remains the same single natural-language controller. V8 adds
 * persistent Canon migration repair queues behind V7's confirmed-change workflow.
 */
@Composable
fun WritingWorkspaceV8(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    WritingWorkspaceV6(
        novelId = novelId,
        viewModel = viewModel,
        onClose = onClose,
        onEditChapter = onEditChapter,
    )
}
