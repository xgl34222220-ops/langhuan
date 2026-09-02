package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry kept for existing navigation and tests.
 * The actual chapter experience now lives in Novel Skill OS V4 unified workspace.
 */
@Composable
fun WritingFlowPage(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    WritingWorkspaceV4(
        novelId = novelId,
        viewModel = viewModel,
        onClose = onClose,
        onEditChapter = onEditChapter,
    )
}
