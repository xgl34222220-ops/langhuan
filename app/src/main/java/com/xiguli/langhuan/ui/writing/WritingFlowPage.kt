package com.xiguli.langhuan.ui.writing

import androidx.compose.runtime.Composable

/**
 * Compatibility entry kept for existing navigation and tests.
 * The actual chapter experience now lives in Novel Skill OS V10 Story Graph workspace.
 */
@Composable
fun WritingFlowPage(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    WritingWorkspaceV10(
        novelId = novelId,
        viewModel = viewModel,
        onClose = onClose,
        onEditChapter = onEditChapter,
    )
}
