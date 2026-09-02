package com.xiguli.langhuan.ui

import androidx.compose.runtime.Composable

/**
 * Novel Skill OS V7 entry.
 *
 * V6 remains the single natural-language controller implementation; V7 extends that controller
 * with Canon proposal/impact/confirmation rather than forking a second chapter workspace.
 */
@Composable
fun WritingWorkspaceV7(
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
