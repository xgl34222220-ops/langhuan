package com.xiguli.langhuan.ui.writing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.story.StoryGraphHealthPillV10
import com.xiguli.langhuan.ui.story.StoryGraphHealthSheetV10
import com.xiguli.langhuan.ui.story.StoryGraphHealthViewModel
import com.xiguli.langhuan.ui.canon.CanonChangeProposalViewModel

/**
 * Novel Skill OS V10 entry: keeps V8/V9 authoring surfaces intact and adds one lightweight,
 * read-only Story Graph health affordance. The health analyzer never mutates the project.
 */
@Composable
fun WritingWorkspaceV10(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    val flow by viewModel.state.collectAsStateWithLifecycle()
    val healthVm: StoryGraphHealthViewModel = viewModel()
    val health by healthVm.state.collectAsStateWithLifecycle()
    val canonVm: CanonChangeProposalViewModel = viewModel()
    var showHealth by remember(novelId) { mutableStateOf(false) }

    LaunchedEffect(novelId) {
        healthVm.load(novelId, force = true)
    }
    LaunchedEffect(
        novelId,
        flow.draft?.version,
        flow.snapshot?.factHistory?.size,
        flow.snapshot?.candidateFacts?.size,
    ) {
        if (flow.ready) healthVm.load(novelId, force = true)
    }

    Box {
        WritingWorkspaceV8(
            novelId = novelId,
            viewModel = viewModel,
            onClose = onClose,
            onEditChapter = onEditChapter,
        )

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 104.dp, end = 12.dp),
        ) {
            StoryGraphHealthPillV10(
                state = health,
                onClick = {
                    healthVm.load(novelId, force = true)
                    showHealth = true
                },
            )
        }
    }

    if (showHealth) {
        StoryGraphHealthSheetV10(
            state = health,
            onRefresh = { healthVm.load(novelId, force = true) },
            onOpenChapter = { chapterNumber ->
                showHealth = false
                onEditChapter(novelId, chapterNumber)
            },
            onOpenMigration = {
                showHealth = false
                canonVm.loadMigrationQueue(novelId)
                canonVm.openMigrationQueue()
            },
            onDismiss = { showHealth = false },
        )
    }
}
